<template>
  <div class="page-container report-list-page">
    <div class="page-header">
      <h1 class="page-title">面试报告</h1>
      <p class="page-subtitle">查看你的面试历史和评估报告</p>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <div class="filter-row">
        <el-select v-model="filters.position" placeholder="岗位" clearable size="default" style="width: 130px;">
          <el-option label="Java后端" value="java_backend" />
          <el-option label="前端开发" value="frontend" />
          <el-option label="全栈开发" value="fullstack" />
          <el-option label="算法工程师" value="algorithm" />
        </el-select>
        <el-select v-model="filters.round" placeholder="轮次" clearable size="default" style="width: 120px;">
          <el-option label="技术面" value="technical" />
          <el-option label="HR面" value="hr" />
          <el-option label="综合面" value="comprehensive" />
        </el-select>
        <el-select v-model="filters.difficulty" placeholder="难度" clearable size="default" style="width: 110px;">
          <el-option label="简单" value="easy" />
          <el-option label="中等" value="medium" />
          <el-option label="困难" value="hard" />
        </el-select>
        <div class="score-range">
          <el-input-number v-model="filters.scoreMin" :min="0" :max="100" placeholder="最低分" controls-position="right" size="default" style="width: 100px;" />
          <span class="range-sep">-</span>
          <el-input-number v-model="filters.scoreMax" :min="0" :max="100" placeholder="最高分" controls-position="right" size="default" style="width: 100px;" />
        </div>
        <el-date-picker v-model="filters.dateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" size="default" style="width: 260px;" value-format="YYYY-MM-DD" />
      </div>
      <div class="filter-actions">
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </div>
    </div>

    <div v-if="loading" class="loading-state">
      <div class="loading-spinner"></div>
      <span>加载中...</span>
    </div>
    <div v-else-if="reports.length === 0" class="empty-state">
      <div class="empty-icon">📊</div>
      <p class="empty-text">暂无面试记录</p>
      <button class="btn-primary-custom empty-btn" @click="$router.push('/interview/config')">开始面试</button>
    </div>
    <div v-else class="report-grid">
      <div v-for="report in reports" :key="report.id" class="report-card">
        <div class="card-top">
          <div class="card-score" :style="{ color: getScoreColor(report.totalScore) }" @click="viewDetail(report.id)">
            {{ report.totalScore }}
          </div>
          <div class="card-level">
            <span :class="['status-tag', 'level-tag', getLevelClass(report.level)]">{{ report.level }}</span>
          </div>
        </div>
        <div class="card-info" @click="viewDetail(report.id)">
          <div class="info-row">
            <span class="info-label">岗位</span>
            <span class="info-value">{{ positionMap[report.position] || report.position }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">轮次</span>
            <span class="info-value">{{ roundMap[report.round] || report.round }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">难度</span>
            <span class="info-value">{{ difficultyMap[report.difficulty] || report.difficulty }}</span>
          </div>
          <div class="info-row" v-if="report.totalDurationSeconds">
            <span class="info-label">用时</span>
            <span class="info-value">{{ formatDuration(report.totalDurationSeconds) }}</span>
          </div>
        </div>
        <div class="card-footer">
          <span class="footer-date">{{ formatDate(report.createdAt) }}</span>
          <div class="footer-actions">
            <span class="footer-link" @click="viewDetail(report.id)">查看详情 →</span>
            <el-popconfirm
              title="确定要删除这份面试报告吗？"
              confirm-button-text="删除"
              cancel-button-text="取消"
              confirm-button-type="danger"
              @confirm="handleDelete(report.id)"
            >
              <template #reference>
                <el-button type="danger" link size="small" @click.stop>
                  <el-icon><Delete /></el-icon>
                </el-button>
              </template>
            </el-popconfirm>
          </div>
        </div>
      </div>
    </div>
    <div v-if="total > pageSize" class="pagination-wrap pagination">
      <button class="btn-ghost page-btn" :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
      <span class="page-info">{{ page }} / {{ Math.ceil(total / pageSize) }}</span>
      <button class="btn-ghost page-btn" :disabled="page >= Math.ceil(total / pageSize)" @click="changePage(page + 1)">下一页</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getReportList, deleteReport } from '@/api/interview'
import { Delete, Search, Refresh } from '@element-plus/icons-vue'
import { positionMap, roundMap, difficultyMap } from '@/utils/constants'
import { ElMessage } from 'element-plus'

const router = useRouter()
const reports = ref([])
const loading = ref(true)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 筛选条件
const filters = ref({
  position: '',
  round: '',
  difficulty: '',
  scoreMin: null,
  scoreMax: null,
  dateRange: []
})



onMounted(() => { loadReports() })

async function loadReports() {
  loading.value = true
  try {
    const params = {
      page: page.value,
      pageSize: pageSize.value,
      ...filters.value
    }
    // 处理日期范围
    if (params.dateRange && params.dateRange.length === 2) {
      params.startDate = params.dateRange[0]
      params.endDate = params.dateRange[1]
    }
    delete params.dateRange

    const res = await getReportList(params)
    if (res.code === 200) {
      reports.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch (e) {
    console.error('加载报告列表失败', e)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  loadReports()
}

function handleReset() {
  filters.value = {
    position: '',
    round: '',
    difficulty: '',
    scoreMin: null,
    scoreMax: null,
    dateRange: []
  }
  page.value = 1
  loadReports()
}

async function handleDelete(reportId) {
  try {
    await deleteReport(reportId)
    ElMessage.success('报告已删除')
    if (reports.value.length === 1 && page.value > 1) {
      page.value--
    }
    await loadReports()
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

function changePage(p) {
  page.value = p
  loadReports()
}

function viewDetail(id) {
  router.push(`/report/detail/${id}`)
}

function getScoreColor(score) {
  if (score >= 90) return '#16A34A'
  if (score >= 75) return '#2563EB'
  if (score >= 60) return '#EAB308'
  return '#DC2626'
}

function getLevelClass(level) {
  if (level === '优秀') return 'excellent'
  if (level === '良好') return 'good'
  if (level === '合格') return 'pass'
  return 'fail'
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${(d.getMonth()+1).toString().padStart(2,'0')}-${d.getDate().toString().padStart(2,'0')}`
}

function formatDuration(seconds) {
  if (!seconds || seconds < 0) return '0:00'
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${s.toString().padStart(2, '0')}`
}
</script>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

// .page-container 全局已定义 min-height, background, padding
.report-list-page { padding: 32px 24px; }

// .page-header, .page-title, .page-subtitle, .loading-state, .loading-spinner, @keyframes spin 全局已定义，此处删除

.filter-bar {
  background: #fff;
  border: 1px solid $border-color;
  border-radius: 10px;
  padding: 20px;
  margin-bottom: 20px;
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
}

.score-range {
  display: flex;
  align-items: center;
  gap: 8px;
}

.range-sep {
  color: $text-muted;
}

.filter-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.empty-state {
  text-align: center;
  padding: 80px 0;
}

.empty-icon { font-size: 48px; margin-bottom: 16px; }
.empty-text { font-size: 14px; color: $text-muted; margin: 0 0 20px 0; }

// .empty-btn 由全局 .btn-primary-custom 覆盖，无需额外样式

.report-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.report-card {
  background: #fff;
  border: 1px solid $border-color;
  border-radius: 10px;
  padding: 20px;
  cursor: pointer;
  transition: border-color $transition-fast;

  &:hover { border-color: $accent-color; }
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.card-score {
  font-size: 36px;
  font-weight: 700;
  line-height: 1;
}

// .status-tag 全局已定义基础样式，level-tag 仅保留修饰符差异
.level-tag {
  &.excellent { background: $bg-success; color: $color-success; }
  &.good { background: $bg-info; color: $accent-color; }
  &.pass { background: $bg-warning; color: $color-warning; }
  &.fail { background: $bg-danger; color: $color-destructive; }
}

.card-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
}

.info-label { color: $text-muted; }
.info-value { color: $text-primary; font-weight: 500; }

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid $bg-muted;
}

.footer-date { font-size: 12px; color: $text-tertiary; }
.footer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.footer-link { font-size: 13px; color: $accent-color; font-weight: 500; cursor: pointer; }

// .pagination-wrap 全局已定义基础样式，pagination 仅保留差异
.pagination { margin-top: 32px; }

// .page-btn 由全局 .btn-ghost 覆盖，无需额外样式

.page-info { font-size: 13px; color: $text-muted; }

@media (max-width: 768px) {
  .report-list-page { padding: 20px 16px; }
  .report-grid { grid-template-columns: 1fr; }
}
</style>
