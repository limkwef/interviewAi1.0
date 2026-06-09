package org.backend.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MistakeAnswerDetail {
    private Long id;
    private Long mistakeId;
    private Long interviewId;
    private String userAnswer;
    private String aiComment;
    private String category;
    private LocalDateTime createdAt;
}
