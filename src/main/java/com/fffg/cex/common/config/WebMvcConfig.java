package com.fffg.cex.common.config;

import com.fffg.cex.auth.filter.JwtAuthFilter;
import com.fffg.cex.auth.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Web MVC 配置：注册自定义过滤器
 */
@Configuration
public class WebMvcConfig {

    /**
     * 注册 JWT 认证过滤器，拦截所有请求，优先级在 TraceIdFilter 之后
     */
    @Bean
    public FilterRegistrationBean<Filter> jwtAuthFilter(JwtUtil jwtUtil, ObjectMapper objectMapper) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new JwtAuthFilter(jwtUtil, objectMapper));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1); // 在 TraceIdFilter 之后
        registration.setName("jwtAuthFilter");
        return registration;
    }
}
