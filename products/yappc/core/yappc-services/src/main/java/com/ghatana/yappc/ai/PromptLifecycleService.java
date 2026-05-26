package com.ghatana.yappc.ai;

import com.ghatana.audit.AuditLogger;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Audited lifecycle facade for prompt template versions.
 *
 * @doc.type class
 * @doc.purpose Promotes, rolls back, scores, and rebalances prompt templates through an auditable service boundary
 * @doc.layer service
 * @doc.pattern Service
 */
public final class PromptLifecycleService {

    private final PromptTemplateRegistry registry;
    private final AuditLogger auditLogger;

    /**
     * Creates the prompt lifecycle service.
     *
     * @param registry prompt template registry
     * @param auditLogger audit logger for lifecycle decisions
     */
    public PromptLifecycleService(
            @NotNull PromptTemplateRegistry registry,
            @NotNull AuditLogger auditLogger) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.auditLogger = Objects.requireNonNull(auditLogger, "auditLogger must not be null");
    }

    /**
     * Registers a prompt template version.
     *
     * @param template prompt template version
     * @return registered template
     */
    public PromptTemplateVersion register(@NotNull PromptTemplateVersion template) {
        registry.register(template);
        return template;
    }

    /**
     * Promotes a prompt version to active.
     *
     * @param key prompt key
     * @param version version to promote
     * @param actorId actor requesting the promotion
     * @param reason promotion reason
     * @return prompt lifecycle decision
     */
    public Promise<PromptLifecycleDecision> promote(
            @NotNull String key,
            @NotNull String version,
            @NotNull String actorId,
            @NotNull String reason) {
        String previousVersion = registry.activeVersion(key).orElse(null);
        registry.setActiveVersion(key, version);
        PromptLifecycleDecision decision = decision(
                "PROMOTED",
                key,
                version,
                previousVersion,
                actorId,
                reason,
                true);
        return audit(decision).map(ignored -> decision);
    }

    /**
     * Rolls a prompt key back to a specific version.
     *
     * @param key prompt key
     * @param version target rollback version
     * @param actorId actor requesting rollback
     * @param reason rollback reason
     * @return prompt lifecycle decision
     */
    public Promise<PromptLifecycleDecision> rollback(
            @NotNull String key,
            @NotNull String version,
            @NotNull String actorId,
            @NotNull String reason) {
        String previousVersion = registry.activeVersion(key).orElse(null);
        boolean applied = registry.rollbackToVersion(key, version);
        PromptLifecycleDecision decision = decision(
                "ROLLED_BACK",
                key,
                version,
                previousVersion,
                actorId,
                reason,
                applied);
        return audit(decision).map(ignored -> decision);
    }

    /**
     * Rolls a prompt key back to the previous active version.
     *
     * @param key prompt key
     * @param actorId actor requesting rollback
     * @param reason rollback reason
     * @return prompt lifecycle decision
     */
    public Promise<PromptLifecycleDecision> rollbackPrevious(
            @NotNull String key,
            @NotNull String actorId,
            @NotNull String reason) {
        String previousVersion = registry.activeVersion(key).orElse(null);
        String rolledBackVersion = registry.rollbackToPreviousVersion(key).orElse(null);
        PromptLifecycleDecision decision = decision(
                "ROLLED_BACK_PREVIOUS",
                key,
                rolledBackVersion,
                previousVersion,
                actorId,
                reason,
                rolledBackVersion != null);
        return audit(decision).map(ignored -> decision);
    }

    /**
     * Records an evaluation score for a prompt variant.
     *
     * @param key prompt key
     * @param version prompt version
     * @param variant prompt variant
     * @param score bounded score in [0, 1]
     */
    public void recordScore(String key, String version, String variant, double score) {
        registry.recordVariantScore(key, version, variant, score);
    }

    /**
     * Rebalances variant weights after enough scored samples exist.
     *
     * @param key prompt key
     * @param version prompt version
     * @param minimumSamplesPerVariant required sample count per variant
     * @param actorId actor requesting rebalance
     * @param reason rebalance reason
     * @return prompt lifecycle decision
     */
    public Promise<PromptLifecycleDecision> rebalanceWeights(
            @NotNull String key,
            @NotNull String version,
            int minimumSamplesPerVariant,
            @NotNull String actorId,
            @NotNull String reason) {
        boolean applied = registry.rebalanceVariantWeights(key, version, minimumSamplesPerVariant);
        PromptLifecycleDecision decision = decision(
                "REBALANCED",
                key,
                version,
                registry.activeVersion(key).orElse(null),
                actorId,
                reason,
                applied);
        return audit(decision).map(ignored -> decision);
    }

    /**
     * Resolves the active prompt version.
     *
     * @param key prompt key
     * @return active version or latest fallback
     */
    public java.util.Optional<PromptTemplateVersion> active(String key) {
        return registry.selectForActiveExperiment(key, "system", "active");
    }

    private PromptLifecycleDecision decision(
            String action,
            String key,
            String version,
            String previousVersion,
            String actorId,
            String reason,
            boolean applied) {
        requireNonBlank(key, "key");
        requireNonBlank(actorId, "actorId");
        requireNonBlank(reason, "reason");
        return new PromptLifecycleDecision(
                action,
                key,
                version,
                previousVersion,
                actorId,
                reason,
                applied,
                Instant.now());
    }

    private Promise<Void> audit(PromptLifecycleDecision decision) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventType", "prompt.lifecycle." + decision.action().toLowerCase(java.util.Locale.ROOT));
        event.put("promptKey", decision.key());
        event.put("version", decision.version());
        event.put("previousVersion", decision.previousVersion());
        event.put("actorId", decision.actorId());
        event.put("reason", decision.reason());
        event.put("applied", decision.applied());
        event.put("timestamp", decision.decidedAt().toString());
        return auditLogger.log(event);
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    /**
     * Auditable prompt lifecycle decision.
     *
     * @param action lifecycle action
     * @param key prompt key
     * @param version target version
     * @param previousVersion previous active version
     * @param actorId actor that requested the decision
     * @param reason decision reason
     * @param applied whether the decision changed registry state
     * @param decidedAt decision timestamp
     */
    public record PromptLifecycleDecision(
            String action,
            String key,
            String version,
            String previousVersion,
            String actorId,
            String reason,
            boolean applied,
            Instant decidedAt
    ) {
    }
}
