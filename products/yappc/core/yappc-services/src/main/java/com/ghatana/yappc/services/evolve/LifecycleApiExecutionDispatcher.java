package com.ghatana.yappc.services.evolve;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.yappc.api.LifecycleApiController;
import com.ghatana.yappc.api.LifecycleExecutionRepository;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Dispatches evolve handoffs through the existing lifecycle execute controller pipeline.
 *
 * @doc.type class
 * @doc.purpose Trigger lifecycle execution from evolve handoffs via existing lifecycle API pipeline
 * @doc.layer service
 * @doc.pattern Adapter
 */
public final class LifecycleApiExecutionDispatcher implements EvolutionLifecycleExecutionDispatcher {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final LifecycleApiController lifecycleApiController;
    private final LifecycleExecutionRepository lifecycleExecutionRepository;
    private final ObjectMapper objectMapper;

    public LifecycleApiExecutionDispatcher(
            @NotNull LifecycleApiController lifecycleApiController,
            @NotNull LifecycleExecutionRepository lifecycleExecutionRepository,
            @NotNull ObjectMapper objectMapper
    ) {
        this.lifecycleApiController = lifecycleApiController;
        this.lifecycleExecutionRepository = lifecycleExecutionRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Promise<String> dispatch(@NotNull EvolutionLifecycleExecutionRequest request) {
        final String payload;
        try {
            payload = toLifecyclePayload(request);
        } catch (Exception e) {
            return Promise.ofException(new IllegalStateException("Failed to encode lifecycle dispatch payload", e));
        }

        HttpRequest httpRequest = HttpRequest.post("http://localhost/api/v1/yappc/lifecycle/execute")
            .withBody(ByteBuf.wrapForReading(payload.getBytes(StandardCharsets.UTF_8)))
            .build();
        httpRequest.attach(Principal.class, new Principal(
            request.requestedBy(),
            List.of("system", "builder"),
            request.tenantId()
        ));

        return lifecycleApiController.executeFullLifecycle(httpRequest)
                .then(response -> resolveExecutionId(request, response));
    }

    private Promise<String> resolveExecutionId(EvolutionLifecycleExecutionRequest request, HttpResponse response) {
        if (response.getCode() != 200) {
            return Promise.ofException(new IllegalStateException(
                    "Lifecycle execution dispatch failed: HTTP " + response.getCode()));
        }

        try {
            String body = response.getBody().asString(StandardCharsets.UTF_8);
            Map<String, Object> decoded = objectMapper.readValue(body, MAP_TYPE);
            String status = resolveStatus(decoded);
            if ("FAILED".equalsIgnoreCase(status)) {
                return Promise.ofException(new IllegalStateException("Lifecycle execution returned FAILED status"));
            }

            String executionId = resolveExecutionIdFromPayload(decoded).orElse(null);
            if (executionId != null && !executionId.isBlank()) {
                return Promise.of(executionId);
            }

            return lifecycleExecutionRepository.findByProject(request.tenantId(), request.projectId(), 1)
                    .then(executions -> {
                        if (executions == null || executions.isEmpty()) {
                            return Promise.ofException(new IllegalStateException(
                                    "Lifecycle execution completed but no persisted execution record was found"));
                        }
                        return Promise.of(executions.getFirst().executionId());
                    });
        } catch (Exception e) {
            return Promise.ofException(new IllegalStateException("Failed to parse lifecycle execution response", e));
        }
    }

    private String toLifecyclePayload(EvolutionLifecycleExecutionRequest request) throws Exception {
        Map<String, Object> metadata = request.metadata();
        String workspaceId = asString(metadata.get("workspaceId"), "workspace-unavailable");
        String environment = asString(metadata.get("environment"), "staging");
        String intentText = asString(metadata.get("intentText"),
                "Execute approved evolve handoff for intent ref " + request.productUnitIntentRef());

        Map<String, Object> payload = Map.of(
                "intentInput", Map.of(
                        "rawText", intentText,
                        "format", "text",
                        "structuredData", Map.of("productUnitIntentRef", request.productUnitIntentRef()),
                        "tenantId", request.tenantId(),
                        "userId", request.requestedBy()
                ),
                "tenantId", request.tenantId(),
                "projectId", request.projectId(),
                "workspaceId", workspaceId,
                "environment", environment
        );
        return objectMapper.writeValueAsString(payload);
    }

    @SuppressWarnings("unchecked")
    private static Optional<String> resolveExecutionIdFromPayload(Map<String, Object> decoded) {
        Object metadataObj = decoded.get("metadata");
        if (!(metadataObj instanceof Map<?, ?> rawMetadata)) {
            return Optional.empty();
        }
        Map<String, Object> metadata = (Map<String, Object>) rawMetadata;
        String executionId = asString(metadata.get("executionId"), "");
        return executionId.isBlank() ? Optional.empty() : Optional.of(executionId);
    }

    @SuppressWarnings("unchecked")
    private static String resolveStatus(Map<String, Object> decoded) {
        Object metadataObj = decoded.get("metadata");
        if (!(metadataObj instanceof Map<?, ?> rawMetadata)) {
            return "UNKNOWN";
        }
        Map<String, Object> metadata = (Map<String, Object>) rawMetadata;
        return asString(metadata.get("status"), "UNKNOWN");
    }

    private static String asString(Object value, String fallback) {
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        return fallback;
    }
}
