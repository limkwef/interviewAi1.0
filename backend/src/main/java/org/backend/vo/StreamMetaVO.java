package org.backend.vo;

import lombok.Data;

/**
 * SSE 流式消息元数据 VO
 */
@Data
public class StreamMetaVO {
    private String type;
    private String content;
    private Integer nextQuestion;
    private Integer remainingQuestions;
    private Long messageId;
}
