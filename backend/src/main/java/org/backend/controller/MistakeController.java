package org.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.backend.common.Result;
import org.backend.entity.CheckAnswerVO;
import org.backend.entity.MistakeDetailVO;
import org.backend.entity.MistakeListVO;
import org.backend.entity.MistakeStatsVO;
import org.backend.service.MistakeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mistakes")
public class MistakeController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(MistakeController.class);

    private final MistakeService mistakeService;

    public MistakeController(MistakeService mistakeService) {
        this.mistakeService = mistakeService;
    }

    /**
     * 获取错题列表（分页+筛选）
     */
    @GetMapping
    public Result<MistakeListVO> list(
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        Long userId = getUserIdFromToken(request);
        MistakeListVO data = mistakeService.getMistakeList(userId, page, size,
                category, difficulty, status, keyword);
        return Result.success(data);
    }

    /**
     * 获取错题统计
     */
    @GetMapping("/stats")
    public Result<MistakeStatsVO> stats(HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        MistakeStatsVO data = mistakeService.getStats(userId);
        return Result.success(data);
    }

    /**
     * 获取错题详情
     */
    @GetMapping("/{id}/details")
    public Result<MistakeDetailVO> detail(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserIdFromToken(request);
        MistakeDetailVO data = mistakeService.getDetail(id, userId);
        return Result.success(data);
    }

    /**
     * 标记为已掌握
     */
    @PutMapping("/{id}/master")
    public Result<Void> markAsMastered(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserIdFromToken(request);
        mistakeService.markAsMastered(id, userId);
        return Result.success("已标记为掌握", null);
    }

    /**
     * 重置为待复习
     */
    @PutMapping("/{id}/reset")
    public Result<Void> resetToPending(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserIdFromToken(request);
        mistakeService.resetToPending(id, userId);
        return Result.success("已重置为待复习", null);
    }

    /**
     * 移出错题本
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserIdFromToken(request);
        mistakeService.deleteById(id, userId);
        return Result.success("已移出错题本", null);
    }

    /**
     * 获取重做题目列表
     */
    @GetMapping("/review/questions")
    public Result<List<Map<String, Object>>> reviewQuestions(
            HttpServletRequest request,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String difficulty,
            @RequestParam(defaultValue = "10") Integer count,
            @RequestParam(required = false) String ids) {
        Long userId = getUserIdFromToken(request);
        List<Map<String, Object>> data = mistakeService.getReviewQuestions(userId, category, difficulty, count, ids);
        return Result.success(data);
    }

    /**
     * 检查答案
     */
    @PostMapping("/review/check")
    public Result<CheckAnswerVO> checkAnswer(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        Long userId = getUserIdFromToken(request);
        Long questionId = body.get("questionId") != null ? ((Number) body.get("questionId")).longValue() : null;
        String userAnswer = (String) body.get("userAnswer");

        if (questionId == null) {
            return Result.error(400, "题目ID不能为空");
        }
        if (userAnswer == null || userAnswer.trim().isEmpty()) {
            return Result.error(400, "答案不能为空");
        }

        CheckAnswerVO data = mistakeService.checkAnswer(questionId, userAnswer.trim());
        return Result.success(data);
    }
}
