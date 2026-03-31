/*
 * Copyright (c) 2026 Ghatana Technologies
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
    void setUp() {
        engine = DurableWorkflowEngine.builder()
                .stateStore(new DurableWorkflowEngine.InMemoryWorkflowStateStore())
                .defaultTimeout(Duration.ofSeconds(10))
                .defaultMaxRetries(1)
                .defaultRetryBackoff(Duration.ofMillis(50))
                .build();
        workflowService    = new LifecycleWorkflowService(engine);
        featureFlagService = new FeatureFlagService();
        meterRegistry      = new SimpleMeterRegistry();
        FeatureFlags.clearOverrides();
    }

    @AfterEach
    void tearDown() {
        FeatureFlags.clearOverrides();
    }

    // ── Feature flag + workflow interaction ───────────────────────────────────

    @Nested
    @DisplayName("feature flags drive workflow behaviour")
    class FeatureFlagWorkflowIntegration {

        @Test
        @DisplayName("AI_REQUIREMENT_EXTRACTION flag is disabled by default (safe default)")
        void aiRequirementExtractionIsOffByDefault() {
            assertThat(featureFlagService.isDisabled(FeatureFlag.AI_REQUIREMENT_EXTRACTION)).isTrue();
        }

        @Test
        @DisplayName("overriding flag to true is reflected in FeatureFlagService")
        void overrideFlagIsReflectedInService() {
            FeatureFlags.override(FeatureFlag.AI_CODE_REVIEW, true);

            assertThat(featureFlagService.isEnabled(FeatureFlag.AI_CODE_REVIEW)).isTrue();
        }

        @Test
        @DisplayName("clearing overrides restores default state")
        void clearingOverridesRestoresDefault() {
            FeatureFlags.override(FeatureFlag.PATTERN_LEARNING, true);
            FeatureFlags.clearOverrides();

            assertThat(featureFlagService.isDisabled(FeatureFlag.PATTERN_LEARNING)).isTrue();
        }

        @Test
        @DisplayName("snapshot returns all known flags")
        void snapshotReturnsAllKnownFlags() {
            Map<FeatureFlag, Boolean> snapshot = featureFlagService.snapshot();

            assertThat(snapshot).containsKeys(FeatureFlag.values());
        }
    }

    // ── Workflow service initialisation ───────────────────────────────────────

    @Nested
    @DisplayName("workflow service initialisation")
    class WorkflowServiceInit {

        @Test
        @DisplayName("workflowService initialises and registers templates")
        void workflowServiceInitialisesTemplates() {
            int count = workflowService.initialize();
            assertThat(count).isGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("registeredTemplates includes standard SDLC workflows")
        void registeredTemplatesContainsStandardWorkflows() {
            workflowService.initialize();
            assertThat(workflowService.registeredTemplates())
                    .contains("new-feature", "bug-fix", "security-remediation");
        }
    }

    // ── Workflow execution ────────────────────────────────────────────────────

    @Nested
    @DisplayName("workflow execution lifecycle")
    class WorkflowExecution {

        @BeforeEach
        void initTemplates() {
            workflowService.initialize();
        }

        @Test
        @DisplayName("starting a new-feature workflow returns a workflow ID")
        void startNewFeatureWorkflowReturnsId() {
            DurableWorkflowEngine.WorkflowExecution exec =
                    workflowService.startWorkflow("new-feature", "tenant-1",
                            Map.of("title", "Add dark mode support"));

            assertThat(exec.workflowId()).isNotBlank();
        }

        @Test
        @DisplayName("workflow status is accessible after start")
        void workflowStatusIsAccessibleAfterStart() {
            DurableWorkflowEngine.WorkflowExecution exec =
                    workflowService.startWorkflow("bug-fix", "tenant-1",
                            Map.of("bugId", "BUG-123"));

            Optional<DurableWorkflowEngine.WorkflowRun> statusOpt =
                    workflowService.getRunStatus(exec.workflowId());
            assertThat(statusOpt).isPresent();
            assertThat(statusOpt.get().status()).isNotNull();
        }

        @Test
        @DisplayName("different tenants get different workflow IDs for same template")
        void differentTenantsGetIsolatedWorkflowIds() {
            DurableWorkflowEngine.WorkflowExecution exec1 =
                    workflowService.startWorkflow("bug-fix", "tenant-A",
                            Map.of("bugId", "BUG-1"));
            DurableWorkflowEngine.WorkflowExecution exec2 =
                    workflowService.startWorkflow("bug-fix", "tenant-B",
                            Map.of("bugId", "BUG-1"));

            assertThat(exec1.workflowId()).isNotEqualTo(exec2.workflowId());
        }
    }
}
