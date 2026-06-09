package org.backend.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class LearningTask {
    private Long id;
    private Long userId;
    private Integer phaseIndex;
    private Integer taskIndex;
    private String phaseName;
    private String taskText;
    private String focusArea;
    private Integer completed;     // 0-未完成 1-已完成
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
}
