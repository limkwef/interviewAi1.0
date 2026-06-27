<template>
  <div class="resume-upload">
    <div class="page-header">
      <h2>上传简历</h2>
      <el-button @click="$router.push('/resume/list')">返回列表</el-button>
    </div>

    <el-card class="upload-card">
      <el-upload
        ref="uploadRef"
        class="upload-area"
        drag
        :auto-upload="false"
        :limit="1"
        :on-change="handleFileChange"
        :on-exceed="handleExceed"
        accept=".pdf,.txt"
      >
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="upload-text">将文件拖到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="upload-tip">支持 PDF、TXT 格式，文件大小不超过 5MB</div>
        </template>
      </el-upload>

      <div v-if="selectedFile" class="file-info">
        <el-text>已选择：{{ selectedFile.name }} ({{ formatSize(selectedFile.size) }})</el-text>
      </div>

      <div class="upload-actions">
        <el-button type="primary" :loading="uploading" :disabled="!selectedFile" @click="handleUpload">
          {{ uploading ? '上传中...' : '开始上传' }}
        </el-button>
      </div>
    </el-card>

    <!-- 解析状态 -->
    <el-card v-if="resumeId !== null" class="status-card">
      <template #header>
        <span>解析状态</span>
      </template>
      <div class="parse-status">
        <el-steps :active="parseStep" finish-status="success" align-center>
          <el-step title="上传完成" />
          <el-step title="文本提取" />
          <el-step title="AI 解析" />
        </el-steps>

        <div v-if="parseStatus === 0" class="status-text">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>AI 正在解析简历内容，请稍候...</span>
        </div>
        <div v-else-if="parseStatus === 1" class="status-text success">
          <el-icon><CircleCheck /></el-icon>
          <span>解析完成！</span>
          <el-button type="primary" size="small" @click="$router.push(`/resume/detail/${resumeId}`)">
            查看详情
          </el-button>
          <el-button size="small" @click="$router.push('/resume/list')">返回列表</el-button>
        </div>
        <div v-else-if="parseStatus === 2" class="status-text error">
          <el-icon><CircleClose /></el-icon>
          <span>解析失败：{{ errorMsg || '未知错误' }}</span>
          <el-button type="warning" size="small" @click="handleReparse">重新解析</el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { UploadFilled, Loading, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { uploadResume, getResumeStatus, reparseResume } from '@/api/resume'

const router = useRouter()
const uploadRef = ref(null)
const selectedFile = ref(null)
const uploading = ref(false)
const resumeId = ref(null)
const parseStatus = ref(null)
const errorMsg = ref('')
let pollTimer = null

function handleFileChange(file) {
  selectedFile.value = file.raw
}

function handleExceed() {
  ElMessage.warning('只能上传一个文件，请先移除已选文件')
}

async function handleUpload() {
  if (!selectedFile.value) return
  uploading.value = true
  try {
    const res = await uploadResume(selectedFile.value)
    if (res.code === 200) {
      resumeId.value = res.data.id
      parseStatus.value = res.data.status
      ElMessage.success('上传成功，正在解析')
      startPolling()
    }
  } catch (e) {
    ElMessage.error('上传失败')
  } finally {
    uploading.value = false
  }
}

function startPolling() {
  pollTimer = setInterval(async () => {
    if (!resumeId.value) return
    try {
      const res = await getResumeStatus(resumeId.value)
      if (res.code === 200) {
        parseStatus.value = res.data.status
        errorMsg.value = res.data.errorMsg || ''
        if (res.data.status === 1 || res.data.status === 2) {
          clearInterval(pollTimer)
          pollTimer = null
        }
      }
    } catch (e) {
      // 轮询失败不处理
    }
  }, 3000)
}

async function handleReparse() {
  try {
    await reparseResume(resumeId.value)
    parseStatus.value = 0
    errorMsg.value = ''
    ElMessage.info('重新解析已启动')
    startPolling()
  } catch (e) {
    ElMessage.error('重新解析失败')
  }
}

const parseStep = ref(0)

// 根据 parseStatus 更新步骤
import { watch } from 'vue'
watch(parseStatus, (val) => {
  if (val === 0) parseStep.value = 1
  else if (val === 1) parseStep.value = 3
  else if (val === 2) parseStep.value = 2
})

function formatSize(bytes) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.resume-upload {
  padding: 20px;
  max-width: 700px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.page-header h2 {
  margin: 0;
  font-size: 20px;
}
.upload-card {
  margin-bottom: 20px;
}
.upload-area {
  width: 100%;
}
.upload-icon {
  font-size: 48px;
  color: #c0c4cc;
}
.upload-text {
  color: #606266;
  margin-top: 8px;
}
.upload-text em {
  color: #409eff;
  font-style: normal;
}
.upload-tip {
  color: #909399;
  font-size: 12px;
  margin-top: 8px;
}
.file-info {
  margin: 16px 0;
}
.upload-actions {
  text-align: center;
  margin-top: 16px;
}
.status-card {
  margin-top: 20px;
}
.parse-status {
  text-align: center;
}
.status-text {
  margin-top: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 15px;
}
.status-text.success {
  color: #67c23a;
}
.status-text.error {
  color: #f56c6c;
}
</style>
