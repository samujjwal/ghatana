package com.ghatana.appplatform.recon.service;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import javax.sql.DataSource;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Ingests bank/CSD statements for reconciliation cross-check (D13-004).
 *              Supports CSV, MT940, and MT942 SWIFT formats plus direct API ingestion.
 *              Detects duplicates (by reference + date), quarantines invalid entries.
 *              Persists to statement_entries table.
 * @doc.layer   Domain — Reconciliation
 * @doc.pattern Strategy (format parser selection); duplicate detection via reference hash;
 *              quarantine for invalid entries rather than silent discard
 */
public class StatementIngestionService {

    public enum SourceFormat { CSV, MT940, MT942, API }

    public record StatementEntry(
        String entryId,
        String bankId,
        String accountNumber,
        LocalDate statementDate,
        String reference,
        String narrative,
        BigDecimal amount,
        String currency,
        String counterparty,
        SourceFormat sourceFormat,
        String rawLine,
        boolean isDuplicate,
        boolean isValid,
        String quarantineReason
    ) {}

    public record IngestionResult(
        int totalLines,
        int validEntries,
        int duplicateEntries,
        int quarantinedEntries,
        List<StatementEntry> entries
    ) {}

    private final DataSource dataSource;
    private final Executor executor;
    private final Counter ingestedCounter;
    private final Counter quarantinedCounter;

    public StatementIngestionService(DataSource dataSource, Executor executor, MeterRegistry registry) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.ingestedCounter   = Counter.builder("recon.statements.entries_ingested_total").register(registry);
        this.quarantinedCounter = Counter.builder("recon.statements.entries_quarantined_total").register(registry);
    }

    /**
     * Ingest a raw statement from string content. Format is detected at dispatch.
     */
    public Promise<IngestionResult> ingest(String bankId, String accountNumber,
                                            LocalDate statementDate, SourceFormat format,
                                            String rawContent) {
        return Promise.ofBlocking(executor, () -> {
            List<StatementEntry> parsed = switch (format) {
                case CSV    -> parseCsv(bankId, accountNumber, statementDate, rawContent);
                case MT940  -> parseMt940(bankId, accountNumber, statementDate, rawContent);
                case MT942  -> parseMt940(bankId, accountNumber, statementDate, rawContent);  // MT942 is intra-day, same format
                case API    -> parseApi(bankId, accountNumber, statementDate, rawContent);
            };

            int duplicates = 0, quarantined = 0, valid = 0;
            for (StatementEntry entry : parsed) {
                if (entry.isDuplicate()) { duplicates++; continue; }
                if (!entry.isValid()) { quarantined++; quarantinedCounter.increment(); }
                else { valid++; ingestedCounter.increment(); }
                persistEntry(entry);
            }

            return new IngestionResult(parsed.size(), valid, duplicates, quarantined, parsed);
        });
    }

    // --- CSV parser ---
    private List<StatementEntry> parseCsv(String bankId, String account,
                                           LocalDate date, String content) throws Exception {
        List<StatementEntry> entries = new ArrayList<>();
        String[] lines = content.split("\n");
        for (int i = 1; i < lines.length; i++) {  // skip header
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            String[] cols = line.split(",", -1);
            if (cols.length < 4) {
                entries.add(invalid(bankId, account, date, SourceFormat.CSV, line, "Too few columns"));
                continue;
            }
            try {
                BigDecimal amount = new BigDecimal(cols[2].trim());
                String reference = cols[0].trim();
                boolean dup = isDuplicate(bankId, reference, date);
                entries.add(new StatementEntry(UUID.randomUUID().toString(), bankId, account,
                    date, reference, cols[1].trim(), amount, cols[3].trim(),
                    cols.length > 4 ? cols[4].trim() : null, SourceFormat.CSV, line, dup, true, null));
            } catch (NumberFormatException e) {
                entries.add(invalid(bankId, account, date, SourceFormat.CSV, line, "Invalid amount: " + cols[2]));
            }
        }
        return entries;
    }

    // --- MT940/MT942 parser ---
    // Parses :61: transaction detail lines. Format: :61:VDATEDATE2CR/DR<amount>N<ref>
    private List<StatementEntry> parseMt940(String bankId, String account,
                                             LocalDate date, String content) throws Exception {
        List<StatementEntry> entries = new ArrayList<>();
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (!line.startsWith(":61:")) continue;
            try {
                // Example: :61:2306010601C1500000NCHGNONREF
                String body = line.substring(4);
                // Extract debit/credit indicator and amount
                int crIdx = body.indexOf('C');
                int drIdx = body.indexOf('D');
                int signIdx = (crIdx < drIdx || drIdx == -1) ? crIdx : drIdx;
                if (signIdx < 6) {
                    entries.add(invalid(bankId, account, date, SourceFormat.MT940, line, "No C/D indicator"));
                    continue;
                }
                char cdIndicator = body.charAt(signIdx);
                String amountStr = body.substring(signIdx + 1).split("[A-Z]")[0].replace(",", ".");
                BigDecimal amount = new BigDecimal(amountStr);
                if (cdIndicator == 'D') amount = amount.negate();
                String reference = body.length() > signIdx + amountStr.length() + 1
                    ? body.substring(signIdx + amountStr.length() + 1) : "NOREF";
                boolean dup = isDuplicate(bankId, reference, date);
                entries.add(new StatementEntry(UUID.randomUUID().toString(), bankId, account,
                    date, reference, null, amount, "NPR", null, SourceFormat.MT940, line, dup, true, null));
            } catch (Exception e) {
                entries.add(invalid(bankId, account, date, SourceFormat.MT940, line, "Parse error: " + e.getMessage()));
            }
        }
        return entries;
    }

    // --- API ingestion (JSON array already parsed into CSV-like lines) ---
    private List<StatementEntry> parseApi(String bankId, String account,
                                          LocalDate date, String content) throws Exception {
        // Expects simple pipe-delimited: reference|narrative|amount|currency|counterparty
        List<StatementEntry> entries = new ArrayList<>();
        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\|", -1);
            if (parts.length < 4) {
                entries.add(invalid(bankId, account, date, SourceFormat.API, line, "Too few pipe fields"));
                continue;
            }
            try {
                BigDecimal amount = new BigDecimal(parts[2].trim());
                String reference = parts[0].trim();
                boolean dup = isDuplicate(bankId, reference, date);
                entries.add(new StatementEntry(UUID.randomUUID().toString(), bankId, account,
                    date, reference, parts[1].trim(), amount, parts[3].trim(),
                    parts.length > 4 ? parts[4].trim() : null, SourceFormat.API, line, dup, true, null));
            } catch (NumberFormatException e) {
                entries.add(invalid(bankId, account, date, SourceFormat.API, line, "Invalid amount: " + parts[2]));
            }
        }
        return entries;
    }

    private StatementEntry invalid(String bankId, String account, LocalDate date,
                                    SourceFormat format, String rawLine, String reason) {
        return new StatementEntry(UUID.randomUUID().toString(), bankId, account, date,
            null, null, null, null, null, format, rawLine, false, false, reason);
    }

    private boolean isDuplicate(String bankId, String reference, LocalDate date) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT COUNT(*) FROM statement_entries " +
                 "WHERE bank_id = ? AND reference = ? AND stmt_date = ?")) {
            ps.setString(1, bankId);
            ps.setString(2, reference);
            ps.setObject(3, date);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getLong(1) > 0;
            }
        }
    }

    private void persistEntry(StatementEntry e) throws Exception {
        String sql = "INSERT INTO statement_entries(id, bank_id, account_number, stmt_date, reference, " +
                     "narrative, amount, currency, counterparty, source_format, raw_line, " +
                     "is_duplicate, is_valid, quarantine_reason) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(e.entryId()));
            ps.setString(2, e.bankId());
            ps.setString(3, e.accountNumber());
            ps.setObject(4, e.statementDate());
            ps.setString(5, e.reference());
            ps.setString(6, e.narrative());
            ps.setBigDecimal(7, e.amount());
            ps.setString(8, e.currency());
            ps.setString(9, e.counterparty());
            ps.setString(10, e.sourceFormat().name());
            ps.setString(11, e.rawLine());
            ps.setBoolean(12, e.isDuplicate());
            ps.setBoolean(13, e.isValid());
            ps.setString(14, e.quarantineReason());
            ps.executeUpdate();
        }
    }
}
