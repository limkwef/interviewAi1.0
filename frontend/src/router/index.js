import { createRouter, createWebHistory } from 'vue-router'
import NProgress from 'nprogress'
import { useUserStore } from '@/stores/user'
import { useInterviewStore } from '@/stores/interview'

const routes = [
  { path: '/login', name: 'Login', component: () => import('@/views/login/index.vue'), meta: { requiresAuth: false, title: '登录' } },
  { path: '/register', name: 'Register', component: () => import('@/views/register/index.vue'), meta: { requiresAuth: false, title: '注册' } },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/dashboard/index.vue'), meta: { title: '首页', icon: 'House' } },
      {
        path: 'questions',
        name: 'Questions',
        redirect: '/questions/list',
        meta: { title: '题库中心', icon: 'Document' },
        children: [
          { path: 'list', name: 'QuestionList', component: () => import('@/views/questions/list/index.vue'), meta: { title: '题目浏览' } },
          { path: 'detail/:id', name: 'QuestionDetail', component: () => import('@/views/questions/detail/index.vue'), meta: { title: '题目详情', hidden: true } },
          { path: 'favorites', name: 'QuestionFavorites', component: () => import('@/views/questions/favorites/index.vue'), meta: { title: '我的收藏' } },
          { path: 'mistakes', name: 'MistakeList', component: () => import('@/views/questions/mistakes/index.vue'), meta: { title: '我的错题' } },
          { path: 'mistakes/:id', name: 'MistakeDetail', component: () => import('@/views/questions/mistakes/detail.vue'), meta: { title: '错题详情', hidden: true } },
          { path: 'mistakes/review', name: 'MistakeReview', component: () => import('@/views/questions/mistakes/review.vue'), meta: { title: '错题重做', hidden: true } }
        ]
      },
      { path: 'learning-path', name: 'LearningPath', component: () => import('@/views/learning-path/index.vue'), meta: { title: '学习路径', icon: 'TrendCharts' } },
      {
        path: 'resume',
        name: 'Resume',
        redirect: '/resume/list',
        meta: { title: '简历管理', icon: 'Files' },
        children: [
          { path: 'list', name: 'ResumeList', component: () => import('@/views/resume-manage/ResumeList.vue'), meta: { title: '简历列表' } },
          { path: 'upload', name: 'ResumeUpload', component: () => import('@/views/resume-manage/ResumeUpload.vue'), meta: { title: '上传简历' } },
          { path: 'create', name: 'ResumeCreate', component: () => import('@/views/resume-manage/ResumeForm.vue'), meta: { title: '在线填写' } },
          { path: 'detail/:id', name: 'ResumeDetail', component: () => import('@/views/resume-manage/ResumeDetail.vue'), meta: { title: '简历详情', hidden: true } }
        ]
      },
      {
        path: 'interview',
        name: 'Interview',
        redirect: '/interview/config',
        meta: { title: '模拟面试', icon: 'ChatDotRound' },
        children: [
          { path: 'config', name: 'InterviewConfig', component: () => import('@/views/interview/config/index.vue'), meta: { title: '面试配置' } },
          { path: 'session/:id', name: 'InterviewSession', component: () => import('@/views/interview/session/index.vue'), meta: { title: '面试进行中', hidden: true } }
        ]
      },
      {
        path: 'report',
        name: 'Report',
        redirect: '/report/list',
        meta: { title: '面试报告', icon: 'DataAnalysis' },
        children: [
          { path: 'list', name: 'ReportList', component: () => import('@/views/report/list/index.vue'), meta: { title: '报告列表' } },
          { path: 'detail/:id', name: 'ReportDetail', component: () => import('@/views/report/detail/index.vue'), meta: { title: '报告详情', hidden: true } },
          { path: 'diagnosis/:id', name: 'DiagnosisReport', component: () => import('@/views/report/diagnosis/index.vue'), meta: { title: 'AI 深度诊断', hidden: true } }
        ]
      },
      {
        path: 'admin',
        name: 'Admin',
        redirect: '/admin/dashboard',
        meta: { title: '系统管理', icon: 'Setting', requiresAdmin: true },
        children: [
          { path: 'dashboard', name: 'AdminDashboard', component: () => import('@/views/admin/dashboard/index.vue'), meta: { title: '管理首页', requiresAdmin: true } },
          { path: 'users', name: 'AdminUsers', component: () => import('@/views/admin/users/index.vue'), meta: { title: '用户管理', requiresAdmin: true } },
          { path: 'questions', name: 'AdminQuestions', component: () => import('@/views/admin/questions/index.vue'), meta: { title: '题库管理', requiresAdmin: true } },
          { path: 'ai-model', name: 'AdminAiModel', component: () => import('@/views/admin/ai-model/index.vue'), meta: { title: 'AI模型管理', requiresAdmin: true } },
          { path: 'logs', name: 'AdminLogs', component: () => import('@/views/admin/logs/index.vue'), meta: { title: '操作日志', requiresAdmin: true } },
          { path: 'feedback', name: 'AdminFeedback', component: () => import('@/views/admin/feedback/index.vue'), meta: { title: '反馈管理', requiresAdmin: true } }
        ]
      },
      {
        path: 'settings',
        name: 'Settings',
        meta: { title: '个人设置', icon: 'UserFilled' },
        children: [
          { path: 'models', name: 'UserModels', component: () => import('@/views/settings/models/index.vue'), meta: { title: '我的模型' } }
        ]
      },
      { path: 'profile', name: 'Profile', component: () => import('@/views/profile/index.vue'), meta: { title: '个人中心', icon: 'User', hidden: true } }
    ]
  },
  { path: '/:pathMatch(.*)*', name: 'NotFound', component: () => import('@/views/error/404.vue'), meta: { title: '页面不存在' } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

const whiteList = ['/login', '/register']

router.beforeEach((to, from, next) => {
  NProgress.start()
  document.title = `${to.meta.title || ''} - AI面试系统`

  const interviewStore = useInterviewStore()
  if (interviewStore.isInInterview && from.name === 'InterviewSession' && to.name !== 'InterviewSession') {
    NProgress.done()
    next(false)
    return
  }

  const userStore = useUserStore()
  const token = userStore.token
  const isAdmin = userStore.isAdmin

  const requiresAdmin = to.matched.some(record => record.meta.requiresAdmin)

  if (token) {
    if (to.path === '/login') {
      next({ path: '/' })
    } else if (requiresAdmin && !isAdmin) {
      next({ path: '/dashboard' })
    } else {
      next()
    }
  } else {
    if (whiteList.includes(to.path)) {
      next()
    } else {
      next(`/login?redirect=${to.path}`)
    }
  }
})

router.afterEach(() => {
  NProgress.done()
})

export default router
