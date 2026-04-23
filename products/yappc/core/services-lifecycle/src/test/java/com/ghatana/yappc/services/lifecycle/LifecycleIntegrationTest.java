/*
 * Copyright (c) 2026 Ghatana Technologies // GH-90000
 * YAPPC Lifecycle Service — Integration Tests
 */
package com.ghatana.yappc.services.lifecycle;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.services.feature.FeatureFlagService;
import com.ghatana.yappc.services.lifecycle.workflow.LifecycleWorkflowService;
import com.ghatana.platform.workflow.engine.DurableWorkflowEngine;
import com.ghatana.yappc.framework.core.config.FeatureFlag;
import com.ghatana.yappc.framework.core.config.FeatureFlags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests covering YAPPC lifecycle service cross-concern wiring.
 *
 * <p>Validates that feature flags, workflow orchestration, and metrics components
 * wire together correctly across the lifecycle service boundary.
 *
 * @doc.type class
 * @doc.purpose Lifecycle cross-concern integration test suite
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("YAPPC Lifecycle Integration")
class LifecycleIntegrationTest extends EventloopTestBase {

    private DurableWorkflowEngine    engine;
    private LifecycleWorkflowService workflowService;
    private FeatureFlagService       featureFlagService;
    private SimpleMeterRegistry      meterRegistry;

    @BeforeEach
    void setUp() { // GH-90000
        engine = DurableWorkflowEngine.builder() // GH-90000
                .stateStore(new DurableWorkflowEngine.InMemoryWorkflowStateStore()) // GH-90000
                .defaultTimeout(Duration.ofSeconds(10)) // GH-90000
                .defaultMaxRetries(1) // GH-90000
                .defaultRetryBackoff(Duration.ofMillis(50)) // GH-90000
                .build(); // GH-90000
        workflowService    = new LifecycleWorkflowService(engine); // GH-90000
        featureFlagService = new FeatureFlagService(); // GH-90000
        meterRegistry      = new SimpleMeterRegistry(); // GH-90000
        FeatureFlags.clearOverrides(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        FeatureFlags.clearOverrides(); // GH-90000
    }

    // ── Feature flag + workflow interaction ───────────────────────────────────

    @Nested
    @DisplayName("feature flags drive workflow behaviour")
    class FeatureFlagWorkflowIntegration {

        @Test
        @DisplayName("AI_REQUIREMENT_EXTRACTION flag is disabled by default (safe default)")
        void aiRequirementExtractionIsOffByDefault() { // GH-90000
            assertThat(featureFlagService.isDisabled(FeatureFlag.AI_REQUIREMENT_EXTRACTION)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("overriding flag to true is reflected in FeatureFlagService")
        void overrideFlagIsReflectedInService() { // GH-90000
            FeatureFlags.override(FeatureFlag.AI_CODE_REVIEW, true); // GH-90000

            assertThat(featureFlagService.isEnabled(FeatureFlag.AI_CODE_REVIEW)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("clearing overrides restores default state")
        void clearingOverridesRestoresDefault() { // GH-90000
            FeatureFlags.override(FeatureFlag.PATTERN_LEARNING, true); // GH-90000
            FeatureFlags.clearOverrides(); // GH-90000

            assertThat(featureFlagService.isDisabled(FeatureFlag.PATTERN_LEARNING)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("snapshot returns all known flags")
        void snapshotReturnsAllKnownFlags() { // GH-90000
            Map<FeatureFlag, Boolean> snapshot = featureFlagService.snapshot(); // GH-90000

            assertThat(snapshot).containsKeys(FeatureFlag.values()); // GH-90000
        }
    }

    // ── Workflow service initialisation ───────────────────────────────────────

    @Nested
    @DisplayName("workflow service initialisation")
    class WorkflowServiceInit {

        @Test
        @DisplayName("workflowService initialises and registers templates")
        void workflowServiceInitialisesTemplates() { // GH-90000
            int count = workflowService.initialize(); // GH-90000
            assertThat(count).isGreaterThanOrEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("registeredTemplates includes standard SDLC workflows")
        void registeredTemplatesContainsStandardWorkflows() { // GH-90000
            workflowService.initialize(); // GH-90000
            assertThat(workflowService.registeredTemplates()) // GH-90000
                    .contains("new-feature", "bug-fix", "security-remediation"); // GH-90000
        }
    }

    // ── Workflow execution ────────────────────────────────────────────────────

    @Nested
    @DisplayName("workflow execution lifecycle")
    class WorkflowExecution {

        @BeforeEach
        void initTemplates() { // GH-90000
            workflowService.initialize(); // GH-90000
        }

        @Test
        @DisplayName("starting a new-feature workflow returns a workflow ID")
        void startNewFeatureWorkflowReturnsId() { // GH-90000
            DurableWorkflowEngine.WorkflowExecution exec =
                    workflowService.startWorkflow("new-feature", "tenant-1", // GH-90000
                            Map.of("title", "Add dark mode support")); // GH-90000

            assertThat(exec.workflowId()).isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("workflow status is accessible after start")
        void workflowStatusIsAccessibleAfterStart() { // GH-90000
            DurableWorkflowEngine.WorkflowExecution exec =
                    workflowService.startWorkflow("bug-fix", "tenant-1", // GH-90000
                            Map.of("bugId", "BUG-123")); // GH-90000

            Optional<DurableWorkflowEngine.WorkflowRun> statusOpt =
                    workflowService.getRunStatus(exec.workflowId()); // GH-90000
            assertThat(statusOpt).isPresent(); // GH-90000
            assertThat(statusOpt.get().status()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("different tenants get different workflow IDs for same template")
        void differentTenantsGetIsolatedWorkflowIds() { // GH-90000
            DurableWorkflowEngine.WorkflowExecution exec1 =
                    workflowService.startWorkflow("bug-fix", "tenant-A", // GH-90000
                            Map.of("bugId", "BUG-1")); // GH-90000
            DurableWorkflowEngine.WorkflowExecution exec2 =
                    workflowService.startWorkflow("bug-fix", "tenant-B", // GH-90000
                            Map.of("bugId", "BUG-1")); // GH-90000

            assertThat(exec1.workflowId()).isNotEqualTo(exec2.workflowId()); // GH-90000
        }
    }
}
