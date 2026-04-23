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
@ExtendWith(MockitoExtension.class) // GH-90000
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
    void setUp() { // GH-90000
        controller = new LifecycleApiController( // GH-90000
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
    void executeFullLifecycleSuccessPath() throws Exception { // GH-90000
        IntentSpec intentSpec = IntentSpec.builder() // GH-90000
            .id("intent-1")
            .productName("Order Service")
            .description("Build an order service")
            .goals(List.of()) // GH-90000
            .personas(List.of()) // GH-90000
            .constraints(List.of()) // GH-90000
            .metadata(Map.of()) // GH-90000
            .tenantId("tenant-1")
            .build(); // GH-90000
        ShapeSpec shapeSpec = ShapeSpec.builder() // GH-90000
            .id("shape-1")
            .intentRef(intentSpec.id()) // GH-90000
            .metadata(Map.of()) // GH-90000
            .tenantId("tenant-1")
            .build(); // GH-90000
        LifecycleValidationResult validationResult = LifecycleValidationResult.builder() // GH-90000
            .passed(true) // GH-90000
            .issues(List.of()) // GH-90000
            .build(); // GH-90000
        GeneratedArtifacts artifacts = GeneratedArtifacts.builder() // GH-90000
            .id("artifacts-1")
            .specRef(shapeSpec.id()) // GH-90000
            .artifacts(List.of()) // GH-90000
            .build(); // GH-90000
        RunResult runResult = RunResult.builder() // GH-90000
            .id("run-1")
            .runSpecRef("run-spec-1")
            .status(RunStatus.SUCCESS) // GH-90000
            .taskResults(List.of()) // GH-90000
            .startedAt(Instant.now()) // GH-90000
            .completedAt(Instant.now()) // GH-90000
            .metadata(Map.of()) // GH-90000
            .build(); // GH-90000
        Observation observation = Observation.builder() // GH-90000
            .id("obs-1")
            .runRef(runResult.runSpecRef()) // GH-90000
            .metrics(List.of()) // GH-90000
            .logs(List.of()) // GH-90000
            .traces(List.of()) // GH-90000
            .build(); // GH-90000
        Insights insights = Insights.builder() // GH-90000
            .id("insights-1")
            .observationRef(observation.id()) // GH-90000
            .patterns(List.of()) // GH-90000
            .anomalies(List.of()) // GH-90000
            .recommendations(List.of()) // GH-90000
            .build(); // GH-90000
        EvolutionPlan evolutionPlan = EvolutionPlan.builder() // GH-90000
            .id("evolution-1")
            .insightsRef(insights.id()) // GH-90000
            .tasks(List.of()) // GH-90000
            .metadata(Map.of()) // GH-90000
            .build(); // GH-90000

        when(intentService.capture(any())).thenReturn(Promise.of(intentSpec)); // GH-90000
        when(shapeService.derive(any())).thenReturn(Promise.of(shapeSpec)); // GH-90000
        when(validationService.validate(any())).thenReturn(Promise.of(validationResult)); // GH-90000
        when(generationService.generate(any())).thenReturn(Promise.of(artifacts)); // GH-90000
        when(runService.execute(any())).thenReturn(Promise.of(runResult)); // GH-90000
        when(observeService.collect(any())).thenReturn(Promise.of(observation)); // GH-90000
        when(learningService.analyze(any())).thenReturn(Promise.of(insights)); // GH-90000
        when(evolutionService.propose(any())).thenReturn(Promise.of(evolutionPlan)); // GH-90000

        String requestJson = JsonMapper.toJson(new LifecycleRequest(IntentInput.of("Build an order service"), "staging"));
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/yappc/lifecycle/execute")
            .withBody(ByteBuf.wrapForReading(requestJson.getBytes(StandardCharsets.UTF_8))) // GH-90000
            .build(); // GH-90000

        HttpResponse response = runPromise(() -> controller.executeFullLifecycle(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8); // GH-90000
        assertThat(responseJson).contains("\"pipelineMode\" : \"DAG\""); // GH-90000
        assertThat(responseJson).contains("\"status\" : \"SUCCESS\""); // GH-90000
        verify(evolutionService).propose(any()); // GH-90000
    }

    @Test
    @DisplayName("execute full lifecycle stops after validation when blocking issues exist")
    void executeFullLifecycleStopsOnValidationFailure() throws Exception { // GH-90000
        IntentSpec intentSpec = IntentSpec.builder() // GH-90000
            .id("intent-1")
            .productName("Order Service")
            .description("Build an order service")
            .goals(List.of()) // GH-90000
            .personas(List.of()) // GH-90000
            .constraints(List.of()) // GH-90000
            .metadata(Map.of()) // GH-90000
            .tenantId("tenant-1")
            .build(); // GH-90000
        ShapeSpec shapeSpec = ShapeSpec.builder() // GH-90000
            .id("shape-1")
            .intentRef(intentSpec.id()) // GH-90000
            .metadata(Map.of()) // GH-90000
            .tenantId("tenant-1")
            .build(); // GH-90000
        LifecycleValidationResult validationResult = LifecycleValidationResult.builder() // GH-90000
            .passed(false) // GH-90000
            .issues(List.of()) // GH-90000
            .build(); // GH-90000

        when(intentService.capture(any())).thenReturn(Promise.of(intentSpec)); // GH-90000
        when(shapeService.derive(any())).thenReturn(Promise.of(shapeSpec)); // GH-90000
        when(validationService.validate(any())).thenReturn(Promise.of(validationResult)); // GH-90000

        String requestJson = JsonMapper.toJson(new LifecycleRequest(IntentInput.of("Build an order service"), "staging"));
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/yappc/lifecycle/execute")
            .withBody(ByteBuf.wrapForReading(requestJson.getBytes(StandardCharsets.UTF_8))) // GH-90000
            .build(); // GH-90000

        HttpResponse response = runPromise(() -> controller.executeFullLifecycle(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8); // GH-90000
        assertThat(responseJson).contains("\"status\" : \"VALIDATION_FAILED\""); // GH-90000
        assertThat(responseJson).contains("\"pipelineMode\" : \"DAG\""); // GH-90000
        verify(generationService, never()).generate(any()); // GH-90000
        verify(runService, never()).execute(any()); // GH-90000
        verify(observeService, never()).collect(any()); // GH-90000
        verify(learningService, never()).analyze(any()); // GH-90000
        verify(evolutionService, never()).propose(any()); // GH-90000
    }

    private record LifecycleRequest(IntentInput intentInput, String environment) { // GH-90000
    }
}
