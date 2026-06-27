package org.backend.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.backend.exception.BusinessException;
import org.backend.service.CacheService;
import org.backend.util.JwtUtil;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class UserInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final CacheService cacheService;

    public UserInterceptor(JwtUtil jwtUtil, CacheService cacheService) {
        this.jwtUtil = jwtUtil;
        this.cacheService = cacheService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new BusinessException(401, "未授权");
        }
        
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            throw new BusinessException(401, "未授权");
        }
        
        Long userId = jwtUtil.getUserId(token);
        request.setAttribute("userId", userId);

        // tokenVersion 校验：Redis 不可用时降级跳过
        int tokenVersion = jwtUtil.getTokenVersion(token);
        String versionKey = "user:token_version:" + userId;
        Integer currentVersion = cacheService.getInt(versionKey);
        if (currentVersion != null && tokenVersion != currentVersion) {
            throw new BusinessException(401, "登录已失效，请重新登录");
        }

        return true;
    }
}