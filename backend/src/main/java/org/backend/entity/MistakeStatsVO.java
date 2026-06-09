package org.backend.entity;

import lombok.Data;

import java.util.List;

/**
 * 错题统计 VO
 */
@Data
public class MistakeStatsVO {
    private Long total;
    private Long pendingReview;
    private Long mastered;
    private String masteredRate;
    private List<CategoryStat> byCategory;

    @Data
    public static class CategoryStat {
        private String category;
        private String label;
        private Integer count;
    }
}
