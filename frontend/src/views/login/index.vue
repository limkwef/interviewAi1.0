<template>
  <div class="login-container">
    <!-- 动态背景 -->
    <div class="login-background">
      <div class="gradient-bg"></div>
      <div class="particles">
        <div v-for="i in 30" :key="i" class="particle" :style="getParticleStyle(i)"></div>
      </div>
      <div class="floating-shapes">
        <div class="shape shape-1">
          <svg viewBox="0 0 200 200" xmlns="http://www.w3.org/2000/svg">
            <path fill="rgba(255,255,255,0.08)" d="M44.7,-76.4C58.8,-69.2,71.8,-58.7,79.6,-45.3C87.4,-31.9,90,-15.9,88.2,-0.9C86.4,14.1,80.2,28.2,72.3,40.1C64.4,52,54.8,61.7,43.2,68.5C31.6,75.3,18,79.2,3.5,79.5C-11,79.8,-26.5,76.5,-39.7,70C-52.9,63.5,-63.8,53.8,-72.4,42.1C-81,30.4,-87.3,16.7,-87.7,2.3C-88.1,-12.1,-82.6,-24.2,-74.5,-34.4C-66.4,-44.6,-55.7,-52.9,-43.7,-60.2C-31.7,-67.5,-18.5,-73.8,-2.3,-70.7C13.9,-67.6,30.6,-55.1,44.7,-76.4Z" transform="translate(100 100)" />
          </svg>
        </div>
        <div class="shape shape-2">
          <svg viewBox="0 0 200 200" xmlns="http://www.w3.org/2000/svg">
            <path fill="rgba(255,255,255,0.06)" d="M39.5,-67.3C52.9,-60.2,66.5,-53.3,74.8,-42.1C83.1,-30.9,86.1,-15.5,84.4,-0.9C82.7,13.7,76.3,27.4,68.7,39.8C61.1,52.2,52.3,63.3,40.7,70.2C29.1,77.1,14.5,79.8,0.6,79C-13.3,78.2,-26.6,73.9,-38.3,67.1C-50,60.3,-60.1,51,-67.5,39.7C-74.9,28.4,-79.6,15.2,-80.1,1.5C-80.6,-12.2,-76.9,-24.4,-70.5,-35.2C-64.1,-46,-55,-55.4,-43.6,-62.5C-32.2,-69.6,-18.5,-74.4,-3,-70.5C12.5,-66.6,26.1,-53.9,39.5,-67.3Z" transform="translate(100 100)" />
          </svg>
        </div>
        <div class="shape shape-3">
          <svg viewBox="0 0 200 200" xmlns="http://www.w3.org/2000/svg">
            <path fill="rgba(255,255,255,0.05)" d="M47.7,-79.1C62.3,-72.5,74.5,-60.1,81.4,-45.4C88.3,-30.7,89.9,-13.7,87.2,1.7C84.5,17.1,77.5,31,68.4,43.1C59.3,55.2,48.1,65.5,35,72.3C21.9,79.1,6.9,82.4,-8.5,82.2C-23.9,82,-39.7,78.3,-52.4,70.5C-65.1,62.7,-74.7,50.8,-80.2,37.3C-85.7,23.8,-87.1,8.7,-84.5,-5.3C-81.9,-19.3,-75.3,-32.2,-66.2,-43.2C-57.1,-54.2,-45.5,-63.3,-32.7,-70.3C-19.9,-77.3,-6,-82.2,5.6,-80.5C17.2,-78.8,33.1,-70.5,47.7,-79.1Z" transform="translate(100 100)" />
          </svg>
        </div>
      </div>
    </div>

    <!-- 登录卡片 -->
    <div class="login-card" :class="{ 'card-enter': isCardVisible }">
      <!-- 卡片光效 -->
      <div class="card-glow"></div>
      
      <div class="card-header">
        <div class="logo-container">
          <div class="logo-icon">
            <svg width="48" height="48" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
              <defs>
                <linearGradient id="logoGradient" x1="0" y1="0" x2="48" y2="48">
                  <stop stop-color="#667eea"/>
                  <stop offset="1" stop-color="#764ba2"/>
                </linearGradient>
              </defs>
              <rect width="48" height="48" rx="12" fill="url(#logoGradient)"/>
              <path d="M14 20L24 14L34 20V28L24 34L14 28V20Z" stroke="white" stroke-width="2.5" fill="none"/>
              <circle cx="24" cy="24" r="4" fill="white"/>
              <path d="M24 20V28M20 24H28" stroke="url(#logoGradient)" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </div>
          <div class="logo-pulse"></div>
        </div>
        <h1 class="login-title">
          <span class="title-text">欢迎回来</span>
          <span class="title-decoration"></span>
        </h1>
        <p class="login-subtitle">登录您的账号，开始AI面试之旅</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" class="login-form">
        <div class="form-group">
          <label class="form-label">账号</label>
          <el-form-item prop="account">
            <div class="input-wrapper" :class="{ 'input-focus': accountFocused }">
              <el-icon class="input-icon"><User /></el-icon>
              <el-input
                v-model="form.account"
                placeholder="请输入邮箱或手机号"
                size="large"
                @focus="accountFocused = true"
                @blur="accountFocused = false"
              />
            </div>
          </el-form-item>
        </div>

        <div class="form-group">
          <label class="form-label">密码</label>
          <el-form-item prop="password">
            <div class="input-wrapper" :class="{ 'input-focus': passwordFocused }">
              <el-icon class="input-icon"><Lock /></el-icon>
              <el-input
                v-model="form.password"
                type="password"
                placeholder="请输入密码"
                size="large"
                show-password
                @focus="passwordFocused = true"
                @blur="passwordFocused = false"
              />
            </div>
          </el-form-item>
        </div>

        <div class="form-options">
          <el-checkbox v-model="rememberMe" class="remember-me">
            <span class="checkbox-custom"></span>
            记住我
          </el-checkbox>
          <span class="forgot-link" @click="handleForgotPassword">忘记密码？</span>
        </div>

        <el-button type="primary" size="large" class="login-btn" @click="handleLogin" :loading="loading">
          <span class="btn-text">登录</span>
          <span class="btn-icon">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M5 12h14M12 5l7 7-7 7"/>
            </svg>
          </span>
        </el-button>
      </el-form>

      <div class="card-footer">
        <p class="register-text">
          还没有账号？
          <router-link to="/register" class="register-link">
            立即注册
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M5 12h14M12 5l7 7-7 7"/>
            </svg>
          </router-link>
        </p>
      </div>
    </div>

    <!-- 装饰元素 -->
    <div class="decoration">
      <div class="deco-circle deco-1"></div>
      <div class="deco-circle deco-2"></div>
      <div class="deco-line deco-3"></div>
      <div class="deco-line deco-4"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores'
import { login } from '@/api/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const formRef = ref(null)
const loading = ref(false)
const rememberMe = ref(false)
const accountFocused = ref(false)
const passwordFocused = ref(false)
const isCardVisible = ref(false)

const form = reactive({
  account: '',
  password: ''
})

const rules = {
  account: [
    { required: true, message: '请输入邮箱或手机号', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        const emailReg = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
        const phoneReg = /^1[3-9]\d{9}$/
        if (!emailReg.test(value) && !phoneReg.test(value)) {
          callback(new Error('请输入正确的邮箱或手机号格式'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ]
}

// 生成粒子样式
function getParticleStyle(index) {
  const size = Math.random() * 4 + 2
  const left = Math.random() * 100
  const top = Math.random() * 100
  const delay = Math.random() * 15
  const duration = Math.random() * 20 + 15
  
  return {
    width: `${size}px`,
    height: `${size}px`,
    left: `${left}%`,
    top: `${top}%`,
    animationDelay: `${delay}s`,
    animationDuration: `${duration}s`
  }
}

onMounted(() => {
  // 添加卡片进入动画
  setTimeout(() => {
    isCardVisible.value = true
  }, 100)
})

async function handleLogin() {
  try {
    await formRef.value.validate()
    loading.value = true
    const res = await login({ account: form.account, password: form.password })
    userStore.setToken(res.data.token, rememberMe.value)
    await userStore.fetchUserInfo()
    ElMessage.success('登录成功')
    const redirect = route.query.redirect || '/dashboard'
    router.push(redirect)
  } catch (err) {
    // 错误消息已在 axios 拦截器中统一展示，此处不再重复提示
    console.error('登录失败：', err)
  } finally {
    loading.value = false
  }
}

function handleForgotPassword() {
  ElMessage.info('此功能正在开发中')
}
</script>

<style lang="scss" scoped>
.login-container {
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  overflow: hidden;
  font-family: $font-family;
}

// 动态背景
.login-background {
  position: fixed;
  inset: 0;
  z-index: 0;
}

.gradient-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
  
  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background: 
      radial-gradient(circle at 20% 80%, rgba(102, 126, 234, 0.3) 0%, transparent 50%),
      radial-gradient(circle at 80% 20%, rgba(118, 75, 162, 0.3) 0%, transparent 50%),
      radial-gradient(circle at 50% 50%, rgba(240, 147, 251, 0.1) 0%, transparent 50%);
  }
}

// 粒子效果
.particles {
  position: absolute;
  inset: 0;
  z-index: 1;
}

.particle {
  position: absolute;
  background: rgba(255, 255, 255, 0.6);
  border-radius: 50%;
  animation: particleFloat linear infinite;
  pointer-events: none;
}

@keyframes particleFloat {
  0% {
    transform: translateY(100vh) rotate(0deg);
    opacity: 0;
  }
  10% {
    opacity: 1;
  }
  90% {
    opacity: 1;
  }
  100% {
    transform: translateY(-100vh) rotate(720deg);
    opacity: 0;
  }
}

// 浮动形状
.floating-shapes {
  position: absolute;
  inset: 0;
  z-index: 2;
}

.shape {
  position: absolute;
  opacity: 0.8;
  animation: shapeFloat 20s ease-in-out infinite;
  
  svg {
    width: 100%;
    height: 100%;
  }
}

.shape-1 {
  width: 400px;
  height: 400px;
  top: -100px;
  left: -100px;
  animation-delay: 0s;
}

.shape-2 {
  width: 300px;
  height: 300px;
  bottom: -50px;
  right: -50px;
  animation-delay: -5s;
}

.shape-3 {
  width: 250px;
  height: 250px;
  top: 50%;
  right: 10%;
  animation-delay: -10s;
}

@keyframes shapeFloat {
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

// 登录卡片
.login-card {
  position: relative;
  z-index: 10;
  width: 440px;
  padding: 48px 40px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px) saturate(180%);
  border-radius: 24px;
  box-shadow:
    0 20px 60px rgba(0, 0, 0, 0.3),
    0 0 0 1px rgba(255, 255, 255, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 0.2);
  opacity: 0;
  transform: translateY(40px) scale(0.95);
  transition: all 0.6s cubic-bezier(0.34, 1.56, 0.64, 1);
  
  &.card-enter {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.card-glow {
  position: absolute;
  top: -2px;
  left: -2px;
  right: -2px;
  bottom: -2px;
  background: linear-gradient(135deg, #667eea, #764ba2, #f093fb, #667eea);
  background-size: 300% 300%;
  border-radius: 26px;
  z-index: -1;
  animation: glowRotate 5s ease infinite;
  opacity: 0.7;
  filter: blur(10px);
}

@keyframes glowRotate {
  0% {
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0% 50%;
  }
}

.card-header {
  text-align: center;
  margin-bottom: 36px;
}

.logo-container {
  position: relative;
  display: inline-block;
  margin-bottom: 24px;
}

.logo-icon {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  width: 80px;
  height: 80px;
  margin: 0 auto;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e8ec 100%);
  border-radius: 20px;
  box-shadow: 0 8px 24px rgba(102, 126, 234, 0.2);
  animation: logoPulse 2s ease-in-out infinite;
}

.logo-pulse {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 80px;
  height: 80px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  border-radius: 20px;
  animation: pulse 2s ease-in-out infinite;
  z-index: 0;
}

@keyframes logoPulse {
  0%, 100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.05);
  }
}

@keyframes pulse {
  0% {
    transform: translate(-50%, -50%) scale(1);
    opacity: 0.5;
  }
  50% {
    transform: translate(-50%, -50%) scale(1.15);
    opacity: 0;
  }
  100% {
    transform: translate(-50%, -50%) scale(1);
    opacity: 0;
  }
}

.login-title {
  position: relative;
  font-size: 32px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0 0 8px 0;
  letter-spacing: -0.5px;
}

.title-text {
  position: relative;
  z-index: 1;
}

.title-decoration {
  position: absolute;
  bottom: -4px;
  left: 50%;
  transform: translateX(-50%);
  width: 60px;
  height: 4px;
  background: linear-gradient(90deg, #667eea, #764ba2);
  border-radius: 2px;
  animation: titleUnderline 2s ease-in-out infinite;
}

@keyframes titleUnderline {
  0%, 100% {
    width: 60px;
    opacity: 1;
  }
  50% {
    width: 80px;
    opacity: 0.8;
  }
}

.login-subtitle {
  font-size: 15px;
  color: #333;
  margin: 0;
  margin-top: 8px;
}

// 表单样式
.login-form {
  .form-group {
    margin-bottom: 20px;
  }
  
  .form-label {
    display: block;
    font-size: 13px;
    font-weight: 600;
    color: #4a5568;
    margin-bottom: 8px;
    letter-spacing: 0.5px;
    text-transform: uppercase;
  }
  
  .input-wrapper {
    position: relative;
    width: 100%;
    transition: all 0.3s ease;
    
    &.input-focus {
      transform: translateY(-2px);
      
      .input-icon {
        color: #667eea;
        transform: translateY(-50%) scale(1.1);
      }
    }
  }
  
  .input-icon {
    position: absolute;
    left: 16px;
    top: 50%;
    transform: translateY(-50%);
    font-size: 20px;
    color: #a0aec0;
    z-index: 10;
    transition: all 0.3s ease;
  }
  
  :deep(.el-input__wrapper) {
    padding-left: 48px;
    padding-right: 48px;
    height: 56px;
    border-radius: 14px;
    box-shadow: 
      0 0 0 2px #e2e8f0 inset,
      0 2px 4px rgba(0, 0, 0, 0.05);
    transition: all 0.3s ease;
    background: #fff;
    
    &:hover {
      box-shadow: 
        0 0 0 2px #cbd5e0 inset,
        0 4px 8px rgba(0, 0, 0, 0.08);
    }
    
    &.is-focus {
      box-shadow: 
        0 0 0 2px rgba(102, 126, 234, 0.4) inset,
        0 4px 12px rgba(102, 126, 234, 0.15);
    }
  }
  
  :deep(.el-input__inner) {
    font-size: 15px;
    color: #2d3748;
    
    &::placeholder {
      color: #a0aec0;
    }
  }
  
  :deep(.el-input__suffix) {
    color: #a0aec0;
    
    .el-icon {
      font-size: 18px;
      
      &:hover {
        color: #667eea;
      }
    }
  }
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 28px;
  
  :deep(.el-checkbox__label) {
    display: flex;
    align-items: center;
    gap: 6px;
  }
  
  .remember-me {
    color: #4a5568;
    font-size: 14px;
    font-weight: 500;
    
    :deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
      background: linear-gradient(135deg, #667eea, #764ba2);
      border-color: transparent;
    }
  }
  
  .forgot-link {
    font-size: 14px;
    color: #667eea;
    text-decoration: none;
    font-weight: 500;
    transition: all 0.2s ease;
    cursor: pointer;
    
    &:hover {
      color: #764ba2;
      text-decoration: underline;
    }
  }
}

// 登录按钮
.login-btn {
  width: 100%;
  height: 56px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 14px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  transition: all 0.3s ease;
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  position: relative;
  overflow: hidden;
  
  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: -100%;
    width: 100%;
    height: 100%;
    background: linear-gradient(90deg, transparent, rgba(255,255,255,0.2), transparent);
    transition: left 0.5s ease;
  }
  
  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 8px 25px rgba(102, 126, 234, 0.4);
    
    &::before {
      left: 100%;
    }
    
    .btn-icon {
      transform: translateX(4px);
    }
  }
  
  &:active {
    transform: translateY(0);
    box-shadow: 0 4px 15px rgba(102, 126, 234, 0.3);
  }
  
  .btn-text {
    position: relative;
    z-index: 1;
  }
  
  .btn-icon {
    position: relative;
    z-index: 1;
    display: flex;
    align-items: center;
    transition: transform 0.3s ease;
  }
}

// 卡片底部
.card-footer {
  text-align: center;
  
  .register-text {
    color: #333;
    font-size: 14px;
    margin: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
  }
  
  .register-link {
    color: #667eea;
    text-decoration: none;
    font-weight: 600;
    transition: all 0.2s ease;
    display: inline-flex;
    align-items: center;
    gap: 4px;
    
    &:hover {
      color: #764ba2;
      
      svg {
        transform: translateX(3px);
      }
    }
    
    svg {
      transition: transform 0.2s ease;
    }
  }
}

// 装饰元素
.decoration {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 5;
}

.deco-circle {
  position: absolute;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.1);
}

.deco-1 {
  width: 100px;
  height: 100px;
  top: 10%;
  right: 15%;
  animation: decoRotate 15s linear infinite;
}

.deco-2 {
  width: 60px;
  height: 60px;
  bottom: 15%;
  left: 10%;
  animation: decoRotate 12s linear infinite reverse;
}

.deco-line {
  position: absolute;
  background: rgba(255, 255, 255, 0.08);
}

.deco-3 {
  width: 150px;
  height: 2px;
  top: 20%;
  left: 5%;
  transform: rotate(-45deg);
  animation: decoPulse 3s ease-in-out infinite;
}

.deco-4 {
  width: 100px;
  height: 2px;
  bottom: 25%;
  right: 10%;
  transform: rotate(45deg);
  animation: decoPulse 3s ease-in-out infinite 1.5s;
}

@keyframes decoRotate {
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
}

@keyframes decoPulse {
  0%, 100% {
    opacity: 0.5;
    transform: rotate(-45deg) scaleX(1);
  }
  50% {
    opacity: 1;
    transform: rotate(-45deg) scaleX(1.2);
  }
}

// 响应式设计
@media (max-width: 480px) {
  .login-card {
    width: calc(100% - 32px);
    margin: 16px;
    padding: 32px 24px;
    border-radius: 20px;
  }
  
  .login-title {
    font-size: 26px;
  }
  
  .form-group {
    margin-bottom: 16px;
  }
  
  :deep(.el-input__wrapper) {
    height: 52px;
  }
  
  .login-btn {
    height: 52px;
  }
  
  .shape-1 {
    width: 200px;
    height: 200px;
  }
  
  .shape-2 {
    width: 150px;
    height: 150px;
  }
  
  .shape-3 {
    display: none;
  }
}

// 动画优化
@media (prefers-reduced-motion: reduce) {
  .particle,
  .shape,
  .logo-pulse,
  .card-glow,
  .deco-circle,
  .deco-line {
    animation: none !important;
  }
  
  .login-card {
    transition: opacity 0.3s ease;
    transform: none;
  }
}
</style>
