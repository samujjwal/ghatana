package com.ghatana.pipeline.registry.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.pipeline.registry.migration.MigrationReport;
import com.ghatana.pipeline.registry.migration.PipelineMigrationService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PipelineMigrationController}.
 *
 * @doc.type test
 * @doc.purpose Verify HTTP responses for pipeline migration trigger endpoint
 * @doc.layer product
 * @doc.pattern ControllerTest
 */
@DisplayName("PipelineMigrationController tests")
@ExtendWith(MockitoExtension.class)
class PipelineMigrationControllerTest extends EventloopTestBase {

    @Mock
    private PipelineMigrationService migrationService;

    private PipelineMigrationController controller;

    @BeforeEach
    void setUp() {
        controller = new PipelineMigrationController(migrationService, new ObjectMapper());
    }

    @Test
    @DisplayName("migratePipelines: live run → 200 OK with report")
    void migratePipelinesLiveRunReturns200() {
        MigrationReport report = MigrationReport.builder().build();
        report.complete();
        when(migrationService.migrateAll(false)).thenReturn(Promise.of(report));

        HttpRequest request = HttpRequest.post("http://localhost/api/v1/migrations/pipelines").build();
        HttpResponse response = runPromise(() -> controller.migratePipelines(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("migratePipelines: ?dryRun=true → 200 OK with dry-run report")
    void migratePipelinesDryRunReturns200() {
        MigrationReport report = MigrationReport.builder().dryRun(true).build();
        report.complete();
        when(migrationService.migrateAll(true)).thenReturn(Promise.of(report));

        HttpRequest request = HttpRequest.post("http://localhost/api/v1/migrations/pipelines?dryRun=true").build();
        HttpResponse response = runPromise(() -> controller.migratePipelines(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("migratePipelines: service failure → 500 Internal Server Error")
    void migratePipelinesServiceFailureReturns500() {
        when(migrationService.migrateAll(false))
                .thenReturn(Promise.ofException(new RuntimeException("Migration failed")));

        HttpRequest request = HttpRequest.post("http://localhost/api/v1/migrations/pipelines").build();
        HttpResponse response = runPromise(() -> controller.migratePipelines(request));

        assertThat(response.getCode()).isEqualTo(500);
    }
}
