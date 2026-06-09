package org.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 发送消息结果 VO
 */
@Data
public class SendMessageVO {
    private Long id;
    private String role;
    private String content;
    private String type;
    private Integer nextQuestion;
    private Integer remainingQuestions;
    private LocalDateTime createdAt;
}
