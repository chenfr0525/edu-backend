package com.edu.web.controller;

import com.edu.common.Result;
import com.edu.domain.User;
import com.edu.domain.dto.LoginRequest;
import com.edu.domain.dto.MenuDTO;
import com.edu.domain.dto.RegisterRequest;
import com.edu.domain.dto.UpdatePasswordRequest;
import com.edu.domain.dto.UpdateUserInfoRequest;
import com.edu.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/auth/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest request) {
        Map<String, Object> data = authService.login(request.getUsername(), request.getPassword(), request.getRole().toUpperCase());
        return Result.success(data);

    }

    @PostMapping("/auth/register")
    public Result<String> register(@RequestBody RegisterRequest request) {
        authService.register(request.getUsername(), request.getPassword(), request.getRole());
        return Result.success("Register success");
    }

   @GetMapping("/menu/list")
    @Operation(summary = "获取菜单列表")
    @PreAuthorize("isAuthenticated()")
    public Result<List<MenuDTO>> getMenus(@RequestParam String role) {
        List<MenuDTO> menus = authService.getMenus(role.toUpperCase());
        return Result.success(menus);
    }

    @GetMapping("/user/info")
    @Operation(summary = "获取用户信息")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> getUserInfo() {
        authService.updateLastLoginTime();
        Map<String, Object> userInfo = authService.getUserInfo();
        return Result.success(userInfo);
    }

    /**
     * 修改User基本信息
     * POST /auth/user-info
     */
     @PostMapping("/auth/update-info")
     @PreAuthorize("isAuthenticated()")
      public Result<User> updateUserInfo(@RequestBody UpdateUserInfoRequest request) {
         User user=authService.updateUserInfo(request);
         return Result.success(user);
     }

    /**
     * 修改密码
     * POST /auth/update-password
     */
    @PostMapping("/auth/update-password")
     @PreAuthorize("isAuthenticated()")
    public Result<Void> updatePassword(@RequestBody UpdatePasswordRequest request) {
        authService.updatePassword(request.getOldPassword(), request.getNewPassword());
        return Result.success(null);
    }

}
