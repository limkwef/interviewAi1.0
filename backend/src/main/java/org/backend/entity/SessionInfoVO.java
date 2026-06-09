package org.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试会话信息 VO
 */
@Data
public class SessionInfoVO {
    private Long id;
    private String position;
    private String round;
    private String difficulty;
    private Integer questionCount;
    private Integer currentQuestion;
    private String status;
    private LocalDateTime createdAt;
}
