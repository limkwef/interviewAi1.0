package org.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.backend.entity.DiagnosisReport;
import org.backend.entity.LearningPathVO;
import org.backend.entity.LearningTask;
import org.backend.entity.ProgressStatsVO;
import org.backend.entity.User;
import org.backend.mapper.DiagnosisReportMapper;
import org.backend.mapper.LearningTaskMapper;
import org.backend.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class LearningPathService {

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
                // 解析失败，返回空资源列表
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
        // 只删除已勾选（已完成）的任务，未完成的保留
        learningTaskMapper.deleteByUserIdAndCompleted(userId, 1);
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
