<template>
  <div class="logs-page">
    <el-card>
      <template #header>
      <div class="card-header">
        <span>操作日志</span>
      </div>
    </template>
      <el-form :inline="true" :model="queryParams" class="search-form">
        <el-form-item label="操作类型">
          <el-select v-model="queryParams.action" placeholder="全部" clearable>
            <el-option label="添加题目" value="CREATE_QUESTION" />
            <el-option label="更新题目" value="UPDATE_QUESTION" />
            <el-option label="删除题目" value="DELETE_QUESTION" />
            <el-option label="批量导入" value="BATCH_IMPORT" />
            <el-option label="更新用户" value="UPDATE_USER" />
            <el-option label="启用用户" value="ENABLE_USER" />
            <el-option label="禁用用户" value="DISABLE_USER" />
            <el-option label="重置密码" value="RESET_PASSWORD" />
            <el-option label="删除用户" value="DELETE_USER" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            :shortcuts="dateShortcuts"
          />
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
        <el-table-column prop="adminName" label="管理员" width="120" />
        <el-table-column prop="action" label="操作类型" width="140">
          <template #default="{ row }">
            <el-tag size="small">{{ getActionLabel(row.action) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetType" label="目标类型" width="100" />
        <el-table-column prop="targetId" label="目标ID" width="100" />
        <el-table-column prop="detail" label="详情" min-width="200" />
        <el-table-column prop="ipAddress" label="IP地址" width="140" />
        <el-table-column prop="createdAt" label="操作时间" width="180" />
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
import { ElMessage } from 'element-plus'
import { getLogList } from '@/api/admin'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const dateRange = ref(null)
const queryParams = reactive({
  page: 1,
  size: 10,
  action: ''
})

const dateShortcuts = [
  {
    text: '最近一周',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setTime(start.getTime() - 7 * 24 * 3600 * 1000)
      return [start, end]
    }
  },
  {
    text: '最近一个月',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setMonth(start.getMonth() - 1)
      return [start, end]
    }
  }
]

const actionLabels = {
  CREATE_QUESTION: '添加题目',
  UPDATE_QUESTION: '更新题目',
  DELETE_QUESTION: '删除题目',
  BATCH_IMPORT: '批量导入',
  UPDATE_USER: '更新用户',
  ENABLE_USER: '启用用户',
  DISABLE_USER: '禁用用户',
  RESET_PASSWORD: '重置密码',
  DELETE_USER: '删除用户'
}

onMounted(() => {
  handleSearch()
})

function getActionLabel(action) {
  return actionLabels[action] || action
}

async function handleSearch() {
  loading.value = true
  try {
    const params = { ...queryParams }
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    const res = await getLogList(params)
    tableData.value = res.data.list
    total.value = res.data.total
  } catch (err) {
    ElMessage.error('获取日志列表失败')
  } finally {
    loading.value = false
  }
}

function handleReset() {
  queryParams.page = 1
  queryParams.action = ''
  dateRange.value = null
  handleSearch()
}
</script>

<style lang="scss" scoped>
.logs-page {
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
}
</style>
