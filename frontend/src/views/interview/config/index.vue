<template>
  <div class="page-container interview-config-page">
    <div class="config-container">
      <div class="config-header">
        <h1 class="config-title">模拟面试</h1>
        <p class="config-subtitle">配置面试参数，开始你的AI模拟面试</p>
      </div>
      <div class="config-body">
        <div class="form-group">
          <label class="form-label">岗位方向</label>
          <div class="select-wrapper">
            <select v-model="config.position" class="form-select" @change="onPositionChange">
              <option value="" disabled>请选择岗位方向</option>
              <option value="java_backend">Java后端开发</option>
              <option value="frontend">前端开发</option>
              <option value="fullstack">全栈开发</option>
              <option value="algorithm">算法工程师</option>
            </select>
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">面试轮次</label>
          <div class="round-options">
            <div v-for="item in roundOptions" :key="item.value"
                 :class="['round-card', config.round === item.value ? 'active' : '']"
                 @click="config.round = item.value">
              <div class="round-icon">{{ item.icon }}</div>
              <div class="round-label">{{ item.label }}</div>
            </div>
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">难度等级</label>
          <div class="difficulty-options">
            <div v-for="item in difficultyOptions" :key="item.value"
                 :class="['difficulty-card', config.difficulty === item.value ? 'active' : '', item.value]"
                 @click="onDifficultyChange(item.value)">
              <div class="difficulty-label">{{ item.label }}</div>
              <div class="difficulty-desc">{{ item.desc }}</div>
            </div>
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">题目数量</label>
          <div class="count-control">
            <button class="count-btn" @click="config.questionCount = Math.max(3, config.questionCount - 1)" :disabled="config.questionCount <= 3">-</button>
            <input type="number" class="count-input" v-model.number="config.questionCount" min="3" :max="maxCount"
                   @input="onCountInput" />
            <button class="count-btn" @click="config.questionCount = Math.min(maxCount, config.questionCount + 1)" :disabled="config.questionCount >= maxCount">+</button>
          </div>
          <p v-if="config.questionCount > availableCount && availableCount > 0" class="count-warning">
            当前筛选条件下可用题目仅 {{ availableCount }} 道
          </p>
          <p v-if="availableCount > 0 && availableCount < 5" class="count-warning">
            当前筛选条件下仅 {{ availableCount }} 道题目可选（至少需 5 道）
          </p>
          <p v-if="availableCount > 0" class="count-tip">
            该筛选题库共 {{ availableCount }} 道，最多可选 {{ maxCount }} 道
          </p>
        </div>
        <button class="start-btn" @click="startInterview" :disabled="loading || !isValid">
          <span v-if="loading" class="loading-spinner"></span>
          {{ loading ? '创建中...' : '开始面试' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { createInterview } from '@/api/interview'
import { getQuestionCount } from '@/api/question'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loading = ref(false)
const availableCount = ref(0)

const config = reactive({
  position: '',
  round: '',
  difficulty: 'medium',
  questionCount: 5
})

const maxCount = computed(() => Math.min(availableCount.value || 10, 10))

const roundOptions = [
  { value: 'technical', label: '技术面', icon: '💻' },
  { value: 'hr', label: 'HR面', icon: '👔' },
  { value: 'comprehensive', label: '综合面', icon: '📋' }
]

const difficultyOptions = [
  { value: 'easy', label: '简单', desc: '基础概念' },
  { value: 'medium', label: '中等', desc: '深入理解' },
  { value: 'hard', label: '困难', desc: '高级进阶' }
]

const isValid = computed(() => config.position && config.round)

async function fetchAvailableCount() {
  if (!config.position) {
    availableCount.value = 0
    return
  }
  try {
    const res = await getQuestionCount({
      direction: config.position,
      difficulty: config.difficulty
    })
    if (res.code === 200) {
      availableCount.value = res.data || 0
    }
  } catch (e) {
    console.error('获取题目数量失败:', e)
  }
}

function onPositionChange() {
  fetchAvailableCount()
}

function onDifficultyChange(value) {
  config.difficulty = value
  fetchAvailableCount()
}

function onCountInput() {
  // 允许用户自由输入，不做自动修正
}

async function startInterview() {
  if (!isValid.value) {
    ElMessage.warning('请完善面试配置')
    return
  }
  if (availableCount.value < config.questionCount) {
    ElMessage.warning(`当前筛选条件下可用题目不足（${availableCount.value} 道），请减少题目数量或调整筛选条件`)
    return
  }
  loading.value = true
  try {
    const res = await createInterview({
      position: config.position,
      round: config.round,
      difficulty: config.difficulty,
      questionCount: config.questionCount
    })
    if (res.code === 200) {
      router.push(`/interview/session/${res.data.id}`)
    } else {
      ElMessage.error(res.message || '创建失败')
    }
  } catch (e) {
    ElMessage.error('创建面试失败，请重试')
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.interview-config-page {
  display: flex;
  justify-content: center;
  padding: 40px 20px;
}

.config-container {
  width: 100%;
  max-width: 640px;
}

.config-header {
  margin-bottom: 32px;
}

.config-body {
  background: #fff;
  border: 1px solid $border-color;
  border-radius: 12px;
  padding: 24px;
  padding: 32px;
}

.form-group {
  margin-bottom: 28px;
}

.form-label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: $text-primary;
  margin-bottom: 10px;
}

.select-wrapper {
  position: relative;
}

.form-select {
  width: 100%;
  padding: 10px 14px;
  font-size: 14px;
  color: $text-primary;
  background: $bg-surface;
  border: 1px solid $border-color;
  border-radius: 8px;
  appearance: none;
  cursor: pointer;
  transition: border-color $transition-fast;

  &:focus {
    outline: none;
    border-color: $accent-color;
  }
}

.round-options {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.round-card {
  padding: 16px;
  text-align: center;
  border: 1px solid $border-color;
  border-radius: 8px;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover { border-color: $accent-color; }
  &.active {
    border-color: $accent-color;
    background: $accent-light;
  }
}

.round-icon {
  font-size: 24px;
  margin-bottom: 6px;
}

.round-label {
  font-size: 13px;
  font-weight: 500;
  color: $text-primary;
}

.difficulty-options {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.difficulty-card {
  padding: 14px;
  text-align: center;
  border: 1px solid $border-color;
  border-radius: 8px;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover { border-color: $accent-color; }
  &.active {
    border-color: $color-success;
    background: $bg-success;
  }
  &.active.medium {
    border-color: $color-warning;
    background: $bg-warning;
  }
  &.active.hard {
    border-color: $color-destructive;
    background: $bg-danger;
  }
}

.difficulty-label {
  font-size: 14px;
  font-weight: 500;
  color: $text-primary;
  margin-bottom: 2px;
}

.difficulty-desc {
  font-size: 12px;
  color: $text-muted;
}

.count-control {
  display: flex;
  align-items: center;
  gap: 0;
  border: 1px solid $border-color;
  border-radius: 8px;
  overflow: hidden;
  width: fit-content;
}

.count-btn {
  width: 44px;
  height: 44px;
  border: none;
  background: $bg-surface;
  font-size: 18px;
  color: $text-primary;
  cursor: pointer;
  transition: background $transition-fast;

  &:hover:not(:disabled) { background: $bg-muted; }
  &:disabled { color: #D4D4D8; cursor: not-allowed; }
}

.count-input {
  width: 60px;
  text-align: center;
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
  border: none;
  border-left: 1px solid $border-color;
  border-right: 1px solid $border-color;
  line-height: 44px;
  outline: none;
  background: transparent;
  -moz-appearance: textfield;

  &::-webkit-outer-spin-button,
  &::-webkit-inner-spin-button {
    -webkit-appearance: none;
    margin: 0;
  }
}

.count-warning {
  margin: 8px 0 0 0;
  font-size: 13px;
  color: $color-destructive;
}

.count-tip {
  margin: 4px 0 0 0;
  font-size: 12px;
  color: $text-muted;
}

.start-btn {
  border: none;
  border-radius: 8px;
  background: $accent-color;
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background $transition-fast;
  white-space: nowrap;
  width: 100%;
  height: 48px;
  font-size: 16px;
  margin-top: 8px;
}

.loading-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid rgba(255,255,255,0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}


@media (max-width: 768px) {
  .config-body { padding: 20px; }
  .round-options, .difficulty-options { grid-template-columns: 1fr; }
}
</style>
