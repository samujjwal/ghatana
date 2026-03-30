/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Scaffold — Smart Scaffolding Advisor
 */
package com.ghatana.yappc.services.scaffold;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * AI-powered advisor that recommends scaffold templates based on detected or described
 * project context.
 *
 * <p>Uses the platform {@link CompletionService} to evaluate the project context
 * (languages, frameworks, goals) and return a ranked list of template recommendations.
 * Recommendations are deterministic within the supplied context because a low temperature
 * (0.2) is used, making the output stable and appropriate for automated tooling.
 *
 * <h3>Typical Usage</h3>
 * <pre>{@code
 * SmartScaffoldingAdvisor advisor = new SmartScaffoldingAdvisor(completionService);
 * ProjectContext ctx = new ProjectContext(
 *     "REST microservice for payment processing",
 *     "Add GitHub Actions CI pipeline with security scanning",
 *     Set.of("java"),
 *     Set.of("spring-boot", "gradle")
 * );
 *
 * List<TemplateRecommendation> recs = advisor.recommendTemplates(ctx).getResult();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose AI-assisted template recommendation for YAPPC scaffolding workflows
 * @doc.layer product
 * @doc.pattern Service
 */
public class SmartScaffoldingAdvisor {

    private static final Logger log = LoggerFactory.getLogger(SmartScaffoldingAdvisor.class);

    /**
     * LLM temperature — intentionally low to produce stable, deterministic recommendations
     * suitable for automated tooling and reproducible builds.
     */
    private static final double TEMPERATURE = 0.2;

    private static final int MAX_TOKENS = 1024;

    private static final int MAX_RECOMMENDATIONS = 5;

    private final CompletionService completionService;

    /**
     * @param completionService platform LLM completion service; must not be null
     */
    public SmartScaffoldingAdvisor(CompletionService completionService) {
        if (completionService == null) {
            throw new IllegalArgumentException("completionService must not be null");
        }
        this.completionService = completionService;
    }

    /**
     * Recommends scaffold templates ranked by confidence for the supplied project context.
     *
     * <p>Returns an empty list (not an error) when the LLM response cannot be parsed or
     * the context is insufficient for confident recommendations.
     *
     * @param context project context describing languages, frameworks, and goal
     * @return promise of recommendations sorted by confidence descending; never null
     */
    public Promise<List<TemplateRecommendation>> recommendTemplates(ProjectContext context) {
        if (context == null) {
            return Promise.of(Collections.emptyList());
        }

        String prompt = buildPrompt(context);
        CompletionRequest request = CompletionRequest.builder()
                .prompt(prompt)
                .maxTokens(MAX_TOKENS)
                .temperature(TEMPERATURE)
                .build();

        return completionService.complete(request)
                .map(result -> parseRecommendations(result, context))
                .mapException(ex -> {
                    log.warn("SmartScaffoldingAdvisor: completion failed for project '{}': {}",
                            context.description(), ex.getMessage());
                    return ex; // propagate so caller can handle
                });
    }

    // ─── Prompt construction ─────────────────────────────────────────────────

    private String buildPrompt(ProjectContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a software scaffolding expert for the YAPPC platform. ");
        sb.append("Given a project context, recommend scaffold templates ranked by suitability.\n\n");

        sb.append("PROJECT CONTEXT:\n");
        sb.append("Description: ").append(context.description()).append("\n");
        sb.append("Goal: ").append(context.goal()).append("\n");

        if (!context.languages().isEmpty()) {
            sb.append("Detected Languages: ").append(String.join(", ", context.languages())).append("\n");
        }
        if (!context.frameworks().isEmpty()) {
            sb.append("Detected Frameworks: ").append(String.join(", ", context.frameworks())).append("\n");
        }

        sb.append("\nReturn exactly up to ").append(MAX_RECOMMENDATIONS)
                .append(" template recommendations, one per line, in this exact format:\n");
        sb.append("TEMPLATE_NAME|Short description (max 80 chars)|confidence 0.0-1.0|rationale (max 120 chars)\n\n");
        sb.append("EXAMPLES:\n");
        sb.append("gradle-java-ci|GitHub Actions CI for Gradle Java projects|0.95|Matches detected Gradle+Java stack\n");
        sb.append("docker-compose-postgres|Docker Compose with PostgreSQL|0.80|Common backend services setup\n\n");
        sb.append("Output ONLY the pipe-delimited lines. No headers, no explanations, no markdown.\n");

        return sb.toString();
    }

    // ─── Response parsing ─────────────────────────────────────────────────────

    private List<TemplateRecommendation> parseRecommendations(
            CompletionResult result, ProjectContext context) {

        String text = result.getText();
        if (text == null || text.isBlank()) {
            log.debug("SmartScaffoldingAdvisor: empty response for project '{}'", context.description());
            return Collections.emptyList();
        }

        List<TemplateRecommendation> recommendations = new ArrayList<>();

        for (String line : text.split("\\R")) {
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            TemplateRecommendation rec = parseLine(line);
            if (rec != null) {
                recommendations.add(rec);
            }

            if (recommendations.size() >= MAX_RECOMMENDATIONS) {
                break;
            }
        }

        // Sort descending by confidence
        recommendations.sort((a, b) -> Double.compare(b.confidence(), a.confidence()));

        log.debug("SmartScaffoldingAdvisor: returned {} recommendations for project '{}'",
                recommendations.size(), context.description());

        return Collections.unmodifiableList(recommendations);
    }

    private TemplateRecommendation parseLine(String line) {
        String[] parts = line.split("\\|", 4);
        if (parts.length < 4) {
            log.trace("SmartScaffoldingAdvisor: skipping malformed recommendation line: '{}'", line);
            return null;
        }

        String name = parts[0].strip();
        String description = parts[1].strip();
        String confidenceStr = parts[2].strip();
        String rationale = parts[3].strip();

        if (name.isEmpty() || description.isEmpty()) {
            return null;
        }

        double confidence;
        try {
            confidence = Double.parseDouble(confidenceStr);
            if (confidence < 0.0 || confidence > 1.0) {
                log.trace("SmartScaffoldingAdvisor: confidence out of range [0,1]: {}", confidenceStr);
                return null;
            }
        } catch (NumberFormatException e) {
            log.trace("SmartScaffoldingAdvisor: non-numeric confidence '{}', skipping line", confidenceStr);
            return null;
        }

        return new TemplateRecommendation(name, description, confidence, rationale);
    }

    // ─── Supporting types ─────────────────────────────────────────────────────

    /**
     * Immutable context describing a project to be scaffolded or improved.
     *
     * @param description  human-readable description of the project
     * @param goal         what the user wants to achieve (e.g., "add CI pipeline")
     * @param languages    detected or declared programming languages (lowercase)
     * @param frameworks   detected or declared frameworks and build tools (lowercase)
     */
    public record ProjectContext(
            String description,
            String goal,
            Set<String> languages,
            Set<String> frameworks) {

        /** Constructs a context with explicit fields. */
        public ProjectContext {
            description = (description != null) ? description : "";
            goal = (goal != null) ? goal : "";
            languages = (languages != null) ? Set.copyOf(languages) : Set.of();
            frameworks = (frameworks != null) ? Set.copyOf(frameworks) : Set.of();
        }

        /** Convenience factory for a plain text description and goal with no language hints. */
        public static ProjectContext of(String description, String goal) {
            return new ProjectContext(description, goal, Set.of(), Set.of());
        }
    }

    /**
     * A single template recommendation returned by the advisor.
     *
     * @param name        identifier of the scaffold template (kebab-case)
     * @param description short human-readable description of what the template sets up
     * @param confidence  suitability score in the range [0.0, 1.0]; higher is more suitable
     * @param rationale   brief explanation of why this template was recommended
     */
    public record TemplateRecommendation(
            String name,
            String description,
            double confidence,
            String rationale) {}
}
