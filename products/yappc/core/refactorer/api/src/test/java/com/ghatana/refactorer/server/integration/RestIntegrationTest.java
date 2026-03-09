package com.ghatana.refactorer.server.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.refactorer.server.dto.RestModels;
import com.ghatana.refactorer.server.testutils.IntegrationTestSupport;
import com.ghatana.refactorer.server.testutils.RestTestData;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Focused REST integration tests validating shared `JobService` lifecycle flows. 
 * @doc.type class
 * @doc.purpose Handles rest integration test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class RestIntegrationTest extends IntegrationTestSupport {

    @Test
    void runStatusCancelReportLifecycle() throws Exception {
        RestModels.RunRequest runRequest =
                RestTestData.runRequest(
                        "/tmp/rest-integration",
                        List.of("**/*.java", "**/*.md"),
                        List.of("java", "markdown"),
                        true,
                        "rest-run-123",
                        false);

        HttpRequest runHttpRequest =
                HttpRequest.newBuilder()
                        .uri(URI.create(harness.getHttpBaseUrl() + "/api/v1/run"))
                        .header("Content-Type", "application/json")
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        objectMapper.writeValueAsString(runRequest)))
                        .build();

        HttpResponse<String> runResponse =
                httpClient.send(runHttpRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(runResponse.statusCode()).isEqualTo(200);

        RestModels.JobResponse jobResponse =
                objectMapper.readValue(runResponse.body(), RestModels.JobResponse.class);
        String jobId = jobResponse.jobId();
        assertThat(jobId).isNotBlank();

        HttpRequest statusRequest =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        harness.getHttpBaseUrl()
                                                + "/api/v1/jobs/"
                                                + jobId
                                                + "/status"))
                        .GET()
                        .build();
        HttpResponse<String> statusResponse =
                httpClient.send(statusRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(statusResponse.statusCode()).isEqualTo(200);

        RestModels.RunStatus status =
                objectMapper.readValue(statusResponse.body(), RestModels.RunStatus.class);
        assertThat(status.jobId()).isEqualTo(jobId);
        assertThat(status.state()).isEqualTo("QUEUED");
        assertThat(status.toolVersions()).containsEntry("idempotencyKey", "rest-run-123");

        HttpRequest cancelRequest =
                HttpRequest.newBuilder()
                        .uri(URI.create(harness.getHttpBaseUrl() + "/api/v1/jobs/" + jobId))
                        .DELETE()
                        .build();
        HttpResponse<String> cancelResponse =
                httpClient.send(cancelRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(cancelResponse.statusCode()).isEqualTo(200);

        RestModels.RunStatus cancelled =
                objectMapper.readValue(cancelResponse.body(), RestModels.RunStatus.class);
        assertThat(cancelled.state()).isEqualTo("CANCELLED");

        HttpRequest reportRequest =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        harness.getHttpBaseUrl()
                                                + "/api/v1/jobs/"
                                                + jobId
                                                + "/report"))
                        .header("Accept", "application/json")
                        .GET()
                        .build();
        HttpResponse<String> reportResponse =
                httpClient.send(reportRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(reportResponse.statusCode()).isEqualTo(200);

        RestModels.Report report =
                objectMapper.readValue(reportResponse.body(), RestModels.Report.class);
        assertThat(report.jobId()).isEqualTo(jobId);
        assertThat(report.summaryJson()).contains("CANCELLED");

        HttpRequest metricsRequest =
                HttpRequest.newBuilder()
                        .uri(URI.create(harness.getHttpBaseUrl() + "/metrics"))
                        .GET()
                        .build();
        HttpResponse<String> metricsResponse =
                httpClient.send(metricsRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(metricsResponse.statusCode()).isEqualTo(200);
        assertThat(metricsResponse.body()).contains("polyfix_jobs_submitted_total");
        assertThat(metricsResponse.body()).contains("polyfix_jobs_cancelled_total");
    }
}
