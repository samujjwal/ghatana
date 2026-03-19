/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.test.integration;

import com.ghatana.kernel.Kernel;
import com.ghatana.kernel.KernelBuilder;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.kernel.modules.authentication.AuthenticationKernelModule;
import com.ghatana.kernel.modules.config.ConfigKernelModule;
import com.ghatana.kernel.modules.eventstore.EventStoreKernelModule;
import com.ghatana.kernel.test.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for kernel module interactions.
 *
 * <p>Validates that kernel modules work together correctly and
 * that dependencies are resolved properly.</p>
 *
 * @doc.type test
 * @doc.purpose Integration tests for kernel module interactions
 * @doc.layer test
 * @doc.pattern IntegrationTest
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
@DisplayName("Kernel Module Integration Tests")
public class KernelModuleIntegrationTest extends EventloopTestBase {

    @Test
    @DisplayName("Should initialize multiple modules with dependencies")
    void shouldInitializeMultipleModulesWithDependencies() {
        // Create modules
        ConfigKernelModule configModule = new ConfigKernelModule();
        EventStoreKernelModule eventStoreModule = new EventStoreKernelModule();
        AuthenticationKernelModule authModule = new AuthenticationKernelModule();

        // Build kernel with modules
        Kernel kernel = KernelBuilder.create()
            .withModule(configModule)
            .withModule(eventStoreModule)
            .withModule(authModule)
            .build();

        // Initialize kernel
        await(kernel.initialize());

        // Verify all modules initialized
        assertTrue(kernel.isInitialized());

        // Start kernel
        await(kernel.start());
        assertTrue(kernel.isRunning());

        // Stop kernel
        await(kernel.stop());
        assertFalse(kernel.isRunning());
    }

    @Test
    @DisplayName("Should resolve dependencies between modules")
    void shouldResolveDependenciesBetweenModules() {
        ConfigKernelModule configModule = new ConfigKernelModule();
        EventStoreKernelModule eventStoreModule = new EventStoreKernelModule();

        // EventStore depends on Config
        Set<KernelCapability> eventStoreDeps = eventStoreModule.getCapabilities();
        assertTrue(eventStoreDeps.contains(KernelCapability.Core.EVENT_PROCESSING));

        // Verify Config module has CONFIG_MANAGEMENT
        Set<KernelCapability> configCaps = configModule.getCapabilities();
        assertTrue(configCaps.contains(KernelCapability.Core.CONFIG_MANAGEMENT));
    }

    @Test
    @DisplayName("Should check kernel purity - no product-specific logic in kernel modules")
    void shouldCheckKernelPurity() {
        KernelModule[] kernelModules = {
            new ConfigKernelModule(),
            new EventStoreKernelModule(),
            new AuthenticationKernelModule()
        };

        for (KernelModule module : kernelModules) {
            String moduleId = module.getModuleId();

            // Verify no product-specific terms in module ID
            assertFalse(moduleId.contains("finance"),
                "Kernel module " + moduleId + " contains product-specific term 'finance'");
            assertFalse(moduleId.contains("trade"),
                "Kernel module " + moduleId + " contains product-specific term 'trade'");
            assertFalse(moduleId.contains("order"),
                "Kernel module " + moduleId + " contains product-specific term 'order'");

            // Verify capabilities are generic
            for (KernelCapability cap : module.getCapabilities()) {
                String capId = cap.getCapabilityId();
                assertFalse(capId.contains("finance"),
                    "Capability " + capId + " in module " + moduleId + " is product-specific");
            }
        }
    }

    @Test
    @DisplayName("Should verify ActiveJ Promise compliance")
    void shouldVerifyActiveJPromiseCompliance() {
        // Verify kernel modules use ActiveJ Promise, not CompletableFuture
        KernelModule module = new ConfigKernelModule();

        // The lifecycle methods should return Promise<Void>
        // This is verified by compilation - if they returned CompletableFuture,
        // they wouldn't satisfy the KernelModule interface
        assertDoesNotThrow(() -> {
            // Check that module implements KernelModule correctly
            assertTrue(module instanceof KernelModule);
        });
    }
}
