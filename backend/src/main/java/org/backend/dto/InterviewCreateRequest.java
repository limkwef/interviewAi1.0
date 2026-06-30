package org.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class InterviewCreateRequest {

    @NotBlank(message = "请选择岗位方向")
    private String position;

    @NotBlank(message = "请选择面试轮次")
    private String round;

    @NotBlank(message = "请选择难度等级")
    private String difficulty;

    @Positive(message = "题目数量至少为 1")
    @Min(value = 1, message = "题目数量至少为 1")
    @Max(value = 20, message = "题目数量最多为 20")
    private Integer questionCount = 5;

    @Min(value = 0, message = "追问次数不能为负数")
    @Max(value = 10, message = "追问次数最多为 10")
    private Integer maxFollowUp = 4;

    private Long resumeId;

    private Long modelId;

    private String interviewType = "normal";
}
