<template>
  <div class="page-container resume-upload-page">
    <div class="page-header">
      <h1 class="page-title">上传简历</h1>
      <p class="page-subtitle">支持 PDF、TXT 格式，上传后自动解析提取信息</p>
    </div>
    <div class="upload-card">
      <el-upload
        ref="uploadRef"
        class="resume-uploader"
        drag
        :auto-upload="false"
        :limit="1"
        accept=".pdf,.txt"
        :on-change="handleFileChange"
        :on-exceed="handleExceed"
      >
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="upload-text">将文件拖到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="upload-tip">仅支持 PDF / TXT 格式，文件大小不超过 10MB</div>
        </template>
      </el-upload>
      <div class="upload-actions">
        <el-button type="primary" :loading="uploading" :disabled="!selectedFile" @click="handleUpload">
          {{ uploading ? '上传中...' : '开始上传' }}
        </el-button>
        <el-button @click="$router.push('/resume/list')">返回列表</el-button>
      </div>
      <div v-if="uploadResult" class="upload-result">
        <el-alert :title="uploadResult.msg" type="success" show-icon :closable="false" />
        <el-button class="result-btn" type="primary" @click="$router.push(`/resume/detail/${uploadResult.id}`)">查看简历详情</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { uploadResume } from '@/api/resume'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'

const router = useRouter()
const uploadRef = ref(null)
const selectedFile = ref(null)
const uploading = ref(false)
const uploadResult = ref(null)

const handleFileChange = (file) => {
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning('文件大小不能超过 10MB')
    uploadRef.value?.clearFiles()
    return
  }
  selectedFile.value = file.raw
}

const handleExceed = () => {
  ElMessage.warning('只能上传一个文件，请先移除已选文件')
}

const handleUpload = async () => {
  if (!selectedFile.value) return
  uploading.value = true
  try {
    const res = await uploadResume(selectedFile.value)
    uploadResult.value = { msg: res.message || '上传成功，正在解析', id: res.data?.id }
    ElMessage.success('上传成功')
  } catch (e) {
    ElMessage.error('上传失败：' + (e.message || '未知错误'))
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.resume-upload-page { padding: 24px; }
.page-header { margin-bottom: 24px; }
.page-title { font-size: 24px; font-weight: 600; color: #1a1a2e; margin: 0 0 4px; }
.page-subtitle { font-size: 14px; color: #8c8c8c; margin: 0; }
.upload-card { background: #fff; border-radius: 12px; padding: 32px; box-shadow: 0 2px 12px rgba(0,0,0,0.04); max-width: 600px; }
.resume-uploader { width: 100%; }
.upload-icon { font-size: 48px; color: #c0c4cc; }
.upload-text { color: #606266; font-size: 14px; margin-top: 8px; }
.upload-text em { color: #409eff; font-style: normal; }
.upload-tip { color: #909399; font-size: 12px; margin-top: 8px; }
.upload-actions { margin-top: 24px; display: flex; gap: 12px; }
.upload-result { margin-top: 24px; }
.result-btn { margin-top: 12px; }
</style>
