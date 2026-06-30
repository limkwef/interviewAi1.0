package org.backend.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 知识点实体（Agent KnowledgeSearchTool 查询用）
 */
@Data
public class KnowledgePoint {
    private Long id;
    private String category;    // 分类（java_basic/spring/database/redis 等）
    private String keyword;     // 关键词
    private String content;     // 知识点内容（参考答案）
    private String difficulty;  // 难度（easy/medium/hard）
    private LocalDateTime createTime;
}
