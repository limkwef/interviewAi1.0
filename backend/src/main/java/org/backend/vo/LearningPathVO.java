package org.backend.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 学习路径 VO
 */
@Data
public class LearningPathVO {
    private Boolean hasPlan;
    private String targetPosition;
    private Integer totalTasks;
    private Integer completedTasks;
    private Integer progressPercent;
    private String currentFocus;
    private List<Phase> phases;
    private List<Resource> resources;
    private LocalDateTime lastUpdated;
    private Long sourceSessionId;

    @Data
    public static class Phase {
        private Integer phaseIndex;
        private String phaseName;
        private String focus;
        private Integer progressPercent;
        private Integer completedCount;
        private Integer totalCount;
        private List<TaskItem> tasks;
    }

    @Data
    public static class TaskItem {
        private Integer taskIndex;
        private String text;
        private Boolean completed;
        private LocalDateTime completedAt;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Resource {
        private String title;
        private String name;
        private String type;
        private String url;
        private String description;
    }
}
