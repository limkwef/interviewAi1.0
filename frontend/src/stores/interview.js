import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useInterviewStore = defineStore('interview', () => {
  const isInInterview = ref(false)

  // ======================== 面试状态缓存（支持页面刷新恢复） ========================
  const cachedSessionId = ref(null)
  const cachedSession = ref(null)
  const cachedMessages = ref([])
  const cachedTimestamp = ref(0)  // 缓存时间戳，用于判断是否过期

  // 缓存有效期：30 分钟
  const CACHE_TTL_MS = 30 * 60 * 1000

  const isCacheValid = computed(() => {
    if (!cachedSessionId.value || !cachedTimestamp.value) return false
    return (Date.now() - cachedTimestamp.value) < CACHE_TTL_MS
  })

  function setInterviewState(state) {
    isInInterview.value = state
  }

  function resetInterviewState() {
    isInInterview.value = false
  }

  /**
   * 缓存面试状态（在消息更新、会话信息加载时调用）
   */
  function cacheInterviewState(sessionId, sessionData, messagesData) {
    cachedSessionId.value = sessionId
    cachedSession.value = sessionData ? { ...sessionData } : null
    cachedMessages.value = messagesData ? messagesData.map(m => ({ ...m })) : []
    cachedTimestamp.value = Date.now()
  }

  /**
   * 获取缓存的面试状态（页面恢复时调用）
   * 返回 null 表示缓存无效或不匹配
   */
  function getCachedState(sessionId) {
    if (!isCacheValid.value) return null
    if (cachedSessionId.value !== sessionId) return null
    return {
      session: cachedSession.value,
      messages: cachedMessages.value
    }
  }

  /**
   * 清除缓存（面试结束或放弃时调用）
   */
  function clearInterviewCache() {
    cachedSessionId.value = null
    cachedSession.value = null
    cachedMessages.value = []
    cachedTimestamp.value = 0
  }

  return {
    isInInterview, setInterviewState, resetInterviewState,
    cachedSessionId, cachedSession, cachedMessages, isCacheValid,
    cacheInterviewState, getCachedState, clearInterviewCache
  }
})
