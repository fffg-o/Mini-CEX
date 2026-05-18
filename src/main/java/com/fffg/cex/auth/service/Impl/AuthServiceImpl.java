package com.fffg.cex.auth.service.Impl;

import com.fffg.cex.auth.config.JwtProperties;
import com.fffg.cex.auth.dto.LoginRequestDTO;
import com.fffg.cex.auth.dto.RegisterRequestDTO;
import com.fffg.cex.auth.mapper.AuthAccountMapper;
import com.fffg.cex.auth.mapper.AuthAccountMapper.AccountWithPassword;
import com.fffg.cex.auth.mapper.AuthAccountMapper.CreateAccountWithPassword;
import com.fffg.cex.auth.service.AuthService;
import com.fffg.cex.auth.util.JwtUtil;
import com.fffg.cex.auth.vo.LoginVO;
import com.fffg.cex.common.exception.BusinessException;
import com.fffg.cex.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthAccountMapper authAccountMapper;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(RegisterRequestDTO request) {
        // 1. 校验用户名是否已存在
        AccountWithPassword existing = authAccountMapper.getAccountByUsername(request.getUsername());
        if (existing != null) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS.getCode(), ErrorCode.USERNAME_EXISTS.getMessage());
        }

        // 2. 密码加密
        String passwordHash = passwordEncoder.encode(request.getPassword());

        // 3. 创建账户
        CreateAccountWithPassword createRequest = new CreateAccountWithPassword();
        createRequest.setUsername(request.getUsername());
        createRequest.setPasswordHash(passwordHash);
        authAccountMapper.createAccountWithPassword(createRequest);

        Long accountId = createRequest.getId();

        log.info("新用户注册成功: accountId={}, username={}", accountId, request.getUsername());

        // 4. 生成 JWT Token
        String token = jwtUtil.generateToken(accountId, request.getUsername());

        return LoginVO.builder()
                .token(token)
                .accountId(accountId)
                .username(request.getUsername())
                .expiresIn(jwtProperties.getExpiration())
                .build();
    }

    @Override
    public LoginVO login(LoginRequestDTO request) {
        // 1. 查询用户
        AccountWithPassword account = authAccountMapper.getAccountByUsername(request.getUsername());
        if (account == null) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 2. 校验密码
        if (!passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 3. 生成 JWT Token
        String token = jwtUtil.generateToken(account.getAccountId(), account.getUserName());

        log.info("用户登录成功: accountId={}, username={}", account.getAccountId(), account.getUserName());

        return LoginVO.builder()
                .token(token)
                .accountId(account.getAccountId())
                .username(account.getUserName())
                .expiresIn(jwtProperties.getExpiration())
                .build();
    }
}
