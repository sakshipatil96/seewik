package com.seewik.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    static List<String> allowedOrigins() {
        return List.of(
                "https://seewik.web.app",
                "https://seewik.firebaseapp.com",
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "http://localhost:5174",
                "http://127.0.0.1:5174",
                "http://localhost:4173",
                "http://127.0.0.1:4173",
                "http://localhost:4174",
                "http://127.0.0.1:4174"
        );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization");
    }
}
