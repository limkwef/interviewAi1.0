import request from '@/utils/request'

/**
 * 获取竞争力分析（有缓存直接返回，无缓存则自动生成）
 */
export function getCompetitiveAnalysis() {
  return request({
    url: '/competitive-analysis',
    method: 'get'
  })
}

/**
 * 强制刷新竞争力分析（重新调用 AI 生成）
 */
export function refreshCompetitiveAnalysis() {
  return request({
    url: '/competitive-analysis/refresh',
    method: 'post'
  })
}

/**
 * 基于某次面试的竞争力分析（用于诊断页内嵌，按 position+round 精确对标）
 */
export function getSessionCompetition(sessionId) {
  return request({
    url: `/competitive-analysis/session/${sessionId}`,
    method: 'get'
  })
}
