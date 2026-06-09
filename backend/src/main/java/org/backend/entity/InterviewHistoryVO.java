package org.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 面试历史列表 VO
 */
@Data
public class InterviewHistoryVO {
    private List<Item> records;
    private Integer total;
    private Integer page;
    private Integer pageSize;

    @Data
    public static class Item {
        private Long id;
        private String position;
        private String round;
        private String difficulty;
        private String status;
        private LocalDateTime createdAt;
        private Integer totalDurationSeconds;
    }
}
