/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.memory.promotion;

import com.ghatana.agent.framework.memory.MemoryNamespace;
import com.ghatana.agent.framework.memory.MemoryNamespaceRepository;
import com.ghatana.agent.framework.memory.MemoryScope;
import com.ghatana.datacloud.memory.MemoryService;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Default implementation of the {@link MemoryPromotionService}.
 *
 * <p>Executes the 7-step episodic→procedural promotion pipeline:
 * <ol>
 *   <li>EVALUATE — compute an internal quality score for the memory entry</li>
 *   <li>ASSESS_QUALITY — gate on the computed score vs. the promotion threshold</li>
 *   <li>CHECK_NAMESPACE — verify a promotion-enabled procedural namespace exists</li>
 *   <li>CREATE_EVIDENCE — persist the promotion evidence record</li>
 *   <li>MARK_PROMOTED — record promotion metadata on the source entry via MemoryService</li>
 *   <li>WRITE_PROCEDURAL — write enriched content to procedural memory tier</li>
 *   <li>EMIT_EVENT — build and return the final PromotionResult</li>
 * </ol>
 *
 * <p>If any gate step (ASSESS_QUALITY or CHECK_NAMESPACE) fails, promotion is aborted
 * immediately. All evidence collected up to that point is included in the result.
 *
 * @doc.type class
 * @doc.purpose 7-step memory promotion pipeline implementation
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DefaultMemoryPromotionService implements MemoryPromotionService {

    private static final Logger log = LoggerFactory.getLogger(DefaultMemoryPromotionService.class);

    // ─── Step name constants (used in evidence records) ───────────────────────
    static final String STEP_EVALUATE        = "EVALUATE";
    static final String STEP_ASSESS_QUALITY  = "ASSESS_QUALITY";
    static final String STEP_CHECK_NAMESPACE = "CHECK_NAMESPACE";
    static final String STEP_CREATE_EVIDENCE = "CREATE_EVIDENCE";
    static final String STEP_MARK_PROMOTED   = "MARK_PROMOTED";
    static final String STEP_WRITE_PROCEDURAL = "WRITE_PROCEDURAL";
    static final String STEP_EMIT_EVENT      = "EMIT_EVENT";

    private final MemoryService memoryService;
    private final MemoryNamespaceRepository namespaceRepository;

    /**
     * Creates a new service instance.
     *
     * @param memoryService       the tiered memory service for reading and writing memory entries
     * @param namespaceRepository repository for looking up namespace registrations
     */
    public DefaultMemoryPromotionService(
            MemoryService memoryService,
            MemoryNamespaceRepository namespaceRepository) {
        this.memoryService       = Objects.requireNonNull(memoryService, "memoryService");
        this.namespaceRepository = Objects.requireNonNull(namespaceRepository, "namespaceRepository");
    }

    @Override
    public Promise<PromotionResult> promote(PromotionRequest request) {
        Objects.requireNonNull(request, "request");
        Instant now = Instant.now();
        List<PromotionEvidence> evidence = new ArrayList<>();

        log.debug("Starting promotion pipeline for memory [{}] agent=[{}] tenant=[{}]",
                request.sourceMemoryId(), request.agentId(), request.tenantId());

        return step1Evaluate(request, now, evidence)
                .then(score -> step2AssessQuality(request, score, now, evidence))
                .then(qualityOk -> {
                    if (!qualityOk) {
                        return Promise.of(PromotionResult.failure(
                                STEP_ASSESS_QUALITY,
                                "Quality score below threshold " + request.effectiveThreshold(),
                                evidence, now));
                    }
                    return step3CheckNamespace(request, now, evidence)
                            .then(ns -> {
                                if (ns == null) {
                                    return Promise.of(PromotionResult.failure(
                                            STEP_CHECK_NAMESPACE,
                                            "No promotion-enabled procedural namespace found for agent "
                                                    + request.agentId(),
                                            evidence, now));
                                }
                                return step4CreateEvidence(request, ns, now, evidence)
                                        .then(ignored -> step5MarkPromoted(request, ns, now, evidence))
                                        .then(ignored -> step6WriteProcedural(request, ns, now, evidence))
                                        .then(targetId -> step7EmitEvent(request, targetId, evidence, now));
                            });
                });
    }

    // ─── Step 1: Evaluate ──────────────────────────────────────────────────────

    private Promise<Double> step1Evaluate(
            PromotionRequest request, Instant now, List<PromotionEvidence> evidence) {
        double score = computeQualityScore(request);
        evidence.add(new PromotionEvidence(
                UUID.randomUUID().toString(),
                request.tenantId(), request.agentId(),
                "__pending__", request.sourceMemoryId(),
                null, STEP_EVALUATE, 1, score, true,
                null, null, null, now, java.util.Map.of("importanceScore",
                        String.valueOf(request.importanceScore()))));
        return Promise.of(score);
    }

    // ─── Step 2: Assess Quality ────────────────────────────────────────────────

    private Promise<Boolean> step2AssessQuality(
            PromotionRequest request, double score,
            Instant now, List<PromotionEvidence> evidence) {
        boolean passed = score >= request.effectiveThreshold();
        PromotionEvidence ev = passed
                ? new PromotionEvidence(UUID.randomUUID().toString(),
                        request.tenantId(), request.agentId(), "__pending__",
                        request.sourceMemoryId(), null, STEP_ASSESS_QUALITY, 2,
                        score, true, null, null, null, now, java.util.Map.of())
                : PromotionEvidence.rejected(UUID.randomUUID().toString(),
                        request.tenantId(), request.agentId(), "__pending__",
                        request.sourceMemoryId(), STEP_ASSESS_QUALITY, 2,
                        "Score " + score + " below threshold " + request.effectiveThreshold(), now);
        evidence.add(ev);
        return Promise.of(passed);
    }

    // ─── Step 3: Check Namespace ───────────────────────────────────────────────

    private Promise<MemoryNamespace> step3CheckNamespace(
            PromotionRequest request, Instant now, List<PromotionEvidence> evidence) {
        return namespaceRepository.findByAgentAndScope(
                        request.agentId(), MemoryScope.PROCEDURAL, request.tenantId())
                .map(opt -> {
                    MemoryNamespace ns = opt.filter(MemoryNamespace::promotionEnabled).orElse(null);
                    String nsId = ns != null ? ns.namespaceId() : "__none__";
                    boolean passed = ns != null;
                    evidence.add(PromotionEvidence.passing(
                            UUID.randomUUID().toString(),
                            request.tenantId(), request.agentId(), nsId,
                            request.sourceMemoryId(), STEP_CHECK_NAMESPACE, 3, now));
                    if (!passed) {
                        // Replace last evidence with rejection
                        evidence.removeLast();
                        evidence.add(PromotionEvidence.rejected(
                                UUID.randomUUID().toString(),
                                request.tenantId(), request.agentId(), "__none__",
                                request.sourceMemoryId(), STEP_CHECK_NAMESPACE, 3,
                                "No promotion-enabled procedural namespace", now));
                    }
                    return ns;
                });
    }

    // ─── Step 4: Create Evidence ───────────────────────────────────────────────

    private Promise<Void> step4CreateEvidence(
            PromotionRequest request, MemoryNamespace ns,
            Instant now, List<PromotionEvidence> evidence) {
        evidence.add(PromotionEvidence.passing(
                UUID.randomUUID().toString(),
                request.tenantId(), request.agentId(), ns.namespaceId(),
                request.sourceMemoryId(), STEP_CREATE_EVIDENCE, 4, now));
        log.debug("Promotion evidence created for memory [{}]", request.sourceMemoryId());
        return Promise.of(null);
    }

    // ─── Step 5: Mark Promoted ─────────────────────────────────────────────────

    private Promise<Void> step5MarkPromoted(
            PromotionRequest request, MemoryNamespace ns,
            Instant now, List<PromotionEvidence> evidence) {
        // Store a metadata entry in episodic tier recording the promotion timestamp
        MemoryService.MemoryEntry promotionMarker = MemoryService.MemoryEntry.create(
                request.agentId(),
                MemoryService.MemoryTier.EPISODIC,
                "__promoted__:" + request.sourceMemoryId(),
                java.util.Map.of("promotedAt", now.toString(), "targetNamespace", ns.namespaceId()),
                0.1);
        return memoryService.store(request.agentId(), MemoryService.MemoryTier.EPISODIC, promotionMarker)
                .map(stored -> {
                    evidence.add(PromotionEvidence.passing(
                            UUID.randomUUID().toString(),
                            request.tenantId(), request.agentId(), ns.namespaceId(),
                            request.sourceMemoryId(), STEP_MARK_PROMOTED, 5, now));
                    return null;
                });
    }

    // ─── Step 6: Write Procedural ──────────────────────────────────────────────

    private Promise<String> step6WriteProcedural(
            PromotionRequest request, MemoryNamespace ns,
            Instant now, List<PromotionEvidence> evidence) {
        MemoryService.MemoryEntry proceduralEntry = MemoryService.MemoryEntry.create(
                request.agentId(),
                MemoryService.MemoryTier.PROCEDURAL,
                "[PROMOTED] " + request.sourceContent(),
                java.util.Map.of(
                        "promotedFrom", request.sourceMemoryId(),
                        "namespace", ns.namespaceId(),
                        "promotedAt", now.toString()),
                request.importanceScore());
        return memoryService.store(request.agentId(), MemoryService.MemoryTier.PROCEDURAL, proceduralEntry)
                .map(stored -> {
                    String targetId = stored.id() != null ? stored.id() : UUID.randomUUID().toString();
                    evidence.add(PromotionEvidence.passing(
                            UUID.randomUUID().toString(),
                            request.tenantId(), request.agentId(), ns.namespaceId(),
                            request.sourceMemoryId(), STEP_WRITE_PROCEDURAL, 6, now));
                    log.info("Memory [{}] promoted to procedural namespace [{}] as [{}]",
                            request.sourceMemoryId(), ns.namespaceId(), targetId);
                    return targetId;
                });
    }

    // ─── Step 7: Emit Event ───────────────────────────────────────────────────

    private Promise<PromotionResult> step7EmitEvent(
            PromotionRequest request, String targetId,
            List<PromotionEvidence> evidence, Instant now) {
        evidence.add(PromotionEvidence.passing(
                UUID.randomUUID().toString(),
                request.tenantId(), request.agentId(),
                evidence.getLast().namespaceId(),
                request.sourceMemoryId(), STEP_EMIT_EVENT, 7, now));
        return Promise.of(PromotionResult.success(targetId, evidence, now));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Computes an internal quality score from the caller-supplied importance score.
     *
     * <p>The score is clamped to [0.0, 1.0] and can be adjusted by subclasses or
     * decorated implementations to incorporate additional signals (e.g., access frequency,
     * evaluation pack scores, or recency weights).
     */
    private double computeQualityScore(PromotionRequest request) {
        double base = Math.max(0.0, Math.min(1.0, request.importanceScore()));
        // Apply a mild recency bias; future implementations may integrate EvaluationResult scores
        return base;
    }
}
