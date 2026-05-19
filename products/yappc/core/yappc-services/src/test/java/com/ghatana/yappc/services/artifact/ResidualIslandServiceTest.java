package com.ghatana.yappc.services.artifact;

import com.ghatana.yappc.domain.artifact.ResidualIslandDto;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResidualIslandServiceTest {

    private final ResidualIslandService service = new ResidualIslandService();

    @Test
    void preservesSourceAndClassifiesSecretLikeResidualsForReview() {
        ArtifactRequestScope scope = new ArtifactRequestScope("project-1", "tenant-1", "workspace-1");
        ResidualIslandDto island = new ResidualIslandDto(
                "residual-1",
                "imperative_logic",
                "raw environment config",
                "const apiKey = process.env.SECRET_TOKEN;",
                null,
                "src/config.ts:1:0-1:39",
                "checksum-1",
                "raw://fragment/residual-1",
                null,
                0.62d,
                false,
                0.0d,
                Map.of("extractor", "ts-worker"),
                1,
                null,
                null,
                null,
                "snapshot-1");

        ResidualIslandDto analyzed = service.analyze(scope, island);

        assertEquals(island.originalSource(), analyzed.originalSource());
        assertEquals(island.checksum(), analyzed.checksum());
        assertEquals(island.sourceSpan(), analyzed.sourceSpan());
        assertEquals("contains-secret-like-fragment", analyzed.reason());
        assertTrue(analyzed.reviewRequired());
        assertTrue(analyzed.riskScore() >= 0.95d);
        assertEquals("tenant-1", analyzed.tenantId());
        assertEquals("workspace-1", analyzed.workspaceId());
        assertEquals("project-1", analyzed.projectId());
        assertEquals("preserve-original-source", analyzed.metadata().get("preservationStrategy"));
    }
}
