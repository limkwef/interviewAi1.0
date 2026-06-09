package org.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 错题详情 VO
 */
@Data
public class MistakeDetailVO {
    private MistakeInfo mistake;
    private QuestionInfo question;
    private List<MistakeAnswerDetail> details;

    @Data
    public static class MistakeInfo {
        private Long id;
        private Long questionId;
        private Integer mistakeCount;
        private Integer status;
        private LocalDateTime firstMistakeTime;
        private LocalDateTime lastMistakeTime;
    }

    @Data
    public static class QuestionInfo {
        private Long id;
        private String title;
        private String content;
        private String answer;
        private String difficulty;
        private String category;
    }
}
