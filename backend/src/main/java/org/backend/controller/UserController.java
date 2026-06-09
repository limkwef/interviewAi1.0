package org.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.backend.common.Result;
import org.backend.dto.ChangePasswordRequest;
import org.backend.dto.UpdateUserRequest;
import org.backend.entity.User;
import org.backend.entity.UserInfoVO;
import org.backend.exception.BusinessException;
import org.backend.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/info")
    public Result<UserInfoVO> getUserInfo(HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        User user = userService.getUserById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        UserInfoVO data = new UserInfoVO();
        data.setId(user.getId());
        data.setUsername(user.getUsername());
        data.setEmail(user.getEmail());
        data.setPhone(user.getPhone());
        data.setAvatar(user.getAvatar());
        data.setTargetPosition(user.getTargetPosition());
        data.setTechStack(user.getTechStack());
        data.setRole(user.getRole());
        data.setCreatedAt(user.getCreatedAt());
        return Result.success(data);
    }

    @PutMapping("/info")
    public Result<?> updateUserInfo(HttpServletRequest request, @Valid @RequestBody UpdateUserRequest updateRequest) {
        Long userId = getUserIdFromToken(request);
        userService.updateUserInfo(userId, updateRequest);
        return Result.success("更新成功", null);
    }

    @PutMapping("/password")
    public Result<?> changePassword(HttpServletRequest request, @Valid @RequestBody ChangePasswordRequest passwordRequest) {
        Long userId = getUserIdFromToken(request);
        userService.changePassword(userId, passwordRequest);
        return Result.success("密码修改成功", null);
    }

    @PostMapping("/avatar")
    public Result<Map<String, String>> uploadAvatar(HttpServletRequest request, @RequestParam("file") MultipartFile file) {
        Long userId = getUserIdFromToken(request);
        String avatarUrl = userService.uploadAvatar(userId, file);
        Map<String, String> data = new HashMap<>();
        data.put("avatar", avatarUrl);
        return Result.success("头像上传成功", data);
    }
}