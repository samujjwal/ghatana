package com.ghatana.digitalmarketing.application;

import com.ghatana.digitalmarketing.application.bootstrap.ProductionBootstrapValidator;
import com.ghatana.digitalmarketing.application.campaign.CampaignRepository;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runtime composition proof for production bootstrap validation.
 *
 * @doc.type class
 * @doc.purpose Runtime proof that production persistence wiring is validated via real object graph checks
 * @doc.layer test
 * @doc.pattern IntegrationTest
 */
@DisplayName("P1-049: Production Persistence Wiring Proof")
class ProductionPersistenceWiringProofTest {

    private static final String LONG_KEY = "0123456789abcdef0123456789abcdef";

    @Test
    @DisplayName("P1-049: production validator passes with runtime-wired durable dependencies")
    void shouldPassWithDurableRuntimeDependencies() throws Exception {
        DataSource dataSource = new AlwaysValidDataSource();
        CampaignRepository campaignRepository = new PostgresCampaignRepositoryDouble();
        DigitalMarketingKernelAdapter kernelAdapter = new MinimalKernelAdapterDouble();
        ProductionBootstrapValidator validator = new ProductionBootstrapValidator.Builder()
            .isProduction(true)
            .dataSource(dataSource)
            .campaignRepository(campaignRepository)
            .kernelAdapter(kernelAdapter)
            .piiHmacKey(LONG_KEY)
            .contactEncryptionKey(LONG_KEY)
            .googleAdsOutboxExecutor(new RuntimeOutboxExecutor())
            .build();

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("P1-049: production validator rejects in-memory repository wiring")
    void shouldRejectEphemeralRepositoryInProduction() throws Exception {
        DataSource dataSource = new AlwaysValidDataSource();
        ProductionBootstrapValidator validator = new ProductionBootstrapValidator.Builder()
            .isProduction(true)
            .dataSource(dataSource)
            .campaignRepository(new EphemeralCampaignRepositoryStub())
            .kernelAdapter(new MinimalKernelAdapterDouble())
            .piiHmacKey(LONG_KEY)
            .contactEncryptionKey(LONG_KEY)
            .googleAdsOutboxExecutor(new RuntimeOutboxExecutor())
            .build();

        assertThatThrownBy(validator::validate)
            .isInstanceOf(ProductionBootstrapValidator.ProductionBootstrapException.class)
            .hasMessageContaining("PERSISTENCE-002");
    }

    @Test
    @DisplayName("P1-049: production validator rejects missing outbox executor")
    void shouldRejectMissingOutboxExecutor() throws Exception {
        DataSource dataSource = new AlwaysValidDataSource();
        ProductionBootstrapValidator validator = new ProductionBootstrapValidator.Builder()
            .isProduction(true)
            .dataSource(dataSource)
            .campaignRepository(new PostgresCampaignRepositoryDouble())
            .kernelAdapter(new MinimalKernelAdapterDouble())
            .piiHmacKey(LONG_KEY)
            .contactEncryptionKey(LONG_KEY)
            .googleAdsOutboxExecutor(null)
            .build();

        assertThatThrownBy(validator::validate)
            .isInstanceOf(ProductionBootstrapValidator.ProductionBootstrapException.class)
            .hasMessageContaining("INTEGRATION-001");
    }

    private static final class RuntimeOutboxExecutor {
    }

    /** DataSource double that always returns a valid connection. */
    private static final class AlwaysValidDataSource implements DataSource {
        @Override public Connection getConnection() throws SQLException { return new ValidConnection(); }
        @Override public Connection getConnection(String user, String pw) throws SQLException { return getConnection(); }
        @Override public PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(PrintWriter out) {}
        @Override public void setLoginTimeout(int seconds) {}
        @Override public int getLoginTimeout() { return 0; }
        @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { throw new SQLFeatureNotSupportedException(); }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException(); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }

    /** Connection double that succeeds on close() and isValid(). */
    private static final class ValidConnection implements Connection {
        @Override public boolean isValid(int timeout) { return true; }
        @Override public void close() {}
        @Override public boolean isClosed() { return false; }
        @Override public java.sql.Statement createStatement() { throw new UnsupportedOperationException(); }
        @Override public java.sql.PreparedStatement prepareStatement(String sql) { throw new UnsupportedOperationException(); }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, int ag) { throw new UnsupportedOperationException(); }
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

    /** CampaignRepository double whose class name does not contain "Ephemeral". */
    private static final class PostgresCampaignRepositoryDouble implements CampaignRepository {
        @Override public Promise<Campaign> save(Campaign c) { throw new UnsupportedOperationException(); }
        @Override public Promise<Optional<Campaign>> findById(DmWorkspaceId w, String id) { return Promise.of(Optional.empty()); }
        @Override public Promise<List<Campaign>> listByWorkspace(DmWorkspaceId w, int limit, int offset) { return Promise.of(List.of()); }
        @Override public Promise<Long> countByWorkspace(DmWorkspaceId w) { return Promise.of(0L); }
    }

    /** Minimal DigitalMarketingKernelAdapter double. */
    private static final class MinimalKernelAdapterDouble implements DigitalMarketingKernelAdapter {
        @Override public void start() {}
        @Override public void stop() {}
        @Override public Promise<Boolean> isAuthorized(DmOperationContext ctx, String resource, String action) { return Promise.of(true); }
        @Override public Promise<Boolean> verifyConsent(DmOperationContext ctx, String subjectId, String purpose) { return Promise.of(true); }
        @Override public Promise<String> requestApproval(DmOperationContext ctx, String opType, String subjectId, String desc) { return Promise.of("approved"); }
        @Override public Promise<String> recordAudit(DmOperationContext ctx, String entityId, String action, Map<String, Object> attrs) { return Promise.of("recorded"); }
    }

    private static final class EphemeralCampaignRepositoryStub implements CampaignRepository {
        @Override
        public io.activej.promise.Promise<com.ghatana.digitalmarketing.domain.campaign.Campaign> save(
                com.ghatana.digitalmarketing.domain.campaign.Campaign campaign) {
            throw new UnsupportedOperationException("Not used in this wiring proof");
        }

        @Override
        public io.activej.promise.Promise<java.util.Optional<com.ghatana.digitalmarketing.domain.campaign.Campaign>> findById(
                com.ghatana.digitalmarketing.contracts.DmWorkspaceId workspaceId,
                String campaignId) {
            throw new UnsupportedOperationException("Not used in this wiring proof");
        }

        @Override
        public io.activej.promise.Promise<java.util.List<com.ghatana.digitalmarketing.domain.campaign.Campaign>> listByWorkspace(
                com.ghatana.digitalmarketing.contracts.DmWorkspaceId workspaceId,
                int limit,
                int offset) {
            throw new UnsupportedOperationException("Not used in this wiring proof");
        }

        @Override
        public io.activej.promise.Promise<Long> countByWorkspace(
                com.ghatana.digitalmarketing.contracts.DmWorkspaceId workspaceId) {
            throw new UnsupportedOperationException("Not used in this wiring proof");
        }
    }
}
