package com.ghatana.plugin.ledger.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.ledger.LedgerPlugin;
import com.ghatana.plugin.ledger.LedgerTransaction;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * @doc.type class
 * @doc.purpose Contract parity tests across standard and durable ledger implementations
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Billing ledger plugin contract parity tests")
@ExtendWith(MockitoExtension.class)
class LedgerPluginContractParityTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    @Test
    @DisplayName("manifest contract: both implementations expose valid metadata")
    void manifestContractIsValid() {
        PluginMetadata standard = new StandardLedgerPlugin().metadata();
        PluginMetadata durable = new DurableLedgerPlugin(testDataSource()).metadata();

        assertThat(standard.id()).isNotBlank();
        assertThat(standard.name()).isNotBlank();
        assertThat(standard.version()).isNotBlank();
        assertThat(standard.type()).isEqualTo(PluginType.CUSTOM);

        assertThat(durable.id()).isNotBlank();
        assertThat(durable.name()).isNotBlank();
        assertThat(durable.version()).isNotBlank();
        assertThat(durable.type()).isEqualTo(PluginType.CUSTOM);
    }

    @Test
    @DisplayName("both implementations declare the same canonical capability set")
    void capabilityParityAcrossImplementations() {
        PluginMetadata standard = new StandardLedgerPlugin().metadata();
        PluginMetadata durable = new DurableLedgerPlugin(testDataSource()).metadata();

        java.util.List<String> expectedCapabilities = java.util.List.of(
            "ledger:post-transaction",
            "ledger:reverse-transaction",
            "ledger:create-account",
            "ledger:query-entries"
        );

        assertThat(standard.capabilities())
            .as("standard billing plugin must declare all canonical capabilities")
            .containsExactlyInAnyOrderElementsOf(expectedCapabilities);

        assertThat(durable.capabilities())
            .as("durable billing plugin must declare the same canonical capabilities")
            .containsExactlyInAnyOrderElementsOf(expectedCapabilities);
    }

    @Test
    @DisplayName("metadata() returns the same singleton instance on repeated calls")
    void metadataIsSingletonForBothImplementations() {
        StandardLedgerPlugin standard = new StandardLedgerPlugin();
        DurableLedgerPlugin durable = new DurableLedgerPlugin(testDataSource());

        assertThat(standard.metadata()).isSameAs(standard.metadata());
        assertThat(durable.metadata()).isSameAs(durable.metadata());
    }

    @Test
    @DisplayName("lifecycle contract: both implementations follow initialize/start/stop/shutdown")
    void lifecycleParityAcrossImplementations() {
        StandardLedgerPlugin standard = new StandardLedgerPlugin();
        DurableLedgerPlugin durable = new DurableLedgerPlugin(testDataSource());
        durable.ensureSchema();

        assertThat(standard.getState()).isEqualTo(PluginState.UNLOADED);
        assertThat(durable.getState()).isEqualTo(PluginState.UNLOADED);

        runPromise(() -> standard.initialize(mockContext));
        runPromise(() -> durable.initialize(mockContext));
        assertThat(standard.getState()).isEqualTo(PluginState.INITIALIZED);
        assertThat(durable.getState()).isEqualTo(PluginState.INITIALIZED);

        runPromise(() -> standard.start());
        runPromise(() -> durable.start());
        assertThat(standard.getState()).isEqualTo(PluginState.STARTED);
        assertThat(durable.getState()).isEqualTo(PluginState.STARTED);

        runPromise(() -> standard.stop());
        runPromise(() -> durable.stop());
        assertThat(standard.getState()).isEqualTo(PluginState.STOPPED);
        assertThat(durable.getState()).isEqualTo(PluginState.STOPPED);

        runPromise(() -> standard.shutdown());
        runPromise(() -> durable.shutdown());
        assertThat(standard.getState()).isEqualTo(PluginState.UNLOADED);
        assertThat(durable.getState()).isEqualTo(PluginState.UNLOADED);
    }

    @Test
    @DisplayName("behavior parity: both implementations support post/get/status/reverse")
    void behaviorParityForCoreLedgerContract() {
        StandardLedgerPlugin standard = new StandardLedgerPlugin();
        DurableLedgerPlugin durable = new DurableLedgerPlugin(testDataSource());
        durable.ensureSchema();

        runPromise(() -> standard.initialize(mockContext).then(v -> standard.start()));
        runPromise(() -> durable.initialize(mockContext).then(v -> durable.start()));

        runPromise(() -> standard.createAccount("contract-debit", LedgerPlugin.AccountType.ASSET));
        runPromise(() -> standard.createAccount("contract-credit", LedgerPlugin.AccountType.LIABILITY));
        runPromise(() -> durable.createAccount("contract-debit", LedgerPlugin.AccountType.ASSET));
        runPromise(() -> durable.createAccount("contract-credit", LedgerPlugin.AccountType.LIABILITY));

        LedgerTransaction tx = LedgerTransaction.builder()
                .transactionId("contract-billing-tx")
                .sourceId("contract-product")
                .debitAccount("contract-debit")
                .creditAccount("contract-credit")
                .amount(new BigDecimal("42.50"))
                .currency("USD")
                .type(LedgerTransaction.TransactionType.CHARGE)
                .description("contract test")
                .build();

        String standardEntryId = runPromise(() -> standard.postTransaction(tx));
        String durableEntryId = runPromise(() -> durable.postTransaction(tx));

        Optional<LedgerPlugin.LedgerEntry> standardEntry = runPromise(() -> standard.getEntry(standardEntryId));
        Optional<LedgerPlugin.LedgerEntry> durableEntry = runPromise(() -> durable.getEntry(durableEntryId));

        assertThat(standardEntry).isPresent();
        assertThat(durableEntry).isPresent();
        assertThat(standardEntry.get().transactionId()).isEqualTo("contract-billing-tx");
        assertThat(durableEntry.get().transactionId()).isEqualTo("contract-billing-tx");

        LedgerPlugin.PostingStatus standardStatus =
                runPromise(() -> standard.getPostingStatus("contract-billing-tx"));
        LedgerPlugin.PostingStatus durableStatus =
                runPromise(() -> durable.getPostingStatus("contract-billing-tx"));

        assertThat(standardStatus).isEqualTo(LedgerPlugin.PostingStatus.POSTED);
        assertThat(durableStatus).isEqualTo(LedgerPlugin.PostingStatus.POSTED);

        runPromise(() -> standard.reverseTransaction("contract-billing-tx", "contract-reversal"));
        runPromise(() -> durable.reverseTransaction("contract-billing-tx", "contract-reversal"));

        LedgerPlugin.PostingStatus standardReversed =
                runPromise(() -> standard.getPostingStatus("contract-billing-tx"));
        LedgerPlugin.PostingStatus durableReversed =
                runPromise(() -> durable.getPostingStatus("contract-billing-tx"));

        assertThat(standardReversed).isEqualTo(LedgerPlugin.PostingStatus.REVERSED);
        assertThat(durableReversed).isEqualTo(LedgerPlugin.PostingStatus.REVERSED);

        Instant now = Instant.now();
        LedgerPlugin.TimeRange range =
                new LedgerPlugin.TimeRange(now.minusSeconds(300), now.plusSeconds(300));

        assertThat(runPromise(() -> standard.queryEntries("contract-debit", range))).isNotEmpty();
        assertThat(runPromise(() -> durable.queryEntries("contract-debit", range))).isNotEmpty();
    }

    @Test
    @DisplayName("migration contract: durable ensureSchema is idempotent")
    void durableEnsureSchemaIsIdempotent() {
        DurableLedgerPlugin durable = new DurableLedgerPlugin(testDataSource());

        assertThatCode(() -> {
            durable.ensureSchema();
            durable.ensureSchema();
        }).doesNotThrowAnyException();
    }

    private JdbcDataSource testDataSource() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:billing_contract_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        return ds;
    }
}
