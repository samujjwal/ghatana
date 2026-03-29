/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.application.facade;

import com.ghatana.datacloud.analytics.AnalyticsQueryEngine;
import com.ghatana.datacloud.analytics.QueryPlan;
import com.ghatana.datacloud.analytics.QueryResult;
import com.ghatana.datacloud.application.ai.QueryRecommender;
import com.ghatana.datacloud.application.nlq.NLQService;
import com.ghatana.datacloud.entity.DataType;
import com.ghatana.datacloud.entity.MetaCollection;
import com.ghatana.datacloud.entity.MetaField;
import io.activej.promise.Promise;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern FIELD_REFERENCE = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\b");
    private static final Set<String> RESERVED_KEYWORDS = Set.of(
            "and", "or", "is", "equals", "contains", "sorted", "sort", "by",
            "ascending", "descending", "asc", "desc", "true", "false");

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

    public Promise<QueryResult> submitQuery(
            String tenantId, String queryText, Map<String, Object> parameters) {
        return analyticsQueryEngine.submitQuery(tenantId, queryText, parameters);
    }

    public Promise<QueryResult> getResult(String queryId) {
        return analyticsQueryEngine.getResult(queryId);
    }

    public Promise<QueryPlan> getPlan(String queryId) {
        return analyticsQueryEngine.getPlan(queryId);
    }

    // ── Natural-language queries ──────────────────────────────────────────────

    public Promise<List<Map<String, Object>>> executeNlq(
            String tenantId, String collection, String query) {
        MetaCollection synthesizedCollection = synthesizeCollection(tenantId, collection, query);

        return nlqService.parseQuery(query, synthesizedCollection)
            .then(nlqService::executeQuery)
            .map(result -> result.rows().stream()
                .map(AnalyticsDomainService::normalizeRow)
                .toList());
    }

    // ── Query recommendations ─────────────────────────────────────────────────

    public Promise<List<String>> getRecommendations(String tenantId, String context) {
        return queryRecommender.getRecommendations(tenantId, context)
            .map(recommendations -> recommendations.stream()
                .map(QueryRecommender.QueryRecommendation::suggestion)
                .toList());
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

    private static MetaCollection synthesizeCollection(String tenantId, String collectionName, String query) {
        List<MetaField> fields = extractReferencedFields(query).stream()
                .map(fieldName -> MetaField.builder()
                        .id(UUID.randomUUID())
                        .collectionId(UUID.randomUUID())
                        .name(fieldName)
                        .label(fieldName)
                        .type(DataType.STRING)
                        .build())
                .toList();

        return MetaCollection.builder()
                .tenantId(tenantId)
                .name(collectionName)
                .label(collectionName)
                .fields(fields)
                .build();
    }

    private static List<String> extractReferencedFields(String query) {
        Matcher matcher = FIELD_REFERENCE.matcher(query);
        Map<String, String> fields = new LinkedHashMap<>();
        while (matcher.find()) {
            String candidate = matcher.group(1);
            String normalized = candidate.toLowerCase();
            if (RESERVED_KEYWORDS.contains(normalized)) {
                continue;
            }
            fields.putIfAbsent(normalized, candidate);
        }
        return List.copyOf(fields.values());
    }

    private static Map<String, Object> normalizeRow(Object row) {
        if (row instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            return normalized;
        }
        return Map.of("value", row);
    }
}
