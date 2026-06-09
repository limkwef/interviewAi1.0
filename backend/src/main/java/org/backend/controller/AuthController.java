package org.backend.controller;

import jakarta.validation.Valid;
import org.backend.common.Result;
import org.backend.dto.LoginRequest;
import org.backend.dto.RegisterRequest;
import org.backend.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public Result<?> register(@Valid @RequestBody RegisterRequest request) {
        logger.info("收到注册请求，邮箱：{}", request.getEmail());
        
        try {
            userService.register(request);
            logger.info("注册成功，邮箱：{}", request.getEmail());
            return Result.success("注册成功", null);
        } catch (Exception e) {
            logger.error("注册失败，邮箱：{}", request.getEmail(), e);
            throw e;
        }
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        logger.info("收到登录请求，账号：{}", request.getAccount());
        
        try {
            Map<String, Object> data = userService.login(request);
            logger.info("登录成功，账号：{}", request.getAccount());
            return Result.success("登录成功", data);
        } catch (Exception e) {
            logger.error("登录失败，账号：{}", request.getAccount(), e);
            throw e;
        }
    }
}
