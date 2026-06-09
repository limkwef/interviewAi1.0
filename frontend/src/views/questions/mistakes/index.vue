<template>
  <div class="mistakes-page">
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">我的错题本</h1>
        <p class="page-subtitle">记录薄弱点，针对性复习巩固</p>
      </div>
      <el-button class="btn-review" @click="goToReview">
        <el-icon><Refresh /></el-icon>
        错题重做
      </el-button>
    </div>

    <div class="stats-cards">
      <div class="stat-card stat-card--pending">
        <div class="stat-card__icon">
          <el-icon :size="24"><Warning /></el-icon>
        </div>
        <div class="stat-card__content">
          <span class="stat-card__value">{{ stats.pendingReview }}</span>
          <span class="stat-card__label">待复习</span>
        </div>
      </div>
      <div class="stat-card stat-card--mastered">
        <div class="stat-card__icon">
          <el-icon :size="24"><CircleCheck /></el-icon>
        </div>
        <div class="stat-card__content">
          <span class="stat-card__value">{{ stats.mastered }}</span>
          <span class="stat-card__label">已掌握</span>
        </div>
      </div>
      <div class="stat-card stat-card--total">
        <div class="stat-card__icon">
          <el-icon :size="24"><Document /></el-icon>
        </div>
        <div class="stat-card__content">
          <span class="stat-card__value">{{ stats.total }}</span>
          <span class="stat-card__label">总错题数</span>
        </div>
      </div>
      <div class="stat-card stat-card--rate">
        <div class="stat-card__icon">
          <el-icon :size="24"><TrendCharts /></el-icon>
        </div>
        <div class="stat-card__content">
          <span class="stat-card__value">{{ stats.masteredRate }}</span>
          <span class="stat-card__label">掌握率</span>
        </div>
      </div>
    </div>

    <div class="filter-bar">
      <div class="filter-bar__controls">
        <el-select
          v-model="filters.category"
          placeholder="知识点筛选"
          clearable
          class="filter-select"
        >
          <el-option
            v-for="item in categoryOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-select
          v-model="filters.difficulty"
          placeholder="难度筛选"
          clearable
          class="filter-select"
        >
          <el-option
            v-for="item in difficultyOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-select
          v-model="filters.status"
          placeholder="状态筛选"
          clearable
          class="filter-select"
        >
          <el-option label="待复习" :value="0" />
          <el-option label="已掌握" :value="1" />
        </el-select>
        <el-input
          v-model="filters.keyword"
          placeholder="搜索题目关键词..."
          :prefix-icon="Search"
          clearable
          class="filter-input"
          @keyup.enter="handleSearch"
        />
      </div>
      <div class="filter-bar__actions">
        <el-button type="primary" :icon="Search" @click="handleSearch">
          筛选
        </el-button>
        <el-button :icon="RefreshRight" @click="handleReset">
          重置
        </el-button>
      </div>
    </div>

    <div class="table-container">
      <el-table
        v-loading="loading"
        :data="mistakeList"
        style="width: 100%"
        :header-cell-style="headerCellStyle"
        :row-style="{ cursor: 'pointer' }"
        empty-text="暂无错题记录"
        @row-click="handleRowClick"
      >
        <el-table-column prop="questionTitle" label="题目标题" min-width="280">
          <template #default="{ row }">
            <div class="title-cell">
              <span class="title-link" @click.stop="goToDetail(row.id)">
                {{ row.questionTitle }}
              </span>
              <div class="title-meta">
                <el-tag class="category-tag" effect="plain" size="small">
                  {{ categoryLabelMap[row.category] || row.category }}
                </el-tag>
                <el-tag
                  class="difficulty-tag"
                  :class="`difficulty-tag--${row.difficulty}`"
                  effect="plain"
                  size="small"
                >
                  {{ difficultyLabelMap[row.difficulty] || row.difficulty }}
                </el-tag>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="错误次数" width="100" align="center">
          <template #default="{ row }">
            <span class="count-badge" :class="{ 'count-badge--high': row.mistakeCount >= 3 }">
              {{ row.mistakeCount }}次
            </span>
          </template>
        </el-table-column>

        <el-table-column label="最近错误" width="160" align="center">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.lastMistakeTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag
              :type="row.status === 0 ? 'warning' : 'success'"
              effect="light"
              class="status-tag"
            >
              {{ row.status === 0 ? '待复习' : '已掌握' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button class="btn-action btn-action--view" @click.stop="goToDetail(row.id)">
                查看
              </el-button>
              <el-button class="btn-action btn-action--review" @click.stop="handleReviewSingle(row)">
                重做
              </el-button>
              <el-button
                v-if="row.status === 0"
                class="btn-action btn-action--master"
                @click.stop="handleMaster(row)"
              >
                已掌握
              </el-button>
              <el-button
                v-else
                class="btn-action btn-action--reset"
                @click.stop="handleResetStatus(row)"
              >
                重置
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-if="pagination.total > 0" class="pagination-wrapper">
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, RefreshRight, Refresh, Warning, CircleCheck, Document, TrendCharts } from '@element-plus/icons-vue'
import request from '@/utils/request'

const router = useRouter()

const categoryOptions = [
  { value: 'java_basic', label: 'Java基础' },
  { value: 'spring', label: 'Spring框架' },
  { value: 'database', label: '数据库' },
  { value: 'frontend', label: '前端' },
  { value: 'devops', label: '运维部署' },
  { value: 'architecture', label: '系统架构' }
]

const difficultyOptions = [
  { value: 'easy', label: '简单' },
  { value: 'medium', label: '中等' },
  { value: 'hard', label: '困难' }
]

const categoryLabelMap = Object.fromEntries(categoryOptions.map(o => [o.value, o.label]))
const difficultyLabelMap = Object.fromEntries(difficultyOptions.map(o => [o.value, o.label]))

const filters = reactive({
  category: '',
  difficulty: '',
  status: '',
  keyword: ''
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const stats = reactive({
  total: 0,
  pendingReview: 0,
  mastered: 0,
  masteredRate: '0%'
})

const mistakeList = ref([])
const loading = ref(false)

const headerCellStyle = {
  backgroundColor: '#FAFAFA',
  color: '#18181B',
  fontWeight: '600',
  fontSize: '14px'
}

function formatTime(timeStr) {
  if (!timeStr) return '-'
  const date = new Date(timeStr)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${month}-${day} ${hours}:${minutes}`
}

async function fetchMistakes() {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      size: pagination.size
    }
    if (filters.category) params.category = filters.category
    if (filters.difficulty) params.difficulty = filters.difficulty
    if (filters.status !== '') params.status = filters.status
    if (filters.keyword) params.keyword = filters.keyword

    const res = await request.get('/mistakes', { params })
    if (res.code === 200) {
      mistakeList.value = res.data.records || []
      pagination.total = res.data.total || 0
    }
  } catch (err) {
    console.error('获取错题列表失败:', err)
    ElMessage.error('获取错题列表失败')
  } finally {
    loading.value = false
  }
}

async function fetchStats() {
  try {
    const res = await request.get('/mistakes/stats')
    if (res.code === 200) {
      Object.assign(stats, res.data)
    }
  } catch (err) {
    console.error('获取错题统计失败:', err)
  }
}

function handleSearch() {
  pagination.page = 1
  fetchMistakes()
}

function handleReset() {
  filters.category = ''
  filters.difficulty = ''
  filters.status = ''
  filters.keyword = ''
  pagination.page = 1
  fetchMistakes()
}

function handlePageChange(page) {
  pagination.page = page
  fetchMistakes()
}

function handleSizeChange(size) {
  pagination.size = size
  pagination.page = 1
  fetchMistakes()
}

function goToDetail(id) {
  router.push(`/questions/mistakes/${id}`)
}

function goToReview() {
  router.push('/questions/mistakes/review')
}

function handleRowClick(row) {
  goToDetail(row.id)
}

function handleReviewSingle(row) {
  router.push(`/questions/mistakes/review?ids=${row.id}`)
}

async function handleMaster(row) {
  try {
    await ElMessageBox.confirm(
      '确定要将该题标记为已掌握吗？',
      '标记已掌握',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'success'
      }
    )
    const res = await request.put(`/mistakes/${row.id}/master`)
    if (res.code === 200) {
      ElMessage.success('已标记为掌握')
      fetchMistakes()
      fetchStats()
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

async function handleResetStatus(row) {
  try {
    await ElMessageBox.confirm(
      '确定要将该题重置为待复习状态吗？',
      '重置状态',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    const res = await request.put(`/mistakes/${row.id}/reset`)
    if (res.code === 200) {
      ElMessage.success('已重置为待复习')
      fetchMistakes()
      fetchStats()
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

onMounted(() => {
  fetchMistakes()
  fetchStats()
})
</script>

<style lang="scss" scoped>
.mistakes-page {
  padding: 24px;
  background-color: $bg-surface;
  min-height: 100%;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 24px;
}

.header-content {
  display: flex;
  flex-direction: column;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: $text-primary;
  line-height: 1.4;
}

.page-subtitle {
  margin: 4px 0 0;
  font-size: 14px;
  color: $text-secondary;
}

.btn-review {
  background-color: $accent-color;
  border-color: $accent-color;
  color: #fff;
  cursor: pointer;
  transition: all $transition-fast;
  display: flex;
  align-items: center;
  gap: 6px;

  &:hover {
    background-color: $accent-hover;
    border-color: $accent-hover;
  }
}

.stats-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: #fff;
  border: 1px solid $border-color;
  border-radius: 8px;
  transition: all $transition-fast;

  &:hover {
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  }

  &__icon {
    width: 48px;
    height: 48px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 12px;
    flex-shrink: 0;
  }

  &__content {
    display: flex;
    flex-direction: column;
  }

  &__value {
    font-size: 28px;
    font-weight: 700;
    line-height: 1.2;
  }

  &__label {
    font-size: 14px;
    color: $text-secondary;
    margin-top: 4px;
  }

  &--pending {
    .stat-card__icon {
      background: rgba(234, 179, 8, 0.1);
      color: #EAB308;
    }
    .stat-card__value {
      color: #EAB308;
    }
  }

  &--mastered {
    .stat-card__icon {
      background: rgba(22, 163, 74, 0.1);
      color: #16A34A;
    }
    .stat-card__value {
      color: #16A34A;
    }
  }

  &--total {
    .stat-card__icon {
      background: rgba(37, 99, 235, 0.1);
      color: $accent-color;
    }
    .stat-card__value {
      color: $accent-color;
    }
  }

  &--rate {
    .stat-card__icon {
      background: rgba(139, 92, 246, 0.1);
      color: #8B5CF6;
    }
    .stat-card__value {
      color: #8B5CF6;
    }
  }
}

.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 24px;
  background-color: #fff;
  border: 1px solid $border-color;
  border-radius: 8px;
  margin-bottom: 20px;
  flex-wrap: wrap;

  &__controls {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
  }
}

.filter-select {
  width: 140px;
}

.filter-input {
  width: 220px;
}

.table-container {
  background-color: #fff;
  border: 1px solid $border-color;
  border-radius: 8px;
  overflow: hidden;
}

.title-cell {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.title-link {
  color: $accent-color;
  font-weight: 500;
  cursor: pointer;
  transition: color $transition-fast;

  &:hover {
    color: $accent-hover;
    text-decoration: underline;
  }
}

.title-meta {
  display: flex;
  gap: 6px;
}

.category-tag {
  background-color: $bg-muted;
  border-color: $border-color;
  color: $text-secondary;
  font-size: 12px;
}

.difficulty-tag {
  font-size: 12px;
  font-weight: 500;
  border: none;

  &--easy {
    background-color: rgba(22, 163, 74, 0.1);
    color: #16A34A;
  }

  &--medium {
    background-color: rgba(234, 179, 8, 0.1);
    color: #ca8a04;
  }

  &--hard {
    background-color: rgba(220, 38, 38, 0.1);
    color: #DC2626;
  }
}

.count-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 40px;
  padding: 2px 8px;
  background: rgba(234, 179, 8, 0.1);
  color: #ca8a04;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 500;

  &--high {
    background: rgba(220, 38, 38, 0.1);
    color: #DC2626;
  }
}

.time-text {
  font-size: 13px;
  color: $text-secondary;
}

.status-tag {
  font-size: 13px;
}

.action-buttons {
  display: flex;
  gap: 4px;
  justify-content: center;
  flex-wrap: nowrap;
}

.btn-action {
  padding: 3px 8px;
  font-size: 12px;
  cursor: pointer;
  transition: all $transition-fast;
  border-radius: 4px;

  &--view {
    background: transparent;
    border: 1px solid $accent-color;
    color: $accent-color;

    &:hover {
      background: $accent-color;
      color: #fff;
    }
  }

  &--review {
    background: transparent;
    border: 1px solid #8B5CF6;
    color: #8B5CF6;

    &:hover {
      background: #8B5CF6;
      color: #fff;
    }
  }

  &--master {
    background: transparent;
    border: 1px solid #16A34A;
    color: #16A34A;

    &:hover {
      background: #16A34A;
      color: #fff;
    }
  }

  &--reset {
    background: transparent;
    border: 1px solid #EAB308;
    color: #EAB308;

    &:hover {
      background: #EAB308;
      color: #fff;
    }
  }
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  padding: 20px 0;
}

:deep(.el-table) {
  --el-table-border-color: #{$border-color};
  --el-table-header-bg-color: #fafafa;

  th.el-table__cell {
    font-weight: 600;
  }
}

:deep(.el-button--primary) {
  --el-button-bg-color: #{$accent-color};
  --el-button-border-color: #{$accent-color};
  --el-button-hover-bg-color: #{$accent-hover};
  --el-button-hover-border-color: #{$accent-hover};
  font-weight: 500;
  cursor: pointer;
}

:deep(.el-button--default) {
  cursor: pointer;

  &:hover {
    border-color: $accent-color;
    color: $accent-color;
  }
}

:deep(.el-pagination.is-background .el-pager li:not(.is-disabled).is-active) {
  background-color: $accent-color;
}

:deep(.el-select) {
  .el-input__wrapper {
    border-radius: 6px;
  }
}

:deep(.el-input__wrapper) {
  border-radius: 6px;
}

@media (max-width: 1024px) {
  .stats-cards {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .mistakes-page {
    padding: 16px;
  }

  .page-header {
    flex-direction: column;
    gap: 16px;
  }

  .page-title {
    font-size: 20px;
  }

  .stats-cards {
    grid-template-columns: 1fr 1fr;
  }

  .filter-bar {
    flex-direction: column;
    align-items: stretch;
    padding: 16px;
    gap: 12px;

    &__controls {
      flex-direction: column;
    }

    &__actions {
      justify-content: flex-end;
    }
  }

  .filter-select,
  .filter-input {
    width: 100%;
  }

  .action-buttons {
    flex-direction: column;
    gap: 4px;
  }

  .pagination-wrapper {
    overflow-x: auto;
  }
}
</style>
