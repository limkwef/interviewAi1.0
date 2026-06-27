package org.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.backend.common.Result;
import org.backend.dto.BatchImportRequest;
import org.backend.dto.ResetPasswordDTO;
import org.backend.entity.Question;
import org.backend.entity.User;
import org.backend.service.AdminLogService;
import org.backend.service.AdminService;
import org.backend.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    private final AdminService adminService;
    private final AdminLogService adminLogService;

    public AdminController(AdminService adminService, AdminLogService adminLogService) {
        this.adminService = adminService;
        this.adminLogService = adminLogService;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    // 系统统计
    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics() {
        return Result.success(adminService.getStatistics());
    }

    // 用户管理
    @GetMapping("/users")
    public Result<Map<String, Object>> getUserList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return Result.success(adminService.getUserList(page, size, status, keyword));
    }

    @GetMapping("/users/{id}")
    public Result<User> getUserById(@PathVariable Long id) {
        return Result.success(adminService.getUserById(id));
    }

    @PutMapping("/users/{id}")
    public Result<?> updateUser(HttpServletRequest request, 
                                @PathVariable Long id, 
                                @RequestBody User user) {
        adminService.updateUser(id, user);
        logAdminAction(request, "UPDATE_USER", "user", id, "更新用户信息");
        return Result.success("更新成功", null);
    }

    @PutMapping("/users/{id}/status")
    public Result<?> updateUserStatus(HttpServletRequest request,
                                      @PathVariable Long id,
                                      @RequestParam Integer status) {
        adminService.updateUserStatus(id, status);
        String actionText = status == 1 ? "启用用户" : "禁用用户";
        logAdminAction(request, status == 1 ? "ENABLE_USER" : "DISABLE_USER", "user", id, actionText);
        return Result.success("操作成功", null);
    }

    @PutMapping("/users/{id}/password")
    public Result<?> resetUserPassword(HttpServletRequest request,
                                       @PathVariable Long id,
                                       @Valid @RequestBody ResetPasswordDTO body) {
        adminService.resetUserPassword(id, body.getNewPassword());
        logAdminAction(request, "RESET_PASSWORD", "user", id, "重置用户密码");
        return Result.success("密码重置成功", null);
    }

    @DeleteMapping("/users/{id}")
    public Result<?> deleteUser(HttpServletRequest request, @PathVariable Long id) {
        adminService.deleteUser(id);
        logAdminAction(request, "DELETE_USER", "user", id, "删除用户");
        return Result.success("删除成功", null);
    }

    // 题目管理
    @GetMapping("/questions")
    public Result<Map<String, Object>> getQuestionList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String keyword) {
        return Result.success(adminService.getQuestionList(page, size, category, difficulty, direction, keyword));
    }

    @GetMapping("/questions/{id}")
    public Result<Question> getQuestionById(@PathVariable Long id) {
        return Result.success(adminService.getQuestionById(id));
    }

    @PostMapping("/questions")
    public Result<Long> createQuestion(HttpServletRequest request, @Valid @RequestBody Question question) {
        try {
            Long id = adminService.createQuestion(question);
            logAdminAction(request, "CREATE_QUESTION", "question", id, "创建题目");
            return Result.success("创建成功", id);
        } catch (Exception e) {
            logger.error("创建题目失败：{}", e.getMessage(), e);
            throw e;
        }
    }

    @PutMapping("/questions/{id}")
    public Result<?> updateQuestion(HttpServletRequest request,
                                    @PathVariable Long id,
                                    @RequestBody Question question) {
        adminService.updateQuestion(id, question);
        logAdminAction(request, "UPDATE_QUESTION", "question", id, "更新题目");
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/questions/{id}")
    public Result<?> deleteQuestion(HttpServletRequest request, @PathVariable Long id) {
        adminService.deleteQuestion(id);
        logAdminAction(request, "DELETE_QUESTION", "question", id, "删除题目");
        return Result.success("删除成功", null);
    }

    @PostMapping("/questions/batch")
    public Result<?> batchImportQuestions(HttpServletRequest request, @Valid @RequestBody BatchImportRequest importRequest) {
        Map<String, Object> result = adminService.batchImportQuestions(importRequest.getQuestions(), importRequest.getDuplicateStrategy());
        logAdminAction(request, "BATCH_IMPORT", "question", null,
                "批量导入题目，数量：" + importRequest.getQuestions().size() + "，策略：" + importRequest.getDuplicateStrategy());
        return Result.success("导入完成", result);
    }

    // 操作日志
    @GetMapping("/logs")
    public Result<Map<String, Object>> getLogList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.success(adminService.getLogList(page, size, action, startDate, endDate));
    }

    // 反馈管理
    @GetMapping("/feedback")
    public Result<Map<String, Object>> getFeedbackList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer status) {
        return Result.success(adminService.getFeedbackList(page, size, status));
    }

    @PutMapping("/feedback/{id}/status")
    public Result<?> updateFeedbackStatus(HttpServletRequest request,
                                          @PathVariable Long id,
                                          @RequestParam Integer status) {
        adminService.updateFeedbackStatus(id, status);
        String statusText = status == 1 ? "已处理" : "已忽略";
        logAdminAction(request, "UPDATE_FEEDBACK_STATUS", "feedback", id, "标记反馈为" + statusText);
        return Result.success("操作成功", null);
    }

    @DeleteMapping("/feedback/{id}")
    public Result<?> deleteFeedback(HttpServletRequest request, @PathVariable Long id) {
        adminService.deleteFeedback(id);
        logAdminAction(request, "DELETE_FEEDBACK", "feedback", id, "删除反馈");
        return Result.success("删除成功", null);
    }

    private void logAdminAction(HttpServletRequest request, String action, 
                               String targetType, Long targetId, String detail) {
        Long adminId = (Long) request.getAttribute("adminId");
        if (adminId == null) {
            logger.warn("adminId 为空，跳过操作日志记录，action={}", action);
            return;
        }
        String adminName = "管理员"; // 可以根据ID查询用户信息获取
        String ip = getClientIp(request);
        adminLogService.log(adminId, adminName, action, targetType, targetId, detail, ip);
    }
}