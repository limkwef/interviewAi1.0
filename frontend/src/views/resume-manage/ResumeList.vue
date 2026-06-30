<template>
  <div class="page-container resume-list-page">
    <div class="page-header">
      <h1 class="page-title">简历管理</h1>
      <p class="page-subtitle">管理你的简历，用于简历模拟面试</p>
    </div>
    <div class="action-bar">
      <el-button type="primary" @click="$router.push('/resume/upload')">
        <el-icon><Upload /></el-icon>上传简历
      </el-button>
      <el-button @click="$router.push('/resume/create')">
        <el-icon><EditPen /></el-icon>在线填写
      </el-button>
    </div>
    <div class="table-container">
      <el-table v-loading="loading" :data="resumeList" style="width: 100%" empty-text="暂无简历，点击上方按钮添加">
        <el-table-column prop="fileName" label="文件名" min-width="200">
          <template #default="{ row }">
            <span class="title-link" @click="goDetail(row.id)">{{ row.fileName || '在线填写简历' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="120">
          <template #default="{ row }">
            <el-tag :type="row.source === 'upload' ? 'primary' : 'success'" effect="plain">
              {{ row.source === 'upload' ? '文件上传' : '在线填写' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" effect="plain">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="激活" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isActive === 1" type="success" effect="dark" size="small">当前</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="goDetail(row.id)">查看</el-button>
            <el-button size="small" type="success" :disabled="row.isActive === 1" @click="handleActivate(row.id)">激活</el-button>
            <el-popconfirm title="确定删除？" @confirm="handleDelete(row.id)">
              <template #reference><el-button size="small" type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap" v-if="total > pageSize">
        <el-pagination v-model:current-page="currentPage" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="fetchList" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getResumeList, activateResume, deleteResume } from '@/api/resume'
import { ElMessage } from 'element-plus'
import { Upload, EditPen } from '@element-plus/icons-vue'

const router = useRouter()
const loading = ref(false)
const resumeList = ref([])
const currentPage = ref(1)
const pageSize = 10
const total = ref(0)

const statusText = (s) => ({ 0: '解析中', 1: '已就绪', 2: '解析失败' }[s] || '未知')
const statusType = (s) => ({ 0: 'warning', 1: 'success', 2: 'danger' }[s] || 'info')
const formatDate = (d) => d ? new Date(d).toLocaleString('zh-CN') : '-'

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getResumeList({ page: currentPage.value, size: pageSize })
    resumeList.value = res.data?.records || res.data?.list || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}

const goDetail = (id) => router.push(`/resume/detail/${id}`)
const handleActivate = async (id) => {
  try { await activateResume(id); ElMessage.success('激活成功'); fetchList() }
  catch { ElMessage.error('激活失败') }
}
const handleDelete = async (id) => {
  try { await deleteResume(id); ElMessage.success('删除成功'); fetchList() }
  catch { ElMessage.error('删除失败') }
}

onMounted(fetchList)
</script>

<style scoped>
.resume-list-page { padding: 24px; }
.page-header { margin-bottom: 24px; }
.page-title { font-size: 24px; font-weight: 600; color: #1a1a2e; margin: 0 0 4px; }
.page-subtitle { font-size: 14px; color: #8c8c8c; margin: 0; }
.action-bar { margin-bottom: 16px; display: flex; gap: 12px; }
.table-container { background: #fff; border-radius: 12px; padding: 16px; box-shadow: 0 2px 12px rgba(0,0,0,0.04); }
.title-link { color: #409eff; cursor: pointer; }
.title-link:hover { text-decoration: underline; }
.pagination-wrap { display: flex; justify-content: center; margin-top: 16px; }
</style>
