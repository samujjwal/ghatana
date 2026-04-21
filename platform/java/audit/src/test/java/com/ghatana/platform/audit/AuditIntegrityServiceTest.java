/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AuditIntegrityService}.
 *
 * @doc.type class
 * @doc.purpose Verify hash-chain integrity computation and verification
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Audit Integrity Service Tests")
class AuditIntegrityServiceTest {

    private final AuditIntegrityService service = new AuditIntegrityService();

    @Test
    @DisplayName("should compute consistent hash for same event")
    void shouldComputeConsistentHash() {
        AuditEventEntity event = createTestEvent("id-1", "tenant-1");
        String hash1 = service.computeChainHash(event, null);
        String hash2 = service.computeChainHash(event, null);
        
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("should compute different hashes for different events")
    void shouldComputeDifferentHashesForDifferentEvents() {
        AuditEventEntity event1 = createTestEvent("id-1", "tenant-1");
        AuditEventEntity event2 = createTestEvent("id-2", "tenant-1");
        
        String hash1 = service.computeChainHash(event1, null);
        String hash2 = service.computeChainHash(event2, null);
        
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("should compute different hashes with different previous hash")
    void shouldComputeDifferentHashesWithDifferentPreviousHash() {
        AuditEventEntity event = createTestEvent("id-1", "tenant-1");
        
        String hash1 = service.computeChainHash(event, null);
        String hash2 = service.computeChainHash(event, "previous-hash-1");
        
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("should verify intact chain")
    void shouldVerifyIntactChain() {
        AuditEventEntity event1 = createTestEvent("id-1", "tenant-1");
        String hash1 = service.computeChainHash(event1, null);
        event1 = createEntityWithHash(event1, null, hash1);
        
        AuditEventEntity event2 = createTestEvent("id-2", "tenant-1");
        String hash2 = service.computeChainHash(event2, hash1);
        event2 = createEntityWithHash(event2, hash1, hash2);
        
        boolean isValid = service.verifyChainIntegrity(List.of(event1, event2));
        
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("should detect broken chain")
    void shouldDetectBrokenChain() {
        AuditEventEntity event1 = createTestEvent("id-1", "tenant-1");
        String hash1 = service.computeChainHash(event1, null);
        event1 = createEntityWithHash(event1, null, hash1);
        
        AuditEventEntity event2 = createTestEvent("id-2", "tenant-1");
        String hash2 = service.computeChainHash(event2, hash1);
        // Tamper with the hash
        event2 = createEntityWithHash(event2, hash1, "tampered-hash");
        
        boolean isValid = service.verifyChainIntegrity(List.of(event1, event2));
        
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("should verify empty chain as valid")
    void shouldVerifyEmptyChainAsValid() {
        boolean isValid = service.verifyChainIntegrity(List.of());
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("should verify single event chain")
    void shouldVerifySingleEventChain() {
        AuditEventEntity event1 = createTestEvent("id-1", "tenant-1");
        String hash1 = service.computeChainHash(event1, null);
        event1 = createEntityWithHash(event1, null, hash1);
        
        boolean isValid = service.verifyChainIntegrity(List.of(event1));
        
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("should verify single event hash")
    void shouldVerifySingleEventHash() {
        AuditEventEntity event = createTestEvent("id-1", "tenant-1");
        String hash = service.computeChainHash(event, null);
        event = createEntityWithHash(event, null, hash);
        
        boolean isValid = service.verifyEventHash(event, null);
        
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("should detect tampered single event")
    void shouldDetectTamperedSingleEvent() {
        AuditEventEntity event = createTestEvent("id-1", "tenant-1");
        String hash = service.computeChainHash(event, null);
        event = createEntityWithHash(event, null, "tampered-hash");
        
        boolean isValid = service.verifyEventHash(event, null);
        
        assertThat(isValid).isFalse();
    }

    private AuditEventEntity createTestEvent(String id, String tenantId) {
        return new AuditEventEntity(
            id, tenantId, "TEST_EVENT", "user-1",
            "resource-type", "resource-id", true,
            Instant.now(), "{}", null, null
        );
    }

    private AuditEventEntity createEntityWithHash(AuditEventEntity event, String previousHash, String chainHash) {
        return new AuditEventEntity(
            event.getId(), event.getTenantId(), event.getEventType(),
            event.getPrincipal(), event.getResourceType(), event.getResourceId(),
            event.getSuccess(), event.getTimestamp(), event.getDetailsJson(),
            previousHash, chainHash
        );
    }
}
