package com.ghatana.yappc.api;

import com.ghatana.yappc.common.JsonMapper;
import com.ghatana.yappc.domain.generate.Artifact;
import com.ghatana.yappc.domain.generate.DiffResult;
import com.ghatana.yappc.domain.generate.GeneratedArtifacts;
import com.ghatana.yappc.domain.generate.ValidatedSpec;
import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.domain.validate.LifecycleValidationResult;
import com.ghatana.yappc.services.generate.GenerationService;
import com.ghatana.yappc.storage.YappcArtifactRepository;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBuf;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verify generation API validation and diff request envelope handling
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("GenerationApiController")
@ExtendWith(MockitoExtension.class) // GH-90000
class GenerationApiControllerTest extends EventloopTestBase {

    @Mock
    private GenerationService generationService;

    @Mock
    private YappcArtifactRepository artifactRepository;

    private GenerationApiController controller;

    @BeforeEach
    void setUp() { // GH-90000
        controller = new GenerationApiController(generationService, artifactRepository); // GH-90000
    }

    @Test
    @DisplayName("generate rejects specs that have not passed validation")
    void generateRejectsUnvalidatedSpec() throws Exception { // GH-90000
        ValidatedSpec invalidSpec = ValidatedSpec.of( // GH-90000
            ShapeSpec.builder().id("shape-1").build(),
            LifecycleValidationResult.builder().passed(false).build() // GH-90000
        );

        HttpRequest request = HttpRequest.post("http://localhost/api/v1/yappc/generate")
            .withBody(ByteBuf.wrapForReading(JsonMapper.toJson(invalidSpec).getBytes(StandardCharsets.UTF_8))) // GH-90000
            .build(); // GH-90000

        HttpResponse response = runPromise(() -> controller.generateArtifacts(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(400); // GH-90000
        verify(generationService, never()).generate(any()); // GH-90000
    }

    @Test
    @DisplayName("regenerate with diff requires validatedSpec and existingArtifacts envelope")
    void regenerateWithDiffUsesExplicitEnvelope() throws Exception { // GH-90000
        ValidatedSpec validSpec = ValidatedSpec.of( // GH-90000
            ShapeSpec.builder().id("shape-1").build(),
            LifecycleValidationResult.builder().passed(true).build() // GH-90000
        );
        GeneratedArtifacts existingArtifacts = GeneratedArtifacts.builder() // GH-90000
            .id("artifacts-1")
            .specRef("shape-1")
            .artifacts(List.of(Artifact.builder().id("artifact-1").name("README").type("doc").build()))
            .build(); // GH-90000
        DiffResult diffResult = DiffResult.builder() // GH-90000
            .newArtifacts(existingArtifacts) // GH-90000
            .oldArtifacts(existingArtifacts) // GH-90000
            .diffs(List.of()) // GH-90000
            .build(); // GH-90000
        when(generationService.regenerateWithDiff(any(), any())).thenReturn(Promise.of(diffResult)); // GH-90000

        String requestJson = JsonMapper.toJson(new DiffEnvelope(validSpec, existingArtifacts)); // GH-90000
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/yappc/generate/diff")
            .withBody(ByteBuf.wrapForReading(requestJson.getBytes(StandardCharsets.UTF_8))) // GH-90000
            .build(); // GH-90000

        HttpResponse response = runPromise(() -> controller.regenerateWithDiff(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        verify(generationService).regenerateWithDiff(any(), any()); // GH-90000
    }

    private record DiffEnvelope(ValidatedSpec validatedSpec, GeneratedArtifacts existingArtifacts) { // GH-90000
    }
}
