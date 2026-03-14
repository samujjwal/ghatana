package com.ghatana.appplatform.audit.chain;

import com.ghatana.appplatform.audit.domain.AuditEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HashChainService}.
 *
 * <p>Pure CPU — no eventloop, no DB. Does not extend {@link com.ghatana.platform.testing.activej.EventloopTestBase}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for SHA-256 hash chain computation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("HashChainService — Unit Tests")
class HashChainServiceTest {

    private HashChainService service;

    private static final AuditEntry SAMPLE_ENTRY = AuditEntry.builder()
        .id("audit-001")
        .action("ORDER_PLACED")
        .actor(AuditEntry.Actor.of("user-42", "TRADER"))
        .resource(AuditEntry.Resource.of("Order", "order-123"))
        .outcome(AuditEntry.Outcome.SUCCESS)
        .tenantId("tenant-1")
        .timestampGregorian(Instant.parse("2026-03-13T10:00:00Z"))
        .build();

    @BeforeEach
    void setUp() {
        service = new HashChainService();
    }

    // =========================================================================
    // Hash computation determinism
    // =========================================================================

    @Test
    @DisplayName("hash_computation_deterministic — same inputs always produce same hash")
    void hashIsDeterministic() {
        String hash1 = service.computeHash(HashChainService.GENESIS_HASH, SAMPLE_ENTRY, 0L);
        String hash2 = service.computeHash(HashChainService.GENESIS_HASH, SAMPLE_ENTRY, 0L);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("hash_is_64_char_hex — output is valid SHA-256 hex string")
    void hashIs64CharHex() {
        String hash = service.computeHash(HashChainService.GENESIS_HASH, SAMPLE_ENTRY, 0L);

        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("hash_specialChars_canonical — special chars in action produce stable hash")
    void specialCharactersProduceStableHash() {
        AuditEntry entry1 = AuditEntry.builder()
            .id("a1").action("ORDER_SPECIAL_\"QUOTED\"").outcome(AuditEntry.Outcome.SUCCESS)
            .actor(AuditEntry.Actor.of("u", "r")).resource(AuditEntry.Resource.of("T","id"))
            .tenantId("t1").timestampGregorian(Instant.parse("2026-01-01T00:00:00Z")).build();

        String h1 = service.computeHash(HashChainService.GENESIS_HASH, entry1, 0L);
        String h2 = service.computeHash(HashChainService.GENESIS_HASH, entry1, 0L);

        assertThat(h1).isEqualTo(h2);
    }

    @Test
    @DisplayName("hash_differentPreviousHash — changing previous_hash changes current_hash")
    void differentPreviousHashChangesOutput() {
        String h1 = service.computeHash(HashChainService.GENESIS_HASH, SAMPLE_ENTRY, 0L);
        String h2 = service.computeHash("a".repeat(64), SAMPLE_ENTRY, 0L);

        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    @DisplayName("hash_differentSequenceNumber — changing seq changes current_hash")
    void differentSequenceChangesOutput() {
        String h1 = service.computeHash(HashChainService.GENESIS_HASH, SAMPLE_ENTRY, 0L);
        String h2 = service.computeHash(HashChainService.GENESIS_HASH, SAMPLE_ENTRY, 1L);

        assertThat(h1).isNotEqualTo(h2);
    }

    // =========================================================================
    // Chain verification
    // =========================================================================

    @Test
    @DisplayName("verify_validHash_returnsTrue — correct hash passes verification")
    void verifyValidHashReturnsTrue() {
        String hash = service.computeHash(HashChainService.GENESIS_HASH, SAMPLE_ENTRY, 0L);

        assertThat(service.verify(HashChainService.GENESIS_HASH, SAMPLE_ENTRY, 0L, hash)).isTrue();
    }

    @Test
    @DisplayName("verify_tamperedHash_returnsFalse — modified hash fails verification")
    void verifyTamperedHashReturnsFalse() {
        String tampered = "deadbeef".repeat(8); // 64 chars but wrong

        assertThat(service.verify(HashChainService.GENESIS_HASH, SAMPLE_ENTRY, 0L, tampered)).isFalse();
    }

    @Test
    @DisplayName("genesisHash_is_64_zeros — genesis constant is correct")
    void genesisHashIs64Zeros() {
        assertThat(HashChainService.GENESIS_HASH).hasSize(64);
        assertThat(HashChainService.GENESIS_HASH).matches("0{64}");
    }
}
