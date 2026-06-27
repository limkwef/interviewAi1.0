package org.backend.entity;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 竞争力分析维度 VO
 */
@Data
public class DimensionVO implements Serializable {
    private static final long serialVersionUID = 1L;
    private String name;
    private Integer userScore;
    private Integer targetScore;
    private Integer gap;
    private String urgency;
    private String analysis;
    private List<String> suggestions;
}
