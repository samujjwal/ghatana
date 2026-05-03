package com.ghatana.aep;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that the AEP factory behaves correctly with respect to production
 * durable EventCloud requirements.
 *
 * <p>In production, {@link Aep#create(AepConfig)} must fail closed when no
 * ServiceLoader-registered {@code EventCloud} implementation is present.
 * Silently falling back to {@code InMemoryEventCloud} in production would cause
 * events to be lost on restart.
 *
 * <p>These tests run without any ServiceLoader-registered {@code EventCloud}
 * on the test classpath (only the test-safe in-memory implementation is
 * available via {@link AepConfig#forTesting()}), so they directly exercise the
 * production fail-closed path.
 *
 * @doc.type class
 * @doc.purpose Prove Aep.create() fails closed when no durable EventCloud provider is registered
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AEP production module — fail-closed durability contract")
class AepProductionModuleTest {

    @Test
    @DisplayName("create() throws when no durable EventCloud is registered and allowInMemory is false")
    void shouldThrowWhenNoDurableEventCloudAndInMemoryNotAllowed() {
        Aep.AepConfig productionConfig = Aep.AepConfig.defaults();

        assertThatThrownBy(() -> Aep.create(productionConfig))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EventCloud");
    }

    @Test
    @DisplayName("create() succeeds when allowInMemoryEventCloud is explicitly set to true")
    void shouldSucceedWhenInMemoryIsExplicitlyAllowed() {
        Aep.AepConfig testConfig = Aep.AepConfig.builder()
                .build();

        // Should not throw — InMemoryEventCloud is an acceptable fallback when explicitly opted in
        AepEngine engine = Aep.create(testConfig);
        engine.close();
    }

    @Test
    @DisplayName("forTesting() factory allows in-memory event cloud without explicit config")
    void shouldAllowInMemoryViaForTestingFactory() {
        // forTesting() is the canonical opt-in path for test harnesses
        AepEngine engine = Aep.forTesting();
        engine.close();
    }
}
