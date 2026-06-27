<template>
  <div class="page-container question-list-page">
    <div class="page-header">
      <h1 class="page-title">题库中心</h1>
      <p class="page-subtitle">浏览并收藏你感兴趣的面试题目</p>
    </div>

    <div class="filter-bar">
      <div class="filter-bar__controls">
        <el-select
          v-model="filters.category"
          placeholder="类别筛选"
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
        :data="questionList"
        style="width: 100%"
        :header-cell-style="headerCellStyle"
        :row-style="{ cursor: 'pointer' }"
        empty-text="暂无题目"
        @row-click="handleRowClick"
      >
        <el-table-column prop="title" label="题目标题" min-width="320">
          <template #default="{ row }">
            <span class="title-link" @click.stop="goToDetail(row.id)">
              {{ row.title }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="类别" width="130">
          <template #default="{ row }">
            <el-tag class="category-tag" effect="plain">
              {{ categoryLabelMap[row.category] || row.category }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="难度" width="100">
          <template #default="{ row }">
            <el-tag
              class="difficulty-tag"
              :class="`difficulty-tag--${row.difficulty}`"
              effect="plain"
            >
              {{ difficultyLabelMap[row.difficulty] || row.difficulty }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="浏览量" width="100" align="center">
          <template #default="{ row }">
            <span class="metric-cell">
              <el-icon class="metric-icon"><View /></el-icon>
              {{ row.viewCount ?? 0 }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="收藏数" width="100" align="center">
          <template #default="{ row }">
            <span class="metric-cell">
              <el-icon class="metric-icon"><Star /></el-icon>
              {{ row.favoriteCount ?? 0 }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              :type="row.isFavorited ? 'warning' : 'default'"
              :icon="row.isFavorited ? StarFilled : Star"
              link
              class="fav-btn"
              @click.stop="handleToggleFavorite(row)"
            >
              {{ row.isFavorited ? '已收藏' : '收藏' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-if="pagination.total > 0" class="pagination-wrap pagination-wrapper">
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
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, RefreshRight, View, Star, StarFilled } from '@element-plus/icons-vue'
import { getQuestionList, getTags, addFavorite, removeFavorite } from '@/api/question'
import { categoryMap } from '@/utils/constants'

const router = useRouter()

const categoryOptions = computed(() =>
  Object.entries(categoryMap).map(([value, label]) => ({ value, label }))
)

const difficultyOptions = [
  { value: 'easy', label: '简单' },
  { value: 'medium', label: '中等' },
  { value: 'hard', label: '困难' }
]

const categoryLabelMap = categoryMap
const difficultyLabelMap = Object.fromEntries(difficultyOptions.map(o => [o.value, o.label]))

const filters = reactive({
  category: '',
  difficulty: '',
  keyword: ''
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const questionList = ref([])
const loading = ref(false)
const tags = ref([])

const headerCellStyle = {
  backgroundColor: '#FAFAFA',
  color: '#18181B',
  fontWeight: '600',
  fontSize: '14px'
}

async function fetchQuestions() {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      size: pagination.size
    }
    if (filters.category) params.category = filters.category
    if (filters.difficulty) params.difficulty = filters.difficulty
    if (filters.keyword) params.keyword = filters.keyword

    const res = await getQuestionList(params)
    if (res.code === 200) {
      questionList.value = (res.data.records || []).map(item => ({
        ...item,
        isFavorited: item.isFavorited ?? false
      }))
      pagination.total = res.data.total || 0
    }
  } catch (err) {
    ElMessage.error('获取题目列表失败')
  } finally {
    loading.value = false
  }
}

async function fetchTags() {
  try {
    const res = await getTags()
    if (res.code === 200) {
      tags.value = res.data || []
    }
  } catch {
    // tags 加载失败不影响主流程
  }
}

function handleSearch() {
  pagination.page = 1
  fetchQuestions()
}

function handleReset() {
  filters.category = ''
  filters.difficulty = ''
  filters.keyword = ''
  pagination.page = 1
  fetchQuestions()
}

function handlePageChange(page) {
  pagination.page = page
  fetchQuestions()
}

function handleSizeChange(size) {
  pagination.size = size
  pagination.page = 1
  fetchQuestions()
}

function goToDetail(id) {
  router.push(`/questions/detail/${id}`)
}

function handleRowClick(row) {
  goToDetail(row.id)
}

async function handleToggleFavorite(row) {
  try {
    if (row.isFavorited) {
      await removeFavorite(row.id)
      row.isFavorited = false
      row.favoriteCount = Math.max(0, (row.favoriteCount || 1) - 1)
      ElMessage.success('已取消收藏')
    } else {
      await addFavorite(row.id)
      row.isFavorited = true
      row.favoriteCount = (row.favoriteCount || 0) + 1
      ElMessage.success('收藏成功')
    }
  } catch {
    ElMessage.error('操作失败，请稍后重试')
  }
}

onMounted(() => {
  fetchQuestions()
  fetchTags()
})
</script>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.question-list-page {
  min-height: 100%;
}

.page-subtitle {
  color: $text-secondary;
}

.filter-bar {
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
  width: 160px;
}

.filter-input {
  width: 240px;
}

.table-container {
  background-color: #fff;
  border: 1px solid $border-color;
  border-radius: 8px;
  overflow: hidden;
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

.category-tag {
  background-color: $bg-muted;
  border-color: $border-color;
  color: $text-secondary;
  font-size: 13px;
}

.difficulty-tag {
  font-size: 13px;
  font-weight: 500;
  border: none;

  &--easy {
    background-color: rgba(22, 163, 74, 0.1);
    color: $color-success;
  }

  &--medium {
    background-color: rgba(234, 179, 8, 0.1);
    color: #ca8a04;
  }

  &--hard {
    background-color: rgba(220, 38, 38, 0.1);
    color: $color-destructive;
  }
}

.metric-cell {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: $text-secondary;
  font-size: 14px;
}

.metric-icon {
  font-size: 15px;
  color: $text-tertiary;
}

.fav-btn {
  font-size: 13px;
  cursor: pointer;
  transition: color $transition-fast;
}

:deep(.el-table) {
  --el-table-border-color: #{$border-color};
  --el-table-header-bg-color: #{$bg-surface};

  th.el-table__cell {
    font-weight: 600;
  }
}

:deep(.el-button--primary) {
  --el-button-bg-color: #{$accent-color};
  --el-button-border-color: #{$accent-color};
  --el-button-hover-bg-color: #{$accent-hover};
  --el-button-hover-border-color: #{$accent-hover};
  --el-button-active-bg-color: #{$accent-hover};
  --el-button-active-border-color: #{$accent-hover};
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

@media (max-width: 768px) {
  .question-list-page {
    padding: 16px;
  }

  .page-title {
    font-size: 20px;
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

  .pagination-wrapper {
    overflow-x: auto;
  }
}
</style>
