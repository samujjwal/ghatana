/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.application.facade;

import com.ghatana.datacloud.analytics.AnalyticsQueryEngine;
import com.ghatana.datacloud.application.ai.QueryRecommender;
import com.ghatana.datacloud.application.nlq.NLQService;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Domain facade aggregating analytics-related application services.
 *
 * <p>Handlers that previously needed to inject {@code AnalyticsQueryEngine},
 * {@code NLQService}, and {@code QueryRecommender} separately now
 * inject one {@code AnalyticsDomainService} instead.
 *
 * @doc.type class
 * @doc.purpose Domain facade for analytics-related application services
 * @doc.layer application
 * @doc.pattern Facade, Service
 */
public final class AnalyticsDomainService {

    private final AnalyticsQueryEngine analyticsQueryEngine;
    private final NLQService nlqService;
    private final QueryRecommender queryRecommender;

    public AnalyticsDomainService(
            AnalyticsQueryEngine analyticsQueryEngine,
            NLQService nlqService,
            QueryRecommender queryRecommender) {
        this.analyticsQueryEngine = Objects.requireNonNull(analyticsQueryEngine, "analyticsQueryEngine");
        this.nlqService           = Objects.requireNonNull(nlqService, "nlqService");
        this.queryRecommender     = Objects.requireNonNull(queryRecommender, "queryRecommender");
    }

    // ── SQL / structured analytics ────────────────────────────────────────────

    public Promise<AnalyticsQueryEngine.QueryResult> submitQuery(
            String tenantId, String queryText, Map<String, Object> parameters) {
        return analyticsQueryEngine.submitQuery(tenantId, queryText, parameters);
    }

    public Promise<AnalyticsQueryEngine.QueryResult> getResult(String queryId) {
        return analyticsQueryEngine.getResult(queryId);
    }

    public Promise<AnalyticsQueryEngine.QueryPlan> getPlan(String queryId) {
        return analyticsQueryEngine.getPlan(queryId);
    }

    // ── Natural-language queries ──────────────────────────────────────────────

    public Promise<List<Map<String, Object>>> executeNlq(
            String tenantId, String collection, String query) {
        return nlqService.executeQuery(tenantId, collection, query);
    }

    // ── Query recommendations ─────────────────────────────────────────────────

    public Promise<List<String>> getRecommendations(String tenantId, String context) {
        return queryRecommender.getRecommendations(tenantId, context);
    }

    // ── Escape hatches ────────────────────────────────────────────────────────

    public AnalyticsQueryEngine analyticsEngine() {
        return analyticsQueryEngine;
    }

    public NLQService nlq() {
        return nlqService;
    }

    public QueryRecommender recommender() {
        return queryRecommender;
    }
}
