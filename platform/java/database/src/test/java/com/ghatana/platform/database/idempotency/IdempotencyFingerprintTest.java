package com.ghatana.platform.database.idempotency;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose Verifies stable request fingerprint generation for idempotent mutations
 * @doc.layer platform
 * @doc.pattern Test
 */
class IdempotencyFingerprintTest {

    @Test
    void createsStableHashRegardlessOfMapInsertionOrder() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("transactionId", "txn-1");
        first.put("tenantId", "tenant-1");
        first.put("amount", 10.25d);

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("amount", 10.25d);
        second.put("tenantId", "tenant-1");
        second.put("transactionId", "txn-1");

        assertThat(IdempotencyFingerprint.sha256(first)).isEqualTo(IdempotencyFingerprint.sha256(second));
    }

    @Test
    void changesHashWhenFieldValueChanges() {
        String first = IdempotencyFingerprint.sha256(Map.of(
            "transactionId", "txn-1",
            "amount", 10.25d
        ));
        String second = IdempotencyFingerprint.sha256(Map.of(
            "transactionId", "txn-1",
            "amount", 20.50d
        ));

        assertThat(first).isNotEqualTo(second);
        assertThat(first).hasSize(64);
    }

    @Test
    void rejectsBlankCanonicalPayload() {
        assertThatThrownBy(() -> IdempotencyFingerprint.sha256(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("canonicalPayload must not be blank");
    }
}
