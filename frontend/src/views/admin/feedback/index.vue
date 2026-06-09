<template>
  <div class="feedback-page">
    <el-card>
      <template #header>
      <div class="card-header">
        <span>反馈管理</span>
      </div>
    </template>
      <el-form :inline="true" :model="queryParams" class="search-form">
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 140px;">
            <el-option label="待处理" :value="0" />
            <el-option label="已处理" :value="1" />
            <el-option label="已忽略" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="userId" label="用户ID" width="100" />
        <el-table-column prop="type" label="类型" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ row.type || '未分类' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="反馈内容" min-width="250" show-overflow-tooltip />
        <el-table-column prop="contact" label="联系方式" width="150" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="提交时间" width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button
                v-if="row.status === 0"
                type="success"
                size="small"
                class="btn-action"
                @click="handleUpdateStatus(row.id, 1)"
              >
                处理
              </el-button>
              <el-button
                v-if="row.status === 0"
                type="warning"
                size="small"
                class="btn-action"
                @click="handleUpdateStatus(row.id, 2)"
              >
                忽略
              </el-button>
              <el-button
                type="danger"
                size="small"
                class="btn-action"
                @click="handleDelete(row.id)"
              >
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="queryParams.page"
        v-model:page-size="queryParams.size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSearch"
        @current-change="handleSearch"
        class="pagination"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, ElSelect, ElOption } from 'element-plus'
import { getFeedbackList, updateFeedbackStatus, deleteFeedback } from '@/api/admin'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const queryParams = reactive({
  page: 1,
  size: 10,
  status: ''
})

onMounted(() => {
  handleSearch()
})

function getStatusLabel(status) {
  const map = { 0: '待处理', 1: '已处理', 2: '已忽略' }
  return map[status] || '未知'
}

function getStatusType(status) {
  const map = { 0: 'warning', 1: 'success', 2: 'info' }
  return map[status] || 'info'
}

async function handleSearch() {
  loading.value = true
  try {
    const params = { ...queryParams }
    if (params.status === undefined || params.status === '' || params.status === null) {
      delete params.status
    }
    const res = await getFeedbackList(params)
    tableData.value = res.data.list
    total.value = res.data.total
  } catch (err) {
    ElMessage.error('获取反馈列表失败')
  } finally {
    loading.value = false
  }
}

function handleReset() {
  queryParams.page = 1
  queryParams.status = ''
  handleSearch()
}

async function handleUpdateStatus(id, status) {
  const statusText = status === 1 ? '已处理' : '已忽略'
  try {
    await ElMessageBox.confirm(`确认将此反馈标记为${statusText}？`, '提示', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await updateFeedbackStatus(id, status)
    ElMessage.success('操作成功')
    handleSearch()
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

async function handleDelete(id) {
  try {
    await ElMessageBox.confirm('确认删除此反馈？删除后不可恢复。', '提示', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteFeedback(id)
    ElMessage.success('删除成功')
    handleSearch()
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}
</script>

<style lang="scss" scoped>
.feedback-page {
  padding: 20px;

  .card-header {
    font-weight: bold;
    font-size: 16px;
  }

  .search-form {
    margin-bottom: 20px;
  }

  .pagination {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
  }

  .action-buttons {
    display: flex;
    gap: 4px;
  }

  .btn-action {
    padding: 4px 8px;
    font-size: 12px;
  }
}
</style>
