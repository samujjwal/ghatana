/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.ghatana.aep.server.http.HttpHelper;
import com.ghatana.platform.toolruntime.change.ChangeApprovalWorkflow;
import com.ghatana.platform.toolruntime.change.ChangeRequest;
import com.ghatana.platform.toolruntime.change.ChangeType;
import com.ghatana.platform.toolruntime.recertification.RecertificationCampaign;
import com.ghatana.platform.toolruntime.recertification.RecertificationItem;
import com.ghatana.platform.toolruntime.recertification.RecertificationPipeline;
import com.ghatana.platform.toolruntime.recertification.RecertificationReport;
import com.ghatana.platform.toolruntime.recertification.RecertificationScope;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Controller providing lifecycle management endpoints for change approval and recertification.
 *
 * <p>Exposes REST API for:
 * <ul>
 *   <li><b>Change management</b> — submit, approve, reject, withdraw, and list changes</li>
 *   <li><b>Recertification</b> — create campaigns, review items, generate audit reports</li>
 * </ul>
 *
 * <p>Change routes:
 * <pre>
 *   POST   /lifecycle/changes                                  — submit a change
 *   GET    /lifecycle/changes?tenantId=                        — list pending changes
 *   GET    /lifecycle/changes/:changeId                        — get a change
 *   POST   /lifecycle/changes/:changeId/approve                — approve
 *   POST   /lifecycle/changes/:changeId/reject                 — reject
 *   POST   /lifecycle/changes/:changeId/withdraw               — withdraw
 * </pre>
 *
 * <p>Recertification routes:
 * <pre>
 *   POST   /lifecycle/recertification/campaigns                — create campaign
 *   GET    /lifecycle/recertification/campaigns?tenantId=      — list campaigns
 *   GET    /lifecycle/recertification/campaigns/:campaignId    — get campaign
 *   GET    /lifecycle/recertification/campaigns/:campaignId/items      — list items
 *   POST   /lifecycle/recertification/campaigns/:campaignId/items/:itemId/certify — certify
 *   POST   /lifecycle/recertification/campaigns/:campaignId/items/:itemId/revoke  — revoke
 *   GET    /lifecycle/recertification/campaigns/:campaignId/report     — generate report
 * </pre>
 *
 * @doc.type class
 * @doc.purpose HTTP controller for agent lifecycle change approval and recertification
 * @doc.layer product
 * @doc.pattern Controller
 */
public final class LifecycleController {

    private static final Logger log = LoggerFactory.getLogger(LifecycleController.class);

    private final ChangeApprovalWorkflow changeWorkflow;
    private final RecertificationPipeline recertPipeline;

    /**
     * @param changeWorkflow  change approval workflow; never {@code null}
     * @param recertPipeline  recertification pipeline; never {@code null}
     */
    public LifecycleController(
            ChangeApprovalWorkflow changeWorkflow,
            RecertificationPipeline recertPipeline) {
        this.changeWorkflow = Objects.requireNonNull(changeWorkflow, "changeWorkflow");
        this.recertPipeline = Objects.requireNonNull(recertPipeline, "recertPipeline");
    }

    // ======================================================================
    // Change Management
    // ======================================================================

    /**
     * POST /lifecycle/changes — submit a change for review.
     * Body: { tenantId, requestingAgent, changeType, description, metadata? }
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleSubmitChange(HttpRequest request) {
        return request.loadBody().then(buf -> {
            try {
                Map<String, Object> body = parseBody(buf);
                String tenantId        = (String) body.get("tenantId");
                String requestingAgent = (String) body.get("requestingAgent");
                String changeTypeName  = (String) body.get("changeType");
                String description     = (String) body.get("description");
                if (tenantId == null || requestingAgent == null || changeTypeName == null || description == null) {
                    return Promise.of(HttpHelper.errorResponse(400,
                        "tenantId, requestingAgent, changeType, description are required"));
                }
                ChangeType changeType;
                try {
                    changeType = ChangeType.valueOf(changeTypeName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return Promise.of(HttpHelper.errorResponse(400,
                        "Unknown changeType: " + changeTypeName));
                }
                Map<String, Object> metadata = body.containsKey("metadata")
                    ? (Map<String, Object>) body.get("metadata")
                    : Map.of();
                return changeWorkflow.submitChange(tenantId, requestingAgent, changeType, description, metadata)
                    .map(cr -> HttpHelper.jsonResponse(changeToMap(cr)));
            } catch (Exception e) {
                log.warn("Error submitting change", e);
                return Promise.of(HttpHelper.errorResponse(400, "Invalid request: " + e.getMessage()));
            }
        });
    }

    /**
     * GET /lifecycle/changes?tenantId= — list pending changes.
     */
    public Promise<HttpResponse> handleListPendingChanges(HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400, "tenantId query param required"));
        }
        return changeWorkflow.listPending(tenantId)
            .map(changes -> HttpHelper.jsonResponse(Map.of(
                "tenantId", tenantId,
                "pending", changes.stream().map(this::changeToMap).collect(Collectors.toList()),
                "count", changes.size()
            )));
    }

    /**
     * GET /lifecycle/changes/:changeId — get a change by ID.
     */
    public Promise<HttpResponse> handleGetChange(HttpRequest request) {
        String changeId = request.getPathParameter("changeId");
        if (changeId == null || changeId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400, "changeId path param required"));
        }
        return changeWorkflow.getChange(changeId)
            .map(cr -> HttpHelper.jsonResponse(changeToMap(cr)))
            .then(Promise::of, e -> {
                if (e instanceof IllegalArgumentException) {
                    return Promise.of(HttpHelper.errorResponse(404, "Change not found: " + changeId));
                }
                return Promise.ofException(e);
            });
    }

    /**
     * POST /lifecycle/changes/:changeId/approve — approve a pending change.
     * Body: { reviewerId, notes? }
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleApproveChange(HttpRequest request) {
        String changeId = request.getPathParameter("changeId");
        if (changeId == null || changeId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400, "changeId path param required"));
        }
        return request.loadBody().then(buf -> {
            try {
                Map<String, Object> body = parseBody(buf);
                String reviewerId = (String) body.get("reviewerId");
                if (reviewerId == null || reviewerId.isBlank()) {
                    return Promise.of(HttpHelper.errorResponse(400, "reviewerId is required"));
                }
                String notes = (String) body.getOrDefault("notes", "");
                return changeWorkflow.approve(changeId, reviewerId, notes)
                    .map(cr -> HttpHelper.jsonResponse(changeToMap(cr)))
                    .then(Promise::of, e -> {
                        if (e instanceof IllegalArgumentException) {
                            return Promise.of(HttpHelper.errorResponse(404, e.getMessage()));
                        }
                        if (e instanceof IllegalStateException) {
                            return Promise.of(HttpHelper.errorResponse(409, e.getMessage()));
                        }
                        return Promise.ofException(e);
                    });
            } catch (Exception e) {
                return Promise.of(HttpHelper.errorResponse(400, "Invalid request: " + e.getMessage()));
            }
        });
    }

    /**
     * POST /lifecycle/changes/:changeId/reject — reject a pending change.
     * Body: { reviewerId, reason }
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleRejectChange(HttpRequest request) {
        String changeId = request.getPathParameter("changeId");
        if (changeId == null || changeId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400, "changeId path param required"));
        }
        return request.loadBody().then(buf -> {
            try {
                Map<String, Object> body = parseBody(buf);
                String reviewerId = (String) body.get("reviewerId");
                String reason     = (String) body.get("reason");
                if (reviewerId == null || reviewerId.isBlank() || reason == null || reason.isBlank()) {
                    return Promise.of(HttpHelper.errorResponse(400, "reviewerId and reason are required"));
                }
                return changeWorkflow.reject(changeId, reviewerId, reason)
                    .map(cr -> HttpHelper.jsonResponse(changeToMap(cr)))
                    .then(Promise::of, e -> {
                        if (e instanceof IllegalArgumentException) {
                            return Promise.of(HttpHelper.errorResponse(404, e.getMessage()));
                        }
                        if (e instanceof IllegalStateException) {
                            return Promise.of(HttpHelper.errorResponse(409, e.getMessage()));
                        }
                        return Promise.ofException(e);
                    });
            } catch (Exception e) {
                return Promise.of(HttpHelper.errorResponse(400, "Invalid request: " + e.getMessage()));
            }
        });
    }

    /**
     * POST /lifecycle/changes/:changeId/withdraw — withdraw a pending change.
     */
    public Promise<HttpResponse> handleWithdrawChange(HttpRequest request) {
        String changeId = request.getPathParameter("changeId");
        if (changeId == null || changeId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400, "changeId path param required"));
        }
        return changeWorkflow.withdraw(changeId)
            .map(cr -> HttpHelper.jsonResponse(changeToMap(cr)))
            .then(Promise::of, e -> {
                if (e instanceof IllegalArgumentException) {
                    return Promise.of(HttpHelper.errorResponse(404, e.getMessage()));
                }
                if (e instanceof IllegalStateException) {
                    return Promise.of(HttpHelper.errorResponse(409, e.getMessage()));
                }
                return Promise.ofException(e);
            });
    }

    // ======================================================================
    // Recertification
    // ======================================================================

    /**
     * POST /lifecycle/recertification/campaigns — create a recertification campaign.
     * Body: { tenantId, campaignName, scope }
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleCreateCampaign(HttpRequest request) {
        return request.loadBody().then(buf -> {
            try {
                Map<String, Object> body = parseBody(buf);
                String tenantId     = (String) body.get("tenantId");
                String campaignName = (String) body.get("campaignName");
                String scopeName    = (String) body.get("scope");
                if (tenantId == null || campaignName == null || scopeName == null) {
                    return Promise.of(HttpHelper.errorResponse(400,
                        "tenantId, campaignName, scope are required"));
                }
                RecertificationScope scope;
                try {
                    scope = RecertificationScope.valueOf(scopeName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return Promise.of(HttpHelper.errorResponse(400, "Unknown scope: " + scopeName));
                }
                return recertPipeline.createCampaign(tenantId, campaignName, scope)
                    .map(campaign -> HttpHelper.jsonResponse(campaignToMap(campaign)));
            } catch (Exception e) {
                log.warn("Error creating campaign", e);
                return Promise.of(HttpHelper.errorResponse(400, "Invalid request: " + e.getMessage()));
            }
        });
    }

    /**
     * GET /lifecycle/recertification/campaigns?tenantId= — list campaigns for a tenant.
     */
    public Promise<HttpResponse> handleListCampaigns(HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400, "tenantId query param required"));
        }
        return recertPipeline.listCampaigns(tenantId)
            .map(campaigns -> HttpHelper.jsonResponse(Map.of(
                "tenantId", tenantId,
                "campaigns", campaigns.stream().map(this::campaignToMap).collect(Collectors.toList()),
                "count", campaigns.size()
            )));
    }

    /**
     * GET /lifecycle/recertification/campaigns/:campaignId — get a campaign.
     */
    public Promise<HttpResponse> handleGetCampaign(HttpRequest request) {
        String campaignId = request.getPathParameter("campaignId");
        if (campaignId == null || campaignId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400, "campaignId path param required"));
        }
        return recertPipeline.getCampaign(campaignId)
            .map(campaign -> HttpHelper.jsonResponse(campaignToMap(campaign)))
            .then(Promise::of, e -> {
                if (e instanceof IllegalArgumentException) {
                    return Promise.of(HttpHelper.errorResponse(404, "Campaign not found: " + campaignId));
                }
                return Promise.ofException(e);
            });
    }

    /**
     * GET /lifecycle/recertification/campaigns/:campaignId/items — list items in a campaign.
     */
    public Promise<HttpResponse> handleGetCampaignItems(HttpRequest request) {
        String campaignId = request.getPathParameter("campaignId");
        if (campaignId == null || campaignId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400, "campaignId path param required"));
        }
        return recertPipeline.getItems(campaignId)
            .map(items -> HttpHelper.jsonResponse(Map.of(
                "campaignId", campaignId,
                "items", items.stream().map(this::itemToMap).collect(Collectors.toList()),
                "count", items.size()
            )))
            .then(Promise::of, e -> {
                if (e instanceof IllegalArgumentException) {
                    return Promise.of(HttpHelper.errorResponse(404, "Campaign not found: " + campaignId));
                }
                return Promise.ofException(e);
            });
    }

    /**
     * POST /lifecycle/recertification/campaigns/:campaignId/items/:itemId/certify
     * Body: { certifierId }
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleCertifyItem(HttpRequest request) {
        String campaignId = request.getPathParameter("campaignId");
        String itemId     = request.getPathParameter("itemId");
        if (campaignId == null || itemId == null) {
            return Promise.of(HttpHelper.errorResponse(400, "campaignId and itemId path params required"));
        }
        return request.loadBody().then(buf -> {
            try {
                Map<String, Object> body = parseBody(buf);
                String certifierId = (String) body.get("certifierId");
                if (certifierId == null || certifierId.isBlank()) {
                    return Promise.of(HttpHelper.errorResponse(400, "certifierId is required"));
                }
                return recertPipeline.certify(campaignId, itemId, certifierId)
                    .map(item -> HttpHelper.jsonResponse(itemToMap(item)))
                    .then(Promise::of, e -> {
                        if (e instanceof IllegalArgumentException) {
                            return Promise.of(HttpHelper.errorResponse(404, e.getMessage()));
                        }
                        if (e instanceof IllegalStateException) {
                            return Promise.of(HttpHelper.errorResponse(409, e.getMessage()));
                        }
                        return Promise.ofException(e);
                    });
            } catch (Exception e) {
                return Promise.of(HttpHelper.errorResponse(400, "Invalid request: " + e.getMessage()));
            }
        });
    }

    /**
     * POST /lifecycle/recertification/campaigns/:campaignId/items/:itemId/revoke
     * Body: { certifierId, reason }
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleRevokeItem(HttpRequest request) {
        String campaignId = request.getPathParameter("campaignId");
        String itemId     = request.getPathParameter("itemId");
        if (campaignId == null || itemId == null) {
            return Promise.of(HttpHelper.errorResponse(400, "campaignId and itemId path params required"));
        }
        return request.loadBody().then(buf -> {
            try {
                Map<String, Object> body = parseBody(buf);
                String certifierId = (String) body.get("certifierId");
                String reason      = (String) body.get("reason");
                if (certifierId == null || certifierId.isBlank() || reason == null || reason.isBlank()) {
                    return Promise.of(HttpHelper.errorResponse(400, "certifierId and reason are required"));
                }
                return recertPipeline.revoke(campaignId, itemId, certifierId, reason)
                    .map(item -> HttpHelper.jsonResponse(itemToMap(item)))
                    .then(Promise::of, e -> {
                        if (e instanceof IllegalArgumentException) {
                            return Promise.of(HttpHelper.errorResponse(404, e.getMessage()));
                        }
                        if (e instanceof IllegalStateException) {
                            return Promise.of(HttpHelper.errorResponse(409, e.getMessage()));
                        }
                        return Promise.ofException(e);
                    });
            } catch (Exception e) {
                return Promise.of(HttpHelper.errorResponse(400, "Invalid request: " + e.getMessage()));
            }
        });
    }

    /**
     * GET /lifecycle/recertification/campaigns/:campaignId/report — generate audit report.
     */
    public Promise<HttpResponse> handleGenerateReport(HttpRequest request) {
        String campaignId = request.getPathParameter("campaignId");
        if (campaignId == null || campaignId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400, "campaignId path param required"));
        }
        return recertPipeline.generateReport(campaignId)
            .map(report -> HttpHelper.jsonResponse(reportToMap(report)))
            .then(Promise::of, e -> {
                if (e instanceof IllegalArgumentException) {
                    return Promise.of(HttpHelper.errorResponse(404, "Campaign not found: " + campaignId));
                }
                return Promise.ofException(e);
            });
    }

    // ======================================================================
    // Serialization helpers
    // ======================================================================

    private Map<String, Object> changeToMap(ChangeRequest cr) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("changeId",        cr.changeId());
        m.put("tenantId",        cr.tenantId());
        m.put("requestingAgent", cr.requestingAgent());
        m.put("changeType",      cr.changeType().name());
        m.put("description",     cr.description());
        m.put("status",          cr.status().name());
        m.put("riskScore",       cr.riskScore());
        m.put("submittedAt",     cr.submittedAt().toString());
        if (cr.reviewerId() != null)  m.put("reviewerId",  cr.reviewerId());
        if (cr.reviewNotes() != null) m.put("reviewNotes", cr.reviewNotes());
        if (cr.reviewedAt() != null)  m.put("reviewedAt",  cr.reviewedAt().toString());
        return m;
    }

    private Map<String, Object> campaignToMap(RecertificationCampaign c) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("campaignId",      c.campaignId());
        m.put("tenantId",        c.tenantId());
        m.put("campaignName",    c.campaignName());
        m.put("scope",           c.scope().name());
        m.put("status",          c.status().name());
        m.put("totalItems",      c.totalItems());
        m.put("certifiedCount",  c.certifiedCount());
        m.put("revokedCount",    c.revokedCount());
        m.put("pendingCount",    c.pendingCount());
        m.put("createdAt",       c.createdAt().toString());
        if (c.completedAt() != null) m.put("completedAt", c.completedAt().toString());
        return m;
    }

    private Map<String, Object> itemToMap(RecertificationItem item) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("itemId",        item.itemId());
        m.put("campaignId",    item.campaignId());
        m.put("itemType",      item.itemType());
        m.put("resourceId",    item.resourceId());
        m.put("resourceName",  item.resourceName());
        m.put("decision",      item.decision().name());
        if (item.certifierId() != null)    m.put("certifierId",    item.certifierId());
        if (item.decisionNotes() != null)  m.put("decisionNotes",  item.decisionNotes());
        if (item.reviewedAt() != null)     m.put("reviewedAt",     item.reviewedAt().toString());
        return m;
    }

    private Map<String, Object> reportToMap(RecertificationReport r) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("campaignId",        r.campaignId());
        m.put("tenantId",          r.tenantId());
        m.put("campaignName",      r.campaignName());
        m.put("scope",             r.scope().name());
        m.put("totalItems",        r.totalItems());
        m.put("certifiedCount",    r.certifiedCount());
        m.put("revokedCount",      r.revokedCount());
        m.put("pendingCount",      r.pendingCount());
        m.put("certificationRate", r.certificationRate());
        m.put("revokedItems",
            r.revokedItems().stream().map(this::itemToMap).collect(Collectors.toList()));
        m.put("generatedAt",       r.generatedAt().toString());
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseBody(io.activej.bytebuf.ByteBuf buf) throws Exception {
        String bodyStr = buf.getString(StandardCharsets.UTF_8);
        return com.ghatana.platform.core.util.JsonUtils.getDefaultMapper()
            .readValue(bodyStr, Map.class);
    }
}
