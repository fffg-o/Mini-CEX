package com.fffg.cex.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * JWT 签名密钥（Base64 编码）
     */
    private String secret;

    /**
     * Access Token 过期时间（毫秒），默认 1 小时
     */
    private long expiration = 3600000L;

    /**
     * 签发者
     */
    private String issuer = "mini-cex";
}
