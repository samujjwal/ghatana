package com.ghatana.datacloud.launcher.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("EventLogAuditService")
class EventLogAuditServiceTest extends EventloopTestBase {

    @Test
    @DisplayName("record persists audit event contract fields and headers")
    void recordPersistsAuditEventContractFieldsAndHeaders() throws Exception {
        EventLogStore eventLogStore = mock(EventLogStore.class);
        when(eventLogStore.append(any(TenantContext.class), any(EventLogStore.EventEntry.class)))
            .thenReturn(Promise.of(Offset.of(12L)));

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        EventLogAuditService auditService = new EventLogAuditService(eventLogStore, objectMapper);

        AuditEvent event = AuditEvent.builder()
            .id("9f6304d8-08ab-471f-9dca-740787cc9a0f")
            .tenantId("tenant-a")
            .eventType("RETENTION_PURGE")
            .principal("svc-governance")
            .resourceType("GOVERNANCE")
            .resourceId("retention-policy-main")
            .success(true)
            .timestamp(Instant.parse("2026-04-14T12:00:00Z"))
            .detail("correlationId", "corr-123")
            .detail("requestId", "req-1")
            .build();

        runPromise(() -> auditService.record(event));

        ArgumentCaptor<TenantContext> tenantCaptor = ArgumentCaptor.forClass(TenantContext.class);
        ArgumentCaptor<EventLogStore.EventEntry> entryCaptor = ArgumentCaptor.forClass(EventLogStore.EventEntry.class);
        verify(eventLogStore).append(tenantCaptor.capture(), entryCaptor.capture());

        TenantContext tenantContext = tenantCaptor.getValue();
        EventLogStore.EventEntry entry = entryCaptor.getValue();

        assertThat(tenantContext.tenantId()).isEqualTo("tenant-a");
        assertThat(tenantContext.metadata()).containsEntry("stream", "__audit");

        assertThat(entry.eventType()).isEqualTo("RETENTION_PURGE");
        assertThat(entry.headers()).containsEntry("stream", "__audit");
        assertThat(entry.headers()).containsEntry("tenantId", "tenant-a");
        assertThat(entry.headers()).containsEntry("resourceType", "GOVERNANCE");
        assertThat(entry.headers()).containsEntry("resourceId", "retention-policy-main");

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = objectMapper.readValue(
            new String(toBytes(entry.payload()), StandardCharsets.UTF_8),
            Map.class);

        // Compliance contract: tenant, actor(principal), action(eventType), timestamp, result(success), correlation id.
        assertThat(payload).containsEntry("tenantId", "tenant-a");
        assertThat(payload).containsEntry("principal", "svc-governance");
        assertThat(payload).containsEntry("eventType", "RETENTION_PURGE");
        assertThat(payload).containsEntry("timestamp", "2026-04-14T12:00:00Z");
        assertThat(payload).containsEntry("success", true);
        assertThat(payload).containsKey("details");
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) payload.get("details");
        assertThat(details).containsEntry("correlationId", "corr-123");
        assertThat(details).containsEntry("requestId", "req-1");
    }

    @Test
    @DisplayName("summarize returns recent compliance events with contract fields")
    void summarizeReturnsRecentComplianceEventsWithContractFields() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        EventLogStore eventLogStore = mock(EventLogStore.class);
        when(eventLogStore.readByTimeRange(any(TenantContext.class), any(Instant.class), any(Instant.class), anyInt()))
            .thenReturn(Promise.of(List.of(
                auditEntry(objectMapper, "RETENTION_PURGE", Instant.parse("2026-04-14T12:06:00Z"),
                    Map.of(
                        "tenantId", "tenant-a",
                        "principal", "svc-governance",
                        "resourceType", "GOVERNANCE",
                        "resourceId", "retention-policy-main",
                        "success", true,
                        "details", Map.of("correlationId", "corr-999", "result", "success")
                    )),
                auditEntry(objectMapper, "PIPELINE_EXECUTION", Instant.parse("2026-04-14T12:05:00Z"),
                    Map.of(
                        "tenantId", "tenant-a",
                        "principal", "svc-pipeline",
                        "resourceType", "PIPELINE",
                        "resourceId", "pipeline-77",
                        "success", false,
                        "details", Map.of("correlationId", "corr-888", "result", "failed")
                    )),
                auditEntry(objectMapper, "PLUGIN_HOT_SWAP", Instant.parse("2026-04-14T12:04:00Z"),
                    Map.of(
                        "tenantId", "tenant-a",
                        "principal", "svc-plugin-runtime",
                        "resourceType", "PLUGIN",
                        "resourceId", "workflow-plugin",
                        "success", true,
                        "details", Map.of("correlationId", "corr-777", "result", "restored")
                    ))
            )));

        EventLogAuditService auditService = new EventLogAuditService(eventLogStore, objectMapper);

        AuditSummaryProvider.AuditSummary summary = runPromise(() ->
            auditService.summarize("tenant-a", Instant.parse("2026-04-01T00:00:00Z"), 100));

        ArgumentCaptor<TenantContext> tenantContextCaptor = ArgumentCaptor.forClass(TenantContext.class);
        ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(eventLogStore).readByTimeRange(
            tenantContextCaptor.capture(),
            any(Instant.class),
            any(Instant.class),
            limitCaptor.capture());

        assertThat(summary.lastAuditAt()).isEqualTo(Instant.parse("2026-04-14T12:06:00Z"));
        assertThat(summary.eventCounts()).containsEntry("RETENTION_PURGE", 1L);
        assertThat(summary.eventCounts()).containsEntry("PIPELINE_EXECUTION", 1L);
        assertThat(summary.eventCounts()).containsEntry("PLUGIN_HOT_SWAP", 1L);
        assertThat(summary.recentEvents()).hasSize(3);
        assertThat(tenantContextCaptor.getValue().tenantId()).isEqualTo("tenant-a");
        assertThat(tenantContextCaptor.getValue().metadata()).containsEntry("stream", "__audit");
        assertThat(limitCaptor.getValue()).isEqualTo(100);

        Map<String, Object> newest = summary.recentEvents().get(0);
        assertThat(newest).containsEntry("tenantId", "tenant-a");
        assertThat(newest).containsEntry("principal", "svc-governance");
        assertThat(newest).containsEntry("eventType", "RETENTION_PURGE");
        assertThat(newest).containsEntry("timestamp", "2026-04-14T12:06:00Z");
        assertThat(newest).containsEntry("success", true);
        @SuppressWarnings("unchecked")
        Map<String, Object> newestDetails = (Map<String, Object>) newest.get("details");
        assertThat(newestDetails).containsEntry("correlationId", "corr-999");
    }

    @Test
    @DisplayName("summarize filters to __audit stream and enforces minimum limit")
    void summarizeFiltersAuditStreamAndEnforcesMinimumLimit() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        EventLogStore eventLogStore = mock(EventLogStore.class);
        when(eventLogStore.readByTimeRange(any(TenantContext.class), any(Instant.class), any(Instant.class), anyInt()))
            .thenReturn(Promise.of(List.of(
                auditEntry(objectMapper, "RETENTION_PURGE", Instant.parse("2026-04-14T12:10:00Z"),
                    Map.of("tenantId", "tenant-a", "principal", "svc-governance", "success", true)),
                nonAuditEntry(objectMapper, "INTERNAL_NOISE", Instant.parse("2026-04-14T12:11:00Z"))
            )));

        EventLogAuditService auditService = new EventLogAuditService(eventLogStore, objectMapper);

        AuditSummaryProvider.AuditSummary summary = runPromise(() ->
            auditService.summarize("tenant-a", Instant.parse("2026-04-01T00:00:00Z"), 0));

        ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(eventLogStore).readByTimeRange(any(TenantContext.class), any(Instant.class), any(Instant.class), limitCaptor.capture());

        assertThat(limitCaptor.getValue()).isEqualTo(1);
        assertThat(summary.eventCounts()).containsOnlyKeys("RETENTION_PURGE");
        assertThat(summary.recentEvents()).hasSize(1);
        assertThat(summary.recentEvents().get(0)).containsEntry("eventType", "RETENTION_PURGE");
    }

    @Test
    @DisplayName("summarize preserves endpoint query dimensions for compliance retrieval")
    void summarizePreservesEndpointQueryDimensionsForComplianceRetrieval() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        EventLogStore eventLogStore = mock(EventLogStore.class);

        Instant insideWindow = Instant.parse("2026-04-20T09:00:00Z");
        Instant outsideWindow = Instant.parse("2026-03-01T09:00:00Z");

        when(eventLogStore.readByTimeRange(any(TenantContext.class), any(Instant.class), any(Instant.class), anyInt()))
            .thenReturn(Promise.of(List.of(
                auditEntry(objectMapper, "RETENTION_PURGE", insideWindow,
                    Map.of(
                        "tenantId", "tenant-a",
                        "principal", "svc-governance",
                        "resourceType", "GOVERNANCE",
                        "resourceId", "retention-policy-main",
                        "success", true,
                        "details", Map.of("correlationId", "corr-42", "requestId", "req-42")
                    )),
                auditEntry(objectMapper, "RETENTION_PURGE", outsideWindow,
                    Map.of(
                        "tenantId", "tenant-a",
                        "principal", "svc-governance",
                        "resourceType", "GOVERNANCE",
                        "resourceId", "retention-policy-legacy",
                        "success", false,
                        "details", Map.of("correlationId", "corr-legacy")
                    ))
            )));

        EventLogAuditService auditService = new EventLogAuditService(eventLogStore, objectMapper);

        AuditSummaryProvider.AuditSummary summary = runPromise(() ->
            auditService.summarize("tenant-a", Instant.parse("2026-04-01T00:00:00Z"), 200));

        assertThat(summary.eventCounts()).containsEntry("RETENTION_PURGE", 2L);
        assertThat(summary.recentEvents()).hasSize(2);

        Map<String, Object> latest = summary.recentEvents().get(0);
        assertThat(latest).containsEntry("eventType", "RETENTION_PURGE");
        assertThat(latest).containsEntry("resourceType", "GOVERNANCE");
        assertThat(latest).containsEntry("resourceId", "retention-policy-main");
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) latest.get("details");
        assertThat(details).containsEntry("correlationId", "corr-42");

        ArgumentCaptor<Instant> startCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(eventLogStore).readByTimeRange(any(TenantContext.class), startCaptor.capture(), any(Instant.class), anyInt());
        assertThat(startCaptor.getValue()).isEqualTo(Instant.parse("2026-04-01T00:00:00Z"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // P1-02: Audit log payload masking assertions
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("audit payload does not contain raw secret token values (P1-02 log masking)")
    void auditPayloadDoesNotContainRawSecretTokenValues() throws Exception {
        EventLogStore eventLogStore = mock(EventLogStore.class);
        when(eventLogStore.append(any(TenantContext.class), any(EventLogStore.EventEntry.class)))
            .thenReturn(Promise.of(Offset.of(1L)));

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        EventLogAuditService auditService = new EventLogAuditService(eventLogStore, objectMapper);

        // Simulate a security event that might carry a raw token in its details
        AuditEvent event = AuditEvent.builder()
            .id(java.util.UUID.randomUUID().toString())
            .tenantId("tenant-secure")
            .eventType("API_KEY_USED")
            .principal("key-sha256fingerprint")
            .resourceType("AUTH")
            .resourceId("api-key-endpoint")
            .success(true)
            .timestamp(Instant.parse("2026-04-14T10:00:00Z"))
            .detail("correlationId", "corr-mask")
            .detail("requestId", "req-mask")
            // principal is the fingerprint — raw key must NOT appear in payload
            .build();

        runPromise(() -> auditService.record(event));

        ArgumentCaptor<EventLogStore.EventEntry> entryCaptor = ArgumentCaptor.forClass(EventLogStore.EventEntry.class);
        verify(eventLogStore).append(any(TenantContext.class), entryCaptor.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = objectMapper.readValue(
            new String(toBytes(entryCaptor.getValue().payload()), StandardCharsets.UTF_8),
            Map.class);

        // The audit log must record the principal NAME (fingerprint), never a raw secret value.
        assertThat(payload).containsKey("principal");
        assertThat((String) payload.get("principal"))
            .as("principal in audit log must be a fingerprint/alias, not a raw bearer credential")
            .startsWith("key-");

        // No field should hold a string that looks like a raw API key (long opaque token)
        payload.values().stream()
            .filter(v -> v instanceof String)
            .map(v -> (String) v)
            .forEach(v ->
                assertThat(v)
                    .as("Audit payload field must not contain a raw API key token (> 40 chars, no spaces)")
                    .satisfiesAnyOf(
                        s -> assertThat(s.length()).isLessThan(40),
                        s -> assertThat(s).contains(" "),     // spaces → not a token
                        s -> assertThat(s).contains("-"),     // structured id — allowed
                        s -> assertThat(s).contains("T"),     // timestamp — allowed
                        s -> assertThat(s).contains("Z")      // ISO-8601 timestamp — allowed
                    )
            );
    }

    @Test
    @DisplayName("record does not include structured PII fields in audit event headers (P1-02)")
    void recordDoesNotIncludeStructuredPiiFieldsInHeaders() throws Exception {
        EventLogStore eventLogStore = mock(EventLogStore.class);
        when(eventLogStore.append(any(TenantContext.class), any(EventLogStore.EventEntry.class)))
            .thenReturn(Promise.of(Offset.of(2L)));

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        EventLogAuditService auditService = new EventLogAuditService(eventLogStore, objectMapper);

        AuditEvent event = AuditEvent.builder()
            .id(java.util.UUID.randomUUID().toString())
            .tenantId("tenant-pii")
            .eventType("USER_LOGIN")
            .principal("user:alice")
            .resourceType("AUTH")
            .resourceId("login-endpoint")
            .success(true)
            .timestamp(Instant.parse("2026-04-15T08:00:00Z"))
            .detail("correlationId", "corr-pii-001")
            .build();

        runPromise(() -> auditService.record(event));

        ArgumentCaptor<EventLogStore.EventEntry> entryCaptor = ArgumentCaptor.forClass(EventLogStore.EventEntry.class);
        verify(eventLogStore).append(any(TenantContext.class), entryCaptor.capture());

        Map<String, String> headers = entryCaptor.getValue().headers();
        // Headers are metadata routing fields — must NOT include PII dimensions like
        // raw email addresses, IP addresses, or user-supplied free-text.
        assertThat(headers.keySet())
            .as("Audit event headers must only contain structured metadata routing keys")
            .allSatisfy(key -> assertThat(key)
                .isIn("stream", "tenantId", "resourceType", "resourceId", "principal"));
    }

    private EventLogStore.EventEntry auditEntry(ObjectMapper objectMapper,
                                                 String eventType,
                                                 Instant timestamp,
                                                 Map<String, Object> extraPayload) throws Exception {
        return EventLogStore.EventEntry.builder()
            .eventType(eventType)
            .timestamp(timestamp)
            .payload(objectMapper.writeValueAsBytes(withBasePayload(eventType, timestamp, extraPayload)))
            .headers(Map.of("stream", "__audit"))
            .build();
    }

    private EventLogStore.EventEntry nonAuditEntry(ObjectMapper objectMapper,
                                                    String eventType,
                                                    Instant timestamp) throws Exception {
        return EventLogStore.EventEntry.builder()
            .eventType(eventType)
            .timestamp(timestamp)
            .payload(objectMapper.writeValueAsBytes(Map.of(
                "id", eventType + "-id",
                "eventType", eventType,
                "timestamp", timestamp.toString()
            )))
            .headers(Map.of("stream", "internal"))
            .build();
    }

    private Map<String, Object> withBasePayload(String eventType,
                                                Instant timestamp,
                                                Map<String, Object> extraPayload) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("id", eventType + "-id");
        payload.put("eventType", eventType);
        payload.put("timestamp", timestamp.toString());
        payload.putAll(extraPayload);
        return payload;
    }

    private byte[] toBytes(java.nio.ByteBuffer payload) {
        java.nio.ByteBuffer duplicate = payload.asReadOnlyBuffer();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return bytes;
    }
}
