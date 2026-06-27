import request from '@/utils/request'

/**
 * 上传简历文件（PDF/TXT）
 */
export function uploadResume(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/resume/upload',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/**
 * 在线填写创建简历
 */
export function createResume(data) {
  return request({
    url: '/resume/create',
    method: 'post',
    data
  })
}

/**
 * 简历列表
 */
export function getResumeList(params) {
  return request({
    url: '/resume/list',
    method: 'get',
    params
  })
}

/**
 * 简历详情
 */
export function getResumeDetail(id) {
  return request({
    url: `/resume/${id}`,
    method: 'get'
  })
}

/**
 * 激活简历
 */
export function activateResume(id) {
  return request({
    url: `/resume/${id}/activate`,
    method: 'put'
  })
}

/**
 * 删除简历
 */
export function deleteResume(id) {
  return request({
    url: `/resume/${id}`,
    method: 'delete'
  })
}

/**
 * 查询解析状态（轮询）
 */
export function getResumeStatus(id) {
  return request({
    url: `/resume/${id}/status`,
    method: 'get'
  })
}

/**
 * 重新解析简历
 */
export function reparseResume(id) {
  return request({
    url: `/resume/${id}/reparse`,
    method: 'post'
  })
}
