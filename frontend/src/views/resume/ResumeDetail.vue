<template>
  <div class="resume-detail">
    <div class="page-header">
      <h2>简历详情</h2>
      <el-button @click="$router.push('/resume/list')">返回列表</el-button>
    </div>

    <el-skeleton v-if="loading" :rows="10" animated />

    <template v-else-if="resume">
      <!-- 状态信息 -->
      <el-card class="info-card">
        <div class="info-row">
          <span>来源：{{ resume.source === 'upload' ? '上传解析' : '在线填写' }}</span>
          <span>状态：<el-tag :type="statusType(resume.status)" size="small">{{ statusText(resume.status) }}</el-tag></span>
          <span>创建时间：{{ formatDate(resume.createTime) }}</span>
          <span v-if="resume.fileName">文件：{{ resume.fileName }}</span>
        </div>
      </el-card>

      <el-empty v-if="resume.status !== 1" description="简历尚未解析完成" />

      <template v-else-if="parsedData">
        <!-- 基本信息 -->
        <el-card class="section-card" v-if="parsedData.basicInfo">
          <template #header><span>基本信息</span></template>
          <el-descriptions :column="3" border>
            <el-descriptions-item label="姓名">{{ parsedData.basicInfo.name || '-' }}</el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ parsedData.basicInfo.email || '-' }}</el-descriptions-item>
            <el-descriptions-item label="电话">{{ parsedData.basicInfo.phone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="目标岗位">{{ parsedData.basicInfo.targetPosition || '-' }}</el-descriptions-item>
            <el-descriptions-item label="所在城市">{{ parsedData.basicInfo.location || '-' }}</el-descriptions-item>
            <el-descriptions-item label="工作年限">{{ parsedData.basicInfo.yearsOfExperience ?? '-' }} 年</el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 技能标签 -->
        <el-card class="section-card" v-if="parsedData.skills && parsedData.skills.length">
          <template #header><span>技能标签</span></template>
          <div class="skill-tags">
            <el-tag v-for="(skill, i) in parsedData.skills" :key="i"
                    :type="skillLevelType(skill.level)" class="skill-tag">
              {{ skill.name }}
              <span v-if="skill.level" class="skill-level">（{{ skill.level }}{{ skill.years ? ' · ' + skill.years + '年' : '' }}）</span>
            </el-tag>
          </div>
        </el-card>

        <!-- 教育经历 -->
        <el-card class="section-card" v-if="parsedData.education && parsedData.education.length">
          <template #header><span>教育经历</span></template>
          <el-timeline>
            <el-timeline-item v-for="(edu, i) in parsedData.education" :key="i"
                              :timestamp="`${edu.startDate || ''} - ${edu.endDate || ''}`" placement="top">
              <h4>{{ edu.school }}</h4>
              <p>{{ edu.degree }} · {{ edu.major }}</p>
            </el-timeline-item>
          </el-timeline>
        </el-card>

        <!-- 工作经历 -->
        <el-card class="section-card" v-if="parsedData.workExperience && parsedData.workExperience.length">
          <template #header><span>工作经历</span></template>
          <el-timeline>
            <el-timeline-item v-for="(work, i) in parsedData.workExperience" :key="i"
                              :timestamp="`${work.startDate || ''} - ${work.endDate || ''}`" placement="top">
              <h4>{{ work.company }} · {{ work.position }}</h4>
              <p v-if="work.description">{{ work.description }}</p>
            </el-timeline-item>
          </el-timeline>
        </el-card>

        <!-- 项目经历 -->
        <el-card class="section-card" v-if="parsedData.projects && parsedData.projects.length">
          <template #header><span>项目经历</span></template>
          <div v-for="(proj, i) in parsedData.projects" :key="i" class="project-item">
            <h4>{{ proj.name }} <span v-if="proj.role" class="project-role">· {{ proj.role }}</span></h4>
            <div v-if="proj.techStack && proj.techStack.length" class="tech-stack">
              <el-tag v-for="tech in proj.techStack" :key="tech" size="small" type="info">{{ tech }}</el-tag>
            </div>
            <p v-if="proj.description" class="project-desc">{{ proj.description }}</p>
          </div>
        </el-card>

        <!-- 证书 -->
        <el-card class="section-card" v-if="parsedData.certifications && parsedData.certifications.length">
          <template #header><span>证书</span></template>
          <el-tag v-for="cert in parsedData.certifications" :key="cert" type="warning" class="cert-tag">{{ cert }}</el-tag>
        </el-card>

        <!-- 自我评价 -->
        <el-card class="section-card" v-if="parsedData.selfEvaluation">
          <template #header><span>自我评价</span></template>
          <p class="self-eval">{{ parsedData.selfEvaluation }}</p>
        </el-card>
      </template>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getResumeDetail } from '@/api/resume'

const route = useRoute()
const loading = ref(true)
const resume = ref(null)
const parsedData = ref(null)

onMounted(async () => {
  const id = route.params.id
  try {
    const res = await getResumeDetail(id)
    if (res.code === 200) {
      resume.value = res.data
      if (res.data.parsedData) {
        parsedData.value = typeof res.data.parsedData === 'string'
          ? JSON.parse(res.data.parsedData)
          : res.data.parsedData
      }
    }
  } catch (e) {
    ElMessage.error('加载简历详情失败')
  } finally {
    loading.value = false
  }
})

function statusText(status) {
  return { 0: '解析中', 1: '已完成', 2: '解析失败' }[status] || '未知'
}

function statusType(status) {
  return { 0: 'warning', 1: 'success', 2: 'danger' }[status] || 'info'
}

function skillLevelType(level) {
  return { '精通': 'danger', '熟练': 'warning', '熟悉': 'success', '了解': 'info' }[level] || ''
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN')
}
</script>

<style scoped>
.resume-detail {
  padding: 20px;
  max-width: 900px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.page-header h2 {
  margin: 0;
  font-size: 20px;
}
.info-card {
  margin-bottom: 16px;
}
.info-row {
  display: flex;
  gap: 24px;
  font-size: 14px;
  color: #666;
}
.section-card {
  margin-bottom: 16px;
}
.skill-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.skill-tag {
  font-size: 14px;
}
.skill-level {
  font-size: 12px;
  opacity: 0.8;
}
.project-item {
  padding: 12px 0;
  border-bottom: 1px dashed #eee;
}
.project-item:last-child {
  border-bottom: none;
}
.project-item h4 {
  margin: 0 0 8px;
}
.project-role {
  font-weight: normal;
  color: #666;
}
.tech-stack {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}
.project-desc {
  color: #666;
  font-size: 14px;
  margin: 0;
}
.cert-tag {
  margin-right: 8px;
  margin-bottom: 8px;
}
.self-eval {
  color: #333;
  line-height: 1.8;
  margin: 0;
}
</style>
