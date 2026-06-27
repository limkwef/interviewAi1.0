package org.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.backend.common.Result;
import org.backend.service.UserFavoriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(FavoriteController.class);

    private final UserFavoriteService userFavoriteService;

    public FavoriteController(UserFavoriteService userFavoriteService) {
        this.userFavoriteService = userFavoriteService;
    }

    @PostMapping("/{questionId}")
    public Result<?> addFavorite(HttpServletRequest request, @PathVariable Long questionId) {
        Long userId = getUserIdFromToken(request);
        userFavoriteService.addFavorite(userId, questionId);
        return Result.success("收藏成功", null);
    }

    @DeleteMapping("/{questionId}")
    public Result<?> removeFavorite(HttpServletRequest request, @PathVariable Long questionId) {
        Long userId = getUserIdFromToken(request);
        userFavoriteService.removeFavorite(userId, questionId);
        return Result.success("取消收藏成功", null);
    }

    @GetMapping
    public Result<Map<String, Object>> list(
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String difficulty) {
        Long userId = getUserIdFromToken(request);
        Map<String, Object> data = userFavoriteService.getFavoriteList(userId, page, size, category, difficulty);
        return Result.success(data);
    }

    @GetMapping("/check/{questionId}")
    public Result<Boolean> check(HttpServletRequest request, @PathVariable Long questionId) {
        Long userId = getUserIdFromToken(request);
        boolean isFavorite = userFavoriteService.isFavorite(userId, questionId);
        return Result.success(isFavorite);
    }

    /**
     * 批量取消收藏
     */
    @DeleteMapping("/batch")
    public Result<Void> batchRemove(HttpServletRequest request, @RequestBody Map<String, List<Long>> body) {
        Long userId = getUserIdFromToken(request);
        List<Long> questionIds = body.get("questionIds");
        if (questionIds == null || questionIds.isEmpty()) return Result.error(400, "请选择题目");
        int count = userFavoriteService.batchRemoveFavorite(userId, questionIds);
        return Result.success("已批量取消" + count + "个收藏", null);
    }
}
