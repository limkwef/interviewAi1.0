package org.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.backend.service.CacheService;
import org.backend.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器 — 替代 UserInterceptor + AdminInterceptor
 *
 * 从 Authorization header 提取 JWT，验证有效性后设置 SecurityContext。
 * 同时设置 request attribute（userId/adminId）以兼容现有 BaseController。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;
    private final CacheService cacheService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, CacheService cacheService) {
        this.jwtUtil = jwtUtil;
        this.cacheService = cacheService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            if (!jwtUtil.validateToken(token)) {
                filterChain.doFilter(request, response);
                return;
            }

            Long userId = jwtUtil.getUserId(token);
            String role = jwtUtil.getRole(token);
            int tokenVersion = jwtUtil.getTokenVersion(token);

            // tokenVersion 校验：Redis 不可用时降级跳过
            String versionKey = "user:token_version:" + userId;
            Integer currentVersion = cacheService.getInt(versionKey);
            if (currentVersion != null && tokenVersion != currentVersion) {
                filterChain.doFilter(request, response);
                return;
            }

            // 设置 request attribute（兼容现有 BaseController 和 AdminController）
            request.setAttribute("userId", userId);
            if ("admin".equals(role)) {
                request.setAttribute("adminId", userId);
                request.setAttribute("adminRole", role);
            }

            // 设置 Spring Security Authentication
            List<SimpleGrantedAuthority> authorities = "admin".equals(role)
                    ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_USER"))
                    : List.of(new SimpleGrantedAuthority("ROLE_USER"));

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (Exception e) {
            logger.debug("JWT 认证失败: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
