package org.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Question {
    private Long id;

    @NotBlank(message = "题目标题不能为空")
    private String title;

    @NotBlank(message = "题目内容不能为空")
    private String content;

    @NotBlank(message = "题目答案不能为空")
    private String answer;

    @NotBlank(message = "分类不能为空")
    private String category;

    @NotBlank(message = "难度不能为空")
    private String difficulty;

    @NotBlank(message = "方向不能为空")
    private String direction;

    private Integer viewCount;
    private Integer favoriteCount;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<Tag> tags;
}
