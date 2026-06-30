package org.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.backend.entity.DiagnosisReport;
import org.backend.vo.LearningPathVO;
import org.backend.entity.LearningTask;
import org.backend.vo.ProgressStatsVO;
import org.backend.entity.User;
import org.backend.mapper.DiagnosisReportMapper;
import org.backend.mapper.LearningTaskMapper;
import org.backend.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class LearningPathService {

    private static final Logger logger = LoggerFactory.getLogger(LearningPathService.class);

    @Autowired
    private LearningTaskMapper learningTaskMapper;

    @Autowired
    private DiagnosisReportMapper diagnosisReportMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 获取用户当前学习路径
     */
    public LearningPathVO getLearningPath(Long userId) {
        List<LearningTask> tasks = learningTaskMapper.findByUserId(userId);
        User user = userMapper.findById(userId);
        DiagnosisReport latestDiagnosis = diagnosisReportMapper.findLatestByUserId(userId);

        LearningPathVO result = new LearningPathVO();

        if (tasks.isEmpty()) {
            result.setHasPlan(false);
            result.setTargetPosition(user != null ? user.getTargetPosition() : null);
            result.setTotalTasks(0);
            result.setCompletedTasks(0);
            result.setProgressPercent(0);
            result.setPhases(Collections.emptyList());
            result.setResources(Collections.emptyList());
            return result;
        }

        // 按阶段分组
        Map<Integer, List<LearningTask>> phaseMap = new LinkedHashMap<>();
        for (LearningTask task : tasks) {
            phaseMap.computeIfAbsent(task.getPhaseIndex(), k -> new ArrayList<>()).add(task);
        }

        // 计算总体进度
        int totalTasks = tasks.size();
        int completedTasks = (int) tasks.stream().filter(t -> t.getCompleted() == 1).count();
        int progressPercent = totalTasks > 0 ? (completedTasks * 100 / totalTasks) : 0;

        // 找到当前阶段（第一个未完成的阶段）
        String currentFocus = null;
        for (Map.Entry<Integer, List<LearningTask>> entry : phaseMap.entrySet()) {
            boolean allCompleted = entry.getValue().stream().allMatch(t -> t.getCompleted() == 1);
            if (!allCompleted) {
                currentFocus = entry.getValue().get(0).getFocusArea();
                break;
            }
        }

        // 构建阶段列表
        List<LearningPathVO.Phase> phases = new ArrayList<>();
        for (Map.Entry<Integer, List<LearningTask>> entry : phaseMap.entrySet()) {
            List<LearningTask> phaseTasks = entry.getValue();
            int phaseCompleted = (int) phaseTasks.stream().filter(t -> t.getCompleted() == 1).count();
            int phaseTotal = phaseTasks.size();
            int phaseProgress = phaseTotal > 0 ? (phaseCompleted * 100 / phaseTotal) : 0;

            LearningPathVO.Phase phase = new LearningPathVO.Phase();
            phase.setPhaseIndex(entry.getKey());
            phase.setPhaseName(phaseTasks.get(0).getPhaseName());
            phase.setFocus(phaseTasks.get(0).getFocusArea());
            phase.setProgressPercent(phaseProgress);
            phase.setCompletedCount(phaseCompleted);
            phase.setTotalCount(phaseTotal);

            List<LearningPathVO.TaskItem> taskList = new ArrayList<>();
            for (LearningTask task : phaseTasks) {
                LearningPathVO.TaskItem taskItem = new LearningPathVO.TaskItem();
                taskItem.setTaskIndex(task.getTaskIndex());
                taskItem.setText(task.getTaskText());
                taskItem.setCompleted(task.getCompleted() == 1);
                taskItem.setCompletedAt(task.getCompletedAt());
                taskList.add(taskItem);
            }
            phase.setTasks(taskList);
            phases.add(phase);
        }

        // 解析资源
        List<LearningPathVO.Resource> resources = Collections.emptyList();
        if (latestDiagnosis != null && latestDiagnosis.getLearningPlan() != null) {
            try {
                Map<String, Object> plan = objectMapper.readValue(latestDiagnosis.getLearningPlan(),
                        new TypeReference<Map<String, Object>>() {});
                Object resObj = plan.get("resources");
                if (resObj instanceof List) {
                    List<Map<String, Object>> rawResources = (List<Map<String, Object>>) resObj;
                    resources = new ArrayList<>();
                    for (Map<String, Object> raw : rawResources) {
                        resources.add(objectMapper.convertValue(raw, LearningPathVO.Resource.class));
                    }
                }
            } catch (Exception e) {
                logger.warn("解析学习计划资源失败，用户{}", userId, e);
            }
        }

        result.setHasPlan(true);
        result.setTargetPosition(user != null ? user.getTargetPosition() : null);
        result.setTotalTasks(totalTasks);
        result.setCompletedTasks(completedTasks);
        result.setProgressPercent(progressPercent);
        result.setCurrentFocus(currentFocus);
        result.setPhases(phases);
        result.setResources(resources);
        result.setLastUpdated(latestDiagnosis != null ? latestDiagnosis.getCreatedAt() : null);
        result.setSourceSessionId(latestDiagnosis != null ? latestDiagnosis.getSessionId() : null);

        return result;
    }

    /**
     * 获取学习进度统计（给首页用）
     */
    public ProgressStatsVO getProgressStats(Long userId) {
        List<LearningTask> tasks = learningTaskMapper.findByUserId(userId);

        if (tasks.isEmpty()) {
            return null;
        }

        int totalTasks = tasks.size();
        int completedTasks = (int) tasks.stream().filter(t -> t.getCompleted() == 1).count();
        int progressPercent = totalTasks > 0 ? (completedTasks * 100 / totalTasks) : 0;

        // 找到当前阶段
        Map<Integer, List<LearningTask>> phaseMap = new LinkedHashMap<>();
        for (LearningTask task : tasks) {
            phaseMap.computeIfAbsent(task.getPhaseIndex(), k -> new ArrayList<>()).add(task);
        }

        String currentPhaseName = null;
        String currentFocus = null;
        int currentPhaseCompleted = 0;
        int currentPhaseTotal = 0;
        int currentPhaseProgress = 0;

        for (Map.Entry<Integer, List<LearningTask>> entry : phaseMap.entrySet()) {
            List<LearningTask> phaseTasks = entry.getValue();
            boolean allCompleted = phaseTasks.stream().allMatch(t -> t.getCompleted() == 1);
            if (!allCompleted) {
                currentPhaseName = phaseTasks.get(0).getPhaseName();
                currentFocus = phaseTasks.get(0).getFocusArea();
                currentPhaseCompleted = (int) phaseTasks.stream().filter(t -> t.getCompleted() == 1).count();
                currentPhaseTotal = phaseTasks.size();
                currentPhaseProgress = currentPhaseTotal > 0 ? (currentPhaseCompleted * 100 / currentPhaseTotal) : 0;
                break;
            }
        }

        ProgressStatsVO stats = new ProgressStatsVO();
        stats.setTotalTasks(totalTasks);
        stats.setCompletedTasks(completedTasks);
        stats.setProgressPercent(progressPercent);
        stats.setCurrentPhaseName(currentPhaseName);
        stats.setCurrentFocus(currentFocus);
        stats.setCurrentPhaseProgress(currentPhaseProgress);
        stats.setCurrentPhaseCompleted(currentPhaseCompleted);
        stats.setCurrentPhaseTotal(currentPhaseTotal);

        return stats;
    }

    /**
     * 从最新诊断报告刷新学习计划
     */
    @Transactional
    public LearningPathVO refreshFromDiagnosis(Long userId) {
        // 1. 获取最新诊断报告
        DiagnosisReport latest = diagnosisReportMapper.findLatestByUserId(userId);
        if (latest == null || latest.getLearningPlan() == null) {
            // 没有诊断报告时只清理已完成任务
            learningTaskMapper.deleteByUserIdAndCompleted(userId, 1);
            return getLearningPath(userId);
        }

        // 2. 解析 learningPlan JSON
        try {
            Map<String, Object> plan = objectMapper.readValue(latest.getLearningPlan(),
                    new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> phases = (List<Map<String, Object>>) plan.get("phases");
            if (phases == null || phases.isEmpty()) {
                return getLearningPath(userId);
            }

            // 3. 删除旧的已完成任务，保留未完成的
            learningTaskMapper.deleteByUserIdAndCompleted(userId, 1);

            // 4. 插入新任务（跳过已有 phaseIndex + taskIndex 的）
            List<LearningTask> newTasks = new ArrayList<>();
            for (int pi = 0; pi < phases.size(); pi++) {
                Map<String, Object> phase = phases.get(pi);
                String phaseName = (String) phase.get("phase");
                String focus = (String) phase.get("focus");
                List<String> tasks = (List<String>) phase.get("tasks");

                if (tasks == null) continue;
                for (int ti = 0; ti < tasks.size(); ti++) {
                    // 检查是否已存在（未完成的需保留）
                    LearningTask existing = learningTaskMapper.findByUserPhaseTask(userId, pi, ti);
                    if (existing != null) continue;

                    LearningTask task = new LearningTask();
                    task.setUserId(userId);
                    task.setPhaseIndex(pi);
                    task.setTaskIndex(ti);
                    task.setPhaseName(phaseName);
                    task.setTaskText(tasks.get(ti));
                    task.setFocusArea(focus);
                    task.setCompleted(0);
                    newTasks.add(task);
                }
            }

            if (!newTasks.isEmpty()) {
                learningTaskMapper.insertBatch(newTasks);
                logger.info("从诊断报告刷新学习计划，用户{}新增{}条任务", userId, newTasks.size());
            }
        } catch (Exception e) {
            logger.error("解析学习计划JSON失败，用户{}", userId, e);
        }

        return getLearningPath(userId);
    }

    /**
     * 标记任务完成
     */
    public void markTaskComplete(Long userId, Integer phaseIndex, Integer taskIndex) {
        learningTaskMapper.markComplete(userId, phaseIndex, taskIndex);
    }

    /**
     * 取消任务完成标记
     */
    public void unmarkTaskComplete(Long userId, Integer phaseIndex, Integer taskIndex) {
        learningTaskMapper.unmarkComplete(userId, phaseIndex, taskIndex);
    }
}
