/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.policy;

import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.yappc.api.domain.LearnedPolicy;
import com.ghatana.yappc.api.repository.LearnedPolicyRepository;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * REST controller exposing the learned policies for a given agent.
 *
 * <h2>Endpoint (plan 9.5.4)</h2>
 * <pre>
 * GET /api/v1/agents/{agentId}/policies
 *     ?minConfidence=0.9   (optional, default 0.0 = all)
 *     &limit=50            (optional, default 50)
 *     &offset=0            (optional, default 0)
 * </pre>
 *
 * <h2>Response (200 OK)</h2>
 * <pre>
 * {
 *   "agentId": "requirements-analyst-v2",
 *   "tenantId": "tenant-alpha",
 *   "total": 2,
 *   "offset": 0,
 *   "limit": 50,
 *   "minConfidence": 0.9,
 *   "items": [
 *     {
 *       "id": "policy-uuid",
 *       "name": "Learned: extract functional requirements...",
 *       "description": "...",
 *       "confidence": 0.95,
 *       "version": 1,
 *       "source": "agent_reflection",
 *       "createdAt": "2025-01-01T10:00:00Z",
 *       "updatedAt": "2025-01-01T10:00:00Z"
 *     }
 *   ]
 * }
 * </pre>
 *
 * @doc.type class
 * @doc.purpose REST API: learned policy query (plan 9.5.4)
 * @doc.layer product
 * @doc.pattern Service, Controller
 */
public class LearnedPolicyController {

    private static final Logger log = LoggerFactory.getLogger(LearnedPolicyController.class);

    private static final int    DEFAULT_LIMIT       = 50;
    private static final double DEFAULT_MIN_CONFIDENCE = 0.0;

    private final LearnedPolicyRepository policyRepository;
    private final TenantContextExtractor  tenantExtractor;

    public LearnedPolicyController(LearnedPolicyRepository policyRepository,
                                    TenantContextExtractor tenantExtractor) {
        this.policyRepository = Objects.requireNonNull(policyRepository, "policyRepository");
        this.tenantExtractor  = Objects.requireNonNull(tenantExtractor,  "tenantExtractor");
    }

    /**
     * Handles {@code GET /api/v1/agents/:agentId/policies}.
     *
     * <p>Returns all learned policies for the tenant-scoped agent, paginated and
     * optionally filtered by {@code minConfidence}.
     *
     * @param request the incoming HTTP request
     * @param agentId the agent whose policies to retrieve
     * @return JSON response with policy list
     */
    public Promise<HttpResponse> getPolicies(HttpRequest request, String agentId) {
        return tenantExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    int    limit         = parseIntParam(request, "limit",         DEFAULT_LIMIT);
                    int    offset        = parseIntParam(request, "offset",        0);
                    double minConfidence = parseDoubleParam(request, "minConfidence", DEFAULT_MIN_CONFIDENCE);

                    Promise<List<LearnedPolicy>> fetcher = minConfidence > 0.0
                            ? policyRepository.findAboveConfidence(ctx.tenantId(), minConfidence)
                              .then(all -> {
                                  // Filter by agentId (findAboveConfidence is tenant-wide)
                                  List<LearnedPolicy> forAgent = all.stream()
                                          .filter(p -> agentId.equals(p.getAgentId()))
                                          .collect(java.util.stream.Collectors.toList());
                                  return Promise.of(forAgent);
                              })
                            : policyRepository.findByAgent(ctx.tenantId(), agentId);

                    return fetcher.then(policies -> {
                        int total  = policies.size();
                        int safeOff = Math.max(0, Math.min(offset, total));
                        int safeLim = Math.max(1, limit);
                        List<LearnedPolicy> page = policies.subList(
                                safeOff, Math.min(safeOff + safeLim, total));

                        Map<String, Object> body = new LinkedHashMap<>();
                        body.put("agentId",       agentId);
                        body.put("tenantId",       ctx.tenantId());
                        body.put("total",          total);
                        body.put("offset",         safeOff);
                        body.put("limit",          safeLim);
                        body.put("minConfidence",  minConfidence);
                        body.put("items",          page.stream()
                                .map(LearnedPolicyController::toItemMap)
                                .collect(java.util.stream.Collectors.toList()));

                        return Promise.of(ApiResponse.ok(body));
                    });
                })
                .mapException(ApiResponse::fromException);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static Map<String, Object> toItemMap(LearnedPolicy p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",          p.getId());
        m.put("name",        p.getName());
        m.put("description", p.getDescription());
        m.put("confidence",  p.getConfidence());
        m.put("version",     p.getVersion());
        m.put("source",      p.getSource());
        m.put("createdAt",   p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
        m.put("updatedAt",   p.getUpdatedAt() != null ? p.getUpdatedAt().toString() : null);
        return m;
    }

    private static int parseIntParam(HttpRequest req, String name, int defaultValue) {
        try {
            String val = req.getQueryParameter(name);
            return val != null ? Integer.parseInt(val) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static double parseDoubleParam(HttpRequest req, String name, double defaultValue) {
        try {
            String val = req.getQueryParameter(name);
            return val != null ? Double.parseDouble(val) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
