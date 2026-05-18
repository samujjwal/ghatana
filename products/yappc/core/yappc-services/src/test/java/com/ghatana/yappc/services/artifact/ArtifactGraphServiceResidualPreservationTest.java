package com.ghatana.yappc.services.artifact;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.artifact.*;
import com.ghatana.yappc.services.artifact.ArtifactGraphService;
import com.ghatana.yappc.domain.artifact.ArtifactNodeDto;
import com.ghatana.yappc.domain.artifact.EdgeResolutionRecordDto;
import com.ghatana.yappc.domain.artifact.ResidualIslandDto;
import com.ghatana.yappc.domain.artifact.SourceLocationDto;
import com.ghatana.yappc.domain.artifact.UnresolvedGraphEdgeDto;
import com.ghatana.yappc.storage.ArtifactGraphRepository;
import com.ghatana.yappc.storage.ArtifactModelVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verifies ArtifactGraphServiceImpl preserves exact residual island payload — no synthesised placeholders
 * @doc.layer test
 * @doc.pattern BehaviorTest
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ArtifactGraphServiceImpl Residual Preservation Tests")
class ArtifactGraphServiceResidualPreservationTest extends EventloopTestBase {

    @Mock
    private ArtifactGraphRepository repository;

    @Mock
    private ArtifactModelVersionRepository versionRepository;

    private ArtifactGraphServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ArtifactGraphServiceImpl(repository, versionRepository, Runnable::run);

        when(repository.upsertNodes(anyString(), anyString(), anyString(), any(), anyString(), anyString(), any()))
            .thenReturn(io.activej.promise.Promise.of(null));
        when(repository.upsertEdges(anyString(), anyString(), anyString(), any(), anyString(), anyString()))
            .thenReturn(io.activej.promise.Promise.of(null));
        when(repository.saveUnresolvedEdges(anyString(), anyString(), anyString(), any()))
            .thenReturn(io.activej.promise.Promise.of(null));
        when(repository.saveEdgeResolutionRecords(anyString(), anyString(), any()))
            .thenReturn(io.activej.promise.Promise.of(null));
        when(repository.saveResidualIslands(anyString(), anyString(), anyString(), any()))
            .thenReturn(io.activej.promise.Promise.of(null));
        when(versionRepository.saveVersion(any()))
            .thenAnswer(invocation -> io.activej.promise.Promise.of(invocation.getArgument(0)));
    }

    @Test
    @DisplayName("persists exact sourceSpan and checksum from residualIslands payload")
    void persistsExactResidualIslandPayload() {
        ResidualIslandDto island = new ResidualIslandDto(
            "ri-exact",
            "imperative_logic",
            "Complex reducer",
            "const next = reducer(state, action);",
            new SourceLocationDto("src/store/reducers.ts", 10, 0, 50, 1),
            "src/store/reducers.ts:10:0-50:1",
            "sha256:cafebabe",
            "blobs/cafebabe",
            "too_complex",
            0.85,
            true,
            0.9,
            Map.of("phase", "extraction"),
            2,
            "t1", "p1", "w1", "snap-1"
        );

        ArtifactGraphIngestRequest request = new ArtifactGraphIngestRequest(
            "p1", "t1",
            List.of(), List.of(),
            null, "snap-1", "ver-1", "checksum-1",
            List.of(), List.of(),
            List.of(island)
        );

        ArtifactRequestScope scope = new ArtifactRequestScope("t1", "w1", "p1");
        runPromise(() -> service.ingestGraph(scope, request));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ArtifactGraphRepository.ResidualIslandRecord>> captor =
            ArgumentCaptor.forClass(List.class);
        verify(repository).saveResidualIslands(anyString(), anyString(), anyString(), captor.capture());

        List<ArtifactGraphRepository.ResidualIslandRecord> records = captor.getValue();
        assertThat(records).hasSize(1);
        ArtifactGraphRepository.ResidualIslandRecord record = records.get(0);

        assertThat(record.id()).isEqualTo("ri-exact");
        assertThat(record.sourceSpan()).isEqualTo("src/store/reducers.ts:10:0-50:1");
        assertThat(record.checksum()).isEqualTo("sha256:cafebabe");
        assertThat(record.rawFragmentRef()).isEqualTo("blobs/cafebabe");
        assertThat(record.reason()).isEqualTo("too_complex");
        assertThat(record.confidence()).isEqualTo(0.85);
        assertThat(record.reviewRequired()).isTrue();
        assertThat(record.riskScore()).isEqualTo(0.9);
        assertThat(record.fileCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("empty residualIslands triggers saveResidualIslands with empty list, not placeholder records")
    void emptyResidualIslandsSavesEmptyList() {
        ArtifactGraphIngestRequest request = new ArtifactGraphIngestRequest(
            "p1", "t1",
            List.of(), List.of(),
            null, "snap-2", "ver-2", "checksum-2",
            List.of(), List.of(),
            List.of()
        );

        ArtifactRequestScope scope = new ArtifactRequestScope("t1", "w1", "p1");
        runPromise(() -> service.ingestGraph(scope, request));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ArtifactGraphRepository.ResidualIslandRecord>> captor =
            ArgumentCaptor.forClass(List.class);
        verify(repository).saveResidualIslands(anyString(), anyString(), anyString(), captor.capture());

        assertThat(captor.getValue()).isEmpty();
    }
}
