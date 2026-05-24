package com.ghatana.orchestrator.deployment.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.http.HttpClient;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.HttpHeaders;
import io.activej.promise.Promise;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * HTTP client for Kernel lifecycle API.
 *
 * <p><b>Purpose</b><br>
 * Submits governed Kernel lifecycle action requests (deploy, rollback, promote) from the AEP gateway.
 * Ensures all lifecycle actions go through the governed Kernel system with proper adapter contracts.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * KernelLifecycleClient client = new KernelLifecycleClient(httpClient, objectMapper, "http://localhost:8080");
 * LifecycleRunResult result = client.executeLifecyclePhase(
 *     "data-cloud-pipeline",
 *     "deploy",
 *     "production",
 *     Map.of("tenantId", "acme-corp")
 * ).await();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose HTTP client for Kernel lifecycle API
 * @doc.layer product
 * @doc.pattern Client
 */
@Slf4j
@RequiredArgsConstructor
public class KernelLifecycleClient {
    private static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP =
            new TypeReference<>() {
            };

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String kernelLifecycleBaseUrl;

    /**
     * Execute a lifecycle phase through the Kernel lifecycle API.
     *
     * <p>Submits a governed lifecycle action request to the Kernel system,
     * ensuring proper adapter contract compliance and evidence collection.
     *
     * @param productUnitId product unit identifier
     * @param phase lifecycle phase (e.g., deploy, rollback, promote)
     * @param environment target environment
     * @param headers additional headers for authentication/authorization
     * @return Promise<LifecycleRunResult> with execution result
     */
    public Promise<LifecycleRunResult> executeLifecyclePhase(
            String productUnitId,
            String phase,
            String environment,
            Map<String, String> headers) {
        MDC.put("productUnitId", productUnitId);
        MDC.put("phase", phase);
        MDC.put("environment", environment);

        try {
            String correlationId = UUID.randomUUID().toString();
            MDC.put("correlationId", correlationId);

            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "phase", phase,
                    "environment", environment,
                    "correlationId", correlationId,
                    "dryRun", false,
                    "providerMode", "bootstrap"
            ));

            String url = String.format("%s/api/kernel/product-units/%s/lifecycle/execute",
                    kernelLifecycleBaseUrl, productUnitId);

            log.info("Submitting governed Kernel lifecycle action request: {} for product unit {}",
                    phase, productUnitId);

            HttpRequest request = HttpRequest.post(url)
                    .withBody(requestBody.getBytes())
                    .withHeader(HttpHeaders.of("Content-Type"), "application/json")
                    .withHeader(HttpHeaders.of("X-Correlation-ID"), correlationId)
                    .build();

            // Add authentication/authorization headers
            if (headers != null) {
                HttpRequest.Builder builder = HttpRequest.post(url)
                        .withBody(requestBody.getBytes())
                        .withHeader(HttpHeaders.of("Content-Type"), "application/json")
                        .withHeader(HttpHeaders.of("X-Correlation-ID"), correlationId);
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    builder = builder.withHeader(HttpHeaders.of(entry.getKey()), entry.getValue());
                }
                request = builder.build();
            }

            return httpClient.request(request)
                    .then(response -> parseResponse(response, productUnitId, phase, correlationId))
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.error("Kernel lifecycle action request failed: {} for product unit {}",
                                    phase, productUnitId, error);
                            metricsIncrement("kernel.lifecycle.request.failed",
                                    "phase", phase,
                                    "product_unit_id", productUnitId);
                        } else {
                            log.info("Kernel lifecycle action request succeeded: {} for product unit {}, runId: {}",
                                    phase, productUnitId, result.runId());
                            metricsIncrement("kernel.lifecycle.request.success",
                                    "phase", phase,
                                    "product_unit_id", productUnitId);
                        }
                        MDC.remove("correlationId");
                        MDC.remove("productUnitId");
                        MDC.remove("phase");
                        MDC.remove("environment");
                    });
        } catch (Exception e) {
            log.error("Failed to serialize Kernel lifecycle request", e);
            MDC.remove("correlationId");
            MDC.remove("productUnitId");
            MDC.remove("phase");
            MDC.remove("environment");
            return Promise.ofException(e);
        }
    }

    /**
     * Create a lifecycle plan through the Kernel lifecycle API.
     *
     * <p>Validates and creates a lifecycle plan without executing it.
     *
     * @param productUnitId product unit identifier
     * @param phase lifecycle phase
     * @param environment target environment
     * @param headers additional headers for authentication/authorization
     * @return Promise<LifecyclePlanResult> with plan result
     */
    public Promise<LifecyclePlanResult> createLifecyclePlan(
            String productUnitId,
            String phase,
            String environment,
            Map<String, String> headers) {
        MDC.put("productUnitId", productUnitId);
        MDC.put("phase", phase);
        MDC.put("environment", environment);

        try {
            String correlationId = UUID.randomUUID().toString();
            MDC.put("correlationId", correlationId);

            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "phase", phase,
                    "environment", environment,
                    "correlationId", correlationId,
                    "providerMode", "bootstrap"
            ));

            String url = String.format("%s/api/kernel/product-units/%s/lifecycle/plans",
                    kernelLifecycleBaseUrl, productUnitId);

            log.info("Creating Kernel lifecycle plan: {} for product unit {}", phase, productUnitId);

            HttpRequest request = HttpRequest.post(url)
                    .withBody(requestBody.getBytes())
                    .withHeader(HttpHeaders.of("Content-Type"), "application/json")
                    .withHeader(HttpHeaders.of("X-Correlation-ID"), correlationId)
                    .build();

            if (headers != null) {
                HttpRequest.Builder builder = HttpRequest.post(url)
                        .withBody(requestBody.getBytes())
                        .withHeader(HttpHeaders.of("Content-Type"), "application/json")
                        .withHeader(HttpHeaders.of("X-Correlation-ID"), correlationId);
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    builder = builder.withHeader(HttpHeaders.of(entry.getKey()), entry.getValue());
                }
                request = builder.build();
            }

            return httpClient.request(request)
                    .then(response -> parsePlanResponse(response, productUnitId, phase, correlationId))
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.error("Kernel lifecycle plan creation failed: {} for product unit {}",
                                    phase, productUnitId, error);
                        } else {
                            log.info("Kernel lifecycle plan created: {} for product unit {}, runId: {}",
                                    phase, productUnitId, result.runId());
                        }
                        MDC.remove("correlationId");
                        MDC.remove("productUnitId");
                        MDC.remove("phase");
                        MDC.remove("environment");
                    });
        } catch (Exception e) {
            log.error("Failed to serialize Kernel lifecycle plan request", e);
            MDC.remove("correlationId");
            MDC.remove("productUnitId");
            MDC.remove("phase");
            MDC.remove("environment");
            return Promise.ofException(e);
        }
    }

    private Promise<LifecycleRunResult> parseResponse(
            HttpResponse response,
            String productUnitId,
            String phase,
            String correlationId) {
        try {
            if (response.getCode() >= 200 && response.getCode() < 300) {
                String body = response.getBody().asString(StandardCharsets.UTF_8);
                Map<String, Object> data = objectMapper.readValue(body, STRING_OBJECT_MAP);
                
                return Promise.of(new LifecycleRunResult(
                        (String) data.get("runId"),
                        (String) data.get("correlationId"),
                        (String) data.get("status"),
                        phase,
                        productUnitId,
                        null
                ));
            } else {
                String errorBody = response.getBody().asString(StandardCharsets.UTF_8);
                log.error("Kernel lifecycle API returned error: {} - {}", response.getCode(), errorBody);
                return Promise.ofException(new RuntimeException(
                        String.format("Kernel lifecycle request failed: %d - %s", response.getCode(), errorBody)));
            }
        } catch (Exception e) {
            log.error("Failed to parse Kernel lifecycle response", e);
            return Promise.ofException(e);
        }
    }

    private Promise<LifecyclePlanResult> parsePlanResponse(
            HttpResponse response,
            String productUnitId,
            String phase,
            String correlationId) {
        try {
            if (response.getCode() >= 200 && response.getCode() < 300) {
                String body = response.getBody().asString(StandardCharsets.UTF_8);
                Map<String, Object> data = objectMapper.readValue(body, STRING_OBJECT_MAP);
                
                return Promise.of(new LifecyclePlanResult(
                        (String) data.get("runId"),
                        (String) data.get("correlationId"),
                        phase,
                        productUnitId
                ));
            } else {
                String errorBody = response.getBody().asString(StandardCharsets.UTF_8);
                log.error("Kernel lifecycle API returned error: {} - {}", response.getCode(), errorBody);
                return Promise.ofException(new RuntimeException(
                        String.format("Kernel lifecycle plan request failed: %d - %s", response.getCode(), errorBody)));
            }
        } catch (Exception e) {
            log.error("Failed to parse Kernel lifecycle plan response", e);
            return Promise.ofException(e);
        }
    }

    private void metricsIncrement(String metricName, String... tags) {
        // Placeholder for metrics collection
        // In production, this would use MetricsCollector
        log.debug("Metric: {} with tags: {}", metricName, String.join(", ", tags));
    }

    /**
     * Result of a lifecycle phase execution.
     */
    public record LifecycleRunResult(
            String runId,
            String correlationId,
            String status,
            String phase,
            String productUnitId,
            String failureReason
    ) {}

    /**
     * Result of a lifecycle plan creation.
     */
    public record LifecyclePlanResult(
            String runId,
            String correlationId,
            String phase,
            String productUnitId
    ) {}
}
