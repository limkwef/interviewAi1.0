package org.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.backend.common.Result;
import org.backend.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/report")
public class ReportController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserIdFromToken(request);
        Map<String, Object> data = reportService.getReportDetail(id, userId);
        return Result.success(data);
    }

    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String position,
            @RequestParam(required = false) String round,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) Integer scoreMin,
            @RequestParam(required = false) Integer scoreMax,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Long userId = getUserIdFromToken(request);
        Map<String, Object> data = reportService.getReportList(userId, page, pageSize, position, round, difficulty, scoreMin, scoreMax, startDate, endDate);
        return Result.success(data);
    }

    @GetMapping("/growth")
    public Result<Map<String, Object>> growth(
            HttpServletRequest request,
            @RequestParam(required = false) String position,
            @RequestParam(required = false) String round) {
        Long userId = getUserIdFromToken(request);
        Map<String, Object> data = reportService.getGrowthData(userId, position, round);
        return Result.success(data);
    }

    /**
     * 提交每题耗时数据
     */
    @PostMapping("/{id}/durations")
    public Result<Void> submitDurations(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Long userId = getUserIdFromToken(request);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> durations = (List<Map<String, Object>>) body.get("durations");
        reportService.saveQuestionDurations(id, userId, durations);
        return Result.success("计时数据已保存", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserIdFromToken(request);
        reportService.deleteReport(id, userId);
        return Result.success("报告已删除", null);
    }
}
