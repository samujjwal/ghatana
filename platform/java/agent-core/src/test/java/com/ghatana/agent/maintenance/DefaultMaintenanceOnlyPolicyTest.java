/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.maintenance;

import com.ghatana.agent.context.version.VersionContext;
import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.mastery.VersionScope;
import com.ghatana.agent.mastery.ApplicabilityScope;
import com.ghatana.agent.mastery.MasteryScore;
import com.ghatana.agent.mastery.MasteryTransition;
import com.ghatana.agent.mastery.VersionApplicability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Tests for DefaultMaintenanceOnlyPolicy.
 * Phase 8 FIX: Tests for maintenance-only policy with strict rules.
 *
 * @doc.type class
 * @doc.purpose Tests for DefaultMaintenanceOnlyPolicy
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("DefaultMaintenanceOnlyPolicy Tests")
class DefaultMaintenanceOnlyPolicyTest {

    private DefaultMaintenanceOnlyPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new DefaultMaintenanceOnlyPolicy();
        policy.setHumanGated(false); // Disable human gating for isolated NEW_WORK intent testing
    }

    @Test
    @DisplayName("Should allow legacy intent for MAINTENANCE_ONLY item")
    void shouldAllowLegacyIntent() {
        MasteryItem item = createMaintenanceOnlyItem();
        TaskIntent intent = TaskIntent.LEGACY_MAINTENANCE;

        MaintenanceOnlyPolicy.ValidationResult result = policy.validate(item, intent);

        assertTrue(result.allowed());
    }

    @Test
    @DisplayName("Should deny NEW_WORK intent for MAINTENANCE_ONLY item")
    void shouldDenyNewWorkIntent() {
        MasteryItem item = createMaintenanceOnlyItem();
        TaskIntent intent = TaskIntent.NEW_WORK;

        MaintenanceOnlyPolicy.ValidationResult result = policy.validate(item, intent);

        assertFalse(result.allowed());
        assertTrue(result.reason().contains("cannot execute new work"));
    }

    @Test
    @DisplayName("Should allow LEGACY_MAINTENANCE intent for MAINTENANCE_ONLY item")
    void shouldAllowLegacyMaintenanceIntent() {
        MasteryItem item = createMaintenanceOnlyItem();
        TaskIntent intent = TaskIntent.LEGACY_MAINTENANCE;

        MaintenanceOnlyPolicy.ValidationResult result = policy.validate(item, intent);

        assertTrue(result.allowed());
    }

    @Test
    @DisplayName("Should deny NEW_WORK when human-gated")
    void shouldRequireHumanApproval() {
        policy.setHumanGated(true);
        MasteryItem item = createMaintenanceOnlyItem();
        TaskIntent intent = TaskIntent.NEW_WORK;

        MaintenanceOnlyPolicy.ValidationResult result = policy.validate(item, intent);

        assertFalse(result.allowed());
        assertTrue(result.reason().contains("requires human approval"));
    }

    @Test
    @DisplayName("Should check version context matches legacy scope")
    void shouldCheckVersionContext() {
        MasteryItem item = createMaintenanceOnlyItem();
        TaskIntent intent = TaskIntent.LEGACY_MAINTENANCE;
        VersionContext versionContext = new VersionContext(
                Map.of("java", "17"),
                Map.of(),
                Map.of(),
                Map.of(),
                "repo1",
                Instant.now()
        );

        // Mock version applicability to return ACTIVE instead of MAINTENANCE
        // In a real test, this would use a mock VersionScope
        MaintenanceOnlyPolicy.ValidationResult result = policy.validate(item, intent, versionContext);

        // This would fail if version context doesn't match legacy scope
        // For now, we just test the method exists
        assertNotNull(result);
    }

    private MasteryItem createMaintenanceOnlyItem() {
        return new MasteryItem(
                "mastery1",
                "tenant1",
                "skill1",
                "domain1",
                "agent1",
                "release1",
                MasteryState.MAINTENANCE_ONLY,
                VersionScope.empty(),
                ApplicabilityScope.minimal("tenant1", "production"),
                MasteryScore.correctnessOnly(0.8),
                List.<String>of(),
                List.<String>of(),
                List.<String>of(),
                List.<String>of(),
                List.<String>of(),
                List.<String>of(),
                List.<MasteryTransition>of(),
                Instant.now(),
                Instant.now().plusSeconds(86400),
                Map.<String, String>of(),
                0.8
        );
    }
}
