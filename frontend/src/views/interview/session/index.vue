<template>
  <div class="page-container interview-session-page">
    <div class="session-container">
      <div class="session-header">
        <div class="header-left">
          <h2 class="header-title">模拟面试</h2>
          <span class="header-badge" v-if="session">{{ positionMap[session.position] }} · {{ difficultyMap[session.difficulty] }}</span>
        </div>
        <div class="header-center" v-if="session">
          <div class="progress-info">
            <span class="progress-text">第 <strong>{{ Math.min(session.currentQuestion + 1, session.questionCount) }}</strong> / {{ session.questionCount }} 题</span>
            <span class="progress-tag" :class="`progress-tag--${getTagType()}`">{{ tagLabel }}</span>
            <span class="follow-up-tag" v-if="session.maxFollowUp > 0">每题最多追问 {{ session.maxFollowUp }} 次</span>
            <span class="follow-up-tag follow-up-tag--disabled" v-else>不追问</span>
          </div>
          <div class="progress-bar">
            <div class="progress-fill" :style="{ width: progressPercent + '%' }"></div>
          </div>
          <!-- 计时信息 -->
          <div class="timer-bar">
            <span class="timer-item">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <polyline points="12 6 12 12 16 14"/>
              </svg>
              总用时 {{ formatDuration(totalElapsed) }}
            </span>
            <span class="timer-divider">|</span>
            <span class="timer-item timer-current">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <polyline points="12 6 12 12 16 14"/>
              </svg>
              当前题 {{ formatDuration(currentQuestionElapsed) }}
            </span>
          </div>
        </div>
        <button class="end-btn" @click="handleEndInterview" :disabled="ending || sending">
          {{ ending ? '结束中...' : '结束面试' }}
        </button>
        <button class="mute-btn" @click="ttsMuted = !ttsMuted" :title="ttsMuted ? '开启语音' : '静音'">
          <svg v-if="!ttsMuted" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/>
            <path d="M15.54 8.46a5 5 0 0 1 0 7.07"/>
            <path d="M19.07 4.93a10 10 0 0 1 0 14.14"/>
          </svg>
          <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/>
            <line x1="23" y1="9" x2="17" y2="15"/>
            <line x1="17" y1="9" x2="23" y2="15"/>
          </svg>
        </button>
      </div>
      <div class="messages-container" ref="messageContainer">
        <div v-for="msg in messages" :key="msg.id" :class="['message-item', msg.role]">
          <div class="message-avatar">
            <div :class="['avatar-circle', msg.role]">{{ msg.role === 'ai' ? 'AI' : '我' }}</div>
          </div>
          <div class="message-bubble">
            <div v-if="msg.role === 'ai'" class="message-text markdown-body" v-html="renderMarkdown(msg.content)"></div>
            <div v-else class="message-text" v-html="formatMessage(msg.content)"></div>
            <TtsPlayer
              v-if="msg.role === 'ai' && msg.content && !ttsMuted"
              :text="msg.content"
              :autoPlay="autoPlayTts && msg.id === lastAiMessageId"
              :muted="ttsMuted"
              :activeId="activeTtsId"
              :messageId="msg.id"
              @tts-started="activeTtsId = msg.id"
            />
            <div class="message-time" v-if="msg.createdAt">{{ formatTime(msg.createdAt) }}</div>
          </div>
        </div>
        <div v-if="sending" class="message-item ai">
          <div class="message-avatar">
            <div class="avatar-circle ai">AI</div>
          </div>
          <div class="message-bubble">
            <div v-if="streamingContent" class="message-text markdown-body" v-html="renderMarkdown(streamingContent)"></div>
            <div v-else class="typing-indicator">
              <span></span><span></span><span></span>
            </div>
          </div>
        </div>
      </div>
      <div class="retry-bar" v-if="retryContent && !sending">
        <span class="retry-icon">⚠️</span>
        <span class="retry-text">发送失败，请重试</span>
        <button class="retry-btn" @click="handleRetry" :disabled="sendingRetry">
          {{ sendingRetry ? '重试中...' : '重新发送' }}
        </button>
      </div>
      <div class="input-area">
        <VoiceRecorder
          @result="handleVoiceResult"
          :disabled="sending || isInterviewEnded"
          class="voice-btn-wrapper"
        />
        <textarea
          ref="textareaRef"
          v-model="inputMessage"
          class="message-input"
          :placeholder="inputPlaceholder"
          @keydown="handleKeydown"
          :disabled="sending"
          rows="1"
        ></textarea>
        <button class="send-btn" @click="handleSend" :disabled="!inputMessage.trim() || sending || isInterviewEnded">
          {{ sending ? '生成中' : '发送' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch, onDeactivated } from 'vue'
import { useRouter, useRoute, onBeforeRouteLeave } from 'vue-router'
import { getSessionMessages, getSessionInfo, sendMessage, endInterview, abandonInterview, getReportStatus } from '@/api/interview'
import { submitReportDurations } from '@/api/interview'
import { useInterviewStore } from '@/stores/interview'
import { ElMessage, ElMessageBox } from 'element-plus'
import { marked } from 'marked'
import { positionMap, difficultyMap } from '@/utils/constants'
import VoiceRecorder from './components/VoiceRecorder.vue'
import TtsPlayer from './components/TtsPlayer.vue'

const interviewStore = useInterviewStore()
const { setInterviewState, cacheInterviewState, getCachedState, clearInterviewCache } = interviewStore
const router = useRouter()
const route = useRoute()
const sessionId = computed(() => route.params.id)
const messageContainer = ref(null)
const textareaRef = ref(null)
const inputMessage = ref('')
const sending = ref(false)
const ending = ref(false)
const messages = ref([])
const session = ref(null)
const streamingContent = ref('')
const interviewEnded = ref(false)
const lastMessageType = ref('')
const retryContent = ref('')          // 待重试的消息内容
const sendingRetry = ref(false)       // 正在重试中
const autoPlayTts = ref(true)         // 自动播放 TTS
const lastAiMessageId = ref(null)     // 最后一条 AI 消息 ID
const ttsMuted = ref(false)           // 全局静音状态
const activeTtsId = ref(null)         // 当前正在播放 TTS 的消息 ID
const lastKeyWasEnter = ref(false)    // 上一次按键是否为 Enter

// ======================== 计时器逻辑 ========================
const startTimestamp = ref(0)
const questionStartTimestamp = ref(0)
const totalElapsed = ref(0)
const currentQuestionElapsed = ref(0)
const questionDurations = ref({})
const questionTimer = ref(null)
const pausedSince = ref(0)
const totalPauseDuration = ref(0)



const isInterviewEnded = computed(() => {
  if (!session.value) return false
  return lastMessageType.value === 'end' || session.value.currentQuestion >= session.value.questionCount
})

const tagLabel = computed(() => {
  if (isInterviewEnded.value) return '面试已结束'
  if (!lastMessageType.value) return '答题中'
  if (lastMessageType.value === 'follow_up') return '追问中'
  if (lastMessageType.value === 'next_question') return '答题中'
  return '答题中'
})

const progressPercent = computed(() => {
  if (!session.value) return 0
  const current = Math.min(session.value.currentQuestion, session.value.questionCount)
  return Math.round((current / session.value.questionCount) * 100)
})

const inputPlaceholder = computed(() => {
  if (isInterviewEnded.value) return '面试已结束，无法发送消息'
  if (lastMessageType.value === 'follow_up') return '回答面试官的追问... (Ctrl+Enter 发送)'
  return '输入你的回答... (Ctrl+Enter 发送)'
})

function getTagType() {
  if (lastMessageType.value === 'follow_up') return 'follow'
  return 'normal'
}

// 启动计时器
function startTimers() {
  startTimestamp.value = Date.now()
  questionStartTimestamp.value = Date.now()
  questionTimer.value = setInterval(() => {
    if (!session.value) return
    const now = Date.now()
    totalElapsed.value = Math.max(0, Math.floor((now - startTimestamp.value) / 1000) - totalPauseDuration.value)
    currentQuestionElapsed.value = Math.max(0, Math.floor((now - questionStartTimestamp.value) / 1000))
  }, 1000)
}

// 停止计时器
function stopTimers() {
  if (questionTimer.value) {
    clearInterval(questionTimer.value)
    questionTimer.value = null
  }
}

// 跳题时记录当前题用时
function recordQuestionDuration() {
  if (!session.value) return
  const qIndex = session.value.currentQuestion
  const elapsed = currentQuestionElapsed.value
  if (elapsed > 0) {
    questionDurations.value[qIndex] = elapsed
  }
  questionStartTimestamp.value = Date.now()
  currentQuestionElapsed.value = 0
}

// 提交所有计时数据到服务端
async function submitDurations(reportId) {
  if (session.value) {
    const lastQ = session.value.currentQuestion
    const lastElapsed = currentQuestionElapsed.value
    if (lastElapsed > 0) {
      questionDurations.value[lastQ] = lastElapsed
    }
  }
  const durations = Object.entries(questionDurations.value).map(([qIndex, seconds]) => ({
    questionIndex: parseInt(qIndex),
    seconds
  }))
  if (durations.length === 0) return
  try {
    await submitReportDurations(reportId, { durations })
  } catch (e) {
    console.warn('提交计时数据失败（不影响主流程）', e)
  }
}

// 格式化时长
function formatDuration(seconds) {
  if (!seconds || seconds < 0) return '0:00'
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${s.toString().padStart(2, '0')}`
}

// 页面可见性变化
function handleVisibilityChange() {
  if (document.hidden) {
    pausedSince.value = Date.now()
  } else if (pausedSince.value > 0) {
    totalPauseDuration.value += Math.floor((Date.now() - pausedSince.value) / 1000)
    pausedSince.value = 0
  }
}

onMounted(async () => {
  setInterviewState(true)
  window.addEventListener('beforeunload', handleBeforeUnload)

  // 尝试从缓存恢复（页面刷新场景）
  const cached = getCachedState(sessionId.value)
  if (cached) {
    session.value = cached.session
    messages.value = cached.messages
    updateLastMessageType()
    // 恢复计时
    if (cached.session?.createdAt) {
      const serverStart = new Date(cached.session.createdAt).getTime()
      const elapsed = Math.floor((Date.now() - serverStart) / 1000)
      totalElapsed.value = Math.max(0, elapsed)
      startTimestamp.value = Date.now() - elapsed * 1000
    }
    startTimers()
    await nextTick()
    scrollToBottom()
    // 后台静默刷新数据（确保最新）
    Promise.all([loadMessages(), loadSessionInfo()])
  } else {
    // 缓存未命中，从 API 加载
    await Promise.all([loadMessages(), loadSessionInfo()])
    startTimers()
  }

  document.addEventListener('visibilitychange', handleVisibilityChange)
})

// textarea 自适应高度
watch(inputMessage, () => {
  nextTick(() => {
    const el = textareaRef.value
    if (!el) return
    el.style.height = 'auto'
    el.style.height = el.scrollHeight + 'px'
  })
})

// keep-alive 下切换面试时重置状态并重新加载
watch(sessionId, async (newId, oldId) => {
  if (newId && newId !== oldId && route.name === 'InterviewSession') {
    // 重置所有状态
    messages.value = []
    session.value = null
    inputMessage.value = ''
    sending.value = false
    ending.value = false
    streamingContent.value = ''
    interviewEnded.value = false
    lastMessageType.value = ''
    retryContent.value = ''
    sendingRetry.value = false
    lastAiMessageId.value = null
    activeTtsId.value = null
    totalElapsed.value = 0
    currentQuestionElapsed.value = 0
    questionDurations.value = {}
    stopTimers()
    // 重新加载
    await Promise.all([loadMessages(), loadSessionInfo()])
    startTimers()
  }
})

onBeforeUnmount(() => {
  isUnmounted.value = true
  stopTimers()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  setInterviewState(false)
  window.removeEventListener('beforeunload', handleBeforeUnload)
})

// keep-alive 下离开面试页时停止计时
onDeactivated(() => {
  stopTimers()
})

// 组件是否已卸载的标记
const isUnmounted = ref(false)

onBeforeRouteLeave((to, from, next) => {
  if (interviewEnded.value) {
    next()
    return
  }
  const hasUserAnswer = messages.value.some(m => m.role === 'user')
  const confirmMsg = hasUserAnswer
    ? '面试正在进行中，离开后面试将自动结束并生成报告。确定要离开吗？'
    : '面试正在进行中，你还没有回答任何题目，离开后将放弃本次面试。确定要离开吗？'
  ElMessageBox.confirm(
    confirmMsg,
    '面试提示',
    {
      confirmButtonText: hasUserAnswer ? '结束面试并离开' : '放弃面试',
      cancelButtonText: '继续面试',
      type: 'warning'
    }
  ).then(async () => {
    try {
      if (hasUserAnswer) {
        await endInterview(sessionId.value)
      } else {
        await abandonInterview(sessionId.value)
      }
    } catch (e) {
      console.warn('结束面试失败', e)
    }
    interviewEnded.value = true
    setInterviewState(false)
    clearInterviewCache()
    stopTimers()
    next({ path: '/interview/config', replace: true })
  }).catch(() => {
    next(false)
  })
})

function handleBeforeUnload(e) {
  if (!interviewEnded.value) {
    e.preventDefault()
    e.returnValue = ''
  }
}

async function loadMessages() {
  try {
    const res = await getSessionMessages(sessionId.value)
    if (res.code === 200) {
      messages.value = res.data || []
      updateLastMessageType()
      // 更新缓存
      cacheInterviewState(sessionId.value, session.value, messages.value)
      await nextTick()
      scrollToBottom()
    }
  } catch (e) {
    ElMessage.error('加载消息失败')
  }
}

async function loadSessionInfo() {
  try {
    const res = await getSessionInfo(sessionId.value)
    if (res.code === 200) {
      session.value = res.data
      // 更新缓存
      cacheInterviewState(sessionId.value, session.value, messages.value)
      // 如果面试已进行了一段时间，从服务端时间算起
      if (res.data?.createdAt) {
        const serverStart = new Date(res.data.createdAt).getTime()
        const elapsed = Math.floor((Date.now() - serverStart) / 1000)
        totalElapsed.value = Math.max(0, elapsed)
        startTimestamp.value = Date.now() - elapsed * 1000
      }
      await nextTick()
      scrollToBottom()
    }
  } catch (e) {
    ElMessage.error('加载面试信息失败')
  }
}

function updateLastMessageType() {
  const lastAi = [...messages.value].reverse().find(m => m.role === 'ai')
  lastMessageType.value = lastAi?.messageType || ''
}

async function handleSend() {
  await doSend(inputMessage.value)
}

function handleKeydown(e) {
  // Ctrl+Enter 直接发送
  if (e.key === 'Enter' && e.ctrlKey) {
    e.preventDefault()
    handleSend()
    return
  }

  // 普通 Enter
  if (e.key === 'Enter' && !e.shiftKey) {
    // 连续两次回车且有内容 → 发送
    if (lastKeyWasEnter.value && inputMessage.value.trim()) {
      e.preventDefault()
      // 去掉末尾的换行符
      inputMessage.value = inputMessage.value.replace(/\n+$/, '')
      handleSend()
      lastKeyWasEnter.value = false
      return
    }
    lastKeyWasEnter.value = true
    return
  }

  // 其他按键重置标记
  lastKeyWasEnter.value = false
}

async function doSend(content, isRetry = false) {
  if (!content || !content.trim()) return
  if (sending.value && !isRetry) return
  // 面试已结束，禁止发送
  if (isInterviewEnded.value) {
    ElMessage.warning('面试已结束，无法发送消息')
    return
  }

  if (!isRetry) {
    inputMessage.value = ''
    messages.value.push({
      id: Date.now(),
      role: 'user',
      content: content,
      createdAt: new Date().toISOString()
    })
    await nextTick()
    scrollToBottom()
  }

  retryContent.value = ''
  sending.value = true
  streamingContent.value = ''
  sendingRetry.value = isRetry

  try {
    const res = await sendMessage(sessionId.value, content)
    if (res.code === 200) {
      messages.value.push({
        id: res.data.id,
        role: 'ai',
        content: res.data.content,
        messageType: res.data.type || 'next_question',
        createdAt: res.data.createdAt
      })
      lastMessageType.value = res.data.type || ''
      if (res.data.messageId) {
        lastAiMessageId.value = res.data.messageId
      }
      if (res.data.type === 'next_question') {
        recordQuestionDuration()
      }
      if (session.value && res.data.nextQuestion) {
        session.value.currentQuestion = res.data.nextQuestion
      }
      if (res.data.type === 'end' || (session.value && session.value.currentQuestion >= session.value.questionCount)) {
        stopTimers()
      }
      await nextTick()
      scrollToBottom()
    } else {
      ElMessage.error(res.message || '发送失败，请重试')
      retryContent.value = content
    }
  } catch {
    ElMessage.error('发送失败，请重试')
    retryContent.value = content
  } finally {
    sending.value = false
    sendingRetry.value = false
  }
}

function handleRetry() {
  if (retryContent.value && !sending.value) {
    doSend(retryContent.value, true)
  }
}

// 语音识别结果回调
function handleVoiceResult(result) {
  if (result.status === 'success') {
    inputMessage.value = result.text
  } else if (result.status === 'error') {
    ElMessage.error('语音识别失败：' + result.message)
  }
}

async function handleEndInterview() {
  if (ending.value || sending.value) return
  ending.value = true
  try {
    const res = await endInterview(sessionId.value)
    if (res.code === 200) {
      interviewEnded.value = true
      stopTimers()
      setInterviewState(false)
      clearInterviewCache()
      // 异步评估，轮询等待报告生成
      const reportId = await waitForReport(sessionId.value)
      if (reportId === 'evaluate_failed') {
        ElMessage.warning('AI 评估失败，请稍后在面试历史中重新评估')
        router.replace('/interview')
      } else if (reportId === 'no_report') {
        ElMessage.info('本次面试无有效回答，未生成报告')
        router.replace('/interview')
      } else if (reportId) {
        submitDurations(reportId)
        router.replace(`/report/detail/${reportId}`)
      } else {
        ElMessage.warning('报告生成超时，请稍后在面试历史中查看')
        router.replace('/interview')
      }
    } else {
      ElMessage.error(res.message || '结束面试失败')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || '结束面试失败')
  } finally {
    ending.value = false
  }
}

/**
 * 轮询等待报告生成完成（最多等 2 分钟）
 */
async function waitForReport(sessionId) {
  for (let i = 0; i < 120; i++) {
    await new Promise(r => setTimeout(r, 1000))
    try {
      const res = await getReportStatus(sessionId)
      if (res.code === 200 && res.data) {
        if (res.data.status === 'completed' && res.data.reportId) {
          return res.data.reportId
        }
        if (res.data.status === 'completed' && !res.data.reportId) {
          // 面试已完成但没有报告（无有效回答的情况）
          return 'no_report'
        }
        if (res.data.status === 'evaluate_failed') {
          return 'evaluate_failed'
        }
        // evaluating 状态继续等待
      }
    } catch (e) {
      console.warn('轮询报告状态失败', e)
    }
  }
  return null
}

function scrollToBottom() {
  if (messageContainer.value) {
    messageContainer.value.scrollTop = messageContainer.value.scrollHeight
  }
}

function formatMessage(text) {
  if (!text) return ''
  return text.replace(/\n/g, '<br>')
}

function renderMarkdown(text) {
  if (!text) return ''
  return marked.parse(text, { breaks: true })
}

function formatTime(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}
</script>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.interview-session-page {
  height: calc(100vh - 120px);
  display: flex;
  justify-content: center;
  padding: 20px;
}

.session-container {
  width: 100%;
  max-width: 800px;
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid $border-color;
  border-radius: 12px;
  overflow: hidden;
}

.session-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 24px;
  border-bottom: 1px solid $border-color;
  background: #fff;
  gap: 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
  margin: 0;
}

.header-badge {
  font-size: 12px;
  color: $text-muted;
  background: $bg-muted;
  padding: 2px 10px;
  border-radius: 10px;
}

.header-center {
  flex: 1;
  min-width: 120px;
  max-width: 300px;
}

.progress-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.progress-text {
  font-size: 13px;
  color: #52525B;

  strong {
    color: $accent-color;
    font-size: 15px;
  }
}

.progress-tag {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 8px;
  font-weight: 500;

  &--normal {
    background: $accent-light;
    color: $accent-color;
  }

  &--follow {
    background: #FEF3C7;
    color: #D97706;
  }
}

.follow-up-tag {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 8px;
  font-weight: 500;
  background: #E0F2FE;
  color: #0284C7;

  &--disabled {
    background: #F3F4F6;
    color: #6B7280;
  }
}

.progress-bar {
  height: 4px;
  background: $border-color;
  border-radius: 2px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, $accent-color, #6366F1);
  border-radius: 2px;
  transition: width 0.3s ease;
}

.timer-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  font-size: 12px;
  color: $text-tertiary;
}
.timer-item {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-variant-numeric: tabular-nums;
}
.timer-current {
  color: $text-muted;
}
.timer-divider {
  color: #D4D4D8;
}

.end-btn {
  padding: 8px 16px;
  border: 1px solid $color-destructive;
  border-radius: 6px;
  background: #fff;
  color: $color-destructive;
  font-size: 13px;
  cursor: pointer;
  transition: all $transition-fast;
  white-space: nowrap;
  font-weight: 500;
  flex-shrink: 0;

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
    border-color: #ccc;
    color: #ccc;
  }
}

.mute-btn {
  width: 36px;
  height: 36px;
  border: 1px solid $border-color;
  border-radius: 6px;
  background: #fff;
  color: #52525B;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all $transition-fast;

  &:hover {
    color: $accent-color;
    border-color: $accent-color;
  }
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.message-item {
  display: flex;
  gap: 12px;
  max-width: 85%;

  &.user {
    align-self: flex-end;
    flex-direction: row-reverse;

    .message-bubble { background: $accent-color; }
    .message-text { color: #fff; }
    .message-time { color: rgba(255,255,255,0.7); }
  }

  &.ai {
    align-self: flex-start;
  }
}

.message-avatar {
  flex-shrink: 0;
}

.avatar-circle {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;

  &.ai { background: #6366F1; color: #fff; }
  &.user { background: $accent-color; color: #fff; }
}

.message-bubble {
  background: $bg-muted;
  padding: 12px 16px;
  border-radius: 12px;
  min-width: 60px;
  overflow: hidden;
}

.message-text {
  font-size: 14px;
  line-height: 1.6;
  color: $text-primary;
  word-break: break-word;

  :deep(pre) {
    background: #1e1e2e;
    color: #cdd6f4;
    padding: 14px 16px;
    border-radius: 8px;
    overflow-x: auto;
    font-size: 13px;
    line-height: 1.5;
    margin: 8px 0;
  }

  :deep(code) {
    font-family: 'Fira Code', 'Consolas', monospace;
    font-size: 13px;
  }

  :deep(p code) {
    background: #f0f0f0;
    padding: 1px 6px;
    border-radius: 4px;
    color: $color-destructive;
  }

  :deep(p) {
    margin: 6px 0;
  }

  :deep(ul), :deep(ol) {
    padding-left: 20px;
    margin: 6px 0;
  }

  :deep(li) {
    margin: 3px 0;
  }

  :deep(strong) {
    font-weight: 600;
  }

  :deep(blockquote) {
    border-left: 3px solid $accent-color;
    padding-left: 12px;
    margin: 8px 0;
    color: #52525B;
  }
}

.message-time {
  font-size: 11px;
  color: $text-muted;
  margin-top: 6px;
  text-align: right;
}

.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 4px 0;

  span {
    width: 8px;
    height: 8px;
    background: $text-muted;
    border-radius: 50%;
    animation: bounce 1.4s infinite ease-in-out;

    &:nth-child(1) { animation-delay: -0.32s; }
    &:nth-child(2) { animation-delay: -0.16s; }
  }
}

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

.retry-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 24px;
  background: $bg-danger;
  border-top: 1px solid #FECACA;
}

.retry-icon {
  font-size: 14px;
}

.retry-text {
  flex: 1;
  font-size: 13px;
  color: $color-destructive;
}

.retry-btn {
  padding: 8px 16px;
  border: 1px solid $color-destructive;
  border-radius: 6px;
  background: #fff;
  color: $color-destructive;
  font-size: 13px;
  cursor: pointer;
  transition: all $transition-fast;
  white-space: nowrap;
  font-weight: 500;
}

.input-area {
  padding: 16px 24px;
  border-top: 1px solid $border-color;
  display: flex;
  gap: 12px;
  align-items: flex-end;
  background: #fff;
}

.voice-btn-wrapper {
  flex-shrink: 0;
  align-self: flex-end;
  margin-bottom: 4px;
}

.message-input {
  flex: 1;
  padding: 10px 14px;
  border: 1px solid $border-color;
  border-radius: 8px;
  font-size: 14px;
  color: $text-primary;
  resize: none;
  font-family: inherit;
  transition: border-color $transition-fast, height 0.2s ease;
  max-height: 200px;
  overflow-y: auto;
  line-height: 1.5;

  &:focus { outline: none; border-color: $accent-color; }
  &::placeholder { color: $text-tertiary; }
  &:disabled { background: $bg-muted; }
}

.send-btn {
  border: none;
  border-radius: 8px;
  background: $accent-color;
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all $transition-fast;
  white-space: nowrap;
  padding: 10px 20px;
  height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.3);

  &:hover:not(:disabled) {
    background: #1d4ed8;
    box-shadow: 0 4px 12px rgba(37, 99, 235, 0.4);
    transform: translateY(-1px);
  }

  &:active:not(:disabled) {
    transform: translateY(0);
    box-shadow: 0 2px 4px rgba(37, 99, 235, 0.3);
  }

  &:disabled {
    background: #a1a1aa;
    cursor: not-allowed;
    opacity: 0.6;
    box-shadow: none;
  }
}

@media (max-width: 768px) {
  .interview-session-page { padding: 12px; }
  .session-header { padding: 12px 16px; flex-wrap: wrap; }
  .header-center { max-width: 100%; order: 3; width: 100%; }
  .messages-container { padding: 16px; }
  .input-area { padding: 12px 16px; }
  .message-item { max-width: 92%; }
}
</style>
