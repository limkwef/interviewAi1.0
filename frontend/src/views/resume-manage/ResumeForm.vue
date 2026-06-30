<template>
  <div class="page-container resume-form-page">
    <div class="page-header">
      <h1 class="page-title">在线填写简历</h1>
      <p class="page-subtitle">填写你的个人信息和工作经历，生成结构化简历</p>
    </div>
    <div class="form-card">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" label-position="top">
        <!-- 基本信息 -->
        <h3 class="section-title">基本信息</h3>
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
            <el-form-item label="电话">
              <el-input v-model="form.basicInfo.phone" placeholder="请输入电话" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="目标岗位">
              <el-input v-model="form.basicInfo.targetPosition" placeholder="如：Java开发工程师" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="所在城市">
              <el-input v-model="form.basicInfo.location" placeholder="如：北京" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="工作年限">
              <el-input-number v-model="form.basicInfo.yearsOfExperience" :min="0" :max="50" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 教育经历 -->
        <h3 class="section-title">教育经历</h3>
        <div v-for="(edu, idx) in form.education" :key="idx" class="sub-form-item">
          <el-row :gutter="16">
            <el-col :span="6">
              <el-form-item label="学校"><el-input v-model="edu.school" placeholder="学校名称" /></el-form-item>
            </el-col>
            <el-col :span="4">
              <el-form-item label="学历"><el-input v-model="edu.degree" placeholder="如：本科" /></el-form-item>
            </el-col>
            <el-col :span="4">
              <el-form-item label="专业"><el-input v-model="edu.major" placeholder="专业" /></el-form-item>
            </el-col>
            <el-col :span="4">
              <el-form-item label="开始时间"><el-input v-model="edu.startDate" placeholder="2018.09" /></el-form-item>
            </el-col>
            <el-col :span="4">
              <el-form-item label="结束时间"><el-input v-model="edu.endDate" placeholder="2022.06" /></el-form-item>
            </el-col>
            <el-col :span="2">
              <el-button class="remove-btn" type="danger" text @click="form.education.splice(idx, 1)">删除</el-button>
            </el-col>
          </el-row>
        </div>
        <el-button type="primary" text @click="addEducation">+ 添加教育经历</el-button>

        <!-- 工作经历 -->
        <h3 class="section-title">工作经历</h3>
        <div v-for="(work, idx) in form.workExperience" :key="idx" class="sub-form-item">
          <el-row :gutter="16">
            <el-col :span="6">
              <el-form-item label="公司"><el-input v-model="work.company" placeholder="公司名称" /></el-form-item>
            </el-col>
            <el-col :span="4">
              <el-form-item label="职位"><el-input v-model="work.position" placeholder="职位" /></el-form-item>
            </el-col>
            <el-col :span="3">
              <el-form-item label="开始"><el-input v-model="work.startDate" placeholder="2022.07" /></el-form-item>
            </el-col>
            <el-col :span="3">
              <el-form-item label="结束"><el-input v-model="work.endDate" placeholder="至今" /></el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="工作描述"><el-input v-model="work.description" type="textarea" :rows="1" placeholder="主要职责" /></el-form-item>
            </el-col>
            <el-col :span="2">
              <el-button class="remove-btn" type="danger" text @click="form.workExperience.splice(idx, 1)">删除</el-button>
            </el-col>
          </el-row>
        </div>
        <el-button type="primary" text @click="addWork">+ 添加工作经历</el-button>

        <!-- 技能 -->
        <h3 class="section-title">技能</h3>
        <div v-for="(skill, idx) in form.skills" :key="idx" class="sub-form-item">
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="技能名称"><el-input v-model="skill.name" placeholder="如：Java" /></el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="掌握程度">
                <el-select v-model="skill.level" placeholder="选择">
                  <el-option v-for="l in ['精通','熟练','熟悉','了解']" :key="l" :label="l" :value="l" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="4">
              <el-form-item label="年限"><el-input-number v-model="skill.years" :min="0" :max="30" style="width: 100%" /></el-form-item>
            </el-col>
            <el-col :span="2">
              <el-button class="remove-btn" type="danger" text @click="form.skills.splice(idx, 1)">删除</el-button>
            </el-col>
          </el-row>
        </div>
        <el-button type="primary" text @click="addSkill">+ 添加技能</el-button>

        <!-- 自我评价 -->
        <h3 class="section-title">自我评价</h3>
        <el-form-item>
          <el-input v-model="form.selfEvaluation" type="textarea" :rows="4" placeholder="简要描述你的优势和特点" />
        </el-form-item>

        <div class="form-actions">
          <el-button type="primary" :loading="submitting" @click="handleSubmit">提交简历</el-button>
          <el-button @click="$router.push('/resume/list')">取消</el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { createResume } from '@/api/resume'
import { ElMessage } from 'element-plus'

const router = useRouter()
const formRef = ref(null)
const submitting = ref(false)

const form = reactive({
  basicInfo: { name: '', email: '', phone: '', targetPosition: '', location: '', yearsOfExperience: 0 },
  education: [{ school: '', degree: '', major: '', startDate: '', endDate: '' }],
  workExperience: [{ company: '', position: '', startDate: '', endDate: '', description: '' }],
  skills: [{ name: '', level: '', years: 0 }],
  certifications: [],
  selfEvaluation: ''
})

const rules = {
  'basicInfo.name': [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  'basicInfo.email': [{ required: true, message: '请输入邮箱', trigger: 'blur' }]
}

const addEducation = () => form.education.push({ school: '', degree: '', major: '', startDate: '', endDate: '' })
const addWork = () => form.workExperience.push({ company: '', position: '', startDate: '', endDate: '', description: '' })
const addSkill = () => form.skills.push({ name: '', level: '', years: 0 })

const handleSubmit = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    const res = await createResume(form)
    ElMessage.success('创建成功')
    router.push(`/resume/detail/${res.data?.id}`)
  } catch (e) {
    ElMessage.error('创建失败：' + (e.message || '未知错误'))
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.resume-form-page { padding: 24px; }
.page-header { margin-bottom: 24px; }
.page-title { font-size: 24px; font-weight: 600; color: #1a1a2e; margin: 0 0 4px; }
.page-subtitle { font-size: 14px; color: #8c8c8c; margin: 0; }
.form-card { background: #fff; border-radius: 12px; padding: 32px; box-shadow: 0 2px 12px rgba(0,0,0,0.04); max-width: 900px; }
.section-title { font-size: 16px; font-weight: 600; color: #303133; margin: 24px 0 12px; padding-bottom: 8px; border-bottom: 1px solid #ebeef5; }
.sub-form-item { background: #fafafa; border-radius: 8px; padding: 12px; margin-bottom: 8px; }
.remove-btn { margin-top: 30px; }
.form-actions { margin-top: 24px; display: flex; gap: 12px; }
</style>
