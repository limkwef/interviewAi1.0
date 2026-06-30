package org.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.backend.common.Result;
import org.backend.vo.LearningPathVO;
import org.backend.vo.ProgressStatsVO;
import org.backend.service.LearningPathService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/learning-path")
public class LearningPathController extends BaseController {

    @Autowired
    private LearningPathService learningPathService;

    /**
     * 获取当前学习路径
     */
    @GetMapping
    public Result<LearningPathVO> getLearningPath(HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        LearningPathVO result = learningPathService.getLearningPath(userId);
        return Result.success(result);
    }

    /**
     * 从最新诊断报告刷新学习计划
     */
    @PostMapping("/refresh")
    public Result<LearningPathVO> refreshLearningPath(HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        try {
            LearningPathVO result = learningPathService.refreshFromDiagnosis(userId);
            return Result.success("学习计划已更新", result);
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 标记任务完成
     */
    @PutMapping("/tasks/complete")
    public Result markTaskComplete(@RequestBody Map<String, Integer> body, HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        Integer phaseIndex = body.get("phaseIndex");
        Integer taskIndex = body.get("taskIndex");

        if (phaseIndex == null || taskIndex == null) {
            return Result.error(400, "参数不完整");
        }

        learningPathService.markTaskComplete(userId, phaseIndex, taskIndex);
        return Result.success("任务已标记完成");
    }

    /**
     * 取消任务完成标记
     */
    @PutMapping("/tasks/uncomplete")
    public Result unmarkTaskComplete(@RequestBody Map<String, Integer> body, HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        Integer phaseIndex = body.get("phaseIndex");
        Integer taskIndex = body.get("taskIndex");

        if (phaseIndex == null || taskIndex == null) {
            return Result.error(400, "参数不完整");
        }

        learningPathService.unmarkTaskComplete(userId, phaseIndex, taskIndex);
        return Result.success("已取消完成标记");
    }

    /**
     * 获取学习进度统计（给首页用）
     */
    @GetMapping("/stats")
    public Result<ProgressStatsVO> getProgressStats(HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        ProgressStatsVO stats = learningPathService.getProgressStats(userId);
        return Result.success(stats);
    }
}
