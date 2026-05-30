package com.ghatana.phr.kernel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.context.KernelTenantContext;
import com.ghatana.phr.kernel.evidence.PhrEvidenceOutbox;
import com.ghatana.phr.kernel.event.PhrConsentEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for ConsentExpiryScanner.
 *
 * @doc.type class
 * @doc.purpose Unit tests for consent expiry scanning and evidence generation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ConsentExpiryScanner Tests")
class ConsentExpiryScannerTest {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private KernelContext mockContext;
    private PhrEvidenceOutbox mockEvidenceOutbox;
    private ConsentExpiryScanner scanner;

    @BeforeEach
    void setUp() {
        mockContext = mock(KernelContext.class);
        when(mockContext.getTenantContext()).thenReturn(new KernelTenantContext(
            "test-tenant",
            KernelTenantContext.TenantType.STANDARD,
            Map.of(),
            java.util.Set.of(),
            null,
            Runnable::run
        ));
        when(mockContext.getEnvironment()).thenReturn("test");

        mockEvidenceOutbox = mock(PhrEvidenceOutbox.class);

        scanner = new ConsentExpiryScanner(mockContext, mockEvidenceOutbox);
    }

    @Test
    @DisplayName("Should return zero results when no expired grants exist")
    void shouldReturnZeroResultsWhenNoExpiredGrants() {
        assertNotNull(scanner);
        assertEquals("phr-consent-expiry-scanner", scanner.getName());
        verifyNoInteractions(mockEvidenceOutbox);
    }

    @Test
    @DisplayName("ExpiryScanResult should correctly report zero expired grants")
    void expiryScanResultShouldReportZeroExpiredGrants() {
        ConsentExpiryScanner.ExpiryScanResult result =
            new ConsentExpiryScanner.ExpiryScanResult(0, 0, 0);

        assertEquals(0, result.expiredCount());
        assertEquals(0, result.evidencePublished());
        assertEquals(0, result.cacheInvalidated());
        assertFalse(result.hasExpiredGrants());
    }

    @Test
    @DisplayName("ExpiryScanResult should correctly report expired grants")
    void expiryScanResultShouldReportExpiredGrants() {
        ConsentExpiryScanner.ExpiryScanResult result =
            new ConsentExpiryScanner.ExpiryScanResult(5, 5, 5);

        assertEquals(5, result.expiredCount());
        assertEquals(5, result.evidencePublished());
        assertEquals(5, result.cacheInvalidated());
        assertTrue(result.hasExpiredGrants());
    }

    @Test
    @DisplayName("ExpiryScanResult should handle partial failures")
    void expiryScanResultShouldHandlePartialFailures() {
        ConsentExpiryScanner.ExpiryScanResult result =
            new ConsentExpiryScanner.ExpiryScanResult(10, 8, 8);

        assertEquals(10, result.expiredCount());
        assertEquals(8, result.evidencePublished());
        assertEquals(8, result.cacheInvalidated());
        assertTrue(result.hasExpiredGrants());
    }

    @Test
    @DisplayName("Should serialize consent expiry evidence as escaped JSON")
    void shouldSerializeConsentExpiryEvidenceAsEscapedJson() throws Exception {
        PhrConsentEvent event = PhrConsentEvent.builder()
            .eventId("event-1")
            .consentType("delegated-access")
            .action("expired")
            .patientId("patient-\"quoted\"")
            .recipientId("provider-1")
            .resourceType("Observation")
            .purpose("consent-expiry")
            .expiresAt(Instant.parse("2026-05-01T00:00:00Z"))
            .tenantId("test-tenant")
            .metadata(Map.of("grantId", "grant-1", "reason", "expired by clock"))
            .timestamp(Instant.parse("2026-05-02T00:00:00Z"))
            .correlationId("corr-1")
            .build();

        JsonNode payload = JSON.readTree(scanner.serializeEvent(event));

        assertEquals("event-1", payload.get("eventId").asText());
        assertEquals("delegated-access", payload.get("consentType").asText());
        assertEquals("patient-\"quoted\"", payload.get("patientId").asText());
        assertEquals("grant-1", payload.get("metadata").get("grantId").asText());
        assertEquals("corr-1", payload.get("correlationId").asText());
    }
}
