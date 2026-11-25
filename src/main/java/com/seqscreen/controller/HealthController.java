package com.seqscreen.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Cheap liveness probe. It touches nothing but its own string, so a host health check answers in
 * milliseconds even while a dataset is being built, and the page can tell "server is up" from
 * "dataset is ready".
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
