package org.backend.dto;

import lombok.Data;

/**
 * 错题导入 DTO
 */
@Data
public class WrongAnswerDTO {
    private Long questionId;
    private String userAnswer;
    private String aiComment;
    private String category;
}
