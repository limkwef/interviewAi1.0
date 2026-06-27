package org.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.backend.entity.InterviewMessage;
import org.backend.entity.Question;
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

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired(required = false)
    private CacheService cacheService;

    private boolean isRedisAvailable() {
        return redisTemplate != null;
    }

    /**
     * 面试开始时：初始化上下文
     */
    public void initContext(Long sessionId, String systemPrompt) {
        if (!isRedisAvailable()) {
            logger.warn("Redis 不可用，跳过初始化上下文 sessionId={}", sessionId);
            return;
        }
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
        if (!isRedisAvailable()) return;
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
        if (!isRedisAvailable()) return List.of();
        try {
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
        } catch (Exception e) {
            logger.warn("Redis 读取上下文失败，sessionId={}", sessionId, e);
            return List.of();
        }
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
        if (isRedisAvailable()) {
            try { redisTemplate.delete(CONTEXT_KEY + sessionId); } catch (Exception ignored) {}
        }
        logger.info("面试上下文已持久化并清理，sessionId={}", sessionId);
    }

    /**
     * 清理面试上下文（不持久化）
     */
    public void clear(Long sessionId) {
        if (!isRedisAvailable()) return;
        try {
            redisTemplate.delete(CONTEXT_KEY + sessionId);
        } catch (Exception e) {
            logger.warn("Redis 清理失败，sessionId={}", sessionId, e);
        }
        logger.info("面试上下文已清理，sessionId={}", sessionId);
    }

    // ======================== 追问次数限制 ========================

    private static final String FOLLOWUP_KEY = "interview:followup:";

    /**
     * 获取当前题目的追问次数
     * 优先从 Redis 读取，降级时从 MySQL 消息表计算
     */
    public int getFollowUpCount(Long sessionId, int questionIndex, InterviewMessageMapper messageMapper) {
        // 1. 优先 Redis
        try {
            String key = FOLLOWUP_KEY + sessionId + ":" + questionIndex;
            Integer count = (Integer) redisTemplate.opsForValue().get(key);
            if (count != null) {
                logger.info("从 Redis 获取追问次数：sessionId={}, questionIndex={}, count={}", sessionId, questionIndex, count);
                return count;
            }
        } catch (Exception e) {
            logger.warn("Redis 获取追问次数失败，降级到 MySQL，sessionId={}, questionIndex={}", sessionId, questionIndex, e);
        }

        // 2. Redis 降级：从 MySQL 消息表计算追问次数
        try {
            List<InterviewMessage> messages = messageMapper.findBySessionId(sessionId);
            int followUpCount = 0;
            for (InterviewMessage msg : messages) {
                if ("ai".equals(msg.getRole()) 
                    && "follow_up".equals(msg.getMessageType())
                    && Integer.valueOf(questionIndex).equals(msg.getQuestionIndex())) {
                    followUpCount++;
                }
            }
            logger.info("从 MySQL 计算追问次数：sessionId={}, questionIndex={}, count={}", sessionId, questionIndex, followUpCount);
            // 写回 Redis（下次直接命中）
            try {
                String key = FOLLOWUP_KEY + sessionId + ":" + questionIndex;
                redisTemplate.opsForValue().set(key, followUpCount, CONTEXT_TTL_HOURS, TimeUnit.HOURS);
            } catch (Exception ignored) {}
            return followUpCount;
        } catch (Exception e) {
            logger.error("MySQL 计算追问次数失败", e);
            return 0;
        }
    }

    /**
     * 追问次数 +1
     */
    public void incrementFollowUp(Long sessionId, int questionIndex) {
        try {
            String key = FOLLOWUP_KEY + sessionId + ":" + questionIndex;
            Long newValue = redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, CONTEXT_TTL_HOURS, TimeUnit.HOURS);
            logger.info("追问次数递增：sessionId={}, questionIndex={}, newValue={}", sessionId, questionIndex, newValue);
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
        if (!isRedisAvailable()) return;
        try {
            String key = RECENT_QUESTIONS_KEY + userId;
        for (Long id : questionIds) {
            // 先移除已存在的（去重），再左推
            redisTemplate.opsForList().remove(key, 1, id);
            redisTemplate.opsForList().leftPush(key, id);
        }
        // 只保留最新的 MAX_RECENT_QUESTIONS 条
        redisTemplate.opsForList().trim(key, 0, MAX_RECENT_QUESTIONS - 1);
        redisTemplate.expire(key, RECENT_QUESTIONS_TTL_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            logger.warn("Redis 记录抽题记录失败", e);
        }
    }

    /**
     * 获取用户近期做过的题目 ID 集合
     */
    public Set<Long> getRecentQuestionIds(Long userId) {
        if (!isRedisAvailable()) return new HashSet<>();
        try {
            String key = RECENT_QUESTIONS_KEY + userId;
        List<Object> members = redisTemplate.opsForList().range(key, 0, -1);
        if (members == null || members.isEmpty()) return new HashSet<>();
            return members.stream()
                    .map(o -> o instanceof Number ? ((Number) o).longValue() : Long.valueOf(o.toString()))
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            logger.warn("Redis 获取抽题记录失败", e);
            return new HashSet<>();
        }
    }

    /**
     * 检查上下文是否存在
     */
    public boolean hasContext(Long sessionId) {
        if (!isRedisAvailable()) return false;
        try {
            String key = CONTEXT_KEY + sessionId;
            Long size = redisTemplate.opsForList().size(key);
            return size != null && size > 0;
        } catch (Exception e) {
            logger.warn("Redis hasContext 失败", e);
            return false;
        }
    }

    // ======================== 面试题目缓存（避免重复查询） ========================

    private static final String QUESTIONS_CACHE_KEY = "interview:questions:";

    /**
     * 缓存面试题目列表
     */
    public void cacheQuestions(Long sessionId, List<org.backend.entity.Question> questions) {
        try {
            String key = QUESTIONS_CACHE_KEY + sessionId;
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(questions), CONTEXT_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            logger.warn("缓存面试题目失败，sessionId={}", sessionId, e);
        }
    }

    /**
     * 获取缓存的面试题目列表
     */
    @SuppressWarnings("unchecked")
    public List<org.backend.entity.Question> getCachedQuestions(Long sessionId) {
        try {
            String key = QUESTIONS_CACHE_KEY + sessionId;
            String json = (String) redisTemplate.opsForValue().get(key);
            if (json != null) {
                return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, org.backend.entity.Question.class));
            }
        } catch (Exception e) {
            logger.warn("读取缓存题目失败，sessionId={}", sessionId, e);
        }
        return null;
    }

    /**
     * 清除题目缓存
     */
    public void clearQuestionsCache(Long sessionId) {
        try {
            redisTemplate.delete(QUESTIONS_CACHE_KEY + sessionId);
        } catch (Exception e) {
            logger.warn("清除题目缓存失败，sessionId={}", sessionId, e);
        }
    }

    // ======================== 全量会话缓存（避免重复查 MySQL） ========================

    private static final String SESSION_CACHE_KEY = "interview:session:";

    /**
     * 缓存整个 InterviewSession 对象
     */
    public void cacheSession(org.backend.entity.InterviewSession session) {
        try {
            String key = SESSION_CACHE_KEY + session.getId();
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(session), CONTEXT_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            logger.warn("缓存会话失败，sessionId={}", session.getId(), e);
        }
    }

    /**
     * 获取缓存的 InterviewSession
     */
    public org.backend.entity.InterviewSession getCachedSession(Long sessionId) {
        try {
            String key = SESSION_CACHE_KEY + sessionId;
            String json = (String) redisTemplate.opsForValue().get(key);
            if (json != null) {
                return objectMapper.readValue(json, org.backend.entity.InterviewSession.class);
            }
        } catch (Exception e) {
            logger.warn("读取缓存会话失败，sessionId={}", sessionId, e);
        }
        return null;
    }

    /**
     * 更新缓存中的会话字段（部分更新，避免全量覆写）
     */
    public void updateCachedSession(Long sessionId, String field, Object value) {
        try {
            org.backend.entity.InterviewSession cached = getCachedSession(sessionId);
            if (cached != null) {
                switch (field) {
                    case "currentQuestion" -> cached.setCurrentQuestion((Integer) value);
                    case "status" -> cached.setStatus((String) value);
                    case "evaluateStatus" -> cached.setEvaluateStatus((String) value);
                    case "evaluateError" -> cached.setEvaluateError((String) value);
                    case "reportId" -> cached.setReportId((Long) value);
                    case "evaluateRetryCount" -> cached.setEvaluateRetryCount((Integer) value);
                    default -> { return; }
                }
                cacheSession(cached);
            }
        } catch (Exception e) {
            logger.warn("更新缓存会话失败，sessionId={}, field={}", sessionId, field, e);
        }
    }

    /**
     * 清除会话缓存
     */
    public void clearSessionCache(Long sessionId) {
        try {
            redisTemplate.delete(SESSION_CACHE_KEY + sessionId);
        } catch (Exception e) {
            logger.warn("清除会话缓存失败，sessionId={}", sessionId, e);
        }
    }

    /**
     * 面试结束时清理所有相关缓存
     */
    public void clearAllInterviewCache(Long sessionId) {
        clear(sessionId);
        clearQuestionsCache(sessionId);
        clearSessionCache(sessionId);
        // 清除追问计数（使用 SCAN 替代 KEYS，避免阻塞 Redis）
        if (cacheService != null) {
            cacheService.deleteByPrefix("interview:followup:" + sessionId + ":");
        }
        logger.info("已清除面试所有缓存，sessionId={}", sessionId);
    }
}
