package org.backend.entity;

import lombok.Data;

import java.util.List;

/**
 * 竞争力分析维度 VO
 */
@Data
public class DimensionVO {
    private String name;
    private Integer userScore;
    private Integer targetScore;
    private Integer gap;
    private String urgency;
    private String analysis;
    private List<String> suggestions;
}
