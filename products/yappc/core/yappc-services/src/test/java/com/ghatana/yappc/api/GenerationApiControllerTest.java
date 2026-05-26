package com.ghatana.yappc.api;

import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.yappc.common.JsonMapper;
import com.ghatana.yappc.domain.generate.Artifact;
import com.ghatana.yappc.domain.generate.DiffResult;
import com.ghatana.yappc.domain.generate.GeneratedArtifacts;
import com.ghatana.yappc.domain.generate.GenerationReviewRequest;
import com.ghatana.yappc.domain.generate.GenerationReviewResult;
import com.ghatana.yappc.domain.generate.ValidatedSpec;
import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.domain.validate.LifecycleValidationResult;
import com.ghatana.yappc.services.generate.GenerationService;
import com.ghatana.yappc.storage.ArtifactStore;
import com.ghatana.yappc.storage.YappcArtifactRepository;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verify generation API validation and diff request envelope handling
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("GenerationApiController")
class GenerationApiControllerTest extends EventloopTestBase {

    private InMemoryGenerationService generationService;
    private InMemoryYappcArtifactRepository artifactRepository;
    private RecordingAuditLogger auditLogger;
    private GenerationApiController controller;

    @BeforeEach
    void setUp() {
        generationService = new InMemoryGenerationService();
        artifactRepository = new InMemoryYappcArtifactRepository();
        auditLogger = new RecordingAuditLogger();
        controller = new GenerationApiController(generationService, artifactRepository, auditLogger);
    }

    @Test
    @DisplayName("generate rejects specs that have not passed validation")
    void generateRejectsUnvalidatedSpec() throws Exception {
        ValidatedSpec invalidSpec = ValidatedSpec.of(
            ShapeSpec.builder().id("shape-1").build(),
            LifecycleValidationResult.builder().passed(false).build()
        );

        HttpRequest request = HttpRequest.post("http://localhost/api/v1/yappc/generate")
            .withBody(ByteBuf.wrapForReading(JsonMapper.toJson(invalidSpec).getBytes(StandardCharsets.UTF_8)))
            .build();
        request.attach(Principal.class, new Principal("user-1", List.of("builder"), "tenant-1"));

        HttpResponse response = runPromise(() -> controller.generateArtifacts(request));

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(generationService.getGenerateCallCount()).isEqualTo(0);
        assertThat(auditLogger.events()).hasSize(1);
        assertThat(auditLogger.events().get(0))
            .containsEntry("type", "generation.generate.request")
            .containsEntry("outcome", "rejected")
            .containsEntry("projectId", "unknown");
        Map<?, ?> metadata = (Map<?, ?>) auditLogger.events().get(0).get("metadata");
        assertThat(metadata.get("route")).isEqualTo("generate");
        assertThat(metadata.get("reason")).isEqualTo("validatedSpec must pass validation before generation");
    }

    @Test
    @DisplayName("generate ProductUnitIntent returns validated Kernel handoff payload")
    void generateProductUnitIntentReturnsValidatedKernelPayload() throws Exception {
        Map<String, Object> handoffRequest = Map.of(
                "tenantId", "tenant-1",
                "workspaceId", "workspace-1",
                "projectId", "project-1",
                "projectName", "Project One",
                "surfaces", List.of("web", "backend-api"),
                "runtimeProvider", "ghatana-file-registry",
                "lifecycleProfile", "standard-web-api-product",
                "correlationId", "corr-1");

        HttpRequest request = HttpRequest.post("http://localhost/api/v1/yappc/generate/product-unit-intent")
                .withBody(ByteBuf.wrapForReading(JsonMapper.toJson(handoffRequest).getBytes(StandardCharsets.UTF_8)))
                .build();
        request.attach(Principal.class, new Principal("user-1", List.of("builder"), "tenant-1"));

        HttpResponse response = runPromise(() -> controller.generateProductUnitIntent(request));

        assertThat(response.getCode()).isEqualTo(200);
        String json = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(json).contains("\"valid\" : true");
        assertThat(json).contains("\"intentType\" : \"create\"");
        assertThat(json).contains("\"projectId\" : \"project-1\"");
        assertThat(json).contains("\"registryProvider\" : \"ghatana-file-registry\"");
        assertThat(auditLogger.events()).extracting(event -> event.get("type"))
                .contains("generation.product-unit-intent.request");
    }

    @Test
    @DisplayName("generate ProductUnitIntent rejects invalid Kernel contract values")
    void generateProductUnitIntentRejectsInvalidKernelPayload() throws Exception {
        Map<String, Object> handoffRequest = Map.of(
                "tenantId", "tenant-1",
                "workspaceId", "workspace-1",
                "projectId", "project-1",
                "projectName", "Project One",
                "surfaces", List.of("not-a-surface"));

        HttpRequest request = HttpRequest.post("http://localhost/api/v1/yappc/generate/product-unit-intent")
                .withBody(ByteBuf.wrapForReading(JsonMapper.toJson(handoffRequest).getBytes(StandardCharsets.UTF_8)))
                .build();
        request.attach(Principal.class, new Principal("user-1", List.of("builder"), "tenant-1"));

        HttpResponse response = runPromise(() -> controller.generateProductUnitIntent(request));

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getBody().asString(StandardCharsets.UTF_8)).contains("Unknown ProductUnit surface");
        assertThat(auditLogger.events()).extracting(event -> event.get("outcome"))
                .contains("rejected");
    }

    @Test
    @DisplayName("regenerate with diff requires validatedSpec and existingArtifacts envelope")
    void regenerateWithDiffUsesExplicitEnvelope() throws Exception {
        ValidatedSpec validSpec = ValidatedSpec.of(
            ShapeSpec.builder().id("shape-1").build(),
            LifecycleValidationResult.builder().passed(true).build()
        );
        GeneratedArtifacts existingArtifacts = GeneratedArtifacts.builder()
            .id("artifacts-1")
            .specRef("shape-1")
            .artifacts(List.of(Artifact.builder().id("artifact-1").name("README").type("doc").build()))
            .build();
        DiffResult diffResult = DiffResult.builder()
            .newArtifacts(existingArtifacts)
            .oldArtifacts(existingArtifacts)
            .diffs(List.of())
            .build();
        generationService.setRegenerateWithDiffResult(diffResult);

        String requestJson = JsonMapper.toJson(new DiffEnvelope(validSpec, existingArtifacts));
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/yappc/generate/diff")
            .withBody(ByteBuf.wrapForReading(requestJson.getBytes(StandardCharsets.UTF_8)))
            .build();
        request.attach(Principal.class, new Principal("user-1", List.of("builder"), "tenant-1"));

        HttpResponse response = runPromise(() -> controller.regenerateWithDiff(request));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(generationService.getRegenerateWithDiffCallCount()).isEqualTo(1);
        assertThat(auditLogger.events()).extracting(event -> event.get("type"))
            .contains("generation.diff.request");
        assertThat(auditLogger.events().get(auditLogger.events().size() - 1))
            .containsEntry("outcome", "succeeded")
            .containsEntry("projectId", "unknown");
    }

    @Test
    @DisplayName("generate request audit includes actor tenant workspace project and correlation metadata")
    void generateRequestAuditIncludesScopeAndCorrelation() throws Exception {
        ValidatedSpec validSpec = ValidatedSpec.of(
            ShapeSpec.builder().id("shape-1").build(),
            LifecycleValidationResult.builder().passed(true).build()
        );

        HttpRequest request = HttpRequest.post("http://localhost/api/v1/yappc/generate")
            .withHeader(HttpHeaders.of("X-Workspace-Id"), "workspace-1")
            .withHeader(HttpHeaders.of("X-Project-Id"), "project-1")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "corr-1")
            .withBody(ByteBuf.wrapForReading(JsonMapper.toJson(validSpec).getBytes(StandardCharsets.UTF_8)))
            .build();
        request.attach(Principal.class, new Principal("user-1", List.of("builder"), "tenant-1"));

        HttpResponse response = runPromise(() -> controller.generateArtifacts(request));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(auditLogger.events()).hasSize(1);
        assertThat(auditLogger.events().get(0))
            .containsEntry("type", "generation.generate.request")
            .containsEntry("outcome", "succeeded")
            .containsEntry("actor", "user-1")
            .containsEntry("tenantId", "tenant-1")
            .containsEntry("workspaceId", "workspace-1")
            .containsEntry("projectId", "project-1")
            .containsEntry("correlationId", "corr-1");
        Map<?, ?> metadata = (Map<?, ?>) auditLogger.events().get(0).get("metadata");
        assertThat(metadata.get("route")).isEqualTo("generate");
        assertThat(metadata.get("specId")).isEqualTo("shape-1");
        assertThat(metadata.get("artifactCount")).isEqualTo(0);
    }

    @Test
    @DisplayName("generation review decisions require actor scope and record apply reject rollback decisions")
    void reviewDecisionsAreExplicitAndAudited() throws Exception {
        String body = JsonMapper.toJson(new ReviewDecisionBody("proj-42", "user-1", "reviewed in cockpit"));

        HttpResponse applyResponse = serveReviewDecision("/api/v1/yappc/generate/runs/run-1/apply", body);
        HttpResponse rejectResponse = serveReviewDecision("/api/v1/yappc/generate/runs/run-2/reject", body);
        HttpResponse rollbackResponse = serveReviewDecision("/api/v1/yappc/generate/runs/run-3/rollback", body);

        assertThat(applyResponse.getCode()).isEqualTo(200);
        assertThat(rejectResponse.getCode()).isEqualTo(200);
        assertThat(rollbackResponse.getCode()).isEqualTo(200);
        assertThat(generationService.getReviewDecisionCallCount()).isEqualTo(3);
        assertThat(generationService.getReviewActions()).containsExactly("apply", "reject", "rollback");
        assertThat(applyResponse.getBody().asString(StandardCharsets.UTF_8)).contains("apply");
    }

    @Test
    @DisplayName("generation review decisions derive actor from authenticated principal")
    void reviewDecisionUsesAuthenticatedActorWhenBodyActorMissing() throws Exception {
        String body = JsonMapper.toJson(new ReviewDecisionBody("proj-42", "", "missing actor"));

        HttpResponse response = serveReviewDecision("/api/v1/yappc/generate/runs/run-1/apply", body);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(generationService.getReviewDecisionCallCount()).isEqualTo(1);
        assertThat(auditLogger.events()).hasSize(1);
        assertThat(auditLogger.events().get(0))
            .containsEntry("type", "generation.review.request")
            .containsEntry("outcome", "succeeded")
            .containsEntry("runId", "run-1");
        Map<?, ?> metadata = (Map<?, ?>) auditLogger.events().get(0).get("metadata");
        assertThat(metadata.get("route")).isEqualTo("generate-review");
        assertThat(metadata.get("action")).isEqualTo("apply");
        assertThat(metadata.get("actorId")).isEqualTo("user-1");
    }

    private HttpResponse serveReviewDecision(String path, String body) {
        AsyncServlet servlet = RoutingServlet.builder(eventloop())
            .with(HttpMethod.POST, "/api/v1/yappc/generate/runs/:runId/apply", controller::applyReviewDecision)
            .with(HttpMethod.POST, "/api/v1/yappc/generate/runs/:runId/reject", controller::rejectReviewDecision)
            .with(HttpMethod.POST, "/api/v1/yappc/generate/runs/:runId/rollback", controller::rollbackReviewDecision)
            .build();
        HttpRequest request = HttpRequest.post("http://localhost" + path)
            .withBody(ByteBuf.wrapForReading(body.getBytes(StandardCharsets.UTF_8)))
            .build();
        request.attach(Principal.class, new Principal("user-1", List.of("builder"), "tenant-1"));
        return runPromise(() -> servlet.serve(request));
    }

    private record DiffEnvelope(ValidatedSpec validatedSpec, GeneratedArtifacts existingArtifacts) {
    }

    private record ReviewDecisionBody(String projectId, String actorId, String reason) {
    }

    private static final class RecordingAuditLogger implements AuditLogger {
        private final List<Map<String, Object>> events = new ArrayList<>();

        @Override
        public Promise<Void> log(Map<String, Object> event) {
            events.add(event);
            return Promise.complete();
        }

        List<Map<String, Object>> events() {
            return events;
        }
    }

    private static final class InMemoryGenerationService implements GenerationService {
        private int generateCallCount = 0;
        private int regenerateWithDiffCallCount = 0;
        private int reviewDecisionCallCount = 0;
        private final java.util.ArrayList<String> reviewActions = new java.util.ArrayList<>();
        private DiffResult regenerateWithDiffResult = null;

        void setRegenerateWithDiffResult(DiffResult result) {
            this.regenerateWithDiffResult = result;
        }

        int getGenerateCallCount() {
            return generateCallCount;
        }

        int getRegenerateWithDiffCallCount() {
            return regenerateWithDiffCallCount;
        }

        int getReviewDecisionCallCount() {
            return reviewDecisionCallCount;
        }

        List<String> getReviewActions() {
            return reviewActions;
        }

        @Override
        public Promise<GeneratedArtifacts> generate(ValidatedSpec spec, com.ghatana.yappc.domain.generate.GenerationContext context) {
            generateCallCount++;
            return Promise.of(GeneratedArtifacts.builder().build());
        }

        @Override
        public Promise<DiffResult> regenerateWithDiff(ValidatedSpec spec, GeneratedArtifacts existing, com.ghatana.yappc.domain.generate.GenerationContext context) {
            regenerateWithDiffCallCount++;
            return Promise.of(regenerateWithDiffResult);
        }

        @Override
        public Promise<GenerationReviewResult> reviewDecision(GenerationReviewRequest request) {
            reviewDecisionCallCount++;
            reviewActions.add(request.action().wireValue());
            return Promise.of(new GenerationReviewResult(
                request.runId(),
                request.projectId(),
                request.action().wireValue(),
                request.action().status(),
                false,
                request.actorId(),
                java.time.Instant.parse("2026-04-21T11:10:00Z"),
                "generate.review." + request.action().wireValue(),
                "reviewed",
                Map.of("reason", request.reason())
            ));
        }
    }

    private static final class InMemoryYappcArtifactRepository extends YappcArtifactRepository {
        public InMemoryYappcArtifactRepository() {
            super(new InMemoryArtifactStore());
        }

        public Promise<GeneratedArtifacts> findById(String id) {
            return Promise.of(null);
        }

        public Promise<Void> save(GeneratedArtifacts artifacts) {
            return Promise.complete();
        }

        public Promise<List<GeneratedArtifacts>> findBySpecRef(String specRef) {
            return Promise.of(List.of());
        }
    }

    private static final class InMemoryArtifactStore implements ArtifactStore {
        public Promise<Void> store(String id, byte[] data) {
            return Promise.complete();
        }

        public Promise<byte[]> retrieve(String id) {
            return Promise.of(new byte[0]);
        }

        @Override
        public Promise<Void> delete(String id) {
            return Promise.complete();
        }

        @Override
        public Promise<Map<String, String>> getMetadata(String path) {
            return Promise.of(Map.of());
        }

        @Override
        public Promise<Void> putMetadata(String path, Map<String, String> metadata) {
            return Promise.complete();
        }

        @Override
        public Promise<List<String>> list(String path) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<byte[]> get(String id) {
            return Promise.of(new byte[0]);
        }

        @Override
        public Promise<String> put(String id, byte[] data) {
            return Promise.of("version-1");
        }
    }
}
