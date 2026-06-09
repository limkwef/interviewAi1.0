package org.backend.service;

import org.backend.dto.ChangePasswordRequest;
import org.backend.dto.LoginRequest;
import org.backend.dto.RegisterRequest;
import org.backend.dto.UpdateUserRequest;
import org.backend.entity.User;
import org.backend.exception.BusinessException;
import org.backend.mapper.UserMapper;
import org.backend.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 用户注册
     */
    public void register(RegisterRequest request) {
        if (userMapper.findByEmail(request.getEmail()) != null) {
            throw new BusinessException("该邮箱已被注册");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setUsername(request.getUsername());
        user.setPhone(request.getPhone());
        user.setRole("user");

        userMapper.insert(user);
    }

    /**
     * 用户登录
     */
    public Map<String, Object> login(LoginRequest request) {
        try {
            String account = request.getAccount();
            User user;

            // 支持邮箱或手机号登录
            if (account.contains("@")) {
                user = userMapper.findByEmail(account);
            } else {
                user = userMapper.findByPhone(account);
            }

            if (user == null) {
                throw new BusinessException("账号不存在");
            }
            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                throw new BusinessException("密码错误");
            }

            String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole());

            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("userId", user.getId());
            data.put("username", user.getUsername());
            data.put("role", user.getRole());
            return data;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            logger.error("用户登录过程中发生异常，账号：{}", request.getAccount(), e);
            throw new BusinessException("登录失败，请稍后重试");
        }
    }

    @Cacheable(cacheNames = "user", key = "'info:' + #id", unless = "#result == null")
    public User getUserById(Long id) {
        logger.debug("根据ID查询用户信息，用户ID：{}", id);
        User user = userMapper.findById(id);
        if (user == null) {
            logger.warn("未找到用户，用户ID：{}", id);
        }
        return user;
    }

    @CacheEvict(cacheNames = "user", key = "'info:' + #id")
    public void updateUserInfo(Long id, UpdateUserRequest request) {
        logger.info("更新用户信息，用户ID：{}", id);
        User user = userMapper.findById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        if (request.getUsername() != null) {
            user.setUsername(request.getUsername());
        }
        if (request.getPhone() != null && !request.getPhone().isEmpty()) {
            user.setPhone(request.getPhone());
        }
        if (request.getTargetPosition() != null) {
            user.setTargetPosition(request.getTargetPosition());
        }
        if (request.getTechStack() != null) {
            user.setTechStack(request.getTechStack());
        }

        userMapper.updateUser(user);
    }

    /**
     * 修改密码
     */
    @CacheEvict(cacheNames = "user", key = "'info:' + #userId")
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException("原密码错误");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateUser(user);
    }

    /**
     * 上传头像
     */
    @CacheEvict(cacheNames = "user", key = "'info:' + #userId")
    public String uploadAvatar(Long userId, MultipartFile file) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 简单处理：使用文件名作为头像URL（实际项目应上传到OSS）
        String filename = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        String avatarUrl = "/uploads/avatars/" + filename;
        user.setAvatar(avatarUrl);
        userMapper.updateUser(user);
        return avatarUrl;
    }

    /**
     * 更新用户目标岗位
     */
    @CacheEvict(cacheNames = "user", key = "'info:' + #userId")
    public void updateTargetPosition(Long userId, String targetPosition) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setTargetPosition(targetPosition);
        userMapper.updateUser(user);
    }
}
