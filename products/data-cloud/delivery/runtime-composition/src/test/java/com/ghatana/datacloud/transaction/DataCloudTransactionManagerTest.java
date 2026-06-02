package com.ghatana.datacloud.transaction;

import com.ghatana.datacloud.spi.TransactionException;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DC-BE-003: Transaction boundary tests for DataCloudTransactionManager.
 * DC-DATA-003: Transaction semantics hardening tests.
 * DC-DATA-004: Transaction failure rollback tests.
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

    // ==================== DC-DATA-003: Transaction Semantics Hardening Tests ====================

    @Test
    @DisplayName("DC-DATA-003: Transaction manager enforces positive timeout")
    void transactionManagerEnforcesPositiveTimeout() {
        assertThatThrownBy(() -> new DataCloudTransactionManager(0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("positive");
        
        assertThatThrownBy(() -> new DataCloudTransactionManager(-100))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("positive");
    }

    @Test
    @DisplayName("DC-DATA-003: Transaction manager accepts custom timeout")
    void transactionManagerAcceptsCustomTimeout() {
        DataCloudTransactionManager customManager = new DataCloudTransactionManager(60000); // 60 seconds
        String result = runPromise(() -> customManager.executeInTransaction(
            "tenant-123",
            () -> Promise.of("ok")
        ));
        assertThat(result).isEqualTo("ok");
    }

    @Test
    @DisplayName("DC-DATA-003: Transaction context tracks tenant ID")
    void transactionContextTracksTenantId() {
        String result = runPromise(() -> transactionManager.executeInTransactionWithContext(
            "tenant-abc",
            context -> {
                assertThat(context.tenantId()).isEqualTo("tenant-abc");
                assertThat(context.transactionId()).isNotNull();
                return Promise.of("tracked");
            }
        ));
        assertThat(result).isEqualTo("tracked");
    }

    // ==================== DC-DATA-004: Transaction Failure Rollback Tests ====================

    @Test
    @DisplayName("DC-DATA-004: Rollback handlers execute in LIFO order")
    void rollbackHandlersExecuteInLIFOOrder() {
        List<String> executionOrder = new ArrayList<>();

        assertThatThrownBy(() -> runPromise(() -> transactionManager.executeInTransactionWithContext(
            "tenant-123",
            context -> {
                // Register handlers in order 1, 2, 3
                context.registerRollbackHandler(() -> executionOrder.add("handler-1"));
                context.registerRollbackHandler(() -> executionOrder.add("handler-2"));
                context.registerRollbackHandler(() -> executionOrder.add("handler-3"));
                
                // Fail to trigger rollback
                return Promise.ofException(new RuntimeException("fail"));
            }
        )))
            .isInstanceOf(TransactionException.class);

        // Verify rollback executed in reverse order (LIFO)
        assertThat(executionOrder).containsExactly("handler-3", "handler-2", "handler-1");
    }

    @Test
    @DisplayName("DC-DATA-004: Multiple rollback handlers all execute on failure")
    void multipleRollbackHandlersAllExecuteOnFailure() {
        AtomicInteger rollbackCount = new AtomicInteger(0);

        assertThatThrownBy(() -> runPromise(() -> transactionManager.executeInTransactionWithContext(
            "tenant-123",
            context -> {
                // Register multiple handlers
                for (int i = 0; i < 5; i++) {
                    final int index = i;
                    context.registerRollbackHandler(() -> rollbackCount.incrementAndGet());
                }
                
                return Promise.ofException(new RuntimeException("fail"));
            }
        )))
            .isInstanceOf(TransactionException.class);

        // All 5 handlers should have executed
        assertThat(rollbackCount.get()).isEqualTo(5);
    }

    @Test
    @DisplayName("DC-DATA-004: Rollback handlers not executed on success")
    void rollbackHandlersNotExecutedOnSuccess() {
        AtomicBoolean rollbackCalled = new AtomicBoolean(false);

        String result = runPromise(() -> transactionManager.executeInTransactionWithContext(
            "tenant-123",
            context -> {
                context.registerRollbackHandler(() -> rollbackCalled.set(true));
                return Promise.of("success");
            }
        ));

        assertThat(result).isEqualTo("success");
        assertThat(rollbackCalled).isFalse();
    }

    @Test
    @DisplayName("DC-DATA-004: Transaction exception includes rollback status")
    void transactionExceptionIncludesRollbackStatus() {
        AtomicBoolean rollbackExecuted = new AtomicBoolean(false);

        TransactionException exception = assertThatThrownBy(() -> runPromise(() -> transactionManager.executeInTransactionWithContext(
            "tenant-123",
            context -> {
                context.registerRollbackHandler(() -> rollbackExecuted.set(true));
                return Promise.ofException(new RuntimeException("fail"));
            }
        )))
            .isInstanceOf(TransactionException.class)
            .extracting(e -> (TransactionException) e)
            .returns(true, e -> e.rollbackStatus() == TransactionException.RollbackStatus.COMPLETED);

        assertThat(rollbackExecuted).isTrue();
    }

    @Test
    @DisplayName("DC-DATA-004: Synchronous exception triggers rollback")
    void synchronousExceptionTriggersRollback() {
        AtomicBoolean rollbackCalled = new AtomicBoolean(false);

        assertThatThrownBy(() -> runPromise(() -> transactionManager.executeInTransactionWithContext(
            "tenant-123",
            context -> {
                context.registerRollbackHandler(() -> rollbackCalled.set(true));
                throw new RuntimeException("sync fail");
            }
        )))
            .isInstanceOf(TransactionException.class);

        assertThat(rollbackCalled).isTrue();
    }
}
