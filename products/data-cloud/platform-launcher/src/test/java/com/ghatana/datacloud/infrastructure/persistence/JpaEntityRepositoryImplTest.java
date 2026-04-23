package com.ghatana.datacloud.infrastructure.persistence;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;

/**
 * Regression tests for {@link JpaEntityRepositoryImpl} intentional design constraints.
 *
 * <p>The key behavioral contract under test is the <b>fast-fail rule</b>:
 * calling {@code findAll()} with a non-empty filter map must immediately reject // GH-90000
 * the returned Promise with {@link UnsupportedOperationException}. This is
 * intentional — silently ignoring filters would return over-broad data sets,
 * which is more dangerous than a clear failure.
 *
 * <p>Callers requiring filtered results must use {@code findByQuery()} with // GH-90000
 * {@code DynamicQueryBuilder} instead.
 *
 * @doc.type test
 * @doc.purpose Regression coverage for findAll() fast-fail constraint on non-empty filters // GH-90000
 * @doc.layer infrastructure
 * @doc.pattern Repository
 */
@DisplayName("JpaEntityRepositoryImpl Regression Tests")
class JpaEntityRepositoryImplTest extends EventloopTestBase {

    private JpaEntityRepositoryImpl repository;

    @BeforeEach
    void setUp() { // GH-90000
        // Use a real virtual-thread executor; it is never invoked for fast-fail paths.
        repository = new JpaEntityRepositoryImpl(Executors.newVirtualThreadPerTaskExecutor()); // GH-90000
    }

    // =========================================================================
    // FAST-FAIL: findAll() with non-empty filter // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("findAll() filter fast-fail constraint")
    class FindAllFilterFastFail {

        @Test
        @DisplayName("should reject Promise with UnsupportedOperationException when filter is non-empty")
        void shouldRejectWhenFilterIsNonEmpty() { // GH-90000
            Map<String, Object> nonEmptyFilter = Map.of("status", "active"); // GH-90000

            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> repository.findAll( // GH-90000
                            "tenant-1",
                            "orders",
                            nonEmptyFilter,
                            null,
                            0,
                            100)))
                    .isInstanceOf(UnsupportedOperationException.class) // GH-90000
                    .hasMessageContaining("findAll()")
                    .hasMessageContaining("findByQuery()");
        }

        @Test
        @DisplayName("should reject Promise with UnsupportedOperationException for any key-value filter")
        void shouldRejectForAnyNonEmptyFilter() { // GH-90000
            Map<String, Object> multiKeyFilter = Map.of( // GH-90000
                    "category", "electronics",
                    "region", "eu-west"
            );

            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> repository.findAll( // GH-90000
                            "tenant-2",
                            "products",
                            multiKeyFilter,
                            "createdAt:DESC",
                            0,
                            50)))
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }

        @Test
        @DisplayName("should NOT reject Promise when filter is null")
        void shouldNotRejectWhenFilterIsNull() { // GH-90000
            // null filter is the permitted fast path — it proceeds to the blocking JPA call.
            // We cannot fully execute the JPA call without EntityManager, but verifying that
            // UnsupportedOperationException is NOT thrown validates the branch decision logic.
            // (The subsequent Promise.ofBlocking will fail with a separate NullPointerException // GH-90000
            // on the missing EntityManager — that is expected and unrelated to this constraint.)
            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> repository.findAll( // GH-90000
                            "tenant-1",
                            "orders",
                            null,
                            null,
                            0,
                            10)))
                    .isNotInstanceOf(UnsupportedOperationException.class); // GH-90000
        }

        @Test
        @DisplayName("should NOT reject Promise when filter is empty map")
        void shouldNotRejectWhenFilterIsEmpty() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> repository.findAll( // GH-90000
                            "tenant-1",
                            "orders",
                            Map.of(), // GH-90000
                            null,
                            0,
                            10)))
                    .isNotInstanceOf(UnsupportedOperationException.class); // GH-90000
        }

        @Test
        @DisplayName("should throw NullPointerException for null tenantId (constructor precondition)")
        void shouldThrowForNullTenantId() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> repository.findAll( // GH-90000
                            null,
                            "orders",
                            null,
                            null,
                            0,
                            10)))
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("should throw NullPointerException for null collectionName")
        void shouldThrowForNullCollectionName() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> repository.findAll( // GH-90000
                            "tenant-1",
                            null,
                            null,
                            null,
                            0,
                            10)))
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }
}
