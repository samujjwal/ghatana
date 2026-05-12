package com.ghatana.finance.kernel.data;

import com.ghatana.platform.database.datastore.DataStoreConfig;
import com.ghatana.platform.database.datastore.DataStoreConfig.AuditLevel;
import com.ghatana.platform.database.datastore.DataStoreConfig.ComplianceRule;
import com.ghatana.platform.database.datastore.DataStoreConfig.DataGovernance;
import com.ghatana.platform.database.datastore.DataStoreConfig.EncryptionLevel;
import com.ghatana.platform.database.datastore.DataStoreConfig.StorageTier;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.util.Set;
import java.util.stream.Stream;

/**
 * Initializes Finance data stores in Data-Cloud with financial governance.
 *
 * <p>All Data-Cloud calls return CompletableFuture; wrapped with
 * {@code Promise.ofFuture(cf)} at the adapter boundary.</p>
 *
 * <p>Financial retention requirements:
 * <ul>
 *   <li>Trade records: 10 years (SEBON regulatory requirement)</li>
 *   <li>Securities master: 10 years</li>
 *   <li>Client records: 7 years</li>
 *   <li>Audit logs: 10 years (financial compliance)</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Finance data store initialization with regulatory retention/governance policies
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public class FinanceDataService {

    private final DataCloudPlatform dataCloud;

    public FinanceDataService(DataCloudPlatform dataCloud) {
        this.dataCloud = dataCloud;
    }

    /**
     * Initializes all Finance data stores.
     *
     * @return Promise completing when all stores are initialized
     */
    public Promise<Void> initializeStores() {
        return Promises.all(
            Stream.of(
                initializeSecuritiesMasterStore(),
                initializeTradeRecordsStore(),
                initializeClientRecordsStore(),
                initializePortfolioStore(),
                initializeMarketDataStore(),
                initializeAuditStore()
            )
        ).toVoid();
    }

    /**
     * Initializes securities master store with financial governance.
     *
     * @return Promise completing when store is initialized
     */
    private Promise<Void> initializeSecuritiesMasterStore() {
        return Promise.ofFuture(
            dataCloud.createStore("securities.master", DataStoreConfig.builder()
                .withSchema("securities-schema-v1")
                .withRetention(DataStoreConfig.Retention.ofYears(10))
                .withGovernance(DataGovernance.FINANCIAL)
                .withEncryption(EncryptionLevel.STRONG)
                .withImmutable(true) // Securities master is reference data
                .withIndexFields(Set.of(
                    "symbol", "isin", "security_type", "exchange", "currency"
                ))
                .build())
        );
    }

    /**
     * Initializes trade records store with financial governance.
     *
     * @return Promise completing when store is initialized
     */
    private Promise<Void> initializeTradeRecordsStore() {
        return Promise.ofFuture(
            dataCloud.createStore("trade.records", DataStoreConfig.builder()
                .withSchema("trade-schema-v1")
                .withRetention(DataStoreConfig.Retention.ofYears(10)) // SEBON regulatory requirement
                .withGovernance(DataGovernance.FINANCIAL)
                .withEncryption(EncryptionLevel.STRONG)
                .withAuditLevel(AuditLevel.DETAILED)
                .withImmutable(true) // Regulatory requirement - trades cannot be modified
                .withWormStorage(true) // Write Once Read Many
                .withComplianceRules(Set.of(
                    ComplianceRule.SEBON_REGULATIONS,
                    ComplianceRule.MIFID_II,
                    ComplianceRule.DODD_FRANK
                ))
                .withIndexFields(Set.of(
                    "trade_id", "order_id", "client_id", "symbol", "trade_date",
                    "exchange", "settlement_date", "trade_type"
                ))
                .build())
        );
    }

    /**
     * Initializes client records store.
     *
     * @return Promise completing when store is initialized
     */
    private Promise<Void> initializeClientRecordsStore() {
        return Promise.ofFuture(
            dataCloud.createStore("client.records", DataStoreConfig.builder()
                .withSchema("client-schema-v1")
                .withRetention(DataStoreConfig.Retention.ofYears(7))
                .withGovernance(DataGovernance.FINANCIAL)
                .withEncryption(EncryptionLevel.STRONG)
                .withAuditLevel(AuditLevel.DETAILED)
                .withPiiFields(Set.of(
                    "name", "address", "phone", "email", "pan", "citizenship_number"
                ))
                .withIndexFields(Set.of(
                    "client_id", "pan", "account_number", "kyc_status"
                ))
                .build())
        );
    }

    /**
     * Initializes portfolio store.
     *
     * @return Promise completing when store is initialized
     */
    private Promise<Void> initializePortfolioStore() {
        return Promise.ofFuture(
            dataCloud.createStore("portfolio.records", DataStoreConfig.builder()
                .withSchema("portfolio-schema-v1")
                .withRetention(DataStoreConfig.Retention.ofYears(10))
                .withGovernance(DataGovernance.FINANCIAL)
                .withEncryption(EncryptionLevel.STRONG)
                .withAuditLevel(AuditLevel.DETAILED)
                .withIndexFields(Set.of(
                    "portfolio_id", "client_id", "symbol", "position_date"
                ))
                .build())
        );
    }

    /**
     * Initializes market data store.
     *
     * @return Promise completing when store is initialized
     */
    private Promise<Void> initializeMarketDataStore() {
        return Promise.ofFuture(
            dataCloud.createStore("market.data", DataStoreConfig.builder()
                .withSchema("marketdata-schema-v1")
                .withRetention(DataStoreConfig.Retention.ofYears(5))
                .withGovernance(DataGovernance.FINANCIAL)
                .withEncryption(EncryptionLevel.STANDARD)
                .withStorageTier(StorageTier.MULTI_TIER)
                .withIndexFields(Set.of(
                    "symbol", "timestamp", "data_type", "exchange", "source"
                ))
                .build())
        );
    }

    /**
     * Initializes audit store for regulatory compliance.
     *
     * @return Promise completing when store is initialized
     */
    private Promise<Void> initializeAuditStore() {
        return Promise.ofFuture(
            dataCloud.createStore("finance.audit", DataStoreConfig.builder()
                .withSchema("audit-schema-v1")
                .withRetention(DataStoreConfig.Retention.ofYears(10)) // SEBON requirement
                .withGovernance(DataGovernance.FINANCIAL)
                .withEncryption(EncryptionLevel.STRONG)
                .withAuditLevel(AuditLevel.FULL)
                .withImmutable(true)
                .withWormStorage(true)
                .withComplianceRules(Set.of(
                    ComplianceRule.SEBON_REGULATIONS,
                    ComplianceRule.MIFID_II_AUDIT
                ))
                .build())
        );
    }

    // ==================== Inner Types ====================

    /**
     * Data-Cloud platform interface.
     */
    public interface DataCloudPlatform {
        java.util.concurrent.CompletableFuture<Void> createStore(String name, DataStoreConfig config);
    }
}
