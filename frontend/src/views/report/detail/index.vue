<template>
  <div class="page-container report-detail-page">
    <div class="detail-container">
      <div class="back-bar">
        <button class="back-btn" @click="$router.back()">← 返回</button>
        <div class="back-bar-right">
          <el-button
            v-if="report"
            type="success"
            size="small"
            :loading="exporting"
            @click="handleExportPdf">
            <el-icon><Download /></el-icon>
            导出 PDF
          </el-button>
          <button
            v-if="diagnosisId"
            class="diagnosis-btn"
            @click="$router.push(`/report/diagnosis/${diagnosisId}`)">
            🤖 AI 深度诊断报告
          </button>
          <el-button
            v-else-if="!loadingDiagnosis && report && !loadFailed"
            type="primary"
            size="small"
            :loading="generating"
            class="diagnosis-gen-btn"
            @click="handleGenerateDiagnosis">
            生成 AI 深度诊断
          </el-button>
        </div>
      </div>
      <div v-if="loading" class="loading-state">
        <div class="loading-spinner"></div>
        <span>加载中...</span>
      </div>
      <template v-else-if="report">
        <div class="score-section">
          <div class="score-card">
            <div class="score-value" :style="{ color: getScoreColor(report.totalScore) }">{{ report.totalScore }}</div>
            <div class="score-label">总分</div>
            <span :class="['level-tag', getLevelClass(report.level)]">{{ report.level }}</span>
          </div>
        </div>
        <div class="info-bar">
          <div class="info-item">
            <span class="info-label">岗位</span>
            <span class="info-value">{{ positionMap[report.position] || report.position }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">轮次</span>
            <span class="info-value">{{ roundMap[report.round] || report.round }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">难度</span>
            <span class="info-value">{{ difficultyMap[report.difficulty] || report.difficulty }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">时间</span>
            <span class="info-value">{{ formatDate(report.createdAt) }}</span>
          </div>
        </div>
        <div class="section">
          <h3 class="section-title">逐题点评</h3>
          <div v-for="(item, index) in report.comments" :key="index" class="comment-card">
            <div class="comment-header">
              <span class="comment-index">第{{ index + 1 }}题</span>
              <span class="comment-duration" v-if="item.durationSeconds">
                ⏱ {{ formatDuration(item.durationSeconds) }}
              </span>
              <span class="comment-score" :style="{ color: getScoreColor(item.score) }">{{ item.score }}分</span>
            </div>
            <div class="comment-question">{{ item.question }}</div>
            <div class="comment-answer" v-if="item.yourAnswer">
              <span class="answer-label">你的回答：</span>{{ item.yourAnswer }}
            </div>
            <div class="comment-text">{{ item.comment }}</div>
          </div>
        </div>
        <div class="section" v-if="report.suggestion">
          <h3 class="section-title">改进建议</h3>
          <div class="suggestion-card">{{ report.suggestion }}</div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onActivated, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getReportDetail } from '@/api/interview'
import { getDiagnosisBySession, generateDiagnosis } from '@/api/interview'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import { positionMap, roundMap, difficultyMap } from '@/utils/constants'
import { exportReportPdf } from '@/utils/exportPdf'

const route = useRoute()
const report = ref(null)
const loading = ref(true)
const diagnosisId = ref(null)        // 如果有诊断报告，存其ID
const loadingDiagnosis = ref(false)  // 是否正在检查诊断报告
const generating = ref(false)        // 是否正在生成诊断
const loadFailed = ref(false)        // 报告加载失败标记
const exporting = ref(false)         // 是否正在导出 PDF



// 监听路由参数变化，重新加载报告（仅在当前路由是 ReportDetail 时触发，
// 避免 keep-alive 下导航到 /report/diagnosis/:id 时误触发）
watch(() => route.params.id, (newId) => {
  if (newId && route.name === 'ReportDetail') {
    loadReport(newId)
  }
}, { immediate: true })

// keep-alive 重新激活时，强制刷新（解决首次加载时序问题）
onActivated(() => {
  if (route.name !== 'ReportDetail') return
  const id = route.params.id
  if (id) {
    loadReport(id)
  }
})

async function loadReport(id) {
  loading.value = true
  report.value = null  // 清空旧数据
  loadFailed.value = false
  diagnosisId.value = null
  try {
    const res = await getReportDetail(id)
    if (res.code === 200 && res.data) {
      report.value = res.data
      // 异步检查是否有诊断报告
      checkDiagnosis(res.data.interviewId)
    }
  } catch (e) {
    loadFailed.value = true
    console.error('加载报告失败', e)
  } finally {
    loading.value = false
  }
}

async function checkDiagnosis(sessionId) {
  if (!sessionId) return
  loadingDiagnosis.value = true
  try {
    const res = await getDiagnosisBySession(sessionId)
    if (res.code === 200 && res.data?.id) {
      diagnosisId.value = res.data.id
    }
  } catch {
    // 诊断报告不存在是正常情况，不做处理
    diagnosisId.value = null
  } finally {
    loadingDiagnosis.value = false
  }
}

async function handleGenerateDiagnosis() {
  if (!report.value?.interviewId) return
  generating.value = true
  const loadingMsg = ElMessage({ message: 'AI 正在生成深度诊断报告，预计需要 30-60 秒，请耐心等待...', type: 'info', duration: 0 })
  try {
    const res = await generateDiagnosis(report.value.interviewId)
    loadingMsg.close()
    if (res.code === 200 && res.data?.id) {
      diagnosisId.value = res.data.id
      ElMessage.success('诊断报告生成成功')
      // 直接跳转到诊断报告页
      await nextTick()
      setTimeout(() => {
        window.location.href = `/report/diagnosis/${res.data.id}`
      }, 500)
    }
  } catch (e) {
    loadingMsg.close()
    // 错误消息已在 axios 拦截器中统一展示（如 403 权限不足、500 服务器错误等）
    // 此处补充兜底提示
    const msg = e?.message || e?.response?.data?.message
    if (!msg) {
      ElMessage.error('生成诊断报告失败，请稍后重试')
    }
  } finally {
    generating.value = false
  }
}

async function handleExportPdf() {
  exporting.value = true
  try {
    await exportReportPdf(route.params.id)
    ElMessage.success('PDF 导出成功')
  } catch (e) {
    ElMessage.error('导出 PDF 失败: ' + (e.message || '未知错误'))
  } finally {
    exporting.value = false
  }
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
  return `${d.getFullYear()}-${(d.getMonth()+1).toString().padStart(2,'0')}-${d.getDate().toString().padStart(2,'0')} ${d.getHours().toString().padStart(2,'0')}:${d.getMinutes().toString().padStart(2,'0')}`
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

.report-detail-page {
  padding: 32px 24px;
}

.detail-container {
  max-width: 900px;
  margin: 0 auto;
}

.back-bar {
  margin-bottom: 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.back-bar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.back-btn {
  padding: 8px 16px;
  border: 1px solid $border-color;
  border-radius: 6px;
  background: #fff;
  color: $text-primary;
  font-size: 13px;
  cursor: pointer;
  transition: all $transition-fast;
}

.diagnosis-btn {
  padding: 8px 18px;
  border: none;
  border-radius: 6px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all $transition-normal;
  margin-left: auto;

  &:hover {
    opacity: 0.9;
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
  }
}

.diagnosis-gen-btn {
  margin-left: auto;
}

.score-section {
  display: grid;
  grid-template-columns: 1fr;
  gap: 20px;
  margin-bottom: 20px;
}

.score-card {
  background: #fff;
  border: 1px solid $border-color;
  border-radius: 12px;
  padding: 24px;
  text-align: center;
  padding: 32px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.score-value {
  font-size: 56px;
  font-weight: 700;
  line-height: 1;
  margin-bottom: 8px;
}

.score-label {
  font-size: 14px;
  color: $text-muted;
  margin-bottom: 16px;
}

.level-tag {
  padding: 6px 20px;
  border-radius: 16px;
  font-size: 14px;
  font-weight: 500;

  &.excellent { background: $bg-success; color: $color-success; }
  &.good { background: $bg-info; color: $accent-color; }
  &.pass { background: $bg-warning; color: $color-warning; }
  &.fail { background: $bg-danger; color: $color-destructive; }
}

.info-bar {
  background: #fff;
  border: 1px solid $border-color;
  border-radius: 12px;
  padding: 24px;
  border-radius: 10px;
  padding: 16px 24px;
  margin-bottom: 24px;
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.info-label { font-size: 13px; color: $text-muted; }
.info-value { font-size: 13px; color: $text-primary; font-weight: 500; }

.section { margin-bottom: 24px; }

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
  margin: 0 0 16px 0;
}

.comment-card {
  background: #fff;
  border: 1px solid $border-color;
  border-radius: 12px;
  padding: 24px;
  border-radius: 10px;
  margin-bottom: 12px;
}

.comment-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.comment-index {
  font-size: 13px;
  font-weight: 600;
  color: $accent-color;
  background: $bg-info;
  padding: 2px 10px;
  border-radius: 10px;
}

.comment-score {
  font-size: 16px;
  font-weight: 600;
}

.comment-duration {
  font-size: 12px;
  color: $text-muted;
  margin-left: auto;
  margin-right: 8px;
}

.comment-question {
  font-size: 14px;
  font-weight: 500;
  color: $text-primary;
  margin-bottom: 10px;
  line-height: 1.5;
}

.comment-answer {
  font-size: 13px;
  color: $text-muted;
  background: $bg-muted;
  padding: 10px 14px;
  border-radius: 8px;
  margin-bottom: 10px;
  line-height: 1.5;
}

.answer-label { font-weight: 500; color: $text-primary; }

.comment-text {
  font-size: 13px;
  color: $text-secondary;
  line-height: 1.6;
}

.suggestion-card {
  background: #fff;
  border: 1px solid $border-color;
  border-radius: 12px;
  padding: 24px;
  border-radius: 10px;
  font-size: 14px;
  color: $text-secondary;
  line-height: 1.8;
}

@media (max-width: 768px) {
  .report-detail-page { padding: 20px 16px; }
  .score-section { grid-template-columns: 1fr; }
  .info-bar { flex-direction: column; gap: 12px; }
}
</style>
