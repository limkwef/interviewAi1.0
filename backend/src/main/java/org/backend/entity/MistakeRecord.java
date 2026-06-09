package org.backend.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MistakeRecord {
    private Long id;
    private Long userId;
    private Long questionId;
    private LocalDateTime firstMistakeTime;
    private LocalDateTime lastMistakeTime;
    private Integer mistakeCount;
    private Integer status;       // 0-待复习, 1-已掌握
    private LocalDateTime masteredTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 联表字段（来自 question 表）
    private String questionTitle;
    private String category;
    private String difficulty;
}
