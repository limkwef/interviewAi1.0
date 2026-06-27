package org.backend.entity;

import lombok.Data;

import java.io.Serializable;

/**
 * 提升预测 VO
 */
@Data
public class ImprovementPredictionVO implements Serializable {
    private static final long serialVersionUID = 1L;
    private String focus;
    private Integer estimatedScore;
    private String effort;
}
