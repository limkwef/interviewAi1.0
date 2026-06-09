import request from '@/utils/request'

export function getQuestionList(params) {
  return request({
    url: '/questions',
    method: 'get',
    params
  })
}

export function getQuestionDetail(id) {
  return request({
    url: `/questions/${id}`,
    method: 'get'
  })
}

export function getTags() {
  return request({
    url: '/tags',
    method: 'get'
  })
}

export function getFavorites(params) {
  return request({
    url: '/favorites',
    method: 'get',
    params
  })
}

export function addFavorite(questionId) {
  return request({
    url: `/favorites/${questionId}`,
    method: 'post'
  })
}

export function removeFavorite(questionId) {
  return request({
    url: `/favorites/${questionId}`,
    method: 'delete'
  })
}

export function checkFavorite(questionId) {
  return request({
    url: `/favorites/check/${questionId}`,
    method: 'get'
  })
}

export function getQuestionCount(params = {}) {
  return request({
    url: '/questions/count',
    method: 'get',
    params
  })
}
