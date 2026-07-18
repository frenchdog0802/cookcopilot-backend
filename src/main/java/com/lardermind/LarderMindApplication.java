package com.lardermind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LarderMindApplication {

    public static void main(String[] args) {
        SpringApplication.run(LarderMindApplication.class, args);
    }

}
