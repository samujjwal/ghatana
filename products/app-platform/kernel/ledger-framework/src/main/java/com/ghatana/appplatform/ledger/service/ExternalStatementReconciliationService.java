/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.service;

import com.ghatana.appplatform.ledger.domain.Currency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

// Well-known currency lookup (covers the static constants defined in Currency)

/**
 * External statement reconciliation service (STORY-K16-015).
 *
 * <h2>Supported import formats</h2>
 * <ul>
 *   <li><b>CSV</b> — comma-separated with header: {@code lineId,amount,currency,date,reference,description}</li>
 *   <li><b>MT940</b> — ISO 15022 SWIFT statement message; the {@code :61:} tag is parsed for
 *       transaction lines.</li>
 * </ul>
 *
 * <h2>Matching engine</h2>
 * <p>Statement lines are matched against internal {@link LedgerEntry} objects using a
 * three-criteria approach:
 * <ol>
 *   <li><b>Amount</b> — must be equal (within scale).</li>
 *   <li><b>Value date</b> — must be within ±1 calendar day of the ledger entry date.</li>
 *   <li><b>Reference</b> — if both statement line and ledger entry carry a reference, they
 *       must match (case-insensitive). If either side has no reference, the date+amount match
 *       is sufficient.</li>
 * </ol>
 *
 * <h2>Output</h2>
 * <ul>
 *   <li>Each statement line is tagged {@link MatchStatus#MATCHED} or {@link MatchStatus#UNMATCHED}.</li>
 *   <li>Ledger entries not matched to any statement line are flagged as unmatched.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose External bank/depository statement reconciliation with CSV and MT940 import (K16-015)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class ExternalStatementReconciliationService {

    private static final Map<String, Currency> KNOWN_CURRENCIES = Map.of(
            "NPR", Currency.NPR,
            "USD", Currency.USD,
            "BTC", Currency.BTC,
            "JPY", Currency.JPY
    );

    private static Currency lookupCurrency(String code) {
        Currency c = KNOWN_CURRENCIES.get(code.toUpperCase());
        if (c == null) throw new IllegalArgumentException("Unknown currency code: " + code);
        return c;
    }

    // ── Match status ──────────────────────────────────────────────────────────

    /** Matching outcome for a single statement line or ledger entry. */
    public enum MatchStatus {
        /** The item was matched to a counterpart in the other set. */
        MATCHED,
        /** No counterpart was found; the item requires investigation. */
        UNMATCHED
    }

    // ── Domain types ──────────────────────────────────────────────────────────

    /**
     * A single line in an imported bank or depository statement.
     *
     * @param lineId      unique identifier for this line (generated on import if absent)
     * @param amount      transaction amount (positive value; signum carried by the side convention)
     * @param currency    ISO 4217 currency
     * @param valueDate   booking/value date reported by the external source
     * @param reference   external reference or payment ID (may be null)
     * @param description free-text narrative (may be null)
     */
    public record StatementLine(
            String lineId,
            BigDecimal amount,
            Currency currency,
            LocalDate valueDate,
            String reference,
            String description
    ) {
        public StatementLine {
            Objects.requireNonNull(lineId, "lineId");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(valueDate, "valueDate");
        }
    }

    /**
     * A ledger entry used as a matching target during external reconciliation.
     *
     * @param entryId   internal ledger entry identifier
     * @param accountId ledger account
     * @param currency  currency of the entry
     * @param amount    absolute entry amount
     * @param valueDate posting date in the ledger
     * @param reference internal reference (may be null)
     */
    public record LedgerEntry(
            UUID entryId,
            UUID accountId,
            Currency currency,
            BigDecimal amount,
            LocalDate valueDate,
            String reference
    ) {
        public LedgerEntry {
            Objects.requireNonNull(entryId, "entryId");
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(valueDate, "valueDate");
        }
    }

    /**
     * Match result for a single statement line.
     *
     * @param line              the statement line being matched
     * @param status            match outcome
     * @param matchedEntryId    the ledger entry UUID if matched; {@code null} when unmatched
     */
    public record StatementMatchResult(
            StatementLine line,
            MatchStatus status,
            UUID matchedEntryId
    ) {
        public StatementMatchResult {
            Objects.requireNonNull(line, "line");
            Objects.requireNonNull(status, "status");
        }
    }

    /**
     * Full external reconciliation report.
     *
     * @param matchResults           per-line match outcomes for all imported statement lines
     * @param unmatchedLedgerEntries ledger entries that had no counterpart in the statement
     */
    public record ReconciliationReport(
            List<StatementMatchResult> matchResults,
            List<LedgerEntry> unmatchedLedgerEntries
    ) {
        public ReconciliationReport {
            matchResults = List.copyOf(matchResults);
            unmatchedLedgerEntries = List.copyOf(unmatchedLedgerEntries);
        }

        /** Number of matched statement lines. */
        public long matchedCount() {
            return matchResults.stream().filter(r -> r.status() == MatchStatus.MATCHED).count();
        }

        /** Number of unmatched statement lines. */
        public long unmatchedStatementCount() {
            return matchResults.stream().filter(r -> r.status() == MatchStatus.UNMATCHED).count();
        }
    }

    // ── CSV import ────────────────────────────────────────────────────────────

    /**
     * Parses a CSV statement.
     *
     * <p>Expected header (case-insensitive): {@code lineId,amount,currency,date,reference,description}
     * <ul>
     *   <li>{@code date} — ISO-8601 local date (yyyy-MM-dd)</li>
     *   <li>{@code lineId} — may be empty; an auto-generated ID will be assigned</li>
     *   <li>{@code reference} and {@code description} — may be empty</li>
     * </ul>
     *
     * @param csv raw CSV text (first line must be the header)
     * @return parsed statement lines; never null
     * @throws IllegalArgumentException if the header is missing or a required field is absent
     */
    public List<StatementLine> parseCsv(String csv) {
        Objects.requireNonNull(csv, "csv");
        String[] rawLines = csv.split("\r?\n");
        if (rawLines.length < 1) {
            throw new IllegalArgumentException("CSV must have at least a header row");
        }

        // Parse header
        String[] headers = splitCsv(rawLines[0]);
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            index.put(headers[i].trim().toLowerCase(), i);
        }
        requireColumn(index, "amount");
        requireColumn(index, "currency");
        requireColumn(index, "date");

        List<StatementLine> lines = new ArrayList<>();
        for (int row = 1; row < rawLines.length; row++) {
            String rawLine = rawLines[row].trim();
            if (rawLine.isEmpty()) continue;

            String[] cols = splitCsv(rawLine);
            String lineId = col(cols, index, "lineid");
            if (lineId == null || lineId.isBlank()) {
                lineId = UUID.randomUUID().toString();
            }
            BigDecimal amount = new BigDecimal(col(cols, index, "amount").replace(",", "."));
            Currency currency = lookupCurrency(col(cols, index, "currency").trim());
            LocalDate date = LocalDate.parse(col(cols, index, "date").trim(),
                    DateTimeFormatter.ISO_LOCAL_DATE);
            String reference = col(cols, index, "reference");
            String description = col(cols, index, "description");

            lines.add(new StatementLine(lineId, amount, currency, date,
                    blankToNull(reference), blankToNull(description)));
        }
        return Collections.unmodifiableList(lines);
    }

    // ── MT940 import ──────────────────────────────────────────────────────────

    /**
     * Parses a simplified MT940 SWIFT statement.
     *
     * <p>Only the {@code :61:} tag (transaction lines) is extracted. The expected format
     * for each {@code :61:} line is:
     * <pre>
     * :61:YYMMDDMMDDCD&lt;AMOUNT&gt;//&lt;REFERENCE&gt;
     * </pre>
     * Where:
     * <ul>
     *   <li>{@code YYMMDD} — value date (first 6 chars after {@code :61:})</li>
     *   <li>{@code MMDD} — entry date (next 4 chars; ignored, value date used)</li>
     *   <li>{@code CD} — credit/debit indicator ({@code CR} or {@code DR})</li>
     *   <li>{@code AMOUNT} — decimal amount (comma as decimal separator)</li>
     *   <li>{@code REFERENCE} — optional reference after {@code //}</li>
     * </ul>
     *
     * <p>The currency is extracted from the {@code :60F:} or {@code :60M:} opening balance tag
     * (e.g. {@code :60F:C250315NPR5000,00}). If not found, {@code NPR} is used as default.
     *
     * @param mt940     raw MT940 message text
     * @param accountId account identifier to associate with parsed entries (informational)
     * @return parsed statement lines; never null
     */
    public List<StatementLine> parseMt940(String mt940, UUID accountId) {
        Objects.requireNonNull(mt940, "mt940");
        Objects.requireNonNull(accountId, "accountId");

        // Extract statement currency from :60F: / :60M: (e.g. ":60F:C250315NPR5000,00")
        Currency statementCurrency = extractMt940Currency(mt940);

        List<StatementLine> lines = new ArrayList<>();
        String[] rawLines = mt940.split("\r?\n");
        for (String raw : rawLines) {
            String line = raw.trim();
            if (!line.startsWith(":61:")) continue;

            // :61:YYMMDDMMDDCRAMOUNT//REFERENCE
            String body = line.substring(4); // after ":61:"
            if (body.length() < 11) continue; // minimum: YYMMDD + MMDD + CR + digit

            String valueDateStr = body.substring(0, 6); // YYMMDD
            // entry date = next 4 chars (MMDD), skip
            String cdStr = body.substring(10, 12);       // CR or DR

            int refSep = body.indexOf("//");
            String amountStr;
            String reference = null;
            if (refSep > 0) {
                amountStr = body.substring(12, refSep);
                reference = body.substring(refSep + 2).trim();
                if (reference.isBlank()) reference = null;
            } else {
                amountStr = body.substring(12).replaceAll("[^0-9,.]", "").trim();
            }

            if (amountStr.isBlank()) continue;

            BigDecimal amount = new BigDecimal(amountStr.replace(",", "."));
            LocalDate valueDate = LocalDate.parse("20" + valueDateStr,
                    DateTimeFormatter.ofPattern("yyyyMMdd"));

            lines.add(new StatementLine(
                    UUID.randomUUID().toString(),
                    amount,
                    statementCurrency,
                    valueDate,
                    blankToNull(reference),
                    cdStr.equalsIgnoreCase("CR") ? "CREDIT" : "DEBIT"
            ));
        }
        return Collections.unmodifiableList(lines);
    }

    // ── Matching engine ───────────────────────────────────────────────────────

    /**
     * Reconciles imported statement lines against internal ledger entries.
     *
     * <p>Matching criteria (all must hold):
     * <ol>
     *   <li>Amount equality (exact, scale-insensitive).</li>
     *   <li>Value date within ±1 calendar day.</li>
     *   <li>Reference match (case-insensitive) <i>when both sides have a reference</i>.</li>
     * </ol>
     *
     * <p>Each ledger entry is matched at most once (consumed on first match).
     *
     * @param statementLines imported statement lines
     * @param ledgerEntries  internal ledger entries for the same account and date range
     * @return full reconciliation report with per-line results and unmatched ledger entries
     */
    public ReconciliationReport reconcile(List<StatementLine> statementLines,
                                          List<LedgerEntry> ledgerEntries) {
        Objects.requireNonNull(statementLines, "statementLines");
        Objects.requireNonNull(ledgerEntries, "ledgerEntries");

        // Track which ledger entries have been consumed
        List<LedgerEntry> remaining = new ArrayList<>(ledgerEntries);
        List<StatementMatchResult> results = new ArrayList<>(statementLines.size());

        for (StatementLine stmtLine : statementLines) {
            LedgerEntry matched = findMatch(stmtLine, remaining);
            if (matched != null) {
                remaining.remove(matched);
                results.add(new StatementMatchResult(stmtLine, MatchStatus.MATCHED,
                        matched.entryId()));
            } else {
                results.add(new StatementMatchResult(stmtLine, MatchStatus.UNMATCHED, null));
            }
        }

        return new ReconciliationReport(results, remaining);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static LedgerEntry findMatch(StatementLine line, List<LedgerEntry> candidates) {
        for (LedgerEntry entry : candidates) {
            if (!entry.currency().equals(line.currency())) continue;
            if (entry.amount().compareTo(line.amount()) != 0) continue;
            long dayDiff = Math.abs(ChronoUnit.DAYS.between(entry.valueDate(), line.valueDate()));
            if (dayDiff > 1) continue;
            // Reference check: only enforce when both sides have a reference
            if (line.reference() != null && entry.reference() != null
                    && !line.reference().equalsIgnoreCase(entry.reference())) {
                continue;
            }
            return entry;
        }
        return null;
    }

    /** Splits a CSV row respecting quoted fields. */
    private static String[] splitCsv(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                tokens.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        tokens.add(current.toString());
        return tokens.toArray(new String[0]);
    }

    private static void requireColumn(Map<String, Integer> index, String col) {
        if (!index.containsKey(col)) {
            throw new IllegalArgumentException("CSV header missing required column: " + col);
        }
    }

    private static String col(String[] cols, Map<String, Integer> index, String name) {
        Integer idx = index.get(name);
        if (idx == null || idx >= cols.length) return "";
        return cols[idx].trim();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /**
     * Extracts the statement currency from the MT940 {@code :60F:} or {@code :60M:} tag.
     * Format: {@code :60F:C250315NPR5000,00} (status + date + currency code + amount).
     * Defaults to {@link Currency#NPR} if not found.
     */
    private static Currency extractMt940Currency(String mt940) {
        for (String line : mt940.split("\r?\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(":60F:") || trimmed.startsWith(":60M:")) {
                // :60F:<C|D><YYMMDD><3-char-currency><amount>
                String body = trimmed.substring(5); // after ":60F:"
                if (body.length() >= 10) {
                    // skip 1 char (status) + 6 chars (date) = index 7 for currency
                    String currencyCode = body.substring(7, 10).toUpperCase();
                    Currency c = KNOWN_CURRENCIES.get(currencyCode);
                    if (c != null) return c;
                }
            }
        }
        return Currency.NPR; // default
    }
}
