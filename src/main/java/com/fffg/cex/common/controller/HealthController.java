package com.fffg.cex.common.controller;

import com.fffg.cex.common.result.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查接口，用于验证服务是否正常启动
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "UP");
        data.put("system", "mini-cex");
        data.put("timestamp", LocalDateTime.now().toString());
        return ApiResponse.success(data);
    }
}
