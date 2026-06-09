package org.backend.controller;

import org.backend.exception.BusinessException;
import org.backend.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 控制器基类 — 统一获取当前登录用户 ID
 *
 * 优先从拦截器设置的 request attribute 获取 userId，
 * 如果不存在（兼容未走拦截器的路径）则手动解析 JWT。
 */
public abstract class BaseController {

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 获取当前登录用户 ID
     *
     * 优先从 UserInterceptor 设置的 request attribute 获取，
     * 兼容未走拦截器的旧路径。
     *
     * @param request HTTP 请求
     * @return 用户 ID
     * @throws BusinessException 401 — 未登录或 Token 无效
     */
    protected Long getUserIdFromToken(HttpServletRequest request) {
        // 优先从拦截器获取
        Object userIdAttr = request.getAttribute("userId");
        if (userIdAttr instanceof Long) {
            return (Long) userIdAttr;
        }
        // 兼容：手动解析 JWT
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new BusinessException(401, "未授权");
        }
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            throw new BusinessException(401, "未授权");
        }
        return jwtUtil.getUserId(token);
    }

    /**
     * 获取当前用户 ID（别名方法，语义更清晰）
     */
    protected Long getCurrentUserId(HttpServletRequest request) {
        return getUserIdFromToken(request);
    }
}
