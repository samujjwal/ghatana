package com.ghatana.yappc.services.artifact;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.artifact.ArtifactGraphResponse;
import com.ghatana.yappc.storage.ArtifactGraphRepository;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verifies snapshot-scoped rollback behavior for artifact graph compensation
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ArtifactGraphService Rollback Tests")
class ArtifactGraphServiceRollbackTest extends EventloopTestBase {

    private static final ArtifactRequestScope SCOPE = new ArtifactRequestScope(
        "project-123",
        "tenant-123",
        "workspace-123"
    );

    @Mock
    private ArtifactGraphRepository repository;

    private ArtifactGraphServiceImpl service;

    @BeforeEach
    void setUp() {
        Executor blockingExecutor = Runnable::run;
        service = new ArtifactGraphServiceImpl(repository, blockingExecutor);
    }

    @Test
    @DisplayName("returns skip response when snapshotId is blank")
    void rollbackIngest_returnsSkipWhenSnapshotIdBlank() {
        ArtifactGraphResponse response = runPromise(() -> service.rollbackIngest(SCOPE, " "));

        assertThat(response.success()).isFalse();
        assertThat(response.operation()).isEqualTo("rollback");
        assertThat(response.message()).contains("snapshotId is missing");
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("rolls back snapshot-scoped graph rows and returns rollback response")
    void rollbackIngest_rollsBackSnapshotScopedRows() {
        when(repository.tombstoneGraphForSnapshot(
            SCOPE.projectId(),
            SCOPE.tenantId(),
            SCOPE.workspaceId(),
            "snap-1"
        )).thenReturn(Promise.of(true));

        ArtifactGraphResponse response = runPromise(() -> service.rollbackIngest(SCOPE, "snap-1"));

        verify(repository).tombstoneGraphForSnapshot(
            SCOPE.projectId(),
            SCOPE.tenantId(),
            SCOPE.workspaceId(),
            "snap-1"
        );
        assertThat(response.success()).isTrue();
        assertThat(response.operation()).isEqualTo("rollback");
        assertThat(response.message()).contains("rollback completed");
        assertThat(response.result()).containsEntry("snapshotId", "snap-1");
        assertThat(response.result()).containsEntry("tombstoned", true);
    }

    @Test
    @DisplayName("returns rollback no-op response when snapshot tombstone updates no rows")
    void rollbackIngest_returnsNoOpResponseWhenNoRowsUpdated() {
        when(repository.tombstoneGraphForSnapshot(
            SCOPE.projectId(),
            SCOPE.tenantId(),
            SCOPE.workspaceId(),
            "snap-empty"
        )).thenReturn(Promise.of(false));

        ArtifactGraphResponse response = runPromise(() -> service.rollbackIngest(SCOPE, "snap-empty"));

        assertThat(response.success()).isFalse();
        assertThat(response.operation()).isEqualTo("rollback");
        assertThat(response.message()).contains("found no active rows");
        assertThat(response.result()).containsEntry("snapshotId", "snap-empty");
        assertThat(response.result()).containsEntry("tombstoned", false);
    }

    @Test
    @DisplayName("propagates repository failure when snapshot rollback tombstoning fails")
    void rollbackIngest_propagatesRepositoryFailure() {
        when(repository.tombstoneGraphForSnapshot(
            SCOPE.projectId(),
            SCOPE.tenantId(),
            SCOPE.workspaceId(),
            "snap-err"
        )).thenReturn(Promise.ofException(new IllegalStateException("rollback-store-down")));

        assertThatThrownBy(() -> runPromise(() -> service.rollbackIngest(SCOPE, "snap-err")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("rollback-store-down");

        verify(repository).tombstoneGraphForSnapshot(
            SCOPE.projectId(),
            SCOPE.tenantId(),
            SCOPE.workspaceId(),
            "snap-err"
        );
    }
}
