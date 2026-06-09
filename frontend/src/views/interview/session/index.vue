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
            <span class="progress-text">第 <strong>{{ session.currentQuestion }}</strong> / {{ session.questionCount }} 题</span>
            <span class="progress-tag" :class="`progress-tag--${getTagType()}`">{{ tagLabel }}</span>
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
        <button class="end-btn" @click="handleEndInterview" :disabled="ending">
          {{ ending ? '结束中...' : '结束面试' }}
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
              v-if="msg.role === 'ai' && msg.content"
              :text="msg.content"
              :autoPlay="autoPlayTts && msg.id === lastAiMessageId"
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
          :disabled="sending"
          class="voice-btn-wrapper"
        />
        <textarea
          v-model="inputMessage"
          class="message-input"
          :placeholder="inputPlaceholder"
          @keydown.ctrl.enter="handleSend"
          :disabled="sending"
          rows="3"
        ></textarea>
        <button class="send-btn" @click="handleSend" :disabled="!inputMessage.trim() || sending">
          {{ sending ? '生成中' : '发送' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter, useRoute, onBeforeRouteLeave } from 'vue-router'
import { getSessionMessages, getSessionInfo, sendMessage, sendMessageStream, endInterview, abandonInterview, pollStreamStatus } from '@/api/interview'
import { submitReportDurations } from '@/api/interview'
import { useInterviewStore } from '@/stores/interview'
import { ElMessage, ElMessageBox } from 'element-plus'
import { marked } from 'marked'
import { positionMap, difficultyMap } from '@/utils/constants'
import VoiceRecorder from './components/VoiceRecorder.vue'
import TtsPlayer from './components/TtsPlayer.vue'

const interviewStore = useInterviewStore()
const setInterviewState = interviewStore.setInterviewState
const router = useRouter()
const route = useRoute()
const sessionId = route.params.id
const messageContainer = ref(null)
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
const pollingTimer = ref(null)        // 轮询降级定时器

// ======================== 计时器逻辑 ========================
const startTimestamp = ref(0)
const questionStartTimestamp = ref(0)
const totalElapsed = ref(0)
const currentQuestionElapsed = ref(0)
const questionDurations = ref({})
const questionTimer = ref(null)
const pausedSince = ref(0)
const totalPauseDuration = ref(0)



const tagLabel = computed(() => {
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
  await Promise.all([loadMessages(), loadSessionInfo()])
  startTimers()
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onBeforeUnmount(() => {
  stopTimers()
  stopPolling()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  setInterviewState(false)
  window.removeEventListener('beforeunload', handleBeforeUnload)
})

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
  ).then(() => {
    const action = hasUserAnswer ? endInterview(sessionId) : abandonInterview(sessionId)
    action.then((res) => {
      interviewEnded.value = true
      setInterviewState(false)
      // 如果是结束面试，跳转到报告页面
      if (hasUserAnswer && res?.code === 200 && res?.data?.reportId) {
        next({ path: `/report/detail/${res.data.reportId}`, replace: true })
      } else {
        next()
      }
    }).catch(() => {
      interviewEnded.value = true
      setInterviewState(false)
      next()
    })
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
    const res = await getSessionMessages(sessionId)
    if (res.code === 200) {
      messages.value = res.data || []
      updateLastMessageType()
      await nextTick()
      scrollToBottom()
    }
  } catch (e) {
    ElMessage.error('加载消息失败')
  }
}

async function loadSessionInfo() {
  try {
    const res = await getSessionInfo(sessionId)
    if (res.code === 200) {
      session.value = res.data
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

async function doSend(content, isRetry = false) {
  if (!content || !content.trim()) return
  if (sending.value && !isRetry) return

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

  // 先尝试流式 SSE
  let success = false
  try {
    const meta = await sendMessageStream(
      sessionId,
      content,
      (chunk) => {
        streamingContent.value += chunk
        nextTick(() => scrollToBottom())
      }
    )
    // 流式成功
    messages.value.push({
      id: meta.messageId || Date.now(),
      role: 'ai',
      content: streamingContent.value,
      messageType: meta.type || 'next_question',
      createdAt: new Date().toISOString()
    })
    streamingContent.value = ''
    sending.value = false
    lastMessageType.value = meta.type || ''
    if (meta.messageId) {
      lastAiMessageId.value = meta.messageId
    }
    if (meta.type === 'next_question') {
      recordQuestionDuration()
    }
    if (session.value && meta.nextQuestion) {
      session.value.currentQuestion = meta.nextQuestion
    }
    success = true
    await nextTick()
    scrollToBottom()
  } catch {
    // SSE 断开 → 自动降级为轮询
    if (streamingContent.value) {
      // SSE 已有部分内容，启动轮询继续获取剩余部分
      success = await startPollingFallback(content)
    }
  }

  // SSE 完全没收到内容 → 降级到轮询
  if (!success) {
    success = await startPollingFallback(content)
  }

  // 轮询也失败 → 最终降级到普通 POST
  if (!success) {
    try {
      const res = await sendMessage(sessionId, content)
      if (res.code === 200) {
        messages.value.push({
          id: res.data.id,
          role: 'ai',
          content: res.data.content,
          messageType: res.data.type || 'next_question',
          createdAt: res.data.createdAt
        })
        lastMessageType.value = res.data.type || ''
        if (session.value && res.data.nextQuestion) {
          session.value.currentQuestion = res.data.nextQuestion
        }
        success = true
        await nextTick()
        scrollToBottom()
      }
    } catch {
      // POST 也失败
    }
  }

  if (!success) {
    // 全部失败，设置重试
    ElMessage.error('发送失败，请重试')
    retryContent.value = content
  }

  sending.value = false
  sendingRetry.value = false
}

/**
 * SSE 断开后的轮询降级：定时查询后端 poll 接口，逐步获取 AI 回复
 * 返回 true 表示成功获取到完整回复
 */
async function startPollingFallback(content) {
  stopPolling()
  let lastContent = ''
  let pollCount = 0
  const MAX_POLL_COUNT = 120 // 最多轮询 120 次（约 2 分钟）
  const POLL_INTERVAL = 1000 // 1 秒轮询一次

  return new Promise((resolve) => {
    pollingTimer.value = setInterval(async () => {
      pollCount++
      if (pollCount > MAX_POLL_COUNT) {
        stopPolling()
        resolve(false)
        return
      }

      try {
        const res = await pollStreamStatus(sessionId)
        if (res.code !== 200) {
          stopPolling()
          resolve(false)
          return
        }

        const data = res.data
        if (data.status === 'pending') {
          // AI 还没开始生成，继续等待
          return
        }

        if (data.status === 'streaming') {
          // 仍在生成中，更新展示内容
          if (data.content && data.content !== lastContent) {
            lastContent = data.content
            streamingContent.value = data.content
            await nextTick()
            scrollToBottom()
          }
          return
        }

        if (data.status === 'completed') {
          // 生成完成
          stopPolling()
          streamingContent.value = ''
          messages.value.push({
            id: data.messageId || Date.now(),
            role: 'ai',
            content: data.content,
            messageType: data.type || 'next_question',
            createdAt: new Date().toISOString()
          })
          lastMessageType.value = data.type || ''
          if (data.messageId) {
            lastAiMessageId.value = data.messageId
          }
          if (data.type === 'next_question') {
            recordQuestionDuration()
          }
          if (session.value && data.nextQuestion) {
            session.value.currentQuestion = data.nextQuestion
          }
          await nextTick()
          scrollToBottom()
          resolve(true)
        }
      } catch {
        // 轮询请求失败，继续尝试
      }
    }, POLL_INTERVAL)
  })
}

function stopPolling() {
  if (pollingTimer.value) {
    clearInterval(pollingTimer.value)
    pollingTimer.value = null
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
  ending.value = true
  try {
    const res = await endInterview(sessionId)
    if (res.code === 200) {
      interviewEnded.value = true
      stopTimers()
      setInterviewState(false)
      if (res.data?.reportId) {
        submitDurations(res.data.reportId)  // fire-and-forget
      }
      // 使用 replace 而非 push，防止返回按钮回到已结束的面试页面
      router.replace(`/report/detail/${res.data.reportId}`)
    } else {
      ElMessage.error(res.message || '结束面试失败')
    }
  } catch (e) {
    ElMessage.error('结束面试失败')
  } finally {
    ending.value = false
  }
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
  transition: border-color $transition-fast;

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
