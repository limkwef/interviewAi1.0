package org.backend.agent;

import lombok.Data;

import java.util.Map;

/**
 * Agent 动作封装
 * LLM 在 think 阶段输出的决策，可能是调用工具或直接回答
 */
@Data
public class AgentAction {

    /** 是否是最终回答 */
    private boolean finalAnswer;

    /** 最终回答内容（finalAnswer=true 时有值） */
    private String answer;

    /** 要调用的工具名（finalAnswer=false 时有值） */
    private String toolName;

    /** 工具参数（finalAnswer=false 时有值） */
    private Map<String, Object> params;

    /** 思考过程（日志/调试用） */
    private String reasoning;

    /** 动作类型：tool / answer / error */
    private String actionType;

    /** 面试决策类型：follow_up / next / end（answer 时有效） */
    private String decisionType;

    // ======================== 工厂方法 ========================

    /** 创建"调用工具"动作 */
    public static AgentAction toolAction(String toolName, Map<String, Object> params, String reasoning) {
        AgentAction action = new AgentAction();
        action.setFinalAnswer(false);
        action.setToolName(toolName);
        action.setParams(params);
        action.setReasoning(reasoning);
        action.setActionType("tool");
        return action;
    }

    /** 创建"最终回答"动作 */
    public static AgentAction answerAction(String answer, String reasoning, String decisionType) {
        AgentAction action = new AgentAction();
        action.setFinalAnswer(true);
        action.setAnswer(answer);
        action.setReasoning(reasoning);
        action.setActionType("answer");
        action.setDecisionType(decisionType);
        return action;
    }

    /** 创建"错误"动作（LLM 输出解析失败时） */
    public static AgentAction errorAction(String errorMessage) {
        AgentAction action = new AgentAction();
        action.setFinalAnswer(true);
        action.setAnswer(errorMessage);
        action.setReasoning("Agent 解析失败，降级为直接回答");
        action.setActionType("error");
        return action;
    }
}
