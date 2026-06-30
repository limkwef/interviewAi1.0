package org.backend.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * Agent 长期记忆实体
 */
@Data
public class AgentMemory {
    private Long id;
    private Long userId;
    private String memoryType;      // strength / weakness / strategy / summary
    private String content;
    private Long sourceSessionId;
    private Float confidence;
    private Integer accessCount;
    private LocalDateTime lastAccessedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
