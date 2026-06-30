package org.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.backend.common.Result;
import org.backend.vo.DashboardVO;
import org.backend.service.DashboardService;
import org.backend.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);

    private final DashboardService dashboardService;
    private final ReportService reportService;

    public DashboardController(DashboardService dashboardService, ReportService reportService) {
        this.dashboardService = dashboardService;
        this.reportService = reportService;
    }

    /**
     * 获取仪表盘所有数据（新接口）
     */
    @GetMapping("/overview")
    public Result<DashboardVO> overview(HttpServletRequest request,
                                        @RequestParam(required = false) String position) {
        Long userId = getUserIdFromToken(request);
        DashboardVO data = dashboardService.getDashboardData(userId, position);
        return Result.success(data);
    }

    /**
     * 获取统计数据（兼容旧接口）
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        Map<String, Object> data = reportService.getDashboardStats(userId);
        return Result.success(data);
    }

    /**
     * 获取分数趋势
     */
    @GetMapping("/score-trend")
    public Result<?> scoreTrend(HttpServletRequest request,
                                @RequestParam(required = false) String position) {
        Long userId = getUserIdFromToken(request);
        return Result.success(dashboardService.getScoreTrend(userId, position));
    }

    /**
     * 获取能力维度
     */
    @GetMapping("/knowledge-overview")
    public Result<?> knowledgeOverview(HttpServletRequest request,
                                       @RequestParam(required = false) String position) {
        Long userId = getUserIdFromToken(request);
        return Result.success(dashboardService.getKnowledgeOverview(userId, position));
    }
}
