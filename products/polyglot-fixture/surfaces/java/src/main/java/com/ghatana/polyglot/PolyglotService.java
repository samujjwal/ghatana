package com.ghatana.polyglot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Polyglot Fixture Java Service
 * 
 * Demonstrates Java service surface integration with the Ghatana platform.
 * 
 * @doc.type class
 * @doc.purpose Java service surface for polyglot fixture product
 * @doc.layer product
 * @doc.pattern Service
 */
@SpringBootApplication
@RestController
public class PolyglotService {

    public static void main(String[] args) {
        SpringApplication.run(PolyglotService.class, args);
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("UP", "java-service", "1.0.0");
    }

    @GetMapping("/api/ping")
    public PingResponse ping() {
        return new PingResponse("pong", System.currentTimeMillis());
    }

    record HealthResponse(String status, String service, String version) {}
    record PingResponse(String message, long timestamp) {}
}
