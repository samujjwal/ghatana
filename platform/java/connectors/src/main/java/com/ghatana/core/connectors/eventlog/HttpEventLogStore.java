/*
 * Copyright (c) 2026 Ghatana Technologies
 * Platform Connectors — EventLogStore SPI implementation over HTTP
 */
package com.ghatana.core.connectors.eventlog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * {@link EventLogStore} SPI implementation that connects to a remote Data-Cloud
 * server via plain HTTPS REST. Used when gRPC is not available (e.g., firewall
 * environments or during initial bootstrap).
 *
 * <p>Unlike {@link GrpcEventLogStore}, this implementation uses HTTP/1.1 and the
 * same REST endpoints exposed by the Data-Cloud HTTP server. Register in
 * {@code META-INF/services/com.ghatana.datacloud.spi.EventLogStore} so that
 * AEP's {@code AepEventCloudFactory} can discover it via {@link java.util.ServiceLoader}.
 *
 * <h2>Configuration (env vars)</h2>
 * <ul>
 *   <li>{@code DATACLOUD_HTTP_BASE_URL} — base URL of the Data-Cloud HTTP server (required).
 *       Must start with {@code https://} in any environment other than {@code development}.</li>
 *   <li>{@code DATACLOUD_HTTP_AUTH_TOKEN} — Bearer auth token (required outside dev).</li>
 *   <li>{@code APP_ENV} — set to {@code development} to allow plain {@code http://} and
 *       skip auth token requirement.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Remote Data-Cloud EventLogStore implementation using HTTPS REST transport
 * @doc.layer core
 * @doc.pattern Adapter, Service Provider Interface
 */
public final class HttpEventLogStore implements EventLogStore {

    private static final Logger LOG = LoggerFactory.getLogger(HttpEventLogStore.class);

    static final String ENV_BASE_URL   = "DATACLOUD_HTTP_BASE_URL";
    static final String ENV_AUTH_TOKEN = "DATACLOUD_HTTP_AUTH_TOKEN";
    static final String ENV_APP_ENV    = "APP_ENV";

    private final String baseUrl;
    private final String authToken;
    private final HttpClient httpClient;
    private final Executor blockingExecutor;
    private final ObjectMapper mapper;

    /**
     * Constructs an {@link HttpEventLogStore} from system environment variables.
     * Fails fast if required vars are absent.
     */
    public HttpEventLogStore() {
        this(System.getenv());
    }

    /**
     * Package-visible constructor for unit testing with an injected env map.
     */
    HttpEventLogStore(Map<String, String> env) {
        String url = requiredEnv(env, ENV_BASE_URL);
        String appEnv  = env.getOrDefault(ENV_APP_ENV, "production");
        boolean isDev  = "development".equalsIgnoreCase(appEnv);

        if (!isDev && !url.startsWith("https://")) {
            throw new IllegalStateException(
                "DATACLOUD_HTTP_BASE_URL must start with 'https://' in non-development environments. "
                + "Got: " + url + ". Set APP_ENV=development to allow plain http in local testing.");
        }

        this.baseUrl   = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.authToken = isDev
            ? env.getOrDefault(ENV_AUTH_TOKEN, "")
            : requiredEnv(env, ENV_AUTH_TOKEN);

        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

        this.blockingExecutor = Executors.newVirtualThreadPerTaskExecutor();

        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());

        LOG.info("HttpEventLogStore initialised → {} ({})", baseUrl, isDev ? "dev" : "production");
    }

    // --- EventLogStore API ---

    @Override
    public Promise<Offset> append(TenantContext tenant, EventEntry entry) {
        Objects.requireNonNull(tenant, "tenant required");
        Objects.requireNonNull(entry,  "entry required");
        return Promise.ofCompletionStage(CompletableFuture.supplyAsync(() -> {
            try {
                String json = mapper.writeValueAsString(toBody(entry));
                HttpResponse<String> resp = send(
                    "/api/v1/eventlog/" + tenant.tenantId() + "/events", "POST", json);
                validate(resp, 200, 201);
                Map<String, Object> result = parse(resp.body());
                return Offset.of(toLong(result.getOrDefault("offset", 0L)));
            } catch (Exception e) {
                throw wrap("append", tenant.tenantId(), e);
            }
        }, blockingExecutor));
    }

    @Override
    public Promise<List<Offset>> appendBatch(TenantContext tenant, List<EventEntry> entries) {
        Objects.requireNonNull(tenant,  "tenant required");
        Objects.requireNonNull(entries, "entries required");
        if (entries.isEmpty()) return Promise.of(List.of());

        return Promise.ofCompletionStage(CompletableFuture.supplyAsync(() -> {
            try {
                List<Map<String, Object>> batch = entries.stream().map(this::toBody).toList();
                String json = mapper.writeValueAsString(Map.of("events", batch));
                HttpResponse<String> resp = send(
                    "/api/v1/eventlog/" + tenant.tenantId() + "/events/batch", "POST", json);
                validate(resp, 200, 201);
                Map<String, Object> result = parse(resp.body());
                List<?> rawOffsets = (List<?>) result.getOrDefault("offsets", List.of());
                return rawOffsets.stream().map(o -> Offset.of(toLong(o))).toList();
            } catch (Exception e) {
                throw wrap("appendBatch", tenant.tenantId(), e);
            }
        }, blockingExecutor));
    }

    @Override
    public Promise<List<EventEntry>> read(TenantContext tenant, Offset from, int limit) {
        Objects.requireNonNull(tenant, "tenant required");
        Objects.requireNonNull(from,   "from required");
        return Promise.ofCompletionStage(CompletableFuture.supplyAsync(() -> {
            try {
                HttpResponse<String> resp = send(
                    "/api/v1/eventlog/" + tenant.tenantId() + "/events?from=" + from.value()
                        + "&limit=" + Math.max(1, limit),
                    "GET", null);
                validate(resp, 200);
                return parseEntries(resp.body());
            } catch (Exception e) {
                throw wrap("read", tenant.tenantId(), e);
            }
        }, blockingExecutor));
    }

    @Override
    public Promise<List<EventEntry>> readByTimeRange(
        TenantContext tenant, Instant startTime, Instant endTime, int limit) {
        Objects.requireNonNull(tenant);
        return Promise.ofCompletionStage(CompletableFuture.supplyAsync(() -> {
            try {
                HttpResponse<String> resp = send(
                    "/api/v1/eventlog/" + tenant.tenantId() + "/events?startTime="
                        + startTime + "&endTime=" + endTime + "&limit=" + Math.max(1, limit),
                    "GET", null);
                validate(resp, 200);
                return parseEntries(resp.body());
            } catch (Exception e) {
                throw wrap("readByTimeRange", tenant.tenantId(), e);
            }
        }, blockingExecutor));
    }

    @Override
    public Promise<List<EventEntry>> readByType(
        TenantContext tenant, String eventType, Offset from, int limit) {
        Objects.requireNonNull(tenant);
        Objects.requireNonNull(eventType, "eventType required");
        return Promise.ofCompletionStage(CompletableFuture.supplyAsync(() -> {
            try {
                HttpResponse<String> resp = send(
                    "/api/v1/eventlog/" + tenant.tenantId() + "/events?type=" + eventType
                        + "&from=" + from.value() + "&limit=" + Math.max(1, limit),
                    "GET", null);
                validate(resp, 200);
                return parseEntries(resp.body());
            } catch (Exception e) {
                throw wrap("readByType", tenant.tenantId(), e);
            }
        }, blockingExecutor));
    }

    @Override
    public Promise<Offset> getLatestOffset(TenantContext tenant) {
        Objects.requireNonNull(tenant);
        return Promise.ofCompletionStage(CompletableFuture.supplyAsync(() -> {
            try {
                HttpResponse<String> resp = send(
                    "/api/v1/eventlog/" + tenant.tenantId() + "/offset/latest", "GET", null);
                validate(resp, 200);
                return Offset.of(toLong(parse(resp.body()).getOrDefault("offset", 0L)));
            } catch (Exception e) {
                throw wrap("getLatestOffset", tenant.tenantId(), e);
            }
        }, blockingExecutor));
    }

    @Override
    public Promise<Offset> getEarliestOffset(TenantContext tenant) {
        Objects.requireNonNull(tenant);
        return Promise.ofCompletionStage(CompletableFuture.supplyAsync(() -> {
            try {
                HttpResponse<String> resp = send(
                    "/api/v1/eventlog/" + tenant.tenantId() + "/offset/earliest", "GET", null);
                validate(resp, 200);
                return Offset.of(toLong(parse(resp.body()).getOrDefault("offset", 0L)));
            } catch (Exception e) {
                throw wrap("getEarliestOffset", tenant.tenantId(), e);
            }
        }, blockingExecutor));
    }

    @Override
    public Promise<Subscription> tail(TenantContext tenant, Offset from, Consumer<EventEntry> handler) {
        Objects.requireNonNull(tenant);
        Objects.requireNonNull(handler, "handler required");

        AtomicBoolean cancelled = new AtomicBoolean(false);
        Offset[] cursor = {from};

        Thread.startVirtualThread(() -> {
            while (!cancelled.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    List<EventEntry> batch = read(tenant, cursor[0], 50).toCompletableFuture().get();
                    for (EventEntry e : batch) {
                        if (!cancelled.get()) handler.accept(e);
                    }
                    if (batch.isEmpty()) {
                        Thread.sleep(250);
                    } else {
                        cursor[0] = Offset.of(cursor[0].value() + batch.size());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    if (!cancelled.get()) {
                        LOG.warn("tail poll error for tenant={}", tenant.tenantId(), e);
                        try { Thread.sleep(1000); } catch (InterruptedException ie) { break; }
                    }
                }
            }
        });

        return Promise.of(new InternalSubscription(cancelled));
    }

    // --- Private helpers ---

    private HttpResponse<String> send(String path, String method, String body)
        throws java.io.IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json");

        if (!authToken.isBlank()) {
            builder.header("Authorization", "Bearer " + authToken);
        }

        HttpRequest.BodyPublisher publisher = (body != null)
            ? HttpRequest.BodyPublishers.ofString(body)
            : HttpRequest.BodyPublishers.noBody();

        HttpRequest request = switch (method) {
            case "POST"   -> builder.POST(publisher).build();
            case "PUT"    -> builder.PUT(publisher).build();
            case "DELETE" -> builder.DELETE().build();
            default       -> builder.GET().build();
        };

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void validate(HttpResponse<?> response, int... expected) {
        int status = response.statusCode();
        for (int e : expected) {
            if (status == e) return;
        }
        throw new DataCloudRemoteException(
            "Unexpected HTTP status " + status + " from Data-Cloud. Body: " + response.body());
    }

    private Map<String, Object> toBody(EventEntry entry) {
        Map<String, Object> body = new HashMap<>();
        body.put("eventId",      entry.eventId().toString());
        body.put("eventType",    entry.eventType());
        body.put("eventVersion", entry.eventVersion());
        body.put("timestamp",    entry.timestamp().toString());
        body.put("payload",      java.util.Base64.getEncoder().encodeToString(toBytes(entry.payload())));
        body.put("contentType",  entry.contentType());
        body.put("headers",      entry.headers());
        entry.idempotencyKey().ifPresent(k -> body.put("idempotencyKey", k));
        return body;
    }

    private Map<String, Object> parse(String json) throws JsonProcessingException {
        return mapper.readValue(json, new TypeReference<>() {});
    }

    @SuppressWarnings("unchecked")
    private List<EventEntry> parseEntries(String json) throws JsonProcessingException {
        Map<String, Object> body = parse(json);
        List<Map<String, Object>> rawEvents = (List<Map<String, Object>>) body.getOrDefault("events", List.of());
        List<EventEntry> entries = new ArrayList<>(rawEvents.size());
        for (Map<String, Object> raw : rawEvents) {
            entries.add(EventEntry.builder()
                .eventId(UUID.fromString((String) raw.get("eventId")))
                .eventType((String) raw.get("eventType"))
                .eventVersion((String) raw.getOrDefault("eventVersion", "1.0.0"))
                .timestamp(Instant.parse((String) raw.get("timestamp")))
                .payload(java.util.Base64.getDecoder().decode((String) raw.get("payload")))
                .contentType((String) raw.getOrDefault("contentType", "application/json"))
                .headers(raw.containsKey("headers") ? (Map<String, String>) raw.get("headers") : Map.of())
                .idempotencyKey((String) raw.get("idempotencyKey"))
                .build());
        }
        return entries;
    }

    private static long toLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) return Long.parseLong(s);
        return 0L;
    }

    private static String requiredEnv(Map<String, String> env, String key) {
        String value = env.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Required environment variable '" + key + "' is not set. "
                + "Set it before starting the application.");
        }
        return value;
    }

    private static RuntimeException wrap(String op, String tenantId, Exception cause) {
        return new DataCloudRemoteException(
            op + " failed for tenant=" + tenantId, cause);
    }

    /** Safely converts a {@link ByteBuffer} to a byte array, handling read-only buffers. */
    private static byte[] toBytes(ByteBuffer buf) {
        if (buf.hasArray() && buf.arrayOffset() == 0 && buf.limit() == buf.capacity()) {
            return buf.array();
        }
        ByteBuffer copy = buf.duplicate();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }

    private record InternalSubscription(AtomicBoolean cancelled) implements Subscription {
        @Override public void cancel() { cancelled.set(true); }
        @Override public boolean isCancelled() { return cancelled.get(); }
    }
}
