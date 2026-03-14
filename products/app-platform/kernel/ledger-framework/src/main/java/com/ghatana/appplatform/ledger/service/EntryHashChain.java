/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.service;

import com.ghatana.appplatform.ledger.domain.JournalEntry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * SHA-256 hash chain computation for ledger journal entries (K16-006).
 *
 * <p>Each entry carries a hash of the previous entry's hash concatenated with
 * its own data. This creates an immutable chain per account, where any
 * tampering with a single row breaks the chain for all subsequent rows.
 *
 * <p>Hash input format:
 * <pre>{@code
 * SHA-256(prev_hash + "|" + entry_id + "|" + account_id + "|" + direction + "|"
 *         + amount + "|" + currency + "|" + sequence_num)
 * }</pre>
 *
 * <p>For the genesis entry (first entry per account), {@code prev_hash} is the
 * string {@code "GENESIS"}.
 *
 * <p>Usage:
 * <pre>{@code
 * String hash = EntryHashChain.compute("GENESIS", genesis_entry, 1L);
 * String nextHash = EntryHashChain.compute(hash, next_entry, 2L);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose SHA-256 hash chain for ledger tamper detection (K16-006)
 * @doc.layer core
 * @doc.pattern Service
 */
public final class EntryHashChain {

    /** Placeholder prev_hash for the first entry in an account's chain. */
    public static final String GENESIS = "GENESIS";

    private EntryHashChain() {}

    /**
     * Computes the SHA-256 hash for a journal entry given its predecessor's hash.
     *
     * @param prevHash   hash of the previous entry in this account's chain,
     *                   or {@link #GENESIS} if this is the first entry
     * @param entry      entry to hash (entryId and accountId must be non-null)
     * @param sequenceNum monotonic sequence number for this account (1-based)
     * @return 64-character lowercase hex SHA-256 hash
     */
    public static String compute(String prevHash, JournalEntry entry, long sequenceNum) {
        Objects.requireNonNull(prevHash, "prevHash");
        Objects.requireNonNull(entry, "entry");

        String input = prevHash + "|"
                + entry.entryId() + "|"
                + entry.accountId() + "|"
                + entry.direction().name() + "|"
                + entry.amount().getAmount().toPlainString() + "|"
                + entry.amount().currencyCode() + "|"
                + sequenceNum;

        return sha256Hex(input);
    }

    /**
     * Verifies whether a chain entry's hash is consistent with its predecessor.
     *
     * @param prevHash       expected previous hash
     * @param entry          entry whose hash is being verified
     * @param sequenceNum    expected sequence number
     * @param expectedHash   the recorded hash to verify against
     * @return true if the computed hash matches expectedHash
     */
    public static boolean verify(String prevHash, JournalEntry entry,
                                 long sequenceNum, String expectedHash) {
        if (expectedHash == null) return false;
        String computed = compute(prevHash, entry, sequenceNum);
        return computed.equals(expectedHash);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
