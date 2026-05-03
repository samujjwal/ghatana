package com.ghatana.yappc.api;

import com.ghatana.yappc.common.JsonMapper;
import com.ghatana.yappc.domain.generate.Artifact;
import com.ghatana.yappc.domain.generate.DiffResult;
import com.ghatana.yappc.domain.generate.GeneratedArtifacts;
import com.ghatana.yappc.domain.generate.ValidatedSpec;
import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.domain.validate.LifecycleValidationResult;
import com.ghatana.yappc.services.generate.GenerationService;
import com.ghatana.yappc.storage.ArtifactStore;
import com.ghatana.yappc.storage.YappcArtifactRepository;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
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
    private GenerationApiController controller;

    @BeforeEach
    void setUp() {
        generationService = new InMemoryGenerationService();
        artifactRepository = new InMemoryYappcArtifactRepository();
        controller = new GenerationApiController(generationService, artifactRepository);
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

        HttpResponse response = runPromise(() -> controller.generateArtifacts(request));

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(generationService.getGenerateCallCount()).isEqualTo(0);
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

        HttpResponse response = runPromise(() -> controller.regenerateWithDiff(request));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(generationService.getRegenerateWithDiffCallCount()).isEqualTo(1);
    }

    private record DiffEnvelope(ValidatedSpec validatedSpec, GeneratedArtifacts existingArtifacts) {
    }

    private static final class InMemoryGenerationService implements GenerationService {
        private int generateCallCount = 0;
        private int regenerateWithDiffCallCount = 0;
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

        @Override
        public Promise<GeneratedArtifacts> generate(ValidatedSpec spec) {
            generateCallCount++;
            return Promise.of(GeneratedArtifacts.builder().build());
        }

        @Override
        public Promise<DiffResult> regenerateWithDiff(ValidatedSpec spec, GeneratedArtifacts existing) {
            regenerateWithDiffCallCount++;
            return Promise.of(regenerateWithDiffResult);
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
