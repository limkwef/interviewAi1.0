package org.backend.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserFavorite {
    private Long id;
    private Long userId;
    private Long questionId;
    private LocalDateTime createdAt;
}
