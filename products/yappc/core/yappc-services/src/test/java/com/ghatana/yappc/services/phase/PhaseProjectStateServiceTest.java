package com.ghatana.yappc.services.phase;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PhaseProjectStateService}.
 *
 * @doc.type class
 * @doc.purpose Verifies typed project snapshot mapping and degraded fail-closed behavior
 * @doc.layer test
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PhaseProjectStateService")
class PhaseProjectStateServiceTest extends EventloopTestBase {

    @Mock
    private DataCloudClient dataCloudClient;

    @Mock
    private PhaseFeatureFlagProvider phaseFeatureFlagProvider;

    @Test
    @DisplayName("queryProjectSnapshot maps enriched state to typed snapshot")
    void queryProjectSnapshot_mapsEnrichedStateToTypedSnapshot() {
        Map<String, Object> persisted = Map.of(
                "id", "project-1",
                "name", "Project One",
                "workspaceId", "workspace-1",
                "workspaceName", "Workspace One",
                "tenantId", "tenant-1",
                "lifecyclePhase", "generate",
                "tier", "PRO",
                "status", "active");
        DataCloudClient.Entity entity = DataCloudClient.Entity.of("project-1", "projects", persisted);

        when(dataCloudClient.findById("tenant-1", "projects", "project-1"))
                .thenReturn(Promise.of(Optional.of(entity)));
        when(phaseFeatureFlagProvider.enrichProjectStateWithTenantFlags(eq("tenant-1"), any()))
                .thenReturn(Promise.of(Map.of(
                        "projectId", "project-1",
                        "name", "Project One",
                        "workspaceId", "workspace-1",
                        "workspaceName", "Workspace One",
                        "tenantId", "tenant-1",
                        "lifecyclePhase", "generate",
                        "tier", "PRO",
                        "status", "active",
                        "enabledPhaseFlags", List.of("phase.advance", "phase.report.export")
                )));

        PhaseProjectStateService service = new PhaseProjectStateService(dataCloudClient, phaseFeatureFlagProvider);

        ProjectLifecycleSnapshot snapshot = runPromise(() ->
                service.queryProjectSnapshot("generate", "project-1", "workspace-1", "tenant-1", "corr-1"));

        assertThat(snapshot.degraded()).isFalse();
        assertThat(snapshot.projectId()).isEqualTo("project-1");
        assertThat(snapshot.workspaceId()).isEqualTo("workspace-1");
        assertThat(snapshot.projectName()).isEqualTo("Project One");
        assertThat(snapshot.lifecyclePhase()).isEqualTo("generate");
        assertThat(snapshot.enabledPhaseFlags()).contains("phase.advance", "phase.report.export");
    }

    @Test
    @DisplayName("queryProjectSnapshot returns degraded snapshot when project is missing")
    void queryProjectSnapshot_returnsDegradedSnapshotWhenProjectMissing() {
        when(dataCloudClient.findById("tenant-1", "projects", "missing-project"))
                .thenReturn(Promise.of(Optional.empty()));

        PhaseProjectStateService service = new PhaseProjectStateService(dataCloudClient, phaseFeatureFlagProvider);

        ProjectLifecycleSnapshot snapshot = runPromise(() ->
                service.queryProjectSnapshot("intent", "missing-project", "workspace-1", "tenant-1", "corr-2"));

        assertThat(snapshot.degraded()).isTrue();
        assertThat(snapshot.degradedReason()).isEqualTo("PROJECT_STATE_NOT_FOUND");
        assertThat(snapshot.status()).isEqualTo("degraded");
    }
}
