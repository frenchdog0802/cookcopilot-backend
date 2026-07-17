package com.cookplanner.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cookplanner.dto.HealthResponse;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("UP", System.currentTimeMillis() / 1000);
    }
}
