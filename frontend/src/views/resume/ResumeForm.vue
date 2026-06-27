<template>
  <div class="resume-form">
    <div class="page-header">
      <h2>在线填写简历</h2>
      <el-button @click="$router.push('/resume/list')">返回列表</el-button>
    </div>

    <el-form ref="formRef" :model="form" label-width="100px" label-position="top">
      <!-- 基本信息 -->
      <el-card class="section-card">
        <template #header><span>基本信息</span></template>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="姓名" prop="basicInfo.name">
              <el-input v-model="form.basicInfo.name" placeholder="请输入姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="邮箱" prop="basicInfo.email">
              <el-input v-model="form.basicInfo.email" placeholder="请输入邮箱" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="电话" prop="basicInfo.phone">
              <el-input v-model="form.basicInfo.phone" placeholder="请输入电话" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="目标岗位" prop="basicInfo.targetPosition">
              <el-input v-model="form.basicInfo.targetPosition" placeholder="如：Java后端开发" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="所在城市" prop="basicInfo.location">
              <el-input v-model="form.basicInfo.location" placeholder="如：北京" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="工作年限" prop="basicInfo.yearsOfExperience">
              <el-input-number v-model="form.basicInfo.yearsOfExperience" :min="0" :max="50" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-card>

      <!-- 教育经历 -->
      <el-card class="section-card">
        <template #header>
          <div class="section-header">
            <span>教育经历</span>
            <el-button size="small" type="primary" @click="addEducation">添加</el-button>
          </div>
        </template>
        <div v-for="(edu, index) in form.education" :key="index" class="list-item">
          <el-row :gutter="16">
            <el-col :span="6">
              <el-form-item label="学校">
                <el-input v-model="edu.school" placeholder="学校名称" />
              </el-form-item>
            </el-col>
            <el-col :span="4">
              <el-form-item label="学位">
                <el-select v-model="edu.degree" placeholder="选择">
                  <el-option label="本科" value="本科" />
                  <el-option label="硕士" value="硕士" />
                  <el-option label="博士" value="博士" />
                  <el-option label="大专" value="大专" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="5">
              <el-form-item label="专业">
                <el-input v-model="edu.major" placeholder="专业" />
              </el-form-item>
            </el-col>
            <el-col :span="4">
              <el-form-item label="开始时间">
                <el-input v-model="edu.startDate" placeholder="2020.09" />
              </el-form-item>
            </el-col>
            <el-col :span="4">
              <el-form-item label="结束时间">
                <el-input v-model="edu.endDate" placeholder="2024.06" />
              </el-form-item>
            </el-col>
            <el-col :span="1">
              <el-button type="danger" :icon="Delete" circle size="small" @click="form.education.splice(index, 1)" />
            </el-col>
          </el-row>
        </div>
        <el-empty v-if="form.education.length === 0" description="点击上方按钮添加教育经历" :image-size="60" />
      </el-card>

      <!-- 工作经历 -->
      <el-card class="section-card">
        <template #header>
          <div class="section-header">
            <span>工作经历</span>
            <el-button size="small" type="primary" @click="addWork">添加</el-button>
          </div>
        </template>
        <div v-for="(work, index) in form.workExperience" :key="index" class="list-item">
          <el-row :gutter="16">
            <el-col :span="6">
              <el-form-item label="公司">
                <el-input v-model="work.company" placeholder="公司名称" />
              </el-form-item>
            </el-col>
            <el-col :span="5">
              <el-form-item label="职位">
                <el-input v-model="work.position" placeholder="职位" />
              </el-form-item>
            </el-col>
            <el-col :span="4">
              <el-form-item label="开始时间">
                <el-input v-model="work.startDate" placeholder="2024.07" />
              </el-form-item>
            </el-col>
            <el-col :span="4">
              <el-form-item label="结束时间">
                <el-input v-model="work.endDate" placeholder="至今" />
              </el-form-item>
            </el-col>
            <el-col :span="4">
              <el-form-item label="职责描述">
                <el-input v-model="work.description" placeholder="简要描述" />
              </el-form-item>
            </el-col>
            <el-col :span="1">
              <el-button type="danger" :icon="Delete" circle size="small" @click="form.workExperience.splice(index, 1)" />
            </el-col>
          </el-row>
        </div>
        <el-empty v-if="form.workExperience.length === 0" description="点击上方按钮添加工作经历" :image-size="60" />
      </el-card>

      <!-- 项目经历 -->
      <el-card class="section-card">
        <template #header>
          <div class="section-header">
            <span>项目经历</span>
            <el-button size="small" type="primary" @click="addProject">添加</el-button>
          </div>
        </template>
        <div v-for="(proj, index) in form.projects" :key="index" class="list-item">
          <el-row :gutter="16">
            <el-col :span="6">
              <el-form-item label="项目名称">
                <el-input v-model="proj.name" placeholder="项目名称" />
              </el-form-item>
            </el-col>
            <el-col :span="4">
              <el-form-item label="角色">
                <el-input v-model="proj.role" placeholder="如：后端开发" />
              </el-form-item>
            </el-col>
            <el-col :span="13">
              <el-form-item label="技术栈（逗号分隔）">
                <el-input v-model="proj.techStackStr" placeholder="如：Spring Boot, MySQL, Redis" @change="proj.techStack = proj.techStackStr.split(',').map(s => s.trim()).filter(Boolean)" />
              </el-form-item>
            </el-col>
            <el-col :span="1">
              <el-button type="danger" :icon="Delete" circle size="small" @click="form.projects.splice(index, 1)" />
            </el-col>
          </el-row>
          <el-form-item label="项目描述">
            <el-input v-model="proj.description" type="textarea" :rows="2" placeholder="简要描述项目内容和你的职责" />
          </el-form-item>
        </div>
        <el-empty v-if="form.projects.length === 0" description="点击上方按钮添加项目经历" :image-size="60" />
      </el-card>

      <!-- 技能标签 -->
      <el-card class="section-card">
        <template #header>
          <div class="section-header">
            <span>技能标签</span>
            <el-button size="small" type="primary" @click="addSkill">添加</el-button>
          </div>
        </template>
        <div v-for="(skill, index) in form.skills" :key="index" class="list-item skill-item">
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="技能名称">
                <el-input v-model="skill.name" placeholder="如：Java" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="熟练程度">
                <el-select v-model="skill.level" placeholder="选择">
                  <el-option label="精通" value="精通" />
                  <el-option label="熟练" value="熟练" />
                  <el-option label="熟悉" value="熟悉" />
                  <el-option label="了解" value="了解" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="使用年限">
                <el-input-number v-model="skill.years" :min="0" :max="30" />
              </el-form-item>
            </el-col>
            <el-col :span="1">
              <el-button type="danger" :icon="Delete" circle size="small" @click="form.skills.splice(index, 1)" />
            </el-col>
          </el-row>
        </div>
        <el-empty v-if="form.skills.length === 0" description="点击上方按钮添加技能" :image-size="60" />
      </el-card>

      <!-- 自我评价 -->
      <el-card class="section-card">
        <template #header><span>自我评价</span></template>
        <el-input v-model="form.selfEvaluation" type="textarea" :rows="4" placeholder="简要的自我评价..." />
      </el-card>

      <div class="form-actions">
        <el-button type="primary" :loading="submitting" @click="handleSubmit">提交简历</el-button>
        <el-button @click="$router.push('/resume/list')">取消</el-button>
      </div>
    </el-form>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'
import { createResume } from '@/api/resume'

const router = useRouter()
const submitting = ref(false)

const form = reactive({
  basicInfo: { name: '', email: '', phone: '', targetPosition: '', location: '', yearsOfExperience: 0 },
  education: [],
  workExperience: [],
  projects: [],
  skills: [],
  certifications: [],
  selfEvaluation: ''
})

function addEducation() {
  form.education.push({ school: '', degree: '', major: '', startDate: '', endDate: '' })
}

function addWork() {
  form.workExperience.push({ company: '', position: '', startDate: '', endDate: '', description: '' })
}

function addProject() {
  form.projects.push({ name: '', role: '', techStack: [], techStackStr: '', description: '' })
}

function addSkill() {
  form.skills.push({ name: '', level: '熟悉', years: 1 })
}

async function handleSubmit() {
  if (!form.basicInfo.name) {
    ElMessage.warning('请填写姓名')
    return
  }
  submitting.value = true
  try {
    const data = {
      ...form,
      projects: form.projects.map(p => ({ ...p, techStack: p.techStackStr ? p.techStackStr.split(',').map(s => s.trim()).filter(Boolean) : [] }))
    }
    const res = await createResume(data)
    if (res.code === 200) {
      ElMessage.success('创建成功')
      router.push('/resume/list')
    }
  } catch (e) {
    ElMessage.error('创建失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.resume-form {
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
.section-card {
  margin-bottom: 16px;
}
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.list-item {
  padding: 12px 0;
  border-bottom: 1px dashed #eee;
}
.list-item:last-child {
  border-bottom: none;
}
.form-actions {
  text-align: center;
  margin-top: 20px;
}
</style>
