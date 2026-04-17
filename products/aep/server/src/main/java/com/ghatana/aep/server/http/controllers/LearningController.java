/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.ghatana.aep.learning.EpisodeLearningPipeline;
import com.ghatana.aep.server.http.HttpHelper;
import com.ghatana.agent.learning.review.HumanReviewQueue;
import com.ghatana.agent.learning.review.ReviewDecision;
import com.ghatana.agent.learning.review.ReviewFilter;
import com.ghatana.agent.learning.review.ReviewItemType;
import com.ghatana.datacloud.DataCloudClient;
import io.activej.http.*;
import io.activej.promise.Promise;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Controller for learning system endpoints (episodes, policies, reflection).
 *
 * @doc.type class
 * @doc.purpose Learning system episode/policy management
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle reflect
 */
public class LearningController {

    private static final Logger log = LoggerFactory.getLogger(LearningController.class);

    @Nullable
    private final DataCloudClient agentDataCloud;
    @Nullable
    private final HumanReviewQueue humanReviewQueue;
    @Nullable
    private final EpisodeLearningPipeline learningPipeline;

    public LearningController(@Nullable DataCloudClient agentDataCloud) {
        this(agentDataCloud, null, null);
    }

    public LearningController(@Nullable DataCloudClient agentDataCloud,
                               @Nullable EpisodeLearningPipeline learningPipeline) {
        this(agentDataCloud, null, learningPipeline);
    }

    public LearningController(@Nullable DataCloudClient agentDataCloud,
                               @Nullable HumanReviewQueue humanReviewQueue,
                               @Nullable EpisodeLearningPipeline learningPipeline) {
        this.agentDataCloud = agentDataCloud;
        this.humanReviewQueue = humanReviewQueue;
        this.learningPipeline = learningPipeline;
    }

    public Promise<HttpResponse> handleListEpisodes(HttpRequest request) {
        if (agentDataCloud == null) {
            return Promise.of(HttpHelper.errorResponse(503,
                "Episode store not available — DataCloudClient not configured"));
        }
        String tenantId = HttpHelper.resolveTenantId(request);
        String agentFilter = request.getQueryParameter("agentId");
        List<DataCloudClient.Filter> filters = new ArrayList<>();
        filters.add(DataCloudClient.Filter.eq("type", "EPISODIC"));
        if (agentFilter != null && !agentFilter.isBlank()) {
            filters.add(DataCloudClient.Filter.eq("agentId", agentFilter));
        }
        DataCloudClient.Query query = DataCloudClient.Query.builder()
            .filters(filters)
            .limit(200)
            .build();
        return agentDataCloud.query(tenantId, "dc_memory", query)
            .map(items -> {
                List<Map<String, Object>> episodes = items.stream()
                    .map(e -> {
                        Map<String, Object> ep = new java.util.HashMap<>(e.data());
                        ep.put("id", e.id());
                        return ep;
                    })
                    .toList();
                return HttpHelper.jsonResponse(Map.of(
                    "episodes", episodes,
                    "count", episodes.size(),
                    "tenantId", tenantId,
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[learning] episodes query failed for tenant={}: {}",
                    tenantId, e.getMessage(), e);
                return Promise.of(HttpHelper.errorResponse(500,
                    "Failed to list episodes: " + e.getMessage()));
            });
    }

    public Promise<HttpResponse> handleListPolicies(HttpRequest request) {
        String tenantId = HttpHelper.resolveTenantId(request);
        if (humanReviewQueue == null) {
            return Promise.of(HttpHelper.jsonResponse(Map.of(
                "policies", List.of(),
                "count", 0,
                "tenantId", tenantId,
                "configured", false,
                "message", "Policy store not available — start AEP with AepLearningModule",
                "timestamp", Instant.now().toString()
            )));
        }
        ReviewFilter filter = new ReviewFilter(tenantId, ReviewItemType.POLICY, null, null, 200);
        return humanReviewQueue.getPending(filter)
            .map(items -> {
                List<Map<String, Object>> policies = items.stream()
                    .map(item -> {
                        Map<String, Object> p = new java.util.HashMap<>();
                        p.put("id", item.getReviewId());
                        p.put("agentId", item.getSkillId());
                        p.put("version", item.getProposedVersion());
                        p.put("status", item.getStatus().name());
                        p.put("confidence", item.getConfidenceScore());
                        p.put("summary", item.getEvaluationSummary() != null
                            ? item.getEvaluationSummary() : "");
                        p.put("context", item.getContext());
                        p.put("createdAt", item.getCreatedAt().toString());
                        if (item.getDecidedAt() != null) {
                            p.put("decidedAt", item.getDecidedAt().toString());
                        }
                        return p;
                    })
                    .toList();
                return HttpHelper.jsonResponse(Map.of(
                    "policies", policies,
                    "count", policies.size(),
                    "tenantId", tenantId,
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[learning] policies query failed for tenant={}: {}",
                    tenantId, e.getMessage(), e);
                return Promise.of(HttpHelper.errorResponse(500,
                    "Failed to list policies: " + e.getMessage()));
            });
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleApprovePolicy(HttpRequest request) {
        if (humanReviewQueue == null) {
            return Promise.of(HttpHelper.errorResponse(501, "Learning module not configured"));
        }
        String policyId = request.getPathParameter("policyId");
        if (policyId == null || policyId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400,
                "policyId path parameter is required"));
        }
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> data = body.isBlank()
                    ? Map.of() : HttpHelper.mapper().readValue(body, Map.class);
                String reviewer = (String) data.getOrDefault("reviewer", "unknown");
                String rationale = (String) data.getOrDefault("rationale",
                    "Approved via learning API");
                String notes = (String) data.get("notes");
                ReviewDecision decision = new ReviewDecision(
                    reviewer, rationale, Instant.now(), notes);
                return humanReviewQueue.approve(policyId, decision)
                    .map(item -> HttpHelper.jsonResponse(Map.of(
                        "id", item.getReviewId(),
                        "status", item.getStatus().name(),
                        "decidedAt", Instant.now().toString()
                    )))
                    .then(Promise::of, e -> {
                        log.warn("[learning] approve policy failed policyId={}: {}",
                            policyId, e.getMessage());
                        return Promise.of(HttpHelper.errorResponse(404,
                            "Policy not found: " + policyId));
                    });
            } catch (Exception e) {
                return Promise.of(HttpHelper.errorResponse(400,
                    "Invalid request: " + e.getMessage()));
            }
        }, e -> Promise.of(HttpHelper.errorResponse(400, "Failed to read request body")));
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleRejectPolicy(HttpRequest request) {
        if (humanReviewQueue == null) {
            return Promise.of(HttpHelper.errorResponse(501, "Learning module not configured"));
        }
        String policyId = request.getPathParameter("policyId");
        if (policyId == null || policyId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400,
                "policyId path parameter is required"));
        }
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> data = body.isBlank()
                    ? Map.of() : HttpHelper.mapper().readValue(body, Map.class);
                String reviewer = (String) data.getOrDefault("reviewer", "unknown");
                String rationale = (String) data.getOrDefault("rationale",
                    "Rejected via learning API");
                String notes = (String) data.get("notes");
                ReviewDecision decision = new ReviewDecision(
                    reviewer, rationale, Instant.now(), notes);
                return humanReviewQueue.reject(policyId, decision)
                    .map(item -> HttpHelper.jsonResponse(Map.of(
                        "id", item.getReviewId(),
                        "status", item.getStatus().name(),
                        "decidedAt", Instant.now().toString()
                    )))
                    .then(Promise::of, e -> {
                        log.warn("[learning] reject policy failed policyId={}: {}",
                            policyId, e.getMessage());
                        return Promise.of(HttpHelper.errorResponse(404,
                            "Policy not found: " + policyId));
                    });
            } catch (Exception e) {
                return Promise.of(HttpHelper.errorResponse(400,
                    "Invalid request: " + e.getMessage()));
            }
        }, e -> Promise.of(HttpHelper.errorResponse(400, "Failed to read request body")));
    }

    public Promise<HttpResponse> handleTriggerReflection(HttpRequest request) {
        String tenantId = HttpHelper.resolveTenantId(request);
        log.info("[learning] reflect triggered for tenant={}", tenantId);

        if (learningPipeline == null) {
            // Degrade gracefully when DataCloud is not configured
            try {
                String json = HttpHelper.mapper().writeValueAsString(Map.of(
                    "triggered", false,
                    "tenantId", tenantId,
                    "message", "Learning pipeline not available — start AEP with DataCloud configured",
                    "timestamp", Instant.now().toString()
                ));
                return Promise.of(HttpResponse.ofCode(202)
                    .withHeader(HttpHeaders.CONTENT_TYPE,
                        HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                    .withBody(json.getBytes(StandardCharsets.UTF_8))
                    .build());
            } catch (Exception e) {
                return Promise.of(HttpHelper.errorResponse(500, "Failed to trigger reflection"));
            }
        }

        return learningPipeline.run(tenantId)
            .map(result -> HttpHelper.jsonResponse(Map.of(
                "triggered", true,
                "tenantId", result.tenantId(),
                "success", result.success(),
                "episodesRead", result.episodesRead(),
                "skillsEvaluated", result.skillsEvaluated(),
                "policiesQueued", result.policiesQueued(),
                "skillsSkipped", result.skillsSkipped(),
                "gateFailures", result.gateFailures(),
                "timestamp", Instant.now().toString()
            )))
            .then(Promise::of, e -> {
                log.error("[learning] reflection pipeline failed for tenant={}: {}",
                    tenantId, e.getMessage(), e);
                return Promise.of(HttpHelper.errorResponse(500,
                    "Reflection pipeline failed: " + e.getMessage()));
            });
    }
}
