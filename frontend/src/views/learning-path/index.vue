<template>
  <div class="page-container learning-path-page">
    <!-- Loading 状态 -->
    <div v-if="loading" class="loading-state">
      <el-skeleton :rows="8" animated />
    </div>

    <!-- 空状态（无计划） -->
    <div v-else-if="!pathData.hasPlan" class="empty-state">
      <el-empty description="">
        <template #image>
          <div class="empty-icon">📚</div>
        </template>
        <template #description>
          <h3>还没有学习计划</h3>
          <p>完成一次模拟面试，AI 将为你生成个性化学习路径！</p>
        </template>
        <el-button type="primary" size="large" @click="$router.push('/interview/config')">
          开始面试
        </el-button>
      </el-empty>
    </div>

    <!-- 正常内容 -->
    <template v-else>
      <!-- 顶部概览 -->
      <div class="overview-section">
        <div class="overview-content">
          <h1 class="page-title">
            <el-icon><TrendCharts /></el-icon>
            我的学习路径
          </h1>
          <div class="overview-meta">
            <div class="meta-item">
              <span class="meta-label">目标岗位</span>
              <span class="meta-value">{{ pathData.targetPosition || '未设置' }}</span>
            </div>
            <div class="meta-divider"></div>
            <div class="meta-item">
              <span class="meta-label">总体进度</span>
              <span class="meta-value progress">{{ pathData.completedTasks }}/{{ pathData.totalTasks }} ({{ pathData.progressPercent }}%)</span>
            </div>
            <div class="meta-divider"></div>
            <div class="meta-item" v-if="pathData.currentFocus">
              <span class="meta-label">当前重点</span>
              <span class="meta-value focus">{{ pathData.currentFocus }}</span>
            </div>
          </div>
        </div>
        <div class="overview-actions">
          <div class="last-updated" v-if="pathData.lastUpdated">
            更新于: {{ formatTime(pathData.lastUpdated) }}
          </div>
          <el-button type="primary" :loading="refreshing" @click="handleRefresh">
            <el-icon><Refresh /></el-icon>
            刷新计划
          </el-button>
        </div>
      </div>

      <!-- 总体进度条 -->
      <div class="overall-progress">
        <el-progress
          :percentage="pathData.progressPercent"
          :stroke-width="12"
          :format="(p) => `${p}%`"
          color="#4F46E5"
        />
      </div>

      <!-- 学习阶段列表 -->
      <div class="phases-section">
        <div v-for="phase in pathData.phases" :key="phase.phaseIndex" class="phase-card">
          <div class="phase-header">
            <div class="phase-title-group">
              <span class="phase-badge">{{ phase.phaseIndex + 1 }}</span>
              <h3 class="phase-title">{{ phase.phaseName }}</h3>
            </div>
            <div class="phase-progress">
              <el-progress
                :percentage="phase.progressPercent"
                :stroke-width="6"
                :show-text="false"
                style="width: 100px"
                :color="phase.progressPercent === 100 ? '#16A34A' : '#4F46E5'"
              />
              <span class="phase-progress-text">{{ phase.completedCount }}/{{ phase.totalCount }}</span>
            </div>
          </div>

          <div class="phase-focus" v-if="phase.focus">
            <el-icon><Aim /></el-icon>
            <span>学习重点: {{ phase.focus }}</span>
          </div>

          <div class="task-list">
            <div
              v-for="task in phase.tasks"
              :key="task.taskIndex"
              class="task-item"
              :class="{ completed: task.completed }"
            >
              <el-checkbox
                :model-value="task.completed"
                @change="(val) => handleTaskToggle(phase.phaseIndex, task.taskIndex, val)"
                :disabled="taskToggling"
              >
                <span class="task-text" :class="{ 'line-through': task.completed }">
                  {{ task.text }}
                </span>
              </el-checkbox>
              <el-button
                v-if="!task.completed"
                text
                type="primary"
                size="small"
                class="practice-btn"
                @click="goPractice(task.text)"
              >
                <el-icon><VideoPlay /></el-icon>
                去练习
              </el-button>
              <span v-if="task.completed && task.completedAt" class="completed-time">
                {{ formatTime(task.completedAt) }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- 推荐资源 -->
      <div class="resources-section" v-if="pathData.resources && pathData.resources.length > 0">
        <div class="section-card">
          <div class="section-header">
            <h3 class="section-title">
              <el-icon><Collection /></el-icon>
              推荐资源
            </h3>
          </div>
          <div class="resource-list">
            <div v-for="(resource, index) in pathData.resources" :key="index" class="resource-item">
              <div class="resource-icon">
                <el-icon v-if="resource.type === '书籍'"><Reading /></el-icon>
                <el-icon v-else-if="resource.type === '课程'"><Monitor /></el-icon>
                <el-icon v-else><Link /></el-icon>
              </div>
              <div class="resource-info">
                <span class="resource-type">{{ resource.type || '资源' }}</span>
                <span class="resource-name">{{ resource.name || resource.title }}</span>
                <span class="resource-desc" v-if="resource.description">{{ resource.description }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { TrendCharts, Refresh, Aim, VideoPlay, Collection, Reading, Monitor, Link } from '@element-plus/icons-vue'
import { getLearningPath, refreshLearningPath, markTaskComplete, unmarkTaskComplete } from '@/api/learningPath'

const router = useRouter()

const loading = ref(true)
const refreshing = ref(false)
const taskToggling = ref(false)
const pathData = ref({
  hasPlan: false,
  targetPosition: '',
  totalTasks: 0,
  completedTasks: 0,
  progressPercent: 0,
  currentFocus: '',
  phases: [],
  resources: [],
  lastUpdated: null
})

onMounted(async () => {
  await loadLearningPath()
})

async function loadLearningPath() {
  loading.value = true
  try {
    const res = await getLearningPath()
    if (res.code === 200 && res.data) {
      // 如果没有学习计划，尝试自动从诊断报告生成
      if (!res.data.hasPlan) {
        const refreshRes = await refreshLearningPath()
        if (refreshRes.code === 200 && refreshRes.data) {
          pathData.value = refreshRes.data
          return
        }
      }
      pathData.value = res.data
    }
  } catch (e) {
    console.error('加载学习路径失败', e)
  } finally {
    loading.value = false
  }
}

async function handleRefresh() {
  refreshing.value = true
  try {
    const res = await refreshLearningPath()
    if (res.code === 200 && res.data) {
      pathData.value = res.data
      ElMessage.success('学习计划已更新')
    }
  } catch (e) {
    ElMessage.error('刷新失败，请稍后重试')
  } finally {
    refreshing.value = false
  }
}

async function handleTaskToggle(phaseIndex, taskIndex, completed) {
  taskToggling.value = true

  // 乐观更新 UI
  const phase = pathData.value.phases.find(p => p.phaseIndex === phaseIndex)
  if (phase) {
    const task = phase.tasks.find(t => t.taskIndex === taskIndex)
    if (task) {
      task.completed = completed
    }
    // 更新阶段进度
    phase.completedCount = phase.tasks.filter(t => t.completed).length
    phase.progressPercent = phase.totalCount > 0 ? Math.round(phase.completedCount * 100 / phase.totalCount) : 0
  }

  // 更新总体进度
  const allTasks = pathData.value.phases.flatMap(p => p.tasks)
  pathData.value.completedTasks = allTasks.filter(t => t.completed).length
  pathData.value.progressPercent = pathData.value.totalTasks > 0
    ? Math.round(pathData.value.completedTasks * 100 / pathData.value.totalTasks)
    : 0

  try {
    if (completed) {
      await markTaskComplete(phaseIndex, taskIndex)
    } else {
      await unmarkTaskComplete(phaseIndex, taskIndex)
    }
  } catch (e) {
    // 回滚乐观更新
    if (phase) {
      const task = phase.tasks.find(t => t.taskIndex === taskIndex)
      if (task) {
        task.completed = !completed
      }
      phase.completedCount = phase.tasks.filter(t => t.completed).length
      phase.progressPercent = phase.totalCount > 0 ? Math.round(phase.completedCount * 100 / phase.totalCount) : 0
    }
    const all = pathData.value.phases.flatMap(p => p.tasks)
    pathData.value.completedTasks = all.filter(t => t.completed).length
    pathData.value.progressPercent = pathData.value.totalTasks > 0
      ? Math.round(pathData.value.completedTasks * 100 / pathData.value.totalTasks)
      : 0

    ElMessage.error('操作失败，请稍后重试')
  } finally {
    taskToggling.value = false
  }
}

function goPractice(taskText) {
  // 根据任务文本尝试提取关键词进行题目筛选
  router.push({ path: '/questions/list', query: { keyword: taskText } })
}

function formatTime(time) {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now - date

  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  if (diff < 604800000) return `${Math.floor(diff / 86400000)}天前`

  return `${date.getMonth() + 1}/${date.getDate()}`
}
</script>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

// Loading 状态
.loading-state {
  background: #fff;
  border-radius: 12px;
  padding: 32px;
}

// 空状态
.empty-state {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
  background: #fff;
  border-radius: 12px;

  .empty-icon {
    font-size: 64px;
    margin-bottom: 16px;
  }

  h3 {
    font-size: 20px;
    color: $text-primary;
    margin: 0 0 8px 0;
  }

  p {
    font-size: 14px;
    color: $text-muted;
    margin: 0 0 24px 0;
  }
}

// 顶部概览
.overview-section {
  background: linear-gradient(135deg, #4F46E5 0%, #7C3AED 100%);
  border-radius: 16px;
  padding: 32px;
  margin-bottom: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #fff;
}

.page-title {
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 16px 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.overview-meta {
  display: flex;
  align-items: center;
  gap: 16px;
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.meta-label {
  font-size: 12px;
  opacity: 0.8;
}

.meta-value {
  font-size: 16px;
  font-weight: 600;

  &.progress {
    color: #C7D2FE;
  }

  &.focus {
    max-width: 200px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.meta-divider {
  width: 1px;
  height: 32px;
  background: rgba(255, 255, 255, 0.3);
}

.overview-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.last-updated {
  font-size: 12px;
  opacity: 0.8;
}

.overview-actions .el-button {
  background: rgba(255, 255, 255, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.3);

  &:hover {
    background: rgba(255, 255, 255, 0.3);
  }
}

// 总体进度条
.overall-progress {
  background: #fff;
  border: 1px solid $border-color;
  border-radius: 12px;
  padding: 24px;
  padding: 20px 24px;
  margin-bottom: 24px;
}

// 阶段卡片
.phases-section {
  display: flex;
  flex-direction: column;
  gap: 20px;
  margin-bottom: 24px;
}

.phase-card {
  background: #fff;
  border: 1px solid $border-color;
  border-radius: 12px;
  padding: 24px;
  transition: all 0.2s ease;

  &:hover {
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  }
}

.phase-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.phase-title-group {
  display: flex;
  align-items: center;
  gap: 12px;
}

.phase-badge {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #4F46E5;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
  flex-shrink: 0;
}

.phase-title {
  font-size: 18px;
  font-weight: 600;
  color: $text-primary;
  margin: 0;
}

.phase-progress {
  display: flex;
  align-items: center;
  gap: 12px;
}

.phase-progress-text {
  font-size: 14px;
  color: #6B7280;
  white-space: nowrap;
}

.phase-focus {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #7C3AED;
  background: #F5F3FF;
  padding: 8px 12px;
  border-radius: 8px;
  margin-bottom: 16px;
}

// 任务列表
.task-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.task-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-radius: 8px;
  transition: background 0.2s ease;

  &:hover {
    background: #F9FAFB;
  }

  &.completed {
    opacity: 0.7;
  }

  .el-checkbox {
    flex: 1;
  }
}

.task-text {
  font-size: 14px;
  color: #374151;

  &.line-through {
    text-decoration: line-through;
    color: #9CA3AF;
  }
}

.practice-btn {
  font-size: 13px;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.task-item:hover .practice-btn {
  opacity: 1;
}

.completed-time {
  font-size: 12px;
  color: #9CA3AF;
}

// 推荐资源
.resources-section {
  margin-bottom: 24px;
}

.section-card {
  background: #fff;
  border: 1px solid $border-color;
  border-radius: 12px;
  padding: 24px;
}

.section-header {
  margin-bottom: 16px;
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

.resource-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.resource-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px;
  background: #F9FAFB;
  border-radius: 8px;
}

.resource-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: #EDE9FE;
  color: #7C3AED;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 18px;
}

.resource-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.resource-type {
  font-size: 12px;
  color: #7C3AED;
  font-weight: 500;
}

.resource-name {
  font-size: 14px;
  font-weight: 500;
  color: $text-primary;
}

.resource-desc {
  font-size: 13px;
  color: #6B7280;
}

@media (max-width: 768px) {
  .learning-path-page {
    padding: 16px;
  }

  .overview-section {
    flex-direction: column;
    gap: 16px;
    text-align: center;
  }

  .overview-meta {
    flex-direction: column;
  }

  .meta-divider {
    width: 40px;
    height: 1px;
  }

  .overview-actions {
    align-items: center;
  }
}
</style>
