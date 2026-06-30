package org.backend.agent.tool;

import org.backend.agent.InterviewerAgent;
import org.backend.dto.WrongAnswerDTO;
import org.backend.service.MistakeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 错题记录工具
 * Agent 在面试中发现候选人回答错误时，自动记录到错题本
 */
@Component
public class MistakeRecordTool implements Tool {

    private static final Logger logger = LoggerFactory.getLogger(MistakeRecordTool.class);

    private final MistakeService mistakeService;

    public MistakeRecordTool(MistakeService mistakeService) {
        this.mistakeService = mistakeService;
    }

    @Override
    public String getName() {
        return "mistake_record";
    }

    @Override
    public String getDescription() {
        return "记录候选人的错误回答到错题本。当候选人回答有明显错误或关键遗漏时使用。";
    }

    @Override
    public String getParametersSchema() {
        return "{\"questionId\": \"题目ID(必填)\", \"userAnswer\": \"候选人的回答摘要(必填)\", " +
               "\"comment\": \"错误原因分析(必填)\", \"category\": \"题目分类(可选)\"}";
    }

    @Override
    public String execute(Map<String, Object> params) {
        // 从 ToolContext 获取系统参数（InterviewerAgent 自动注入）
        Long userId = InterviewerAgent.ToolContext.getUserId();
        Long sessionId = InterviewerAgent.ToolContext.getSessionId();
        // 也支持从 params 传入（兼容直接调用）
        if (userId == null && params.get("userId") instanceof Number) {
            userId = ((Number) params.get("userId")).longValue();
        }
        if (sessionId == null && params.get("sessionId") instanceof Number) {
            sessionId = ((Number) params.get("sessionId")).longValue();
        }

        Object questionIdObj = params.get("questionId");
        String userAnswer = (String) params.get("userAnswer");
        String comment = (String) params.get("comment");
        String category = (String) params.get("category");

        if (questionIdObj == null) {
            return "错误：缺少 questionId 参数";
        }
        if (userAnswer == null || userAnswer.isBlank()) {
            return "错误：缺少 userAnswer 参数";
        }
        if (comment == null || comment.isBlank()) {
            return "错误：缺少 comment 参数";
        }

        Long questionId = questionIdObj instanceof Number
                ? ((Number) questionIdObj).longValue() : Long.parseLong(questionIdObj.toString());

        try {
            WrongAnswerDTO wa = new WrongAnswerDTO();
            wa.setQuestionId(questionId);
            wa.setUserAnswer(userAnswer.length() > 500 ? userAnswer.substring(0, 500) : userAnswer);
            wa.setAiComment(comment);
            wa.setCategory(category != null ? category : "general");

            List<WrongAnswerDTO> list = new ArrayList<>();
            list.add(wa);

            if (userId != null && sessionId != null) {
                mistakeService.batchImport(userId, sessionId, list);
                logger.info("MistakeRecordTool: 记录错题 userId={}, questionId={}", userId, questionId);
                return "已记录到错题本。";
            } else {
                return "错题信息已收集（userId/sessionId 缺失，将在面试结束后统一导入）。";
            }
        } catch (Exception e) {
            logger.error("MistakeRecordTool 执行失败", e);
            return "错题记录失败：" + e.getMessage();
        }
    }
}
