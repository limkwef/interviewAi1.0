package org.backend.entity;

import lombok.Data;

/**
 * AI 评估返回结果 VO
 */
@Data
public class AIResultVO {
    private String content;
    private String type;
    private Integer nextQuestion;
    private Integer remainingQuestions;
}
