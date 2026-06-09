package org.backend.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class InterviewSession {
    private Long id;
    private Long userId;
    private String position;
    private String round;
    private String difficulty;
    private Integer questionCount;
    private Integer currentQuestion;
    private String questionIds;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** JOIN interview_report 时获取的报告生成时间，用于计算耗时 */
    private LocalDateTime reportCreatedAt;
}