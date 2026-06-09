package org.backend.entity;

import lombok.Data;

/**
 * 提升预测 VO
 */
@Data
public class ImprovementPredictionVO {
    private String focus;
    private Integer estimatedScore;
    private String effort;
}
