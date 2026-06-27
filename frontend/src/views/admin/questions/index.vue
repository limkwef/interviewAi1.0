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
          <el-select v-model="queryParams.category" placeholder="全部" clearable style="width: 160px;">
            <el-option v-for="(label, value) in categoryMap" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="queryParams.difficulty" placeholder="全部" clearable style="width: 160px;">
            <el-option label="简单" value="easy" />
            <el-option label="中等" value="medium" />
            <el-option label="困难" value="hard" />
          </el-select>
        </el-form-item>
        <el-form-item label="方向">
          <el-select v-model="queryParams.direction" placeholder="全部" clearable style="width: 160px;">
            <el-option label="Java后端" value="java_backend" />
            <el-option label="前端" value="frontend" />
            <el-option label="全栈" value="fullstack" />
            <el-option label="算法" value="algorithm" />
            <el-option label="HR/软素质" value="hr" />
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
            <div class="action-buttons">
              <el-button size="small" @click="handleEdit(row)">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
              <el-button size="small" type="danger" @click="handleDelete(row)">
                <el-icon><Delete /></el-icon>
                删除
              </el-button>
            </div>
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
                <el-option v-for="(label, value) in categoryMap" :key="value" :label="label" :value="value" />
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
                <el-option label="HR/软素质" value="hr" />
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
      width="700px"
    >
      <div class="import-section">
        <div class="import-header">
          <el-button type="text" @click="downloadTemplate">
            <el-icon><Download /></el-icon>
            下载导入模板
          </el-button>
        </div>

        <el-upload
          class="upload-area"
          :auto-upload="false"
          :on-change="handleFileChange"
          :limit="1"
          accept=".json,.xlsx,.xls,.csv"
          :file-list="[]"
        >
          <el-button type="primary">选择文件</el-button>
          <template #tip>
            <div class="el-upload__tip">
              支持 JSON、Excel (.xlsx)、CSV 格式
            </div>
          </template>
        </el-upload>
      </div>

      <!-- 数据预览 -->
      <div v-if="previewData.length > 0" class="preview-section">
        <h4>数据预览（前5条）</h4>
        <el-table :data="previewData.slice(0, 5)" border size="small">
          <el-table-column prop="title" label="标题" width="150" show-overflow-tooltip />
          <el-table-column prop="category" label="分类" width="100">
            <template #default="{ row }">{{ getCategoryLabel(row.category) }}</template>
          </el-table-column>
          <el-table-column prop="difficulty" label="难度" width="80">
            <template #default="{ row }">{{ getDifficultyLabel(row.difficulty) }}</template>
          </el-table-column>
          <el-table-column prop="direction" label="方向" width="100">
            <template #default="{ row }">{{ getDirectionLabel(row.direction) }}</template>
          </el-table-column>
          <el-table-column label="校验结果" width="120">
            <template #default="{ row }">
              <el-tag :type="row._valid ? 'success' : 'danger'" size="small">
                {{ row._valid ? '通过' : row._errors }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 导入进度 -->
      <div v-if="importing" class="progress-section">
        <el-progress :percentage="progress" :stroke-width="10" />
        <p class="progress-text">正在导入... {{ progress }}%</p>
      </div>

      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!importFile || importing" @click="handleImportConfirm">
          开始导入
        </el-button>
      </template>
    </el-dialog>

    <!-- 导入结果弹窗 -->
    <el-dialog
      v-model="resultDialogVisible"
      title="导入结果"
      width="500px"
    >
      <div v-if="importResult" class="result-section">
        <div class="result-summary">
          <div class="result-item success">
            <span class="result-count">{{ importResult.successCount }}</span>
            <span class="result-label">成功</span>
          </div>
          <div class="result-item warning">
            <span class="result-count">{{ importResult.skipCount }}</span>
            <span class="result-label">跳过</span>
          </div>
          <div class="result-item danger">
            <span class="result-count">{{ importResult.failCount }}</span>
            <span class="result-label">失败</span>
          </div>
        </div>

        <div v-if="importResult.failures && importResult.failures.length > 0" class="result-detail">
          <h4>失败详情</h4>
          <el-table :data="importResult.failures" border size="small">
            <el-table-column prop="index" label="行号" width="60" />
            <el-table-column prop="title" label="标题" width="150" show-overflow-tooltip />
            <el-table-column prop="reason" label="失败原因" />
          </el-table>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="resultDialogVisible = false">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus, Upload, Search, Refresh, Edit, Delete, Download } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getQuestionList,
  addQuestion,
  updateQuestion,
  deleteQuestion,
  batchImportQuestions
} from '@/api/admin'
import { categoryMap, difficultyMap } from '@/utils/constants'
import { parseImportFile, validateQuestions, downloadTemplate } from '@/utils/importHelper'

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
const previewData = ref([])
const importing = ref(false)
const progress = ref(0)
const resultDialogVisible = ref(false)
const importResult = ref(null)

const directionMap = {
  java_backend: 'Java后端',
  frontend: '前端',
  fullstack: '全栈',
  algorithm: '算法',
  hr: 'HR/软素质'
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
    const res = await getQuestionList({
      ...queryParams,
      _t: Date.now()  // 添加时间戳防止缓存
    })
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
  previewData.value = []
  importing.value = false
  progress.value = 0
  importResult.value = null
  importDialogVisible.value = true
}

async function handleFileChange(file) {
  importFile.value = file.raw
  try {
    const data = await parseImportFile(file.raw)
    previewData.value = validateQuestions(data)
  } catch (err) {
    ElMessage.error('文件解析失败：' + err.message)
    importFile.value = null
    previewData.value = []
  }
}

async function handleImportConfirm() {
  if (!importFile.value || previewData.value.length === 0) return

  // 检查是否有校验失败的数据
  const validData = previewData.value.filter(item => item._valid)
  if (validData.length === 0) {
    ElMessage.error('没有通过校验的数据，请检查文件内容')
    return
  }

  importing.value = true
  progress.value = 0

  try {
    // 分批导入，每批100条
    const chunkSize = 100
    const chunks = []
    for (let i = 0; i < validData.length; i += chunkSize) {
      chunks.push(validData.slice(i, i + chunkSize))
    }

    let totalResult = { successCount: 0, skipCount: 0, failCount: 0, failures: [] }

    for (let i = 0; i < chunks.length; i++) {
      const result = await batchImportQuestions({ questions: chunks[i], duplicateStrategy: 'skip' })
      if (result.data) {
        totalResult.successCount += result.data.successCount || 0
        totalResult.skipCount += result.data.skipCount || 0
        totalResult.failCount += result.data.failCount || 0
        if (result.data.failures) {
          totalResult.failures = totalResult.failures.concat(result.data.failures)
        }
      }
      progress.value = Math.round(((i + 1) / chunks.length) * 100)
    }

    importResult.value = totalResult
    importDialogVisible.value = false
    resultDialogVisible.value = true
    handleSearch()
  } catch (err) {
    ElMessage.error('导入失败：' + (err.message || '未知错误'))
  } finally {
    importing.value = false
    progress.value = 0
  }
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

  .action-buttons {
    display: flex;
    gap: 8px;
    align-items: center;
    justify-content: center;
  }

  .pagination {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
  }

  .import-section {
    margin-bottom: 20px;

    .import-header {
      margin-bottom: 16px;
    }

    .upload-area {
      width: 100%;
    }
  }

  .preview-section {
    margin-top: 20px;
    padding-top: 20px;
    border-top: 1px solid #eee;

    h4 {
      margin-bottom: 12px;
      font-weight: bold;
      color: #333;
    }
  }

  .progress-section {
    margin-top: 20px;
    padding-top: 20px;
    border-top: 1px solid #eee;

    .progress-text {
      margin-top: 8px;
      text-align: center;
      color: #666;
    }
  }

  .result-section {
    .result-summary {
      display: flex;
      justify-content: space-around;
      margin-bottom: 24px;
      padding: 20px 0;
      background: #f5f5f5;
      border-radius: 8px;
    }

    .result-item {
      text-align: center;

      .result-count {
        display: block;
        font-size: 32px;
        font-weight: bold;
        line-height: 1.2;
      }

      .result-label {
        display: block;
        margin-top: 8px;
        font-size: 14px;
        color: #666;
      }

      &.success .result-count {
        color: #67c23a;
      }

      &.warning .result-count {
        color: #e6a23c;
      }

      &.danger .result-count {
        color: #f56c6c;
      }
    }

    .result-detail {
      h4 {
        margin-bottom: 12px;
        font-weight: bold;
        color: #333;
      }
    }
  }
}
</style>
