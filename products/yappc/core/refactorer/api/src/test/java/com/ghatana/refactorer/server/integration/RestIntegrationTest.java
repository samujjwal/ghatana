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
    void runStatusCancelReportLifecycle() throws Exception { // GH-90000
        RestModels.RunRequest runRequest =
                RestTestData.runRequest( // GH-90000
                        "/tmp/rest-integration",
                        List.of("**/*.java", "**/*.md"), // GH-90000
                        List.of("java", "markdown"), // GH-90000
                        true,
                        "rest-run-123",
                        false);

        HttpRequest runHttpRequest =
                HttpRequest.newBuilder() // GH-90000
                        .uri(URI.create(harness.getHttpBaseUrl() + "/api/v1/run")) // GH-90000
                        .header("Content-Type", "application/json") // GH-90000
                        .POST( // GH-90000
                                HttpRequest.BodyPublishers.ofString( // GH-90000
                                        objectMapper.writeValueAsString(runRequest))) // GH-90000
                        .build(); // GH-90000

        HttpResponse<String> runResponse =
                httpClient.send(runHttpRequest, HttpResponse.BodyHandlers.ofString()); // GH-90000
        assertThat(runResponse.statusCode()).isEqualTo(200); // GH-90000

        RestModels.JobResponse jobResponse =
                objectMapper.readValue(runResponse.body(), RestModels.JobResponse.class); // GH-90000
        String jobId = jobResponse.jobId(); // GH-90000
        assertThat(jobId).isNotBlank(); // GH-90000

        HttpRequest statusRequest =
                HttpRequest.newBuilder() // GH-90000
                        .uri( // GH-90000
                                URI.create( // GH-90000
                                        harness.getHttpBaseUrl() // GH-90000
                                                + "/api/v1/jobs/"
                                                + jobId
                                                + "/status"))
                        .GET() // GH-90000
                        .build(); // GH-90000
        HttpResponse<String> statusResponse =
                httpClient.send(statusRequest, HttpResponse.BodyHandlers.ofString()); // GH-90000
        assertThat(statusResponse.statusCode()).isEqualTo(200); // GH-90000

        RestModels.RunStatus status =
                objectMapper.readValue(statusResponse.body(), RestModels.RunStatus.class); // GH-90000
        assertThat(status.jobId()).isEqualTo(jobId); // GH-90000
        assertThat(status.state()).isEqualTo("QUEUED [GH-90000]");
        assertThat(status.toolVersions()).containsEntry("idempotencyKey", "rest-run-123"); // GH-90000

        HttpRequest cancelRequest =
                HttpRequest.newBuilder() // GH-90000
                        .uri(URI.create(harness.getHttpBaseUrl() + "/api/v1/jobs/" + jobId)) // GH-90000
                        .DELETE() // GH-90000
                        .build(); // GH-90000
        HttpResponse<String> cancelResponse =
                httpClient.send(cancelRequest, HttpResponse.BodyHandlers.ofString()); // GH-90000
        assertThat(cancelResponse.statusCode()).isEqualTo(200); // GH-90000

        RestModels.RunStatus cancelled =
                objectMapper.readValue(cancelResponse.body(), RestModels.RunStatus.class); // GH-90000
        assertThat(cancelled.state()).isEqualTo("CANCELLED [GH-90000]");

        HttpRequest reportRequest =
                HttpRequest.newBuilder() // GH-90000
                        .uri( // GH-90000
                                URI.create( // GH-90000
                                        harness.getHttpBaseUrl() // GH-90000
                                                + "/api/v1/jobs/"
                                                + jobId
                                                + "/report"))
                        .header("Accept", "application/json") // GH-90000
                        .GET() // GH-90000
                        .build(); // GH-90000
        HttpResponse<String> reportResponse =
                httpClient.send(reportRequest, HttpResponse.BodyHandlers.ofString()); // GH-90000
        assertThat(reportResponse.statusCode()).isEqualTo(200); // GH-90000

        RestModels.Report report =
                objectMapper.readValue(reportResponse.body(), RestModels.Report.class); // GH-90000
        assertThat(report.jobId()).isEqualTo(jobId); // GH-90000
        assertThat(report.summaryJson()).contains("CANCELLED [GH-90000]");

        HttpRequest metricsRequest =
                HttpRequest.newBuilder() // GH-90000
                        .uri(URI.create(harness.getHttpBaseUrl() + "/metrics")) // GH-90000
                        .GET() // GH-90000
                        .build(); // GH-90000
        HttpResponse<String> metricsResponse =
                httpClient.send(metricsRequest, HttpResponse.BodyHandlers.ofString()); // GH-90000
        assertThat(metricsResponse.statusCode()).isEqualTo(200); // GH-90000
        assertThat(metricsResponse.body()).contains("polyfix_jobs_submitted_total [GH-90000]");
        assertThat(metricsResponse.body()).contains("polyfix_jobs_cancelled_total [GH-90000]");
    }
}
