package com.seewik.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WebConfigTest {
    @Test
    void allowsSupportedLocalDevelopmentOrigins() {
        var origins = WebConfig.allowedOrigins();

        assertTrue(origins.contains("http://localhost:5173"));
        assertTrue(origins.contains("http://127.0.0.1:5173"));
        assertTrue(origins.contains("http://localhost:5174"));
        assertTrue(origins.contains("http://127.0.0.1:5174"));
        assertTrue(origins.contains("http://localhost:4173"));
        assertTrue(origins.contains("http://127.0.0.1:4173"));
        assertTrue(origins.contains("http://localhost:4174"));
        assertTrue(origins.contains("http://127.0.0.1:4174"));
    }
}
