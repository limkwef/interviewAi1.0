package org.backend.controller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.backend.common.Result;
import org.backend.entity.AiModel;
import org.backend.vo.AiModelVO;
import org.backend.service.AiModelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api")
public class AiModelController {

    private static final Logger logger = LoggerFactory.getLogger(AiModelController.class);

    private final AiModelService aiModelService;
    private final ObjectMapper objectMapper;

    public AiModelController(AiModelService aiModelService, ObjectMapper objectMapper) {
        this.aiModelService = aiModelService;
        this.objectMapper = objectMapper;
    }

    // ======================== 用户接口 ========================

    /** 用户获取可用模型列表（自己的 + 系统的），返回 VO 不含 apiKey */
    @GetMapping("/ai-models")
    public Result<List<AiModelVO>> getEnabledModels() {
        Long userId = getCurrentUserId();
        if (userId != null) {
            return Result.success(AiModelVO.fromList(aiModelService.getEnabledByUserId(userId)));
        }
        return Result.success(AiModelVO.fromList(aiModelService.getEnabled()));
    }

    /** 用户获取自己的模型列表，返回 VO 不含 apiKey */
    @GetMapping("/user/ai-models")
    public Result<List<AiModelVO>> getUserModels() {
        Long userId = getCurrentUserId();
        if (userId == null) return Result.error(401, "未登录");
        return Result.success(AiModelVO.fromList(aiModelService.getByUserId(userId)));
    }

    /** 用户添加模型 */
    @PostMapping("/user/ai-models")
    public Result<AiModelVO> createUserModel(@RequestBody AiModel model) {
        Long userId = getCurrentUserId();
        if (userId == null) return Result.error(401, "未登录");
        return Result.success("添加成功", AiModelVO.fromEntity(aiModelService.createUserModel(userId, model)));
    }

    /** 用户更新自己的模型 */
    @PutMapping("/user/ai-models/{id}")
    public Result<AiModelVO> updateUserModel(@PathVariable Long id, @RequestBody AiModel model) {
        Long userId = getCurrentUserId();
        if (userId == null) return Result.error(401, "未登录");
        return Result.success("更新成功", AiModelVO.fromEntity(aiModelService.updateUserModel(userId, id, model)));
    }

    /** 用户删除自己的模型 */
    @DeleteMapping("/user/ai-models/{id}")
    public Result<Void> deleteUserModel(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        if (userId == null) return Result.error(401, "未登录");
        aiModelService.deleteUserModel(userId, id);
        return Result.success("删除成功", null);
    }

    /** 用户测试自己的模型连通性 */
    @PostMapping("/user/ai-models/{id}/test")
    public Result<Map<String, Object>> testUserModel(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        if (userId == null) return Result.error(401, "未登录");
        AiModel model = aiModelService.getById(id);
        if (model == null) return Result.error(404, "模型不存在");
        if (model.getUserId() != null && !model.getUserId().equals(userId)) {
            return Result.error(403, "只能操作自己的模型");
        }
        return doTestModel(model);
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long) {
            return (Long) auth.getPrincipal();
        }
        return null;
    }

    // ======================== 管理员接口 ========================

    @GetMapping("/admin/ai-models")
    public Result<List<AiModelVO>> getAllModels() {
        return Result.success(AiModelVO.fromList(aiModelService.getAll()));
    }

    @GetMapping("/admin/ai-models/{id}")
    public Result<AiModelVO> getModel(@PathVariable Long id) {
        AiModel model = aiModelService.getById(id);
        if (model == null) return Result.error(404, "模型不存在");
        return Result.success(AiModelVO.fromEntity(model));
    }

    @PostMapping("/admin/ai-models")
    public Result<AiModelVO> createModel(@RequestBody AiModel model) {
        return Result.success("添加成功", AiModelVO.fromEntity(aiModelService.create(model)));
    }

    @PutMapping("/admin/ai-models/{id}")
    public Result<AiModelVO> updateModel(@PathVariable Long id, @RequestBody AiModel model) {
        return Result.success("更新成功", AiModelVO.fromEntity(aiModelService.update(id, model)));
    }

    @DeleteMapping("/admin/ai-models/{id}")
    public Result<Void> deleteModel(@PathVariable Long id) {
        aiModelService.delete(id);
        return Result.success("删除成功", null);
    }

    @PostMapping("/admin/ai-models/{id}/set-default")
    public Result<Void> setDefault(@PathVariable Long id) {
        aiModelService.setDefault(id);
        return Result.success("设置成功", null);
    }

    /** 测试模型连通性 */
    @PostMapping("/admin/ai-models/{id}/test")
    public Result<Map<String, Object>> testModel(@PathVariable Long id) {
        AiModel model = aiModelService.getById(id);
        if (model == null) return Result.error(404, "模型不存在");
        return doTestModel(model);
    }

    private Result<Map<String, Object>> doTestModel(AiModel model) {
        try {
            Map<String, Object> body = Map.of(
                "model", model.getModelCode(),
                "messages", List.of(Map.of("role", "user", "content", "Hi")),
                "max_tokens", 10,
                "stream", false
            );

            String jsonBody = objectMapper.writeValueAsString(body);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(model.getApiUrl() + "/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + model.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return Result.success("连通性测试成功", Map.of("status", "ok", "statusCode", 200));
            } else {
                return Result.success("连通性测试失败 HTTP " + response.statusCode(),
                        Map.of("status", "error", "statusCode", response.statusCode()));
            }
        } catch (Exception e) {
            logger.error("模型连通性测试失败 {}", e.getMessage());
            return Result.success("连通性测试失败 " + e.getMessage(),
                    Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
