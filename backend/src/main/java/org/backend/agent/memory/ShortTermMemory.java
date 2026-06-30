package org.backend.agent.memory;

import org.backend.service.InterviewContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Agent 短期记忆
 * 包装现有的 InterviewContextService，提供统一的记忆接口
 */
@Component
public class ShortTermMemory {

    private static final Logger logger = LoggerFactory.getLogger(ShortTermMemory.class);

    private final InterviewContextService contextService;

    public ShortTermMemory(InterviewContextService contextService) {
        this.contextService = contextService;
    }

    /** 获取面试上下文 */
    public List<Map<String, String>> getContext(Long sessionId) {
        return contextService.getContext(sessionId);
    }

    /** 追加用户消息 */
    public void addUserMessage(Long sessionId, String content) {
        contextService.addMessage(sessionId, "user", content);
    }

    /** 追加助手消息 */
    public void addAssistantMessage(Long sessionId, String content) {
        contextService.addMessage(sessionId, "assistant", content);
    }

    /** 检查上下文是否存在 */
    public boolean hasContext(Long sessionId) {
        return contextService.hasContext(sessionId);
    }
}
