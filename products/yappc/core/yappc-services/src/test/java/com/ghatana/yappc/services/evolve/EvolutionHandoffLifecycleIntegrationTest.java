package com.ghatana.yappc.services.evolve;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.api.LifecycleApiController;
import com.ghatana.yappc.api.LifecycleExecutionRepository;
import com.ghatana.yappc.domain.evolve.EvolutionPlan;
import com.ghatana.yappc.domain.generate.GeneratedArtifacts;
import com.ghatana.yappc.domain.intent.IntentSpec;
import com.ghatana.yappc.domain.learn.Insights;
import com.ghatana.yappc.domain.observe.Observation;
import com.ghatana.yappc.domain.run.RunResult;
import com.ghatana.yappc.domain.run.RunStatus;
import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.domain.validate.LifecycleValidationResult;
import com.ghatana.yappc.services.generate.GenerationService;
import com.ghatana.yappc.services.intent.IntentService;
import com.ghatana.yappc.services.learn.LearningService;
import com.ghatana.yappc.services.observe.ObserveService;
import com.ghatana.yappc.services.run.RunService;
import com.ghatana.yappc.services.shape.ShapeService;
import com.ghatana.yappc.services.validate.ValidationService;
import io.activej.dns.DnsClient;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpClient;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvolutionHandoffLifecycleIntegrationTest extends EventloopTestBase {

    @Test
    void shouldDispatchQueuedHandoffThroughLifecycleAndPersistExecution() {
        DataCloudClient dataCloudClient = mock(DataCloudClient.class);
        IntentService intentService = mock(IntentService.class);
        ShapeService shapeService = mock(ShapeService.class);
        ValidationService validationService = mock(ValidationService.class);
        GenerationService generationService = mock(GenerationService.class);
        RunService runService = mock(RunService.class);
        ObserveService observeService = mock(ObserveService.class);
        LearningService learningService = mock(LearningService.class);
        EvolutionService evolutionService = mock(EvolutionService.class);

        InMemoryLifecycleExecutionRepository executionRepository = new InMemoryLifecycleExecutionRepository();
        Eventloop eventloop = Eventloop.create();
        HttpClient httpClient = HttpClient.create(eventloop,
                DnsClient.builder(eventloop, java.net.InetAddress.getLoopbackAddress()).build());

        IntentSpec intentSpec = IntentSpec.builder()
                .id("intent-1")
                .productName("Integration Product")
                .description("Integration lifecycle")
                .goals(List.of())
                .personas(List.of())
                .constraints(List.of())
                .metadata(Map.of())
                .tenantId("tenant-1")
                .build();
        ShapeSpec shapeSpec = ShapeSpec.builder()
                .id("shape-1")
                .intentRef(intentSpec.id())
                .metadata(Map.of())
                .tenantId("tenant-1")
                .build();
        LifecycleValidationResult validation = LifecycleValidationResult.builder()
                .passed(true)
                .issues(List.of())
                .build();
        GeneratedArtifacts artifacts = GeneratedArtifacts.builder()
                .id("artifacts-1")
                .specRef(shapeSpec.id())
                .artifacts(List.of())
                .build();
        RunResult runResult = RunResult.builder()
                .id("run-1")
                .runSpecRef("run-spec-1")
                .status(RunStatus.SUCCESS)
                .taskResults(List.of())
                .startedAt(Instant.now())
                .completedAt(Instant.now())
                .metadata(Map.of())
                .build();
        Observation observation = Observation.builder()
                .id("obs-1")
                .runRef(runResult.runSpecRef())
                .metrics(List.of())
                .logs(List.of())
                .traces(List.of())
                .build();
        Insights insights = Insights.builder()
                .id("insights-1")
                .observationRef(observation.id())
                .patterns(List.of())
                .anomalies(List.of())
                .recommendations(List.of())
                .build();
        EvolutionPlan evolutionPlan = EvolutionPlan.builder()
                .id("evolution-1")
                .insightsRef(insights.id())
                .tasks(List.of())
                .metadata(Map.of())
                .build();

        when(intentService.capture(any())).thenReturn(Promise.of(intentSpec));
        when(shapeService.derive(any())).thenReturn(Promise.of(shapeSpec));
        when(validationService.validate(any())).thenReturn(Promise.of(validation));
        when(generationService.generate(any(), any())).thenReturn(Promise.of(artifacts));
        when(runService.execute(any())).thenReturn(Promise.of(runResult));
        when(observeService.collect(any())).thenReturn(Promise.of(observation));
        when(learningService.analyze(any())).thenReturn(Promise.of(insights));
        when(evolutionService.propose(any())).thenReturn(Promise.of(evolutionPlan));

        LifecycleApiController lifecycleApiController = new LifecycleApiController(
                intentService,
                shapeService,
                validationService,
                generationService,
                runService,
                observeService,
                learningService,
                evolutionService,
                eventloop,
                httpClient,
                executionRepository
        );

        LifecycleApiExecutionDispatcher lifecycleDispatcher =
                new LifecycleApiExecutionDispatcher(lifecycleApiController, executionRepository, new ObjectMapper());
        EvolutionExecutionHandoffDispatcher handoffDispatcher =
                new EvolutionExecutionHandoffDispatcher(dataCloudClient, lifecycleDispatcher);

        Map<String, Object> queued = Map.ofEntries(
                Map.entry("id", "handoff-1"),
                Map.entry("handoffId", "handoff-1"),
                Map.entry("proposalId", "proposal-1"),
                Map.entry("tenantId", "tenant-1"),
                Map.entry("projectId", "project-1"),
                Map.entry("productUnitIntentRef", "intent-ref-1"),
                Map.entry("requestedBy", "system-reviewer"),
                Map.entry("phases", List.of("validate", "generate", "run")),
                Map.entry("status", "QUEUED"),
                Map.entry("requestedAt", Instant.now().toString()),
                Map.entry("metadata", Map.of(
                        "workspaceId", "workspace-1",
                        "environment", "staging",
                        "intentText", "Run evolved lifecycle for project-1"
                ))
        );

        when(dataCloudClient.query(eq("tenant-1"), eq("yappc_evolution_execution_handoffs"), any(DataCloudClient.Query.class)))
                .thenReturn(Promise.of(List.of(DataCloudClient.Entity.of("handoff-1", "yappc_evolution_execution_handoffs", queued))));
        when(dataCloudClient.save(eq("tenant-1"), eq("yappc_evolution_execution_handoffs"), anyMap()))
                .thenReturn(Promise.of(DataCloudClient.Entity.of("handoff-1", "yappc_evolution_execution_handoffs", queued)));

        EvolutionExecutionHandoffDispatcher.DispatchSummary summary =
                runPromise(() -> handoffDispatcher.dispatchQueued("tenant-1", 10));

        assertEquals(1, summary.queued());
        assertEquals(1, summary.dispatched());
        assertEquals(0, summary.failed());
        assertFalse(executionRepository.executions.isEmpty());
        assertEquals("project-1", executionRepository.executions.getFirst().projectId());

        verify(dataCloudClient).save(
                eq("tenant-1"),
                eq("yappc_evolution_execution_handoffs"),
                argThat(doc -> "DISPATCHED".equals(doc.get("status"))
                        && doc.containsKey("executionId")
                        && doc.get("executionId") instanceof String id
                        && !id.isBlank()));
    }

    private static final class InMemoryLifecycleExecutionRepository implements LifecycleExecutionRepository {
        private final List<LifecycleExecution> executions = new ArrayList<>();

        @Override
        public Promise<Void> persist(LifecycleExecution execution) {
            executions.add(execution);
            return Promise.complete();
        }

        @Override
        public Promise<LifecycleExecution> findById(String executionId) {
            return Promise.of(executions.stream()
                    .filter(execution -> execution.executionId().equals(executionId))
                    .findFirst()
                    .orElse(null));
        }

        @Override
        public Promise<List<LifecycleExecution>> findByProject(String tenantId, String projectId, int limit) {
            List<LifecycleExecution> filtered = executions.stream()
                    .filter(execution -> execution.tenantId().equals(tenantId))
                    .filter(execution -> execution.projectId().equals(projectId))
                    .limit(limit)
                    .toList();
            return Promise.of(filtered);
        }
    }
}
