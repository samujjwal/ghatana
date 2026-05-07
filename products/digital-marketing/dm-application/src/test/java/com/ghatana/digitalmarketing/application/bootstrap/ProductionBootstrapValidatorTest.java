package com.ghatana.digitalmarketing.application.bootstrap;

import com.ghatana.digitalmarketing.application.campaign.CampaignRepository;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P0-016 / P1-004: Production bootstrap validator tests.
 *
 * <p>Verifies that the validator correctly detects stub implementations,
 * in-memory repositories, missing configuration, and other production blockers.
 */
@DisplayName("ProductionBootstrapValidator Tests")
class ProductionBootstrapValidatorTest {

    @Test
    @DisplayName("P0-016: Rejects deterministic adapter in production")
    void shouldRejectDeterministicAdapterInProduction() {
        // Given
        ProductionBootstrapValidator validator = new ProductionBootstrapValidator.Builder()
            .isProduction(true)
            .dataSource(new AlwaysValidDataSource())
            .campaignRepository(new PostgresCampaignRepository())
            .piiHmacKey("valid-hmac-key-that-is-32-chars-long-for-test")
            .validateAdapter(new DeterministicTestAdapter())
            .build();

        // Then
        assertThatThrownBy(validator::validate)
            .isInstanceOf(ProductionBootstrapValidator.ProductionBootstrapException.class)
            .hasMessageContaining("ADAPTER-001")
            .hasMessageContaining("Deterministic");
    }

    @Test
    @DisplayName("P0-016: Rejects stub adapter in production")
    void shouldRejectStubAdapterInProduction() {
        // Given
        ProductionBootstrapValidator validator = new ProductionBootstrapValidator.Builder()
            .isProduction(true)
            .dataSource(new AlwaysValidDataSource())
            .campaignRepository(new PostgresCampaignRepository())
            .piiHmacKey("valid-hmac-key-that-is-32-chars-long-for-test")
            .validateAdapter(new StubTestAdapter())
            .build();

        // Then
        assertThatThrownBy(validator::validate)
            .isInstanceOf(ProductionBootstrapValidator.ProductionBootstrapException.class)
            .hasMessageContaining("ADAPTER-001")
            .hasMessageContaining("Stub");
    }

    @Test
    @DisplayName("P0-016: Rejects fake adapter in production")
    void shouldRejectFakeAdapterInProduction() {
        // Given
        ProductionBootstrapValidator validator = new ProductionBootstrapValidator.Builder()
            .isProduction(true)
            .dataSource(new AlwaysValidDataSource())
            .campaignRepository(new PostgresCampaignRepository())
            .piiHmacKey("valid-hmac-key-that-is-32-chars-long-for-test")
            .validateAdapter(new FakeTestAdapter())
            .build();

        // Then
        assertThatThrownBy(validator::validate)
            .isInstanceOf(ProductionBootstrapValidator.ProductionBootstrapException.class)
            .hasMessageContaining("ADAPTER-001")
            .hasMessageContaining("Fake");
    }

    @Test
    @DisplayName("P0-016: Rejects mock adapter in production")
    void shouldRejectMockAdapterInProduction() {
        // Given
        ProductionBootstrapValidator validator = new ProductionBootstrapValidator.Builder()
            .isProduction(true)
            .dataSource(new AlwaysValidDataSource())
            .campaignRepository(new PostgresCampaignRepository())
            .piiHmacKey("valid-hmac-key-that-is-32-chars-long-for-test")
            .validateAdapter(new MockTestAdapter())
            .build();

        // Then
        assertThatThrownBy(validator::validate)
            .isInstanceOf(ProductionBootstrapValidator.ProductionBootstrapException.class)
            .hasMessageContaining("ADAPTER-001")
            .hasMessageContaining("Mock");
    }

    @Test
    @DisplayName("P0-016: Allows real adapters in production")
    void shouldAllowRealAdaptersInProduction() throws Exception {
        // Given
        ProductionBootstrapValidator validator = new ProductionBootstrapValidator.Builder()
            .isProduction(true)
            .dataSource(new AlwaysValidDataSource())
            .campaignRepository(new PostgresCampaignRepository())
            .piiHmacKey("valid-hmac-key-that-is-32-chars-long-for-test")
            .contactEncryptionKey("valid-contact-enc-key-32chars-long!")
            .googleAdsOutboxExecutor(new ProductionOutboxExecutor())
            .kernelAdapter(new MinimalKernelAdapter())
            .validateAdapter(new RealProductionAdapter())
            .build();

        // When/Then - should not throw
        validator.validate();
    }

    @Test
    @DisplayName("P0-016: Skips validation in non-production mode")
    void shouldSkipValidationInNonProductionMode() {
        // Given
        ProductionBootstrapValidator validator = new ProductionBootstrapValidator.Builder()
            .isProduction(false)
            .build();

        // When/Then - should not throw even with invalid config
        validator.validate();
    }

    @Test
    @DisplayName("P1-004: Rejects in-memory repository in production")
    void shouldRejectInMemoryRepositoryInProduction() {
        // Given
        ProductionBootstrapValidator validator = new ProductionBootstrapValidator.Builder()
            .isProduction(true)
            .dataSource(new AlwaysValidDataSource())
            .campaignRepository(new InMemoryCampaignRepository())
            .piiHmacKey("valid-hmac-key-that-is-32-chars-long-for-test")
            .build();

        // Then
        assertThatThrownBy(validator::validate)
            .isInstanceOf(ProductionBootstrapValidator.ProductionBootstrapException.class)
            .hasMessageContaining("PERSISTENCE-002")
            .hasMessageContaining("in-memory");
    }

    @Test
    @DisplayName("P1-004: Rejects missing PII HMAC key in production")
    void shouldRejectMissingPiiHmacKeyInProduction() {
        // Given
        ProductionBootstrapValidator validator = new ProductionBootstrapValidator.Builder()
            .isProduction(true)
            .dataSource(new AlwaysValidDataSource())
            .campaignRepository(new PostgresCampaignRepository())
            .piiHmacKey(null) // Missing key
            .build();

        // Then
        assertThatThrownBy(validator::validate)
            .isInstanceOf(ProductionBootstrapValidator.ProductionBootstrapException.class)
            .hasMessageContaining("PII-001");
    }

    @Test
    @DisplayName("P1-004: Rejects short PII HMAC key in production")
    void shouldRejectShortPiiHmacKeyInProduction() {
        // Given
        ProductionBootstrapValidator validator = new ProductionBootstrapValidator.Builder()
            .isProduction(true)
            .dataSource(new AlwaysValidDataSource())
            .campaignRepository(new PostgresCampaignRepository())
            .piiHmacKey("short") // Too short
            .build();

        // Then
        assertThatThrownBy(validator::validate)
            .isInstanceOf(ProductionBootstrapValidator.ProductionBootstrapException.class)
            .hasMessageContaining("PII-002");
    }

    @Test
    @DisplayName("P0-016: Rejects test-named adapter in production")
    void shouldRejectTestPackageAdapterInProduction() {
        // Given — TestPackageAdapter simpleName contains "Test" which triggers STUB-001
        ProductionBootstrapValidator validator = new ProductionBootstrapValidator.Builder()
            .isProduction(true)
            .dataSource(new AlwaysValidDataSource())
            .campaignRepository(new PostgresCampaignRepository())
            .piiHmacKey("valid-hmac-key-that-is-32-chars-long-for-test")
            .validateAdapter(new TestPackageAdapter())
            .build();

        // Then
        assertThatThrownBy(validator::validate)
            .isInstanceOf(ProductionBootstrapValidator.ProductionBootstrapException.class)
            .hasMessageContaining("ADAPTER-001");
    }

    @Test
    @DisplayName("P0-016: Validates multiple adapters at once")
    void shouldValidateMultipleAdapters() throws Exception {
        // Given
        ProductionBootstrapValidator validator = new ProductionBootstrapValidator.Builder()
            .isProduction(true)
            .dataSource(new AlwaysValidDataSource())
            .campaignRepository(new PostgresCampaignRepository())
            .kernelAdapter(new MinimalKernelAdapter())
            .piiHmacKey("valid-hmac-key-that-is-32-chars-long-for-test")
            .contactEncryptionKey("valid-contact-enc-key-32chars-long!")
            .googleAdsOutboxExecutor(new ProductionOutboxExecutor())
            .validateAdapters(new RealProductionAdapter(), new RealProductionAdapter())
            .build();

        // When/Then - should not throw
        validator.validate();
    }

    // ─── In-memory test doubles ───────────────────────────────────────────────

    /** Returns a valid connection to satisfy DataSource checks. */
    private static class AlwaysValidDataSource implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            return new ValidConnection();
        }
        @Override public Connection getConnection(String user, String pw) throws SQLException { return getConnection(); }
        @Override public PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(PrintWriter out) {}
        @Override public void setLoginTimeout(int seconds) {}
        @Override public int getLoginTimeout() { return 0; }
        @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { throw new SQLFeatureNotSupportedException(); }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException(); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }

    /** Minimal Connection that reports isValid=true. */
    private static class ValidConnection implements Connection {
        @Override public boolean isValid(int timeout) { return true; }
        @Override public void close() {}
        @Override public boolean isClosed() { return false; }
        // Remaining Connection methods throw UnsupportedOperationException
        @Override public java.sql.Statement createStatement() { throw new UnsupportedOperationException(); }
        @Override public java.sql.PreparedStatement prepareStatement(String sql) { throw new UnsupportedOperationException(); }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) { throw new UnsupportedOperationException(); }
        @Override public java.sql.CallableStatement prepareCall(String sql) { throw new UnsupportedOperationException(); }
        @Override public String nativeSQL(String sql) { throw new UnsupportedOperationException(); }
        @Override public void setAutoCommit(boolean b) {}
        @Override public boolean getAutoCommit() { return true; }
        @Override public void commit() {}
        @Override public void rollback() {}
        @Override public java.sql.DatabaseMetaData getMetaData() { throw new UnsupportedOperationException(); }
        @Override public void setReadOnly(boolean b) {}
        @Override public boolean isReadOnly() { return false; }
        @Override public void setCatalog(String c) {}
        @Override public String getCatalog() { return null; }
        @Override public void setTransactionIsolation(int i) {}
        @Override public int getTransactionIsolation() { return 0; }
        @Override public java.sql.SQLWarning getWarnings() { return null; }
        @Override public void clearWarnings() {}
        @Override public java.sql.Statement createStatement(int t, int c) { throw new UnsupportedOperationException(); }
        @Override public java.sql.PreparedStatement prepareStatement(String s, int t, int c) { throw new UnsupportedOperationException(); }
        @Override public java.sql.CallableStatement prepareCall(String s, int t, int c) { throw new UnsupportedOperationException(); }
        @Override public java.util.Map<String, Class<?>> getTypeMap() { throw new UnsupportedOperationException(); }
        @Override public void setTypeMap(java.util.Map<String, Class<?>> m) {}
        @Override public void setHoldability(int h) {}
        @Override public int getHoldability() { return 0; }
        @Override public java.sql.Savepoint setSavepoint() { throw new UnsupportedOperationException(); }
        @Override public java.sql.Savepoint setSavepoint(String n) { throw new UnsupportedOperationException(); }
        @Override public void rollback(java.sql.Savepoint s) {}
        @Override public void releaseSavepoint(java.sql.Savepoint s) {}
        @Override public java.sql.Statement createStatement(int t, int c, int h) { throw new UnsupportedOperationException(); }
        @Override public java.sql.PreparedStatement prepareStatement(String s, int t, int c, int h) { throw new UnsupportedOperationException(); }
        @Override public java.sql.CallableStatement prepareCall(String s, int t, int c, int h) { throw new UnsupportedOperationException(); }
        @Override public java.sql.PreparedStatement prepareStatement(String s, int[] c) { throw new UnsupportedOperationException(); }
        @Override public java.sql.PreparedStatement prepareStatement(String s, String[] c) { throw new UnsupportedOperationException(); }
        @Override public java.sql.Clob createClob() { throw new UnsupportedOperationException(); }
        @Override public java.sql.Blob createBlob() { throw new UnsupportedOperationException(); }
        @Override public java.sql.NClob createNClob() { throw new UnsupportedOperationException(); }
        @Override public java.sql.SQLXML createSQLXML() { throw new UnsupportedOperationException(); }
        @Override public java.sql.Array createArrayOf(String t, Object[] e) { throw new UnsupportedOperationException(); }
        @Override public java.sql.Struct createStruct(String t, Object[] a) { throw new UnsupportedOperationException(); }
        @Override public void setSchema(String s) {}
        @Override public String getSchema() { return null; }
        @Override public void abort(java.util.concurrent.Executor e) {}
        @Override public void setNetworkTimeout(java.util.concurrent.Executor e, int t) {}
        @Override public int getNetworkTimeout() { return 0; }
        @Override public java.util.Properties getClientInfo() { return new java.util.Properties(); }
        @Override public String getClientInfo(String name) { return null; }
        @Override public void setClientInfo(java.util.Properties p) {}
        @Override public void setClientInfo(String name, String value) {}
        @Override public <T> T unwrap(Class<T> i) throws SQLException { throw new SQLException(); }
        @Override public boolean isWrapperFor(Class<?> i) { return false; }
    }

    /** CampaignRepository backed by a PostgreSQL-type class name (no "InMemory"). */
    private static class PostgresCampaignRepository implements CampaignRepository {
        @Override public Promise<Campaign> save(Campaign c) { throw new UnsupportedOperationException(); }
        @Override public Promise<java.util.Optional<Campaign>> findById(DmWorkspaceId w, String id) { return Promise.of(java.util.Optional.empty()); }
        @Override public Promise<java.util.List<Campaign>> listByWorkspace(DmWorkspaceId w, int limit, int offset) { return Promise.of(java.util.List.of()); }
        @Override public Promise<Long> countByWorkspace(DmWorkspaceId w) { return Promise.of(0L); }
    }

    /** CampaignRepository whose class name contains "InMemory" — triggers PERSISTENCE-002. */
    private static class InMemoryCampaignRepository implements CampaignRepository {
        @Override public Promise<Campaign> save(Campaign c) { throw new UnsupportedOperationException(); }
        @Override public Promise<java.util.Optional<Campaign>> findById(DmWorkspaceId w, String id) { return Promise.of(java.util.Optional.empty()); }
        @Override public Promise<java.util.List<Campaign>> listByWorkspace(DmWorkspaceId w, int limit, int offset) { return Promise.of(java.util.List.of()); }
        @Override public Promise<Long> countByWorkspace(DmWorkspaceId w) { return Promise.of(0L); }
    }

    // ─── Helper classes for testing ──────────────────────────────────────────

    private static class DeterministicTestAdapter {
        // class name contains "Deterministic" — triggers STUB-001
    }

    private static class StubTestAdapter {
        // class name contains "Stub" — triggers STUB-001
    }

    private static class FakeTestAdapter {
        // class name contains "Fake" — triggers STUB-001
    }

    private static class MockTestAdapter {
        // class name contains "Mock" — triggers STUB-001
    }

    private static class RealProductionAdapter {
        // Real production implementation — no stub keywords
    }

    private static class MinimalKernelAdapter implements DigitalMarketingKernelAdapter {
        @Override
        public void start() {}
        @Override
        public void stop() {}
        @Override
        public Promise<Boolean> isAuthorized(com.ghatana.digitalmarketing.contracts.DmOperationContext context, String resource, String action) {
            return Promise.of(true);
        }
        @Override
        public Promise<Boolean> verifyConsent(com.ghatana.digitalmarketing.contracts.DmOperationContext context, String subjectId, String purpose) {
            return Promise.of(true);
        }
        @Override
        public Promise<String> requestApproval(com.ghatana.digitalmarketing.contracts.DmOperationContext context, String operationType, String subjectId, String description) {
            return Promise.of("approved");
        }
        @Override
        public Promise<String> recordAudit(com.ghatana.digitalmarketing.contracts.DmOperationContext context, String entityId, String action, java.util.Map<String, Object> attributes) {
            return Promise.of("audit-recorded");
        }
    }

    private static class ProductionOutboxExecutor {
        // A "real" outbox executor — no stub/test/fake naming patterns
    }

    private static class TestPackageAdapter {
        // toString returns a test-package path — triggers STUB-002
        @Override
        public String toString() {
            return "com.example.test.package.TestPackageAdapter";
        }
    }
}
