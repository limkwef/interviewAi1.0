import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores'
import router from '@/router'

const service = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

service.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    // 禁用浏览器缓存
    if (config.method === 'get') {
      config.params = {
        ...config.params,
        _t: Date.now()
      }
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

service.interceptors.response.use(
  (response) => {
    const res = response.data
    const config = response.config || {}
    if (res.code && res.code !== 200) {
      // silent: 业务预期的错误（如诊断报告不存在/无权查看），不弹 toast
      if (!config.silent) {
        ElMessage.error(res.message || '请求失败')
      }
      // 登录接口的401不跳转，让业务层处理
      if (res.code === 401 && !(config.url || '').includes('/auth/login')) {
        const userStore = useUserStore()
        userStore.logout()
        router.push('/login')
      }
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  (error) => {
    const config = error.config || {}
    let message = '网络错误，请稍后重试'
    if (error.response) {
      const status = error.response.status
      const serverMessage = error.response.data?.message
      switch (status) {
        case 400:
          message = serverMessage || '请求参数错误'
          break
        case 401:
          // 登录接口透传后端错误消息（如"账号不存在"、"密码错误"）
          message = serverMessage || '未授权，请重新登录'
          // 只有非登录接口才执行logout和跳转
          if (!(config.url || '').includes('/auth/login')) {
            const userStore = useUserStore()
            userStore.logout()
            router.push('/login')
          }
          break
        case 403:
          message = serverMessage || '拒绝访问'
          break
        case 404:
          message = serverMessage || '请求资源不存在'
          break
        case 500:
          message = serverMessage || '服务器内部错误'
          break
        default:
          message = serverMessage || message
      }
    } else if (error.message.includes('timeout')) {
      message = '请求超时，请稍后重试'
    }
    // silent: 业务预期的错误不弹 toast
    if (!config.silent) {
      ElMessage.error(message)
    }
    return Promise.reject(error)
  }
)

export default service
