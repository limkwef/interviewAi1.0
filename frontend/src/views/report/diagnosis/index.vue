<template>
  <div class="diagnosis-page">
    <!-- 加载状态 -->
    <div v-if="loading" class="loading-container">
      <el-icon class="loading-icon" :size="48"><Loading /></el-icon>
      <p class="loading-text">AI 正在分析您的面试表现...</p>
      <p class="loading-sub">深度诊断报告生成中，请稍候</p>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="error" class="error-container">
      <el-icon :size="64" color="#f56c6c"><CircleCloseFilled /></el-icon>
      <p class="error-text">{{ error }}</p>
      <el-button type="primary" @click="fetchDiagnosis">重试</el-button>
    </div>

    <!-- 诊断报告内容 -->
    <template v-else-if="diagnosis">
      <!-- 顶部成绩卡片 -->
      <div class="score-header-card">
        <div class="diagnosis-meta">
          <span class="meta-time">
            <el-icon><Clock /></el-icon>
            生成时间：{{ formatTime(diagnosis.createdAt) }}
          </span>
          <div class="meta-actions">
            <el-button
              type="success"
              size="small"
              :loading="exporting"
              @click="handleExportPdf"
              class="export-btn"
            >
              <el-icon><Download /></el-icon>
              导出 PDF
            </el-button>
            <el-button
              type="primary"
              size="small"
              :loading="regenerating"
              @click="handleRegenerate"
              class="regenerate-btn"
            >
              <el-icon><Refresh /></el-icon>
              基于最新数据重新生成
            </el-button>
          </div>
        </div>
        <div class="score-content">
          <div class="score-left">
            <div class="score-circle" :class="getScoreLevel(diagnosis.totalScore)">
              <span class="score-number">{{ diagnosis.totalScore }}</span>
              <span class="score-unit">分</span>
            </div>
            <div class="score-info">
              <span class="score-level">{{ diagnosis.level }}</span>
              <span class="score-change" :class="getChangeClass(diagnosis.scoreChange)">
                {{ diagnosis.scoreChange > 0 ? '+' : '' }}{{ diagnosis.scoreChange }}分
              </span>
            </div>
          </div>
          <div class="score-trend" v-if="scoreTrend.length > 0">
            <div class="trend-label">
              成长趋势
              <span class="trend-count" v-if="growthData?.totalInterviews">（共 {{ growthData.totalInterviews }} 场）</span>
            </div>
            <div class="trend-chart">
              <div v-for="(item, index) in scoreTrend" :key="index"
                   class="trend-bar" :class="{ 'trend-bar--latest': item.isLatest }"
                   :style="{ height: item.height + '%', backgroundColor: item.color }">
                <span class="trend-value">{{ item.value }}</span>
              </div>
            </div>
            <div class="trend-labels" v-if="scoreTrend.length > 1">
              <span v-for="(item, index) in scoreTrend" :key="index" class="trend-label-item">
                {{ item.date ? formatTrendDate(item.date) : index + 1 }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- Tab 切换 -->
      <el-tabs v-model="activeTab" class="diagnosis-tabs" @tab-change="onTabChange">
        <!-- 知识维度分析 -->
        <el-tab-pane label="知识维度分析" name="knowledge">
          <div class="section-card">
            <h3 class="section-title">各知识维度得分</h3>
            <div class="knowledge-grid">
              <div v-for="item in knowledgeAnalysis" :key="item.dimension" class="knowledge-item">
                <div class="knowledge-header">
                  <span class="knowledge-name">{{ item.dimension }}</span>
                  <span class="knowledge-score" :class="getScoreLevel(item.score)">{{ item.score }}分</span>
                </div>
                <el-progress :percentage="item.score" :color="getProgressColor(item.score)" :show-text="false" />
                <p class="knowledge-desc">{{ item.description }}</p>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- 思维模式分析 -->
        <el-tab-pane label="思维模式分析" name="thinking">
          <div class="section-card">
            <div class="thinking-header">
              <div class="thinking-type-badge">{{ thinkingAnalysis.type }}</div>
            </div>
            <p class="thinking-summary">{{ thinkingAnalysis.summary }}</p>

            <div class="thinking-details">
              <div class="thinking-column">
                <h4 class="column-title strengths">优势</h4>
                <ul class="thinking-list">
                  <li v-for="(item, index) in thinkingAnalysis.strengths" :key="index">
                    <el-icon color="#67c23a"><CircleCheckFilled /></el-icon>
                    {{ item }}
                  </li>
                </ul>
              </div>
              <div class="thinking-column">
                <h4 class="column-title weaknesses">待提升</h4>
                <ul class="thinking-list">
                  <li v-for="(item, index) in thinkingAnalysis.weaknesses" :key="index">
                    <el-icon color="#e6a23c"><WarningFilled /></el-icon>
                    {{ item }}
                  </li>
                </ul>
              </div>
            </div>

            <div class="thinking-suggestions">
              <h4 class="column-title">AI 建议</h4>
              <ul class="suggestion-list">
                <li v-for="(item, index) in thinkingAnalysis.suggestions" :key="index">
                  {{ item }}
                </li>
              </ul>
            </div>
          </div>
        </el-tab-pane>

        <!-- 错题模式分析 -->
        <el-tab-pane label="错题模式分析" name="mistakes">
          <div class="section-card">
            <div v-if="mistakePatterns.length === 0" class="empty-state">
              <el-icon :size="48" color="#67c23a"><CircleCheckFilled /></el-icon>
              <p>表现优秀，暂无明显错题模式</p>
            </div>
            <div v-else class="mistake-list">
              <div v-for="(item, index) in mistakePatterns" :key="index" class="mistake-card">
                <div class="mistake-header">
                  <span class="mistake-pattern">{{ item.pattern }}</span>
                  <el-tag type="warning" size="small">{{ item.frequency }}</el-tag>
                </div>
                <p class="mistake-desc">{{ item.description }}</p>
                <div class="mistake-suggestion">
                  <el-icon><InfoFilled /></el-icon>
                  <span>{{ item.suggestion }}</span>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- 学习计划 -->
        <el-tab-pane label="学习计划" name="plan">
          <div class="section-card">
            <p class="plan-summary">{{ learningPlan.summary }}</p>

            <div class="plan-phases">
              <div v-for="(phase, index) in learningPlan.phases" :key="index" class="phase-card">
                <div class="phase-header">
                  <span class="phase-number">{{ index + 1 }}</span>
                  <div class="phase-info">
                    <h4 class="phase-name">{{ phase.phase }}</h4>
                    <span class="phase-focus">{{ phase.focus }}</span>
                  </div>
                </div>
                <ul class="phase-tasks">
                  <li v-for="(task, tIndex) in phase.tasks" :key="tIndex">{{ task }}</li>
                </ul>
              </div>
            </div>

            <div v-if="learningPlan.resources && learningPlan.resources.length > 0" class="plan-resources">
              <h4 class="resources-title">推荐资源</h4>
              <div class="resources-grid">
                <div v-for="(res, index) in learningPlan.resources" :key="index" class="resource-card">
                  <el-tag :type="getResourceTagType(res.type)" size="small">{{ res.type }}</el-tag>
                  <span class="resource-name">{{ res.name }}</span>
                  <span class="resource-desc">{{ res.description }}</span>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- 竞争力对比 -->
        <el-tab-pane label="竞争力对比" name="competition">
          <div class="section-card">
            <!-- 加载中 -->
            <div v-if="compLoading" class="loading-container" style="min-height: 200px;">
              <el-icon class="loading-icon" :size="32"><Loading /></el-icon>
              <p class="loading-text" style="font-size: 15px;">正在计算竞争力数据...</p>
            </div>
            <!-- 错误 -->
            <div v-else-if="compError" class="empty-container" style="min-height: 200px;">
              <el-icon :size="48" color="#909399"><DataAnalysis /></el-icon>
              <p style="color: #909399; margin-top: 12px;">{{ compError }}</p>
            </div>
            <!-- 竞争力数据 -->
            <template v-else-if="competition">
              <!-- 分数对比 -->
              <div class="compare-header">
                <div class="score-block">
                  <span class="score-block-label">本场得分</span>
                  <span class="score-block-value" :class="getScoreLevel(diagnosis.totalScore)">
                    {{ diagnosis.totalScore }}<span class="score-block-unit">分</span>
                  </span>
                </div>
                <div class="vs-divider">
                  <span class="vs-text">VS</span>
                  <span class="vs-gap" v-if="competition.gap > 0">还差 {{ competition.gap }} 分</span>
                  <span class="vs-gap achieved" v-else>已达标</span>
                </div>
                <div class="score-block">
                  <span class="score-block-label">目标分数</span>
                  <span class="score-block-value target">
                    {{ competition.targetScore }}<span class="score-block-unit">分</span>
                  </span>
                </div>
              </div>

              <!-- 等级递进描述 -->
              <div class="tier-desc">
                {{ competition.summary || '' }}
              </div>

              <!-- 排名信息 -->
              <div class="rank-info" v-if="competition.peerTotalCount > 0">
                你在同岗位同类型面试中，排名第 <strong>{{ competition.peerRank }}</strong> 名，
                超过 <strong>{{ competition.peerPercentile }}%</strong> 的候选人
                （共 {{ competition.peerTotalCount }} 人）
              </div>

              <!-- 各维度对比 -->
              <div class="dimension-compare">
                <h4 class="sub-title">各维度对比</h4>
                <div v-for="dim in competition.dimensions" :key="dim.name" class="dim-compare-item">
                  <div class="dim-compare-header">
                    <span class="dim-compare-name">{{ dim.name }}</span>
                    <span class="dim-urgency" :class="dim.urgency">
                      {{ { high: '紧急', medium: '需提升', low: '达标' }[dim.urgency] || dim.urgency }}
                    </span>
                  </div>
                  <div class="dim-compare-bars">
                    <div class="dim-compare-bar-row">
                      <span class="bar-label-sm">当前</span>
                      <div class="bar-track-sm">
                        <div class="bar-fill-sm user" :style="{ width: dim.userScore + '%' }"></div>
                      </div>
                      <span class="bar-val-sm">{{ dim.userScore }}</span>
                    </div>
                    <div class="dim-compare-bar-row">
                      <span class="bar-label-sm">目标</span>
                      <div class="bar-track-sm">
                        <div class="bar-fill-sm target" :style="{ width: dim.targetScore + '%' }"></div>
                      </div>
                      <span class="bar-val-sm">{{ dim.targetScore }}</span>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 薄弱环节 -->
              <div class="weak-adv-section" v-if="competition.weaknesses && competition.weaknesses.length > 0">
                <h4 class="sub-title">薄弱环节</h4>
                <div class="tag-list-wrap">
                  <el-tag v-for="(w, i) in competition.weaknesses" :key="i" type="warning" effect="plain">{{ w }}</el-tag>
                </div>
              </div>

              <!-- 竞争优势 -->
              <div class="weak-adv-section" v-if="competition.competitiveAdvantage && competition.competitiveAdvantage.length > 0">
                <h4 class="sub-title">竞争优势</h4>
                <div class="tag-list-wrap">
                  <el-tag v-for="(a, i) in competition.competitiveAdvantage" :key="i" type="success" effect="plain">{{ a }}</el-tag>
                </div>
              </div>
            </template>
          </div>
        </el-tab-pane>
      </el-tabs>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading, CircleCloseFilled, CircleCheckFilled, WarningFilled, InfoFilled, DataAnalysis, Clock, Refresh, Download } from '@element-plus/icons-vue'
import { getDiagnosisById, getDiagnosisBySession, getGrowthData, generateDiagnosis } from '@/api/interview'
import { getSessionCompetition } from '@/api/competition'
import { exportDiagnosisPdf } from '@/utils/exportPdf'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const error = ref(null)
const diagnosis = ref(null)
const growthData = ref(null)
const activeTab = ref('knowledge')
const regenerating = ref(false)
const exporting = ref(false)

// 竞争力对比数据
const competition = ref(null)
const compLoading = ref(false)
const compError = ref(null)
let compLoaded = false

// 计算属性：解析 JSON 字段
const knowledgeAnalysis = computed(() => {
  if (!diagnosis.value?.knowledgeAnalysis) return []
  try {
    return typeof diagnosis.value.knowledgeAnalysis === 'string'
      ? JSON.parse(diagnosis.value.knowledgeAnalysis)
      : diagnosis.value.knowledgeAnalysis
  } catch { return [] }
})

const thinkingAnalysis = computed(() => {
  if (!diagnosis.value?.thinkingAnalysis) return { type: '', summary: '', strengths: [], weaknesses: [], suggestions: [] }
  try {
    return typeof diagnosis.value.thinkingAnalysis === 'string'
      ? JSON.parse(diagnosis.value.thinkingAnalysis)
      : diagnosis.value.thinkingAnalysis
  } catch { return { type: '', summary: '', strengths: [], weaknesses: [], suggestions: [] } }
})

const mistakePatterns = computed(() => {
  if (!diagnosis.value?.mistakePatterns) return []
  try {
    return typeof diagnosis.value.mistakePatterns === 'string'
      ? JSON.parse(diagnosis.value.mistakePatterns)
      : diagnosis.value.mistakePatterns
  } catch { return [] }
})

const learningPlan = computed(() => {
  if (!diagnosis.value?.learningPlan) return { summary: '', phases: [], resources: [] }
  try {
    return typeof diagnosis.value.learningPlan === 'string'
      ? JSON.parse(diagnosis.value.learningPlan)
      : diagnosis.value.learningPlan
  } catch { return { summary: '', phases: [], resources: [] } }
})

// 分数趋势数据
const scoreTrend = computed(() => {
  const trend = growthData.value?.trend
  if (trend && trend.length > 0) {
    const maxScore = Math.max(...trend.map(t => t.score), 100)
    return trend.slice(-6).map((t, i, arr) => ({
      value: t.score,
      height: Math.max(8, (t.score / maxScore) * 100),
      color: getProgressColor(t.score),
      date: t.date,
      isLatest: i === arr.length - 1
    }))
  }
  const score = diagnosis.value?.totalScore || 0
  if (score > 0) {
    return [{ value: score, height: Math.max(8, score), color: getProgressColor(score), date: '本次', isLatest: true }]
  }
  return []
})

onMounted(() => {
  fetchDiagnosis()
})

// keep-alive 重新激活时，强制刷新
onActivated(() => {
  fetchDiagnosis()
})

async function fetchDiagnosis() {
  loading.value = true
  error.value = null

  try {
    const id = route.params.id
    let res = await getDiagnosisById(id)

    if (!res.data) {
      res = await getDiagnosisBySession(id)
    }

    if (res.data) {
      diagnosis.value = res.data
    } else {
      error.value = '未找到诊断报告'
      return
    }

    // 并行拉取成长趋势数据
    try {
      const growthRes = await getGrowthData()
      if (growthRes.code === 200 && growthRes.data) {
        growthData.value = growthRes.data
      }
    } catch {
      console.warn('获取成长趋势数据失败')
    }
  } catch (err) {
    error.value = err.message || '获取诊断报告失败'
  } finally {
    loading.value = false
  }
}

// Tab 切换时懒加载竞争力数据
function onTabChange(tab) {
  if (tab === 'competition' && !compLoaded) {
    loadCompetition()
  }
}

async function loadCompetition() {
  compLoading.value = true
  compError.value = null
  compLoaded = true

  try {
    const sessionId = diagnosis.value?.sessionId || route.params.id
    const res = await getSessionCompetition(sessionId)
    if (res.code === 200 && res.data) {
      competition.value = res.data
    } else {
      compError.value = res.message || '获取竞争力数据失败'
    }
  } catch (err) {
    compError.value = err.response?.data?.message || err.message || '获取竞争力数据失败'
  } finally {
    compLoading.value = false
  }
}

function getScoreLevel(score) {
  if (score >= 90) return 'excellent'
  if (score >= 75) return 'good'
  if (score >= 60) return 'average'
  return 'poor'
}

function getChangeClass(change) {
  if (change > 0) return 'positive'
  if (change < 0) return 'negative'
  return 'neutral'
}

function formatTrendDate(dateStr) {
  if (!dateStr) return ''
  const parts = dateStr.split('-')
  if (parts.length >= 3) return parts[1] + '/' + parts[2]
  return dateStr.slice(5) || dateStr
}

function getProgressColor(score) {
  if (score >= 90) return '#67c23a'
  if (score >= 75) return '#409eff'
  if (score >= 60) return '#e6a23c'
  return '#f56c6c'
}

function getResourceTagType(type) {
  const map = { '书籍': 'info', '课程': 'success', '网站': 'warning', '工具': '' }
  return map[type] || 'info'
}

function formatTime(time) {
  if (!time) return ''
  const date = new Date(time)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}`
}

async function handleRegenerate() {
  const sessionId = diagnosis.value?.sessionId
  if (!sessionId) {
    ElMessage.error('无法获取面试会话ID')
    return
  }

  regenerating.value = true
  try {
    const res = await generateDiagnosis(sessionId)
    if (res.code === 200 && res.data) {
      diagnosis.value = res.data
      ElMessage.success('诊断报告已重新生成')
      // 重新加载竞争力数据
      compLoaded = false
      competition.value = null
    } else {
      ElMessage.error(res.message || '重新生成失败')
    }
  } catch (err) {
    ElMessage.error(err.message || '重新生成失败')
  } finally {
    regenerating.value = false
  }
}

async function handleExportPdf() {
  exporting.value = true
  try {
    await exportDiagnosisPdf(route.params.id)
    ElMessage.success('PDF 导出成功')
  } catch (e) {
    ElMessage.error('导出 PDF 失败: ' + (e.message || '未知错误'))
  } finally {
    exporting.value = false
  }
}
</script>

<style lang="scss" scoped>
.diagnosis-page {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.loading-container, .error-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
}

.loading-icon {
  animation: rotate 1.5s linear infinite;
  color: #409eff;
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.loading-text {
  font-size: 18px;
  color: #303133;
  margin-top: 20px;
}

.loading-sub {
  font-size: 14px;
  color: #909399;
  margin-top: 8px;
}

.error-text {
  font-size: 16px;
  color: #f56c6c;
  margin: 20px 0;
}

// 成绩卡片
.score-header-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 16px;
  padding: 32px;
  margin-bottom: 24px;
  color: #fff;
}

.diagnosis-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
}

.meta-time {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  opacity: 0.9;
}

.meta-actions {
  display: flex;
  gap: 12px;
}

.export-btn {
  background: rgba(103, 194, 58, 0.6) !important;
  border: 1px solid rgba(103, 194, 58, 0.8) !important;
  color: #fff !important;
  backdrop-filter: blur(10px);

  &:hover {
    background: rgba(103, 194, 58, 0.8) !important;
  }
}

.regenerate-btn {
  background: rgba(255, 255, 255, 0.2) !important;
  border: 1px solid rgba(255, 255, 255, 0.3) !important;
  color: #fff !important;
  backdrop-filter: blur(10px);

  &:hover {
    background: rgba(255, 255, 255, 0.3) !important;
  }
}

.score-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.score-left {
  display: flex;
  align-items: center;
  gap: 24px;
}

.score-circle {
  width: 100px;
  height: 100px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(10px);
}

.score-number {
  font-size: 36px;
  font-weight: 700;
  line-height: 1;
}

.score-unit {
  font-size: 14px;
  opacity: 0.8;
}

.score-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.score-level {
  font-size: 20px;
  font-weight: 600;
}

.score-change {
  font-size: 16px;
  padding: 4px 12px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.2);

  &.positive { color: #67c23a; }
  &.negative { color: #f56c6c; }
  &.neutral { color: #909399; }
}

.score-trend {
  text-align: center;
}

.trend-label {
  font-size: 14px;
  opacity: 0.8;
  margin-bottom: 12px;
}

.trend-count {
  font-size: 12px;
  opacity: 0.6;
  font-weight: 400;
}

.trend-chart {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 8px;
  height: 80px;
}

.trend-bar {
  width: 40px;
  border-radius: 8px 8px 0 0;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 8px;
  transition: height 0.5s ease;
}

.trend-value {
  font-size: 12px;
  font-weight: 600;
}

.trend-bar--latest {
  transform: scaleY(1.05);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  position: relative;

  &::after {
    content: '最新';
    position: absolute;
    top: -18px;
    left: 50%;
    transform: translateX(-50%);
    font-size: 10px;
    background: rgba(255, 255, 255, 0.25);
    padding: 1px 6px;
    border-radius: 4px;
    white-space: nowrap;
  }
}

.trend-labels {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 6px;
}

.trend-label-item {
  width: 40px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.7);
  text-align: center;
}

// Tabs
.diagnosis-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 24px;
  }

  :deep(.el-tabs__item) {
    font-size: 16px;
    padding: 0 24px;
    height: 48px;
    line-height: 48px;
  }
}

// 通用卡片
.section-card {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 24px 0;
}

.sub-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 16px 0;
}

// 知识维度
.knowledge-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
}

.knowledge-item {
  padding: 20px;
  background: #f8f9fa;
  border-radius: 12px;
}

.knowledge-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.knowledge-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.knowledge-score {
  font-size: 20px;
  font-weight: 700;

  &.excellent { color: #67c23a; }
  &.good { color: #409eff; }
  &.average { color: #e6a23c; }
  &.poor { color: #f56c6c; }
}

.knowledge-desc {
  font-size: 14px;
  color: #606266;
  margin: 12px 0 0 0;
  line-height: 1.6;
}

// 思维模式
.thinking-header { margin-bottom: 20px; }

.thinking-type-badge {
  display: inline-block;
  padding: 8px 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  border-radius: 24px;
  font-size: 16px;
  font-weight: 600;
}

.thinking-summary {
  font-size: 15px;
  color: #606266;
  line-height: 1.8;
  margin-bottom: 24px;
}

.thinking-details {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  margin-bottom: 24px;
}

.column-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 16px 0;

  &.strengths { color: #67c23a; }
  &.weaknesses { color: #e6a23c; }
}

.thinking-list {
  list-style: none;
  padding: 0;
  margin: 0;

  li {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 0;
    font-size: 14px;
    color: #606266;
    border-bottom: 1px solid #ebeef5;

    &:last-child { border-bottom: none; }
  }
}

.thinking-suggestions {
  background: #f0f9ff;
  padding: 20px;
  border-radius: 12px;
}

.suggestion-list {
  list-style: none;
  padding: 0;
  margin: 0;

  li {
    padding: 8px 0;
    font-size: 14px;
    color: #606266;

    &::before {
      content: '\2022';
      color: #409eff;
      margin-right: 8px;
    }
  }
}

// 错题模式
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px;

  p {
    margin-top: 16px;
    font-size: 16px;
    color: #67c23a;
  }
}

.mistake-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.mistake-card {
  padding: 20px;
  background: #fdf6ec;
  border-radius: 12px;
  border-left: 4px solid #e6a23c;
}

.mistake-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.mistake-pattern {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.mistake-desc {
  font-size: 14px;
  color: #606266;
  margin: 0 0 12px 0;
  line-height: 1.6;
}

.mistake-suggestion {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 14px;
  color: #909399;

  .el-icon { margin-top: 2px; }
}

// 学习计划
.plan-summary {
  font-size: 15px;
  color: #606266;
  line-height: 1.8;
  margin: 0 0 24px 0;
}

.plan-phases {
  display: flex;
  flex-direction: column;
  gap: 20px;
  margin-bottom: 32px;
}

.phase-card {
  padding: 24px;
  background: #f8f9fa;
  border-radius: 12px;
}

.phase-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.phase-number {
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
}

.phase-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.phase-focus {
  font-size: 14px;
  color: #909399;
}

.phase-tasks {
  list-style: none;
  padding: 0;
  margin: 0;

  li {
    padding: 8px 0;
    font-size: 14px;
    color: #606266;

    &::before {
      content: '\2713';
      color: #67c23a;
      margin-right: 8px;
    }
  }
}

.plan-resources {
  border-top: 1px solid #ebeef5;
  padding-top: 24px;
}

.resources-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 16px 0;
}

.resources-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 12px;
}

.resource-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}

.resource-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.resource-desc {
  font-size: 12px;
  color: #909399;
}

// 竞争力对比
.compare-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 32px;
  padding: 32px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  color: #fff;
  margin-bottom: 24px;
}

.score-block {
  text-align: center;
}

.score-block-label {
  display: block;
  font-size: 14px;
  opacity: 0.8;
  margin-bottom: 8px;
}

.score-block-value {
  font-size: 48px;
  font-weight: 700;
  line-height: 1;

  &.excellent { color: #67c23a; }
  &.good { color: #86efac; }
  &.average { color: #fde68a; }
  &.poor { color: #fca5a5; }
  &.target { color: #fff; }
}

.score-block-unit {
  font-size: 16px;
  opacity: 0.7;
  margin-left: 2px;
}

.vs-divider {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.vs-text {
  font-size: 20px;
  font-weight: 700;
  opacity: 0.8;
}

.vs-gap {
  font-size: 14px;
  opacity: 0.9;

  &.achieved { color: #67c23a; }
}

.tier-desc {
  text-align: center;
  font-size: 15px;
  color: #606266;
  padding: 12px 20px;
  background: #f0f9ff;
  border-radius: 8px;
  margin-bottom: 20px;
  line-height: 1.6;
}

.rank-info {
  text-align: center;
  font-size: 15px;
  color: #374151;
  padding: 16px;
  background: #f8f9fa;
  border-radius: 8px;
  margin-bottom: 24px;

  strong {
    color: #4F46E5;
    font-size: 18px;
  }
}

.dimension-compare {
  margin-bottom: 24px;
}

.dim-compare-item {
  padding: 16px 0;
  border-bottom: 1px solid #F3F4F6;

  &:last-child { border-bottom: none; }
}

.dim-compare-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.dim-compare-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.dim-urgency {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 12px;
  font-weight: 500;

  &.high { background: #FEF2F2; color: #DC2626; }
  &.medium { background: #FEFCE8; color: #D97706; }
  &.low { background: #F0FDF4; color: #16A34A; }
}

.dim-compare-bars {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.dim-compare-bar-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.bar-label-sm {
  font-size: 12px;
  color: #6B7280;
  width: 32px;
  flex-shrink: 0;
}

.bar-track-sm {
  flex: 1;
  height: 20px;
  background: #F3F4F6;
  border-radius: 4px;
  overflow: hidden;
}

.bar-fill-sm {
  height: 100%;
  border-radius: 4px;
  transition: width 0.6s ease;

  &.user { background: linear-gradient(90deg, #818CF8, #4F46E5); }
  &.target { background: linear-gradient(90deg, #A5B4FC, #6366F1); }
}

.bar-val-sm {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
  width: 32px;
  text-align: right;
}

.weak-adv-section {
  margin-bottom: 20px;
}

.tag-list-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.empty-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

@media (max-width: 768px) {
  .diagnosis-page { padding: 16px; }

  .score-content { flex-direction: column; gap: 24px; }
  .thinking-details { grid-template-columns: 1fr; }
  .knowledge-grid { grid-template-columns: 1fr; }

  .compare-header {
    flex-direction: column;
    gap: 16px;
    padding: 24px;
  }

  .score-block-value { font-size: 36px; }
}
</style>
