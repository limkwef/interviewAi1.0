<template>
  <div class="review-page">
    <div class="review-header">
      <button class="back-btn" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        <span>返回错题本</span>
      </button>
      <h1 class="page-title">错题重做</h1>
    </div>

    <div v-if="phase === 'config'" class="config-section">
      <div class="config-card">
        <h2 class="config-title">练习配置</h2>
        <div class="config-form">
          <div class="form-item">
            <label class="form-label">知识点筛选</label>
            <el-select
              v-model="config.category"
              placeholder="全部知识点"
              clearable
              class="form-select"
            >
              <el-option
                v-for="item in categoryOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </div>
          <div class="form-item">
            <label class="form-label">难度筛选</label>
            <el-select
              v-model="config.difficulty"
              placeholder="全部难度"
              clearable
              class="form-select"
            >
              <el-option
                v-for="item in difficultyOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </div>
          <div class="form-item">
            <label class="form-label">题目数量</label>
            <el-input-number
              v-model="config.count"
              :min="1"
              :max="50"
              class="form-number"
            />
          </div>
          <el-button
            type="primary"
            class="btn-start"
            :loading="startLoading"
            @click="startReview"
          >
            开始练习
          </el-button>
        </div>
      </div>
    </div>

    <div v-else-if="phase === 'answering'" class="answering-section">
      <div class="progress-bar">
        <div class="progress-info">
          <span class="progress-text">进度</span>
          <span class="progress-count">{{ currentIndex + 1 }} / {{ questions.length }}</span>
        </div>
        <el-progress
          :percentage="((currentIndex + 1) / questions.length) * 100"
          :stroke-width="8"
          :show-text="false"
          color="#2563EB"
        />
      </div>

      <div class="question-card">
        <div class="question-header">
          <div class="question-tags">
            <el-tag class="category-tag" effect="plain" size="small">
              {{ categoryLabelMap[currentQuestion.category] || currentQuestion.category }}
            </el-tag>
            <el-tag
              class="difficulty-tag"
              :class="`difficulty-tag--${currentQuestion.difficulty}`"
              effect="plain"
              size="small"
            >
              {{ difficultyLabelMap[currentQuestion.difficulty] || currentQuestion.difficulty }}
            </el-tag>
          </div>
          <span class="question-count">
            错误次数: {{ currentQuestion.mistakeCount }}次
          </span>
        </div>
        <h2 class="question-title">{{ currentQuestion.title }}</h2>
        <div class="question-content">{{ currentQuestion.content }}</div>
      </div>

      <div class="answer-card">
        <h3 class="answer-title">你的回答</h3>
        <el-input
          v-model="userAnswer"
          type="textarea"
          :rows="6"
          placeholder="请输入你的答案..."
          class="answer-input"
        />
        <div class="answer-actions">
          <el-button
            type="primary"
            class="btn-submit"
            :loading="submitLoading"
            @click="submitAnswer"
          >
            提交答案
          </el-button>
        </div>
      </div>

      <div v-if="showFeedback" class="feedback-card" :class="{ 'feedback-card--correct': isCorrect, 'feedback-card--wrong': !isCorrect }">
        <div class="feedback-header">
          <el-icon v-if="isCorrect" :size="24" color="#16A34A"><CircleCheck /></el-icon>
          <el-icon v-else :size="24" color="#DC2626"><CircleClose /></el-icon>
          <span class="feedback-status">{{ isCorrect ? '回答正确！' : '回答有误' }}</span>
        </div>
        <div class="feedback-answer">
          <h4>参考答案</h4>
          <p>{{ currentQuestion.answer }}</p>
        </div>
        <div class="feedback-actions">
          <el-button class="btn-next" @click="nextQuestion">
            {{ currentIndex < questions.length - 1 ? '下一题' : '查看总结' }}
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
      </div>
    </div>

    <div v-else-if="phase === 'summary'" class="summary-section">
      <div class="summary-card">
        <div class="summary-icon">
          <el-icon :size="64" color="#2563EB"><Trophy /></el-icon>
        </div>
        <h2 class="summary-title">练习完成！</h2>
        <div class="summary-stats">
          <div class="summary-stat">
            <span class="summary-stat__value">{{ summary.total }}</span>
            <span class="summary-stat__label">总题数</span>
          </div>
          <div class="summary-stat">
            <span class="summary-stat__value summary-stat__value--correct">{{ summary.correct }}</span>
            <span class="summary-stat__label">答对</span>
          </div>
          <div class="summary-stat">
            <span class="summary-stat__value summary-stat__value--wrong">{{ summary.wrong }}</span>
            <span class="summary-stat__label">答错</span>
          </div>
          <div class="summary-stat">
            <span class="summary-stat__value summary-stat__value--rate">{{ summary.rate }}</span>
            <span class="summary-stat__label">正确率</span>
          </div>
        </div>

        <div v-if="summary.wrongQuestions.length > 0" class="wrong-list">
          <h3 class="wrong-title">仍需巩固的题目</h3>
          <div class="wrong-items">
            <div
              v-for="q in summary.wrongQuestions"
              :key="q.id"
              class="wrong-item"
              @click="goToDetail(q.id)"
            >
              <span class="wrong-item__title">{{ q.title }}</span>
              <el-icon><ArrowRight /></el-icon>
            </div>
          </div>
        </div>

        <div class="summary-actions">
          <el-button class="btn-action btn-action--retry" @click="resetToConfig">
            再练一次
          </el-button>
          <el-button class="btn-action btn-action--back" @click="goBack">
            返回错题本
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowRight, CircleCheck, CircleClose, Trophy } from '@element-plus/icons-vue'
import request from '@/utils/request'

const router = useRouter()
const route = useRoute()

const phase = ref('config')
const startLoading = ref(false)
const submitLoading = ref(false)
const showFeedback = ref(false)
const isCorrect = ref(false)
const currentIndex = ref(0)
const userAnswer = ref('')

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

const config = reactive({
  category: '',
  difficulty: '',
  count: 10
})

const questions = ref([])
const results = ref([])

const currentQuestion = computed(() => questions.value[currentIndex.value] || {})

const summary = computed(() => {
  const total = results.value.length
  const correct = results.value.filter(r => r.correct).length
  const wrong = total - correct
  const rate = total > 0 ? Math.round((correct / total) * 100) + '%' : '0%'
  const wrongQuestions = results.value
    .filter(r => !r.correct)
    .map(r => ({
      id: r.questionId,
      title: r.title
    }))
  return { total, correct, wrong, rate, wrongQuestions }
})

function goBack() {
  router.push('/questions/mistakes')
}

function goToDetail(id) {
  router.push(`/questions/mistakes/${id}`)
}

function resetToConfig() {
  phase.value = 'config'
  questions.value = []
  results.value = []
  currentIndex.value = 0
  userAnswer.value = ''
  showFeedback.value = false
}

async function startReview() {
  startLoading.value = true
  try {
    const params = {
      count: config.count
    }
    if (config.category) params.category = config.category
    if (config.difficulty) params.difficulty = config.difficulty

    const specificIds = route.query.ids
    if (specificIds) {
      params.ids = specificIds
    }

    const res = await request.get('/mistakes/review/questions', { params })
    if (res.code === 200) {
      questions.value = res.data || []
      if (questions.value.length === 0) {
        ElMessage.warning('没有符合条件的错题')
        return
      }
      phase.value = 'answering'
      currentIndex.value = 0
      userAnswer.value = ''
      showFeedback.value = false
      results.value = []
    }
  } catch (err) {
    console.error('获取错题失败:', err)
    ElMessage.error('获取错题失败')
  } finally {
    startLoading.value = false
  }
}

async function submitAnswer() {
  if (!userAnswer.value.trim()) {
    ElMessage.warning('请输入你的答案')
    return
  }

  submitLoading.value = true
  try {
    const res = await request.post('/mistakes/review/check', {
      questionId: currentQuestion.value.id,
      userAnswer: userAnswer.value
    })
    if (res.code === 200) {
      isCorrect.value = res.data.correct
      showFeedback.value = true
      results.value.push({
        questionId: currentQuestion.value.id,
        title: currentQuestion.value.title,
        correct: res.data.correct
      })
    }
  } catch (err) {
    console.error('提交答案失败:', err)
    ElMessage.error('提交答案失败')
  } finally {
    submitLoading.value = false
  }
}

function nextQuestion() {
  if (currentIndex.value < questions.value.length - 1) {
    currentIndex.value++
    userAnswer.value = ''
    showFeedback.value = false
  } else {
    phase.value = 'summary'
  }
}

onMounted(() => {
  const specificIds = route.query.ids
  if (specificIds) {
    config.count = specificIds.split(',').length
  }
})
</script>

<style lang="scss" scoped>
.review-page {
  max-width: 800px;
  margin: 0 auto;
  padding: 24px 20px 48px;
  min-height: 100vh;
  background: $bg-surface;
}

.review-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 32px;
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

.config-section {
  display: flex;
  justify-content: center;
}

.config-card {
  width: 100%;
  max-width: 480px;
  background: #fff;
  border: 1px solid $border-color;
  border-radius: 12px;
  padding: 32px;
}

.config-title {
  font-size: 20px;
  font-weight: 600;
  color: $text-primary;
  margin: 0 0 24px;
  text-align: center;
}

.config-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-label {
  font-size: 14px;
  font-weight: 500;
  color: $text-primary;
}

.form-select {
  width: 100%;
}

.form-number {
  width: 100%;
}

.btn-start {
  width: 100%;
  height: 44px;
  font-size: 16px;
  font-weight: 500;
  background: $accent-color;
  border-color: $accent-color;
  cursor: pointer;
  transition: all $transition-normal;

  &:hover {
    background: $accent-hover;
    border-color: $accent-hover;
  }
}

.progress-bar {
  margin-bottom: 24px;
}

.progress-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.progress-text {
  font-size: 14px;
  color: $text-secondary;
}

.progress-count {
  font-size: 14px;
  font-weight: 500;
  color: $text-primary;
}

.question-card {
  background: #fff;
  border: 1px solid $border-color;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 20px;
}

.question-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.question-tags {
  display: flex;
  gap: 8px;
}

.category-tag {
  background: #EFF6FF;
  border-color: $accent-color;
  color: $accent-color;
}

.difficulty-tag {
  font-weight: 500;
  border: none;

  &--easy {
    background: rgba(22, 163, 74, 0.1);
    color: #16A34A;
  }

  &--medium {
    background: rgba(234, 179, 8, 0.1);
    color: #ca8a04;
  }

  &--hard {
    background: rgba(220, 38, 38, 0.1);
    color: #DC2626;
  }
}

.question-count {
  font-size: 13px;
  color: $text-secondary;
}

.question-title {
  font-size: 18px;
  font-weight: 600;
  color: $text-primary;
  margin: 0 0 12px;
}

.question-content {
  font-size: 15px;
  line-height: 1.8;
  color: $text-primary;
  white-space: pre-wrap;
  word-break: break-word;
}

.answer-card {
  background: #fff;
  border: 1px solid $border-color;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 20px;
}

.answer-title {
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
  margin: 0 0 12px;
}

.answer-input {
  margin-bottom: 16px;
}

.answer-actions {
  display: flex;
  justify-content: flex-end;
}

.btn-submit {
  background: $accent-color;
  border-color: $accent-color;
  cursor: pointer;
  transition: all $transition-normal;

  &:hover {
    background: $accent-hover;
    border-color: $accent-hover;
  }
}

.feedback-card {
  background: #fff;
  border: 1px solid $border-color;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 20px;
  animation: fadeIn 0.3s ease;

  &--correct {
    border-color: #16A34A;
    background: #F0FDF4;
  }

  &--wrong {
    border-color: #DC2626;
    background: #FEF2F2;
  }
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.feedback-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.feedback-status {
  font-size: 18px;
  font-weight: 600;
  color: $text-primary;
}

.feedback-answer {
  background: #fff;
  border: 1px solid $border-color;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;

  h4 {
    font-size: 14px;
    font-weight: 600;
    color: $text-secondary;
    margin: 0 0 8px;
  }

  p {
    font-size: 14px;
    line-height: 1.6;
    color: $text-primary;
    margin: 0;
    white-space: pre-wrap;
    word-break: break-word;
  }
}

.feedback-actions {
  display: flex;
  justify-content: flex-end;
}

.btn-next {
  background: $accent-color;
  border-color: $accent-color;
  color: #fff;
  cursor: pointer;
  transition: all $transition-normal;

  &:hover {
    background: $accent-hover;
    border-color: $accent-hover;
  }
}

.summary-section {
  display: flex;
  justify-content: center;
}

.summary-card {
  width: 100%;
  max-width: 560px;
  background: #fff;
  border: 1px solid $border-color;
  border-radius: 12px;
  padding: 40px;
  text-align: center;
}

.summary-icon {
  margin-bottom: 20px;
}

.summary-title {
  font-size: 24px;
  font-weight: 600;
  color: $text-primary;
  margin: 0 0 32px;
}

.summary-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 32px;
}

.summary-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;

  &__value {
    font-size: 32px;
    font-weight: 700;
    color: $text-primary;

    &--correct {
      color: #16A34A;
    }

    &--wrong {
      color: #DC2626;
    }

    &--rate {
      color: $accent-color;
    }
  }

  &__label {
    font-size: 14px;
    color: $text-secondary;
  }
}

.wrong-list {
  margin-bottom: 32px;
  text-align: left;
}

.wrong-title {
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
  margin: 0 0 12px;
}

.wrong-items {
  border: 1px solid $border-color;
  border-radius: 8px;
  overflow: hidden;
}

.wrong-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #fff;
  border-bottom: 1px solid $border-color;
  cursor: pointer;
  transition: background $transition-normal;

  &:last-child {
    border-bottom: none;
  }

  &:hover {
    background: $bg-surface;
  }

  &__title {
    font-size: 14px;
    color: $accent-color;
    font-weight: 500;
  }
}

.summary-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
}

.btn-action {
  padding: 10px 24px;
  font-size: 14px;
  cursor: pointer;
  transition: all $transition-normal;
  border-radius: 6px;

  &--retry {
    background: $accent-color;
    border: 1px solid $accent-color;
    color: #fff;

    &:hover {
      background: $accent-hover;
      border-color: $accent-hover;
    }
  }

  &--back {
    background: #fff;
    border: 1px solid $border-color;
    color: $text-secondary;

    &:hover {
      border-color: $accent-color;
      color: $accent-color;
    }
  }
}

@media (max-width: 768px) {
  .review-page {
    padding: 16px;
  }

  .config-card,
  .summary-card {
    padding: 24px;
  }

  .summary-stats {
    grid-template-columns: repeat(2, 1fr);
  }

  .summary-stat__value {
    font-size: 24px;
  }
}
</style>
