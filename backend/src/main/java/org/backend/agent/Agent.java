package org.backend.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.backend.agent.tool.Tool;
import org.backend.agent.tool.ToolRegistry;
import org.backend.util.AIService;
import org.backend.util.ScoringEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Agent 抽象基类
 * 实现 think-act-observe 循环，子类只需实现 buildSystemPrompt() 和 think()
 */
public abstract class Agent {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final AIService aiService;
    protected final ScoringEngine scoringEngine;
    protected final ToolRegistry toolRegistry;
    protected final AgentConfig agentConfig;
    protected final ObjectMapper objectMapper;

    protected Agent(AIService aiService, ScoringEngine scoringEngine,
                    ToolRegistry toolRegistry, AgentConfig agentConfig,
                    ObjectMapper objectMapper) {
        this.aiService = aiService;
        this.scoringEngine = scoringEngine;
        this.toolRegistry = toolRegistry;
        this.agentConfig = agentConfig;
        this.objectMapper = objectMapper;
    }

    /**
     * 运行 Agent 循环
     *
     * @param task    用户输入（候选人的回答）
     * @param context 对话上下文（system + 历史消息）
     * @return Agent 运行结果
     */
    public AgentResult run(String task, List<Map<String, String>> context) {
        List<Map<String, String>> memory = new ArrayList<>(context);
        memory.add(Map.of("role", "user", "content", task));

        StringBuilder toolCallLog = new StringBuilder();
        boolean usedTools = false;
        int iterations = 0;

        for (int i = 0; i < agentConfig.getMaxIterations(); i++) {
            iterations = i + 1;

            // 1. Think：LLM 决定下一步
            AgentAction action = think(memory);

            // 2. 如果是最终回答，直接返回
            if (action.isFinalAnswer()) {
                String content = action.getAnswer();
                if (content == null || content.isBlank()) {
                    content = "感谢你的回答，我们继续下一题。";
                }
                // 优先使用 LLM 返回的 decisionType，降级用 ScoringEngine 解析
                String type = action.getDecisionType() != null
                        ? action.getDecisionType() : detectType(content);
                AgentResult result = AgentResult.of(content, type, iterations, usedTools,
                        toolCallLog.toString());
                return result;
            }

            // 3. Act：调用工具
            if (!agentConfig.isToolsEnabled()) {
                // 工具禁用时，强制 LLM 直接回答
                logger.warn("工具已禁用，跳过工具调用: {}", action.getToolName());
                memory.add(Map.of("role", "user", "content",
                        "工具暂不可用，请直接回答问题。"));
                continue;
            }

            String toolResult = act(action);
            usedTools = true;
            toolCallLog.append(String.format("迭代%d: 调用 %s → %s\n",
                    iterations, action.getToolName(),
                    toolResult.length() > 200 ? toolResult.substring(0, 200) + "..." : toolResult));

            // 4. Observe：将工具结果记录到记忆中
            observe(memory, action, toolResult);

            logger.info("Agent 迭代 {}/{}: 调用工具 {}, 结果长度={}",
                    iterations, agentConfig.getMaxIterations(), action.getToolName(), toolResult.length());
        }

        // 达到最大迭代次数，强制总结
        logger.warn("Agent 达到最大迭代次数 {}，强制总结返回", agentConfig.getMaxIterations());
        String summary = summarize(memory);
        return AgentResult.of(summary, detectType(summary), iterations, usedTools,
                toolCallLog.toString());
    }

    /**
     * Think：让 LLM 决定下一步
     * 返回 AgentAction（工具调用 或 最终回答）
     */
    protected AgentAction think(List<Map<String, String>> memory) {
        // 构建 think 请求：system prompt + 工具描述 + 历史记忆
        List<Map<String, String>> messages = new ArrayList<>();

        // System prompt：角色设定 + 工具描述 + 决策指令
        String systemPrompt = buildSystemPrompt();
        if (agentConfig.isToolsEnabled()) {
            systemPrompt += "\n\n" + toolRegistry.buildToolDescriptions();
            systemPrompt += "\n\n" + buildToolDecisionInstructions();
        } else {
            systemPrompt += "\n\n请直接给出面试回复内容，不要尝试调用工具。";
        }

        // 强化 JSON 输出约束（DeepSeek 对 response_format 支持不稳定，需在 prompt 中显式要求）
        systemPrompt += "\n\n【输出格式强制要求】\n" +
                "你必须且只能输出一个合法的 JSON 对象，不要输出任何其他文字、解释或 markdown 标记。\n" +
                "禁止以「好的」「以下是」等自然语言开头，直接输出 JSON。";

        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.addAll(memory);

        // 调用 LLM（结构化输出）
        Map<String, Object> result = aiService.chatStructuredWithFallback(messages);
        if (result == null) {
            // LLM 调用完全失败，降级
            return AgentAction.errorAction("AI 服务暂时不可用，请稍后再试。");
        }

        return parseAction(result);
    }

    /**
     * Act：执行工具调用
     */
    protected String act(AgentAction action) {
        Tool tool = toolRegistry.getTool(action.getToolName());
        if (tool == null) {
            logger.warn("工具不存在: {}", action.getToolName());
            return "工具「" + action.getToolName() + "」不存在。请直接基于你的知识回答。";
        }

        try {
            Map<String, Object> params = action.getParams();
            if (params == null) params = Collections.emptyMap();
            return tool.execute(params);
        } catch (Exception e) {
            logger.error("工具 {} 执行失败", action.getToolName(), e);
            return "工具调用失败：" + e.getMessage() + "。请直接基于你的知识回答。";
        }
    }

    /**
     * Observe：将工具调用结果记录到记忆中
     */
    protected void observe(List<Map<String, String>> memory, AgentAction action, String toolResult) {
        // 将工具调用和结果作为 assistant 消息记录
        String observation = String.format("【工具调用】%s\n参数: %s\n结果: %s",
                action.getToolName(),
                action.getParams() != null ? action.getParams().toString() : "{}",
                toolResult);
        memory.add(Map.of("role", "assistant", "content", observation));
    }

    /**
     * 达到最大迭代次数时，强制 LLM 总结
     */
    protected String summarize(List<Map<String, String>> memory) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content",
                "你是一位面试官。请根据以下对话历史，直接给出下一条面试回复（不要调用工具）。"));
        messages.addAll(memory);
        messages.add(Map.of("role", "user", "content",
                "请直接给出面试回复，不要尝试调用任何工具。"));

        String result = aiService.chat(messages);
        return result != null ? result : "感谢你的回答，我们继续。";
    }

    /**
     * 解析 LLM 输出为 AgentAction
     */
    protected AgentAction parseAction(Map<String, Object> result) {
        try {
            String actionType = (String) result.getOrDefault("action", "answer");

            if ("tool".equalsIgnoreCase(actionType) || "use_tool".equalsIgnoreCase(actionType)) {
                String toolName = (String) result.get("tool");
                String reasoning = (String) result.getOrDefault("reasoning", "");
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (Map<String, Object>) result.get("params");
                if (params == null) params = Collections.emptyMap();

                if (toolName == null || toolName.isBlank()) {
                    return AgentAction.errorAction("工具名为空，降级为直接回答");
                }
                return AgentAction.toolAction(toolName, params, reasoning);
            } else {
                // answer 或其他 → 最终回答
                String content = (String) result.getOrDefault("content", result.getOrDefault("answer", ""));
                String reasoning = (String) result.getOrDefault("reasoning", "");
                String decisionType = (String) result.getOrDefault("type",
                        result.getOrDefault("decision", "follow_up"));

                AgentAction action = AgentAction.answerAction(content, reasoning, decisionType);

                return action;
            }
        } catch (Exception e) {
            logger.error("解析 AgentAction 失败", e);
            return AgentAction.errorAction("解析失败，降级为直接回答");
        }
    }

    /**
     * 从回答内容中检测决策类型
     */
    protected String detectType(String content) {
        if (content == null) return "answer";
        // 复用 ScoringEngine 的决策解析
        return scoringEngine.parseDecisionType(content);
    }

    // ======================== 子类实现 ========================

    /** 构建 Agent 的 system prompt（角色设定 + 行为准则） */
    protected abstract String buildSystemPrompt();

    // ======================== 工具决策指令 ========================

    /** 构建工具调用的决策指令（告诉 LLM 如何输出工具调用请求） */
    protected String buildToolDecisionInstructions() {
        return """
                === 工具使用规则 ===
                
                你可以选择：
                1. **调用工具**：当你需要查询参考资料、记录错题或调整难度时
                2. **直接回答**：当你已经有足够信息可以直接回复候选人时
                
                【重要】大多数情况下你应该直接回答，只在以下场景调用工具：
                - 候选人提到了一个你不太确定的概念 → 调用 knowledge_search 查询
                - 候选人回答有明显错误 → 调用 mistake_record 记录
                - 候选人连续答对/答错，需要调整难度 → 调用 difficulty_adjust
                
                输出格式（JSON）：
                调用工具：{"action":"tool","tool":"工具名","params":{...},"reasoning":"思考过程"}
                直接回答：{"action":"answer","content":"你的回复内容","type":"决策类型","reasoning":"思考过程"}
                
                决策类型(type)必须是以下之一：
                - "follow_up"：对当前回答进行追问、评价、深入探讨
                - "next"：当前问题已完成，进入下一题
                - "end"：面试结束，给出总结评价
                
                注意：content 中不要包含【决策: xxx】标记，系统会根据 type 字段自动处理。
                """;
    }
}
