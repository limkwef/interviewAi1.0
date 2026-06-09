package org.backend.entity;

import lombok.Data;

/**
 * SSE 降级轮询状态 VO
 */
@Data
public class PollStatusVO {
    /** 消息 ID */
    private Long messageId;
    /** 消息角色（ai / user） */
    private String role;
    /** 当前已生成的内容（增量） */
    private String content;
    /** 消息类型：follow_up / next_question / end */
    private String type;
    /** 下一题序号 */
    private Integer nextQuestion;
    /** 剩余题目数 */
    private Integer remainingQuestions;
    /** 生成状态：streaming / completed */
    private String status;
}
