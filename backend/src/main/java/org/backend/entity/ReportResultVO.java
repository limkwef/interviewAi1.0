package org.backend.entity;

import lombok.Data;

import java.util.List;

/**
 * 面试评分报告 VO
 */
@Data
public class ReportResultVO {
    private Integer totalScore;
    private String level;
    private Integer technicalScore;
    private Integer expressionScore;
    private Integer logicScore;
    private Integer completenessScore;
    private Integer innovationScore;
    private String suggestion;
    private List<Comment> comments;

    @Data
    public static class Comment {
        private String questionText;
        private String userAnswer;
        private Integer score;
        private String comment;
        private Integer sortOrder;
    }
}
