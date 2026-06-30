<template>
  <div class="page-container learning-path-page">
    <!-- Loading 状态 -->
    <div v-if="loading" class="loading-state">
      <el-skeleton :rows="8" animated />
    </div>

    <!-- 空状态（无计划） -->
    <div v-else-if="!pathData.hasPlan" class="empty-state">
      <div class="empty-illustration">
        <div class="empty-road">
          <div class="road-line"></div>
          <div class="road-dot dot-1"></div>
          <div class="road-dot dot-2"></div>
          <div class="road-dot dot-3"></div>
        </div>
        <div class="empty-icon-wrap">
          <el-icon :size="48"><TrendCharts /></el-icon>
        </div>
      </div>
      <h3>还没有学习计划</h3>
      <p>完成一次模拟面试，AI 将为你生成个性化学习路径</p>
      <el-button type="primary" size="large" @click="$router.push('/interview/config')">
        <el-icon><VideoPlay /></el-icon>
        开始面试
      </el-button>
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
              <span class="meta-value progress">{{ pathData.completedTasks }}/{{ pathData.totalTasks }}</span>
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
          <el-button :loading="refreshing" @click="handleRefresh">
            <el-icon><Refresh /></el-icon>
            刷新计划
          </el-button>
        </div>
      </div>

      <!-- 总体进度卡片 -->
      <div class="progress-card">
        <div class="progress-info">
          <span class="progress-label">学习进度</span>
          <span class="progress-numbers">{{ pathData.completedTasks }} / {{ pathData.totalTasks }} 个任务</span>
        </div>
        <el-progress
          :percentage="pathData.progressPercent"
          :stroke-width="10"
          :format="(p) => `${p}%`"
          color="#4F46E5"
        />
        <div class="progress-hint" v-if="pathData.progressPercent === 100">
          恭喜！所有任务已完成
        </div>
        <div class="progress-hint" v-else-if="pathData.progressPercent >= 60">
          加油！已完成过半
        </div>
      </div>

      <!-- 学习路线图 -->
      <div class="roadmap-section">
        <div
          v-for="(phase, idx) in pathData.phases"
          :key="phase.phaseIndex"
          class="phase-block"
          :class="{
            'phase-completed': phase.progressPercent === 100,
            'phase-current': isCurrentPhase(phase),
            'phase-locked': isLockedPhase(phase, idx)
          }"
        >
          <!-- 连接线 -->
          <div class="phase-connector" v-if="idx > 0">
            <div class="connector-line" :class="{ 'connector-done': isPrevPhaseCompleted(idx) }"></div>
          </div>

          <!-- 阶段节点 -->
          <div class="phase-node">
            <div class="phase-badge" :class="{ 'badge-done': phase.progressPercent === 100, 'badge-current': isCurrentPhase(phase) }">
              <el-icon v-if="phase.progressPercent === 100" :size="20"><Check /></el-icon>
              <span v-else>{{ phase.phaseIndex + 1 }}</span>
            </div>

            <div class="phase-content">
              <div class="phase-header">
                <h3 class="phase-title">{{ phase.phaseName }}</h3>
                <div class="phase-progress-tag">
                  <el-progress
                    :percentage="phase.progressPercent"
                    :stroke-width="4"
                    :show-text="false"
                    style="width: 80px"
                    :color="phase.progressPercent === 100 ? '#16A34A' : '#4F46E5'"
                  />
                  <span class="phase-count">{{ phase.completedCount }}/{{ phase.totalCount }}</span>
                </div>
              </div>

              <div class="phase-focus" v-if="phase.focus">
                <el-icon><Aim /></el-icon>
                <span>{{ phase.focus }}</span>
              </div>

              <!-- 任务列表 -->
              <div class="task-list">
                <div
                  v-for="task in phase.tasks"
                  :key="task.taskIndex"
                  class="task-item"
                  :class="{ completed: task.completed, 'just-completed': task._animating }"
                >
                  <el-checkbox
                    :model-value="task.completed"
                    @change="(val) => handleTaskToggle(phase.phaseIndex, task.taskIndex, val)"
                    :disabled="taskToggling"
                  >
                    <span class="task-text">{{ task.text }}</span>
                  </el-checkbox>
                  <div class="task-actions">
                    <el-button
                      v-if="!task.completed"
                      text
                      type="primary"
                      size="small"
                      class="practice-btn"
                      @click="goPractice(task.text)"
                    >
                      <el-icon><VideoPlay /></el-icon>
                      练习
                    </el-button>
                    <span v-if="task.completed && task.completedAt" class="completed-time">
                      {{ formatTime(task.completedAt) }}
                    </span>
                  </div>
                </div>
              </div>

              <!-- 阶段完成提示 -->
              <div class="phase-complete-banner" v-if="phase.progressPercent === 100">
                <el-icon><CircleCheck /></el-icon>
                <span>阶段完成</span>
              </div>
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
            <div v-for="(resource, index) in pathData.resources" :key="index" class="resource-item" @click="openResource(resource)">
              <div class="resource-icon">
                <el-icon v-if="resource.type === '书籍'"><Reading /></el-icon>
                <el-icon v-else-if="resource.type === '课程'"><Monitor /></el-icon>
                <el-icon v-else><Link /></el-icon>
              </div>
              <div class="resource-info">
                <span class="resource-type">{{ resource.type || '资源' }}</span>
                <span class="resource-name">{{ resource.title || resource.name }}</span>
                <span class="resource-desc" v-if="resource.description">{{ resource.description }}</span>
              </div>
              <el-icon class="resource-arrow"><Right /></el-icon>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- 完成庆祝动画 -->
    <Transition name="celebrate">
      <div v-if="showCelebration" class="celebration-overlay" @click="showCelebration = false">
        <div class="celebration-content">
          <div class="celebration-icon">&#127881;</div>
          <h3>{{ celebrationText }}</h3>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { TrendCharts, Refresh, Aim, VideoPlay, Collection, Reading, Monitor, Link, Right, Check, CircleCheck } from '@element-plus/icons-vue'
import { getLearningPath, refreshLearningPath, markTaskComplete, unmarkTaskComplete } from '@/api/learningPath'

const router = useRouter()

const loading = ref(true)
const refreshing = ref(false)
const taskToggling = ref(false)
const showCelebration = ref(false)
const celebrationText = ref('')
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
  } catch {
    ElMessage.error('刷新失败，请稍后重试')
  } finally {
    refreshing.value = false
  }
}

function openResource(resource) {
  if (resource.url) {
    window.open(resource.url, '_blank')
  } else {
    const keyword = encodeURIComponent(`${resource.title || resource.name} ${resource.type || ''}`)
    window.open(`https://www.bing.com/search?q=${keyword}`, '_blank')
  }
}

function isCurrentPhase(phase) {
  return phase.progressPercent > 0 && phase.progressPercent < 100
}

function isLockedPhase(phase, idx) {
  if (idx === 0) return false
  const prevPhase = pathData.value.phases[idx - 1]
  return prevPhase && prevPhase.progressPercent < 100 && phase.progressPercent === 0
}

function isPrevPhaseCompleted(idx) {
  if (idx === 0) return true
  const prevPhase = pathData.value.phases[idx - 1]
  return prevPhase && prevPhase.progressPercent === 100
}

async function handleTaskToggle(phaseIndex, taskIndex, completed) {
  taskToggling.value = true

  // 找到任务并添加动画标记
  const phase = pathData.value.phases.find(p => p.phaseIndex === phaseIndex)
  let task = null
  if (phase) {
    task = phase.tasks.find(t => t.taskIndex === taskIndex)
    if (task && completed) {
      task._animating = true
      setTimeout(() => { if (task) task._animating = false }, 600)
    }
  }

  // 乐观更新 UI
  if (task) task.completed = completed
  if (phase) {
    phase.completedCount = phase.tasks.filter(t => t.completed).length
    phase.progressPercent = phase.totalCount > 0 ? Math.round(phase.completedCount * 100 / phase.totalCount) : 0
  }

  const allTasks = pathData.value.phases.flatMap(p => p.tasks)
  pathData.value.completedTasks = allTasks.filter(t => t.completed).length
  pathData.value.progressPercent = pathData.value.totalTasks > 0
    ? Math.round(pathData.value.completedTasks * 100 / pathData.value.totalTasks)
    : 0

  // 检查阶段完成
  if (phase && phase.progressPercent === 100 && completed) {
    celebrationText.value = `「${phase.phaseName}」阶段已完成！`
    showCelebration.value = true
    setTimeout(() => { showCelebration.value = false }, 2000)
  }

  try {
    if (completed) {
      await markTaskComplete(phaseIndex, taskIndex)
    } else {
      await unmarkTaskComplete(phaseIndex, taskIndex)
    }
  } catch {
    // 回滚
    if (task) task.completed = !completed
    if (phase) {
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

.loading-state {
  background: #fff;
  border-radius: 12px;
  padding: 32px;
}

// 空状态
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  background: #fff;
  border-radius: 12px;
  padding: 48px;

  h3 { font-size: 20px; color: $text-primary; margin: 24px 0 8px; }
  p { font-size: 14px; color: $text-muted; margin: 0 0 24px; }
}

.empty-illustration {
  position: relative;
  width: 200px;
  height: 100px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-road {
  position: absolute;
  width: 100%;
  height: 2px;
  top: 50%;
}

.road-line {
  width: 100%;
  height: 2px;
  background: linear-gradient(90deg, #E5E7EB, #4F46E5, #E5E7EB);
  border-radius: 1px;
}

.road-dot {
  position: absolute;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #C7D2FE;
  top: -4px;
}

.dot-1 { left: 20%; }
.dot-2 { left: 50%; background: #818CF8; }
.dot-3 { left: 80%; }

.empty-icon-wrap {
  position: relative;
  z-index: 1;
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: linear-gradient(135deg, #EEF2FF, #E0E7FF);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #4F46E5;
}

// 顶部概览
.overview-section {
  background: linear-gradient(135deg, #4F46E5 0%, #7C3AED 100%);
  border-radius: 16px;
  padding: 28px 32px;
  margin-bottom: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #fff;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 12px 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.overview-meta { display: flex; align-items: center; gap: 16px; }
.meta-item { display: flex; flex-direction: column; gap: 4px; }
.meta-label { font-size: 12px; opacity: 0.8; }
.meta-value {
  font-size: 15px; font-weight: 600;
  &.progress { color: #C7D2FE; }
  &.focus { max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
}
.meta-divider { width: 1px; height: 32px; background: rgba(255,255,255,0.3); }

.overview-actions { display: flex; flex-direction: column; align-items: flex-end; gap: 8px; }
.last-updated { font-size: 12px; opacity: 0.8; }
.overview-actions .el-button {
  background: rgba(255,255,255,0.2);
  border: 1px solid rgba(255,255,255,0.3);
  color: #fff;
  &:hover { background: rgba(255,255,255,0.3); }
}

// 进度卡片
.progress-card {
  background: #fff;
  border: 1px solid $border-color;
  border-radius: 12px;
  padding: 20px 24px;
  margin-bottom: 24px;
}

.progress-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.progress-label { font-size: 15px; font-weight: 600; color: $text-primary; }
.progress-numbers { font-size: 13px; color: #6B7280; }
.progress-hint { margin-top: 8px; font-size: 13px; color: #16A34A; font-weight: 500; }

// 路线图
.roadmap-section {
  display: flex;
  flex-direction: column;
  margin-bottom: 24px;
}

.phase-block {
  position: relative;

  &.phase-locked {
    .phase-content { opacity: 0.5; }
  }
}

.phase-connector {
  display: flex;
  justify-content: center;
  padding: 4px 0;
}

.connector-line {
  width: 2px;
  height: 24px;
  background: #E5E7EB;
  border-radius: 1px;
  transition: background 0.3s;

  &.connector-done { background: #4F46E5; }
}

.phase-node {
  display: flex;
  gap: 16px;
}

.phase-badge {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #E5E7EB;
  color: #6B7280;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 700;
  flex-shrink: 0;
  transition: all 0.3s;
  border: 3px solid transparent;

  &.badge-done {
    background: #16A34A;
    color: #fff;
  }

  &.badge-current {
    background: #4F46E5;
    color: #fff;
    border-color: #C7D2FE;
    box-shadow: 0 0 0 4px rgba(79, 70, 229, 0.15);
  }
}

.phase-content {
  flex: 1;
  background: #fff;
  border: 1px solid $border-color;
  border-radius: 12px;
  padding: 20px 24px;
  transition: all 0.3s;

  .phase-completed & {
    border-color: #BBF7D0;
    background: #F0FDF4;
  }

  .phase-current & {
    border-color: #C7D2FE;
    box-shadow: 0 2px 12px rgba(79, 70, 229, 0.08);
  }

  &:hover { box-shadow: 0 4px 12px rgba(0,0,0,0.05); }
}

.phase-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.phase-title {
  font-size: 17px;
  font-weight: 600;
  color: $text-primary;
  margin: 0;
}

.phase-progress-tag {
  display: flex;
  align-items: center;
  gap: 8px;
}

.phase-count {
  font-size: 13px;
  color: #6B7280;
  white-space: nowrap;
}

.phase-focus {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #7C3AED;
  background: #F5F3FF;
  padding: 6px 12px;
  border-radius: 6px;
  margin-bottom: 12px;
}

// 任务列表
.task-list { display: flex; flex-direction: column; gap: 2px; }

.task-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 8px;
  transition: all 0.2s;

  &:hover { background: #F9FAFB; }
  &.completed { opacity: 0.65; }

  &.just-completed {
    animation: taskPop 0.4s ease;
  }

  .el-checkbox { flex: 1; }
}

@keyframes taskPop {
  0% { transform: scale(1); }
  30% { transform: scale(1.03); background: #EEF2FF; }
  100% { transform: scale(1); }
}

.task-text {
  font-size: 14px;
  color: #374151;

  .completed & {
    text-decoration: line-through;
    color: #9CA3AF;
  }
}

.task-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.practice-btn {
  font-size: 13px;
  opacity: 0.6;
  transition: opacity 0.2s;
  &:hover { opacity: 1; }
}

.completed-time {
  font-size: 12px;
  color: #9CA3AF;
}

// 阶段完成提示
.phase-complete-banner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 12px;
  padding: 8px;
  background: #DCFCE7;
  border-radius: 6px;
  color: #16A34A;
  font-size: 13px;
  font-weight: 500;
}

// 推荐资源
.resources-section { margin-bottom: 24px; }
.section-card {
  background: #fff;
  border: 1px solid $border-color;
  border-radius: 12px;
  padding: 24px;
}
.section-header { margin-bottom: 16px; }
.section-title {
  font-size: 17px;
  font-weight: 600;
  color: $text-primary;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.resource-list { display: flex; flex-direction: column; gap: 10px; }

.resource-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px;
  background: #F9FAFB;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: #F3F0FF;
    transform: translateX(4px);
  }
}

.resource-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: #EDE9FE;
  color: #7C3AED;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 16px;
}

.resource-info { display: flex; flex-direction: column; gap: 2px; }
.resource-type { font-size: 11px; color: #7C3AED; font-weight: 500; }
.resource-name { font-size: 14px; font-weight: 500; color: $text-primary; }
.resource-desc { font-size: 12px; color: #6B7280; }

.resource-arrow {
  margin-left: auto;
  color: #9CA3AF;
  font-size: 14px;
  flex-shrink: 0;
  align-self: center;
  transition: all 0.2s;

  .resource-item:hover & { color: #7C3AED; transform: translateX(4px); }
}

// 庆祝动画
.celebration-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  pointer-events: none;
}

.celebration-content {
  text-align: center;
  animation: celebrateIn 0.5s ease;

  .celebration-icon { font-size: 64px; margin-bottom: 12px; }
  h3 { font-size: 20px; color: #16A34A; font-weight: 600; }
}

@keyframes celebrateIn {
  0% { transform: scale(0.5); opacity: 0; }
  50% { transform: scale(1.1); }
  100% { transform: scale(1); opacity: 1; }
}

.celebrate-enter-active { animation: celebrateIn 0.5s ease; }
.celebrate-leave-active { animation: celebrateIn 0.3s ease reverse; }

@media (max-width: 768px) {
  .learning-path-page { padding: 16px; }
  .overview-section { flex-direction: column; gap: 16px; text-align: center; }
  .overview-meta { flex-direction: column; }
  .meta-divider { width: 40px; height: 1px; }
  .overview-actions { align-items: center; }
  .phase-node { gap: 12px; }
  .phase-badge { width: 32px; height: 32px; font-size: 14px; }
  .phase-content { padding: 16px; }
}
</style>
