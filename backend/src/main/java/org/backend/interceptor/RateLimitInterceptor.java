package org.backend.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.backend.common.Result;
import org.backend.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

/**
 * 接口限流拦截器 — Redis 滑动窗口
 *
 * 规则（可通过 application.yml 配置）：
 * - 普通接口：60 次/分钟/用户
 * - AI 面试创建：5 次/小时/用户
 * - AI 对话发送：20 次/分钟/用户
 * - 文件上传：10 次/小时/用户
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitInterceptor.class);

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    // 默认限流规则（可通过 application.yml 覆盖）
    private static final int DEFAULT_LIMIT = 60;
    private static final long DEFAULT_WINDOW_MS = 60_000; // 1 分钟

    // 特殊路径限流规则
    private static final String INTERVIEW_CREATE_PATH = "/api/interview/create";
    private static final String INTERVIEW_MESSAGE_PATH = "/api/interview/*/message";
    private static final String INTERVIEW_STREAM_PATH = "/api/interview/*/stream";
    private static final String UPLOAD_PATH = "/api/user/avatar";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (stringRedisTemplate == null) {
            return true; // Redis 不可用时限流跳过
        }

        String userId = extractUserId(request);
        if (userId == null) {
            return true; // 未登录用户不限流（由认证拦截器处理）
        }

        String path = request.getRequestURI();
        String method = request.getMethod();
        String group = resolveGroup(path, method);

        int limit = getLimit(group);
        long windowMs = getWindowMs(group);

        String key = "rate_limit:" + userId + ":" + group;
        long now = System.currentTimeMillis();
        long windowStart = now - windowMs;

        try {
            // 滑动窗口：ZSET 存储请求时间戳
            ZSetOperations<String, String> zSetOps = stringRedisTemplate.opsForZSet();

            // 移除窗口外的旧记录
            zSetOps.removeRangeByScore(key, 0, windowStart);

            // 统计当前窗口内的请求数
            Long count = zSetOps.zCard(key);
            if (count != null && count >= limit) {
                logger.warn("用户 {} 接口 {} 触发限流（{}/{}ms）", userId, group, count, windowMs);
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(objectMapper.writeValueAsString(Result.error(429, "请求过于频繁，请稍后再试")));
                return false;
            }

            // 添加当前请求
            zSetOps.add(key, String.valueOf(now), now);

            // 设置 key 过期（窗口时间 + 缓冲）
            stringRedisTemplate.expire(key, windowMs + 10_000, TimeUnit.MILLISECONDS);

            return true;
        } catch (Exception e) {
            logger.error("限流检查异常，放行请求", e);
            return true; // Redis 异常时放行
        }
    }

    /**
     * 从 JWT token 提取用户 ID
     */
    private String extractUserId(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            try {
                Long userId = jwtUtil.getUserId(token);
                return userId != null ? String.valueOf(userId) : null;
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 根据请求路径和方法确定限流分组
     */
    private String resolveGroup(String path, String method) {
        if ("POST".equals(method) && path.equals(INTERVIEW_CREATE_PATH)) {
            return "interview_create";
        }
        if ("POST".equals(method) && path.matches("/api/interview/\\d+/message")) {
            return "ai_message";
        }
        if ("POST".equals(method) && path.matches("/api/interview/\\d+/stream")) {
            return "ai_message";
        }
        if ("POST".equals(method) && path.equals(UPLOAD_PATH)) {
            return "upload";
        }
        return "default";
    }

    private int getLimit(String group) {
        return switch (group) {
            case "interview_create" -> 5;
            case "ai_message" -> 20;
            case "upload" -> 10;
            default -> DEFAULT_LIMIT;
        };
    }

    private long getWindowMs(String group) {
        return switch (group) {
            case "interview_create" -> 3_600_000; // 1 小时
            case "upload" -> 3_600_000;           // 1 小时
            default -> DEFAULT_WINDOW_MS;         // 1 分钟
        };
    }
}
