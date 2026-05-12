/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.obsolescence;

import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryState;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Policy for determining when to mark mastery items as obsolete.
 *
 * @doc.type interface
 * @doc.purpose Policy for obsolescence decisions
 * @doc.layer agent-core
 * @doc.pattern Policy
 */
public interface ObsolescencePolicy {

    /**
     * Evaluates whether a mastery item should be marked as obsolete.
     *
     * @param item mastery item to evaluate
     * @param signals obsolescence signals for the item
     * @return promise of obsolescence decision
     */
    @NotNull
    Promise<ObsolescenceDecision> evaluate(
            @NotNull MasteryItem item,
            @NotNull List<ObsolescenceSignal> signals);

    /**
     * Returns the threshold for marking as obsolete.
     *
     * @return threshold (0.0 to 1.0)
     */
    double threshold();

    /**
     * Default obsolescence policy that marks items obsolete based on signal severity.
     */
    ObsolescencePolicy DEFAULT = new ObsolescencePolicy() {
        @Override
        @NotNull
        public Promise<ObsolescenceDecision> evaluate(
                @NotNull MasteryItem item,
                @NotNull List<ObsolescenceSignal> signals) {
            Objects.requireNonNull(item, "item must not be null");
            Objects.requireNonNull(signals, "signals must not be null");

            double maxSeverity = signals.stream()
                    .mapToDouble(ObsolescenceSignal::severity)
                    .max()
                    .orElse(0.0);

            if (maxSeverity >= threshold()) {
                return Promise.of(ObsolescenceDecision.markObsolete(
                        item.masteryId(),
                        "Severity threshold exceeded: " + maxSeverity
                ));
            }

            return Promise.of(ObsolescenceDecision.keep(item.masteryId()));
        }

        @Override
        public double threshold() {
            return 0.7;
        }
    };

    /**
     * Conservative policy requiring high confidence before marking obsolete.
     */
    ObsolescencePolicy CONSERVATIVE = new ObsolescencePolicy() {
        @Override
        @NotNull
        public Promise<ObsolescenceDecision> evaluate(
                @NotNull MasteryItem item,
                @NotNull List<ObsolescenceSignal> signals) {
            Objects.requireNonNull(item, "item must not be null");
            Objects.requireNonNull(signals, "signals must not be null");

            long highSeverityCount = signals.stream()
                    .filter(ObsolescenceSignal::isHighSeverity)
                    .count();

            if (highSeverityCount >= 2) {
                return Promise.of(ObsolescenceDecision.markObsolete(
                        item.masteryId(),
                        "Multiple high severity signals: " + highSeverityCount
                ));
            }

            return Promise.of(ObsolescenceDecision.keep(item.masteryId()));
        }

        @Override
        public double threshold() {
            return 0.9;
        }
    };

    /**
     * Record representing an obsolescence decision.
     */
    record ObsolescenceDecision(
            @NotNull String masteryItemId,
            boolean shouldMarkObsolete,
            @NotNull String reason
    ) {
        public ObsolescenceDecision {
            Objects.requireNonNull(masteryItemId, "masteryItemId must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
        }

        /**
         * Creates a decision to mark the item as obsolete.
         *
         * @param masteryItemId mastery item identifier
         * @param reason reason for obsolescence
         * @return obsolescence decision
         */
        @NotNull
        public static ObsolescenceDecision markObsolete(@NotNull String masteryItemId, @NotNull String reason) {
            return new ObsolescenceDecision(masteryItemId, true, reason);
        }

        /**
         * Creates a decision to keep the item.
         *
         * @param masteryItemId mastery item identifier
         * @return obsolescence decision
         */
        @NotNull
        public static ObsolescenceDecision keep(@NotNull String masteryItemId) {
            return new ObsolescenceDecision(masteryItemId, false, "No obsolescence detected");
        }
    }
}
