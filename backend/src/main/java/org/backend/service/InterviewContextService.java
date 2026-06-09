package org.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.backend.entity.InterviewMessage;
import org.backend.mapper.InterviewMessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class InterviewContextService {

    private static final Logger logger = LoggerFactory.getLogger(InterviewContextService.class);

    private static final String CONTEXT_KEY = "interview:ctx:";
    private static final long CONTEXT_TTL_HOURS = 2;

    // ======================== 用户近期抽题记录（去重用） ========================
    private static final String RECENT_QUESTIONS_KEY = "recent:q:";
    private static final int MAX_RECENT_QUESTIONS = 100;
    private static final long RECENT_QUESTIONS_TTL_DAYS = 30;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 面试开始时：初始化上下文
     */
    public void initContext(Long sessionId, String systemPrompt) {
        try {
            String key = CONTEXT_KEY + sessionId;
            Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            redisTemplate.opsForList().rightPush(key, objectMapper.writeValueAsString(systemMessage));
            redisTemplate.expire(key, CONTEXT_TTL_HOURS, TimeUnit.HOURS);
            logger.info("初始化面试上下文，sessionId={}", sessionId);
        } catch (Exception e) {
            logger.error("初始化面试上下文失败", e);
        }
    }

    /**
     * 追加一条消息
     */
    public void addMessage(Long sessionId, String role, String content) {
        try {
            String key = CONTEXT_KEY + sessionId;
            Map<String, String> message = new HashMap<>();
            message.put("role", role);
            message.put("content", content);
            redisTemplate.opsForList().rightPush(key, objectMapper.writeValueAsString(message));
        } catch (Exception e) {
            logger.error("追加消息失败", e);
        }
    }

    /**
     * 获取完整的对话上下文
     */
    public List<Map<String, String>> getContext(Long sessionId) {
        String key = CONTEXT_KEY + sessionId;
        Long size = redisTemplate.opsForList().size(key);
        if (size == null || size == 0) {
            return List.of();
        }
        List<Object> rawList = redisTemplate.opsForList().range(key, 0, -1);
        if (rawList == null) return List.of();
        List<Map<String, String>> result = new ArrayList<>();
        for (Object item : rawList) {
            try {
                result.add(objectMapper.readValue(item.toString(), new TypeReference<Map<String, String>>() {}));
            } catch (Exception e) {
                logger.error("反序列化上下文消息失败", e);
            }
        }
        return result;
    }

    /**
     * 面试结束：将上下文持久化到 MySQL 并清理 Redis
     */
    public void persistAndClear(Long sessionId, InterviewMessageMapper messageMapper) {
        List<Map<String, String>> context = getContext(sessionId);
        for (Map<String, String> msg : context) {
            if (!"system".equals(msg.get("role"))) {
                InterviewMessage record = new InterviewMessage();
                record.setSessionId(sessionId);
                record.setRole(msg.get("role"));
                record.setContent(msg.get("content"));
                messageMapper.insert(record);
            }
        }
        redisTemplate.delete(CONTEXT_KEY + sessionId);
        logger.info("面试上下文已持久化并清理，sessionId={}", sessionId);
    }

    /**
     * 清理面试上下文（不持久化）
     */
    public void clear(Long sessionId) {
        redisTemplate.delete(CONTEXT_KEY + sessionId);
        logger.info("面试上下文已清理，sessionId={}", sessionId);
    }

    // ======================== 追问次数限制 ========================

    private static final String FOLLOWUP_KEY = "interview:followup:";

    /**
     * 获取当前题目的追问次数
     */
    public int getFollowUpCount(Long sessionId, int questionIndex) {
        try {
            String key = FOLLOWUP_KEY + sessionId + ":" + questionIndex;
            Integer count = (Integer) redisTemplate.opsForValue().get(key);
            return count != null ? count : 0;
        } catch (Exception e) {
            logger.error("获取追问次数失败", e);
            return 0;
        }
    }

    /**
     * 追问次数 +1
     */
    public void incrementFollowUp(Long sessionId, int questionIndex) {
        try {
            String key = FOLLOWUP_KEY + sessionId + ":" + questionIndex;
            redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, CONTEXT_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            logger.error("递增追问次数失败", e);
        }
    }

    /**
     * 清除追问计数（跳题时调用）
     */
    public void clearFollowUp(Long sessionId, int questionIndex) {
        try {
            String key = FOLLOWUP_KEY + sessionId + ":" + questionIndex;
            redisTemplate.delete(key);
        } catch (Exception e) {
            logger.error("清除追问计数失败", e);
        }
    }

    // ======================== 用户近期抽题记录（去重用） ========================

    /**
     * 记录用户最近抽到的题目 ID（用于后续面试去重）
     * 使用 Redis List 左推 + Trim 限制数量，确保不会无限增长
     */
    public void addRecentQuestionIds(Long userId, List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) return;
        String key = RECENT_QUESTIONS_KEY + userId;
        for (Long id : questionIds) {
            // 先移除已存在的（去重），再左推
            redisTemplate.opsForList().remove(key, 1, id);
            redisTemplate.opsForList().leftPush(key, id);
        }
        // 只保留最新的 MAX_RECENT_QUESTIONS 条
        redisTemplate.opsForList().trim(key, 0, MAX_RECENT_QUESTIONS - 1);
        redisTemplate.expire(key, RECENT_QUESTIONS_TTL_DAYS, TimeUnit.DAYS);
    }

    /**
     * 获取用户近期做过的题目 ID 集合
     */
    public Set<Long> getRecentQuestionIds(Long userId) {
        String key = RECENT_QUESTIONS_KEY + userId;
        List<Object> members = redisTemplate.opsForList().range(key, 0, -1);
        if (members == null || members.isEmpty()) return new HashSet<>();
        return members.stream()
                .map(o -> o instanceof Number ? ((Number) o).longValue() : Long.valueOf(o.toString()))
                .collect(Collectors.toSet());
    }

    /**
     * 检查上下文是否存在
     */
    public boolean hasContext(Long sessionId) {
        String key = CONTEXT_KEY + sessionId;
        Long size = redisTemplate.opsForList().size(key);
        return size != null && size > 0;
    }
}
