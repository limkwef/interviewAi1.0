package org.backend.entity;

import lombok.Data;

/**
 * 学习进度统计 VO
 */
@Data
public class ProgressStatsVO {
    private Integer totalTasks;
    private Integer completedTasks;
    private Integer progressPercent;
    private String currentPhaseName;
    private String currentFocus;
    private Integer currentPhaseProgress;
    private Integer currentPhaseCompleted;
    private Integer currentPhaseTotal;
}
