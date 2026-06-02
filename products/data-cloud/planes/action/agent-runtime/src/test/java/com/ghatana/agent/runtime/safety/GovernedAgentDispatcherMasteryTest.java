/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentResultStatus;
import com.ghatana.agent.audit.AgentTraceLedger;
import com.ghatana.agent.context.version.VersionContext;
import com.ghatana.agent.dispatch.AgentDispatcher;
import com.ghatana.agent.dispatch.ExecutionTier;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.agent.mastery.MasteryDecision;
import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.mastery.MasteryScore;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.mastery.VersionApplicability;
import com.ghatana.agent.mastery.VersionScope;
import com.ghatana.agent.runtime.mode.ExecutionStrategy;
import com.ghatana.agent.runtime.mode.MasteryAwareModeSelector;
import com.ghatana.agent.runtime.mode.TaskClassification;
import com.ghatana.agent.runtime.mode.TaskNovelty;
import com.ghatana.agent.runtime.mode.TaskRiskLevel;
import com.ghatana.agent.runtime.mode.SupervisionMode;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Validates mastery-aware dispatch behavior with current runtime contracts
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("GovernedAgentDispatcher Mastery")
class GovernedAgentDispatcherMasteryTest extends EventloopTestBase {

    private AgentDispatcher delegate;
    private AgentTraceLedger traceLedger;
    private MasteryRegistry masteryRegistry;
    private MasteryAwareModeSelector modeSelector;
    private GovernedAgentDispatcher dispatcher;
    private AgentContext ctxWithSkill;

    @BeforeEach
    void setUp() {
        delegate = mock(AgentDispatcher.class);
        traceLedger = mock(AgentTraceLedger.class);
        masteryRegistry = mock(MasteryRegistry.class);
        modeSelector = mock(MasteryAwareModeSelector.class);

        lenient().when(traceLedger.append(any())).thenReturn(Promise.of(null));
        lenient().when(delegate.resolve(anyString())).thenReturn(ExecutionTier.JAVA_IMPLEMENTED);
        lenient().when(delegate.dispatch(anyString(), any(), any()))
                .thenReturn(Promise.of(AgentResult.builder()
                        .status(AgentResultStatus.SUCCESS)
                        .agentId("test-agent")
                        .confidence(1.0)
                        .processingTime(Duration.ofMillis(10))
                        .build()));

        MasteryDecision decision = MasteryDecision.allow(
                "item-1",
                "skill-1",
                MasteryState.PRACTICED,
                MasteryScore.correctnessOnly(0.8),
                VersionScope.empty(),
                "allowed"
        );

        when(masteryRegistry.decide(any()))
                .thenReturn(Promise.of(decision));

        MasteryAwareModeSelector.EnrichedModeSelectionResult autonomousMode =
                new MasteryAwareModeSelector.EnrichedModeSelectionResult(
                        ExecutionStrategy.DETERMINISTIC_EXECUTION,
                        SupervisionMode.AUTONOMOUS,
                        "mastery-allowed",
                        Map.of("traceId", "trace-1", "durationMs", 1L),
                        decision,
                        TaskClassification.of(TaskRiskLevel.LOW, TaskNovelty.FAMILIAR),
                        VersionContext.empty()
                );

        when(modeSelector.selectModeWithDecision(any(), anyString(), anyString(), any(), anyString(), any()))
                .thenReturn(Promise.of(autonomousMode));

        dispatcher = new GovernedAgentDispatcher(
                delegate,
                new DefaultInvariantMonitor(),
                traceLedger,
                null,
                null,
                null,
                masteryRegistry,
                null,
                null,
                modeSelector,
                null
        );

        ctxWithSkill = AgentContext.builder()
                .tenantId("tenant-a")
                .agentId("test-agent")
                .turnId("turn-1")
                .memoryStore(mock(MemoryStore.class))
                .addConfig("skillId", "skill-1")
                .build();
    }

    @Test
    @DisplayName("uses mastery decision and mode selector during dispatch")
    void usesMasteryDecisionAndModeSelection() {
        AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", ctxWithSkill));

        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS);
        verify(masteryRegistry).decide(any());
        verify(modeSelector).selectModeWithDecision(any(), anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    @DisplayName("denies when mode selector returns blocked supervision")
    void deniesWhenModeSelectionBlocks() {
        MasteryDecision decision = MasteryDecision.allow(
                "item-2",
                "skill-1",
                MasteryState.PRACTICED,
                MasteryScore.correctnessOnly(0.8),
                VersionScope.empty(),
                "allowed"
        );

        MasteryAwareModeSelector.EnrichedModeSelectionResult blockedMode =
                new MasteryAwareModeSelector.EnrichedModeSelectionResult(
                        ExecutionStrategy.VERIFICATION_FIRST,
                        SupervisionMode.BLOCKED,
                        "blocked",
                        Map.of("traceId", "trace-2", "durationMs", 1L),
                        decision,
                        TaskClassification.of(TaskRiskLevel.HIGH, TaskNovelty.NOVEL),
                        VersionContext.empty()
                );

        when(modeSelector.selectModeWithDecision(any(), anyString(), anyString(), any(), anyString(), any()))
                .thenReturn(Promise.of(blockedMode));

        AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", ctxWithSkill));

        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DENIED);
        assertThat(result.getExplanation()).contains("blocked");
    }
}
