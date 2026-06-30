package org.backend.agent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.backend.agent.memory.LongTermMemory;
import org.backend.agent.tool.ToolRegistry;
import org.backend.entity.InterviewSession;
import org.backend.entity.Question;
import org.backend.mapper.InterviewMessageMapper;
import org.backend.mapper.InterviewSessionMapper;
import org.backend.service.InterviewContextService;
import org.backend.util.AIService;
import org.backend.util.ScoringEngine;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 面试官 Agent
 * 继承 Agent 基类，实现面试专用的 think-act-observe 逻辑
 */
@Component
public class InterviewerAgent extends Agent {

    private final InterviewContextService contextService;
    private final InterviewSessionMapper sessionMapper;
    private final InterviewMessageMapper messageMapper;
    private final LongTermMemory longTermMemory;

    public InterviewerAgent(AIService aiService, ScoringEngine scoringEngine,
                            ToolRegistry toolRegistry, AgentConfig agentConfig,
                            ObjectMapper objectMapper,
                            InterviewContextService contextService,
                            InterviewSessionMapper sessionMapper,
                            InterviewMessageMapper messageMapper,
                            LongTermMemory longTermMemory) {
        super(aiService, scoringEngine, toolRegistry, agentConfig, objectMapper);
        this.contextService = contextService;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.longTermMemory = longTermMemory;
    }

    /**
     * 处理候选人的回答（主入口）
     *
     * @param sessionId  面试会话 ID
     * @param userAnswer 候选人的回答
     * @param currentQuestion 当前题目（可选）
     * @param questionIndex   当前题目序号
     * @param totalQuestions   总题数
     * @return Agent 运行结果
     */
    public AgentResult processAnswer(Long sessionId, String userAnswer,
                                     Question currentQuestion, int questionIndex,
                                     int totalQuestions) {
        // 1. 加载短期记忆（Redis 上下文）
        List<Map<String, String>> context = new ArrayList<>();
        List<Map<String, String>> redisContext = contextService.getContext(sessionId);
        // 只取 system + 历史对话（去掉最后一条 user 消息，因为它会被重新添加）
        for (int i = 0; i < redisContext.size(); i++) {
            Map<String, String> msg = redisContext.get(i);
            if ("system".equals(msg.get("role"))) {
                context.add(msg);
            } else if (i < redisContext.size() - 1 || !"user".equals(msg.get("role"))) {
                context.add(msg);
            }
        }

        // 2. 获取 userId、当前难度、面试类型
        Long userId = null;
        String currentDifficulty = "medium";
        String interviewType = "normal";
        int maxFollowUp = 3;
        try {
            InterviewSession session = sessionMapper.findById(sessionId);
            if (session != null) {
                userId = session.getUserId();
                if (session.getDifficulty() != null) {
                    currentDifficulty = session.getDifficulty();
                }
                if (session.getInterviewType() != null) {
                    interviewType = session.getInterviewType();
                }
                if (session.getMaxFollowUp() != null) {
                    maxFollowUp = session.getMaxFollowUp();
                }
            }
        } catch (Exception e) {
            logger.warn("获取 session 信息失败: {}", e.getMessage());
        }

        // 3. 召回长期记忆（仅简历面试时召回，普通面试不注入避免干扰）
        String longTermContext = "";
        if (userId != null && "resume".equals(interviewType)) {
            longTermContext = longTermMemory.recall(userId);
        }

        // 4. 构建任务描述（注入长期记忆、当前难度、面试类型、追问次数）
        int currentFollowUp = contextService.getFollowUpCount(sessionId, questionIndex, messageMapper);
        String task = buildTaskDescription(userAnswer, currentQuestion, questionIndex,
                totalQuestions, userId, sessionId, longTermContext, currentDifficulty, interviewType,
                currentFollowUp, maxFollowUp);

        // 5. 注入工具上下文参数
        injectToolContext(sessionId, userId);

        // 6. 运行 Agent 循环
        AgentResult result = run(task, context);

        // 7. 更新短期记忆
        try {
            contextService.addMessage(sessionId, "user", userAnswer);
            contextService.addMessage(sessionId, "assistant", result.getContent());
        } catch (Exception e) {
            logger.warn("更新短期记忆失败: {}", e.getMessage());
        }

        logger.info("InterviewerAgent 完成: sessionId={}, iterations={}, usedTools={}, type={}",
                sessionId, result.getIterations(), result.isUsedTools(), result.getType());
        return result;
    }

    /**
     * 构建任务描述（注入到 user 消息中）
     */
    private String buildTaskDescription(String userAnswer, Question currentQuestion,
                                        int questionIndex, int totalQuestions,
                                        Long userId, Long sessionId, String longTermContext,
                                        String currentDifficulty, String interviewType,
                                        int currentFollowUp, int maxFollowUp) {
        StringBuilder task = new StringBuilder();

        // 注入长期记忆（如果有，仅简历面试）
        if (longTermContext != null && !longTermContext.isEmpty()) {
            task.append(longTermContext).append("\n");
        }

        task.append("【候选人回答】\n").append(userAnswer).append("\n");

        if (currentQuestion != null) {
            task.append("\n【当前题目信息】\n");
            task.append("- 题目：").append(currentQuestion.getTitle()).append("\n");
            task.append("- 分类：").append(currentQuestion.getCategory()).append("\n");
            task.append("- 难度：").append(currentQuestion.getDifficulty()).append("\n");
            // 注意：不注入参考答案（currentQuestion.getAnswer()），避免 AI 在候选人说"不知道"时
            // 把参考答案泄露出去。AI 凭自身知识足以评估回答质量。
            task.append("- 进度：第 ").append(questionIndex + 1).append(" 题 / 共 ").append(totalQuestions).append(" 题\n");
            task.append("- 本题已追问 ").append(currentFollowUp).append(" 次 / 最多 ").append(maxFollowUp).append(" 次\n");
            if (questionIndex + 1 >= totalQuestions) {
                task.append("- ⚠️ 当前是最后一题（第 ").append(questionIndex + 1).append(" 题 / 共 ")
                   .append(totalQuestions).append(" 题），答完后必须结束面试（type 设为 \"end\"），")
                   .append("绝对不能说\"进入下一题\"\"继续下一题\"\"下一题\"——因为没有下一题了\n");
            }
        }

        // 注入当前面试难度级别
        task.append("\n【当前面试难度】").append(currentDifficulty).append("\n");

        // 注入面试类型
        task.append("\n【面试类型】").append("resume".equals(interviewType) ? "简历面试" : "普通面试").append("\n");
        if ("normal".equals(interviewType)) {
            task.append("注意：这是普通面试，不要引用候选人的简历内容，只基于题目本身提问。\n");
        }

        // 每 2 道题提醒 Agent 评估难度
        if (questionIndex > 0 && questionIndex % 2 == 0) {
            task.append("\n【难度评估提醒】已完成 ").append(questionIndex).append(" 题，请评估候选人表现并考虑是否需要调整难度。\n");
        }

        task.append("\n请评估候选人的回答，决定下一步行动。");

        return task.toString();
    }

    /**
     * 注入工具上下文参数（userId, sessionId）
     * 这些参数不是 LLM 传入的，而是系统自动注入
     */
    private void injectToolContext(Long sessionId, Long userId) {
        // 通过 ThreadLocal 传递上下文参数给工具
        ToolContext.setSessionId(sessionId);
        ToolContext.setUserId(userId);
    }

    @Override
    protected String buildSystemPrompt() {
        return """
                你是一位经验丰富的技术面试官，正在对候选人进行面试。

                【核心原则】
                你是在面试，不是在培训。你的目标是考察候选人的真实能力，而不是教他知识点。

                【回复风格】
                - 保持面试官的专业语气
                - 回复简洁有力，每次不超过 200 字
                - 你的回复内容就是候选人看到的内容，不要包含任何元数据标记
                - 你不需要在回复中包含【决策: xxx】标记，系统会自动处理

                【绝对禁止规则 — 面试不是培训】
                无论何种情况（包括候选人说"不知道"、答错、答得不好、主动询问答案），你都绝对禁止在回复中：
                - 给出当前题目的正确答案或参考答案
                - 讲解相关知识点、原理、机制（例如"Spring事务传播行为有7种，最常用的是REQUIRED..."这种内容绝对禁止）
                - 用"我来给你总结""顺便说一下""其实答案是"等话术教学
                正确的做法：只问不教，只评不讲。泄露答案会破坏后续题目的区分度，是严重的面试事故。

                【追问规则】
                - 当候选人回答不够深入或有遗漏时，用追问来引导他思考
                - 追问方式：提出引导性问题，而不是直接给答案
                - 例如："你刚才提到了X，能再展开说说Y吗？" 或 "那如果是Z场景呢？"
                - 注意【候选人的回答】中会标注"本题追问次数"，你必须遵守追问次数上限，达到上限后必须进入下一题

                【候选人不会回答时的处理】
                如果候选人说"不知道"、"不清楚"、"不会"、"没了解过"等表示不了解：
                - 不要追问关于这个话题的问题
                - 绝对禁止给出正确答案、知识点讲解、原理说明或任何教学性内容
                - 简短过渡即可，例如："没关系，这个知识点确实有点偏，我们换个话题"
                - 然后直接进入下一题（type 设为 "next"），或在最后一题时结束面试（type 设为 "end"）
                - 即使候选人主动问"那答案是什么？""能讲讲吗？"，也要委婉拒绝："这个我们面试结束后可以再交流"

                【自适应难度规则】
                每完成 2 道题后，评估候选人表现：
                - 正确率 >= 80%：调用 difficulty_adjust 工具将难度提升一档
                - 正确率 <= 40%：调用 difficulty_adjust 工具将难度降低一档
                - 其他情况：保持当前难度
                评估时综合考虑回答的准确性、完整性和深度。
                """;
    }

    // ======================== 工具上下文 ========================

    /**
     * 工具上下文（ThreadLocal）
     * 用于向工具传递 sessionId/userId 等系统参数
     */
    public static class ToolContext {
        private static final ThreadLocal<Long> SESSION_ID = new ThreadLocal<>();
        private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

        public static void setSessionId(Long sessionId) { SESSION_ID.set(sessionId); }
        public static Long getSessionId() { return SESSION_ID.get(); }
        public static void setUserId(Long userId) { USER_ID.set(userId); }
        public static Long getUserId() { return USER_ID.get(); }

        public static void clear() {
            SESSION_ID.remove();
            USER_ID.remove();
        }
    }
}

