<template>
  <div class="page-container interview-config-page">
    <div class="config-container">
      <div class="config-header">
        <h1 class="config-title">模拟面试</h1>
        <p class="config-subtitle">配置面试参数，开始你的AI模拟面试</p>
      </div>
      <div class="config-body">
        <div class="form-group">
          <label class="form-label">面试类型</label>
          <div class="interview-type-options">
            <div :class="['type-card', config.interviewType === 'normal' ? 'active' : '']"
                 @click="config.interviewType = 'normal'">
              <div class="type-icon">📝</div>
              <div class="type-label">普通面试</div>
              <div class="type-desc">从题库抽题，按岗位方向出题</div>
            </div>
            <div :class="['type-card resume', config.interviewType === 'resume' ? 'active' : '', !hasActiveResume ? 'disabled' : '']"
                 @click="hasActiveResume && (config.interviewType = 'resume')">
              <div class="type-icon">📄</div>
              <div class="type-label">简历面试</div>
              <div class="type-desc">基于简历内容，AI 精准出题</div>
            </div>
          </div>
          <p v-if="!hasActiveResume" class="count-tip" style="color: #E6A23C;">
            暂无已激活的简历，请先<a href="/resume/list" style="color: #409EFF;">前往简历管理</a>上传并激活简历
          </p>
          <p v-if="config.interviewType === 'resume'" class="count-tip" style="color: #67C23A;">
            将基于您已激活的简历内容进行针对性提问
          </p>
        </div>
        <div class="form-group">
          <label class="form-label">岗位方向</label>
          <div class="select-wrapper">
            <select v-model="config.position" class="form-select" @change="onPositionChange">
              <option value="" disabled>请选择岗位方向</option>
              <option value="java_backend">Java后端开发</option>
              <option value="frontend">前端开发</option>
              <option value="fullstack">全栈开发</option>
              <option value="algorithm">算法工程师</option>
              <option value="hr">HR / 软素质</option>
            </select>
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">面试轮次</label>
          <div class="round-options">
            <div v-for="item in roundOptions" :key="item.value"
                 :class="['round-card', config.round === item.value ? 'active' : '', config.position === 'hr' && item.value !== 'hr' ? 'disabled' : '']"
                 @click="config.position !== 'hr' && (config.round = item.value)">
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
            <button class="count-btn" @click="config.questionCount = Math.max(1, config.questionCount - 1)" :disabled="config.questionCount <= 1">-</button>
            <input type="number" class="count-input" v-model.number="config.questionCount" min="1" />
            <button class="count-btn" @click="config.questionCount++">+</button>
          </div>
          <p v-if="config.questionCount > availableCount && availableCount > 0" class="count-warning">
            当前筛选条件下可用题目仅 {{ availableCount }} 道，超出部分将自动截取
          </p>
          <p v-if="availableCount > 0" class="count-tip">
            该筛选题库共 {{ availableCount }} 道
          </p>
        </div>
        <div class="form-group">
          <label class="form-label">AI 模型</label>
          <div class="select-wrapper">
            <select v-model="config.modelId" class="form-select">
              <option :value="null">默认模型</option>
              <option v-for="m in aiModels" :key="m.id" :value="m.id">
                {{ m.modelName }}{{ m.isDefault ? ' (默认)' : '' }}{{ m.userId ? ' (我的)' : '' }}
              </option>
            </select>
          </div>
          <p class="count-tip">选择不同的 AI 模型进行面试，可在<a href="/settings/models" style="color: #409EFF;">个人设置</a>中管理自己的模型</p>
        </div>
        <div class="form-group">
          <label class="form-label">每题追问次数</label>
          <div class="count-control">
            <button class="count-btn" @click="config.maxFollowUp = Math.max(0, config.maxFollowUp - 1)" :disabled="config.maxFollowUp <= 0">-</button>
            <input type="number" class="count-input" v-model.number="config.maxFollowUp" min="0" />
            <button class="count-btn" @click="config.maxFollowUp++">+</button>
          </div>
          <p class="count-tip">AI 对每道题最多追问的次数，设为 0 则不追问直接跳题</p>
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
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { createInterview } from '@/api/interview'
import { getQuestionCount } from '@/api/question'
import { getResumeList } from '@/api/resume'
import { getEnabledModels } from '@/api/aiModel'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const availableCount = ref(0)
const hasActiveResume = ref(false)
const aiModels = ref([])

const config = reactive({
  interviewType: 'normal',
  position: '',
  round: '',
  difficulty: 'medium',
  questionCount: 5,
  maxFollowUp: 4,
  modelId: null
})

// 从路由参数初始化配置（如从首页快速开始跳转）
onMounted(async () => {
  if (route.query.position) {
    config.position = route.query.position
  }
  if (route.query.round) {
    config.round = route.query.round
  }
  if (route.query.position) {
    fetchAvailableCount()
  }
  // 检查是否有已激活的简历
  try {
    const res = await getResumeList()
    if (res.code === 200 && res.data && res.data.records) {
      hasActiveResume.value = res.data.records.some(r => r.isActive === 1 && r.status === 1)
    }
  } catch (e) {
    console.error('获取简历列表失败:', e)
  }
  // 获取可用AI模型列表
  try {
    const res = await getEnabledModels()
    if (res.code === 200 && res.data) {
      aiModels.value = res.data
      const defaultModel = res.data.find(m => m.isDefault === 1)
      if (defaultModel) config.modelId = defaultModel.id
    }
  } catch (e) {
    console.error('获取AI模型列表失败:', e)
  }
})

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
  if (config.position === 'hr') {
    config.round = 'hr'
  }
}

function onDifficultyChange(value) {
  config.difficulty = value
  fetchAvailableCount()
}

async function startInterview() {
  if (!isValid.value) {
    ElMessage.warning('请完善面试配置')
    return
  }
  if (availableCount.value > 0 && availableCount.value < config.questionCount) {
    ElMessage.warning(`当前筛选条件下可用题目仅 ${availableCount.value} 道，超出部分将自动截取`)
  }
  loading.value = true
  try {
    const res = await createInterview({
      interviewType: config.interviewType,
      position: config.position,
      round: config.round,
      difficulty: config.difficulty,
      questionCount: config.questionCount,
      maxFollowUp: config.maxFollowUp,
      modelId: config.modelId
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

.interview-type-options {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.type-card {
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
  &.active.resume {
    border-color: #67C23A;
    background: #f0f9ff;
  }
  &.disabled {
    opacity: 0.45;
    cursor: not-allowed;
    pointer-events: none;
  }
}

.type-icon {
  font-size: 28px;
  margin-bottom: 6px;
}

.type-label {
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 2px;
}

.type-desc {
  font-size: 12px;
  color: $text-muted;
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
  &.disabled {
    opacity: 0.45;
    cursor: not-allowed;
    pointer-events: none;
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


.toggle-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.toggle-switch {
  position: relative;
  display: inline-block;
  width: 44px;
  height: 24px;
  flex-shrink: 0;

  input { opacity: 0; width: 0; height: 0; }
}

.toggle-slider {
  position: absolute;
  cursor: pointer;
  top: 0; left: 0; right: 0; bottom: 0;
  background-color: #dcdfe6;
  border-radius: 24px;
  transition: 0.3s;

  &::before {
    position: absolute;
    content: "";
    height: 18px;
    width: 18px;
    left: 3px;
    bottom: 3px;
    background-color: white;
    border-radius: 50%;
    transition: 0.3s;
  }
}

.toggle-switch input:checked + .toggle-slider {
  background-color: $accent-color;
}

.toggle-switch input:checked + .toggle-slider::before {
  transform: translateX(20px);
}

.toggle-label {
  font-size: 14px;
  color: $text-secondary;
}

@media (max-width: 768px) {
  .config-body { padding: 20px; }
  .round-options, .difficulty-options, .interview-type-options { grid-template-columns: 1fr; }
}
</style>
