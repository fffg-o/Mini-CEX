package com.fffg.cex.auth.service;

import com.fffg.cex.auth.dto.LoginRequestDTO;
import com.fffg.cex.auth.dto.RegisterRequestDTO;
import com.fffg.cex.auth.vo.LoginVO;

public interface AuthService {

    /**
     * 用户注册
     */
    LoginVO register(RegisterRequestDTO request);

    /**
     * 用户登录
     */
    LoginVO login(LoginRequestDTO request);
}
