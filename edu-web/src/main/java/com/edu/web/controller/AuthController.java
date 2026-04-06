package com.edu.web.controller;

import com.edu.common.Result;
import com.edu.domain.dto.LoginRequest;
import com.edu.domain.dto.RegisterRequest;
import com.edu.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest request) {
        Map<String, Object> data = authService.login(request.getUsername(), request.getPassword());
        return Result.success(data);
    }

    @PostMapping("/register")
    public Result<String> register(@RequestBody RegisterRequest request) {
        authService.register(request.getUsername(), request.getPassword(), request.getName(), request.getRole());
        return Result.success("Register success");
    }
}
