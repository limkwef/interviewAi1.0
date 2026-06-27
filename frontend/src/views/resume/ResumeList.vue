<template>
  <div class="resume-list">
    <div class="page-header">
      <h2>简历管理</h2>
      <div class="header-actions">
        <el-button type="primary" @click="$router.push('/resume/upload')">
          <el-icon><Upload /></el-icon>上传简历
        </el-button>
        <el-button @click="$router.push('/resume/create')">
          <el-icon><Edit /></el-icon>在线填写
        </el-button>
      </div>
    </div>

    <el-empty v-if="!loading && resumes.length === 0" description="暂无简历，上传或在线填写一份吧">
      <el-button type="primary" @click="$router.push('/resume/upload')">上传简历</el-button>
    </el-empty>

    <div v-else class="resume-cards">
      <el-card v-for="resume in resumes" :key="resume.id" class="resume-card" shadow="hover">
        <div class="card-header">
          <div class="card-title">
            <el-icon class="file-icon"><Document /></el-icon>
            <span>{{ resume.fileName || '在线填写简历' }}</span>
          </div>
          <el-tag v-if="resume.isActive === 1" type="success" size="small">使用中</el-tag>
        </div>

        <div class="card-info">
          <div class="info-item">
            <span class="label">来源：</span>
            <el-tag :type="resume.source === 'upload' ? 'primary' : 'warning'" size="small">
              {{ resume.source === 'upload' ? '上传解析' : '在线填写' }}
            </el-tag>
          </div>
          <div class="info-item">
            <span class="label">状态：</span>
            <el-tag :type="statusType(resume.status)" size="small">
              {{ statusText(resume.status) }}
            </el-tag>
          </div>
          <div class="info-item" v-if="resume.fileSize">
            <span class="label">大小：</span>
            <span>{{ formatSize(resume.fileSize) }}</span>
          </div>
          <div class="info-item">
            <span class="label">创建：</span>
            <span>{{ formatDate(resume.createTime) }}</span>
          </div>
        </div>

        <div v-if="resume.status === 2" class="error-msg">
          <el-text type="danger" size="small">{{ resume.errorMsg }}</el-text>
        </div>

        <div class="card-actions">
          <el-button size="small" @click="$router.push(`/resume/detail/${resume.id}`)"
                     :disabled="resume.status !== 1">
            查看详情
          </el-button>
          <el-button size="small" type="success"
                     v-if="resume.isActive !== 1 && resume.status === 1"
                     @click="handleActivate(resume.id)">
            设为当前简历
          </el-button>
          <el-button size="small" type="warning"
                     v-if="resume.status === 2 && resume.source === 'upload'"
                     @click="handleReparse(resume.id)">
            重新解析
          </el-button>
          <el-button size="small" type="danger"
                     v-if="resume.isActive !== 1"
                     @click="handleDelete(resume.id)">
            删除
          </el-button>
        </div>
      </el-card>
    </div>

    <div v-if="total > pageSize" class="pagination">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next"
        @current-change="loadList"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Edit, Document } from '@element-plus/icons-vue'
import { getResumeList, activateResume, deleteResume, reparseResume } from '@/api/resume'

const router = useRouter()
const loading = ref(false)
const resumes = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

onMounted(() => loadList())

async function loadList() {
  loading.value = true
  try {
    const res = await getResumeList({ page: currentPage.value, size: pageSize.value })
    if (res.code === 200) {
      resumes.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch (e) {
    ElMessage.error('加载简历列表失败')
  } finally {
    loading.value = false
  }
}

async function handleActivate(id) {
  try {
    await ElMessageBox.confirm('确定将此简历设为当前使用的简历？', '确认')
    const res = await activateResume(id)
    if (res.code === 200) {
      ElMessage.success('激活成功')
      loadList()
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('激活失败')
  }
}

async function handleDelete(id) {
  try {
    await ElMessageBox.confirm('确定删除此简历？删除后不可恢复。', '确认', { type: 'warning' })
    const res = await deleteResume(id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      loadList()
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

async function handleReparse(id) {
  try {
    const res = await reparseResume(id)
    if (res.code === 200) {
      ElMessage.success('重新解析已启动')
      loadList()
    }
  } catch (e) {
    ElMessage.error('重新解析失败')
  }
}

function statusText(status) {
  return { 0: '解析中', 1: '已完成', 2: '解析失败' }[status] || '未知'
}

function statusType(status) {
  return { 0: 'warning', 1: 'success', 2: 'danger' }[status] || 'info'
}

function formatSize(bytes) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN')
}
</script>

<style scoped>
.resume-list {
  padding: 20px;
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
.header-actions {
  display: flex;
  gap: 10px;
}
.resume-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 16px;
}
.resume-card {
  cursor: default;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 15px;
}
.file-icon {
  color: #409eff;
}
.card-info {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-bottom: 12px;
}
.info-item {
  font-size: 13px;
  color: #666;
}
.info-item .label {
  color: #999;
}
.error-msg {
  margin-bottom: 12px;
}
.card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}
</style>
