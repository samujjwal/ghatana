package com.ghatana.core.database.jdbc;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

/**
 * Production-grade abstract base DataSource simplifying custom DataSource implementations.
 *
 * <p><b>Purpose</b><br>
 * Provides default implementations of boilerplate javax.sql.DataSource methods,
 * allowing subclasses to focus only on connection creation logic. Handles
 * JDBC wrapper chain, optional features, and logging boilerplate.
 *
 * <p><b>Architecture Role</b><br>
 * Base adapter in core/database/jdbc for custom DataSource implementations.
 * Used by:
 * - Custom Connection Pools - Implement lightweight pooling
 * - Test DataSources - Simple testing implementations
 * - Delegating DataSources - Proxy/routing DataSources
 * - Mock DataSources - Testing with mocked connections
 * - Migration DataSources - Temporary connection adapters
 *
 * <p><b>Default Implementations</b><br>
 * - <b>getLogWriter/setLogWriter</b>: Unsupported (throws SQLFeatureNotSupportedException)
 * - <b>setLoginTimeout/getLoginTimeout</b>: Unsupported (timeout 0 = system default)
 * - <b>getParentLogger()</b>: Returns global logger
 * - <b>unwrap(Class)</b>: JDBC wrapper pattern support
 * - <b>isWrapperFor(Class)</b>: Check if wraps given interface
 *
 * <p><b>Subclass Responsibilities</b><br>
 * Must implement:
 * - <b>getConnection()</b>: Create connection without credentials
 * - <b>getConnection(username, password)</b>: Create connection with credentials
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // 1. Simple test DataSource
 * public class SimpleDataSource extends AbstractDataSource {
 *     private final String jdbcUrl;
 *     private final String username;
 *     private final String password;
 *     
 *     public SimpleDataSource(String jdbcUrl, String username, String password) {
 *         this.jdbcUrl = jdbcUrl;
 *         this.username = username;
 *         this.password = password;
 *     }
 *     
 *     @Override
 *     public Connection getConnection() throws SQLException {
 *         return DriverManager.getConnection(jdbcUrl, username, password);
 *     }
 *     
 *     @Override
 *     public Connection getConnection(String username, String password) throws SQLException {
 *         return DriverManager.getConnection(jdbcUrl, username, password);
 *     }
 * }
 *
 * // 2. Routing DataSource (multi-tenant)
 * public class RoutingDataSource extends AbstractDataSource {
 *     private final Map<String, DataSource> tenantDataSources;
 *     
 *     @Override
 *     public Connection getConnection() throws SQLException {
 *         String tenantId = TenantContext.getCurrentTenantId();
 *         DataSource tenantDS = tenantDataSources.get(tenantId);
 *         if (tenantDS == null) {
 *             throw new SQLException("No DataSource for tenant: " + tenantId);
 *         }
 *         return tenantDS.getConnection();
 *     }
 *     
 *     @Override
 *     public Connection getConnection(String username, String password) throws SQLException {
 *         // Delegate to default implementation
 *         return getConnection();
 *     }
 * }
 *
 * // 3. Read-write splitting DataSource
 * public class ReadWriteDataSource extends AbstractDataSource {
 *     private final DataSource writeDataSource;
 *     private final DataSource readDataSource;
 *     
 *     @Override
 *     public Connection getConnection() throws SQLException {
 *         boolean isReadOnly = TransactionContext.isReadOnly();
 *         return isReadOnly 
 *             ? readDataSource.getConnection() 
 *             : writeDataSource.getConnection();
 *     }
 *     
 *     @Override
 *     public Connection getConnection(String username, String password) throws SQLException {
 *         // Not supported for read-write splitting
 *         throw new SQLFeatureNotSupportedException(
 *             "Read-write DataSource doesn't support credential override");
 *     }
 * }
 *
 * // 4. Connection counting DataSource
 * public class CountingDataSource extends AbstractDataSource {
 *     private final DataSource delegate;
 *     private final AtomicInteger activeConnections = new AtomicInteger(0);
 *     
 *     @Override
 *     public Connection getConnection() throws SQLException {
 *         activeConnections.incrementAndGet();
 *         Connection conn = delegate.getConnection();
 *         return new ConnectionWrapper(conn) {
 *             @Override
 *             public void close() throws SQLException {
 *                 activeConnections.decrementAndGet();
 *                 super.close();
 *             }
 *         };
 *     }
 *     
 *     @Override
 *     public Connection getConnection(String username, String password) throws SQLException {
 *         return getConnection(); // Ignore credentials
 *     }
 *     
 *     public int getActiveConnectionCount() {
 *         return activeConnections.get();
 *     }
 * }
 *
 * // 5. Lazy-initializing DataSource
 * public class LazyDataSource extends AbstractDataSource {
 *     private final Supplier<DataSource> dataSourceSupplier;
 *     private volatile DataSource delegate;
 *     
 *     @Override
 *     public Connection getConnection() throws SQLException {
 *         if (delegate == null) {
 *             synchronized (this) {
 *                 if (delegate == null) {
 *                     delegate = dataSourceSupplier.get();
 *                 }
 *             }
 *         }
 *         return delegate.getConnection();
 *     }
 *     
 *     @Override
 *     public Connection getConnection(String username, String password) throws SQLException {
 *         return getConnection();
 *     }
 * }
 *
 * // 6. Testing with mock connections
 * public class MockDataSource extends AbstractDataSource {
 *     private final Queue<Connection> connections = new ConcurrentLinkedQueue<>();
 *     
 *     public void addMockConnection(Connection conn) {
 *         connections.add(conn);
 *     }
 *     
 *     @Override
 *     public Connection getConnection() throws SQLException {
 *         Connection conn = connections.poll();
 *         if (conn == null) {
 *             throw new SQLException("No mock connections available");
 *         }
 *         return conn;
 *     }
 *     
 *     @Override
 *     public Connection getConnection(String username, String password) throws SQLException {
 *         return getConnection();
 *     }
 * }
 * }</pre>
 *
 * <p><b>JDBC Wrapper Pattern</b><br>
 * Supports JDBC 4.0+ wrapper interface:
 * <pre>{@code
 * DataSource ds = new MyDataSource();
 * 
 * // Check if wraps specific type
 * if (ds.isWrapperFor(HikariDataSource.class)) {
 *     HikariDataSource hikari = ds.unwrap(HikariDataSource.class);
 *     logger.info("Pool size: {}", hikari.getMaximumPoolSize());
 * }
 * }</pre>
 *
 * <p><b>Unsupported Features</b><br>
 * Default implementations throw SQLFeatureNotSupportedException for:
 * - <b>setLogWriter/getLogWriter</b>: Use SLF4J/Log4j2 instead of JDBC logging
 * - <b>setLoginTimeout</b>: Configure timeout at connection pool level
 *
 * <p><b>Login Timeout</b><br>
 * Returns 0 (system default timeout). Override if custom timeout needed:
 * <pre>{@code
 * @Override
 * public int getLoginTimeout() throws SQLException {
 *     return 30; // 30 seconds
 * }
 * }</pre>
 *
 * <p><b>Parent Logger</b><br>
 * Returns global logger by default. Override for class-specific logging:
 * <pre>{@code
 * @Override
 * public Logger getParentLogger() {
 *     return Logger.getLogger(MyDataSource.class.getName());
 * }
 * }</pre>
 *
 * <p><b>Best Practices</b><br>
 * - Implement getConnection() first, delegate getConnection(u, p) to it if credentials ignored
 * - Use constructor injection for delegate DataSource in decorators
 * - Make fields final for immutability where possible
 * - Document which methods throw SQLFeatureNotSupportedException
 * - Consider connection pooling for production (use HikariCP instead)
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safety depends on subclass implementation. AbstractDataSource
 * itself is stateless and thread-safe. Subclasses must ensure getConnection()
 * is thread-safe.
 *
 * @see javax.sql.DataSource
 * @see JdbcTemplate
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose Abstract base DataSource simplifying custom implementations
 * @doc.layer core
 * @doc.pattern Adapter
 */
public abstract class AbstractDataSource implements javax.sql.DataSource {

    @Override
    public abstract Connection getConnection() throws SQLException;

    @Override
    public abstract Connection getConnection(String username, String password) throws SQLException;

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        throw new SQLFeatureNotSupportedException("getLogWriter not supported");
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        throw new SQLFeatureNotSupportedException("setLogWriter not supported");
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        throw new SQLFeatureNotSupportedException("setLoginTimeout not supported");
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return 0; // 0 means use default system timeout
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return (T) this;
        }
        throw new SQLException("DataSource of type [" + getClass().getName() +
                "] cannot be unwrapped as [" + iface.getName() + "]");
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this);
    }
}
