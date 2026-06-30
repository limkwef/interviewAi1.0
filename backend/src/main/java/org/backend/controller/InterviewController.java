package org.backend.controller;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.backend.common.Result;
import org.backend.dto.InterviewCreateRequest;
import org.backend.dto.SendMessageRequest;
import org.backend.vo.EndInterviewVO;
import org.backend.vo.InterviewCreateVO;
import org.backend.vo.InterviewHistoryVO;
import org.backend.entity.InterviewMessage;
import org.backend.vo.PollStatusVO;
import org.backend.vo.SendMessageVO;
import org.backend.vo.SessionInfoVO;
import org.backend.exception.BusinessException;
import org.backend.service.InterviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/interview")
public class InterviewController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(InterviewController.class);

    private final InterviewService interviewService;
    private final ExecutorService sseExecutor;

    public InterviewController(InterviewService interviewService,
                               @Qualifier("sseExecutor") ExecutorService sseExecutor) {
        this.interviewService = interviewService;
        this.sseExecutor = sseExecutor;
    }

    @PostMapping("/create")
    public Result<InterviewCreateVO> create(HttpServletRequest request,
                                            @Valid @RequestBody InterviewCreateRequest body) {
        Long userId = getUserIdFromToken(request);
        InterviewCreateVO data = interviewService.createInterview(
                userId, body.getPosition(), body.getRound(), body.getDifficulty(),
                body.getQuestionCount(), body.getMaxFollowUp(),
                body.getResumeId(), body.getInterviewType());
        return Result.success("面试创建成功", data);
    }

    @PostMapping("/{id}/message")
    public Result<SendMessageVO> sendMessage(HttpServletRequest request, @PathVariable Long id,
                                             @Valid @RequestBody SendMessageRequest body) {
        Long userId = getUserIdFromToken(request);
        SendMessageVO data = interviewService.sendMessage(id, userId, body.getContent().trim());
        return Result.success(data);
    }

    @PostMapping("/{id}/end")
    public Result<EndInterviewVO> endInterview(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserIdFromToken(request);
        EndInterviewVO data = interviewService.endInterview(id, userId);
        return Result.success("面试已结束", data);
    }

    @DeleteMapping("/{id}")
    public Result<Void> abandonInterview(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserIdFromToken(request);
        interviewService.abandonInterview(id, userId);
        return Result.success("面试已放弃", null);
    }

    @GetMapping("/history")
    public Result<InterviewHistoryVO> history(
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status) {
        Long userId = getUserIdFromToken(request);
        InterviewHistoryVO data = interviewService.getHistory(userId, page, pageSize);
        return Result.success(data);
    }

    @GetMapping("/{id}/messages")
    public Result<List<InterviewMessage>> messages(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserIdFromToken(request);
        List<InterviewMessage> messages = interviewService.getSessionMessages(id, userId);
        return Result.success(messages);
    }

    /**
     * SSE 降级轮询接口：前端 SSE 断开时，轮询此接口获取 AI 回复的生成状态
     */
    @GetMapping("/{id}/poll")
    public Result<PollStatusVO> pollStreamStatus(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserIdFromToken(request);
        PollStatusVO data = interviewService.pollStreamStatus(id, userId);
        return Result.success(data);
    }

    @GetMapping("/{id}/info")
    public Result<SessionInfoVO> info(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserIdFromToken(request);
        SessionInfoVO data = interviewService.getSessionInfo(id, userId);
        return Result.success(data);
    }

    /**
     * 轮询报告生成状态
     */
    @GetMapping("/{id}/report-status")
    public Result<Map<String, Object>> reportStatus(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserIdFromToken(request);
        Map<String, Object> data = interviewService.getReportStatus(id, userId);
        return Result.success(data);
    }

    @PostMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(
            HttpServletRequest request,
            @PathVariable Long id,
            @Valid @RequestBody SendMessageRequest body) {
        Long userId = getUserIdFromToken(request);

        SseEmitter emitter = new SseEmitter(300000L);

        sseExecutor.execute(() -> {
            try {
                interviewService.sendMessageStream(id, userId, body.getContent().trim(), emitter);
            } catch (Exception e) {
                logger.error("sendMessageStream failed", e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
