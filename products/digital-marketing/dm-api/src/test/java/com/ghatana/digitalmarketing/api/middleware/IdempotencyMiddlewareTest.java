package com.ghatana.digitalmarketing.api.middleware;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IdempotencyMiddleware")
class IdempotencyMiddlewareTest extends EventloopTestBase {

    private DataSource dataSource;
    private AtomicInteger delegateCalls;
    private AsyncServlet servlet;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:idempotency_" + System.nanoTime() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        dataSource = ds;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE dmos_idempotency_store (
                    idempotency_key VARCHAR(128) NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL,
                    request_fingerprint VARCHAR(256) NOT NULL,
                    response_status INTEGER NOT NULL,
                    response_headers VARCHAR,
                    response_body BYTEA,
                    expires_at TIMESTAMP NOT NULL,
                    created_at TIMESTAMP DEFAULT NOW(),
                    PRIMARY KEY (idempotency_key, tenant_id)
                )
                """);
        }

        delegateCalls = new AtomicInteger();
        AsyncServlet delegate = request -> {
            int call = delegateCalls.incrementAndGet();
            return Promise.of(HttpResponse.ofCode(201)
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .withBody(("{\"call\":" + call + "}").getBytes(StandardCharsets.UTF_8))
                .build());
        };
        servlet = new IdempotencyMiddleware(dataSource, eventloop(), Duration.ofHours(1)).wrap(delegate);
    }

    @Test
    @DisplayName("replays previous response for same key and same semantic JSON body")
    void shouldReplayPreviousResponseForSameSemanticInput() {
        HttpResponse first = runPromise(() -> servlet.serve(request("idem-1", "{\"name\":\"Launch\",\"budget\":500}")));
        HttpResponse second = runPromise(() -> servlet.serve(request("idem-1", "{\"budget\":500,\"name\":\"Launch\"}")));

        assertThat(first.getCode()).isEqualTo(201);
        assertThat(second.getCode()).isEqualTo(201);
        assertThat(body(second)).isEqualTo(body(first));
        assertThat(delegateCalls).hasValue(1);
    }

    @Test
    @DisplayName("returns 409 when duplicate key has different semantic input")
    void shouldReturnConflictForSameKeyWithDifferentInput() {
        HttpResponse first = runPromise(() -> servlet.serve(request("idem-2", "{\"name\":\"Launch\"}")));
        HttpResponse second = runPromise(() -> servlet.serve(request("idem-2", "{\"name\":\"Different\"}")));

        assertThat(first.getCode()).isEqualTo(201);
        assertThat(second.getCode()).isEqualTo(409);
        assertThat(body(second)).contains("X-Idempotency-Key was already used for a different request");
        assertThat(delegateCalls).hasValue(1);
    }

    private HttpRequest request(String idempotencyKey, String body) {
        return HttpRequest.post("http://localhost/v1/workspaces/ws-1/campaigns")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "principal-1")
            .withHeader(HttpHeaders.of("X-Session-ID"), "session-1")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "corr-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), idempotencyKey)
            .withBody(body.getBytes(StandardCharsets.UTF_8))
            .build();
    }

    private String body(HttpResponse response) {
        return response.getBody() == null ? "" : new String(response.getBody().asArray(), StandardCharsets.UTF_8);
    }
}
