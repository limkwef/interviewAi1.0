<template>
  <div class="user-model-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <div>
            <span>我的模型</span>
            <span class="subtitle">管理你自己的 AI 模型，添加后可在面试时选择使用</span>
          </div>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            添加模型
          </el-button>
        </div>
      </template>

      <el-table :data="modelList" v-loading="loading" stripe>
        <el-table-column prop="modelName" label="模型名称" width="150" />
        <el-table-column prop="modelCode" label="模型标识" width="140" />
        <el-table-column prop="provider" label="供应商" width="100" />
        <el-table-column prop="apiUrl" label="API地址" min-width="200" show-overflow-tooltip />
        <el-table-column label="创造性" width="100">
          <template #default="{ row }">
            <span>{{ row.temperature }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.userId" type="primary" size="small">我的</el-tag>
            <el-tag v-else type="info" size="small">系统</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isEnabled === 1 ? 'success' : 'info'" size="small">
              {{ row.isEnabled === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <template v-if="row.userId">
              <el-button size="small" @click="handleTest(row)" :loading="row._testing">测试</el-button>
              <el-button size="small" type="primary" @click="handleEdit(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
            <template v-else>
              <span class="system-hint">系统模型，不可编辑</span>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 添加/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑模型' : '添加模型'" width="550px">
      <el-form :model="form" label-width="100px" :rules="rules" ref="formRef">
        <el-form-item label="模型名称" prop="modelName">
          <el-input v-model="form.modelName" placeholder="如：我的 DeepSeek" />
        </el-form-item>
        <el-form-item label="模型标识" prop="modelCode">
          <el-input v-model="form.modelCode" placeholder="如：deepseek-chat" />
        </el-form-item>
        <el-form-item label="供应商" prop="provider">
          <el-select v-model="form.provider" placeholder="请选择" @change="onProviderChange">
            <el-option label="DeepSeek" value="deepseek" />
            <el-option label="OpenAI" value="openai" />
            <el-option label="阿里云" value="aliyun" />
            <el-option label="自定义" value="custom" />
          </el-select>
        </el-form-item>
        <el-form-item label="API地址" prop="apiUrl">
          <el-input v-model="form.apiUrl" placeholder="https://api.deepseek.com" />
        </el-form-item>
        <el-form-item label="API密钥" prop="apiKey">
          <el-input v-model="form.apiKey" :placeholder="isEdit ? '留空则不修改原密钥' : 'sk-xxx'" show-password />
        </el-form-item>
        <el-form-item label="创造性">
          <el-slider v-model="form.temperature" :min="0" :max="2" :step="0.1" show-input :format-tooltip="v => v.toFixed(1)" />
          <div class="form-tip">控制回答的随机性。0=最稳定，2=最随机。面试场景建议0.3-0.5</div>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.isEnabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  getUserModels, addUserModel, updateUserModel, deleteUserModel, testUserModel
} from '@/api/aiModel'

const loading = ref(false)
const submitting = ref(false)
const modelList = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(null)
const formRef = ref(null)

const PROVIDER_DEFAULTS = {
  deepseek: { apiUrl: 'https://api.deepseek.com', modelCode: 'deepseek-chat' },
  openai: { apiUrl: 'https://api.openai.com', modelCode: 'gpt-4o' },
  aliyun: { apiUrl: 'https://dashscope.aliyuncs.com/compatible-mode', modelCode: 'qwen-plus' },
  custom: { apiUrl: '', modelCode: '' }
}

const form = reactive({
  modelName: '',
  modelCode: '',
  provider: 'deepseek',
  apiUrl: 'https://api.deepseek.com',
  apiKey: '',
  temperature: 0.7,
  isEnabled: 1
})

const rules = computed(() => ({
  modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  modelCode: [{ required: true, message: '请输入模型标识', trigger: 'blur' }],
  provider: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  apiUrl: [{ required: true, message: '请输入API地址', trigger: 'blur' }],
  apiKey: isEdit.value
    ? [{ required: false }]
    : [{ required: true, message: '请输入API密钥', trigger: 'blur' }]
}))

function onProviderChange(val) {
  const defaults = PROVIDER_DEFAULTS[val]
  if (defaults) {
    form.apiUrl = defaults.apiUrl
    form.modelCode = defaults.modelCode
  }
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getUserModels()
    if (res.code === 200) {
      modelList.value = (res.data || []).map(item => ({ ...item, _testing: false }))
    }
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, {
    modelName: '', modelCode: '', provider: 'deepseek',
    apiUrl: 'https://api.deepseek.com', apiKey: '',
    temperature: 0.7, isEnabled: 1
  })
}

function handleAdd() {
  resetForm()
  isEdit.value = false
  editId.value = null
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  editId.value = row.id
  Object.assign(form, {
    modelName: row.modelName, modelCode: row.modelCode, provider: row.provider,
    apiUrl: row.apiUrl, apiKey: '',
    temperature: parseFloat(row.temperature),
    isEnabled: row.isEnabled
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  try {
    await formRef.value.validate()
  } catch { return }

  submitting.value = true
  try {
    const res = isEdit.value
      ? await updateUserModel(editId.value, { ...form })
      : await addUserModel({ ...form })
    if (res.code === 200) {
      ElMessage.success(res.message || '操作成功')
      dialogVisible.value = false
      fetchList()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除模型「${row.modelName}」？`, '提示', { type: 'warning' })
    const res = await deleteUserModel(row.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      fetchList()
    }
  } catch {}
}

async function handleTest(row) {
  row._testing = true
  try {
    const res = await testUserModel(row.id)
    if (res.data && res.data.status === 'ok') {
      ElMessage.success(`${row.modelName} 连通性测试成功`)
    } else {
      ElMessage.warning(res.message || '测试失败')
    }
  } catch {
    ElMessage.error('测试请求失败')
  } finally {
    row._testing = false
  }
}

onMounted(fetchList)
</script>

<style scoped>
.user-model-page {
  padding: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.subtitle {
  display: block;
  font-size: 13px;
  color: #909399;
  font-weight: normal;
  margin-top: 4px;
}
.system-hint {
  font-size: 12px;
  color: #909399;
}
.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.4;
}
</style>
