package com.ghatana.appplatform.recon.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Transforms raw bank/custodian statement entries into a canonical normalized form.
 *              Handles date conversion (BS→AD via K-15 CalendarPort), currency code
 *              standardization, counterparty name normalization, and narrative cleansing.
 *              Invalid entries (zero amount, missing reference, date out of allowable range)
 *              are quarantined to statement_quarantine with a rejection reason rather than
 *              discarded, enabling manual review and reprocessing.
 * @doc.layer   Domain
 * @doc.pattern K-15 CalendarPort for BS↔AD date conversion; quarantine pattern for bad data;
 *              idempotency via ON CONFLICT(fetch_id, raw_line_number) DO NOTHING.
 */
public class StatementNormalizationService {

    private static final Logger log = LoggerFactory.getLogger(StatementNormalizationService.class);

    /** Reject entries with date more than this many days in the future. */
    private static final int MAX_FUTURE_DAYS = 5;
    /** Reject entries with date more than this many days in the past. */
    private static final int MAX_PAST_DAYS   = 730; // 2 years

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final CalendarPort     calendarPort;
    private final Counter          normalizedCounter;
    private final Counter          quarantinedCounter;

    public StatementNormalizationService(HikariDataSource dataSource, Executor executor,
                                         CalendarPort calendarPort, MeterRegistry registry) {
        this.dataSource        = dataSource;
        this.executor          = executor;
        this.calendarPort      = calendarPort;
        this.normalizedCounter = registry.counter("recon.normalization.normalized");
        this.quarantinedCounter = registry.counter("recon.normalization.quarantined");
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /**
     * K-15 CalendarPort for Bikram Sambat ↔ AD date conversion.
     */
    public interface CalendarPort {
        LocalDate bsToAd(int bsYear, int bsMonth, int bsDay);
        boolean   isHoliday(LocalDate adDate);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    /** Raw entry parsed directly from statement file (before normalization). */
    public record RawStatementEntry(
        String fetchId,
        int    lineNumber,
        String rawDate,          // Could be AD (yyyy-MM-dd) or BS (yyyy/MM/dd or YYYYMMDD-BS)
        String rawCurrency,
        String rawAmount,        // may have commas, parentheses for negatives
        String rawReference,
        String rawCounterparty,
        String rawNarrative
    ) {}

    /** Normalized canonical entry ready for matching. */
    public record NormalizedEntry(
        String    entryId,
        String    fetchId,
        int       lineNumber,
        LocalDate valueDateAd,
        String    currency,
        double    amount,        // signed: credit = +, debit = -
        String    reference,
        String    counterparty,
        String    narrative
    ) {}

    public record NormalizationSummary(
        int normalizedCount,
        int quarantinedCount
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Normalize all raw entries for a given fetchId.
     */
    public Promise<NormalizationSummary> normalizeAll(List<RawStatementEntry> entries) {
        return Promise.ofBlocking(executor, () -> {
            int normalized  = 0;
            int quarantined = 0;
            for (RawStatementEntry entry : entries) {
                if (normalizeEntry(entry)) {
                    normalized++;
                } else {
                    quarantined++;
                }
            }
            normalizedCounter.increment(normalized);
            quarantinedCounter.increment(quarantined);
            log.info("Normalization complete: normalized={} quarantined={}", normalized, quarantined);
            return new NormalizationSummary(normalized, quarantined);
        });
    }

    /**
     * Load raw entries from statement_fetch_log for a given fetchId and normalize.
     */
    public Promise<NormalizationSummary> normalizeForFetch(String fetchId) {
        return Promise.ofBlocking(executor, () -> {
            List<RawStatementEntry> raw = loadRawEntries(fetchId);
            int n = 0, q = 0;
            for (RawStatementEntry entry : raw) {
                if (normalizeEntry(entry)) n++; else q++;
            }
            normalizedCounter.increment(n);
            quarantinedCounter.increment(q);
            return new NormalizationSummary(n, q);
        });
    }

    // ─── Core logic ──────────────────────────────────────────────────────────

    private boolean normalizeEntry(RawStatementEntry raw) {
        try {
            // 1. Parse date
            LocalDate valueDateAd = parseDate(raw.rawDate());
            String dateError = validateDate(valueDateAd);
            if (dateError != null) {
                quarantine(raw, dateError);
                return false;
            }

            // 2. Parse amount
            double amount;
            try {
                amount = parseAmount(raw.rawAmount());
            } catch (NumberFormatException ex) {
                quarantine(raw, "Invalid amount: " + raw.rawAmount());
                return false;
            }
            if (amount == 0.0) {
                quarantine(raw, "Zero amount entry");
                return false;
            }

            // 3. Validate reference
            String reference = raw.rawReference() == null ? null : raw.rawReference().strip();
            if (reference == null || reference.isBlank()) {
                quarantine(raw, "Missing reference");
                return false;
            }

            // 4. Normalize currency code
            String currency = normalizeCurrency(raw.rawCurrency());

            // 5. Normalize counterparty
            String counterparty = normalizeCounterparty(raw.rawCounterparty());

            // 6. Clean narrative
            String narrative = cleanNarrative(raw.rawNarrative());

            NormalizedEntry entry = new NormalizedEntry(
                UUID.randomUUID().toString(),
                raw.fetchId(),
                raw.lineNumber(),
                valueDateAd,
                currency,
                amount,
                reference,
                counterparty,
                narrative
            );
            persistNormalized(entry);
            return true;

        } catch (Exception ex) {
            log.warn("Failed to normalize fetchId={} line={}", raw.fetchId(), raw.lineNumber(), ex);
            quarantine(raw, "Unexpected error: " + ex.getMessage());
            return false;
        }
    }

    // ─── Normalization helpers ────────────────────────────────────────────────

    private LocalDate parseDate(String rawDate) {
        if (rawDate == null) throw new IllegalArgumentException("Null date");
        String d = rawDate.strip();
        // BS format marked with -BS suffix (e.g. 2081/05/15-BS)
        if (d.endsWith("-BS")) {
            String[] parts = d.replace("-BS", "").split("[/\\-]");
            return calendarPort.bsToAd(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
            );
        }
        // Standard AD
        return LocalDate.parse(d.replace("/", "-"));
    }

    private String validateDate(LocalDate date) {
        LocalDate today = LocalDate.now();
        if (date.isAfter(today.plusDays(MAX_FUTURE_DAYS))) {
            return "Date too far in future: " + date;
        }
        if (date.isBefore(today.minusDays(MAX_PAST_DAYS))) {
            return "Date too far in past: " + date;
        }
        return null;
    }

    private double parseAmount(String rawAmount) {
        if (rawAmount == null) throw new NumberFormatException("null");
        String cleaned = rawAmount.strip()
            .replace(",", "")
            .replace(" ", "");
        boolean negative = cleaned.startsWith("(") || cleaned.startsWith("-");
        cleaned = cleaned.replace("(", "").replace(")", "").replace("-", "");
        double value = Double.parseDouble(cleaned);
        return negative ? -value : value;
    }

    private String normalizeCurrency(String raw) {
        if (raw == null) return "NPR";
        return switch (raw.strip().toUpperCase()) {
            case "NRS", "NR", "RS", "रु", "NPR" -> "NPR";
            case "USD", "US$", "$"              -> "USD";
            case "EUR", "€"                     -> "EUR";
            case "GBP", "£"                     -> "GBP";
            default -> raw.strip().toUpperCase();
        };
    }

    private String normalizeCounterparty(String raw) {
        if (raw == null || raw.isBlank()) return "UNKNOWN";
        return raw.strip().toUpperCase().replaceAll("[^A-Z0-9 ]", "");
    }

    private String cleanNarrative(String raw) {
        if (raw == null) return "";
        return raw.strip().replaceAll("\\s+", " ");
    }

    // ─── DB helpers ──────────────────────────────────────────────────────────

    private void persistNormalized(NormalizedEntry entry) throws SQLException {
        String sql = """
            INSERT INTO statement_entries (
                entry_id, fetch_id, line_number, value_date_ad, currency, amount,
                reference, counterparty, narrative, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (fetch_id, line_number) DO NOTHING
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.entryId());
            ps.setString(2, entry.fetchId());
            ps.setInt(3, entry.lineNumber());
            ps.setObject(4, entry.valueDateAd());
            ps.setString(5, entry.currency());
            ps.setDouble(6, entry.amount());
            ps.setString(7, entry.reference());
            ps.setString(8, entry.counterparty());
            ps.setString(9, entry.narrative());
            ps.executeUpdate();
        }
    }

    private void quarantine(RawStatementEntry raw, String reason) {
        String sql = """
            INSERT INTO statement_quarantine (
                quarantine_id, fetch_id, line_number, raw_date, raw_amount, raw_reference,
                rejection_reason, quarantined_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (fetch_id, line_number) DO NOTHING
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, raw.fetchId());
            ps.setInt(3, raw.lineNumber());
            ps.setString(4, raw.rawDate());
            ps.setString(5, raw.rawAmount());
            ps.setString(6, raw.rawReference());
            ps.setString(7, reason);
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.error("Failed to quarantine fetchId={} line={}", raw.fetchId(), raw.lineNumber(), ex);
        }
    }

    private List<RawStatementEntry> loadRawEntries(String fetchId) throws SQLException {
        // Raw entries are expected to be pre-parsed and stored in statement_raw_lines table
        String sql = """
            SELECT line_number, raw_date, raw_currency, raw_amount, raw_reference,
                   raw_counterparty, raw_narrative
            FROM statement_raw_lines
            WHERE fetch_id = ?
            ORDER BY line_number
            """;
        List<RawStatementEntry> entries = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fetchId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(new RawStatementEntry(
                        fetchId,
                        rs.getInt("line_number"),
                        rs.getString("raw_date"),
                        rs.getString("raw_currency"),
                        rs.getString("raw_amount"),
                        rs.getString("raw_reference"),
                        rs.getString("raw_counterparty"),
                        rs.getString("raw_narrative")
                    ));
                }
            }
        }
        return entries;
    }
}
