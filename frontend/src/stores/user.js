import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getUserInfo } from '@/api/user'

export const useUserStore = defineStore('user', () => {
  // 从两个存储中读取 token（优先 localStorage，兜底 sessionStorage）
  const token = ref(localStorage.getItem('token') || sessionStorage.getItem('token') || '')

  const storedUserInfo = localStorage.getItem('userInfo') || sessionStorage.getItem('userInfo')
  const userInfo = ref(storedUserInfo ? JSON.parse(storedUserInfo) : null)

  const isAdmin = computed(() => {
    return userInfo.value?.role === 'admin'
  })

  /**
   * 设置 token
   * @param {string} newToken
   * @param {boolean} remember - true 存 localStorage（持久），false 存 sessionStorage（关页面即失效）
   */
  function setToken(newToken, remember = true) {
    token.value = newToken
    // 先清除两处，避免残留
    localStorage.removeItem('token')
    sessionStorage.removeItem('token')
    if (remember) {
      localStorage.setItem('token', newToken)
    } else {
      sessionStorage.setItem('token', newToken)
    }
  }

  function removeToken() {
    token.value = ''
    localStorage.removeItem('token')
    sessionStorage.removeItem('token')
  }

  function setUserInfo(info) {
    userInfo.value = info
    if (info) {
      localStorage.setItem('userInfo', JSON.stringify(info))
      // 同步到 sessionStorage，方便无论何种存储都能读到
      sessionStorage.setItem('userInfo', JSON.stringify(info))
    } else {
      localStorage.removeItem('userInfo')
      sessionStorage.removeItem('userInfo')
    }
  }

  async function fetchUserInfo() {
    try {
      const res = await getUserInfo()
      userInfo.value = res.data
      const infoStr = JSON.stringify(res.data)
      localStorage.setItem('userInfo', infoStr)
      sessionStorage.setItem('userInfo', infoStr)
    } catch (err) {
      console.error('获取用户信息失败:', err)
    }
  }

  function logout() {
    removeToken()
    userInfo.value = null
    localStorage.removeItem('userInfo')
    sessionStorage.removeItem('userInfo')
  }

  return {
    token,
    userInfo,
    isAdmin,
    setToken,
    removeToken,
    setUserInfo,
    fetchUserInfo,
    logout
  }
})
