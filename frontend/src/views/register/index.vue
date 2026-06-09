<template>
  <div class="register-container">
    <div class="register-background">
      <div class="shape shape-1"></div>
      <div class="shape shape-2"></div>
      <div class="shape shape-3"></div>
      <div class="shape shape-4"></div>
    </div>

    <div class="register-card">
      <div class="card-header">
        <div class="logo-icon">
          <svg width="40" height="40" viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect width="40" height="40" rx="10" fill="url(#gradient1)"/>
            <path d="M12 16L20 12L28 16V24L20 28L12 24V16Z" stroke="white" stroke-width="2" fill="none"/>
            <circle cx="20" cy="20" r="3" fill="white"/>
            <defs>
              <linearGradient id="gradient1" x1="0" y1="0" x2="40" y2="40">
                <stop stop-color="#667eea"/>
                <stop offset="1" stop-color="#764ba2"/>
              </linearGradient>
            </defs>
          </svg>
        </div>
        <h1 class="register-title">创建账号</h1>
        <p class="register-subtitle">注册您的专属账号，开启AI面试之旅</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" class="register-form">
        <el-form-item prop="email">
          <div class="input-wrapper">
            <el-icon class="input-icon"><Message /></el-icon>
            <el-input
              v-model="form.email"
              placeholder="请输入邮箱"
              size="large"
            />
          </div>
        </el-form-item>

        <el-form-item prop="phone">
          <div class="input-wrapper">
            <el-icon class="input-icon"><Phone /></el-icon>
            <el-input
              v-model="form.phone"
              placeholder="请输入手机号（选填）"
              size="large"
            />
          </div>
        </el-form-item>

        <el-form-item prop="username">
          <div class="input-wrapper">
            <el-icon class="input-icon"><User /></el-icon>
            <el-input
              v-model="form.username"
              placeholder="请输入昵称（选填，不填将随机生成）"
              size="large"
            />
          </div>
        </el-form-item>

        <el-form-item prop="password">
          <div class="input-wrapper">
            <el-icon class="input-icon"><Lock /></el-icon>
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码（6-20位）"
              size="large"
              show-password
            />
          </div>
        </el-form-item>

        <el-form-item prop="confirmPassword">
          <div class="input-wrapper">
            <el-icon class="input-icon"><Lock /></el-icon>
            <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="请确认密码"
              size="large"
              show-password
            />
          </div>
        </el-form-item>

        <el-button type="primary" size="large" class="register-btn" @click="handleRegister" :loading="loading">
          注册
        </el-button>
      </el-form>

      <div class="card-footer">
        <p class="login-text">
          已有账号？
          <router-link to="/login" class="login-link">立即登录</router-link>
        </p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Message, Lock, Phone, User } from '@element-plus/icons-vue'
import { register } from '@/api/user'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  email: '',
  phone: '',
  username: '',
  password: '',
  confirmPassword: ''
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  phone: [
    {
      validator: (rule, value, callback) => {
        if (value && !/^1[3-9]\d{9}$/.test(value)) {
          callback(new Error('请输入正确的手机号格式'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  username: [
    { max: 50, message: '昵称不能超过50个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度应在6-20位之间', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

async function handleRegister() {
  try {
    await formRef.value.validate()
    loading.value = true
    await register({ email: form.email, password: form.password, phone: form.phone, username: form.username })
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch (err) {
    if (err !== false) {
      ElMessage.error(err.message || '注册失败')
    }
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.register-container {
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  overflow: hidden;
  font-family: $font-family;
}

.register-background {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 50%, #f093fb 100%);
  z-index: 0;
}

.shape {
  position: absolute;
  border-radius: 50%;
  opacity: 0.15;
  animation: float 20s infinite ease-in-out;
}

.shape-1 {
  width: 500px;
  height: 500px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  top: -200px;
  left: -200px;
  animation-delay: 0s;
}

.shape-2 {
  width: 400px;
  height: 400px;
  background: linear-gradient(135deg, #f093fb, #f5576c);
  bottom: -150px;
  right: -150px;
  animation-delay: -5s;
}

.shape-3 {
  width: 300px;
  height: 300px;
  background: linear-gradient(135deg, #4facfe, #00f2fe);
  top: 50%;
  right: 10%;
  animation-delay: -10s;
}

.shape-4 {
  width: 250px;
  height: 250px;
  background: linear-gradient(135deg, #43e97b, #38f9d7);
  bottom: 10%;
  left: 10%;
  animation-delay: -15s;
}

@keyframes float {
  0%, 100% {
    transform: translate(0, 0) rotate(0deg) scale(1);
  }
  25% {
    transform: translate(30px, -30px) rotate(90deg) scale(1.05);
  }
  50% {
    transform: translate(-20px, 20px) rotate(180deg) scale(0.95);
  }
  75% {
    transform: translate(15px, 15px) rotate(270deg) scale(1.02);
  }
}

.register-card {
  position: relative;
  z-index: 10;
  width: 480px;
  padding: 40px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-radius: 24px;
  box-shadow:
    0 20px 60px rgba(0, 0, 0, 0.15),
    0 0 0 1px rgba(255, 255, 255, 0.2);
  animation: cardSlideIn 0.6s ease-out;
}

@keyframes cardSlideIn {
  from {
    opacity: 0;
    transform: translateY(30px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.card-header {
  text-align: center;
  margin-bottom: 28px;
}

.logo-icon {
  margin-bottom: 16px;
  display: flex;
  justify-content: center;
}

.register-title {
  font-size: 28px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0 0 8px 0;
  letter-spacing: -0.5px;
}

.register-subtitle {
  font-size: 14px;
  color: #8b8fa3;
  margin: 0;
}

.register-form {
  .input-wrapper {
    position: relative;
    width: 100%;
  }

  .input-icon {
    position: absolute;
    left: 16px;
    top: 50%;
    transform: translateY(-50%);
    font-size: 18px;
    color: #8b8fa3;
    z-index: 10;
  }

  :deep(.el-input__wrapper) {
    padding-left: 44px;
    border-radius: 12px;
    box-shadow: 0 0 0 1px #e4e7ed inset;
    transition: all 0.3s ease;

    &:hover {
      box-shadow: 0 0 0 1px #c0c4cc inset;
    }

    &.is-focus {
      box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.3) inset;
    }
  }

  :deep(.el-form-item) {
    margin-bottom: 20px;
  }
}

.register-btn {
  width: 100%;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 12px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  transition: all 0.3s ease;
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.3);
  margin-top: 8px;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
  }

  &:active {
    transform: translateY(0);
  }
}

.card-footer {
  margin-top: 24px;
  text-align: center;

  .login-text {
    color: #8b8fa3;
    font-size: 14px;
    margin: 0;
  }

  .login-link {
    color: #667eea;
    text-decoration: none;
    font-weight: 600;
    transition: color 0.2s ease;

    &:hover {
      color: #764ba2;
    }
  }
}

@media (max-width: 480px) {
  .register-card {
    width: 100%;
    margin: 16px;
    padding: 24px;
    border-radius: 20px;
  }

  .register-title {
    font-size: 24px;
  }
}
</style>
