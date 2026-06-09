<template>
  <div class="profile-page">
    <div class="profile-header">
      <div class="profile-header__info">
        <el-avatar :size="64" :src="userStore.userInfo?.avatar">
          <span class="profile-header__fallback">
            {{ (userStore.userInfo?.username || 'U')[0].toUpperCase() }}
          </span>
        </el-avatar>
        <div class="profile-header__text">
          <h2 class="profile-header__name">{{ userStore.userInfo?.username || '未登录' }}</h2>
          <span class="profile-header__email">{{ userStore.userInfo?.email || '' }}</span>
        </div>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="profile-tabs">
      <el-tab-pane label="基本信息" name="basic">
        <div class="tab-content">
          <div class="basic-info">
            <div class="avatar-section">
              <div class="avatar-wrapper" @click="triggerFileInput">
                <el-avatar :size="100" :src="formData.avatar" class="avatar-display">
                  <span class="avatar-fallback">
                    {{ (formData.username || 'U')[0].toUpperCase() }}
                  </span>
                </el-avatar>
                <div class="avatar-overlay" :class="{ 'is-loading': avatarLoading }">
                  <el-icon v-if="!avatarLoading" class="avatar-overlay__icon"><Camera /></el-icon>
                  <el-icon v-else class="avatar-overlay__icon is-spinning"><Loading /></el-icon>
                  <span v-if="!avatarLoading" class="avatar-overlay__text">更换头像</span>
                </div>
                <input
                  ref="fileInputRef"
                  type="file"
                  accept="image/*"
                  class="avatar-input"
                  @change="handleAvatarChange"
                />
              </div>
            </div>

            <el-form
              ref="profileFormRef"
              :model="formData"
              :rules="profileRules"
              label-position="top"
              class="profile-form"
            >
              <el-form-item label="用户名" prop="username">
                <el-input
                  v-model="formData.username"
                  placeholder="请输入用户名"
                  :disabled="saving"
                />
              </el-form-item>

              <el-form-item label="邮箱">
                <el-input
                  :model-value="formData.email"
                  disabled
                  placeholder="邮箱不可修改"
                >
                  <template #prefix>
                    <el-icon><Lock /></el-icon>
                  </template>
                </el-input>
              </el-form-item>

              <el-form-item label="手机号" prop="phone">
                <el-input
                  v-model="formData.phone"
                  placeholder="请输入手机号"
                  :disabled="saving"
                />
              </el-form-item>

              <el-form-item label="目标岗位" prop="targetPosition">
                <el-select
                  v-model="formData.targetPosition"
                  placeholder="请选择目标岗位"
                  style="width: 100%"
                  :disabled="saving"
                >
                  <el-option
                    v-for="item in positionOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>

              <el-form-item label="技术栈" prop="techStack">
                <el-select
                  v-model="formData.techStack"
                  multiple
                  placeholder="请选择技术栈"
                  style="width: 100%"
                  :disabled="saving"
                >
                  <el-option
                    v-for="item in techStackOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>

              <el-form-item>
                <el-button
                  type="primary"
                  :loading="saving"
                  @click="handleSaveProfile"
                >
                  保存修改
                </el-button>
              </el-form-item>
            </el-form>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="安全设置" name="security">
        <div class="tab-content">
          <div class="security-section">
            <h3 class="section-title">修改密码</h3>
            <el-form
              ref="passwordFormRef"
              :model="passwordForm"
              :rules="passwordRules"
              label-position="top"
              class="password-form"
            >
              <el-form-item label="旧密码" prop="oldPassword">
                <el-input
                  v-model="passwordForm.oldPassword"
                  type="password"
                  show-password
                  placeholder="请输入旧密码"
                  :disabled="changingPassword"
                />
              </el-form-item>

              <el-form-item label="新密码" prop="newPassword">
                <el-input
                  v-model="passwordForm.newPassword"
                  type="password"
                  show-password
                  placeholder="请输入新密码（至少6位）"
                  :disabled="changingPassword"
                />
              </el-form-item>

              <el-form-item label="确认新密码" prop="confirmPassword">
                <el-input
                  v-model="passwordForm.confirmPassword"
                  type="password"
                  show-password
                  placeholder="请再次输入新密码"
                  :disabled="changingPassword"
                />
              </el-form-item>

              <el-form-item>
                <el-button
                  type="primary"
                  :loading="changingPassword"
                  @click="handleChangePassword"
                >
                  修改密码
                </el-button>
              </el-form-item>
            </el-form>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="帮助与反馈" name="help">
        <div class="tab-content">
          <div class="help-section">
            <h3 class="section-title">帮助中心</h3>
            <el-collapse class="faq-collapse">
              <el-collapse-item title="如何开始模拟面试？" name="1">
                <p>在左侧菜单点击「模拟面试」，选择面试类型（Java后端/前端/全栈/数据库），配置面试轮次和时间后点击「开始面试」即可。</p>
              </el-collapse-item>
              <el-collapse-item title="面试报告在哪里查看？" name="2">
                <p>面试完成后系统会自动生成报告。您也可以在左侧菜单点击「面试报告」查看所有历史报告。</p>
              </el-collapse-item>
              <el-collapse-item title="如何收藏题目？" name="3">
                <p>在题库中心页面，每道题目右侧有收藏按钮（星标图标），点击即可收藏。收藏的题目可以在「我的收藏」中查看。</p>
              </el-collapse-item>
              <el-collapse-item title="如何修改个人信息？" name="4">
                <p>在个人中心的「基本信息」Tab中，您可以修改用户名、手机号、目标岗位和技术栈。邮箱地址不支持修改。</p>
              </el-collapse-item>
              <el-collapse-item title="忘记密码怎么办？" name="5">
                <p>请联系管理员重置密码，或通过注册邮箱找回。</p>
              </el-collapse-item>
            </el-collapse>
          </div>

          <div class="feedback-section">
            <h3 class="section-title">意见反馈</h3>
            <el-form
              ref="feedbackFormRef"
              :model="feedbackForm"
              :rules="feedbackRules"
              label-position="top"
              class="feedback-form"
            >
              <el-form-item label="反馈类型" prop="type">
                <el-select v-model="feedbackForm.type" placeholder="请选择反馈类型" style="width: 100%">
                  <el-option label="功能建议" value="功能建议" />
                  <el-option label="Bug反馈" value="Bug反馈" />
                  <el-option label="体验问题" value="体验问题" />
                  <el-option label="其他" value="其他" />
                </el-select>
              </el-form-item>
              <el-form-item label="反馈内容" prop="content">
                <el-input
                  v-model="feedbackForm.content"
                  type="textarea"
                  :rows="4"
                  placeholder="请详细描述您的问题或建议..."
                  maxlength="500"
                  show-word-limit
                />
              </el-form-item>
              <el-form-item label="联系方式（选填）" prop="contact">
                <el-input
                  v-model="feedbackForm.contact"
                  placeholder="邮箱或手机号，方便我们联系您"
                />
              </el-form-item>
              <el-form-item>
                <el-button
                  type="primary"
                  :loading="submittingFeedback"
                  @click="handleSubmitFeedback"
                >
                  提交反馈
                </el-button>
              </el-form-item>
            </el-form>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Camera, Loading, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores'
import { updateUserInfo, changePassword, uploadAvatar } from '@/api/user'
import request from '@/utils/request'
import { positionMap } from '@/utils/constants'

const userStore = useUserStore()

const activeTab = ref('basic')
const saving = ref(false)
const changingPassword = ref(false)
const avatarLoading = ref(false)

const profileFormRef = ref(null)
const passwordFormRef = ref(null)
const fileInputRef = ref(null)

const positionOptions = Object.entries(positionMap).map(([value, label]) => ({ label, value }))

const techStackOptions = [
  { label: 'Java', value: 'Java' },
  { label: 'Python', value: 'Python' },
  { label: 'Vue.js', value: 'Vue.js' },
  { label: 'React', value: 'React' },
  { label: 'MySQL', value: 'MySQL' },
  { label: 'Redis', value: 'Redis' },
  { label: 'Spring Boot', value: 'Spring Boot' },
  { label: 'Node.js', value: 'Node.js' },
  { label: 'Docker', value: 'Docker' },
  { label: 'Git', value: 'Git' }
]

const formData = reactive({
  username: '',
  email: '',
  phone: '',
  avatar: '',
  targetPosition: '',
  techStack: []
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const feedbackFormRef = ref(null)
const submittingFeedback = ref(false)

const feedbackForm = reactive({
  type: '功能建议',
  content: '',
  contact: ''
})

const feedbackRules = {
  type: [
    { required: true, message: '请选择反馈类型', trigger: 'change' }
  ],
  content: [
    { required: true, message: '请输入反馈内容', trigger: 'blur' },
    { min: 10, message: '反馈内容至少10个字符', trigger: 'blur' }
  ]
}

const validatePhone = (rule, value, callback) => {
  if (!value) {
    callback()
    return
  }
  const phoneReg = /^1[3-9]\d{9}$/
  if (!phoneReg.test(value)) {
    callback(new Error('请输入正确的手机号'))
  } else {
    callback()
  }
}

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const profileRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '用户名长度在 2 到 20 个字符', trigger: 'blur' }
  ],
  phone: [
    { validator: validatePhone, trigger: 'blur' }
  ]
}

const passwordRules = {
  oldPassword: [
    { required: true, message: '请输入旧密码', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

function initFormData() {
  const info = userStore.userInfo
  if (info) {
    formData.username = info.username || ''
    formData.email = info.email || ''
    formData.phone = info.phone || ''
    formData.avatar = info.avatar || ''
    formData.targetPosition = info.targetPosition || ''
    if (info.techStack) {
      formData.techStack = typeof info.techStack === 'string' ? JSON.parse(info.techStack) : info.techStack
    } else {
      formData.techStack = []
    }
  }
}

async function loadUserInfo() {
  if (!userStore.userInfo) {
    await userStore.fetchUserInfo()
  }
  initFormData()
}

function triggerFileInput() {
  fileInputRef.value?.click()
}

async function handleAvatarChange(event) {
  const file = event.target.files?.[0]
  if (!file) return

  const isImage = file.type.startsWith('image/')
  if (!isImage) {
    ElMessage.error('请选择图片文件')
    event.target.value = ''
    return
  }

  const maxSize = 2 * 1024 * 1024
  if (file.size > maxSize) {
    ElMessage.error('图片大小不能超过2MB')
    event.target.value = ''
    return
  }

  avatarLoading.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    const res = await uploadAvatar(fd)
    if (res.code === 200) {
      formData.avatar = res.data.avatar
      userStore.setUserInfo({ ...userStore.userInfo, avatar: res.data.avatar })
      ElMessage.success('头像更新成功')
    } else {
      ElMessage.error(res.message || '头像上传失败')
    }
  } catch (err) {
    ElMessage.error('头像上传失败，请重试')
  } finally {
    avatarLoading.value = false
    event.target.value = ''
  }
}

async function handleSaveProfile() {
  if (!profileFormRef.value) return

  try {
    await profileFormRef.value.validate()
  } catch {
    return
  }

  saving.value = true
  try {
    const payload = {
      username: formData.username,
      phone: formData.phone,
      targetPosition: formData.targetPosition,
      techStack: formData.techStack ? JSON.stringify(formData.techStack) : null
    }
    const res = await updateUserInfo(payload)
    if (res.code === 200) {
      await userStore.fetchUserInfo()
      initFormData()
      ElMessage.success('个人信息保存成功')
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch (err) {
    ElMessage.error('保存失败，请重试')
  } finally {
    saving.value = false
  }
}

async function handleChangePassword() {
  if (!passwordFormRef.value) return

  try {
    await passwordFormRef.value.validate()
  } catch {
    return
  }

  changingPassword.value = true
  try {
    const res = await changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    if (res.code === 200) {
      ElMessage.success('密码修改成功')
      passwordFormRef.value.resetFields()
      Object.assign(passwordForm, {
        oldPassword: '',
        newPassword: '',
        confirmPassword: ''
      })
    } else {
      ElMessage.error(res.message || '密码修改失败')
    }
  } catch (err) {
    ElMessage.error('密码修改失败，请重试')
  } finally {
    changingPassword.value = false
  }
}

async function handleSubmitFeedback() {
  if (!feedbackFormRef.value) return
  try {
    await feedbackFormRef.value.validate()
    submittingFeedback.value = true
    const res = await request({
      url: '/feedback',
      method: 'post',
      data: {
        type: feedbackForm.type,
        content: feedbackForm.content,
        contact: feedbackForm.contact
      }
    })
    if (res.code === 200) {
      ElMessage.success('感谢您的反馈')
      feedbackForm.type = '功能建议'
      feedbackForm.content = ''
      feedbackForm.contact = ''
    } else {
      ElMessage.error(res.message || '提交失败')
    }
  } catch (err) {
    if (err !== false) {
      ElMessage.error('提交失败，请重试')
    }
  } finally {
    submittingFeedback.value = false
  }
}

onMounted(() => {
  loadUserInfo()
})
</script>

<style lang="scss" scoped>
.profile-page {
  min-height: 100%;
  padding: 24px;
  background-color: $bg-surface;
}

.profile-header {
  background: #fff;
  border: 1px solid $border-color;
  border-radius: 8px;
  padding: 24px 32px;
  margin-bottom: 24px;

  &__info {
    display: flex;
    align-items: center;
    gap: 16px;
  }

  &__text {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  &__name {
    margin: 0;
    font-size: 20px;
    font-weight: 600;
    color: $text-primary;
    line-height: 1.4;
  }

  &__email {
    font-size: 14px;
    color: $text-secondary;
  }

  &__fallback {
    font-size: 24px;
    font-weight: 600;
    color: $accent-color;
  }
}

.profile-tabs {
  background: #fff;
  border: 1px solid $border-color;
  border-radius: 8px;
  padding: 0 24px;

  :deep(.el-tabs__header) {
    margin: 0;
  }

  :deep(.el-tabs__nav-wrap::after) {
    height: 1px;
    background-color: $border-color;
  }

  :deep(.el-tabs__active-bar) {
    background-color: $accent-color;
  }

  :deep(.el-tabs__item.is-active) {
    color: $accent-color;
  }

  :deep(.el-tabs__item:hover) {
    color: $accent-hover;
  }
}

.tab-content {
  padding: 32px 0;
}

.basic-info {
  display: flex;
  gap: 48px;
  align-items: flex-start;
}

.avatar-section {
  flex-shrink: 0;
}

.avatar-wrapper {
  position: relative;
  width: 100px;
  height: 100px;
  border-radius: 50%;
  cursor: pointer;
  overflow: hidden;

  &:hover .avatar-overlay {
    opacity: 1;
  }
}

.avatar-display {
  width: 100%;
  height: 100%;
}

.avatar-fallback {
  font-size: 36px;
  font-weight: 600;
  color: $accent-color;
}

.avatar-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  background: rgba(0, 0, 0, 0.5);
  border-radius: 50%;
  opacity: 0;
  transition: opacity $transition-normal;

  &.is-loading {
    opacity: 1;
  }

  &__icon {
    font-size: 24px;
    color: #fff;

    &.is-spinning {
      animation: spin 1s linear infinite;
    }
  }

  &__text {
    font-size: 12px;
    color: #fff;
    white-space: nowrap;
  }
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.avatar-input {
  display: none;
}

.profile-form {
  flex: 1;
  max-width: 480px;

  :deep(.el-form-item__label) {
    font-weight: 500;
    color: $text-primary;
  }

  :deep(.el-input.is-disabled .el-input__wrapper) {
    background-color: $bg-muted;
  }

  :deep(.el-button--primary) {
    --el-button-bg-color: #{$accent-color};
    --el-button-border-color: #{$accent-color};
    --el-button-hover-bg-color: #{$accent-hover};
    --el-button-hover-border-color: #{$accent-hover};
    --el-button-active-bg-color: #{$accent-hover};
    --el-button-active-border-color: #{$accent-hover};
    font-weight: 500;
  }
}

.security-section {
  max-width: 480px;
}

.section-title {
  margin: 0 0 24px;
  font-size: 18px;
  font-weight: 600;
  color: $text-primary;
}

.password-form {
  :deep(.el-form-item__label) {
    font-weight: 500;
    color: $text-primary;
  }

  :deep(.el-button--primary) {
    --el-button-bg-color: #{$accent-color};
    --el-button-border-color: #{$accent-color};
    --el-button-hover-bg-color: #{$accent-hover};
    --el-button-hover-border-color: #{$accent-hover};
    --el-button-active-bg-color: #{$accent-hover};
    --el-button-active-border-color: #{$accent-hover};
    font-weight: 500;
  }
}

.help-section,
.feedback-section {
  margin-bottom: 32px;
}

.faq-collapse {
  :deep(.el-collapse-item__header) {
    font-size: 14px;
    font-weight: 500;
    color: $text-primary;
    background: transparent;
    border-bottom: 1px solid $border-color;
    transition: color $transition-fast;

    &:hover {
      color: $accent-color;
    }
  }

  :deep(.el-collapse-item__content) {
    padding: 12px 0;
    color: $text-secondary;
    font-size: 14px;
    line-height: 1.6;
  }

  :deep(.el-collapse-item__wrap) {
    border-bottom: 1px solid $border-color;
  }
}

.feedback-form {
  max-width: 600px;

  :deep(.el-form-item__label) {
    font-weight: 500;
    color: $text-primary;
  }

  :deep(.el-button--primary) {
    --el-button-bg-color: #{$accent-color};
    --el-button-border-color: #{$accent-color};
    --el-button-hover-bg-color: #{$accent-hover};
    --el-button-hover-border-color: #{$accent-hover};
    --el-button-active-bg-color: #{$accent-hover};
    --el-button-active-border-color: #{$accent-hover};
    font-weight: 500;
  }
}

@media (max-width: 768px) {
  .profile-page {
    padding: 16px;
  }

  .profile-header {
    padding: 16px 20px;
  }

  .profile-tabs {
    padding: 0 16px;
  }

  .tab-content {
    padding: 24px 0;
  }

  .basic-info {
    flex-direction: column;
    align-items: center;
    gap: 32px;
  }

  .avatar-section {
    display: flex;
    justify-content: center;
  }

  .profile-form {
    width: 100%;
    max-width: 100%;
  }

  .security-section {
    max-width: 100%;
  }
}
</style>
