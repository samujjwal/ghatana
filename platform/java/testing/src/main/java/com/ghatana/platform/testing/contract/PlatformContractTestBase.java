package com.ghatana.platform.testing.contract;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Tag;

import java.util.function.Supplier;

/**
 * Base class for cross-product and product-to-platform contract tests.
 *
 * <h2>Purpose</h2>
 * <p>Platform contract tests verify that the interface agreed between two modules
 * (producer side and consumer side) does not break silently when either side
 * evolves. This is the enforcement layer that prevents platform API drift from
 * accumulating between teams.
 *
 * <h2>What to test here</h2>
 * <ul>
 *   <li><strong>Shape contracts</strong> — call a platform API and assert that the
 *       response record fields are present and typed correctly (no field removals
 *       without a deprecation cycle).</li>
 *   <li><strong>Behaviour contracts</strong> — given a documented precondition, the
 *       platform module returns the documented result or throws the documented
 *       exception.</li>
 *   <li><strong>Tenant-isolation contracts</strong> — a request with tenant A's
 *       credentials cannot read, modify, or list tenant B's resources.</li>
 *   <li><strong>Error-contract invariants</strong> — error responses contain the
 *       mandatory fields: {@code code}, {@code message}, {@code requestId}.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @DisplayName("Event-Cloud SPI Contract — Producer: AEP, Consumer: Data-Cloud")
 * class EventCloudSpiContractTest extends PlatformContractTestBase {
 *
 *     private EventLogStore store;
 *
 *     @Override
 *     protected void setUpContract() {
 *         store = new InMemoryEventLogStore(); // or Testcontainers-backed
 *     }
 *
 *     @ContractTest(
 *         producerModule = "platform:java:event-cloud",
 *         consumerModule = "products:data-cloud"
 *     )
 *     void appendedEventIsReadableByConsumer() {
 *         EventRecord event = EventRecord.of("test.entity.created", "{}");
 *         String result = runPromise(() -> store.append("tenantA", event)
 *             .then(id -> store.findById("tenantA", id))
 *             .map(found -> found.map(EventRecord::type).orElse("NOT_FOUND")));
 *         assertThat(result).isEqualTo("test.entity.created");
 *     }
 *
 *     @ContractTest(
 *         producerModule = "platform:java:event-cloud",
 *         consumerModule = "products:data-cloud"
 *     )
 *     void tenantIsolationPreventsReadAcrossTenants() {
 *         EventRecord event = EventRecord.of("secret.event", "{}");
 *         runPromise(() -> store.append("tenantA", event));
 *         long count = runPromise(() -> store.count("tenantB"));
 *         assertThat(count).isZero();
 *     }
 * }
 * }</pre>
 *
 * <h2>Tagging</h2>
 * <p>All subclass tests inherit the {@code "contract"} JUnit 5 tag, allowing
 * CI to run contract tests as a separate step:
 * <pre>{@code ./gradlew test -Ptest.tags=contract}</pre>
 *
 * <h2>Relationship to integration tests</h2>
 * <p>Contract tests are a subset of integration tests focused exclusively on
 * <em>boundary semantics</em> rather than end-to-end scenarios. They should be
 * fast (milliseconds to seconds), not requiring a full service startup.
 *
 * @doc.type class
 * @doc.purpose Base class for cross-product platform contract verification
 * @doc.layer platform
 * @doc.pattern TestBase, Contract
 */
@Tag("contract")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public abstract class PlatformContractTestBase extends EventloopTestBase {

    /**
     * Hook called once before the test class's contract tests run.
     * Override to instantiate the platform service under test (production or
     * in-memory stub).
     *
     * <p>Default implementation is a no-op; override as needed.
     */
    protected void setUpContract() {
        // no-op by default
    }

    /**
     * Asserts that a supplier does not throw any exception, returning its value.
     * Convenience helper for lambda-based contract assertions.
     *
     * @param supplier the supplier to invoke
     * @param <T>      return type
     * @return the returned value
     */
    protected static <T> T assertDoesNotThrow(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            throw new AssertionError("Expected no exception but got: " + e.getMessage(), e);
        }
    }

    /**
     * Asserts that two tenant IDs produce isolated results from the provided
     * count supplier. Used to enforce tenant-isolation contracts.
     *
     * @param expectedCount the expected item count for the <em>other</em> tenant
     * @param actualCount   the actual count observed for the other tenant
     * @param label         a human-readable label for the assertion message
     */
    protected static void assertTenantIsolation(long expectedCount, long actualCount, String label) {
        if (actualCount != expectedCount) {
            throw new AssertionError(
                "Tenant-isolation violation for [%s]: expected count=%d but got count=%d. "
                    + "Tenant B must not observe Tenant A's data."
                        .formatted(label, expectedCount, actualCount));
        }
    }
}
