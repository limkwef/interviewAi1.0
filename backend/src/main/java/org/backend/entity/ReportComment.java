package org.backend.entity;

import lombok.Data;

@Data
public class ReportComment {
    private Long id;
    private Long reportId;
    private String questionText;
    private String userAnswer;
    private Integer score;
    private String comment;
    private Integer sortOrder;
    private Integer durationSeconds;
}