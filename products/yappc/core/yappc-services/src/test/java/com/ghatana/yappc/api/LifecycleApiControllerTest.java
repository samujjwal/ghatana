package com.ghatana.yappc.api;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.common.JsonMapper;
import com.ghatana.yappc.domain.evolve.EvolutionPlan;
import com.ghatana.yappc.domain.generate.DiffResult;
import com.ghatana.yappc.domain.generate.GeneratedArtifacts;
import com.ghatana.yappc.domain.generate.ValidatedSpec;
import com.ghatana.yappc.domain.intent.ConstraintSpec;
import com.ghatana.yappc.domain.intent.IntentInput;
import com.ghatana.yappc.domain.intent.IntentSpec;
import com.ghatana.yappc.domain.learn.HistoricalContext;
import com.ghatana.yappc.domain.learn.Insights;
import com.ghatana.yappc.domain.observe.Observation;
import com.ghatana.yappc.domain.run.ObservationConfig;
import com.ghatana.yappc.domain.run.RunResult;
import com.ghatana.yappc.domain.run.RunSpec;
import com.ghatana.yappc.domain.run.RunStatus;
import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.domain.shape.SystemModel;
import com.ghatana.yappc.domain.validate.LifecycleValidationResult;
import com.ghatana.yappc.domain.validate.PolicySpec;
import com.ghatana.yappc.domain.validate.ValidationConfig;
import com.ghatana.yappc.services.evolve.EvolutionService;
import com.ghatana.yappc.services.generate.GenerationService;
import com.ghatana.yappc.services.intent.IntentService;
import com.ghatana.yappc.services.learn.LearningService;
import com.ghatana.yappc.services.observe.ObserveService;
import com.ghatana.yappc.services.run.RunService;
import com.ghatana.yappc.services.shape.ShapeService;
import com.ghatana.yappc.services.validate.ValidationService;
import io.activej.bytebuf.ByteBuf;
import io.activej.dns.DnsClient;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpClient;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Tests full lifecycle API orchestration and validation-gate behavior
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("LifecycleApiController")
class LifecycleApiControllerTest extends EventloopTestBase {

    private InMemoryIntentService intentService;
    private InMemoryShapeService shapeService;
    private InMemoryValidationService validationService;
    private InMemoryGenerationService generationService;
    private InMemoryRunService runService;
    private InMemoryObserveService observeService;
    private InMemoryLearningService learningService;
    private InMemoryEvolutionService evolutionService;
    private Eventloop eventloop;
    private HttpClient httpClient;
    private LifecycleApiController controller;

    @BeforeEach
    void setUp() {
        intentService = new InMemoryIntentService();
        shapeService = new InMemoryShapeService();
        validationService = new InMemoryValidationService();
        generationService = new InMemoryGenerationService();
        runService = new InMemoryRunService();
        observeService = new InMemoryObserveService();
        learningService = new InMemoryLearningService();
        evolutionService = new InMemoryEvolutionService();
        eventloop = Eventloop.create();
        httpClient = HttpClient.create(eventloop,
            DnsClient.builder(eventloop, java.net.InetAddress.getLoopbackAddress()).build());
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

        intentService.setCaptureResult(intentSpec);
        shapeService.setDeriveResult(shapeSpec);
        validationService.setValidateResult(validationResult);
        generationService.setGenerateResult(artifacts);
        runService.setExecuteResult(runResult);
        observeService.setCollectResult(observation);
        learningService.setAnalyzeResult(insights);
        evolutionService.setProposeResult(evolutionPlan);

        String requestJson = JsonMapper.toJson(new LifecycleRequest(IntentInput.of("Build an order service"), "staging"));
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/yappc/lifecycle/execute")
            .withBody(ByteBuf.wrapForReading(requestJson.getBytes(StandardCharsets.UTF_8)))
            .build();

        HttpResponse response = runPromise(() -> controller.executeFullLifecycle(request));

        assertThat(response.getCode()).isEqualTo(200);
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(responseJson).contains("\"pipelineMode\" : \"DAG\"");
        assertThat(responseJson).contains("\"status\" : \"SUCCESS\"");
        assertThat(evolutionService.getProposeCallCount()).isEqualTo(1);
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

        intentService.setCaptureResult(intentSpec);
        shapeService.setDeriveResult(shapeSpec);
        validationService.setValidateResult(validationResult);

        String requestJson = JsonMapper.toJson(new LifecycleRequest(IntentInput.of("Build an order service"), "staging"));
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/yappc/lifecycle/execute")
            .withBody(ByteBuf.wrapForReading(requestJson.getBytes(StandardCharsets.UTF_8)))
            .build();

        HttpResponse response = runPromise(() -> controller.executeFullLifecycle(request));

        assertThat(response.getCode()).isEqualTo(200);
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(responseJson).contains("\"status\" : \"VALIDATION_FAILED\"");
        assertThat(responseJson).contains("\"pipelineMode\" : \"DAG\"");
        assertThat(generationService.getGenerateCallCount()).isEqualTo(0);
        assertThat(runService.getExecuteCallCount()).isEqualTo(0);
        assertThat(observeService.getCollectCallCount()).isEqualTo(0);
        assertThat(learningService.getAnalyzeCallCount()).isEqualTo(0);
        assertThat(evolutionService.getProposeCallCount()).isEqualTo(0);
    }

    private record LifecycleRequest(IntentInput intentInput, String environment) {
    }

    private static final class InMemoryIntentService implements IntentService {
        private IntentSpec captureResult = null;
        private com.ghatana.yappc.domain.intent.IntentAnalysis analyzeResult = null;
        private int captureCallCount = 0;
        private int analyzeCallCount = 0;

        void setCaptureResult(IntentSpec result) {
            this.captureResult = result;
        }

        void setAnalyzeResult(com.ghatana.yappc.domain.intent.IntentAnalysis result) {
            this.analyzeResult = result;
        }

        int getCaptureCallCount() {
            return captureCallCount;
        }

        int getAnalyzeCallCount() {
            return analyzeCallCount;
        }

        @Override
        public Promise<IntentSpec> capture(IntentInput input) {
            captureCallCount++;
            return Promise.of(captureResult);
        }

        @Override
        public Promise<com.ghatana.yappc.domain.intent.IntentAnalysis> analyze(IntentSpec spec) {
            analyzeCallCount++;
            return Promise.of(analyzeResult);
        }
    }

    private static final class InMemoryShapeService implements ShapeService {
        private ShapeSpec deriveResult = null;
        private int deriveCallCount = 0;

        void setDeriveResult(ShapeSpec result) {
            this.deriveResult = result;
        }

        int getDeriveCallCount() {
            return deriveCallCount;
        }

        @Override
        public Promise<ShapeSpec> derive(IntentSpec intentSpec) {
            deriveCallCount++;
            return Promise.of(deriveResult);
        }

        @Override
        public Promise<SystemModel> generateModel(ShapeSpec spec) {
            return Promise.of(new SystemModel(spec, Map.of(), Map.of()));
        }
    }

    private static final class InMemoryValidationService implements ValidationService {
        private LifecycleValidationResult validateResult = null;
        private int validateCallCount = 0;

        void setValidateResult(LifecycleValidationResult result) {
            this.validateResult = result;
        }

        int getValidateCallCount() {
            return validateCallCount;
        }

        @Override
        public Promise<LifecycleValidationResult> validate(ShapeSpec spec) {
            validateCallCount++;
            return Promise.of(validateResult);
        }

        @Override
        public Promise<LifecycleValidationResult> validate(ShapeSpec spec, ValidationConfig config) {
            validateCallCount++;
            return Promise.of(validateResult);
        }

        @Override
        public Promise<LifecycleValidationResult> validateWithPolicy(ShapeSpec spec, PolicySpec policy) {
            validateCallCount++;
            return Promise.of(validateResult);
        }
    }

    private static final class InMemoryGenerationService implements GenerationService {
        private GeneratedArtifacts generateResult = null;
        private int generateCallCount = 0;
        private DiffResult regenerateWithDiffResult = null;
        private int regenerateWithDiffCallCount = 0;

        void setGenerateResult(GeneratedArtifacts result) {
            this.generateResult = result;
        }

        void setRegenerateWithDiffResult(DiffResult result) {
            this.regenerateWithDiffResult = result;
        }

        int getGenerateCallCount() {
            return generateCallCount;
        }

        int getRegenerateWithDiffCallCount() {
            return regenerateWithDiffCallCount;
        }

        @Override
        public Promise<GeneratedArtifacts> generate(ValidatedSpec spec) {
            generateCallCount++;
            return Promise.of(generateResult);
        }

        @Override
        public Promise<DiffResult> regenerateWithDiff(ValidatedSpec spec, GeneratedArtifacts existing) {
            regenerateWithDiffCallCount++;
            return Promise.of(regenerateWithDiffResult);
        }
    }

    private static final class InMemoryRunService implements RunService {
        private RunResult executeResult = null;
        private int executeCallCount = 0;

        void setExecuteResult(RunResult result) {
            this.executeResult = result;
        }

        int getExecuteCallCount() {
            return executeCallCount;
        }

        public Promise<RunResult> execute(Object runSpec) {
            executeCallCount++;
            return Promise.of(executeResult);
        }

        @Override
        public Promise<RunResult> promote(String runId, String environment) {
            return Promise.of(executeResult);
        }

        @Override
        public Promise<RunResult> execute(RunSpec spec) {
            return null;
        }

        @Override
        public Promise<RunResult> executeWithObservation(RunSpec spec, ObservationConfig config) {
            return null;
        }

        @Override
        public Promise<RunResult> rollback(String deploymentId, String targetVersion) {
            return Promise.of(executeResult);
        }
    }

    private static final class InMemoryObserveService implements ObserveService {
        private Observation collectResult = null;
        private int collectCallCount = 0;

        void setCollectResult(Observation result) {
            this.collectResult = result;
        }

        int getCollectCallCount() {
            return collectCallCount;
        }

        @Override
        public Promise<Observation> collect(RunResult run) {
            collectCallCount++;
            return Promise.of(collectResult);
        }

        @Override
        public Promise<Void> streamObservations(RunResult run, java.util.function.Consumer<Observation> callback) {
            return Promise.complete();
        }
    }

    private static final class InMemoryLearningService implements LearningService {
        private Insights analyzeResult = null;
        private int analyzeCallCount = 0;

        void setAnalyzeResult(Insights result) {
            this.analyzeResult = result;
        }

        int getAnalyzeCallCount() {
            return analyzeCallCount;
        }

        @Override
        public Promise<Insights> analyze(Observation observation) {
            analyzeCallCount++;
            return Promise.of(analyzeResult);
        }

        @Override
        public Promise<Insights> analyzeWithContext(Observation observation, HistoricalContext context) {
            analyzeCallCount++;
            return Promise.of(analyzeResult);
        }
    }

    private static final class InMemoryEvolutionService implements EvolutionService {
        private EvolutionPlan proposeResult = null;
        private int proposeCallCount = 0;

        void setProposeResult(EvolutionPlan result) {
            this.proposeResult = result;
        }

        int getProposeCallCount() {
            return proposeCallCount;
        }

        @Override
        public Promise<EvolutionPlan> propose(Insights insights) {
            proposeCallCount++;
            return Promise.of(proposeResult);
        }

        @Override
        public Promise<EvolutionPlan> proposeWithConstraints(Insights insights, ConstraintSpec constraints) {
            proposeCallCount++;
            return Promise.of(proposeResult);
        }
    }
}
