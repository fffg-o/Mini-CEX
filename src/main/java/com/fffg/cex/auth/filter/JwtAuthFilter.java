package com.fffg.cex.auth.filter;

import com.fffg.cex.auth.util.JwtUtil;
import com.fffg.cex.common.result.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器：校验请求头中的 Authorization: Bearer <token><br/>
 * 白名单路径（/auth/**）直接放行，其他路径需要有效 JWT 认证。
 * <p>
 * 通过 {@code FilterRegistrationBean} 注册到 Spring 容器中，请参见 {@link com.fffg.cex.common.config.WebMvcConfig}。
 */
@Slf4j
public class JwtAuthFilter implements Filter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String REQUEST_ATTR_ACCOUNT_ID = "currentAccountId";
    private static final String REQUEST_ATTR_USERNAME = "currentUsername";

    /**
     * 白名单路径前缀：不需要认证即可访问
     */
    private static final List<String> WHITE_LIST = List.of(
            "/auth/"
    );

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtUtil jwtUtil, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getRequestURI();

        // 白名单路径直接放行
        if (isWhiteListed(path)) {
            chain.doFilter(request, response);
            return;
        }

        // 获取 Authorization 头
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(response, "缺少认证令牌");
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        if (token.isEmpty()) {
            writeUnauthorized(response, "认证令牌为空");
            return;
        }

        // 验证 JWT
        try {
            if (!jwtUtil.validateToken(token)) {
                writeUnauthorized(response, "认证令牌无效或已过期");
                return;
            }

            Long accountId = jwtUtil.getAccountId(token);
            String username = jwtUtil.getUsername(token);

            // 将用户信息存入请求属性，供 Controller 使用
            request.setAttribute(REQUEST_ATTR_ACCOUNT_ID, accountId);
            request.setAttribute(REQUEST_ATTR_USERNAME, username);

            chain.doFilter(request, response);

        } catch (Exception e) {
            log.error("JWT 认证异常", e);
            writeUnauthorized(response, "认证服务异常");
        }
    }

    /**
     * 判断请求路径是否在白名单中
     */
    private boolean isWhiteListed(String path) {
        return WHITE_LIST.stream().anyMatch(path::startsWith);
    }

    /**
     * 返回 401 未认证响应
     */
    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        ApiResponse<Void> apiResponse = ApiResponse.fail(401, message);
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }

    /**
     * 从请求中获取当前登录的账户ID
     */
    public static Long getCurrentAccountId(HttpServletRequest request) {
        return (Long) request.getAttribute(REQUEST_ATTR_ACCOUNT_ID);
    }

    /**
     * 从请求中获取当前登录的用户名
     */
    public static String getCurrentUsername(HttpServletRequest request) {
        return (String) request.getAttribute(REQUEST_ATTR_USERNAME);
    }
}
