package com.ghatana.products.multi.agent.orchestrator.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.products.multi.agent.orchestrator.config.OrchestratorAppConfig;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Health check servlet for the orchestrator application.
 * 
 * <p>This servlet provides a simple health check endpoint that returns
 * a 200 OK response with some basic information about the application.
 * 
 * @author Platform Team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class HealthServlet implements AsyncServlet {
    
    private final OrchestratorAppConfig config;
    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();
    
    @Override
    public Promise<HttpResponse> serve(HttpRequest request) {
        if (!request.getPath().equals(config.getHealthPath())) {
            return HttpResponse.notFound404().toPromise();
        }

        try {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "UP");
            health.put("timestamp", System.currentTimeMillis());
            health.put("version", "1.0.0");

            byte[] body = objectMapper.writeValueAsBytes(health);

            return ResponseBuilder.ok()
                    .header("Content-Type", "application/json")
                    .bytes(body, "application/json")
                    .build()
                    .toPromise();
        } catch (Exception e) {
            log.error("Error generating health check response", e);
            return ResponseBuilder.internalServerError()
                    .text("Error generating health check response")
                    .build()
                    .toPromise();
        }
    }
}
