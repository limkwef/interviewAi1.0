<template>
  <div class="mistake-detail" v-loading="loading">
    <div class="detail-header">
      <div class="header-left">
        <button class="back-btn" @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
          <span>返回错题列表</span>
        </button>
        <h1 class="page-title">错题详情</h1>
      </div>
      <div class="header-actions">
        <el-button class="btn-action btn-action--review" @click="handleReview">
          <el-icon><Refresh /></el-icon>
          重做
        </el-button>
        <el-button
          v-if="mistakeInfo.status === 0"
          class="btn-action btn-action--master"
          @click="handleMaster"
        >
          <el-icon><CircleCheck /></el-icon>
          标记已掌握
        </el-button>
        <el-button
          v-else
          class="btn-action btn-action--reset"
          @click="handleReset"
        >
          <el-icon><RefreshLeft /></el-icon>
          重置为待复习
        </el-button>
        <el-button class="btn-action btn-action--delete" @click="handleDelete">
          <el-icon><Delete /></el-icon>
          移出错题本
        </el-button>
      </div>
    </div>

    <div class="info-card">
      <div class="info-left">
        <span class="tag tag--category">{{ categoryLabel }}</span>
        <span class="tag tag--difficulty" :class="`tag--${questionInfo.difficulty}`">
          {{ difficultyLabel }}
        </span>
      </div>
      <div class="info-right">
        <span class="stat-item">
          <span class="stat-label">错误次数</span>
          <span class="stat-value stat-value--warning">{{ mistakeInfo.mistakeCount }}次</span>
        </span>
        <span class="stat-item">
          <span class="stat-label">首次错误</span>
          <span class="stat-value">{{ formatDate(mistakeInfo.firstMistakeTime) }}</span>
        </span>
        <span class="stat-item">
          <span class="stat-label">最近错误</span>
          <span class="stat-value">{{ formatDate(mistakeInfo.lastMistakeTime) }}</span>
        </span>
        <el-tag
          :type="mistakeInfo.status === 0 ? 'warning' : 'success'"
          effect="light"
        >
          {{ mistakeInfo.status === 0 ? '待复习' : '已掌握' }}
        </el-tag>
      </div>
    </div>

    <div class="content-card">
      <h2 class="card-title">题目描述</h2>
      <div class="card-body">{{ questionInfo.content }}</div>
    </div>

    <div class="content-card history-card">
      <h2 class="card-title">
        <span>错误记录</span>
        <span class="record-count">共 {{ answerDetails.length }} 次</span>
      </h2>
      <div class="timeline">
        <div
          v-for="(detail, index) in answerDetails"
          :key="detail.id"
          class="timeline-item"
        >
          <div class="timeline-dot" :class="{ 'timeline-dot--latest': index === 0 }"></div>
          <div class="timeline-content">
            <div class="timeline-header">
              <span class="timeline-time">
                <el-icon><Clock /></el-icon>
                {{ formatDateTime(detail.createdAt) }}
              </span>
              <el-tag v-if="detail.interviewId" size="small" type="info">
                来自面试 #{{ detail.interviewId }}
              </el-tag>
              <el-tag v-else size="small" type="info">手动添加</el-tag>
            </div>
            <div class="answer-box">
              <div class="answer-label">我的回答</div>
              <div class="answer-content">{{ detail.userAnswer || '未作答' }}</div>
            </div>
            <div v-if="detail.aiComment" class="ai-comment">
              <div class="comment-label">
                <el-icon><ChatDotRound /></el-icon>
                AI点评
              </div>
              <div class="comment-content">{{ detail.aiComment }}</div>
            </div>
          </div>
        </div>
      </div>
      <div v-if="answerDetails.length === 0" class="empty-timeline">
        <el-icon :size="48" color="#C0C4CC"><Document /></el-icon>
        <p>暂无错误记录</p>
      </div>
    </div>

    <div class="content-card answer-card">
      <h2 class="card-title">正确答案与解析</h2>
      <div class="card-body">{{ questionInfo.answer }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Refresh, CircleCheck, RefreshLeft, Delete, Clock, ChatDotRound, Document } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { difficultyMap, categoryMap } from '@/utils/constants'

const router = useRouter()
const route = useRoute()

const mistakeInfo = ref({})
const questionInfo = ref({})
const answerDetails = ref([])
const loading = ref(false)


const difficultyLabel = computed(() => difficultyMap[questionInfo.value.difficulty] || questionInfo.value.difficulty)
const categoryLabel = computed(() => categoryMap[questionInfo.value.category] || questionInfo.value.category)

function formatDate(dateStr) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function formatDateTime(dateStr) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${day} ${hours}:${minutes}`
}

function goBack() {
  router.push('/questions/mistakes')
}

async function loadMistakeDetail() {
  const id = route.params.id
  if (!id) return
  loading.value = true
  try {
    const res = await request.get(`/mistakes/${id}/details`)
    if (res.code === 200) {
      mistakeInfo.value = res.data.mistake || {}
      questionInfo.value = res.data.question || {}
      answerDetails.value = res.data.details || []
    }
  } catch (e) {
    console.error('加载错题详情失败:', e)
    ElMessage.error('加载错题详情失败')
  } finally {
    loading.value = false
  }
}

function handleReview() {
  router.push(`/questions/mistakes/review?ids=${route.params.id}`)
}

async function handleMaster() {
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
    const res = await request.put(`/mistakes/${route.params.id}/master`)
    if (res.code === 200) {
      ElMessage.success('已标记为掌握')
      mistakeInfo.value.status = 1
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

async function handleReset() {
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
    const res = await request.put(`/mistakes/${route.params.id}/reset`)
    if (res.code === 200) {
      ElMessage.success('已重置为待复习')
      mistakeInfo.value.status = 0
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm(
      '确定要将该题移出错题本吗？此操作不可撤销。',
      '移出错题本',
      {
        confirmButtonText: '确定移出',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    const res = await request.delete(`/mistakes/${route.params.id}`)
    if (res.code === 200) {
      ElMessage.success('已移出错题本')
      router.push('/questions/mistakes')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

onMounted(() => {
  loadMistakeDetail()
})
</script>

<style lang="scss" scoped>
.mistake-detail {
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
  flex-wrap: wrap;
  gap: 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
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

.btn-action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  font-size: 14px;
  cursor: pointer;
  transition: all $transition-normal;
  border-radius: 6px;

  &--review {
    background: #fff;
    border: 1px solid #8B5CF6;
    color: #8B5CF6;

    &:hover {
      background: #8B5CF6;
      color: #fff;
    }
  }

  &--master {
    background: #fff;
    border: 1px solid #16A34A;
    color: #16A34A;

    &:hover {
      background: #16A34A;
      color: #fff;
    }
  }

  &--reset {
    background: #fff;
    border: 1px solid #EAB308;
    color: #EAB308;

    &:hover {
      background: #EAB308;
      color: #fff;
    }
  }

  &--delete {
    background: #fff;
    border: 1px solid #DC2626;
    color: #DC2626;

    &:hover {
      background: #DC2626;
      color: #fff;
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
}

.info-right {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-label {
  font-size: 12px;
  color: #A1A1AA;
}

.stat-value {
  font-size: 14px;
  font-weight: 500;
  color: $text-primary;

  &--warning {
    color: #EAB308;
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
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.record-count {
  font-size: 14px;
  font-weight: 400;
  color: $text-secondary;
}

.card-body {
  font-size: 15px;
  line-height: 1.8;
  color: $text-primary;
  white-space: pre-wrap;
  word-break: break-word;
}

.timeline {
  position: relative;
  padding-left: 24px;

  &::before {
    content: '';
    position: absolute;
    left: 8px;
    top: 0;
    bottom: 0;
    width: 2px;
    background: $border-color;
  }
}

.timeline-item {
  position: relative;
  margin-bottom: 24px;

  &:last-child {
    margin-bottom: 0;
  }
}

.timeline-dot {
  position: absolute;
  left: -20px;
  top: 8px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #fff;
  border: 2px solid $border-color;

  &--latest {
    border-color: $accent-color;
    background: $accent-color;
  }
}

.timeline-content {
  background: $bg-surface;
  border: 1px solid $border-color;
  border-radius: 8px;
  padding: 16px;
}

.timeline-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.timeline-time {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: $text-secondary;
}

.answer-box {
  background: #fff;
  border: 1px solid $border-color;
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 12px;
}

.answer-label {
  font-size: 12px;
  font-weight: 600;
  color: $text-secondary;
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.answer-content {
  font-size: 14px;
  line-height: 1.6;
  color: $text-primary;
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-comment {
  background: #EFF6FF;
  border: 1px solid #BFDBFE;
  border-radius: 6px;
  padding: 12px;
}

.comment-label {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 600;
  color: $accent-color;
  margin-bottom: 8px;
}

.comment-content {
  font-size: 14px;
  line-height: 1.6;
  color: $text-primary;
}

.empty-timeline {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 0;
  color: #C0C4CC;

  p {
    margin: 12px 0 0;
    font-size: 14px;
  }
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

@media (max-width: 768px) {
  .mistake-detail {
    padding: 16px;
  }

  .detail-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .header-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .info-card {
    flex-direction: column;
    align-items: flex-start;
  }

  .info-right {
    width: 100%;
    justify-content: space-between;
  }
}
</style>
