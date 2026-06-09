import request from '@/utils/request'
import { useUserStore } from '@/stores'

export function createInterview(data) {
  return request({
    url: '/interview/create',
    method: 'post',
    data
  })
}

export function sendMessage(interviewId, content) {
  return request({
    url: `/interview/${interviewId}/message`,
    method: 'post',
    data: { content }
  })
}

/**
 * 流式发送消息 — 使用 fetch POST + ReadableStream 解析 SSE
 * 返回 Promise，成功时 resolve(meta)，失败时 reject(error)
 */
export function sendMessageStream(interviewId, content, onChunk) {
  const userStore = useUserStore()
  const token = userStore.token

  return new Promise((resolve, reject) => {
    fetch(`/api/interview/${interviewId}/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({ content })
    }).then(async (response) => {
      if (!response.ok) {
        reject(new Error(`请求失败 (${response.status})`))
        return
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let eventType = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (line.startsWith('event: ')) {
            eventType = line.slice(7).trim()
          } else if (line.startsWith('data: ')) {
            const data = line.slice(6)
            if (eventType === 'chunk') {
              onChunk(data)
            } else if (eventType === 'meta') {
              try {
                const meta = JSON.parse(data)
                resolve(meta)
              } catch (e) {
                reject(new Error('解析元数据失败'))
              }
              return
            }
          }
        }
      }
      // 流正常结束但没有 meta（异常情况）
      reject(new Error('流意外结束'))
    }).catch((err) => {
      reject(err)
    })
  })
}

export function endInterview(interviewId) {
  return request({
    url: `/interview/${interviewId}/end`,
    method: 'post'
  })
}

export function abandonInterview(interviewId) {
  return request({
    url: `/interview/${interviewId}`,
    method: 'delete'
  })
}

export function getInterviewHistory(params) {
  return request({
    url: '/interview/history',
    method: 'get',
    params
  })
}

export function getSessionMessages(sessionId) {
  return request({
    url: `/interview/${sessionId}/messages`,
    method: 'get'
  })
}

/**
 * SSE 降级轮询接口：获取会话最新 AI 消息的生成状态
 */
export function pollStreamStatus(sessionId) {
  return request({
    url: `/interview/${sessionId}/poll`,
    method: 'get'
  })
}

export function getSessionInfo(sessionId) {
  return request({
    url: `/interview/${sessionId}/info`,
    method: 'get'
  })
}

export function getReportList(params) {
  return request({
    url: '/report/list',
    method: 'get',
    params
  })
}

export function getReportDetail(reportId) {
  return request({
    url: `/report/${reportId}`,
    method: 'get'
  })
}

export function deleteReport(reportId) {
  return request({
    url: `/report/${reportId}`,
    method: 'delete'
  })
}

export function getGrowthData(params = {}) {
  return request({
    url: '/report/growth',
    method: 'get',
    params
  })
}

export function getDashboardStats() {
  return request({
    url: '/dashboard/stats',
    method: 'get'
  })
}

export function getDashboardOverview(params = {}) {
  return request({
    url: '/dashboard/overview',
    method: 'get',
    params
  })
}

// ======================== AI 诊断报告 ========================

export function generateDiagnosis(sessionId) {
  return request({
    url: `/diagnosis/generate/${sessionId}`,
    method: 'post'
  })
}

export function getDiagnosisById(id) {
  return request({
    url: `/diagnosis/${id}`,
    method: 'get'
  })
}

export function getDiagnosisBySession(sessionId) {
  return request({
    url: `/diagnosis/session/${sessionId}`,
    method: 'get'
  })
}

export function getDiagnosisHistory(params) {
  return request({
    url: '/diagnosis/history',
    method: 'get',
    params
  })
}

export function getLatestDiagnosis() {
  return request({
    url: '/diagnosis/latest',
    method: 'get'
  })
}

// ======================== 面试计时 ========================

export function submitReportDurations(reportId, data) {
  return request({
    url: `/report/${reportId}/durations`,
    method: 'post',
    data
  })
}
