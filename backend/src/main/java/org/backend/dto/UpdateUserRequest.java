package org.backend.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserRequest {
    @Size(max = 50, message = "昵称不能超过50个字符")
    private String username;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "请输入正确的手机号格式")
    private String phone;

    @Size(max = 50, message = "目标岗位不能超过50个字符")
    private String targetPosition;

    private String techStack;
}