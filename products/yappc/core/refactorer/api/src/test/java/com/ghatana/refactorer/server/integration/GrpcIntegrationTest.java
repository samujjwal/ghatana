package com.ghatana.refactorer.server.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.refactorer.api.v1.DiagnoseRequest;
import com.ghatana.refactorer.api.v1.HealthRequest;
import com.ghatana.refactorer.api.v1.JobId;
import com.ghatana.refactorer.api.v1.ProgressEvent;
import com.ghatana.refactorer.api.v1.Report;
import com.ghatana.refactorer.api.v1.RunRequest;
import com.ghatana.refactorer.api.v1.RunStatus;
import com.ghatana.refactorer.server.testutils.GrpcTestData;
import com.ghatana.refactorer.server.testutils.IntegrationTestSupport;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Integration tests exercising the gRPC transport implementation backed by the shared service
 * runtime.
 
 * @doc.type class
 * @doc.purpose Handles grpc integration test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class GrpcIntegrationTest extends IntegrationTestSupport {

    @Test
    void grpcRunCreatesJob() {
        RunRequest request =
                GrpcTestData.runRequest(
                        "/tmp/grpc-run",
                        List.of("**/*.java"),
                        false,
                        "tenant-grpc",
                        "grpc-run-123",
                        Map.of("policy.strict", "true"),
                        List.of("java", "python"));

        JobId response = grpcBlockingStub.run(request);

        assertThat(response.getId()).isNotBlank();
        assertThat(harness.getJobService().get(response.getId())).isPresent();
    }

    @Test
    void grpcDiagnoseReturnsDiagnostics() {
        DiagnoseRequest request =
                GrpcTestData.diagnoseRequest(
                        "/tmp/grpc-diagnose",
                        List.of("**/*.java", "**/*.ts"),
                        true,
                        "tenant-diagnose",
                        List.of("java"),
                        Map.of());

        var response = grpcBlockingStub.diagnose(request);

        assertThat(response.getDiagnosticsList()).isNotEmpty();
        assertThat(response.getExecutionId()).isNotBlank();
    }

    @Test
    void grpcGetStatusAfterRunReturnsQueuedState() {
        JobId jobId = createJobViaGrpc("grpc-status-123");

        RunStatus status = grpcBlockingStub.getStatus(jobId);

        assertThat(status.getJobId()).isEqualTo(jobId.getId());
        assertThat(status.getState()).isEqualTo("QUEUED");
        assertThat(status.getToolVersionsMap()).containsEntry("idempotencyKey", "grpc-status-123");
    }

    @Test
    void grpcGetReportAfterRunReturnsSummary() {
        JobId jobId = createJobViaGrpc("grpc-report-123");

        Report report = grpcBlockingStub.getReport(jobId);

        assertThat(report.getJobId()).isEqualTo(jobId.getId());
        assertThat(report.getSummaryJson()).contains("QUEUED");
    }

    @Test
    void grpcStreamProgressEmitsEvents() {
        JobId jobId = createJobViaGrpc("grpc-progress-123");

        Iterator<ProgressEvent> iterator = grpcBlockingStub.streamProgress(jobId);
        int count = 0;
        while (iterator.hasNext()) {
            ProgressEvent event = iterator.next();
            assertThat(event.getJobId()).isEqualTo(jobId.getId());
            assertThat(event.getEventType()).isEqualTo("progress");
            count++;
        }
        assertThat(count).isGreaterThanOrEqualTo(3);
    }

    @Test
    void grpcHealthEndpointReportsUp() {
        var response = grpcBlockingStub.health(HealthRequest.getDefaultInstance());

        assertThat(response.getStatus()).isEqualTo("UP");
    }

    private JobId createJobViaGrpc(String idempotencyKey) {
        RunRequest request =
                GrpcTestData.runRequest(
                        "/tmp/grpc-run",
                        List.of("**/*.java"),
                        false,
                        "tenant-grpc",
                        idempotencyKey,
                        Map.of(),
                        List.of("java"));
        return grpcBlockingStub.run(request);
    }
}
