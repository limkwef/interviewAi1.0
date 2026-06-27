package org.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建面试结果 VO
 */
@Data
public class InterviewCreateVO {
    private Long id;
    private String position;
    private String round;
    private String difficulty;
    private Integer questionCount;
    private Integer maxFollowUp;
    private Integer currentQuestion;
    private String interviewType;  // normal / resume
    private String status;
    private LocalDateTime createdAt;
    private FirstMessage firstMessage;

    @Data
    public static class FirstMessage {
        private Long id;
        private String role;
        private String content;
        private String type;
        private LocalDateTime createdAt;
    }
}
