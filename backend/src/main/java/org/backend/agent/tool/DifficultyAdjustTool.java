package org.backend.agent.tool;

import org.backend.agent.InterviewerAgent;
import org.backend.entity.InterviewSession;
import org.backend.mapper.InterviewSessionMapper;
import org.backend.service.QuestionSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 难度调整工具
 * Agent 根据候选人表现，动态调整后续题目的难度
 * 调整后会更新数据库中 session 的难度级别
 */
@Component
public class DifficultyAdjustTool implements Tool {

    private static final Logger logger = LoggerFactory.getLogger(DifficultyAdjustTool.class);

    private final QuestionSelector questionSelector;
    private final InterviewSessionMapper sessionMapper;

    public DifficultyAdjustTool(QuestionSelector questionSelector, InterviewSessionMapper sessionMapper) {
        this.questionSelector = questionSelector;
        this.sessionMapper = sessionMapper;
    }

    @Override
    public String getName() {
        return "difficulty_adjust";
    }

    @Override
    public String getDescription() {
        return "调整后续题目的难度。当候选人表现明显好于预期（正确率>=80%）时提升难度，" +
               "或表现明显差于预期（正确率<=40%）时降低难度。";
    }

    @Override
    public String getParametersSchema() {
        return "{\"direction\": \"调整方向(必填): up/down/keep\", " +
               "\"reason\": \"调整原因(必填)\", " +
               "\"currentDifficulty\": \"当前难度(可选): easy/medium/hard\"}";
    }

    @Override
    public String execute(Map<String, Object> params) {
        String direction = (String) params.get("direction");
        String reason = (String) params.get("reason");
        String currentDifficulty = (String) params.get("currentDifficulty");

        if (direction == null || direction.isBlank()) {
            return "错误：缺少 direction 参数";
        }
        if (reason == null || reason.isBlank()) {
            return "错误：缺少 reason 参数";
        }

        String effectiveDifficulty = currentDifficulty != null ? currentDifficulty : "medium";
        String newDifficulty;

        switch (direction.toLowerCase()) {
            case "up" -> {
                newDifficulty = switch (effectiveDifficulty) {
                    case "easy" -> "medium";
                    case "medium" -> "hard";
                    default -> "hard";
                };
            }
            case "down" -> {
                newDifficulty = switch (effectiveDifficulty) {
                    case "hard" -> "medium";
                    case "medium" -> "easy";
                    default -> "easy";
                };
            }
            case "keep" -> {
                newDifficulty = effectiveDifficulty;
            }
            default -> {
                return "错误：direction 必须是 up/down/keep";
            }
        }

        String result = String.format(
                "难度调整决策：\n- 方向：%s\n- 原因：%s\n- 原难度：%s → 新难度：%s\n\n" +
                "后续面试请按%s难度标准进行追问和评价。",
                direction, reason, effectiveDifficulty, newDifficulty, newDifficulty);

        // 更新数据库中的难度
        if (!newDifficulty.equals(effectiveDifficulty)) {
            try {
                Long sessionId = InterviewerAgent.ToolContext.getSessionId();
                if (sessionId != null) {
                    InterviewSession session = sessionMapper.findById(sessionId);
                    if (session != null) {
                        session.setDifficulty(newDifficulty);
                        sessionMapper.updateDifficulty(sessionId, newDifficulty);
                        logger.info("DifficultyAdjustTool: 已更新数据库 sessionId={}, difficulty={}", sessionId, newDifficulty);
                    }
                }
            } catch (Exception e) {
                logger.error("DifficultyAdjustTool: 更新数据库失败", e);
            }
        }

        logger.info("DifficultyAdjustTool: {} → {}, 原因: {}", effectiveDifficulty, newDifficulty, reason);
        return result;
    }
}
