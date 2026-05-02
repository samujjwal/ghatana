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
@ExtendWith(MockitoExtension.class) 
class GenerationApiControllerTest extends EventloopTestBase {

    @Mock
    private GenerationService generationService;

    @Mock
    private YappcArtifactRepository artifactRepository;

    private GenerationApiController controller;

    @BeforeEach
    void setUp() { 
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
        verify(generationService, never()).generate(any()); 
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
        when(generationService.regenerateWithDiff(any(), any())).thenReturn(Promise.of(diffResult)); 

        String requestJson = JsonMapper.toJson(new DiffEnvelope(validSpec, existingArtifacts)); 
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/yappc/generate/diff")
            .withBody(ByteBuf.wrapForReading(requestJson.getBytes(StandardCharsets.UTF_8))) 
            .build(); 

        HttpResponse response = runPromise(() -> controller.regenerateWithDiff(request)); 

        assertThat(response.getCode()).isEqualTo(200); 
        verify(generationService).regenerateWithDiff(any(), any()); 
    }

    private record DiffEnvelope(ValidatedSpec validatedSpec, GeneratedArtifacts existingArtifacts) { 
    }
}
