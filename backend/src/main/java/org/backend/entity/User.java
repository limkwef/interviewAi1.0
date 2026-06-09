package org.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class User {

    private Long id;
    private String username;
    private String email;
    private String phone;
    private String password;
    private String avatar;
    private String targetPosition;
    private String techStack;
    private String role;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
