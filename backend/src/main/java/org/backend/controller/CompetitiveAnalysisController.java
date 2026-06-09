package org.backend.controller;

import org.backend.common.Result;
import org.backend.entity.CompetitiveAnalysis;
import org.backend.service.CompetitiveAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/competitive-analysis")
public class CompetitiveAnalysisController extends BaseController {

    @Autowired
    private CompetitiveAnalysisService competitiveAnalysisService;

    /**
     * 获取竞争力分析（有缓存直接返回，无缓存则自动生成）
     */
    @GetMapping
    public Result getAnalysis(HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        try {
            CompetitiveAnalysis analysis = competitiveAnalysisService.getAnalysis(userId);
            return Result.success(analysis);
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 强制刷新竞争力分析（重新调用 AI 生成）
     */
    @PostMapping("/refresh")
    public Result refreshAnalysis(HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        try {
            CompetitiveAnalysis analysis = competitiveAnalysisService.refreshAnalysis(userId);
            return Result.success("竞争力分析已更新", analysis);
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 基于某次面试的竞争力分析（用于诊断页内嵌，按 position+round 精确对标）
     */
    @GetMapping("/session/{sessionId}")
    public Result getAnalysisForSession(@PathVariable Long sessionId, HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        try {
            CompetitiveAnalysis analysis = competitiveAnalysisService.getAnalysisForSession(userId, sessionId);
            return Result.success(analysis);
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }
}
