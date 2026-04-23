/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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

    private final AuditIntegrityService service = new AuditIntegrityService(); // GH-90000

    @Test
    @DisplayName("should compute consistent hash for same event")
    void shouldComputeConsistentHash() { // GH-90000
        AuditEventEntity event = createTestEvent("id-1", "tenant-1"); // GH-90000
        String hash1 = service.computeChainHash(event, null); // GH-90000
        String hash2 = service.computeChainHash(event, null); // GH-90000
        
        assertThat(hash1).isEqualTo(hash2); // GH-90000
    }

    @Test
    @DisplayName("should compute different hashes for different events")
    void shouldComputeDifferentHashesForDifferentEvents() { // GH-90000
        AuditEventEntity event1 = createTestEvent("id-1", "tenant-1"); // GH-90000
        AuditEventEntity event2 = createTestEvent("id-2", "tenant-1"); // GH-90000
        
        String hash1 = service.computeChainHash(event1, null); // GH-90000
        String hash2 = service.computeChainHash(event2, null); // GH-90000
        
        assertThat(hash1).isNotEqualTo(hash2); // GH-90000
    }

    @Test
    @DisplayName("should compute different hashes with different previous hash")
    void shouldComputeDifferentHashesWithDifferentPreviousHash() { // GH-90000
        AuditEventEntity event = createTestEvent("id-1", "tenant-1"); // GH-90000
        
        String hash1 = service.computeChainHash(event, null); // GH-90000
        String hash2 = service.computeChainHash(event, "previous-hash-1"); // GH-90000
        
        assertThat(hash1).isNotEqualTo(hash2); // GH-90000
    }

    @Test
    @DisplayName("should verify intact chain")
    void shouldVerifyIntactChain() { // GH-90000
        AuditEventEntity event1 = createTestEvent("id-1", "tenant-1"); // GH-90000
        String hash1 = service.computeChainHash(event1, null); // GH-90000
        event1 = createEntityWithHash(event1, null, hash1); // GH-90000
        
        AuditEventEntity event2 = createTestEvent("id-2", "tenant-1"); // GH-90000
        String hash2 = service.computeChainHash(event2, hash1); // GH-90000
        event2 = createEntityWithHash(event2, hash1, hash2); // GH-90000
        
        boolean isValid = service.verifyChainIntegrity(List.of(event1, event2)); // GH-90000
        
        assertThat(isValid).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("should detect broken chain")
    void shouldDetectBrokenChain() { // GH-90000
        AuditEventEntity event1 = createTestEvent("id-1", "tenant-1"); // GH-90000
        String hash1 = service.computeChainHash(event1, null); // GH-90000
        event1 = createEntityWithHash(event1, null, hash1); // GH-90000
        
        AuditEventEntity event2 = createTestEvent("id-2", "tenant-1"); // GH-90000
        String hash2 = service.computeChainHash(event2, hash1); // GH-90000
        // Tamper with the hash
        event2 = createEntityWithHash(event2, hash1, "tampered-hash"); // GH-90000
        
        boolean isValid = service.verifyChainIntegrity(List.of(event1, event2)); // GH-90000
        
        assertThat(isValid).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("should verify empty chain as valid")
    void shouldVerifyEmptyChainAsValid() { // GH-90000
        boolean isValid = service.verifyChainIntegrity(List.of()); // GH-90000
        assertThat(isValid).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("should verify single event chain")
    void shouldVerifySingleEventChain() { // GH-90000
        AuditEventEntity event1 = createTestEvent("id-1", "tenant-1"); // GH-90000
        String hash1 = service.computeChainHash(event1, null); // GH-90000
        event1 = createEntityWithHash(event1, null, hash1); // GH-90000
        
        boolean isValid = service.verifyChainIntegrity(List.of(event1)); // GH-90000
        
        assertThat(isValid).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("should verify single event hash")
    void shouldVerifySingleEventHash() { // GH-90000
        AuditEventEntity event = createTestEvent("id-1", "tenant-1"); // GH-90000
        String hash = service.computeChainHash(event, null); // GH-90000
        event = createEntityWithHash(event, null, hash); // GH-90000
        
        boolean isValid = service.verifyEventHash(event, null); // GH-90000
        
        assertThat(isValid).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("should detect tampered single event")
    void shouldDetectTamperedSingleEvent() { // GH-90000
        AuditEventEntity event = createTestEvent("id-1", "tenant-1"); // GH-90000
        String hash = service.computeChainHash(event, null); // GH-90000
        event = createEntityWithHash(event, null, "tampered-hash"); // GH-90000
        
        boolean isValid = service.verifyEventHash(event, null); // GH-90000
        
        assertThat(isValid).isFalse(); // GH-90000
    }

    private AuditEventEntity createTestEvent(String id, String tenantId) { // GH-90000
        return new AuditEventEntity( // GH-90000
            id, tenantId, "TEST_EVENT", "user-1",
            "resource-type", "resource-id", true,
            Instant.now(), "{}", null, null // GH-90000
        );
    }

    private AuditEventEntity createEntityWithHash(AuditEventEntity event, String previousHash, String chainHash) { // GH-90000
        return new AuditEventEntity( // GH-90000
            event.getId(), event.getTenantId(), event.getEventType(), // GH-90000
            event.getPrincipal(), event.getResourceType(), event.getResourceId(), // GH-90000
            event.getSuccess(), event.getTimestamp(), event.getDetailsJson(), // GH-90000
            previousHash, chainHash
        );
    }
}
