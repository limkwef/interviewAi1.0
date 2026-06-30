import request from '@/utils/request'

/** 用户获取可用模型列表（自己的 + 系统的） */
export function getEnabledModels() {
  return request({
    url: '/ai-models',
    method: 'get'
  })
}

/** 用户获取自己的模型列表 */
export function getUserModels() {
  return request({
    url: '/user/ai-models',
    method: 'get'
  })
}

/** 用户添加模型 */
export function addUserModel(data) {
  return request({
    url: '/user/ai-models',
    method: 'post',
    data
  })
}

/** 用户更新自己的模型 */
export function updateUserModel(id, data) {
  return request({
    url: `/user/ai-models/${id}`,
    method: 'put',
    data
  })
}

/** 用户删除自己的模型 */
export function deleteUserModel(id) {
  return request({
    url: `/user/ai-models/${id}`,
    method: 'delete'
  })
}

/** 用户测试自己的模型连通性 */
export function testUserModel(id) {
  return request({
    url: `/user/ai-models/${id}/test`,
    method: 'post'
  })
}
