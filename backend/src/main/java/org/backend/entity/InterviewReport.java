package org.backend.entity;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class InterviewReport {
    private Long id;
    private Long sessionId;
    private Long userId;
    private Integer totalScore;
    private String level;
    private Integer technicalScore;
    private Integer expressionScore;
    private Integer logicScore;
    private Integer completenessScore;
    private Integer innovationScore;
    private String suggestion;
    private LocalDateTime createdAt;
    private List<ReportComment> comments;
    private String position;
    private String round;
    private String difficulty;
}