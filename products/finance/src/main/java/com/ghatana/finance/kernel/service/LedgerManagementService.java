package com.ghatana.finance.kernel.service;

import com.ghatana.kernel.context.KernelContext;
import io.activej.promise.Promise;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ledger Management Service for double-entry bookkeeping.
 *
 * <p>Manages financial ledger with:
 * <ul>
 *   <li>Double-entry bookkeeping transactions</li>
 *   <li>Account balance tracking</li>
 *   <li>Multi-currency support</li>
 *   <li>Audit trail and reconciliation</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Finance ledger management service
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public class LedgerManagementService extends FinanceServiceBase {

    private static final String LEDGER_DATASET = "finance.ledger.entries";

    public LedgerManagementService(KernelContext context) {
        super(context);
    }

    @Override
    public String getName() {
        return "ledger-management";
    }

    @Override
    protected Promise<Void> initializeDatasets() {
        return createSchema(
            LEDGER_DATASET,
            Map.of(
                "entryId", "string",
                "debitAccount", "string",
                "creditAccount", "string",
                "amount", "decimal",
                "currency", "string",
                "postedAt", "timestamp"
            ),
            Map.of("retention", "permanent")
        ).whenException(e -> {});
    }

    public Promise<LedgerEntry> postEntry(LedgerEntryRequest request) {
        ensureRunning();

        ValidationResult validation = validateEntry(request);
        if (!validation.isValid()) {
            return Promise.ofException(new IllegalStateException(validation.getError()));
        }

        String entryId = generateId("le");
        Instant now = Instant.now();

        LedgerEntry entry = new LedgerEntry(
            entryId,
            request.getDebitAccount(),
            request.getCreditAccount(),
            request.getAmount(),
            request.getCurrency(),
            request.getDescription(),
            request.getTransactionType(),
            now,
            "POSTED"
        );

        return createRecord(
            LEDGER_DATASET,
            entryId,
            entry,
            Map.of(
                "debitAccount", entry.getDebitAccount(),
                "creditAccount", entry.getCreditAccount(),
                "amount", entry.getAmount().toString(),
                "postedAt", now.toString()
            ),
            "LedgerEntry",
            1
        ).then(stored -> audit("LEDGER_ENTRY_POST", entryId,
            "Entry posted: " + request.getDebitAccount() + " -> " + request.getCreditAccount())
            .map($ -> stored));
    }

    public Promise<Optional<LedgerEntry>> getEntry(String entryId) {
        ensureRunning();
        return readRecord(LEDGER_DATASET, entryId, LedgerEntry.class)
            .whenException(e -> Promise.of(Optional.empty()));
    }

    public Promise<List<LedgerEntry>> getAccountEntries(String accountId, Instant from, Instant to) {
        ensureRunning();

        return queryRecords(
            LEDGER_DATASET,
            "(debitAccount = :accountId OR creditAccount = :accountId) AND postedAt >= :from AND postedAt <= :to",
            Map.of("accountId", accountId, "from", from, "to", to),
            10000,
            0,
            LedgerEntry.class
        ).map(entries -> entries.stream()
            .sorted((a, b) -> b.getPostedAt().compareTo(a.getPostedAt()))
            .toList());
    }

    public Promise<BigDecimal> getAccountBalance(String accountId) {
        ensureRunning();

        return getAccountEntries(accountId, Instant.EPOCH, Instant.now())
            .map(entries -> {
                BigDecimal balance = BigDecimal.ZERO;
                for (LedgerEntry entry : entries) {
                    if (entry.getDebitAccount().equals(accountId)) {
                        balance = balance.add(entry.getAmount());
                    }
                    if (entry.getCreditAccount().equals(accountId)) {
                        balance = balance.subtract(entry.getAmount());
                    }
                }
                return balance;
            });
    }

    private ValidationResult validateEntry(LedgerEntryRequest request) {
        if (request == null) {
            return ValidationResult.error("Request must not be null");
        }
        if (request.getDebitAccount() == null || request.getDebitAccount().isBlank()) {
            return ValidationResult.error("Debit account is required");
        }
        if (request.getCreditAccount() == null || request.getCreditAccount().isBlank()) {
            return ValidationResult.error("Credit account is required");
        }
        if (request.getDebitAccount().equals(request.getCreditAccount())) {
            return ValidationResult.error("Debit and credit accounts must be different");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return ValidationResult.error("Amount must be positive");
        }
        if (request.getCurrency() == null || request.getCurrency().isBlank()) {
            return ValidationResult.error("Currency is required");
        }
        return ValidationResult.success();
    }

    // ==================== Inner Types ====================
    public static class LedgerEntry {
        private final String id;
        private final String debitAccount;
        private final String creditAccount;
        private final BigDecimal amount;
        private final String currency;
        private final String description;
        private final String transactionType;
        private final Instant postedAt;
        private final String status;

        public LedgerEntry(String id, String debitAccount, String creditAccount,
                          BigDecimal amount, String currency, String description,
                          String transactionType, Instant postedAt, String status) {
            this.id = id;
            this.debitAccount = debitAccount;
            this.creditAccount = creditAccount;
            this.amount = amount;
            this.currency = currency;
            this.description = description;
            this.transactionType = transactionType;
            this.postedAt = postedAt;
            this.status = status;
        }

        public String getId() { return id; }
        public String getDebitAccount() { return debitAccount; }
        public String getCreditAccount() { return creditAccount; }
        public BigDecimal getAmount() { return amount; }
        public String getCurrency() { return currency; }
        public String getDescription() { return description; }
        public String getTransactionType() { return transactionType; }
        public Instant getPostedAt() { return postedAt; }
        public String getStatus() { return status; }
    }

    public static class LedgerEntryRequest {
        private final String debitAccount;
        private final String creditAccount;
        private final BigDecimal amount;
        private final String currency;
        private final String description;
        private final String transactionType;

        public LedgerEntryRequest(String debitAccount, String creditAccount, BigDecimal amount,
                                 String currency, String description, String transactionType) {
            this.debitAccount = debitAccount;
            this.creditAccount = creditAccount;
            this.amount = amount;
            this.currency = currency;
            this.description = description;
            this.transactionType = transactionType;
        }

        public String getDebitAccount() { return debitAccount; }
        public String getCreditAccount() { return creditAccount; }
        public BigDecimal getAmount() { return amount; }
        public String getCurrency() { return currency; }
        public String getDescription() { return description; }
        public String getTransactionType() { return transactionType; }
    }

    private static class ValidationResult {
        private final boolean valid;
        private final String error;

        private ValidationResult(boolean valid, String error) {
            this.valid = valid;
            this.error = error;
        }

        static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }

        boolean isValid() { return valid; }
        String getError() { return error; }
    }
}
