package com.ghatana.yappc.services.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.http.HttpHeader;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * @doc.type class
 * @doc.purpose Encapsulates lifecycle approval HTTP request handling for listing and deciding approval requests.
 * @doc.layer product
 * @doc.pattern Handler
 */
final class ApprovalHttpHandlers {

    private static final HttpHeader TENANT_HEADER = HttpHeaders.of("X-Tenant-Id");

    private final HumanApprovalService humanApprovalService;
    private final ObjectMapper objectMapper;

    ApprovalHttpHandlers(HumanApprovalService humanApprovalService, ObjectMapper objectMapper) {
        this.humanApprovalService = Objects.requireNonNull(humanApprovalService, "humanApprovalService");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    Promise<HttpResponse> listPending(HttpRequest request) {
        String tenantId = extractTenantId(request);
        if (tenantId == null) {
            return Promise.of(badRequest("Missing required X-Tenant-Id header"));
        }

        try {
            String json = objectMapper.writeValueAsString(humanApprovalService.allPending(tenantId));
            return HttpResponse.ok200().withJson(json).toPromise();
        } catch (Exception exception) {
            return Promise.of(HttpResponse.ofCode(500).withPlainText("Serialization error").build());
        }
    }

    Promise<HttpResponse> create(HttpRequest request) {
        String tenantId = extractTenantId(request);
        if (tenantId == null) {
            return Promise.of(badRequest("Missing required X-Tenant-Id header"));
        }

        return request.loadBody().then(body -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = objectMapper.readValue(
                        body.getString(StandardCharsets.UTF_8), Map.class);

                String projectId = (String) payload.get("projectId");
                String requestingAgentId = (String) payload.get("requestingAgentId");
                String approvalTypeStr = (String) payload.get("approvalType");

                if (projectId == null || projectId.isBlank()) {
                    return Promise.of(badRequest("Missing required field: projectId"));
                }
                if (approvalTypeStr == null || approvalTypeStr.isBlank()) {
                    return Promise.of(badRequest("Missing required field: approvalType"));
                }

                ApprovalRequest.ApprovalType approvalType;
                try {
                    approvalType = ApprovalRequest.ApprovalType.valueOf(approvalTypeStr);
                } catch (IllegalArgumentException e) {
                    return Promise.of(badRequest("Invalid approvalType: " + approvalTypeStr));
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> contextMap = payload.containsKey("context")
                        ? (Map<String, Object>) payload.get("context")
                        : Map.of();
                String fromPhase = (String) contextMap.getOrDefault("fromPhase", "");
                String toPhase = (String) contextMap.getOrDefault("toPhase", "");
                String blockReason = (String) contextMap.getOrDefault("blockReason", "");
                @SuppressWarnings("unchecked")
                List<String> unmetCriteria = contextMap.containsKey("unmetCriteria")
                        ? (List<String>) contextMap.get("unmetCriteria")
                        : List.of();
                @SuppressWarnings("unchecked")
                List<String> missingArtifacts = contextMap.containsKey("missingArtifacts")
                        ? (List<String>) contextMap.get("missingArtifacts")
                        : List.of();

                ApprovalRequest.ApprovalContext context = new ApprovalRequest.ApprovalContext(
                        fromPhase, toPhase, blockReason, unmetCriteria, missingArtifacts);

                return humanApprovalService.requestApproval(
                        tenantId, projectId, requestingAgentId, approvalType, context)
                        .map(created -> {
                            try {
                                return HttpResponse.ofCode(201)
                                        .withJson(objectMapper.writeValueAsString(created))
                                        .build();
                            } catch (Exception ex) {
                                return HttpResponse.ofCode(500).withPlainText("Serialization error").build();
                            }
                        });
            } catch (Exception exception) {
                return Promise.of(badRequest(exception.getMessage()));
            }
        });
    }

    Promise<HttpResponse> getById(HttpRequest request, String requestId) {
        String tenantId = extractTenantId(request);
        if (tenantId == null) {
            return Promise.of(badRequest("Missing required X-Tenant-Id header"));
        }

        Optional<ApprovalRequest> found = humanApprovalService.findById(tenantId, requestId);
        if (found.isEmpty()) {
            return Promise.of(HttpResponse.ofCode(404)
                    .withJson(errorJson("Approval request not found: " + requestId))
                    .build());
        }

        try {
            return Promise.of(HttpResponse.ok200()
                    .withJson(objectMapper.writeValueAsString(found.get()))
                    .build());
        } catch (Exception exception) {
            return Promise.of(HttpResponse.ofCode(500).withPlainText("Serialization error").build());
        }
    }

    Promise<HttpResponse> approve(HttpRequest request, String requestId) {
        return decide(request, requestId, true);
    }

    Promise<HttpResponse> reject(HttpRequest request, String requestId) {
        return decide(request, requestId, false);
    }

    private Promise<HttpResponse> decide(HttpRequest request, String requestId, boolean approved) {
        String tenantId = extractTenantId(request);
        if (tenantId == null) {
            return Promise.of(badRequest("Missing required X-Tenant-Id header"));
        }

        return request.loadBody().then(body -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = objectMapper.readValue(
                        body.getString(StandardCharsets.UTF_8), Map.class);
                String decidedBy = (String) payload.getOrDefault("decidedBy", "unknown");

                Promise<ApprovalRequest> decision = approved
                        ? humanApprovalService.approve(tenantId, requestId, decidedBy)
                        : humanApprovalService.reject(tenantId, requestId, decidedBy);

                return decision.map(result -> {
                    try {
                        return HttpResponse.ok200()
                                .withJson(objectMapper.writeValueAsString(result))
                                .build();
                    } catch (Exception exception) {
                        return HttpResponse.ofCode(500).withPlainText("Serialization error").build();
                    }
                }).then(
                        Promise::of,
                        error -> Promise.of(HttpResponse.ofCode(409)
                                .withJson(errorJson(error.getMessage()))
                                .build())
                );
            } catch (IllegalArgumentException | IllegalStateException domainError) {
                // thrown synchronously by service (e.g. request not found, already decided)
                return Promise.of(HttpResponse.ofCode(409)
                        .withJson(errorJson(domainError.getMessage()))
                        .build());
            } catch (Exception exception) {
                return Promise.of(badRequest(exception.getMessage()));
            }
        });
    }

    private String extractTenantId(HttpRequest request) {
        String tenantId = request.getHeader(TENANT_HEADER);
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        return tenantId;
    }

    private HttpResponse badRequest(String message) {
        return HttpResponse.ofCode(400)
                .withJson(errorJson(message))
                .build();
    }

    private String errorJson(String message) {
        return "{\"error\":\"" + message + "\"}";
    }
}