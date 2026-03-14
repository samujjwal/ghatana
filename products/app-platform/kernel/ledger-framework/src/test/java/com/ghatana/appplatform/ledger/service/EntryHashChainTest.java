/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.service;

import com.ghatana.appplatform.ledger.domain.JournalEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.RoundingMode;
import java.util.UUID;

import com.ghatana.appplatform.ledger.domain.Currency;
import com.ghatana.appplatform.ledger.domain.Direction;
import com.ghatana.appplatform.ledger.domain.MonetaryAmount;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EntryHashChain — SHA-256 tamper-detection chain")
class EntryHashChainTest {

    private static final Currency NPR = Currency.NPR;
    private static final UUID ACCOUNT_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    @DisplayName("genesis hash is deterministic for same entry")
    void compute_genesisHash_deterministic() {
        JournalEntry entry = JournalEntry.debit(ACCOUNT_A, MonetaryAmount.of("100", NPR), "debit");

        String hash1 = EntryHashChain.compute(EntryHashChain.GENESIS, entry, 1L);
        String hash2 = EntryHashChain.compute(EntryHashChain.GENESIS, entry, 1L);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64);  // SHA-256 hex = 64 chars
    }

    @Test
    @DisplayName("chain hash changes when sequence number changes")
    void compute_differentSequence_differentHash() {
        JournalEntry entry = JournalEntry.debit(ACCOUNT_A, MonetaryAmount.of("100", NPR), "d");

        String hash1 = EntryHashChain.compute(EntryHashChain.GENESIS, entry, 1L);
        String hash2 = EntryHashChain.compute(EntryHashChain.GENESIS, entry, 2L);

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("each next hash depends on the previous hash (chain property)")
    void compute_chainDependency() {
        JournalEntry entry1 = JournalEntry.debit(ACCOUNT_A, MonetaryAmount.of("100", NPR), "first");
        JournalEntry entry2 = JournalEntry.credit(ACCOUNT_A, MonetaryAmount.of("100", NPR), "second");

        String hash1 = EntryHashChain.compute(EntryHashChain.GENESIS, entry1, 1L);
        String hash2 = EntryHashChain.compute(hash1, entry2, 2L);

        // hash2 must depend on hash1
        String hash2alt = EntryHashChain.compute("differentseed", entry2, 2L);
        assertThat(hash2).isNotEqualTo(hash2alt);
    }

    @Test
    @DisplayName("verify returns true for matching computed hash")
    void verify_matchingHash_returnsTrue() {
        JournalEntry entry = JournalEntry.debit(ACCOUNT_A, MonetaryAmount.of("500", NPR), "verify test");
        String hash = EntryHashChain.compute(EntryHashChain.GENESIS, entry, 1L);

        boolean valid = EntryHashChain.verify(EntryHashChain.GENESIS, entry, 1L, hash);
        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("verify returns false when stored hash doesn't match recomputed")
    void verify_tamperedHash_returnsFalse() {
        JournalEntry entry = JournalEntry.debit(ACCOUNT_A, MonetaryAmount.of("500", NPR), "tamper test");

        boolean valid = EntryHashChain.verify(EntryHashChain.GENESIS, entry, 1L, "fakehash");
        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("verify returns false for null stored hash")
    void verify_nullStoredHash_returnsFalse() {
        JournalEntry entry = JournalEntry.debit(ACCOUNT_A, MonetaryAmount.of("100", NPR), "null hash");

        boolean valid = EntryHashChain.verify(EntryHashChain.GENESIS, entry, 1L, null);
        assertThat(valid).isFalse();
    }
}
