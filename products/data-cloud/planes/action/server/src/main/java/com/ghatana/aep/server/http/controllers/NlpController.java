/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.ghatana.aep.server.http.HttpHelper;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * HTTP controller for Natural Language Query (NLQ) parsing.
 *
 * <p>Accepts a free-text query and returns a structured intent + entity extraction.
 * Uses a layered matching strategy:
 * <ol>
 *   <li>Keyword/regex-based intent classification (deterministic, always available)</li>
 *   <li>Entity extraction for time windows, pipeline names, statuses, and tenant references</li>
 * </ol>
 *
 * <p>This controller is designed to be replaced with an LLM-backed implementation
 * when the AI gateway is wired. The response contract is stable regardless of backend.
 *
 * @doc.type class
 * @doc.purpose NLQ intent parsing and entity extraction for the AEP operator cockpit
 * @doc.layer product
 * @doc.pattern Controller
 */
public final class NlpController {

    private static final Logger log = LoggerFactory.getLogger(NlpController.class);

    private static final double HIGH_CONFIDENCE = 0.90;
    private static final double MEDIUM_CONFIDENCE = 0.75;
    private static final double LOW_CONFIDENCE = 0.55;

    // Intent patterns — order matters: first match wins
    private static final List<IntentRule> INTENT_RULES = List.of(
            new IntentRule("list_runs",       Pattern.compile("(?i)(show|list|get|find).*runs?")),
            new IntentRule("list_pipelines",  Pattern.compile("(?i)(show|list|get|find).*pipelines?")),
            new IntentRule("list_agents",     Pattern.compile("(?i)(show|list|get|find).*agents?")),
            new IntentRule("list_anomalies",  Pattern.compile("(?i)(show|list|get|find).*(anomal|alert|problem|issue)s?")),
            new IntentRule("filter_failed",   Pattern.compile("(?i)(fail(ed|ing)|error(s)?|broken)")),
            new IntentRule("filter_running",  Pattern.compile("(?i)(running|active|in.?progress)")),
            new IntentRule("filter_success",  Pattern.compile("(?i)(succeed|success|succeeded|passing|healthy)")),
            new IntentRule("time_window",     Pattern.compile("(?i)(last|past|previous)\\s+(\\d+)\\s+(minute|hour|day|week)s?")),
            new IntentRule("trigger_reflect", Pattern.compile("(?i)(trigger|run|start|execute).*(reflect|learn|episode)")),
            new IntentRule("kill_switch",     Pattern.compile("(?i)(kill.?switch|shutdown|disable|stop).*(tenant|all|pipeline)")),
            new IntentRule("status_query",    Pattern.compile("(?i)(status|health|state|how is)"))
    );

    // Time-window entity extractor
    private static final Pattern TIME_WINDOW_PATTERN =
            Pattern.compile("(?i)(last|past|previous)\\s+(\\d+)\\s+(minute|hour|day|week)s?");

    // Pipeline name extractor — quoted or 'named' style
    private static final Pattern PIPELINE_NAME_PATTERN =
            Pattern.compile("(?i)pipeline\\s+['\"]?([\\w\\-]+)['\"]?");

    // Status words
        private static final Pattern STATUS_PATTERN =
            Pattern.compile("(?i)\\b(failed|failing|running|succeeded|cancelled|pending)\\b");

    /**
     * POST /api/v1/nlp/parse
     *
     * <p>Request body: {@code { "query": "show me failing pipelines last 1 hour", "tenantId": "..." }}
     * <p>Response: {@code { "intent": "...", "confidence": 0.9, "entities": [...], "tenantId": "..." }}
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleParseQuery(HttpRequest request) {
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> data = HttpHelper.mapper().readValue(body, Map.class);

                String query = (String) data.get("query");
                if (query == null || query.isBlank()) {
                    return Promise.of(HttpHelper.errorResponse(400, "Missing required field: query"));
                }
                String tenantId = HttpHelper.resolveTenantId(request, data);

                log.debug("[nlp] parsing query tenant={} query={}", tenantId, query);

                ParseResult result = parse(query.trim());

                Map<String, Object> response = new java.util.LinkedHashMap<>();
                response.put("intent", result.intent());
                response.put("confidence", result.confidence());
                response.put("entities", result.entities());
                response.put("query", query);
                response.put("tenantId", tenantId);
                response.put("timestamp", Instant.now().toString());

                return Promise.of(HttpHelper.jsonResponse(response));
            } catch (Exception e) {
                log.error("[nlp] parse failed: {}", e.getMessage(), e);
                return Promise.of(HttpHelper.errorResponse(400, "Failed to parse query: " + e.getMessage()));
            }
        }, e -> {
            log.error("[nlp] failed to read request body: {}", e.getMessage(), e);
            return Promise.of(HttpHelper.errorResponse(400, "Failed to read request body"));
        });
    }

    // ─── Parsing logic ───────────────────────────────────────────────────────

    private ParseResult parse(String query) {
        String primary = classifyIntent(query);
        double confidence = computeConfidence(query, primary);
        List<Map<String, Object>> entities = extractEntities(query);

        // Compound refinement: if a time-window is present and primary is ambiguous,
        // default to list_runs with time context
        if ("status_query".equals(primary) && entities.stream()
                .anyMatch(e -> "time_window".equals(e.get("type")))) {
            primary = "list_runs";
        }

        boolean hasFailedStatus = entities.stream()
                .anyMatch(e -> "status".equals(e.get("type")) && "FAILED".equals(e.get("value")));
        boolean hasRunningStatus = entities.stream()
                .anyMatch(e -> "status".equals(e.get("type")) && "RUNNING".equals(e.get("value")));

        if (hasFailedStatus && ("list_runs".equals(primary) || "list_pipelines".equals(primary))) {
            primary = "filter_failed";
        } else if (hasRunningStatus && ("list_runs".equals(primary) || "list_pipelines".equals(primary))) {
            primary = "filter_running";
        }

        return new ParseResult(primary, confidence, entities);
    }

    private String classifyIntent(String query) {
        for (IntentRule rule : INTENT_RULES) {
            if (rule.pattern().matcher(query).find()) {
                return rule.intent();
            }
        }
        return "unknown";
    }

    private double computeConfidence(String query, String intent) {
        if ("unknown".equals(intent)) return LOW_CONFIDENCE;
        // More specific matches earn higher confidence
        long matchCount = INTENT_RULES.stream()
                .filter(r -> r.pattern().matcher(query).find())
                .count();
        if (matchCount >= 2) return HIGH_CONFIDENCE;
        return MEDIUM_CONFIDENCE;
    }

    private List<Map<String, Object>> extractEntities(String query) {
        List<Map<String, Object>> entities = new ArrayList<>();

        // Time window entity
        var twMatcher = TIME_WINDOW_PATTERN.matcher(query);
        if (twMatcher.find()) {
            int amount = Integer.parseInt(twMatcher.group(2));
            String unit = twMatcher.group(3).toLowerCase(Locale.ROOT);
            String displayUnit = amount == 1 ? unit : unit + "s";
            String normalised = toIso8601Duration(amount, unit);
            entities.add(Map.of(
                    "type", "time_window",
                    "amount", amount,
                "unit", displayUnit,
                    "iso8601", normalised
            ));
        }

        // Pipeline name entity
        var pnMatcher = PIPELINE_NAME_PATTERN.matcher(query);
        if (pnMatcher.find()) {
            entities.add(Map.of(
                    "type", "pipeline_name",
                    "value", pnMatcher.group(1)
            ));
        }

        // Status entity
        var statusMatcher = STATUS_PATTERN.matcher(query);
        if (statusMatcher.find()) {
            String rawStatus = statusMatcher.group(1).toUpperCase(Locale.ROOT);
            String status = "FAILING".equals(rawStatus) ? "FAILED" : rawStatus;
            entities.add(Map.of(
                    "type", "status",
                "value", status
            ));
        }

        return entities;
    }

    private static String toIso8601Duration(int amount, String unit) {
        return switch (unit) {
            case "minute", "minutes" -> "PT" + amount + "M";
            case "hour", "hours"     -> "PT" + amount + "H";
            case "day", "days"       -> "P" + amount + "D";
            case "week", "weeks"     -> "P" + (amount * 7) + "D";
            default                  -> "PT" + amount + "H";
        };
    }

    // ─── Value types ─────────────────────────────────────────────────────────

    private record IntentRule(String intent, Pattern pattern) {}

    private record ParseResult(
            String intent,
            double confidence,
            List<Map<String, Object>> entities) {}
}
