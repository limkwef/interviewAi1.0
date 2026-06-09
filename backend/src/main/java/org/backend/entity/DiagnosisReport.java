package org.backend.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DiagnosisReport {
    private Long id;
    private Long userId;
    private Long sessionId;
    private Long reportId;

    // 原有评分
    private Integer totalScore;
    private String level;
    private Integer scoreChange;      // 较上次变化
    private Integer previousScore;    // 上次分数

    // JSON 字段（MyBatis 中用 String 存储，Service 层做 JSON 序列化/反序列化）
    private String knowledgeAnalysis;  // 知识维度分析
    private String thinkingAnalysis;   // 思维模式分析
    private String mistakePatterns;    // 错误模式
    private String learningPlan;       // 学习计划
    private String detailedComments;   // 逐题详细点评

    private LocalDateTime createdAt;
}
