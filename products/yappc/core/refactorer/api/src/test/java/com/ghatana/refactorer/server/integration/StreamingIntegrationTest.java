package com.ghatana.refactorer.server.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ghatana.refactorer.server.testutils.IntegrationTestSupport;
import com.ghatana.refactorer.server.testutils.TestJobs;
import java.net.URI;
import java.time.Duration;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Integration tests focused on streaming transports (SSE, WebSocket) backed by the shared 
 * JobProgressStreamer implementation.

 * @doc.type class
 * @doc.purpose Handles streaming integration test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class StreamingIntegrationTest extends IntegrationTestSupport {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {}; 

    @Test
    void sseStreamReturnsExpectedEventSequence() throws Exception { 
        String jobId = TestJobs.submit(harness.getJobService(), "sse-seq-1").jobId(); 

        HttpRequest request =
                HttpRequest.newBuilder() 
                        .uri( 
                                URI.create( 
                                        harness.getHttpBaseUrl() 
                                                + "/api/v1/jobs/"
                                                + jobId
                                                + "/events"))
                        .timeout(Duration.ofSeconds(5)) 
                        .header("Accept", "text/event-stream") 
                        .GET() 
                        .build(); 

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString()); 

        assertThat(response.statusCode()).isEqualTo(200); 
        assertThat(response.headers().firstValue("Content-Type")).contains("text/event-stream");

        String body = response.body(); 
        int idxConnected = body.indexOf("event: connected");
        int idxStatus = body.indexOf("event: status");
        int idxProgress = body.indexOf("event: progress");
        int idxComplete = body.indexOf("event: complete");

        assertThat(idxConnected).isGreaterThanOrEqualTo(0); 
        assertThat(idxStatus).isGreaterThan(idxConnected); 
        assertThat(idxProgress).isGreaterThan(idxStatus); 
        assertThat(idxComplete).isGreaterThan(idxProgress); 
        assertThat(body).contains("data: {\"jobId\":\"" + jobId); 
        assertThat(body).contains("event: complete\ndata: {\"jobId\":\"" + jobId + "\"}"); 
    }
}
