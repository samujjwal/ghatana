package com.ghatana.yappc.api;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.common.JsonMapper;
import com.ghatana.yappc.domain.evolve.EvolutionPlan;
import com.ghatana.yappc.domain.generate.GeneratedArtifacts;
import com.ghatana.yappc.domain.intent.IntentInput;
import com.ghatana.yappc.domain.intent.IntentSpec;
import com.ghatana.yappc.domain.learn.Insights;
import com.ghatana.yappc.domain.observe.Observation;
import com.ghatana.yappc.domain.run.RunResult;
import com.ghatana.yappc.domain.run.RunStatus;
import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.domain.validate.LifecycleValidationResult;
import com.ghatana.yappc.services.evolve.EvolutionService;
import com.ghatana.yappc.services.generate.GenerationService;
import com.ghatana.yappc.services.intent.IntentService;
import com.ghatana.yappc.services.learn.LearningService;
import com.ghatana.yappc.services.observe.ObserveService;
import com.ghatana.yappc.services.run.RunService;
import com.ghatana.yappc.services.shape.ShapeService;
import com.ghatana.yappc.services.validate.ValidationService;
import io.activej.bytebuf.ByteBuf;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpClient;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Tests full lifecycle API orchestration and validation-gate behavior
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("LifecycleApiController")
@ExtendWith(MockitoExtension.class)
class LifecycleApiControllerTest extends EventloopTestBase {

    @Mock
    private IntentService intentService;

    @Mock
    private ShapeService shapeService;

    @Mock
    private ValidationService validationService;

    @Mock
    private GenerationService generationService;

    @Mock
    private RunService runService;

    @Mock
    private ObserveService observeService;

    @Mock
    private LearningService learningService;

    @Mock
    private EvolutionService evolutionService;

    @Mock
    private Eventloop eventloop;

    @Mock
    private HttpClient httpClient;

    private LifecycleApiController controller;

    @BeforeEach
    void setUp() {
        controller = new LifecycleApiController(
            intentService,
            shapeService,
            validationService,
            generationService,
            runService,
            observeService,
            learningService,
            evolutionService,
            eventloop,
            httpClient
        );
    }

    @Test
    @DisplayName("execute full lifecycle returns all phase outputs when validation passes")
    void executeFullLifecycleSuccessPath() throws Exception {
        IntentSpec intentSpec = IntentSpec.builder()
            .id("intent-1")
            .productName("Order Service")
            .description("Build an order service")
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
        LifecycleValidationResult validationResult = LifecycleValidationResult.builder()
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
        when(validationService.validate(any())).thenReturn(Promise.of(validationResult));
        when(generationService.generate(any())).thenReturn(Promise.of(artifacts));
        when(runService.execute(any())).thenReturn(Promise.of(runResult));
        when(observeService.collect(any())).thenReturn(Promise.of(observation));
        when(learningService.analyze(any())).thenReturn(Promise.of(insights));
        when(evolutionService.propose(any())).thenReturn(Promise.of(evolutionPlan));

        String requestJson = JsonMapper.toJson(new LifecycleRequest(IntentInput.of("Build an order service"), "staging"));
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/yappc/lifecycle/execute")
            .withBody(ByteBuf.wrapForReading(requestJson.getBytes(StandardCharsets.UTF_8)))
            .build();

        HttpResponse response = runPromise(() -> controller.executeFullLifecycle(request));

        assertThat(response.getCode()).isEqualTo(200);
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(responseJson).contains("\"pipelineMode\" : \"DAG\"");
        assertThat(responseJson).contains("\"status\" : \"SUCCESS\"");
        verify(evolutionService).propose(any());
    }

    @Test
    @DisplayName("execute full lifecycle stops after validation when blocking issues exist")
    void executeFullLifecycleStopsOnValidationFailure() throws Exception {
        IntentSpec intentSpec = IntentSpec.builder()
            .id("intent-1")
            .productName("Order Service")
            .description("Build an order service")
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
        LifecycleValidationResult validationResult = LifecycleValidationResult.builder()
            .passed(false)
            .issues(List.of())
            .build();

        when(intentService.capture(any())).thenReturn(Promise.of(intentSpec));
        when(shapeService.derive(any())).thenReturn(Promise.of(shapeSpec));
        when(validationService.validate(any())).thenReturn(Promise.of(validationResult));

        String requestJson = JsonMapper.toJson(new LifecycleRequest(IntentInput.of("Build an order service"), "staging"));
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/yappc/lifecycle/execute")
            .withBody(ByteBuf.wrapForReading(requestJson.getBytes(StandardCharsets.UTF_8)))
            .build();

        HttpResponse response = runPromise(() -> controller.executeFullLifecycle(request));

        assertThat(response.getCode()).isEqualTo(200);
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(responseJson).contains("\"status\" : \"VALIDATION_FAILED\"");
        assertThat(responseJson).contains("\"pipelineMode\" : \"DAG\"");
        verify(generationService, never()).generate(any());
        verify(runService, never()).execute(any());
        verify(observeService, never()).collect(any());
        verify(learningService, never()).analyze(any());
        verify(evolutionService, never()).propose(any());
    }

    private record LifecycleRequest(IntentInput intentInput, String environment) {
    }
}
