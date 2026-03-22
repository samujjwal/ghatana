/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.audit;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Hash-chained implementation of {@link AgentTraceLedger}.
 *
 * <p>Maintains an in-memory append-only ledger with SHA-256 hash chains per
 * tenant partition. Each appended event's hash is computed from its canonical
 * fields and linked to the previous event's hash.
 *
 * <p><b>Production note:</b> This in-memory implementation is suitable for
 * testing and single-node development. Production deployments should back this
 * with an append-only persistent store (e.g., EventCloud, PostgreSQL with
 * row-level immutability triggers, or a dedicated ledger service).
 *
 * @doc.type class
 * @doc.purpose In-memory hash-chained trace ledger implementation
 * @doc.layer agent-runtime
 * @doc.pattern Repository
 */
public class HashChainedTraceAppender implements AgentTraceLedger {

    private static final Logger log = LoggerFactory.getLogger(HashChainedTraceAppender.class);
    private static final String GENESIS_HASH = "";

    /** Tenant-partitioned ledger: tenantId → ordered event list. */
    private final Map<String, CopyOnWriteArrayList<TraceEvent>> partitions = new ConcurrentHashMap<>();

    /** Last hash per partition for chain validation on append. */
    private final Map<String, String> lastHashes = new ConcurrentHashMap<>();

    @Override
    @NotNull
    public Promise<Void> append(@NotNull TraceEvent event) {
        Objects.requireNonNull(event, "event");

        String partitionKey = event.tenantId();
        CopyOnWriteArrayList<TraceEvent> partition = partitions.computeIfAbsent(
                partitionKey, k -> new CopyOnWriteArrayList<>());

        String expectedPreviousHash = lastHashes.getOrDefault(partitionKey, GENESIS_HASH);
        if (!event.previousHash().equals(expectedPreviousHash)) {
            String msg = String.format(
                    "Hash chain broken for tenant=%s: expected previousHash='%s' but got '%s'",
                    partitionKey, expectedPreviousHash, event.previousHash());
            log.error(msg);
            return Promise.ofException(new IllegalStateException(msg));
        }

        // Verify event hash integrity
        String computed = computeHash(event);
        if (!computed.equals(event.eventHash())) {
            String msg = String.format(
                    "Event hash mismatch for eventId=%s: computed='%s' but declared='%s'",
                    event.eventId(), computed, event.eventHash());
            log.error(msg);
            return Promise.ofException(new IllegalStateException(msg));
        }

        partition.add(event);
        lastHashes.put(partitionKey, event.eventHash());

        log.debug("Appended trace event: type={} agent={} tenant={} seq={}",
                event.eventType(), event.agentId(), event.tenantId(), event.sequenceNumber());

        return Promise.complete();
    }

    @Override
    @NotNull
    public Promise<List<TraceEvent>> getByTrace(@NotNull String traceId, @NotNull String tenantId) {
        Objects.requireNonNull(traceId, "traceId");
        Objects.requireNonNull(tenantId, "tenantId");

        List<TraceEvent> partition = partitions.getOrDefault(tenantId, new CopyOnWriteArrayList<>());
        List<TraceEvent> result = partition.stream()
                .filter(e -> e.traceId().equals(traceId))
                .sorted(Comparator.comparingLong(TraceEvent::sequenceNumber))
                .toList();

        return Promise.of(result);
    }

    @Override
    @NotNull
    public Promise<List<TraceEvent>> getByAgent(
            @NotNull String agentId,
            @NotNull String tenantId,
            @Nullable Instant from,
            @Nullable Instant to,
            int limit) {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(tenantId, "tenantId");

        List<TraceEvent> partition = partitions.getOrDefault(tenantId, new CopyOnWriteArrayList<>());
        return Promise.of(partition.stream()
                .filter(e -> e.agentId().equals(agentId))
                .filter(e -> from == null || !e.timestamp().isBefore(from))
                .filter(e -> to == null || e.timestamp().isBefore(to))
                .sorted(Comparator.comparingLong(TraceEvent::sequenceNumber))
                .limit(limit)
                .toList());
    }

    @Override
    @NotNull
    public Promise<List<TraceEvent>> getByType(
            @NotNull TraceEventType eventType,
            @NotNull String tenantId,
            @Nullable Instant from,
            @Nullable Instant to,
            int limit) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(tenantId, "tenantId");

        List<TraceEvent> partition = partitions.getOrDefault(tenantId, new CopyOnWriteArrayList<>());
        return Promise.of(partition.stream()
                .filter(e -> e.eventType() == eventType)
                .filter(e -> from == null || !e.timestamp().isBefore(from))
                .filter(e -> to == null || e.timestamp().isBefore(to))
                .sorted(Comparator.comparingLong(TraceEvent::sequenceNumber))
                .limit(limit)
                .toList());
    }

    @Override
    public boolean verifyChain(@NotNull List<TraceEvent> events) {
        if (events.isEmpty()) return true;

        for (int i = 1; i < events.size(); i++) {
            TraceEvent prev = events.get(i - 1);
            TraceEvent curr = events.get(i);
            if (!curr.previousHash().equals(prev.eventHash())) {
                log.warn("Chain break at seq={}: expected previousHash='{}' got '{}'",
                        curr.sequenceNumber(), prev.eventHash(), curr.previousHash());
                return false;
            }
            // Verify each event's own hash
            String computed = computeHash(curr);
            if (!computed.equals(curr.eventHash())) {
                log.warn("Hash mismatch at seq={}: computed='{}' declared='{}'",
                        curr.sequenceNumber(), computed, curr.eventHash());
                return false;
            }
        }
        return true;
    }

    // =========================================================================
    // Hash computation
    // =========================================================================

    /**
     * Computes the SHA-256 hash of a trace event's canonical fields.
     *
     * @param event the trace event
     * @return hex-encoded SHA-256 hash
     */
    @NotNull
    public static String computeHash(@NotNull TraceEvent event) {
        String canonical = String.join("|",
                event.eventId(),
                event.traceId(),
                String.valueOf(event.sequenceNumber()),
                event.eventType().name(),
                event.agentId(),
                event.tenantId(),
                event.previousHash(),
                event.summary(),
                event.timestamp().toString());

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the Java spec
            throw new AssertionError("SHA-256 not available", e);
        }
    }

    /**
     * Returns the last hash in the chain for a given tenant partition.
     * Useful for building the next event's {@code previousHash} field.
     *
     * @param tenantId tenant identifier
     * @return last hash in the partition, or empty string for genesis
     */
    @NotNull
    public String getLastHash(@NotNull String tenantId) {
        return lastHashes.getOrDefault(tenantId, GENESIS_HASH);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
