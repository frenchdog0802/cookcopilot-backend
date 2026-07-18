package com.cookcopilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CookCopilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(CookCopilotApplication.class, args);
    }

}
