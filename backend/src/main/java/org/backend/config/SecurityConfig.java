package org.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.backend.common.Result;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置 — 替代手写 Interceptor 鉴权
 *
 * 路由权限规则：
 * - 公开接口：/api/auth/**, /api/tags/**
 * - 静态资源：/uploads/**
 * - 管理员接口：/api/admin/** 需要 ROLE_ADMIN
 * - 其他 /api/**：需要认证
 * - OPTIONS 预检请求：放行
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 禁用 CSRF（API 服务，使用 JWT 无状态认证）
                .csrf(AbstractHttpConfigurer::disable)

                // CORS 由 WebConfig 管理，不重复配置
                .cors(cors -> {})

                // 无状态 Session
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 路由权限
                .authorizeHttpRequests(auth -> auth
                        // OPTIONS 预检请求放行
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // 公开接口
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/tags/**").permitAll()
                        .requestMatchers("/uploads/**").permitAll()

                        // 管理员接口
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // 其他 API 需要认证
                        .requestMatchers("/api/**").authenticated()

                        // 其他请求放行
                        .anyRequest().permitAll()
                )

                // 未认证/无权限时返回 JSON 而非重定向
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(objectMapper.writeValueAsString(
                                    Result.error(401, "未授权，请先登录")));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(objectMapper.writeValueAsString(
                                    Result.error(403, "权限不足")));
                        })
                )

                // JWT 过滤器放在 UsernamePasswordAuthenticationFilter 之前
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
