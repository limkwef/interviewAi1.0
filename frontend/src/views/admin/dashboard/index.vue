<template>
  <div class="admin-dashboard">
    <el-row :gutter="20" class="stats-row">
      <el-col :span="8">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon user-icon">
              <el-icon><User /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ statistics.totalUsers || 0 }}</div>
              <div class="stat-label">用户总数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon question-icon">
              <el-icon><Document /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ statistics.totalQuestions || 0 }}</div>
              <div class="stat-label">题目总数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon interview-icon">
              <el-icon><ChatDotRound /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ statistics.totalInterviews || 0 }}</div>
              <div class="stat-label">面试总数</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <el-col :span="16">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <span>用户增长趋势（最近7天）</span>
            </div>
          </template>
          <div ref="chartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>快捷操作</span>
            </div>
          </template>
          <div class="quick-actions">
            <el-button type="primary" @click="$router.push('/admin/users')">
              <el-icon><User /></el-icon>
              用户管理
            </el-button>
            <el-button type="success" @click="$router.push('/admin/questions')">
              <el-icon><Document /></el-icon>
              题库管理
            </el-button>
            <el-button type="info" @click="$router.push('/admin/logs')">
              <el-icon><Document /></el-icon>
              操作日志
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, onBeforeUnmount } from 'vue'
import { User, Document, ChatDotRound } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getStatistics } from '@/api/admin'
import { ElMessage } from 'element-plus'

const statistics = ref({})
const chartRef = ref(null)
let chartInstance = null

function initChart(trend) {
  if (!chartRef.value) return
  chartInstance = echarts.init(chartRef.value)

  const option = {
    tooltip: {
      trigger: 'axis',
      formatter: '{b}<br/>新增用户：{c} 人'
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '10%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: trend.dates.map(d => d.substring(5)),
      axisLine: { lineStyle: { color: '#ddd' } },
      axisLabel: { color: '#666' }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#666' },
      splitLine: { lineStyle: { color: '#f0f0f0' } }
    },
    series: [
      {
        name: '新增用户',
        type: 'line',
        smooth: true,
        data: trend.counts,
        symbol: 'circle',
        symbolSize: 8,
        lineStyle: {
          color: '#667eea',
          width: 3
        },
        itemStyle: {
          color: '#667eea',
          borderColor: '#fff',
          borderWidth: 2
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(102,126,234,0.35)' },
            { offset: 1, color: 'rgba(102,126,234,0.05)' }
          ])
        }
      }
    ]
  }

  chartInstance.setOption(option)
}

function handleResize() {
  chartInstance?.resize()
}

onMounted(async () => {
  try {
    const res = await getStatistics()
    statistics.value = res.data
    await nextTick()
    if (res.data.userGrowthTrend) {
      initChart(res.data.userGrowthTrend)
    }
  } catch (err) {
    ElMessage.error('获取统计数据失败')
  }
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
})
</script>

<style lang="scss" scoped>
.admin-dashboard {
  padding: 20px;
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  .stat-content {
    display: flex;
    align-items: center;
    gap: 20px;
  }

  .stat-icon {
    width: 60px;
    height: 60px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;

    .el-icon {
      font-size: 30px;
      color: #fff;
    }
  }

  .user-icon {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  }

  .question-icon {
    background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  }

  .interview-icon {
    background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
  }

  .stat-info {
    flex: 1;

    .stat-value {
      font-size: 28px;
      font-weight: bold;
      color: #333;
      margin-bottom: 4px;
    }

    .stat-label {
      font-size: 14px;
      color: #999;
    }
  }
}

.chart-card {
  .card-header {
    font-weight: bold;
    font-size: 16px;
  }

  .chart-container {
    width: 100%;
    height: 320px;
  }
}

.quick-actions {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 8px 0;

  .el-button {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 16px 24px;
    font-size: 15px;
    width: 100%;
  }
}
</style>
