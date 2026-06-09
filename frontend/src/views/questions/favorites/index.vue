<template>
  <div class="favorites-page">
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">我的收藏</h1>
        <span class="total-count">共 {{ total }} 题</span>
      </div>
    </div>

    <div class="favorites-container" v-loading="loading">
      <!-- 筛选栏 -->
      <div class="filter-bar">
        <el-select v-model="filterCategory" placeholder="选择类别" clearable @change="handleFilter">
          <el-option label="全部类别" value="" />
          <el-option v-for="(label, value) in categoryMap" :key="value" :label="label" :value="value" />
        </el-select>
        <el-select v-model="filterDifficulty" placeholder="选择难度" clearable @change="handleFilter">
          <el-option label="全部难度" value="" />
          <el-option v-for="(label, value) in difficultyMap" :key="value" :label="label" :value="value" />
        </el-select>
      </div>

      <template v-if="favorites.length > 0">
        <el-table :data="favorites" class="favorites-table" :header-cell-style="headerCellStyle">
          <el-table-column prop="title" label="题目标题" min-width="280">
            <template #default="{ row }">
              <span class="title-link" @click="goToDetail(row.id)">{{ row.title }}</span>
            </template>
          </el-table-column>

          <el-table-column prop="category" label="类别" width="120">
            <template #default="{ row }">
              <el-tag class="category-tag" effect="plain">
                {{ getCategoryLabel(row.category) }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column prop="difficulty" label="难度" width="100">
            <template #default="{ row }">
              <el-tag :style="getDifficultyStyle(row.difficulty)" effect="plain" class="difficulty-tag">
                {{ getDifficultyLabel(row.difficulty) }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column prop="viewCount" label="浏览量" width="100" align="center">
            <template #default="{ row }">
              <div class="stat-cell">
                <el-icon class="stat-icon"><View /></el-icon>
                <span>{{ row.viewCount }}</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column prop="favoriteCount" label="收藏数" width="100" align="center">
            <template #default="{ row }">
              <div class="stat-cell">
                <el-icon class="stat-icon"><Star /></el-icon>
                <span>{{ row.favoriteCount }}</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="200" align="center">
            <template #default="{ row }">
              <div class="action-buttons">
                <el-button class="btn-view" @click="goToDetail(row.id)">
                  查看详情
                </el-button>
                <el-button class="btn-cancel" @click="handleRemoveFavorite(row)">
                  取消收藏
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 30, 50]"
            :total="total"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @size-change="handleSizeChange"
            @current-change="handlePageChange"
          />
        </div>
      </template>

      <template v-else-if="!loading">
        <div class="empty-state">
          <div class="empty-icon">
            <el-icon :size="64" color="#C0C4CC"><FolderOpened /></el-icon>
          </div>
          <h3 class="empty-title">暂无收藏题目</h3>
          <p class="empty-description">可以去题库中心收藏感兴趣的题目</p>
          <el-button class="btn-go-list" @click="goToQuestionList">
            去题库中心
          </el-button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { View, Star, FolderOpened } from '@element-plus/icons-vue'
import { getFavorites, removeFavorite } from '@/api/question'
import { difficultyMap } from '@/utils/constants'

const router = useRouter()

const favorites = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 筛选条件
const filterCategory = ref('')
const filterDifficulty = ref('')

const categoryMap = {
  java_basic: 'Java基础',
  spring: 'Spring框架',
  database: '数据库',
  frontend: '前端',
  devops: '运维部署',
  architecture: '系统架构'
}


const difficultyColorMap = {
  easy: '#16A34A',
  medium: '#EAB308',
  hard: '#DC2626'
}

const headerCellStyle = {
  backgroundColor: '#FAFAFA',
  color: '#18181B',
  fontWeight: '600',
  borderBottom: '1px solid #E4E4E7'
}

function getCategoryLabel(category) {
  return categoryMap[category] || category
}

function getDifficultyLabel(difficulty) {
  return difficultyMap[difficulty] || difficulty
}

function getDifficultyStyle(difficulty) {
  const color = difficultyColorMap[difficulty] || '#909399'
  return {
    color: color,
    borderColor: color,
    backgroundColor: 'transparent'
  }
}

async function loadFavorites() {
  loading.value = true
  try {
    const res = await getFavorites({
      page: currentPage.value,
      size: pageSize.value,
      category: filterCategory.value,
      difficulty: filterDifficulty.value
    })
    if (res.code === 200) {
      favorites.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch (error) {
    console.error('获取收藏列表失败:', error)
    ElMessage.error('获取收藏列表失败')
  } finally {
    loading.value = false
  }
}

function handlePageChange(page) {
  currentPage.value = page
  loadFavorites()
}

function handleSizeChange(size) {
  pageSize.value = size
  currentPage.value = 1
  loadFavorites()
}

function handleFilter() {
  currentPage.value = 1
  loadFavorites()
}

function goToDetail(id) {
  router.push(`/questions/detail/${id}`)
}

function goToQuestionList() {
  router.push('/questions/list')
}

async function handleRemoveFavorite(row) {
  try {
    await ElMessageBox.confirm(
      `确定要取消收藏「${row.title}」吗？`,
      '取消收藏',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    const res = await removeFavorite(row.id)
    if (res.code === 200) {
      ElMessage.success('已取消收藏')
      if (favorites.value.length === 1 && currentPage.value > 1) {
        currentPage.value--
      }
      loadFavorites()
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('取消收藏失败:', error)
      ElMessage.error('取消收藏失败')
    }
  }
}

onMounted(() => {
  loadFavorites()
})
</script>

<style lang="scss" scoped>
.favorites-page {
  padding: 24px;
  min-height: 100%;
}

.page-header {
  margin-bottom: 24px;
}

.header-content {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: $text-primary;
  margin: 0;
}

.total-count {
  font-size: 14px;
  color: $text-secondary;
}

.favorites-container {
  background-color: #fff;
  border: 1px solid $border-color;
  border-radius: 4px;
  padding: 20px;
  min-height: 400px;
}

.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  padding-bottom: 20px;
  border-bottom: 1px solid $border-color;

  .el-select {
    width: 180px;
  }
}

.favorites-table {
  width: 100%;
}

.title-link {
  color: $accent-color;
  cursor: pointer;
  transition: color $transition-fast;
  font-weight: 500;

  &:hover {
    color: $accent-hover;
  }
}

.category-tag {
  border-color: $border-color;
  color: $text-secondary;
  background-color: $bg-surface;
}

.difficulty-tag {
  font-weight: 500;
  border-width: 1px;
  border-style: solid;
}

.stat-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: $text-secondary;
}

.stat-icon {
  font-size: 16px;
}

.action-buttons {
  display: flex;
  gap: 8px;
  justify-content: center;
}

.btn-view {
  border-color: $accent-color;
  color: $accent-color;
  background-color: transparent;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    background-color: $accent-color;
    color: #fff;
    border-color: $accent-color;
  }
}

.btn-cancel {
  border-color: $color-destructive;
  color: $color-destructive;
  background-color: transparent;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    background-color: $color-destructive;
    color: #fff;
    border-color: $color-destructive;
  }
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  padding-top: 20px;
  border-top: 1px solid $border-color;
  margin-top: 20px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
}

.empty-icon {
  margin-bottom: 24px;
}

.empty-title {
  font-size: 18px;
  font-weight: 500;
  color: $text-primary;
  margin: 0 0 8px 0;
}

.empty-description {
  font-size: 14px;
  color: $text-secondary;
  margin: 0 0 24px 0;
}

.btn-go-list {
  background-color: $accent-color;
  border-color: $accent-color;
  color: #fff;
  cursor: pointer;
  transition: all $transition-fast;
  padding: 10px 24px;

  &:hover {
    background-color: $accent-hover;
    border-color: $accent-hover;
  }
}
</style>
