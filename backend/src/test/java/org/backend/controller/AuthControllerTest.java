package org.backend.controller;

import org.backend.dto.LoginRequest;
import org.backend.dto.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String TEST_EMAIL = "test_login_" + System.currentTimeMillis() + "@example.com";
    private static final String TEST_PASSWORD = "123456";

    @Test
    @Order(1)
    @DisplayName("AUTH-001: 正常注册 - 邮箱格式正确")
    void testRegister_Success() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(TEST_EMAIL);
        request.setPassword(TEST_PASSWORD);
        request.setUsername("testuser");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("注册成功"));
    }

    @Test
    @Order(2)
    @DisplayName("AUTH-002: 注册失败 - 邮箱为空")
    void testRegister_EmptyEmail() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("");
        request.setPassword("123456");
        request.setUsername("testuser");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(3)
    @DisplayName("AUTH-003: 注册失败 - 密码长度不足6位")
    void testRegister_PasswordTooShort() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("short@example.com");
        request.setPassword("123");
        request.setUsername("testuser");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(4)
    @DisplayName("AUTH-004: 注册失败 - 重复邮箱")
    void testRegister_DuplicateEmail() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(TEST_EMAIL);
        request.setPassword("654321");
        request.setUsername("anotheruser");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(5)
    @DisplayName("AUTH-005: 正常登录 - 账号密码正确")
    void testLogin_Success() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setAccount(TEST_EMAIL);
        request.setPassword(TEST_PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("登录成功"))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.userId").isNumber())
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andReturn();

        System.out.println("登录成功响应: " + result.getResponse().getContentAsString());
    }

    @Test
    @Order(6)
    @DisplayName("AUTH-006: 登录失败 - 账号不存在")
    void testLogin_AccountNotFound() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setAccount("nonexistent@example.com");
        request.setPassword("123456");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @Order(7)
    @DisplayName("AUTH-007: 登录失败 - 密码错误")
    void testLogin_WrongPassword() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setAccount(TEST_EMAIL);
        request.setPassword("wrongpassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @Order(8)
    @DisplayName("AUTH-008: 登录失败 - 账号为空")
    void testLogin_EmptyAccount() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setAccount("");
        request.setPassword("123456");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(9)
    @DisplayName("AUTH-009: 登录失败 - 密码为空")
    void testLogin_EmptyPassword() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setAccount(TEST_EMAIL);
        request.setPassword("");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(10)
    @DisplayName("AUTH-010: 登录失败 - 账号格式不正确")
    void testLogin_InvalidAccountFormat() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setAccount("invalid-email");
        request.setPassword("123456");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}