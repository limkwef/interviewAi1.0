package org.backend.entity;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class Question {
    private Long id;
    private String title;
    private String content;
    private String answer;
    private String category;
    private String difficulty;
    private String direction;
    private Integer viewCount;
    private Integer favoriteCount;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<Tag> tags;
}
