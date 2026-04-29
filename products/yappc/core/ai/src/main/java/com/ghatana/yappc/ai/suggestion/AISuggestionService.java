/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC AI Module — AI Suggestion Service
 */
package com.ghatana.yappc.ai.suggestion;

import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.yappc.ai.AiSource;
import com.ghatana.yappc.ai.router.AIModelRouter;
import com.ghatana.yappc.ai.router.AIRequest;
import com.ghatana.yappc.ai.router.AIResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Provides context-aware AI suggestions for YAPPC lifecycle activities.
 *
 * <p>Each suggestion is scoped to a project ID and lifecycle context (e.g. the current
 * phase, active requirements, recent decisions). The service delegates to the
 * {@link AIModelRouter} for model routing, caching, and fallback handling, and applies
 * a lightweight parse step to extract structured {@link Suggestion} items from the
 * raw LLM output.
 *
 * <p><b>Suggestion types</b></p>
 * <ul>
 *   <li>{@link SuggestionType#REQUIREMENT} — refinements/gaps in requirements</li>
 *   <li>{@link SuggestionType#DESIGN} — architectural or design improvements</li>
 *   <li>{@link SuggestionType#TEST} — missing test scenarios</li>
 *   <li>{@link SuggestionType#RISK} — risks or blockers to surface to the team</li>
 *   <li>{@link SuggestionType#ACTION} — concrete next steps</li>
 * </ul>
 *
 * <p><b>Usage</b></p>
 * <pre>{@code
 * AISuggestionService suggestions = new AISuggestionService(aiModelRouter);
 *
 * suggestions.suggest("project-42", "SHAPE", Map.of("title", "Add dark mode"))
 *            .whenResult(list -> list.forEach(s -> log.info(s.text())));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Context-aware AI suggestion service for YAPPC lifecycle phases
 * @doc.layer product
 * @doc.pattern Service, Facade
 */
public final class AISuggestionService {

    private static final Logger log = LoggerFactory.getLogger(AISuggestionService.class);

    /** Maximum number of suggestions returned per call. */
    private static final int MAX_SUGGESTIONS = 5;

    private final AIModelRouter router;

    /**
     * Constructs the suggestion service.
     *
     * @param router AI model router (with caching and fallback support)
     */
    public AISuggestionService(@NotNull AIModelRouter router) {
        this.router = Objects.requireNonNull(router, "AIModelRouter must not be null");
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Generates a list of AI-powered suggestions for a project in a given lifecycle phase.
     *
     * @param projectId project to generate suggestions for
     * @param phase     current lifecycle phase name (e.g. {@code "SHAPE"})
     * @param context   additional context such as requirements text, recent decisions, or
     *                  artifact summaries — keys and values are model-agnostic
     * @return promise resolving to an unmodifiable list of up to {@value #MAX_SUGGESTIONS} suggestions
     */
    public Promise<List<Suggestion>> suggest(
            @NotNull  String              projectId,
            @NotNull  String              phase,
            @Nullable Map<String, Object> context) {

        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(phase,     "phase");

        String tenantId = resolveTenantId();
        Map<String, Object> ctx = context != null ? context : Map.of();

        String prompt = buildPrompt(projectId, phase, ctx);

        AIRequest request = AIRequest.builder()
                .taskType(AIRequest.TaskType.REASONING)
                .prompt(prompt)
                .context(ctx)
                .parameters(AIRequest.RequestParameters.builder()
                        .temperature(0.4)
                        .maxTokens(800)
                        .build())
                .build();

        log.debug("Requesting AI suggestions for project={} phase={} tenant={}", projectId, phase, tenantId);

        return router.route(request)
                .map(response -> parseSuggestions(response, projectId, phase))
                .whenException(ex ->
                        log.error("AI suggestion call failed for project={} phase={}: {}",
                                projectId, phase, ex.getMessage(), ex));
    }

    /**
     * Generates suggestions specifically about requirement quality.
     *
     * @param projectId    project to evaluate
     * @param requirements raw requirement text (may be multi-line)
     * @return promise resolving to a list of requirement-specific suggestions
     */
    public Promise<List<Suggestion>> suggestRequirementImprovements(
            @NotNull String projectId,
            @NotNull String requirements) {

        Objects.requireNonNull(projectId,    "projectId");
        Objects.requireNonNull(requirements, "requirements");

        return suggest(projectId, "SHAPE",
                Map.of("requirements", requirements, "focus", "requirement_quality"));
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static String buildPrompt(String projectId, String phase, Map<String, Object> ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a senior software engineering advisor helping a team navigate the ")
          .append(phase)
          .append(" phase of the YAPPC lifecycle.\n\n");

        sb.append("Project: ").append(projectId).append("\n");
        sb.append("Phase: ").append(phase).append("\n");

        if (!ctx.isEmpty()) {
            sb.append("Context:\n");
            ctx.forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append("\n"));
        }

        sb.append("\nGenerate up to ").append(MAX_SUGGESTIONS).append(" concise, actionable suggestions. ")
          .append("Each suggestion must start with a type prefix: [REQUIREMENT], [DESIGN], [TEST], [RISK], or [ACTION]. ")
          .append("One suggestion per line. Be specific and brief (max 25 words per suggestion).");

        return sb.toString();
    }

    private static List<Suggestion> parseSuggestions(AIResponse response, String projectId, String phase) {
        if (response == null || response.getContent() == null || response.getContent().isBlank()) {
            log.warn("Empty AI response for project={} phase={}", projectId, phase);
            return List.of();
        }

        List<Suggestion> results = response.getContent().lines()
                .map(String::strip)
                .filter(line -> !line.isBlank())
                .filter(line -> line.contains("["))
                .limit(MAX_SUGGESTIONS)
                .map(line -> parseLine(line, projectId, phase))
                .filter(Objects::nonNull)
                .toList();

        log.debug("Parsed {} suggestions for project={} phase={}", results.size(), projectId, phase);
        return Collections.unmodifiableList(results);
    }

    private static @Nullable Suggestion parseLine(String line, String projectId, String phase) {
        SuggestionType type = SuggestionType.ACTION; // default

        if (line.startsWith("[REQUIREMENT]")) {
            type = SuggestionType.REQUIREMENT;
            line = line.substring("[REQUIREMENT]".length()).strip();
        } else if (line.startsWith("[DESIGN]")) {
            type = SuggestionType.DESIGN;
            line = line.substring("[DESIGN]".length()).strip();
        } else if (line.startsWith("[TEST]")) {
            type = SuggestionType.TEST;
            line = line.substring("[TEST]".length()).strip();
        } else if (line.startsWith("[RISK]")) {
            type = SuggestionType.RISK;
            line = line.substring("[RISK]".length()).strip();
        } else if (line.startsWith("[ACTION]")) {
            type = SuggestionType.ACTION;
            line = line.substring("[ACTION]".length()).strip();
        }

        if (line.isBlank()) return null;

        return new Suggestion(
                UUID.randomUUID().toString(),
                projectId,
                phase,
                type,
                line,
                AiSource.MODEL,
                Instant.now()
        );
    }

    private static String resolveTenantId() {
        String tenantId = TenantContext.getCurrentTenantId();
        return (tenantId != null && !tenantId.isBlank()) ? tenantId : "default";
    }

    // ── Domain types ──────────────────────────────────────────────────────────

    /**
     * Categories of AI suggestion.
     */
    public enum SuggestionType {
        /** A refinement or gap identified in the requirements. */
        REQUIREMENT,
        /** An architectural or design improvement opportunity. */
        DESIGN,
        /** A missing or weak test scenario. */
        TEST,
        /** A risk or blocker that should be surfaced to the team. */
        RISK,
        /** A concrete next step the team should take. */
        ACTION
    }

    /**
     * An individual AI-generated suggestion.
     *
     * @param id          unique suggestion identifier
     * @param projectId   project this suggestion pertains to
     * @param phase       lifecycle phase context
     * @param type        suggestion category
     * @param text        human-readable suggestion text
     * @param source      provenance discriminator — {@link AiSource#MODEL} when produced by an
     *                    LLM/ML model, {@link AiSource#RULE} when produced by a deterministic
     *                    rule engine
     * @param generatedAt when this suggestion was produced
     */
    public record Suggestion(
            String         id,
            String         projectId,
            String         phase,
            SuggestionType type,
            String         text,
            AiSource       source,
            Instant        generatedAt) {}
}
