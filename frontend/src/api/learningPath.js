import request from '@/utils/request'

/**
 * 获取当前学习路径
 */
export function getLearningPath() {
  return request({
    url: '/learning-path',
    method: 'get'
  })
}

/**
 * 从最新诊断报告刷新学习计划
 */
export function refreshLearningPath() {
  return request({
    url: '/learning-path/refresh',
    method: 'post'
  })
}

/**
 * 标记任务完成
 */
export function markTaskComplete(phaseIndex, taskIndex) {
  return request({
    url: '/learning-path/tasks/complete',
    method: 'put',
    data: { phaseIndex, taskIndex }
  })
}

/**
 * 取消任务完成标记
 */
export function unmarkTaskComplete(phaseIndex, taskIndex) {
  return request({
    url: '/learning-path/tasks/uncomplete',
    method: 'put',
    data: { phaseIndex, taskIndex }
  })
}

/**
 * 获取学习进度统计（给首页用）
 */
export function getLearningPathStats() {
  return request({
    url: '/learning-path/stats',
    method: 'get'
  })
}
