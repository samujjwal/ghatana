package com.ghatana.appplatform.compliance.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * @doc.type      Service
 * @doc.purpose   AML risk scoring service. Assigns and manages AML risk levels (LOW/MEDIUM/HIGH/CRITICAL)
 *                per client, applies trading restrictions based on risk level, and integrates with
 *                external AML providers via a T3 adapter port.
 * @doc.layer     Application
 * @doc.pattern   Strategy per risk level + external adapter integration
 *
 * Risk-level rules:
 *   LOW      → standard processing
 *   MEDIUM   → enhanced monitoring, all trades flagged for review log
 *   HIGH     → maker-checker required for ALL trades
 *   CRITICAL → trading suspended
 *
 * Story: D07-007
 */
public class AmlRiskScoringService {

    private static final Logger log = LoggerFactory.getLogger(AmlRiskScoringService.class);

    private final AmlRiskStore     riskStore;
    private final AmlProviderPort  providerPort;
    private final Consumer<Object> eventPublisher;
    private final Counter          riskEscalations;
    private final Counter          criticalSuspensions;

    public AmlRiskScoringService(AmlRiskStore riskStore, AmlProviderPort providerPort,
                                  Consumer<Object> eventPublisher, MeterRegistry meterRegistry) {
        this.riskStore          = riskStore;
        this.providerPort       = providerPort;
        this.eventPublisher     = eventPublisher;
        this.riskEscalations    = meterRegistry.counter("compliance.aml.escalations");
        this.criticalSuspensions = meterRegistry.counter("compliance.aml.suspensions");
    }

    /**
     * Evaluates and updates the AML risk score for a client.
     * Fetches from external AML provider (T3) and updates the risk store.
     *
     * @param clientId client to evaluate
     * @return current AML risk level
     */
    public AmlRiskLevel refresh(String clientId) {
        AmlRiskLevel current  = riskStore.findRiskLevel(clientId).orElse(AmlRiskLevel.LOW);
        AmlRiskLevel updated  = providerPort.fetchRiskLevel(clientId);

        riskStore.upsert(clientId, updated);

        if (updated != current) {
            riskEscalations.increment();
            log.info("AmlRiskScoring: risk changed client={} {} → {}", clientId, current, updated);
            eventPublisher.accept(new ClientRiskChangedEvent(clientId, current, updated));
        }

        if (updated == AmlRiskLevel.CRITICAL) {
            criticalSuspensions.increment();
            eventPublisher.accept(new TradingSuspendedEvent(clientId, "AML risk CRITICAL"));
        }

        return updated;
    }

    /**
     * Returns the AML trading restriction applicable to a client.
     */
    public AmlRestriction getRestriction(String clientId) {
        AmlRiskLevel level = riskStore.findRiskLevel(clientId).orElse(AmlRiskLevel.LOW);
        return switch (level) {
            case LOW      -> AmlRestriction.STANDARD;
            case MEDIUM   -> AmlRestriction.ENHANCED_MONITORING;
            case HIGH     -> AmlRestriction.MAKER_CHECKER_ALL;
            case CRITICAL -> AmlRestriction.SUSPENDED;
        };
    }

    // ─── Ports ────────────────────────────────────────────────────────────────

    public interface AmlRiskStore {
        java.util.Optional<AmlRiskLevel> findRiskLevel(String clientId);
        void upsert(String clientId, AmlRiskLevel level);
    }

    public interface AmlProviderPort {
        AmlRiskLevel fetchRiskLevel(String clientId);
    }

    // ─── Domain types ─────────────────────────────────────────────────────────

    public enum AmlRiskLevel { LOW, MEDIUM, HIGH, CRITICAL }
    public enum AmlRestriction { STANDARD, ENHANCED_MONITORING, MAKER_CHECKER_ALL, SUSPENDED }

    // ─── Events ───────────────────────────────────────────────────────────────

    public record ClientRiskChangedEvent(String clientId, AmlRiskLevel from, AmlRiskLevel to) {}
    public record TradingSuspendedEvent(String clientId, String reason) {}
}
