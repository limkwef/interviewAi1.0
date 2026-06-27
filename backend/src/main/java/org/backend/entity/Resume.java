package org.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历实体
 */
@Data
public class Resume {
    private Long id;
    private Long userId;
    private Integer version;
    private String source;          // upload / template
    private Integer status;         // 0-解析中 1-解析完成 2-解析失败
    private String errorMsg;
    private String rawText;
    private String parsedData;      // JSON 字符串，查询时手动反序列化为 ResumeData
    private String fileName;
    private String fileUrl;
    private Long fileSize;
    private Integer isActive;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
