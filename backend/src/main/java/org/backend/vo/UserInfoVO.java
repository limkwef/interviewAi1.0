package org.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息 VO
 */
@Data
public class UserInfoVO {
    private Long id;
    private String username;
    private String email;
    private String phone;
    private String avatar;
    private String targetPosition;
    private String techStack;
    private String role;
    private LocalDateTime createdAt;
}
