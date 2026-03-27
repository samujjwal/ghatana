package com.ghatana.platform.testing.utils;

import com.ghatana.platform.core.service.BaseService;
import io.activej.promise.Promise;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testing utilities for services implementing {@link BaseService} and similar lifecycle contracts.
 *
 * <p>Provides helpers for:
 * <ul>
 *   <li>Waiting for async initialization to complete</li>
 *   <li>Asserting service health status</li>
 *   <li>Verifying clean lifecycle (init → healthy → shutdown)</li>
 * </ul>
 *
 * <p>For async assertions, use within a class that extends {@link EventloopTestBase}:
 * <pre>{@code
 * class MyServiceTest extends EventloopTestBase {
 *
 *     {@literal @}Test
 *     void serviceInitializesSuccessfully() {
 *         MyService svc = new MyService();
 *         runPromise(svc::initialize);
 *         ServiceTestUtils.assertHealthy(svc);
 *     }
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Test utilities for service lifecycle and health assertions
 * @doc.layer platform
 * @doc.pattern Utility, TestHelper
 *
 * @since 2026-03-27
 */
public final class ServiceTestUtils {

    private ServiceTestUtils() {} // Utility class

    /**
     * Asserts that the service reports a HEALTHY status.
     *
     * @param service the service under test
     */
    public static void assertHealthy(BaseService service) {
        BaseService.ServiceHealth health = service.getHealthStatus();
        assertThat(health).as("service health must not be null").isNotNull();
        assertThat(health.status())
            .as("expected HEALTHY, got %s: %s", health.status(), health.message())
            .isEqualTo(BaseService.ServiceHealth.Status.HEALTHY);
    }

    /**
     * Asserts that the service reports a DEGRADED or HEALTHY status (i.e., not UNHEALTHY).
     *
     * @param service the service under test
     */
    public static void assertOperational(BaseService service) {
        BaseService.ServiceHealth health = service.getHealthStatus();
        assertThat(health).as("service health must not be null").isNotNull();
        assertThat(health.status())
            .as("expected HEALTHY or DEGRADED, got %s: %s", health.status(), health.message())
            .isNotEqualTo(BaseService.ServiceHealth.Status.UNHEALTHY);
    }

    /**
     * Asserts that the service reports an UNHEALTHY status.
     *
     * @param service the service under test
     */
    public static void assertUnhealthy(BaseService service) {
        BaseService.ServiceHealth health = service.getHealthStatus();
        assertThat(health).as("service health must not be null").isNotNull();
        assertThat(health.status())
            .as("expected UNHEALTHY, got %s: %s", health.status(), health.message())
            .isEqualTo(BaseService.ServiceHealth.Status.UNHEALTHY);
    }

    /**
     * Asserts that the service info map contains the given key.
     *
     * @param service the service under test
     * @param key     the key that must be present
     */
    public static void assertServiceInfoContains(BaseService service, String key) {
        assertThat(service.getServiceInfo())
            .as("service info must contain key '%s'", key)
            .containsKey(key);
    }

    /**
     * Verifies the full lifecycle of a service: initialize → healthy → shutdown.
     *
 * This method is suitable for use inside an {@code EventloopTestBase} test.
     * Pass a lambda in {@code duringHealthy} to run assertions after initialization.
     *
     * @param service the service to exercise
     */
    public static void exerciseLifecycle(BaseService service, java.util.function.Consumer<BaseService> duringHealthy) {
        // Initialize
        Promise<Void> initPromise = service.initialize();
        assertThat(initPromise).as("initialize() must return a non-null promise").isNotNull();

        // Allow caller to run assertions while healthy
        if (duringHealthy != null) {
            duringHealthy.accept(service);
        }

        // Shutdown
        Promise<Void> shutdownPromise = service.shutdown();
        assertThat(shutdownPromise).as("shutdown() must return a non-null promise").isNotNull();
    }

    /**
     * Creates a simple always-healthy stub BaseService with the given service info.
     *
     * @param name the service name to include in info
     * @return a stub {@link BaseService}
     */
    public static BaseService stubHealthy(String name) {
        return new BaseService() {
            @Override
            public BaseService.ServiceHealth getHealthStatus() {
                return BaseService.ServiceHealth.healthy(name + " is healthy");
            }

            @Override
            public java.util.Map<String, Object> getServiceInfo() {
                return java.util.Map.of("name", name, "status", "HEALTHY");
            }
        };
    }
}
