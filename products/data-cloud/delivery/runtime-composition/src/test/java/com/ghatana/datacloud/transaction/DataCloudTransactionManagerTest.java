package com.ghatana.datacloud.transaction;

import com.ghatana.datacloud.spi.TransactionException;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DC-BE-003: Transaction boundary tests for DataCloudTransactionManager.
 *
 * @doc.type class
 * @doc.purpose Transaction boundary tests for DataCloudTransactionManager
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudTransactionManager Tests")
class DataCloudTransactionManagerTest extends EventloopTestBase {

    private final DataCloudTransactionManager transactionManager = new DataCloudTransactionManager();

    @Test
    @DisplayName("commits successful transactions")
    void commitsSuccessfulTransactions() {
        String result = runPromise(() -> transactionManager.executeInTransaction(
            "tenant-123",
            () -> Promise.of("ok")
        ));

        assertThat(result).isEqualTo("ok");
    }

    @Test
    @DisplayName("provides transaction context to operations")
    void providesTransactionContextToOperations() {
        AtomicBoolean contextSeen = new AtomicBoolean(false);

        String result = runPromise(() -> transactionManager.executeInTransactionWithContext(
            "tenant-123",
            context -> {
                contextSeen.set(true);
                assertThat(context).isNotNull();
                assertThat(context.tenantId()).isEqualTo("tenant-123");
                return Promise.of("created");
            }
        ));

        assertThat(result).isEqualTo("created");
        assertThat(contextSeen).isTrue();
    }

    @Test
    @DisplayName("rolls back registered handlers on failure")
    void rollsBackRegisteredHandlersOnFailure() {
        AtomicBoolean rollbackCalled = new AtomicBoolean(false);

        assertThatThrownBy(() -> runPromise(() -> transactionManager.executeInTransactionWithContext(
            "tenant-123",
            context -> {
                context.registerRollbackHandler(() -> rollbackCalled.set(true));
                return Promise.ofException(new IllegalStateException("boom"));
            }
        )))
            .isInstanceOf(TransactionException.class)
            .hasMessageContaining("transactional operation with context");

        assertThat(rollbackCalled).isTrue();
    }

    @Test
    @DisplayName("rejects null tenant IDs")
    void rejectsNullTenantIds() {
        assertThatThrownBy(() -> runPromise(() -> transactionManager.executeInTransaction(
            null,
            () -> Promise.of("ok")
        )))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("tenantId");
    }

    @Test
    @DisplayName("rejects null operations")
    void rejectsNullOperations() {
        assertThatThrownBy(() -> runPromise(() -> transactionManager.executeInTransaction(
            "tenant-123",
            null
        )))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("operation");
    }

    @Test
    @DisplayName("rejects null context operations")
    void rejectsNullContextOperations() {
        assertThatThrownBy(() -> runPromise(() -> transactionManager.executeInTransactionWithContext(
            "tenant-123",
            null
        )))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("operation");
    }
}
