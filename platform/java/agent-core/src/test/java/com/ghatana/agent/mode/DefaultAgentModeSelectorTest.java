/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mode;

import com.ghatana.agent.AgentType;
import com.ghatana.agent.environment.EnvironmentFingerprint;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.config.AgentDefinition;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.agent.release.AgentRelease;
import com.ghatana.agent.release.AgentReleaseBuilder;
import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryScore;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.mastery.VersionScope;
import com.ghatana.agent.mastery.ApplicabilityScope;
import com.ghatana.agent.runtime.mode.ExecutionMode;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @doc.type class
 * @doc.purpose Tests for DefaultAgentModeSelector
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("DefaultAgentModeSelector Tests")
@ExtendWith(MockitoExtension.class)
class DefaultAgentModeSelectorTest extends EventloopTestBase {

    @Mock
    private MemoryStore memoryStore;

    private final DefaultAgentModeSelector selector = new DefaultAgentModeSelector(new SimpleTaskClassifier());

    @Test
    @DisplayName("Mastered fresh skill uses deterministic mode")
    void masteredFreshUsesDeterministic() {
        AgentDefinition definition = createDefinition();
        AgentRelease release = createRelease();
        AgentContext context = createMockContext();
        EnvironmentFingerprint env = EnvironmentFingerprint.minimal("tenant-1", "repo-1", "typescript");
        MasteryItem mastery = createMasteredMastery();

        ModeDecision decision = runPromise(() -> selector.decide(definition, release, context, env, Optional.of(mastery), "test task"));
        assertThat(decision.executionMode()).isEqualTo(ExecutionMode.DETERMINISTIC_EXECUTION);
    }

    @Test
    @DisplayName("Obsolete skill uses blocked mode")
    void obsoleteUsesBlocked() {
        AgentDefinition definition = createDefinition();
        AgentRelease release = createRelease();
        AgentContext context = createMockContext();
        EnvironmentFingerprint env = EnvironmentFingerprint.minimal("tenant-1", "repo-1", "typescript");
        MasteryItem mastery = createObsoleteMastery();

        ModeDecision decision = runPromise(() -> selector.decide(definition, release, context, env, Optional.of(mastery), "test task"));
        assertThat(decision.executionMode()).isEqualTo(ExecutionMode.BLOCKED);
    }

    @Test
    @DisplayName("Unknown task uses fast-learning mode")
    void unknownTaskUsesFastLearning() {
        AgentDefinition definition = createDefinition();
        AgentRelease release = createRelease();
        AgentContext context = createMockContext();
        EnvironmentFingerprint env = EnvironmentFingerprint.minimal("tenant-1", "repo-1", "typescript");

        ModeDecision decision = runPromise(() -> selector.decide(definition, release, context, env, Optional.empty(), "test task"));
        assertThat(decision.executionMode()).isEqualTo(ExecutionMode.EXPLORATORY_FAST_LEARNING);
    }

    private static class SimpleTaskClassifier implements TaskClassifier {
        @Override
        public TaskClass classify(String taskDescription, Optional<MasteryItem> mastery, EnvironmentFingerprint env) {
            if (mastery.isEmpty()) {
                return TaskClass.UNKNOWN_TASK;
            }
            return TaskClass.KNOWN_TASK;
        }
    }

    private AgentDefinition createDefinition() {
        return AgentDefinition.builder()
                .id("agent-1")
                .version("1.0.0")
                .type(AgentType.DETERMINISTIC)
                .build();
    }

    private AgentRelease createRelease() {
        return new AgentReleaseBuilder()
                .agentReleaseId("release-1")
                .agentId("agent-1")
                .releaseVersion("1.0.0")
                .redactionProfileId("default")
                .threatModelId("default")
                .addPermittedPurpose("treatment")
                .capabilityMaturityProfile("L1")
                .evaluationPackId("eval-pack-1")
                .evaluationPackDigest("digest-1")
                .memoryContractId("memory-contract-1")
                .createdBy("test")
                .build();
    }

    private AgentContext createMockContext() {
        return AgentContext.builder()
                .agentId("agent-1")
                .turnId("turn-1")
                .tenantId("tenant-1")
                .memoryStore(memoryStore)
                .build();
    }

    private MasteryItem createMasteredMastery() {
        return new MasteryItem(
                "mastery-1",
                "tenant-1",
                "skill-1",
                "domain-1",
                "agent-1",
                "release-1",
                MasteryState.MASTERED,
                VersionScope.empty(),
                ApplicabilityScope.minimal("tenant-1", "production"),
                new MasteryScore(0.9, 0.8, 0.7, 0.9, 0.6, 0.8, 0.9),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Instant.now(),
                Instant.now().plus(30, ChronoUnit.DAYS),
                Map.of(),
                List.of(),
                0.9
        );
    }

    private MasteryItem createObsoleteMastery() {
        return new MasteryItem(
                "mastery-1",
                "tenant-1",
                "skill-1",
                "domain-1",
                "agent-1",
                "release-1",
                MasteryState.OBSOLETE,
                VersionScope.empty(),
                ApplicabilityScope.minimal("tenant-1", "production"),
                new MasteryScore(0.9, 0.8, 0.7, 0.9, 0.6, 0.8, 0.9),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Instant.now().minus(100, ChronoUnit.DAYS),
                Instant.now().minus(1, ChronoUnit.DAYS),
                Map.of(),
                List.of(),
                0.9
        );
    }
}
