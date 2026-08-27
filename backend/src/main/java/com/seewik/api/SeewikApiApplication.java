package com.seewik.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SeewikApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(SeewikApiApplication.class, args);
    }
}
