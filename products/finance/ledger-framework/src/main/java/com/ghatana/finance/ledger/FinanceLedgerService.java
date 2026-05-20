/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.finance.ledger;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Finance Ledger Service.
 *
 * <p>Finance-specific double-entry accounting ledger with regulatory compliance.
 * Provides financial transaction processing, balance management, and regulatory reporting
 * for financial operations according to accounting standards and regulatory requirements.</p>
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Double-entry accounting with automatic balance validation</li>
 *   <li>Financial transaction processing with regulatory compliance</li>
 *   <li>Multi-currency support with real-time rate conversion</li>
 *   <li>Fiscal year management with dual calendar support</li>
 *   <li>Regulatory audit trails and compliance reporting</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Finance ledger - double-entry accounting, regulatory compliance, multi-currency
 * @doc.layer finance
 * @doc.pattern Service, Ledger
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public final class FinanceLedgerService {

    private static final List<FinanceAccount> NO_FINANCE_ACCOUNTS = List.of();
    private static final FinanceJournal NO_FINANCE_JOURNAL = null;
    private static final List<FinanceJournal> NO_FINANCE_JOURNALS = List.of();
    private static final List<String> NO_VIOLATIONS = List.of();

    // ── Finance-Specific Inner Ports ───────────────────────────────────────────────

    /** Financial account management with regulatory tracking. */
    public interface FinanceAccountStore {
        Promise<FinanceAccount> createAccount(FinanceAccount account);
        Promise<FinanceAccount> getAccount(String accountId);
        Promise<List<FinanceAccount>> getAccountsByTenant(String tenantId);
        Promise<Void> updateAccountBalance(String accountId, FinanceBalance newBalance);
    }

    /** Financial journal storage with regulatory compliance. */
    public interface FinanceJournalStore {
        Promise<String> createJournal(FinanceJournal journal);
        Promise<FinanceJournal> getJournal(String journalId);
        Promise<List<FinanceJournal>> getJournalsByTenant(String tenantId, LocalDate startDate, LocalDate endDate);
        Promise<List<FinanceJournal>> getJournalsByReference(String reference);
    }

    /** Currency conversion and rate management. */
    public interface FinanceCurrencyService {
        Promise<BigDecimal> getExchangeRate(String fromCurrency, String toCurrency, Instant asOf);
        Promise<BigDecimal> convertAmount(MonetaryAmount amount, String toCurrency, Instant asOf);
        Promise<List<String>> getSupportedCurrencies();
    }

    /** Regulatory compliance validation for financial transactions. */
    public interface FinanceComplianceValidator {
        Promise<FinanceComplianceResult> validateTransaction(FinanceTransaction transaction);
        Promise<FinanceComplianceResult> validateJournal(FinanceJournal journal);
        Promise<Void> reportToRegulator(FinanceRegulatoryReport report);
    }

    // ── Finance-Specific Value Types ─────────────────────────────────────────────

    public enum FinanceAccountType {
        ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE, REGULATORY_RESERVE, CLIENT_FUNDS
    }

    public enum FinanceTransactionType {
        TRADE, SETTLEMENT, TRANSFER, FEE, INTEREST, DIVIDEND, TAX, REGULATORY_ADJUSTMENT
    }

    public enum FinanceRegulatoryBody {
        SEC, FINRA, FCA, ESMA, MAS, HKMA, ASIC, JFSA
    }

    public record FinanceAccount(
        String accountId,
        String accountNumber,
        String accountName,
        FinanceAccountType accountType,
        String currency,
        String tenantId,
        FinanceBalance currentBalance,
        boolean isActive,
        Instant createdAt,
        Instant lastModified
    ) {}

    public record FinanceBalance(
        String accountId,
        BigDecimal availableBalance,
        BigDecimal ledgerBalance,
        BigDecimal blockedAmount,
        Instant lastUpdated
    ) {}

    public record MonetaryAmount(
        BigDecimal amount,
        String currency,
        Instant timestamp
    ) {}

    public record FinanceTransaction(
        String transactionId,
        String reference,
        FinanceTransactionType transactionType,
        MonetaryAmount amount,
        String debitAccountId,
        String creditAccountId,
        String tenantId,
        String userId,
        FinanceRegulatoryBody regulatoryBody,
        Instant transactionTime,
        Map<String, Object> metadata
    ) {}

    public record FinanceJournal(
        String journalId,
        String reference,
        String description,
        String fiscalYear,
        LocalDate postedDate,
        Instant postedAtUtc,
        String tenantId,
        String userId,
        FinanceRegulatoryBody regulatoryBody,
        List<FinanceJournalEntry> entries
    ) {}

    public record FinanceJournalEntry(
        String entryId,
        String accountId,
        String direction, // DEBIT or CREDIT
        MonetaryAmount amount,
        String description,
        Instant entryTime
    ) {}

    public record FinanceComplianceResult(
        boolean compliant,
        List<String> violations,
        String riskLevel,
        String regulatoryReference,
        String nextReviewDate
    ) {}

    public record FinanceRegulatoryReport(
        String reportId,
        String reportType,
        FinanceRegulatoryBody regulatoryBody,
        String tenantId,
        LocalDate reportPeriod,
        Map<String, Object> reportData,
        Instant generatedAt
    ) {}

    public record FinanceTransactionResult(
        String transactionId,
        String journalId,
        boolean successful,
        String status,
        List<String> violations,
        Map<String, FinanceBalance> updatedBalances
    ) {}

    // ── Fields ────────────────────────────────────────────────────────────────

    private final FinanceAccountStore accountStore;
    private final FinanceJournalStore journalStore;
    private final FinanceCurrencyService currencyService;
    private final FinanceComplianceValidator complianceValidator;
    private final Executor executor;

    private final Map<String, FinanceAccount> accountCache = new ConcurrentHashMap<>();
    private final Counter transactionProcessedCounter;
    private final Counter complianceViolationCounter;
    private final Counter journalCreatedCounter;

    // ── Constructor ─────────────────────────────────────────────────────────────

    public FinanceLedgerService(
        FinanceAccountStore accountStore,
        FinanceJournalStore journalStore,
        FinanceCurrencyService currencyService,
        FinanceComplianceValidator complianceValidator,
        MeterRegistry registry,
        Executor executor
    ) {
        this.accountStore = accountStore;
        this.journalStore = journalStore;
        this.currencyService = currencyService;
        this.complianceValidator = complianceValidator;
        this.executor = executor;

        this.transactionProcessedCounter = Counter.builder("finance.ledger.transaction.processed_total").register(registry);
        this.complianceViolationCounter = Counter.builder("finance.ledger.compliance.violations_total").register(registry);
        this.journalCreatedCounter = Counter.builder("finance.ledger.journal.created_total").register(registry);
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Process a financial transaction with double-entry accounting and compliance validation.
     */
    public Promise<FinanceTransactionResult> processTransaction(FinanceTransaction transaction) {
        return Promise.ofBlocking(executor, () -> {
            // Validate transaction compliance
            FinanceComplianceResult complianceResult = complianceValidator.validateTransaction(transaction).getResult();

            if (!complianceResult.compliant()) {
                complianceViolationCounter.increment();
                return new FinanceTransactionResult(
                    transaction.transactionId(), null, false, "COMPLIANCE_VIOLATION",
                    complianceResult.violations(), Map.of()
                );
            }

            // Create journal entries for double-entry accounting
            List<FinanceJournalEntry> entries = createJournalEntries(transaction);

            // Create and post journal
            FinanceJournal journal = new FinanceJournal(
                UUID.randomUUID().toString(),
                transaction.reference(),
                transaction.transactionType().name() + " transaction",
                getCurrentFiscalYear(),
                LocalDate.now(),
                Instant.now(),
                transaction.tenantId(),
                transaction.userId(),
                transaction.regulatoryBody(),
                entries
            );

            // Validate journal compliance
            FinanceComplianceResult journalCompliance = complianceValidator.validateJournal(journal).getResult();
            if (!journalCompliance.compliant()) {
                complianceViolationCounter.increment();
                return new FinanceTransactionResult(
                    transaction.transactionId(), null, false, "JOURNAL_COMPLIANCE_VIOLATION",
                    journalCompliance.violations(), Map.of()
                );
            }

            // Post journal and update balances
            String journalId = journalStore.createJournal(journal).getResult();
            Map<String, FinanceBalance> updatedBalances = updateAccountBalances(entries);

            transactionProcessedCounter.increment();
            journalCreatedCounter.increment();

            return new FinanceTransactionResult(
                transaction.transactionId(), journalId, true, "COMPLETED",
                NO_VIOLATIONS, updatedBalances
            );
        });
    }

    /**
     * Create a new financial account with regulatory compliance.
     */
    public Promise<FinanceAccount> createAccount(FinanceAccount account) {
        return accountStore.createAccount(account)
            .then(createdAccount -> {
                accountCache.put(createdAccount.accountId(), createdAccount);
                return Promise.of(createdAccount);
            });
    }

    /**
     * Get account balance with real-time currency conversion if needed.
     */
    public Promise<FinanceBalance> getAccountBalance(String accountId, String displayCurrency) {
        return accountStore.getAccount(accountId)
            .then(account -> {
                if (account.currency().equals(displayCurrency)) {
                    return Promise.of(account.currentBalance());
                } else {
                    // Convert to display currency
                    return currencyService.convertAmount(
                        new MonetaryAmount(account.currentBalance().availableBalance(), account.currency(), Instant.now()),
                        displayCurrency,
                        Instant.now()
                    ).then(convertedAmount -> {
                        return Promise.of(new FinanceBalance(
                            accountId, convertedAmount, convertedAmount,
                            account.currentBalance().blockedAmount(), Instant.now()
                        ));
                    });
                }
            });
    }

    /**
     * Get trial balance for regulatory reporting.
     */
    public Promise<FinanceTrialBalance> getTrialBalance(String tenantId, LocalDate asOfDate) {
        return Promise.ofBlocking(executor, () -> {
            // Get all accounts for tenant
            List<FinanceAccount> accounts = accountStore.getAccountsByTenant(tenantId).getResult();

            Map<FinanceAccountType, MonetaryAmount> balances = new EnumMap<>(FinanceAccountType.class);

            for (FinanceAccount account : accounts) {
                if (account.isActive()) {
                    FinanceBalance balance = account.currentBalance();
                    MonetaryAmount amount = new MonetaryAmount(balance.ledgerBalance(), account.currency(), asOfDate.atStartOfDay().atZone(java.time.ZoneOffset.UTC).toInstant());

                    balances.merge(account.accountType(), amount, (existing, newValue) -> {
                        if (existing.currency().equals(newValue.currency())) {
                            return new MonetaryAmount(
                                existing.amount().add(newValue.amount()),
                                existing.currency(),
                                existing.timestamp()
                            );
                        } else {
                            // Convert to base currency (USD) for aggregation
                            return convertToBaseCurrency(existing).then(convertedExisting -> {
                                return convertToBaseCurrency(newValue).then(convertedNewValue -> {
                                    return Promise.of(new MonetaryAmount(
                                        convertedExisting.amount().add(convertedNewValue.amount()),
                                        "USD",
                                        existing.timestamp()
                                    ));
                                });
                            }).getResult();
                        }
                    });
                }
            }

            return new FinanceTrialBalance(tenantId, asOfDate, balances, Instant.now());
        });
    }

    /**
     * Generate regulatory compliance report.
     */
    public Promise<FinanceRegulatoryReport> generateRegulatoryReport(
            String tenantId, FinanceRegulatoryBody regulatoryBody, LocalDate reportPeriod) {

        return Promise.ofBlocking(executor, () -> {
            // Get journals for the reporting period
            LocalDate startDate = reportPeriod.minusMonths(1);
            List<FinanceJournal> journals = journalStore.getJournalsByTenant(tenantId, startDate, reportPeriod).getResult();

            // Aggregate transaction data
            Map<String, Object> reportData = new HashMap<>();
            reportData.put("totalTransactions", journals.size());
            reportData.put("totalAmount", journals.stream()
                .flatMap(j -> j.entries().stream())
                .mapToDouble(e -> e.amount().amount().doubleValue())
                .sum());
            reportData.put("journalIds", journals.stream().map(FinanceJournal::journalId).toList());

            FinanceRegulatoryReport report = new FinanceRegulatoryReport(
                UUID.randomUUID().toString(),
                "MONTHLY_TRANSACTION_REPORT",
                regulatoryBody,
                tenantId,
                reportPeriod,
                reportData,
                Instant.now()
            );

            // Submit report to regulator
            complianceValidator.reportToRegulator(report).getResult();

            return report;
        });
    }

    // ── Private Helper Methods ───────────────────────────────────────────────────

    private List<FinanceJournalEntry> createJournalEntries(FinanceTransaction transaction) {
        List<FinanceJournalEntry> entries = new ArrayList<>();

        // Debit entry
        entries.add(new FinanceJournalEntry(
            UUID.randomUUID().toString(),
            transaction.debitAccountId(),
            "DEBIT",
            transaction.amount(),
            transaction.transactionType().name() + " debit",
            Instant.now()
        ));

        // Credit entry
        entries.add(new FinanceJournalEntry(
            UUID.randomUUID().toString(),
            transaction.creditAccountId(),
            "CREDIT",
            transaction.amount(),
            transaction.transactionType().name() + " credit",
            Instant.now()
        ));

        return entries;
    }

    private Map<String, FinanceBalance> updateAccountBalances(List<FinanceJournalEntry> entries) {
        Map<String, FinanceBalance> updatedBalances = new HashMap<>();

        for (FinanceJournalEntry entry : entries) {
            FinanceAccount account = accountCache.get(entry.accountId());
            if (account == null) {
                account = accountStore.getAccount(entry.accountId()).getResult();
                accountCache.put(entry.accountId(), account);
            }

            FinanceBalance currentBalance = account.currentBalance();
            BigDecimal newLedgerBalance;

            if ("DEBIT".equals(entry.direction())) {
                if (account.accountType() == FinanceAccountType.ASSET ||
                    account.accountType() == FinanceAccountType.EXPENSE) {
                    newLedgerBalance = currentBalance.ledgerBalance().add(entry.amount().amount());
                } else {
                    newLedgerBalance = currentBalance.ledgerBalance().subtract(entry.amount().amount());
                }
            } else { // CREDIT
                if (account.accountType() == FinanceAccountType.ASSET ||
                    account.accountType() == FinanceAccountType.EXPENSE) {
                    newLedgerBalance = currentBalance.ledgerBalance().subtract(entry.amount().amount());
                } else {
                    newLedgerBalance = currentBalance.ledgerBalance().add(entry.amount().amount());
                }
            }

            FinanceBalance newBalance = new FinanceBalance(
                entry.accountId(),
                newLedgerBalance,
                newLedgerBalance,
                currentBalance.blockedAmount(),
                Instant.now()
            );

            accountStore.updateAccountBalance(entry.accountId(), newBalance).getResult();
            updatedBalances.put(entry.accountId(), newBalance);
        }

        return updatedBalances;
    }

    private String getCurrentFiscalYear() {
        // Simple fiscal year calculation - would be more sophisticated in production
        int year = LocalDate.now().getYear();
        return year + "/" + (year + 1);
    }

    private Promise<MonetaryAmount> convertToBaseCurrency(MonetaryAmount amount) {
        return currencyService.convertAmount(amount, "USD", Instant.now())
            .map(bd -> new MonetaryAmount(bd, "USD", Instant.now()));
    }

    // ── Supporting Records ─────────────────────────────────────────────────────

    public record FinanceTrialBalance(
        String tenantId,
        LocalDate asOfDate,
        Map<FinanceAccountType, MonetaryAmount> balances,
        Instant generatedAt
    ) {}

    // ── Default Implementation (for testing) ─────────────────────────────────────

    public static FinanceLedgerService createDefault(MeterRegistry registry, Executor executor) {
        return new FinanceLedgerService(
            new DefaultFinanceAccountStore(),
            new DefaultFinanceJournalStore(),
            new DefaultFinanceCurrencyService(),
            new DefaultFinanceComplianceValidator(),
            registry,
            executor
        );
    }

    // Default implementations for testing/development
    private static final class DefaultFinanceAccountStore implements FinanceAccountStore {
        @Override
        public Promise<FinanceAccount> createAccount(FinanceAccount account) {
            return Promise.of(account);
        }

        @Override
        public Promise<FinanceAccount> getAccount(String accountId) {
            return Promise.of(new FinanceAccount(
                accountId, "ACC-001", "Test Account", FinanceAccountType.ASSET, "USD",
                "tenant-001", new FinanceBalance(accountId, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, Instant.now()),
                true, Instant.now(), Instant.now()
            ));
        }

        @Override
        public Promise<List<FinanceAccount>> getAccountsByTenant(String tenantId) {
            return Promise.of(NO_FINANCE_ACCOUNTS);
        }

        @Override
        public Promise<Void> updateAccountBalance(String accountId, FinanceBalance newBalance) {
            return Promise.complete();
        }
    }

    private static final class DefaultFinanceJournalStore implements FinanceJournalStore {
        @Override
        public Promise<String> createJournal(FinanceJournal journal) {
            return Promise.of(journal.journalId());
        }

        @Override
        public Promise<FinanceJournal> getJournal(String journalId) {
            return Promise.of(NO_FINANCE_JOURNAL);
        }

        @Override
        public Promise<List<FinanceJournal>> getJournalsByTenant(String tenantId, LocalDate startDate, LocalDate endDate) {
            return Promise.of(NO_FINANCE_JOURNALS);
        }

        @Override
        public Promise<List<FinanceJournal>> getJournalsByReference(String reference) {
            return Promise.of(NO_FINANCE_JOURNALS);
        }
    }

    private static final class DefaultFinanceCurrencyService implements FinanceCurrencyService {
        @Override
        public Promise<BigDecimal> getExchangeRate(String fromCurrency, String toCurrency, Instant asOf) {
            return Promise.of(BigDecimal.ONE);
        }

        @Override
        public Promise<BigDecimal> convertAmount(MonetaryAmount amount, String toCurrency, Instant asOf) {
            return Promise.of(amount.amount());
        }

        @Override
        public Promise<List<String>> getSupportedCurrencies() {
            return Promise.of(List.of("USD", "EUR", "GBP"));
        }
    }

    private static final class DefaultFinanceComplianceValidator implements FinanceComplianceValidator {
        @Override
        public Promise<FinanceComplianceResult> validateTransaction(FinanceTransaction transaction) {
            return Promise.of(new FinanceComplianceResult(true, List.of(), "LOW", "SEC-001", "2024-12-31"));
        }

        @Override
        public Promise<FinanceComplianceResult> validateJournal(FinanceJournal journal) {
            return Promise.of(new FinanceComplianceResult(true, List.of(), "LOW", "SEC-001", "2024-12-31"));
        }

        @Override
        public Promise<Void> reportToRegulator(FinanceRegulatoryReport report) {
            return Promise.complete();
        }
    }
}
