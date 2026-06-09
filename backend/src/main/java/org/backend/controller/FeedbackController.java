package org.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.backend.common.Result;
import org.backend.service.FeedbackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(FeedbackController.class);

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public Result<?> submit(HttpServletRequest request, @RequestBody Map<String, String> body) {
        Long userId = getUserIdFromToken(request);
        String type = body.get("type");
        String content = body.get("content");
        String contact = body.get("contact");

        if (type == null || type.trim().isEmpty()) {
            type = "其他";
        }

        feedbackService.submitFeedback(userId, type, content, contact);
        return Result.success("感谢您的反馈", null);
    }
}
