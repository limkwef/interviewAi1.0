package org.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.backend.common.Result;
import org.backend.entity.DiagnosisReport;
import org.backend.service.AIDiagnosisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/diagnosis")
public class DiagnosisController extends BaseController {

    @Autowired
    private AIDiagnosisService diagnosisService;

    /**
     * 生成 AI 诊断报告（面试结束后自动调用，也可手动触发）
     */
    @PostMapping("/generate/{sessionId}")
    public Result generateDiagnosis(@PathVariable Long sessionId, HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        DiagnosisReport report = diagnosisService.generateDiagnosisReport(sessionId);
        return Result.success(report);
    }

    /**
     * 查看某次面试的诊断报告
     */
    @GetMapping("/{id}")
    public Result getDiagnosis(@PathVariable Long id, HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        DiagnosisReport report = diagnosisService.findById(id);
        if (report == null) {
            return Result.error(404, "未找到诊断报告");
        }
        if (!report.getUserId().equals(userId)) {
            return Result.error(403, "无权查看此报告");
        }
        return Result.success(report);
    }

    /**
     * 根据面试ID查看诊断报告
     */
    @GetMapping("/session/{sessionId}")
    public Result getDiagnosisBySession(@PathVariable Long sessionId, HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        DiagnosisReport report = diagnosisService.findBySessionId(sessionId);
        if (report == null) {
            return Result.error(404, "未找到诊断报告");
        }
        if (!report.getUserId().equals(userId)) {
            return Result.error(403, "无权查看此报告");
        }
        return Result.success(report);
    }

    /**
     * 诊断报告历史列表
     */
    @GetMapping("/history")
    public Result getDiagnosisHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        Map<String, Object> result = diagnosisService.getDiagnosisHistory(userId, page, pageSize);
        return Result.success(result);
    }

    /**
     * 获取最新的诊断报告
     */
    @GetMapping("/latest")
    public Result getLatestDiagnosis(HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        DiagnosisReport report = diagnosisService.getLatestDiagnosis(userId);
        return Result.success(report);
    }
}
