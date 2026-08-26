package com.seewik.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("https://seewik.web.app", "https://seewik.firebaseapp.com", "http://localhost:5173")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization");
    }
}
