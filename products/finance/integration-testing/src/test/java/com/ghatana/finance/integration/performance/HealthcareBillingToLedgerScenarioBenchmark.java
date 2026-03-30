package com.ghatana.finance.integration.performance;

import com.ghatana.finance.integration.HealthcareBillingToLedgerIntegrationTestSuiteService;
import com.ghatana.platform.audit.AuditBusPort;
import com.ghatana.platform.audit.AuditEvent;
import org.h2.jdbcx.JdbcDataSource;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for the cross-domain healthcare billing to finance ledger scenario.
 *
 * @doc.type class
 * @doc.purpose Baseline benchmark for PHR->Finance billing integration scenario
 * @doc.layer product
 * @doc.pattern Benchmark
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class HealthcareBillingToLedgerScenarioBenchmark {

    private HealthcareBillingToLedgerIntegrationTestSuiteService suite;

    @Setup
    public void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:finance-bench;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("sa");

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS phr_finance_integration_runs (
                  run_id VARCHAR(128) PRIMARY KEY,
                  suite_name VARCHAR(128) NOT NULL,
                  status VARCHAR(32) NOT NULL,
                  error_message VARCHAR(1024),
                  recorded_at BIGINT NOT NULL
                )
                """);
        }

        StubPhrBillingPort phrBilling = new StubPhrBillingPort();
        StubFinanceLedgerPort financeLedger = new StubFinanceLedgerPort();
        AuditBusPort audit = event -> {
            AuditEvent ignored = event;
        };

        suite = new HealthcareBillingToLedgerIntegrationTestSuiteService(
            dataSource,
            phrBilling,
            financeLedger,
            audit,
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
            Runnable::run
        );
    }

    @Benchmark
    public boolean runScenario() {
        HealthcareBillingToLedgerIntegrationTestSuiteService.TestResult result = suite
            .runEndToEndScenario("patient-1", "provider-1", 1500.0, "NPR")
            .toCompletableFuture()
            .join();
        return result.passed();
    }

    private static final class StubPhrBillingPort
        implements HealthcareBillingToLedgerIntegrationTestSuiteService.PhrBillingPort {

        private final Map<String, String> encounterToLedger = new ConcurrentHashMap<>();

        @Override
        public String createEncounter(String patientId, String providerId, String currency, double amount) {
            String encounterId = patientId + "-" + providerId + "-" + amount;
            encounterToLedger.put(encounterId, "ledger-" + encounterId);
            return encounterId;
        }

        @Override
        public String closeEncounter(String encounterId) {
            return encounterToLedger.get(encounterId);
        }
    }

    private static final class StubFinanceLedgerPort
        implements HealthcareBillingToLedgerIntegrationTestSuiteService.FinanceLedgerPort {

        @Override
        public boolean hasEntry(String ledgerEntryId) {
            return ledgerEntryId != null && ledgerEntryId.startsWith("ledger-");
        }

        @Override
        public boolean isBalanced(String ledgerEntryId) {
            return true;
        }

        @Override
        public String getPostingType(String ledgerEntryId) {
            return "CHARGE";
        }
    }
}
