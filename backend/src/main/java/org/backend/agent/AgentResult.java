package org.backend.agent;

import lombok.Data;

/**
 * Agent 运行结果
 * 包含最终回答内容和元数据
 */
@Data
public class AgentResult {

    /** 最终回答内容 */
    private String content;

    /** 决策类型：follow_up / next_question / end */
    private String type;

    /** Agent 循环迭代次数 */
    private int iterations;

    /** 是否使用了工具 */
    private boolean usedTools;

    /** 工具调用详情（日志用） */
    private String toolCallLog;

    // ======================== 工厂方法 ========================

    public static AgentResult of(String content) {
        AgentResult result = new AgentResult();
        result.setContent(content);
        result.setType("answer");
        result.setIterations(1);
        result.setUsedTools(false);
        return result;
    }

    public static AgentResult of(String content, String type, int iterations, boolean usedTools, String toolCallLog) {
        AgentResult result = new AgentResult();
        result.setContent(content);
        result.setType(type);
        result.setIterations(iterations);
        result.setUsedTools(usedTools);
        result.setToolCallLog(toolCallLog);
        return result;
    }
}
