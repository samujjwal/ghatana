package com.ghatana.yappc.services.phase;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.api.PhasePacket;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DataCloudPlatformRunStatusService}.
 *
 * @doc.type class
 * @doc.purpose Verifies Data Cloud run-status failures surface as degraded runtime truth
 * @doc.layer test
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataCloudPlatformRunStatusService")
class DataCloudPlatformRunStatusServiceTest extends EventloopTestBase {

    @Mock private DataCloudClient dataCloudClient;

    @Test
    @DisplayName("Data Cloud query failure returns degraded runtime truth instead of empty")
    void queryFailure_returnsDegradedRuntimeTruth() {
        when(dataCloudClient.query(eq("tenant-1"), eq("yappc_platform_runs"), any()))
                .thenReturn(Promise.ofException(new RuntimeException("Data Cloud down")));

        DataCloudPlatformRunStatusService service = new DataCloudPlatformRunStatusService(dataCloudClient);

        Optional<PhasePacket.PlatformRunStatus> status = runPromise(() ->
                service.findLatest("tenant-1", "workspace-1", "project-1", "RUN"));

        assertThat(status).isPresent();
        assertThat(status.orElseThrow().status()).isEqualTo("DEGRADED_RUNTIME_TRUTH");
        assertThat(status.orElseThrow().evidenceIds())
                .anyMatch(id -> id.startsWith("runtime-truth-query-failed:"));
    }
}
