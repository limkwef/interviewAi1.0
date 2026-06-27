package org.backend.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class InterviewSession {
    private Long id;
    private Long userId;
    private String position;
    private String round;
    private String difficulty;
    private Integer questionCount;
    private Integer maxFollowUp;
    private Integer currentQuestion;
    private String questionIds;
    private Long resumeId;
    private String interviewType;  // normal / resume
    private String status;
    private String evaluateStatus;      // evaluating / evaluate_failed / null
    private String evaluateError;       // 评估失败原因
    private Long reportId;              // 关联的报告 ID
    private Integer evaluateRetryCount; // 评估重试次数
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** JOIN interview_report 时获取的报告生成时间，用于计算耗时 */
    private LocalDateTime reportCreatedAt;
}