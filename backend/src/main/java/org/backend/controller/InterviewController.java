package org.backend.controller;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.backend.common.Result;
import org.backend.entity.EndInterviewVO;
import org.backend.entity.InterviewCreateVO;
import org.backend.entity.InterviewHistoryVO;
import org.backend.entity.InterviewMessage;
import org.backend.entity.PollStatusVO;
import org.backend.entity.SendMessageVO;
import org.backend.entity.SessionInfoVO;
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
    public Result<InterviewCreateVO> create(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        Long userId = getUserIdFromToken(request);
        String position = (String) body.get("position");
        String round = (String) body.get("round");
        String difficulty = (String) body.get("difficulty");
        int questionCount = body.get("questionCount") != null ? ((Number) body.get("questionCount")).intValue() : 5;

        if (position == null || position.isEmpty()) {
            return Result.error(400, "请选择岗位方向");
        }
        if (round == null || round.isEmpty()) {
            return Result.error(400, "请选择面试轮次");
        }
        if (difficulty == null || difficulty.isEmpty()) {
            return Result.error(400, "请选择难度等级");
        }

        InterviewCreateVO data = interviewService.createInterview(userId, position, round, difficulty, questionCount);
        return Result.success("面试创建成功", data);
    }

    @PostMapping("/{id}/message")
    public Result<SendMessageVO> sendMessage(HttpServletRequest request, @PathVariable Long id, @RequestBody Map<String, String> body) {
        Long userId = getUserIdFromToken(request);
        String content = body.get("content");
        if (content == null || content.trim().isEmpty()) {
            return Result.error(400, "消息内容不能为空");
        }
        SendMessageVO data = interviewService.sendMessage(id, userId, content.trim());
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

    @PostMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Long userId = getUserIdFromToken(request);
        String content = body.get("content");
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException(400, "消息内容不能为空");
        }

        SseEmitter emitter = new SseEmitter(300000L);

        sseExecutor.execute(() -> {
            try {
                Map<String, Object> data = interviewService.sendMessageStream(id, userId, content.trim(), emitter);
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
