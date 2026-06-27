package org.backend.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.backend.dto.ChangePasswordRequest;
import org.backend.dto.LoginRequest;
import org.backend.dto.RegisterRequest;
import org.backend.dto.UpdateUserRequest;
import org.backend.entity.User;
import org.backend.exception.BusinessException;
import org.backend.mapper.UserMapper;
import org.backend.util.JwtUtil;
import org.backend.service.CacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${upload.path:./uploads/}")
    private String uploadPath;

    /** 头像最大 1MB */
    private static final long MAX_AVATAR_SIZE = 1 * 1024 * 1024L;

    /** 允许的头像 MIME 类型 */
    private static final java.util.Set<String> ALLOWED_MIME_TYPES = java.util.Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    /**
     * 用户注册
     */
    public void register(RegisterRequest request) {
        if (userMapper.findByEmail(request.getEmail()) != null) {
            throw new BusinessException(409, "该邮箱已被注册");
        }

        // 手机号不为空时检查是否已被注册
        String phone = request.getPhone();
        if (phone != null && !phone.isEmpty()) {
            if (userMapper.findByPhone(phone) != null) {
                throw new BusinessException(409, "该手机号已被注册");
            }
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setUsername(request.getUsername());
        // 手机号为空时设为null（MySQL唯一索引允许多个null值，但不允许多个空串）
        user.setPhone(phone != null && !phone.isEmpty() ? phone : null);
        user.setRole("user");
        user.setStatus(1);

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
                throw new BusinessException(401, "账号不存在");
            }
            // 检查账号是否被禁用（status = 0 表示禁用）
            if (user.getStatus() != null && user.getStatus() == 0) {
                throw new BusinessException(401, "账号已被禁用，请联系管理员");
            }
            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                throw new BusinessException(401, "密码错误");
            }

            String versionKey = "user:token_version:" + user.getId();
            Integer tokenVersion = cacheService.getInt(versionKey);
            if (tokenVersion == null) {
                cacheService.setInt(versionKey, 0);
                tokenVersion = 0;
            }
            String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole(), tokenVersion);

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
            // 手机号变更时检查是否已被其他用户占用
            if (!request.getPhone().equals(user.getPhone())) {
                User phoneUser = userMapper.findByPhone(request.getPhone());
                if (phoneUser != null && !phoneUser.getId().equals(id)) {
                    throw new BusinessException(409, "该手机号已被其他用户绑定");
                }
            }
            user.setPhone(request.getPhone());
        }
        if (request.getTargetPosition() != null) {
            user.setTargetPosition(request.getTargetPosition());
        }
        if (request.getTechStack() != null) {
            String ts = request.getTechStack().trim();
            // 数据库 tech_stack 是 json 列，确保存储为合法 JSON
            if (!ts.startsWith("[")) {
                // 普通字符串转 JSON 数组：按逗号分割
                String[] parts = ts.split("[,，]");
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < parts.length; i++) {
                    if (i > 0) sb.append(",");
                    sb.append("\"").append(parts[i].trim().replace("\"", "\\\"")).append("\"");
                }
                sb.append("]");
                ts = sb.toString();
            }
            user.setTechStack(ts);
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

        // 1. 校验文件是否为空
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择要上传的头像文件");
        }

        // 2. 校验文件大小
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new BusinessException(400, "头像文件大小不能超过 1MB");
        }

        // 3. 校验文件类型
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new BusinessException(400, "仅支持 JPG/PNG/GIF/WebP 格式的头像");
        }

        // 4. 校验并清理文件名（防止路径穿越）
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf(".")).toLowerCase();
            // 只允许常见图片扩展名
            if (!ext.matches("\\.(jpg|jpeg|png|gif|webp)$")) {
                ext = ".jpg";
            }
        } else {
            ext = ".jpg";
        }
        String safeFilename = UUID.randomUUID() + ext;

        // 5. 写入磁盘
        try {
            Path avatarDir = Paths.get(uploadPath, "avatars").normalize();
            Files.createDirectories(avatarDir);
            Path targetPath = avatarDir.resolve(safeFilename).normalize();

            // 防止路径穿越（再次确保目标在 avatarDir 下）
            if (!targetPath.startsWith(avatarDir)) {
                throw new BusinessException(400, "非法的文件名");
            }

            Files.copy(file.getInputStream(), targetPath);

            String avatarUrl = "/uploads/avatars/" + safeFilename;
            user.setAvatar(avatarUrl);
            userMapper.updateUser(user);
            logger.info("头像上传成功，用户ID：{}，文件名：{}", userId, safeFilename);
            return avatarUrl;
        } catch (IOException e) {
            logger.error("头像文件写入失败，用户ID：{}", userId, e);
            throw new BusinessException(500, "头像上传失败，请稍后重试");
        }
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

    /**
     * 清除指定用户的缓存（供 AdminService 调用）
     */
    @CacheEvict(cacheNames = "user", key = "'info:' + #userId")
    public void clearUserCache(Long userId) {
        logger.info("清除用户缓存，用户ID：{}", userId);
    }
}
