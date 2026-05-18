package com.fffg.cex.auth.controller;

import com.fffg.cex.auth.dto.LoginRequestDTO;
import com.fffg.cex.auth.dto.RegisterRequestDTO;
import com.fffg.cex.auth.service.AuthService;
import com.fffg.cex.auth.vo.LoginVO;
import com.fffg.cex.common.result.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public ApiResponse<LoginVO> register(@RequestBody @Valid RegisterRequestDTO request) {
        return ApiResponse.success(authService.register(request));
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ApiResponse<LoginVO> login(@RequestBody @Valid LoginRequestDTO request) {
        return ApiResponse.success(authService.login(request));
    }
}
