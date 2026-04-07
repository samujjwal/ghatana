package com.ghatana.phr.hie;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpNepalHieClientTest extends EventloopTestBase {

    @Test
    void parsesAcceptedAcknowledgement() {
        HttpNepalHieClient client = new HttpNepalHieClient(
            Executors.newSingleThreadExecutor(),
            new NepalHieConfig("https://example.invalid", "GHATANA-PHR", "PHR-NEPAL", "NEPAL-HIE", "NHIE", "token", Duration.ofSeconds(5)),
            request -> new StubResponse(200, "MSH|^~\\&|NEPAL-HIE|NHIE|GHATANA-PHR|PHR-NEPAL|20260406120000||ACK|ctrl-1|P|2.5\rMSA|AA|ctrl-1|Accepted")
        );

        NepalHieAck ack = runPromise(() -> client.submitMessage("patient-1", "corr-1", "MSH|^~\\&|..."));

        assertTrue(ack.accepted());
        assertEquals("ctrl-1", ack.messageControlId());
    }

    @Test
    void failsOnNonSuccessStatus() {
        HttpNepalHieClient client = new HttpNepalHieClient(
            Executors.newSingleThreadExecutor(),
            new NepalHieConfig("https://example.invalid", "GHATANA-PHR", "PHR-NEPAL", "NEPAL-HIE", "NHIE", "token", Duration.ofSeconds(5)),
            request -> new StubResponse(503, "unavailable")
        );

        assertThrows(Exception.class, () -> runPromise(() -> client.submitMessage("patient-1", "corr-1", "MSH|^~\\&|...")));
    }

    private static final class StubResponse implements HttpResponse<String> {
        private final int statusCode;
        private final String body;

        private StubResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        @Override public int statusCode() { return statusCode; }
        @Override public String body() { return body; }
        @Override public HttpRequest request() { return HttpRequest.newBuilder().uri(URI.create("https://example.invalid")).build(); }
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(java.util.Map.of(), (a, b) -> true); }
        @Override public URI uri() { return URI.create("https://example.invalid"); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
    }
}