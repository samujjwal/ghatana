package com.ghatana.kernel.test.integration;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for kernel module interactions.
 *
 * <p>Disabled pending: kernel module subprojects (authentication, config, event-store)
 * are not on the kernel test classpath. Move these tests to each module's own test suite.</p>
 *
 * @doc.type test
 * @doc.purpose Integration tests for kernel module interactions
 * @doc.layer test
 * @doc.pattern IntegrationTest
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
@Disabled("Kernel module subprojects are not on kernel test classpath")
@DisplayName("Kernel Module Integration Tests")
public class KernelModuleIntegrationTest extends EventloopTestBase {

    @Test
    @Disabled
    void placeholder() {
        // All tests disabled - module subprojects not on test classpath
    }
}
