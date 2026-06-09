<template>
  <div class="page-container dashboard-page">
    <!-- 统计卡片 -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon" style="background: #e8f5e9;">
          <el-icon :size="24" color="#4caf50"><ChatDotRound /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ stats.totalInterviews }}</span>
          <span class="stat-label">面试次数</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: #e3f2fd;">
          <el-icon :size="24" color="#2196f3"><TrendCharts /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ stats.avgScore }}</span>
          <span class="stat-label">平均分数</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: #fff3e0;">
          <el-icon :size="24" color="#ff9800"><Trophy /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ stats.highestScore }}</span>
          <span class="stat-label">最高分数</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: #fce4ec;">
          <el-icon :size="24" color="#e91e63"><Document /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ stats.totalQuestions }}</span>
          <span class="stat-label">已练题目</span>
        </div>
      </div>
    </div>

    <div class="main-content">
      <!-- 左侧主要内容 -->
      <div class="left-content">
        <!-- 快速开始 -->
        <div class="section-card">
          <div class="section-header">
            <h3 class="section-title">
              <el-icon><ChatDotRound /></el-icon>
              快速开始面试
            </h3>
          </div>
          <div class="quick-start-grid">
            <div class="quick-start-item" v-for="item in quickStartItems" :key="item.position"
                 @click="startInterview(item.position, item.round)">
              <div class="quick-icon" :style="{ background: item.color }">
                <el-icon :size="20"><component :is="item.icon" /></el-icon>
              </div>
              <div class="quick-info">
                <span class="quick-name">{{ item.name }}</span>
                <span class="quick-desc">{{ item.description }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 分数趋势图 -->
        <div class="section-card">
          <div class="section-header">
            <h3 class="section-title">
              <el-icon><TrendCharts /></el-icon>
              分数趋势
            </h3>
            <el-select v-model="scoreTrendPosition" placeholder="全部岗位" clearable size="small"
                       @change="onScoreTrendFilterChange" style="width: 130px;">
              <el-option v-for="p in positionOptions" :key="p.value" :label="p.label" :value="p.value" />
            </el-select>
          </div>
          <div v-if="scoreTrend.length === 0" class="empty-chart">
            <p>暂无趋势数据</p>
            <p class="empty-hint">完成面试后将展示分数变化趋势</p>
          </div>
          <div v-else ref="scoreChartRef" class="chart-container" style="height: 200px;"></div>
        </div>

        <!-- 能力维度雷达图 -->
        <div class="section-card">
          <div class="section-header">
            <h3 class="section-title">
              <el-icon><Aim /></el-icon>
              能力维度
            </h3>
            <el-select v-model="knowledgePosition" placeholder="全部岗位" clearable size="small"
                       @change="onKnowledgeFilterChange" style="width: 130px;">
              <el-option v-for="p in positionOptions" :key="p.value" :label="p.label" :value="p.value" />
            </el-select>
          </div>
          <div v-if="!hasKnowledgeData" class="empty-chart">
            <p>暂无能力数据</p>
            <p class="empty-hint">完成面试后将展示能力维度分析</p>
          </div>
          <div v-else ref="radarChartRef" class="chart-container" style="height: 250px;"></div>
        </div>
      </div>

      <!-- 右侧面板 -->
      <div class="right-panel">
        <!-- 推荐练习 -->
        <div class="section-card">
          <div class="section-header">
            <h3 class="section-title">
              <el-icon><Document /></el-icon>
              推荐练习
            </h3>
          </div>
          <div class="recommend-list">
            <div v-for="(item, index) in recommendQuestions" :key="index" class="recommend-item"
                 @click="$router.push(`/questions/detail/${item.id}`)">
              <div class="recommend-index">{{ index + 1 }}</div>
              <div class="recommend-info">
                <span class="recommend-title">{{ item.title }}</span>
                <div class="recommend-tags">
                  <el-tag size="small" :type="getDifficultyType(item.difficulty)">
                    {{ getDifficultyLabel(item.difficulty) }}
                  </el-tag>
                  <span class="recommend-category">{{ item.category }}</span>
                </div>
              </div>
            </div>
            <div v-if="recommendQuestions.length === 0" class="empty-recommend">
              <p>暂无推荐题目</p>
            </div>
          </div>
        </div>

        <!-- 错题复习提醒 -->
        <div class="section-card" v-if="mistakeCount > 0">
          <div class="section-header">
            <h3 class="section-title">
              <el-icon><Warning /></el-icon>
              错题复习
            </h3>
          </div>
          <div class="mistake-reminder">
            <p class="mistake-count">{{ mistakeCount }} 道错题待复习</p>
            <p class="mistake-tip">定期复习错题能有效提升面试通过率</p>
            <el-button type="warning" plain @click="$router.push('/questions/mistakes')" style="width: 100%;">
              开始复习
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useUserStore } from '@/stores'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatDotRound, TrendCharts, Trophy, Document, Aim, Warning } from '@element-plus/icons-vue'
import { getDashboardData, getDashboardStats, getScoreTrend, getKnowledgeOverview } from '@/api/common'
import * as echarts from 'echarts'
import { positionMap, normalizePosition } from '@/utils/constants'

const router = useRouter()
const currentPosition = ref('')

const stats = ref({
  totalInterviews: 0,
  avgScore: 0,
  highestScore: 0,
  totalQuestions: 0
})

const recommendQuestions = ref([])
const mistakeCount = ref(0)
const scoreTrend = ref([])
const knowledgeOverview = ref({})
const scoreChartRef = ref(null)
const radarChartRef = ref(null)
let scoreChartInstance = null
let radarChartInstance = null

const positionOptions = Object.entries(positionMap).map(([value, label]) => ({ label, value }))
const scoreTrendPosition = ref('java_backend')
const knowledgePosition = ref('java_backend')

const hasKnowledgeData = computed(() => {
  const keys = Object.keys(knowledgeOverview.value)
  if (keys.length === 0) return false
  return keys.some(key => knowledgeOverview.value[key] > 0)
})

const quickStartItems = [
  { name: 'Java后端技术面', description: '技术能力深度考察', position: 'java_backend', round: 'technical', color: '#e8f5e9', icon: 'ChatDotRound' },
  { name: '前端开发技术面', description: '前端技术栈考察', position: 'frontend', round: 'technical', color: '#e3f2fd', icon: 'ChatDotRound' },
  { name: 'HR面试', description: '软素质与沟通能力', position: 'java_backend', round: 'hr', color: '#fff3e0', icon: 'ChatDotRound' },
  { name: '综合面试', description: '全面能力评估', position: 'java_backend', round: 'comprehensive', color: '#fce4ec', icon: 'ChatDotRound' }
]

onMounted(() => {
  const userStore = useUserStore()
  if (userStore.userInfo?.targetPosition) {
    currentPosition.value = normalizePosition(userStore.userInfo.targetPosition)
    scoreTrendPosition.value = currentPosition.value
    knowledgePosition.value = currentPosition.value
  }
  loadDashboardData()
})

onUnmounted(() => {
  if (scoreChartInstance) {
    scoreChartInstance.dispose()
    scoreChartInstance = null
  }
  if (radarChartInstance) {
    radarChartInstance.dispose()
    radarChartInstance = null
  }
})

async function loadDashboardData() {
  try {
    // 并行获取统计数据（整体）和其他数据（按岗位筛选）
    const [statsRes, dataRes] = await Promise.all([
      getDashboardStats(),
      getDashboardData(currentPosition.value ? { position: currentPosition.value } : {})
    ])

    // 统计数据使用整体数据（不按岗位筛选）
    if (statsRes.code === 200 && statsRes.data) {
      stats.value = { ...stats.value, ...statsRes.data }
    }

    // 其他数据按岗位筛选
    if (dataRes.code === 200 && dataRes.data) {
      scoreTrend.value = dataRes.data.scoreTrend || []
      knowledgeOverview.value = dataRes.data.knowledgeOverview || {}
      recommendQuestions.value = dataRes.data.recommendations || dataRes.data.recommendQuestions || []
      mistakeCount.value = dataRes.data.stats?.wrongCount || dataRes.data.mistakeCount || 0

      // 初始化图表
      nextTick(() => {
        initScoreChart()
        initRadarChart()
      })
    }
  } catch (error) {
    console.error('获取仪表盘数据失败:', error)
    ElMessage.error('加载仪表盘数据失败，请刷新页面重试')
  }
}

function initScoreChart() {
  if (!scoreChartRef.value || scoreTrend.value.length === 0) return
  if (!scoreChartInstance) {
    scoreChartInstance = echarts.init(scoreChartRef.value)
  }
  scoreChartInstance.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: scoreTrend.value.map((_, i) => `第${i + 1}次`)
    },
    yAxis: { type: 'value', min: 0, max: 100 },
    series: [{
      type: 'line',
      data: scoreTrend.value.map(item => item.totalScore),
      smooth: true,
      areaStyle: { opacity: 0.1 }
    }]
  }, true)
}

function initRadarChart() {
  if (!radarChartRef.value || !hasKnowledgeData.value) return
  if (!radarChartInstance) {
    radarChartInstance = echarts.init(radarChartRef.value)
  }
  const indicators = Object.keys(knowledgeOverview.value).map(key => ({
    name: key,
    max: 100
  }))
  radarChartInstance.setOption({
    tooltip: {},
    radar: { indicator: indicators },
    series: [{
      type: 'radar',
      data: [{ value: Object.values(knowledgeOverview.value), name: '能力值' }]
    }]
  }, true)
}

function startInterview(position, round) {
  router.push({
    path: '/interview/config',
    query: { position, round }
  })
}

function getDifficultyType(difficulty) {
  const map = { easy: 'success', medium: 'warning', hard: 'danger' }
  return map[difficulty] || 'info'
}

function getDifficultyLabel(difficulty) {
  const map = { easy: '简单', medium: '中等', hard: '困难' }
  return map[difficulty] || difficulty
}

async function handlePositionChange() {
  await loadDashboardData()
}

async function onScoreTrendFilterChange(val) {
  try {
    const res = await getScoreTrend(val ? { position: val } : {})
    if (res.code === 200) {
      scoreTrend.value = res.data || []
      if (scoreTrend.value.length === 0 && scoreChartInstance) {
        scoreChartInstance.dispose()
        scoreChartInstance = null
      } else {
        nextTick(() => initScoreChart())
      }
    }
  } catch (e) {
    console.error('获取分数趋势失败:', e)
    ElMessage.error('加载分数趋势失败，请重试')
  }
}

async function onKnowledgeFilterChange(val) {
  try {
    const res = await getKnowledgeOverview(val ? { position: val } : {})
    if (res.code === 200) {
      knowledgeOverview.value = res.data || {}
      const hasData = Object.keys(knowledgeOverview.value).some(key => knowledgeOverview.value[key] > 0)
      if (!hasData && radarChartInstance) {
        radarChartInstance.dispose()
        radarChartInstance = null
      } else {
        nextTick(() => initRadarChart())
      }
    }
  } catch (e) {
    console.error('获取能力维度失败:', e)
    ElMessage.error('加载能力维度数据失败，请重试')
  }
}
</script>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.dashboard-page {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.filter-bar {
  margin-bottom: 16px;
  display: flex;
  gap: 12px;
  align-items: center;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 24px;
}

.stat-card {
  background: #fff;
  border: 1px solid $border-color;
  border-radius: 12px;
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: $text-primary;
}

.stat-label {
  font-size: 14px;
  color: $text-muted;
}

.main-content {
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: 24px;
}

.right-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.section-card {
  background: #fff;
  border: 1px solid $border-color;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.section-header {
  margin-bottom: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: $text-primary;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.quick-start-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.quick-start-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: $bg-surface;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: #e8f5e9;
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  }
}

.quick-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.quick-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.quick-name {
  font-size: 15px;
  font-weight: 600;
  color: $text-primary;
}

.quick-desc {
  font-size: 12px;
  color: $text-muted;
}

.recommend-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.recommend-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: $bg-surface;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: #e8f5e9;
  }
}

.recommend-index {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #4caf50;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
}

.recommend-info {
  flex: 1;
  min-width: 0;
}

.recommend-title {
  font-size: 14px;
  font-weight: 500;
  color: $text-primary;
  display: block;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.recommend-tags {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}

.recommend-category {
  font-size: 12px;
  color: $text-muted;
}

.empty-recommend {
  text-align: center;
  padding: 24px;
  color: $text-muted;
}

.mistake-reminder {
  text-align: center;
}

.mistake-count {
  font-size: 20px;
  font-weight: 600;
  color: #e6a23c;
  margin: 0 0 8px 0;
}

.mistake-tip {
  font-size: 14px;
  color: $text-muted;
  margin: 0 0 16px 0;
}

.chart-container {
  width: 100%;
}

.empty-chart {
  height: 250px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: $bg-surface;
  border-radius: 8px;
  color: $text-muted;

  p {
    margin: 4px 0;
    font-size: 14px;
  }

  .empty-hint {
    font-size: 12px;
    opacity: 0.7;
  }
}

.recent-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.recent-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  background: $bg-surface;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  &:hover { background: #f0f0f0; }
}

.recent-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.recent-position {
  font-size: 13px;
  font-weight: 500;
  color: $text-primary;
}

.recent-time {
  font-size: 11px;
  color: $text-muted;
}

.recent-score {
  text-align: right;
}

.score-value {
  font-size: 18px;
  font-weight: 600;
  color: $accent-color;
}

.score-label {
  font-size: 12px;
  color: $text-muted;
  margin-left: 2px;
}

@media (max-width: 1000px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .main-content {
    grid-template-columns: 1fr;
  }

  .quick-start-grid {
    grid-template-columns: 1fr;
  }
}
</style>
