package org.backend.entity;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 竞争力分析结果（存储在 Redis 缓存中，不持久化到 MySQL）
 */
@Data
public class CompetitiveAnalysis {
    private Long userId;
    private String targetPosition;
    private Integer currentScore;
    private Integer targetScore;
    private Integer gap;

    // 各维度得分
    private Integer technicalScore;
    private Integer expressionScore;
    private Integer logicScore;
    private Integer completenessScore;
    private Integer innovationScore;

    // 各维度目标分数
    private Integer technicalTarget;
    private Integer expressionTarget;
    private Integer logicTarget;
    private Integer completenessTarget;
    private Integer innovationTarget;

    // 趋势分析
    private String trendDirection;   // rapid_up / slow_up / stable / slow_down / rapid_down
    private Double trendSlope;       // 斜率（分/次）
    private Boolean confidence;      // 数据置信度：true=充足, false=仅供参考
    private Integer interviewCount;  // 面试次数

    // 同行对比（基于同岗位真实数据）
    private Integer peerTotalCount;  // 同岗位总人数
    private Integer peerRank;        // 用户排名
    private Integer peerPercentile;  // 百分位（超过 x% 的候选人）

    // 结构化数据
    private List<DimensionVO> dimensions;
    private List<String> competitiveAdvantage;
    private List<String> weaknesses;
    private List<ImprovementPredictionVO> improvementPrediction;
    private String summary;

    private LocalDateTime createdAt;
}
