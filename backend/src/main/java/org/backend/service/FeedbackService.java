package org.backend.service;

import org.backend.entity.Feedback;
import org.backend.exception.BusinessException;
import org.backend.mapper.FeedbackMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FeedbackService {

    private static final Logger logger = LoggerFactory.getLogger(FeedbackService.class);

    private final FeedbackMapper feedbackMapper;

    public FeedbackService(FeedbackMapper feedbackMapper) {
        this.feedbackMapper = feedbackMapper;
    }

    public void submitFeedback(Long userId, String type, String content, String contact) {
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException("反馈内容不能为空");
        }

        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setType(type);
        feedback.setContent(content.trim());
        feedback.setContact(contact != null ? contact.trim() : "");

        feedbackMapper.insert(feedback);
        logger.info("用户{}提交反馈，类型：{}", userId, type);
    }
}
