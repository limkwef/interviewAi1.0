package org.backend.entity;

import lombok.Data;

/**
 * SSE 流式消息元数据 VO
 */
@Data
public class StreamMetaVO {
    private String type;
    private Integer nextQuestion;
    private Integer remainingQuestions;
    private Long messageId;
}
