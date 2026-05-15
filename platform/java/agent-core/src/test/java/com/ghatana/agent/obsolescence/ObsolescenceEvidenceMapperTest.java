/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.obsolescence;

import com.ghatana.agent.mastery.MasteryState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ObsolescenceEvidenceMapper Tests")
class ObsolescenceEvidenceMapperTest {

    @Test
    @DisplayName("VERSION_MISMATCH should emit version transition keys")
    void shouldMapVersionMismatchToVersionKeys() {
        ObsolescenceEvent event = ObsolescenceEvent.of(
                "mastery-1",
                "tenant-1",
                ObsolescenceReason.VERSION_MISMATCH,
                "Version mismatch",
                List.of(),
                Map.of("new_active_version_id", "2.0.0"),
                ObsolescenceEvent.Severity.MEDIUM,
                MasteryState.MAINTENANCE_ONLY
        );

        Map<String, String> mapped = ObsolescenceEvidenceMapper.toTransitionEvidence(event);

        assertThat(mapped).containsEntry("new_active_version_id", "2.0.0");
        assertThat(mapped).containsEntry("replaced_by_newer", "true");
    }

    @Test
    @DisplayName("SECURITY_VULNERABILITY should emit quarantine-compatible keys")
    void shouldMapSecurityToSafetyKeys() {
        ObsolescenceEvent event = ObsolescenceEvent.of(
                "mastery-2",
                "tenant-2",
                ObsolescenceReason.SECURITY_VULNERABILITY,
                "Vulnerability detected",
                List.of(),
                Map.of(),
                ObsolescenceEvent.Severity.CRITICAL,
                MasteryState.QUARANTINED
        );

        Map<String, String> mapped = ObsolescenceEvidenceMapper.toTransitionEvidence(event);

        assertThat(mapped).containsEntry("security_advisory", "true");
        assertThat(mapped).containsEntry("safety_violation", "true");
    }

    @Test
    @DisplayName("API_CHANGE should emit api_break key")
    void shouldMapApiChangeToApiBreak() {
        ObsolescenceEvent event = ObsolescenceEvent.of(
                "mastery-3",
                "tenant-3",
                ObsolescenceReason.API_CHANGE,
                "API changed",
                List.of(),
                Map.of(),
                ObsolescenceEvent.Severity.HIGH,
                MasteryState.OBSOLETE
        );

        Map<String, String> mapped = ObsolescenceEvidenceMapper.toTransitionEvidence(event);

        assertThat(mapped).containsEntry("api_break", "true");
    }
}
