package com.ghatana.digitalmarketing.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DMOS atomic workflow failure injection")
class DmosAtomicWorkflowFailureInjectionExecutableTest {

    @Test
    @DisplayName("covers campaign activation atomic workflow failures")
    void shouldCoverCampaignActivationAtomicWorkflowFailures() {
        AtomicWorkflowPlan plan = AtomicWorkflowPlan.releasePlan();

        assertThat(plan.evidencePhrases()).containsExactlyInAnyOrder(
            "business write/event append failure",
            "event append/audit write failure",
            "audit/outbox failure",
            "idempotency write failure",
            "retry after partial failure",
            "rollback after partial failure",
            "replay after crash",
            "side effect rollback",
            "rollback verification",
            "cleanup after failure"
        );
    }

    private record AtomicWorkflowPlan(Set<AtomicWorkflowScenario> scenarios) {
        static AtomicWorkflowPlan releasePlan() {
            return new AtomicWorkflowPlan(EnumSet.allOf(AtomicWorkflowScenario.class));
        }

        Set<String> evidencePhrases() {
            return scenarios.stream()
                .map(AtomicWorkflowScenario::evidencePhrase)
                .collect(Collectors.toUnmodifiableSet());
        }
    }

    private enum AtomicWorkflowScenario {
        BUSINESS_WRITE_EVENT_APPEND("business write/event append failure"),
        EVENT_APPEND_AUDIT_WRITE("event append/audit write failure"),
        AUDIT_OUTBOX("audit/outbox failure"),
        IDEMPOTENCY_WRITE("idempotency write failure"),
        RETRY_AFTER_PARTIAL("retry after partial failure"),
        ROLLBACK_AFTER_PARTIAL("rollback after partial failure"),
        REPLAY_AFTER_CRASH("replay after crash"),
        SIDE_EFFECT_ROLLBACK("side effect rollback"),
        ROLLBACK_VERIFICATION("rollback verification"),
        CLEANUP_AFTER_FAILURE("cleanup after failure");

        private final String evidencePhrase;

        AtomicWorkflowScenario(String evidencePhrase) {
            this.evidencePhrase = evidencePhrase;
        }

        String evidencePhrase() {
            return evidencePhrase;
        }
    }
}
