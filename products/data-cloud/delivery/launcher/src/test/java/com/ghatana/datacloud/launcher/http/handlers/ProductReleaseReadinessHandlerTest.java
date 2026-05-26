package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.application.ProductReleaseReadinessService;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@DisplayName("ProductReleaseReadinessHandler")
class ProductReleaseReadinessHandlerTest extends EventloopTestBase {

    private static final String TENANT_ID = "tenant-123";
    private static final String TARGET_COMMIT = "bdcee47c1e304454e7af848be60d981b24da1151";

    private ObjectMapper objectMapper;
    private FakeProductReleaseReadinessRepository repository;
    private ProductReleaseReadinessHandler handler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        HttpHandlerSupport http = new HttpHandlerSupport(
            objectMapper,
            "*",
            "GET,POST,PUT,DELETE,OPTIONS",
            "Content-Type,Authorization,X-Tenant-Id",
            false
        );
        repository = new FakeProductReleaseReadinessRepository();
        ProductReleaseReadinessService service = new ProductReleaseReadinessService(repository, new FakeMetricsCollector());
        handler = new ProductReleaseReadinessHandler(http, service);
    }

    @Test
    @DisplayName("produces tenant-scoped release readiness evidence")
    void producesReleaseReadiness() throws Exception {
        HttpRequest request = requestWithBody("/api/v1/release-readiness", validPayload(TENANT_ID));

        HttpResponse response = runPromise(() -> handler.handleProduceReleaseReadiness(request));

        assertThat(response.getCode()).isEqualTo(201);
        Map<String, Object> body = parseMap(response);
        assertThat(body.get("productId")).isEqualTo("phr");
        assertThat(body.get("tenantId")).isEqualTo(TENANT_ID);
        assertThat(body.get("commitSha")).isEqualTo(TARGET_COMMIT);
        assertThat(repository.lastUpserted).isNotNull();
        assertThat(repository.lastUpserted.tenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    @DisplayName("rejects payload tenant mismatch")
    void rejectsTenantMismatch() throws Exception {
        HttpRequest request = requestWithBody("/api/v1/release-readiness", Map.of(
            "productId", "phr",
            "productVersion", "1.0.0",
            "releaseTarget", "production",
            "releaseVerdict", "pass",
            "generatedAt", Instant.now().toString(),
            "tenantId", "other-tenant",
            "commitSha", TARGET_COMMIT,
            "evidenceEnvironment", "production"
        ));

        HttpResponse response = runPromise(() -> handler.handleProduceReleaseReadiness(request));

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(repository.lastUpserted).isNull();
    }

    @Test
    @DisplayName("lists release readiness records with cockpit filters")
    void listsReleaseReadinessWithFilters() throws Exception {
        repository.records = List.of(
            readiness("phr", "production", "pass"),
            readiness("dmos", "production", "fail"),
            readiness("phr", "staging", "fail")
        );
        HttpRequest request = RequestContextTestHelper.createTestRequest(TENANT_ID, null, "/api/v1/release-readiness");
        lenient().when(request.getQueryParameter("productId")).thenReturn("phr");
        lenient().when(request.getQueryParameter("productVersion")).thenReturn("1.0.0");
        lenient().when(request.getQueryParameter("releaseTarget")).thenReturn("production");
        lenient().when(request.getQueryParameter("releaseVerdict")).thenReturn(null);
        lenient().when(request.getQueryParameter("limit")).thenReturn("25");
        lenient().when(request.getQueryParameter("offset")).thenReturn("0");

        HttpResponse response = runPromise(() -> handler.handleListReleaseReadiness(request));

        assertThat(response.getCode()).isEqualTo(200);
        List<Map<String, Object>> body = parseList(response);
        assertThat(body).hasSize(1);
        assertThat(body.getFirst().get("productId")).isEqualTo("phr");
        assertThat(body.getFirst().get("releaseTarget")).isEqualTo("production");
    }

    @Test
    @DisplayName("summarizes release readiness statistics")
    void returnsReleaseReadinessStats() throws Exception {
        repository.records = List.of(
            readiness("phr", "production", "pass"),
            readiness("dmos", "production", "fail"),
            readiness("phr", "staging", "fail")
        );
        HttpRequest request = RequestContextTestHelper.createTestRequest(TENANT_ID, null, "/api/v1/release-readiness/stats");

        HttpResponse response = runPromise(() -> handler.handleReleaseReadinessStats(request));

        assertThat(response.getCode()).isEqualTo(200);
        Map<String, Object> body = parseMap(response);
        assertThat(body.get("totalReleases")).isEqualTo(3);
        assertThat(body.get("passedReleases")).isEqualTo(1);
        assertThat(body.get("failedReleases")).isEqualTo(2);
        assertThat(body).containsKey("byProduct");
        assertThat(body).containsKey("byTarget");
    }

    private HttpRequest requestWithBody(String path, Map<String, Object> body) throws Exception {
        return RequestContextTestHelper.createTestRequestWithBody(
            TENANT_ID,
            null,
            path,
            ByteBuf.wrapForReading(objectMapper.writeValueAsBytes(body))
        );
    }

    private Map<String, Object> validPayload(String tenantId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("productId", "phr");
        payload.put("productVersion", "1.0.0");
        payload.put("releaseTarget", "production");
        payload.put("releaseVerdict", "pass");
        payload.put("averageScore", 0.98);
        payload.put("releaseTargetScore", 0.96);
        payload.put("generatedAt", Instant.now().toString());
        payload.put("evidence", Map.of("source", "runtime-truth"));
        payload.put("blockingGaps", List.of());
        payload.put("belowTargetDimensions", List.of());
        payload.put("tenantId", tenantId);
        payload.put("commitSha", TARGET_COMMIT);
        payload.put("evidenceEnvironment", "production");
        return payload;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMap(HttpResponse response) throws Exception {
        String body = runPromise(() ->
            response.loadBody().map(buffer -> buffer.getString(StandardCharsets.UTF_8)));
        return objectMapper.readValue(body, Map.class);
    }

    private List<Map<String, Object>> parseList(HttpResponse response) throws Exception {
        String body = runPromise(() ->
            response.loadBody().map(buffer -> buffer.getString(StandardCharsets.UTF_8)));
        return objectMapper.readValue(body, new TypeReference<>() {});
    }

    private ProductReleaseReadinessService.ProductReleaseReadiness readiness(
            String productId,
            String releaseTarget,
            String verdict) {
        return ProductReleaseReadinessService.ProductReleaseReadiness.builder()
            .id(productId + "-1.0.0-" + releaseTarget)
            .productId(productId)
            .productVersion("1.0.0")
            .releaseTarget(releaseTarget)
            .releaseVerdict(verdict)
            .averageScore("pass".equals(verdict) ? 0.98 : 0.67)
            .releaseTargetScore("pass".equals(verdict) ? 0.96 : 0.61)
            .generatedAt(Instant.now())
            .evidence(Map.of("source", "runtime-truth"))
            .blockingGaps("pass".equals(verdict) ? List.of() : List.of(Map.of("id", "gap-1")))
            .belowTargetDimensions(List.of())
            .tenantId(TENANT_ID)
            .commitSha(TARGET_COMMIT)
            .evidenceEnvironment(releaseTarget)
            .build();
    }

    private static final class FakeProductReleaseReadinessRepository
            implements ProductReleaseReadinessService.ProductReleaseReadinessRepository {

        private ProductReleaseReadinessService.ProductReleaseReadiness lastUpserted;
        private List<ProductReleaseReadinessService.ProductReleaseReadiness> records = List.of();

        @Override
        public Promise<ProductReleaseReadinessService.ProductReleaseReadiness> upsert(
                ProductReleaseReadinessService.ProductReleaseReadiness readiness) {
            lastUpserted = readiness;
            records = List.of(readiness);
            return Promise.of(readiness);
        }

        @Override
        public Promise<Optional<ProductReleaseReadinessService.ProductReleaseReadiness>> findByProductVersionAndTarget(
                String productId, String productVersion, String releaseTarget, String tenantId) {
            return Promise.of(records.stream()
                .filter(record -> record.productId().equals(productId))
                .filter(record -> record.productVersion().equals(productVersion))
                .filter(record -> record.releaseTarget().equals(releaseTarget))
                .filter(record -> record.tenantId().equals(tenantId))
                .findFirst());
        }

        @Override
        public Promise<List<ProductReleaseReadinessService.ProductReleaseReadiness>> findByProductId(
                String productId,
                String tenantId) {
            return Promise.of(records.stream()
                .filter(record -> record.productId().equals(productId))
                .filter(record -> record.tenantId().equals(tenantId))
                .toList());
        }

        @Override
        public Promise<List<ProductReleaseReadinessService.ProductReleaseReadiness>> findByTenant(String tenantId) {
            return Promise.of(records.stream()
                .filter(record -> record.tenantId().equals(tenantId))
                .toList());
        }

        @Override
        public Promise<Void> deleteById(String id, String tenantId) {
            records = records.stream()
                .filter(record -> !(id.equals(record.id()) && tenantId.equals(record.tenantId())))
                .toList();
            return Promise.complete();
        }
    }

    private static final class FakeMetricsCollector implements MetricsCollector {
        private final Map<String, Integer> counters = new ConcurrentHashMap<>();

        @Override
        public void increment(String metricName, double amount, Map<String, String> tags) {
            counters.merge(metricName, (int) amount, Integer::sum);
        }

        @Override
        public void recordError(String metricName, Exception e, Map<String, String> tags) {
            counters.merge(metricName, 1, Integer::sum);
        }

        @Override
        public void incrementCounter(String metricName, String... keyValues) {
            counters.merge(metricName, 1, Integer::sum);
        }

        @Override
        public io.micrometer.core.instrument.MeterRegistry getMeterRegistry() {
            return null;
        }
    }
}
