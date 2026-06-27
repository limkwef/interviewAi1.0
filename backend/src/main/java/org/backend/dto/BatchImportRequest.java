package org.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.backend.entity.Question;

import java.util.List;

@Data
public class BatchImportRequest {

    @NotEmpty(message = "题目列表不能为空")
    @Valid
    private List<Question> questions;

    @Pattern(regexp = "skip|update|duplicate", message = "重复策略必须为 skip、update 或 duplicate")
    private String duplicateStrategy = "skip";
}
