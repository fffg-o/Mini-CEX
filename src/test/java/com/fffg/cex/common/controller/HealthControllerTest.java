package com.fffg.cex.common.controller;

import com.fffg.cex.common.result.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HealthControllerTest {

    @Test
    void testHealth() {
        HealthController controller = new HealthController();
        ApiResponse<Map<String, Object>> response = controller.health();

        assertEquals(0, response.getCode());
        assertEquals("success", response.getMessage());
        assertNotNull(response.getData());
        assertEquals("UP", response.getData().get("status"));
        assertEquals("mini-cex", response.getData().get("system"));
        assertNotNull(response.getData().get("timestamp"));
    }
}
