<template>
  <div class="question-detail" v-loading="loading">
    <div class="detail-header">
      <div class="header-left">
        <button class="back-btn" @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
          <span>返回题库</span>
        </button>
        <h1 class="page-title">题目详情</h1>
      </div>
      <button
        class="favorite-btn"
        :class="{ 'is-favorited': isFavorited }"
        @click="toggleFavorite"
      >
        <el-icon><Star /></el-icon>
        <span>{{ isFavorited ? '已收藏' : '收藏' }}</span>
      </button>
    </div>

    <div class="info-card">
      <div class="info-left">
        <span class="tag tag--category">分类：{{ categoryLabel }}</span>
        <span class="tag tag--difficulty" :class="`tag--${question.difficulty}`">
          难度：{{ difficultyLabel }}
        </span>
        <span v-if="question.direction" class="tag tag--direction">方向：{{ directionLabel }}</span>
      </div>
      <div class="info-right">
        <span class="stat-item">
          <el-icon><View /></el-icon>
          <span>{{ question.viewCount ?? 0 }}</span>
        </span>
        <span class="stat-item">
          <el-icon><Star /></el-icon>
          <span>{{ question.favoriteCount ?? 0 }}</span>
        </span>
        <span class="stat-item stat-item--date">
          {{ formatDate(question.createdAt) }}
        </span>
      </div>
    </div>

    <div class="content-card">
      <h2 class="card-title">题目描述</h2>
      <div class="card-body">{{ question.content }}</div>
    </div>

    <div class="content-card answer-card">
      <h2 class="card-title">参考答案</h2>
      <div class="card-body">{{ question.answer }}</div>
    </div>

    <div class="tags-section" v-if="question.tags && question.tags.length">
      <span
        v-for="tag in question.tags"
        :key="tag.id"
        class="tag-item"
      >
        {{ tag.name }}
      </span>
    </div>

    <div class="bottom-nav">
      <button class="nav-btn" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        <span>返回列表</span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Star, View } from '@element-plus/icons-vue'
import { getQuestionDetail, addFavorite, removeFavorite, checkFavorite } from '@/api/question'
import { difficultyMap, categoryMap, positionMap } from '@/utils/constants'

const router = useRouter()
const route = useRoute()

const question = ref({})
const loading = ref(false)
const isFavorited = ref(false)
const favoriteLoading = ref(false)


const difficultyLabel = computed(() => difficultyMap[question.value.difficulty] || question.value.difficulty)
const categoryLabel = computed(() => categoryMap[question.value.category] || question.value.category)
const directionLabel = computed(() => positionMap[question.value.direction] || question.value.direction)

function formatDate(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function goBack() {
  router.push('/questions/list')
}

async function loadQuestion() {
  const id = route.params.id
  if (!id) return
  loading.value = true
  try {
    const res = await getQuestionDetail(id)
    if (res.code === 200) {
      question.value = res.data
    }
  } catch (e) {
    ElMessage.error('加载题目详情失败')
  } finally {
    loading.value = false
  }
}

async function loadFavoriteStatus() {
  const id = route.params.id
  if (!id) return
  try {
    const res = await checkFavorite(id)
    if (res.code === 200) {
      isFavorited.value = res.data === true
    }
  } catch {
    // silent
  }
}

async function toggleFavorite() {
  const id = route.params.id
  if (!id || favoriteLoading.value) return
  favoriteLoading.value = true
  try {
    if (isFavorited.value) {
      await removeFavorite(id)
      isFavorited.value = false
      question.value.favoriteCount = Math.max(0, (question.value.favoriteCount || 1) - 1)
      ElMessage.success('已取消收藏')
    } else {
      await addFavorite(id)
      isFavorited.value = true
      question.value.favoriteCount = (question.value.favoriteCount || 0) + 1
      ElMessage.success('收藏成功')
    }
  } catch {
    ElMessage.error('操作失败，请稍后重试')
  } finally {
    favoriteLoading.value = false
  }
}

onMounted(() => {
  loadQuestion()
  loadFavoriteStatus()
})

// keep-alive 下路由参数变化时重新加载
watch(() => route.params.id, (newId) => {
  if (newId && route.name === 'QuestionDetail') {
    loadQuestion()
    loadFavoriteStatus()
  }
})

onActivated(() => {
  if (route.name !== 'QuestionDetail') return
  loadQuestion()
  loadFavoriteStatus()
})
</script>

<style lang="scss" scoped>
.question-detail {
  max-width: 960px;
  margin: 0 auto;
  padding: 24px 20px 48px;
  min-height: 100vh;
  background: $bg-surface;
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  background: #fff;
  border: 1px solid $border-color;
  border-radius: 6px;
  color: $text-secondary;
  font-size: 14px;
  cursor: pointer;
  transition: color $transition-normal, border-color $transition-normal;

  &:hover {
    color: $accent-color;
    border-color: $accent-color;
  }
}

.page-title {
  font-size: 22px;
  font-weight: 600;
  color: $text-primary;
  margin: 0;
}

.favorite-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 18px;
  background: #fff;
  border: 1px solid $border-color;
  border-radius: 6px;
  color: $text-secondary;
  font-size: 14px;
  cursor: pointer;
  transition: color $transition-normal, border-color $transition-normal, background $transition-normal;

  .el-icon {
    font-size: 16px;
  }

  &:hover {
    color: $accent-color;
    border-color: $accent-color;
  }

  &.is-favorited {
    color: #EAB308;
    border-color: #EAB308;
    background: #FEFCE8;

    &:hover {
      color: #CA8A04;
      border-color: #CA8A04;
    }
  }
}

.info-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding: 16px 20px;
  background: #fff;
  border: 1px solid $border-color;
  border-radius: 8px;
  margin-bottom: 20px;
}

.info-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.tag {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.5;
  border: 1px solid $border-color;
  background: $bg-muted;
  color: $text-secondary;

  &--category {
    color: $accent-color;
    border-color: $accent-color;
    background: #EFF6FF;
  }

  &--difficulty {
    color: #fff;
    border-color: transparent;
  }

  &--easy {
    background: #16A34A;
  }

  &--medium {
    background: #EAB308;
  }

  &--hard {
    background: #DC2626;
  }

  &--direction {
    color: $text-secondary;
  }
}

.info-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: $text-secondary;

  .el-icon {
    font-size: 15px;
  }

  &--date {
    color: #A1A1AA;
  }
}

.content-card {
  background: #fff;
  border: 1px solid $border-color;
  border-radius: 8px;
  padding: 24px;
  margin-bottom: 20px;
}

.card-title {
  font-size: 17px;
  font-weight: 600;
  color: $text-primary;
  margin: 0 0 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid $border-color;
}

.answer-card {
  .card-title {
    color: $accent-color;
  }

  .card-body {
    background: $bg-surface;
    padding: 16px;
    border-radius: 6px;
    border: 1px solid $border-color;
  }
}

.card-body {
  font-size: 15px;
  line-height: 1.8;
  color: $text-primary;
  white-space: pre-wrap;
  word-break: break-word;
}

.tags-section {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 32px;
}

.tag-item {
  display: inline-block;
  padding: 4px 14px;
  background: $bg-muted;
  border: 1px solid $border-color;
  border-radius: 4px;
  font-size: 13px;
  color: $text-secondary;
}

.bottom-nav {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding-top: 8px;
}

.nav-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 24px;
  background: #fff;
  border: 1px solid $border-color;
  border-radius: 6px;
  color: $text-secondary;
  font-size: 14px;
  cursor: pointer;
  transition: color $transition-normal, border-color $transition-normal;

  &:hover {
    color: $accent-color;
    border-color: $accent-color;
  }
}
</style>
