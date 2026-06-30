import request from '@/utils/request'

// 统计
export function getStatistics() {
  return request({
    url: '/admin/statistics',
    method: 'get'
  })
}

// 用户管理
export function getUserList(params) {
  return request({
    url: '/admin/users',
    method: 'get',
    params
  })
}

export function getUserById(id) {
  return request({
    url: `/admin/users/${id}`,
    method: 'get'
  })
}

export function updateUser(id, data) {
  return request({
    url: `/admin/users/${id}`,
    method: 'put',
    data
  })
}

export function updateUserStatus(id, status) {
  return request({
    url: `/admin/users/${id}/status`,
    method: 'put',
    params: { status }
  })
}

export function resetUserPassword(id, newPassword) {
  return request({
    url: `/admin/users/${id}/password`,
    method: 'put',
    data: { newPassword }
  })
}

export function deleteUser(id) {
  return request({
    url: `/admin/users/${id}`,
    method: 'delete'
  })
}

// 题库管理
export function getQuestionList(params) {
  return request({
    url: '/admin/questions',
    method: 'get',
    params
  })
}

export function getQuestionById(id) {
  return request({
    url: `/admin/questions/${id}`,
    method: 'get'
  })
}

export function addQuestion(data) {
  return request({
    url: '/admin/questions',
    method: 'post',
    data
  })
}

export function updateQuestion(id, data) {
  return request({
    url: `/admin/questions/${id}`,
    method: 'put',
    data
  })
}

export function deleteQuestion(id) {
  return request({
    url: `/admin/questions/${id}`,
    method: 'delete'
  })
}

export function batchImportQuestions(data) {
  return request({
    url: '/admin/questions/batch',
    method: 'post',
    data
  })
}

// 操作日志
export function getLogList(params) {
  return request({
    url: '/admin/logs',
    method: 'get',
    params
  })
}

// AI模型管理
export function getAiModelList() {
  return request({
    url: '/admin/ai-models',
    method: 'get'
  })
}

export function getAiModelById(id) {
  return request({
    url: `/admin/ai-models/${id}`,
    method: 'get'
  })
}

export function addAiModel(data) {
  return request({
    url: '/admin/ai-models',
    method: 'post',
    data
  })
}

export function updateAiModel(id, data) {
  return request({
    url: `/admin/ai-models/${id}`,
    method: 'put',
    data
  })
}

export function deleteAiModel(id) {
  return request({
    url: `/admin/ai-models/${id}`,
    method: 'delete'
  })
}

export function setDefaultAiModel(id) {
  return request({
    url: `/admin/ai-models/${id}/set-default`,
    method: 'post'
  })
}

export function testAiModel(id) {
  return request({
    url: `/admin/ai-models/${id}/test`,
    method: 'post',
    timeout: 30000
  })
}

// 反馈管理
export function getFeedbackList(params) {
  return request({
    url: '/admin/feedback',
    method: 'get',
    params
  })
}

export function updateFeedbackStatus(id, status) {
  return request({
    url: `/admin/feedback/${id}/status`,
    method: 'put',
    params: { status }
  })
}

export function deleteFeedback(id) {
  return request({
    url: `/admin/feedback/${id}`,
    method: 'delete'
  })
}
