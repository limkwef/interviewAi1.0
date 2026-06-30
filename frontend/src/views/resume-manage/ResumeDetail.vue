<template>
  <div class="page-container resume-detail-page">
    <div class="page-header">
      <el-button text @click="$router.push('/resume/list')">&larr; 返回列表</el-button>
      <h1 class="page-title">简历详情</h1>
    </div>

    <div v-loading="loading" class="detail-content">
      <!-- 状态栏 -->
      <div class="status-bar">
        <el-tag :type="statusType(resume.status)" effect="dark" size="large">
          {{ statusText(resume.status) }}
        </el-tag>
        <el-tag v-if="resume.isActive === 1" type="success" effect="dark">当前激活</el-tag>
        <span class="file-info" v-if="resume.fileName">{{ resume.fileName }} ({{ formatSize(resume.fileSize) }})</span>
        <span class="time-info">创建于 {{ formatDate(resume.createTime) }}</span>
      </div>

      <!-- 解析中提示 -->
      <el-alert v-if="resume.status === 0" title="简历正在解析中，请稍候..." type="info" show-icon :closable="false" class="status-alert" />

      <!-- 解析失败提示 -->
      <el-alert v-if="resume.status === 2" :title="'解析失败：' + (resume.errorMsg || '未知错误')" type="error" show-icon class="status-alert">
        <el-button size="small" type="danger" @click="handleReparse">重新解析</el-button>
      </el-alert>

      <!-- 结构化数据展示 -->
      <div v-if="resume.status === 1 && parsedData" class="data-sections">
        <!-- 基本信息 -->
        <div class="section-card" v-if="parsedData.basicInfo">
          <h3 class="section-title">基本信息</h3>
          <el-descriptions :column="3" border>
            <el-descriptions-item label="姓名">{{ parsedData.basicInfo.name || '-' }}</el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ parsedData.basicInfo.email || '-' }}</el-descriptions-item>
            <el-descriptions-item label="电话">{{ parsedData.basicInfo.phone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="目标岗位">{{ parsedData.basicInfo.targetPosition || '-' }}</el-descriptions-item>
            <el-descriptions-item label="所在城市">{{ parsedData.basicInfo.location || '-' }}</el-descriptions-item>
            <el-descriptions-item label="工作年限">{{ parsedData.basicInfo.yearsOfExperience ?? '-' }} 年</el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 教育经历 -->
        <div class="section-card" v-if="parsedData.education?.length">
          <h3 class="section-title">教育经历</h3>
          <el-timeline>
            <el-timeline-item v-for="(edu, idx) in parsedData.education" :key="idx" :timestamp="`${edu.startDate} - ${edu.endDate}`">
              <h4>{{ edu.school }}</h4>
              <p>{{ edu.degree }} · {{ edu.major }}</p>
            </el-timeline-item>
          </el-timeline>
        </div>

        <!-- 工作经历 -->
        <div class="section-card" v-if="parsedData.workExperience?.length">
          <h3 class="section-title">工作经历</h3>
          <el-timeline>
            <el-timeline-item v-for="(work, idx) in parsedData.workExperience" :key="idx" :timestamp="`${work.startDate} - ${work.endDate}`">
              <h4>{{ work.company }} · {{ work.position }}</h4>
              <p>{{ work.description }}</p>
            </el-timeline-item>
          </el-timeline>
        </div>

        <!-- 项目经历 -->
        <div class="section-card" v-if="parsedData.projects?.length">
          <h3 class="section-title">项目经历</h3>
          <div v-for="(proj, idx) in parsedData.projects" :key="idx" class="project-item">
            <h4>{{ proj.name }} <span class="role">{{ proj.role }}</span></h4>
            <el-tag v-for="tech in proj.techStack" :key="tech" size="small" class="tech-tag">{{ tech }}</el-tag>
            <p class="desc">{{ proj.description }}</p>
          </div>
        </div>

        <!-- 技能 -->
        <div class="section-card" v-if="parsedData.skills?.length">
          <h3 class="section-title">技能</h3>
          <div class="skills-grid">
            <div v-for="(skill, idx) in parsedData.skills" :key="idx" class="skill-item">
              <span class="skill-name">{{ skill.name }}</span>
              <el-tag :type="skillLevelType(skill.level)" size="small">{{ skill.level }}</el-tag>
              <span class="skill-years">{{ skill.years }} 年</span>
            </div>
          </div>
        </div>

        <!-- 证书 -->
        <div class="section-card" v-if="parsedData.certifications?.length">
          <h3 class="section-title">证书</h3>
          <el-tag v-for="cert in parsedData.certifications" :key="cert" class="cert-tag" effect="plain">{{ cert }}</el-tag>
        </div>

        <!-- 自我评价 -->
        <div class="section-card" v-if="parsedData.selfEvaluation">
          <h3 class="section-title">自我评价</h3>
          <p class="self-eval">{{ parsedData.selfEvaluation }}</p>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="action-bar" v-if="resume.id">
        <el-button type="primary" :disabled="resume.isActive === 1" @click="handleActivate">
          {{ resume.isActive === 1 ? '已激活' : '设为当前简历' }}
        </el-button>
        <el-button @click="$router.push('/resume/list')">返回列表</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getResumeDetail, activateResume, reparseResume, getResumeStatus } from '@/api/resume'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const resume = ref({})
let pollTimer = null

const parsedData = computed(() => {
  if (!resume.value.parsedData) return null
  try { return typeof resume.value.parsedData === 'string' ? JSON.parse(resume.value.parsedData) : resume.value.parsedData }
  catch { return null }
})

const statusText = (s) => ({ 0: '解析中', 1: '已就绪', 2: '解析失败' }[s] || '未知')
const statusType = (s) => ({ 0: 'warning', 1: 'success', 2: 'danger' }[s] || 'info')
const skillLevelType = (l) => ({ '精通': 'danger', '熟练': 'warning', '熟悉': 'success', '了解': 'info' }[l] || '')
const formatDate = (d) => d ? new Date(d).toLocaleString('zh-CN') : '-'
const formatSize = (s) => {
  if (!s) return '-'
  if (s < 1024) return s + ' B'
  if (s < 1024 * 1024) return (s / 1024).toFixed(1) + ' KB'
  return (s / 1024 / 1024).toFixed(1) + ' MB'
}

const fetchDetail = async () => {
  loading.value = true
  try {
    const res = await getResumeDetail(route.params.id)
    resume.value = res.data || {}
    if (resume.value.status === 0) startPoll()
  } catch (e) {
    ElMessage.error('获取简历详情失败')
  } finally {
    loading.value = false
  }
}

const startPoll = () => {
  stopPoll()
  pollTimer = setInterval(async () => {
    try {
      const res = await getResumeStatus(route.params.id)
      if (res.data?.status !== 0) {
        resume.value.status = res.data.status
        stopPoll()
        fetchDetail()
      }
    } catch { stopPoll() }
  }, 3000)
}

const stopPoll = () => { if (pollTimer) { clearInterval(pollTimer); pollTimer = null } }

const handleActivate = async () => {
  try { await activateResume(resume.value.id); ElMessage.success('激活成功'); fetchDetail() }
  catch { ElMessage.error('激活失败') }
}

const handleReparse = async () => {
  try { await reparseResume(resume.value.id); ElMessage.success('重新解析已启动'); resume.value.status = 0; startPoll() }
  catch { ElMessage.error('重新解析失败') }
}

onMounted(fetchDetail)
onUnmounted(stopPoll)
</script>

<style scoped>
.resume-detail-page { padding: 24px; }
.page-header { margin-bottom: 24px; }
.page-title { font-size: 24px; font-weight: 600; color: #1a1a2e; margin: 8px 0 0; }
.status-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.file-info, .time-info { color: #909399; font-size: 13px; }
.status-alert { margin-bottom: 16px; }
.section-card { background: #fff; border-radius: 12px; padding: 24px; margin-bottom: 16px; box-shadow: 0 2px 12px rgba(0,0,0,0.04); }
.section-title { font-size: 16px; font-weight: 600; color: #303133; margin: 0 0 16px; padding-bottom: 8px; border-bottom: 1px solid #ebeef5; }
.project-item { margin-bottom: 16px; }
.project-item h4 { margin: 0 0 8px; }
.role { font-weight: normal; color: #909399; font-size: 13px; }
.tech-tag { margin-right: 6px; margin-bottom: 4px; }
.desc { color: #606266; font-size: 14px; margin-top: 6px; }
.skills-grid { display: flex; flex-wrap: wrap; gap: 12px; }
.skill-item { display: flex; align-items: center; gap: 8px; background: #f5f7fa; padding: 8px 12px; border-radius: 6px; }
.skill-name { font-weight: 500; }
.skill-years { color: #909399; font-size: 12px; }
.cert-tag { margin-right: 8px; margin-bottom: 8px; }
.self-eval { color: #606266; line-height: 1.8; }
.action-bar { margin-top: 24px; display: flex; gap: 12px; }
</style>
