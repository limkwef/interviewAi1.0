package org.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "邮箱或手机号不能为空")
    @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$|^1[3-9]\\d{9}$", message = "请输入正确的邮箱或手机号格式")
    private String account;

    @NotBlank(message = "密码不能为空")
    private String password;
}
