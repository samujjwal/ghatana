package com.ghatana.appplatform.recon.service;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import javax.sql.DataSource;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Extracts internal client balances from the K-16 ledger module for reconciliation (D13-002).
 *              Computes available + earmarked balances per client as-of a given date.
 *              Creates a SHA-256 hash of the balance snapshot for chain-of-custody integrity.
 * @doc.layer   Domain — Reconciliation
 * @doc.pattern Port-adapter: LedgerPort wraps K-16 ledger; immutable snapshot with hash
 */
public class InternalBalanceExtractionService {

    /** Port for K-16 Ledger Framework integration. */
    public interface LedgerPort {
        List<ClientBalance> fetchBalancesAsOf(LocalDate date);
    }

    public record ClientBalance(
        String clientId,
        String currency,
        BigDecimal availableBalance,
        BigDecimal earmarkedBalance
    ) {}

    public record BalanceSnapshot(
        String snapshotId,
        String reconRunId,
        LocalDate asOfDate,
        List<ClientBalance> balances,
        String snapshotHash,     // SHA-256 of serialised balance data
        Instant extractedAt
    ) {}

    private final LedgerPort ledgerPort;
    private final DataSource dataSource;
    private final Executor executor;
    private final Counter extractionsCounter;

    public InternalBalanceExtractionService(LedgerPort ledgerPort, DataSource dataSource,
                                             Executor executor, MeterRegistry registry) {
        this.ledgerPort = ledgerPort;
        this.dataSource = dataSource;
        this.executor = executor;
        this.extractionsCounter = Counter.builder("recon.balance_extractions_total").register(registry);
    }

    /**
     * Extract all client balances as-of reconDate, persist snapshot, and return it.
     * @param reconRunId the recon_run this snapshot belongs to
     */
    public Promise<BalanceSnapshot> extractAndSnapshot(String reconRunId, LocalDate reconDate) {
        return Promise.ofBlocking(executor, () -> {
            List<ClientBalance> balances = ledgerPort.fetchBalancesAsOf(reconDate);
            String hash = computeHash(balances);
            String snapshotId = UUID.randomUUID().toString();

            persistSnapshot(snapshotId, reconRunId, reconDate, balances, hash);
            extractionsCounter.increment();

            return new BalanceSnapshot(snapshotId, reconRunId, reconDate, balances, hash, Instant.now());
        });
    }

    private void persistSnapshot(String snapshotId, String reconRunId, LocalDate date,
                                  List<ClientBalance> balances, String hash) throws Exception {
        String sql = "INSERT INTO recon_balance_snapshots(id, run_id, client_id, currency, " +
                     "available_balance, earmarked_balance, total_balance, as_of_date) VALUES(?,?,?,?,?,?,?,?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (ClientBalance b : balances) {
                BigDecimal total = b.availableBalance().add(b.earmarkedBalance());
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, UUID.fromString(reconRunId));
                ps.setObject(3, UUID.fromString(b.clientId()));
                ps.setString(4, b.currency());
                ps.setBigDecimal(5, b.availableBalance());
                ps.setBigDecimal(6, b.earmarkedBalance());
                ps.setBigDecimal(7, total);
                ps.setObject(8, date);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        // Store hash on recon_runs row
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE recon_runs SET internal_balance_hash = ? WHERE id = ?")) {
            ps.setString(1, hash);
            ps.setObject(2, UUID.fromString(reconRunId));
            ps.executeUpdate();
        }
    }

    private String computeHash(List<ClientBalance> balances) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            // Sort for determinism before hashing
            balances.stream()
                .sorted(java.util.Comparator.comparing(ClientBalance::clientId)
                    .thenComparing(ClientBalance::currency))
                .forEach(b -> sb.append(b.clientId()).append(b.currency())
                    .append(b.availableBalance()).append(b.earmarkedBalance())
                    .append("|"));
            byte[] digest = md.digest(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte byt : digest) hex.append(String.format("%02x", byt));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
