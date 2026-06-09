import request from '@/utils/request'

export function getOptions() {
  return request({
    url: '/common/options',
    method: 'get'
  })
}

export function getDashboardData(params = {}) {
  return request({
    url: '/dashboard/overview',
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

export function getScoreTrend(params = {}) {
  return request({
    url: '/dashboard/score-trend',
    method: 'get',
    params
  })
}

export function getKnowledgeOverview(params = {}) {
  return request({
    url: '/dashboard/knowledge-overview',
    method: 'get',
    params
  })
}
