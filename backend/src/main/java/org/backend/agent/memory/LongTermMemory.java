package org.backend.agent.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.backend.entity.AgentMemory;
import org.backend.entity.InterviewMessage;
import org.backend.mapper.AgentMemoryMapper;
import org.backend.mapper.InterviewMessageMapper;
import org.backend.util.AIService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Agent 长期记忆
 * 负责跨面试的经验积累（反思）和复用（召回）
 */
@Component
public class LongTermMemory {

    private static final Logger logger = LoggerFactory.getLogger(LongTermMemory.class);

    private final AgentMemoryMapper memoryMapper;
    private final AIService aiService;
    private final InterviewMessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    public LongTermMemory(AgentMemoryMapper memoryMapper, AIService aiService,
                          InterviewMessageMapper messageMapper,
                          ObjectMapper objectMapper) {
        this.memoryMapper = memoryMapper;
        this.aiService = aiService;
        this.messageMapper = messageMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 召回：加载用户的历史面试经验
     * 在新面试开始时调用，注入到 Agent 上下文中
     */
    public String recall(Long userId) {
        if (userId == null) return "";

        try {
            List<AgentMemory> memories = memoryMapper.findRecentByUserId(userId, 10);
            if (memories.isEmpty()) return "";

            // 更新访问次数
            for (AgentMemory mem : memories) {
                try {
                    memoryMapper.incrementAccessCount(mem.getId());
                } catch (Exception ignored) {}
            }

            StringBuilder sb = new StringBuilder();
            sb.append("【历史面试经验】\n");
            for (AgentMemory mem : memories) {
                sb.append("- [").append(mem.getMemoryType()).append("] ")
                  .append(mem.getContent()).append("\n");
            }
            sb.append("请参考以上信息调整本次面试策略。\n");

            logger.info("召回用户{}的{}条历史记忆", userId, memories.size());
            return sb.toString();
        } catch (Exception e) {
            logger.error("召回记忆失败, userId={}", userId, e);
            return "";
        }
    }

    /**
     * 反思：面试结束后，调用 LLM 总结本次面试经验
     * 异步调用，不阻塞主流程
     */
    public void reflect(Long userId, Long sessionId) {
        if (userId == null || sessionId == null) return;

        try {
            // 1. 从数据库获取本次面试对话
            List<InterviewMessage> messages = messageMapper.findBySessionId(sessionId);
            if (messages == null || messages.isEmpty()) {
                logger.warn("面试对话为空，跳过反思, sessionId={}", sessionId);
                return;
            }

            // 2. 构建反思 prompt
            StringBuilder conversationText = new StringBuilder();
            for (InterviewMessage msg : messages) {
                String role = "user".equals(msg.getRole()) ? "候选人" : "面试官";
                conversationText.append(role).append(": ").append(msg.getContent()).append("\n");
            }

            String prompt = "以下是一段技术面试对话，请分析并总结：\n\n" +
                    conversationText.toString() + "\n\n" +
                    "请以 JSON 数组格式返回分析结果，每条包含 type 和 content 字段：\n" +
                    "- type=\"strength\": 候选人的强项\n" +
                    "- type=\"weakness\": 候选人的弱项\n" +
                    "- type=\"strategy\": 下次面试的策略建议\n\n" +
                    "示例：[{\"type\":\"strength\",\"content\":\"MySQL索引理解扎实\"},{\"type\":\"weakness\",\"content\":\"Redis集群概念模糊\"}]\n\n" +
                    "只返回 JSON 数组，不要有其他文字。";

            // 3. 调用 LLM（直接用 chat 获取文本，手动解析 JSON 数组）
            List<Map<String, String>> llmMessages = List.of(Map.of("role", "user", "content", prompt));
            String llmResult = aiService.chat(llmMessages);
            if (llmResult == null || llmResult.isBlank()) {
                logger.warn("LLM 反思调用返回空, sessionId={}", sessionId);
                return;
            }

            // 4. 提取并解析 JSON 数组
            String jsonStr = llmResult.trim();
            // 尝试提取 JSON 数组部分
            int start = jsonStr.indexOf('[');
            int end = jsonStr.lastIndexOf(']');
            if (start >= 0 && end > start) {
                jsonStr = jsonStr.substring(start, end + 1);
            }

            List<Map<String, String>> memoriesList;
            try {
                memoriesList = objectMapper.readValue(jsonStr, new TypeReference<List<Map<String, String>>>() {});
            } catch (Exception e) {
                logger.warn("反思JSON解析失败, sessionId={}, result={}", sessionId, llmResult, e);
                return;
            }

            if (memoriesList != null && !memoriesList.isEmpty()) {
                int saved = 0;
                for (Map<String, String> item : memoriesList) {
                    String type = item.get("type");
                    String content = item.get("content");
                    if (type == null || content == null || content.isBlank()) continue;

                    AgentMemory memory = new AgentMemory();
                    memory.setUserId(userId);
                    memory.setMemoryType(type);
                    memory.setContent(content.length() > 500 ? content.substring(0, 500) : content);
                    memory.setSourceSessionId(sessionId);
                    memory.setConfidence(1.0f);

                    try {
                        memoryMapper.insert(memory);
                        saved++;
                    } catch (Exception e) {
                        logger.error("存储记忆失败", e);
                    }
                }
                logger.info("反思完成: userId={}, sessionId={}, 存储{}条记忆", userId, sessionId, saved);

                // 清理旧记忆（保留最近 50 条）
                try {
                    memoryMapper.deleteOldMemories(userId, 50);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            logger.error("反思过程异常, userId={}, sessionId={}", userId, sessionId, e);
        }
    }
}
