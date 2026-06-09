<template>
  <div class="questions-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>题库管理</span>
          <div class="header-actions">
            <el-button type="primary" @click="handleAdd">
              <el-icon><Plus /></el-icon>
              添加题目
            </el-button>
            <el-button type="success" @click="handleBatchImport">
              <el-icon><Upload /></el-icon>
              批量导入
            </el-button>
          </div>
        </div>
      </template>

      <el-form :inline="true" :model="queryParams" class="search-form">
        <el-form-item label="分类">
          <el-select v-model="queryParams.category" placeholder="全部" clearable>
            <el-option label="Java基础" value="java_basic" />
            <el-option label="Spring" value="spring" />
            <el-option label="数据库" value="database" />
            <el-option label="前端" value="frontend" />
            <el-option label="算法" value="algorithm" />
            <el-option label="设计模式" value="design_pattern" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="queryParams.difficulty" placeholder="全部" clearable>
            <el-option label="简单" value="easy" />
            <el-option label="中等" value="medium" />
            <el-option label="困难" value="hard" />
          </el-select>
        </el-form-item>
        <el-form-item label="方向">
          <el-select v-model="queryParams.direction" placeholder="全部" clearable>
            <el-option label="Java后端" value="java_backend" />
            <el-option label="前端" value="frontend" />
            <el-option label="全栈" value="fullstack" />
            <el-option label="算法" value="algorithm" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="queryParams.keyword"
            placeholder="题目标题"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="题目标题" min-width="200" />
        <el-table-column prop="category" label="分类" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ getCategoryLabel(row.category) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="difficulty" label="难度" width="100">
          <template #default="{ row }">
            <el-tag :type="getDifficultyType(row.difficulty)" size="small">
              {{ getDifficultyLabel(row.difficulty) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="direction" label="方向" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ getDirectionLabel(row.direction) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="viewCount" label="浏览数" width="100" />
        <el-table-column prop="favoriteCount" label="收藏数" width="100" />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="queryParams.page"
        v-model:page-size="queryParams.size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSearch"
        @current-change="handleSearch"
        class="pagination"
      />
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogType === 'add' ? '添加题目' : '编辑题目'"
      width="700px"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
      >
        <el-form-item label="题目标题" prop="title">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="题目内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="4"
          />
        </el-form-item>
        <el-form-item label="参考答案" prop="answer">
          <el-input
            v-model="form.answer"
            type="textarea"
            :rows="4"
          />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="分类" prop="category">
              <el-select v-model="form.category" style="width: 100%">
                <el-option label="Java基础" value="java_basic" />
                <el-option label="Spring" value="spring" />
                <el-option label="数据库" value="database" />
                <el-option label="前端" value="frontend" />
                <el-option label="算法" value="algorithm" />
                <el-option label="设计模式" value="design_pattern" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="难度" prop="difficulty">
              <el-select v-model="form.difficulty" style="width: 100%">
                <el-option label="简单" value="easy" />
                <el-option label="中等" value="medium" />
                <el-option label="困难" value="hard" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="方向" prop="direction">
              <el-select v-model="form.direction" style="width: 100%">
                <el-option label="Java后端" value="java_backend" />
                <el-option label="前端" value="frontend" />
                <el-option label="全栈" value="fullstack" />
                <el-option label="算法" value="algorithm" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleDialogConfirm">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="importDialogVisible"
      title="批量导入"
      width="500px"
    >
      <div class="import-tips">
        <p>请上传 JSON 格式的题目数据</p>
        <el-upload
          class="upload-demo"
          :auto-upload="false"
          :on-change="handleFileChange"
          :limit="1"
          accept=".json"
        >
          <el-button type="primary">选择文件</el-button>
          <template #tip>
            <div class="el-upload__tip">
              只能上传 JSON 文件
            </div>
          </template>
        </el-upload>
      </div>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!importFile" @click="handleImportConfirm">
          导入
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus, Upload, Search, Refresh, Edit, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getQuestionList,
  addQuestion,
  updateQuestion,
  deleteQuestion,
  batchImportQuestions
} from '@/api/admin'
import { difficultyMap } from '@/utils/constants'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const queryParams = reactive({
  page: 1,
  size: 10,
  category: '',
  difficulty: '',
  direction: '',
  keyword: ''
})

const dialogVisible = ref(false)
const dialogType = ref('add')
const currentQuestion = ref(null)
const formRef = ref(null)
const form = reactive({
  title: '',
  content: '',
  answer: '',
  category: '',
  difficulty: '',
  direction: ''
})

const rules = {
  title: [{ required: true, message: '请输入题目标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入题目内容', trigger: 'blur' }],
  answer: [{ required: true, message: '请输入参考答案', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  difficulty: [{ required: true, message: '请选择难度', trigger: 'change' }],
  direction: [{ required: true, message: '请选择方向', trigger: 'change' }]
}

const importDialogVisible = ref(false)
const importFile = ref(null)

const categoryMap = {
  java_basic: 'Java基础',
  spring: 'Spring',
  database: '数据库',
  frontend: '前端',
  algorithm: '算法',
  design_pattern: '设计模式'
}


const directionMap = {
  java_backend: 'Java后端',
  frontend: '前端',
  fullstack: '全栈',
  algorithm: '算法'
}

onMounted(() => {
  handleSearch()
})

function getCategoryLabel(category) {
  return categoryMap[category] || category
}

function getDifficultyLabel(difficulty) {
  return difficultyMap[difficulty] || difficulty
}

function getDifficultyType(difficulty) {
  const typeMap = { easy: 'success', medium: 'warning', hard: 'danger' }
  return typeMap[difficulty] || 'info'
}

function getDirectionLabel(direction) {
  return directionMap[direction] || direction
}

async function handleSearch() {
  loading.value = true
  try {
    const res = await getQuestionList(queryParams)
    tableData.value = res.data.list
    total.value = res.data.total
  } catch (err) {
    ElMessage.error('获取题目列表失败')
  } finally {
    loading.value = false
  }
}

function handleReset() {
  queryParams.page = 1
  queryParams.category = ''
  queryParams.difficulty = ''
  queryParams.direction = ''
  queryParams.keyword = ''
  handleSearch()
}

function handleAdd() {
  dialogType.value = 'add'
  Object.assign(form, {
    title: '',
    content: '',
    answer: '',
    category: '',
    difficulty: '',
    direction: ''
  })
  dialogVisible.value = true
}

function handleEdit(row) {
  currentQuestion.value = row
  dialogType.value = 'edit'
  Object.assign(form, {
    title: row.title,
    content: row.content,
    answer: row.answer,
    category: row.category,
    difficulty: row.difficulty,
    direction: row.direction
  })
  dialogVisible.value = true
}

async function handleDialogConfirm() {
  await formRef.value?.validate()
  try {
    if (dialogType.value === 'add') {
      await addQuestion(form)
      ElMessage.success('添加成功')
    } else {
      await updateQuestion(currentQuestion.value.id, form)
      ElMessage.success('更新成功')
    }
    dialogVisible.value = false
    handleSearch()
  } catch (err) {
    ElMessage.error('操作失败')
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定要删除这个题目吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteQuestion(row.id)
    ElMessage.success('删除成功')
    handleSearch()
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

function handleBatchImport() {
  importFile.value = null
  importDialogVisible.value = true
}

function handleFileChange(file) {
  importFile.value = file.raw
}

async function handleImportConfirm() {
  if (!importFile.value) return
  const reader = new FileReader()
  reader.onload = async (e) => {
    try {
      const questions = JSON.parse(e.target.result)
      await batchImportQuestions(questions)
      ElMessage.success('导入成功')
      importDialogVisible.value = false
      handleSearch()
    } catch (err) {
      ElMessage.error('导入失败，请检查文件格式')
    }
  }
  reader.readAsText(importFile.value)
}
</script>

<style lang="scss" scoped>
.questions-page {
  padding: 20px;

  .card-header {
    font-weight: bold;
    font-size: 16px;
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .search-form {
    margin-bottom: 20px;
  }

  .pagination {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
  }

  .import-tips {
    p {
      margin-bottom: 16px;
      color: #666;
    }
  }
}
</style>
