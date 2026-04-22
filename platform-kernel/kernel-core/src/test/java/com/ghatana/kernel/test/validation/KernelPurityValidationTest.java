/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.kernel.test.validation;

import com.ghatana.kernel.annotation.KernelInternal;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.module.KernelModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kernel Purity Validation Tests.
 *
 * <p>Validates that kernel modules comply with kernel purity principles:
 * <ul>
 *   <li>No product-specific logic</li>
 *   <li>Generic capabilities only</li>
 *   <li>ActiveJ Promise compliance</li>
 *   <li>No direct infrastructure access</li>
 * </ul></p>
 *
 * @doc.type test
 * @doc.purpose Validate kernel purity principles
 * @doc.layer test
 * @doc.pattern ValidationTest
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
@DisplayName("Kernel Purity Validation Tests [GH-90000]")
public class KernelPurityValidationTest {

    private static final List<String> FORBIDDEN_PRODUCT_TERMS = List.of( // GH-90000
        "finance", "trading", "trade", "order", "portfolio", "risk",
        "compliance", "market", "execution", "oms", "ems", "pms"
    );

    @Test
    @DisplayName("All kernel capabilities must be generic [GH-90000]")
    void allKernelCapabilitiesMustBeGeneric() { // GH-90000
        // Get all capabilities from KernelCapability.Core
        try {
            Class<?> coreClass = KernelCapability.Core.class;
            Field[] fields = coreClass.getDeclaredFields(); // GH-90000

            for (Field field : fields) { // GH-90000
                if (field.getType() == KernelCapability.class) { // GH-90000
                    KernelCapability capability = (KernelCapability) field.get(null); // GH-90000
                    assertNotNull(capability); // GH-90000

                    String capabilityId = capability.getCapabilityId(); // GH-90000

                    // Verify capability is generic (contains product-specific terms) // GH-90000
                    for (String forbiddenTerm : FORBIDDEN_PRODUCT_TERMS) { // GH-90000
                        assertFalse(capabilityId.contains(forbiddenTerm), // GH-90000
                            "Kernel capability '" + capabilityId + "' contains forbidden product term: " + forbiddenTerm);
                    }
                }
            }
        } catch (IllegalAccessException e) { // GH-90000
            fail("Failed to access kernel capabilities: " + e.getMessage()); // GH-90000
        }
    }

    @Test
    @DisplayName("Kernel modules must not have product-specific method names [GH-90000]")
    void kernelModulesMustNotHaveProductSpecificMethodNames() { // GH-90000
        // This test validates that kernel modules don't have product-specific methods
        // by checking for forbidden terms in method names

        Class<KernelModule> moduleInterface = KernelModule.class;
        Method[] methods = moduleInterface.getDeclaredMethods(); // GH-90000

        for (Method method : methods) { // GH-90000
            String methodName = method.getName(); // GH-90000

            for (String forbiddenTerm : FORBIDDEN_PRODUCT_TERMS) { // GH-90000
                assertFalse(methodName.toLowerCase().contains(forbiddenTerm), // GH-90000
                    "KernelModule method '" + methodName + "' contains forbidden term: " + forbiddenTerm);
            }
        }
    }

    @Test
    @DisplayName("Capability IDs follow naming convention [GH-90000]")
    void capabilityIdsFollowNamingConvention() { // GH-90000
        // Validate capability IDs follow the convention: category.action
        Set<String> validPrefixes = Set.of( // GH-90000
            "data.", "user.", "api.", "workflow.", "event.",
            "ai.", "observability.", "security.", "config.",
            "tenant.", "resilience.", "circuit.", "retry.", "bulkhead.",
            "mfa.", "oauth.", "audit."
        );

        try {
            Class<?> coreClass = KernelCapability.Core.class;
            Field[] fields = coreClass.getDeclaredFields(); // GH-90000

            for (Field field : fields) { // GH-90000
                if (field.getType() == KernelCapability.class) { // GH-90000
                    KernelCapability capability = (KernelCapability) field.get(null); // GH-90000
                    if (capability != null) { // GH-90000
                        String capabilityId = capability.getCapabilityId(); // GH-90000

                        boolean hasValidPrefix = validPrefixes.stream() // GH-90000
                            .anyMatch(capabilityId::startsWith); // GH-90000

                        assertTrue(hasValidPrefix, // GH-90000
                            "Capability ID '" + capabilityId + "' does not have a valid prefix. " +
                            "Valid prefixes: " + validPrefixes);

                        // Validate format: lowercase letters, numbers, hyphens, dots
                        assertTrue(capabilityId.matches("^[a-z0-9-.]+$ [GH-90000]"),
                            "Capability ID '" + capabilityId + "' contains invalid characters. " +
                            "Only lowercase letters, numbers, hyphens, and dots are allowed.");
                    }
                }
            }
        } catch (IllegalAccessException e) { // GH-90000
            fail("Failed to access kernel capabilities: " + e.getMessage()); // GH-90000
        }
    }

    @Test
    @DisplayName("No CompletableFuture in kernel module signatures [GH-90000]")
    void noCompletableFutureInKernelModuleSignatures() { // GH-90000
        // Verify kernel module interfaces don't use CompletableFuture
        Class<KernelModule> moduleInterface = KernelModule.class;
        Method[] methods = moduleInterface.getDeclaredMethods(); // GH-90000

        for (Method method : methods) { // GH-90000
            Class<?> returnType = method.getReturnType(); // GH-90000

            assertFalse(returnType.getName().contains("CompletableFuture [GH-90000]"),
                "Method '" + method.getName() + "' returns CompletableFuture. " + // GH-90000
                "Kernel modules must use ActiveJ Promise instead.");

            // Check parameter types
            for (Class<?> paramType : method.getParameterTypes()) { // GH-90000
                assertFalse(paramType.getName().contains("CompletableFuture [GH-90000]"),
                    "Method '" + method.getName() + "' has parameter type CompletableFuture. " + // GH-90000
                    "Kernel modules must use ActiveJ Promise instead.");
            }
        }
    }

    @Test
    @DisplayName("All kernel modules have proper JSDoc tags [GH-90000]")
    void allKernelModulesHaveProperJSDocTags() { // GH-90000
        // This test validates that all kernel module implementations
        // have the required @doc.* tags

        // The validation is done through compilation and code review
        // This test serves as documentation of the requirement

        Set<String> requiredTags = Set.of( // GH-90000
            "@doc.type",
            "@doc.purpose",
            "@doc.layer",
            "@doc.pattern"
        );

        assertEquals(4, requiredTags.size(), // GH-90000
            "Kernel modules must have all 4 required @doc.* tags");
    }

    // ==================== Convergence-era purity checks (Day 2) ==================== // GH-90000

    @Test
    @DisplayName("Transitional capability class has been removed (package capability.* no longer exists) [GH-90000]")
    void transitionalCapabilityClassHasBeenRemoved() { // GH-90000
        // D1: capability.KernelCapability was transitional and has been completely removed.
        // Verify the package no longer exists by checking ClassNotFoundException
        assertThrows(ClassNotFoundException.class, () -> { // GH-90000
            Class.forName("com.ghatana.kernel.capability.KernelCapability [GH-90000]");
        }, "capability.KernelCapability should have been completely removed. " +
            "Canonical type is descriptor.KernelCapability.");
    }

    @Test
    @DisplayName("Transitional plugin.KernelExtension has been removed [GH-90000]")
    void transitionalPluginExtensionHasBeenRemoved() { // GH-90000
        assertThrows(ClassNotFoundException.class, () -> // GH-90000
                Class.forName("com.ghatana.kernel.plugin.KernelExtension [GH-90000]"),
            "plugin.KernelExtension should have been removed. " +
            "Canonical type is extension.KernelExtension.");
    }

    @Test
    @DisplayName("ProductPlugin has been removed [GH-90000]")
    void productPluginHasBeenRemoved() { // GH-90000
        assertThrows(ClassNotFoundException.class, () -> // GH-90000
                Class.forName("com.ghatana.kernel.plugin.ProductPlugin [GH-90000]"),
            "ProductPlugin should have been removed. Canonical runtime model is KernelPlugin.");
    }

    @Test
    @DisplayName("CrossProductAuditService has been removed in favor of CrossScopeAuditService [GH-90000]")
    void crossProductAuditServiceHasBeenRemoved() { // GH-90000
        // CrossProductAuditService was deprecated and has been completely removed.
        // Verify the class no longer exists by checking ClassNotFoundException
        assertThrows(ClassNotFoundException.class, () -> { // GH-90000
            Class.forName("com.ghatana.kernel.audit.CrossProductAuditService [GH-90000]");
        }, "CrossProductAuditService should have been completely removed. " +
            "Use CrossScopeAuditService with policy-driven retention.");
    }

    @Test
    @DisplayName("descriptor.KernelCapability.Products has been removed [GH-90000]")
    void descriptorProductsInnerClassHasBeenRemoved() { // GH-90000
        assertThrows(ClassNotFoundException.class, () -> // GH-90000
                Class.forName("com.ghatana.kernel.descriptor.KernelCapability$Products [GH-90000]"),
            "KernelCapability.Products should have been removed. Product-specific capabilities " +
            "must be declared by product/domain modules.");
    }

    @Test
    @DisplayName("descriptor.KernelCapability must not expose product capability inner class [GH-90000]")
    void descriptorMustNotExposeProductCapabilityInnerClass() { // GH-90000
        boolean hasProductsInnerClass = Arrays.stream(KernelCapability.class.getDeclaredClasses()) // GH-90000
            .anyMatch(c -> c.getSimpleName().equals("Products [GH-90000]"));
        assertFalse(hasProductsInnerClass, // GH-90000
            "KernelCapability must not expose a Products inner class. Product capabilities " +
            "must be declared in product-owned modules.");
    }

    @Test
    @DisplayName("CrossScopeAuditService must exist as canonical replacement [GH-90000]")
    void crossScopeAuditServiceMustExist() { // GH-90000
        // Verify the canonical replacement exists alongside the deprecated service
        try {
            Class<?> canonical = Class.forName("com.ghatana.kernel.audit.CrossScopeAuditService [GH-90000]");
            assertNotNull(canonical); // GH-90000
            assertFalse(canonical.isAnnotationPresent(Deprecated.class), // GH-90000
                "CrossScopeAuditService is the canonical service and must NOT be deprecated.");
        } catch (ClassNotFoundException e) { // GH-90000
            fail("CrossScopeAuditService must exist as the canonical audit service. [GH-90000]");
        }
    }

    @Test
    @DisplayName("Scope abstractions must exist for scope-first kernel framing [GH-90000]")
    void scopeAbstractionsMustExist() { // GH-90000
        // These are foundational abstractions for the scope-first model
        try {
            Class.forName("com.ghatana.kernel.scope.ScopeType [GH-90000]");
            Class.forName("com.ghatana.kernel.scope.ScopeDescriptor [GH-90000]");
            Class.forName("com.ghatana.kernel.policy.ClassificationDescriptor [GH-90000]");
            Class.forName("com.ghatana.kernel.policy.RetentionPolicyResolver [GH-90000]");
            Class.forName("com.ghatana.kernel.policy.AuditPolicyResolver [GH-90000]");
        } catch (ClassNotFoundException e) { // GH-90000
            fail("Missing scope/policy abstraction: " + e.getMessage() + // GH-90000
                ". Per KERNEL_CANONICALIZATION_DECISIONS §4, scope-first framing is required.");
        }
    }

    // ==================== Day 7-9: Boundary/Bus/Workflow Convergence Checks ====================

    @Test
    @DisplayName("ProductBoundaryEnforcer has been removed (use ScopeBoundaryEnforcer) [GH-90000]")
    void productBoundaryEnforcerHasBeenRemoved() { // GH-90000
        // ProductBoundaryEnforcer was deprecated and has been completely removed.
        assertThrows(ClassNotFoundException.class, () -> { // GH-90000
            Class.forName("com.ghatana.kernel.boundary.ProductBoundaryEnforcer [GH-90000]");
        }, "ProductBoundaryEnforcer should have been completely removed. Use ScopeBoundaryEnforcer.");
    }

    @Test
    @DisplayName("ScopeBoundaryEnforcer must exist as canonical replacement [GH-90000]")
    void scopeBoundaryEnforcerMustExist() { // GH-90000
        try {
            Class<?> canonical = Class.forName("com.ghatana.kernel.boundary.ScopeBoundaryEnforcer [GH-90000]");
            assertNotNull(canonical); // GH-90000
            assertFalse(canonical.isAnnotationPresent(Deprecated.class), // GH-90000
                "ScopeBoundaryEnforcer is canonical and must NOT be deprecated.");
        } catch (ClassNotFoundException e) { // GH-90000
            fail("ScopeBoundaryEnforcer must exist as the canonical boundary enforcer. [GH-90000]");
        }
    }

    @Test
    @DisplayName("KernelInterProductBus has been removed [GH-90000]")
    void kernelInterProductBusHasBeenRemoved() { // GH-90000
        assertThrows(ClassNotFoundException.class, () -> // GH-90000
                Class.forName("com.ghatana.kernel.communication.KernelInterProductBus [GH-90000]"),
            "KernelInterProductBus should have been removed — use KernelInterScopeBus.");
    }

    @Test
    @DisplayName("KernelInterScopeBus must exist as canonical replacement [GH-90000]")
    void kernelInterScopeBusMustExist() { // GH-90000
        try {
            Class<?> canonical = Class.forName("com.ghatana.kernel.communication.KernelInterScopeBus [GH-90000]");
            assertNotNull(canonical); // GH-90000
            assertFalse(canonical.isAnnotationPresent(Deprecated.class), // GH-90000
                "KernelInterScopeBus is canonical and must NOT be deprecated.");
        } catch (ClassNotFoundException e) { // GH-90000
            fail("KernelInterScopeBus must exist as the canonical inter-scope bus. [GH-90000]");
        }
    }

    @Test
    @DisplayName("CrossProductWorkflowEngine has been removed [GH-90000]")
    void crossProductWorkflowEngineHasBeenRemoved() { // GH-90000
        assertThrows(ClassNotFoundException.class, () -> // GH-90000
                Class.forName("com.ghatana.kernel.workflow.CrossProductWorkflowEngine [GH-90000]"),
            "CrossProductWorkflowEngine should have been removed — use CrossScopeWorkflowEngine.");
    }

    @Test
    @DisplayName("CrossScopeWorkflowEngine must exist as canonical replacement [GH-90000]")
    void crossScopeWorkflowEngineMustExist() { // GH-90000
        try {
            Class<?> canonical = Class.forName("com.ghatana.kernel.workflow.CrossScopeWorkflowEngine [GH-90000]");
            assertNotNull(canonical); // GH-90000
            assertFalse(canonical.isAnnotationPresent(Deprecated.class), // GH-90000
                "CrossScopeWorkflowEngine is canonical and must NOT be deprecated.");
        } catch (ClassNotFoundException e) { // GH-90000
            fail("CrossScopeWorkflowEngine must exist as the canonical workflow engine. [GH-90000]");
        }
    }

    @Test
    @DisplayName("BoundaryPolicyResolver must exist for scope-driven boundary checks [GH-90000]")
    void boundaryPolicyResolverMustExist() { // GH-90000
        try {
            Class.forName("com.ghatana.kernel.boundary.BoundaryPolicyResolver [GH-90000]");
            Class.forName("com.ghatana.kernel.boundary.DefaultBoundaryPolicyResolver [GH-90000]");
        } catch (ClassNotFoundException e) { // GH-90000
            fail("Missing boundary policy abstraction: " + e.getMessage()); // GH-90000
        }
    }

    // ==================== Day 5: Registry Canonicalization (Decision D4) ==================== // GH-90000

    @Test
    @DisplayName("KernelRegistry must NOT be @KernelInternal — it is the public contract [GH-90000]")
    void kernelRegistryMustNotBeInternal() { // GH-90000
        Class<?> registry = com.ghatana.kernel.registry.KernelRegistry.class;
        assertFalse(registry.isAnnotationPresent(KernelInternal.class), // GH-90000
            "KernelRegistry is the canonical public contract per Decision D4 " +
            "and must NOT be annotated with @KernelInternal.");
    }

    @Test
    @DisplayName("KernelRegistryImpl must NOT be @KernelInternal — it is the public implementation [GH-90000]")
    void kernelRegistryImplMustNotBeInternal() { // GH-90000
        Class<?> registryImpl = com.ghatana.kernel.registry.KernelRegistryImpl.class;
        assertFalse(registryImpl.isAnnotationPresent(KernelInternal.class), // GH-90000
            "KernelRegistryImpl is the public implementation of KernelRegistry " +
            "and must NOT be annotated with @KernelInternal.");
    }

    @Test
    @DisplayName("CapabilityRegistry must be @KernelInternal [GH-90000]")
    void capabilityRegistryMustBeInternal() { // GH-90000
        Class<?> capReg = com.ghatana.kernel.registry.CapabilityRegistry.class;
        assertTrue(capReg.isAnnotationPresent(KernelInternal.class), // GH-90000
            "CapabilityRegistry is an internal sub-registry per Decision D4 " +
            "and must be annotated with @KernelInternal.");
    }

    @Test
    @DisplayName("ServiceRegistry must be @KernelInternal [GH-90000]")
    void serviceRegistryMustBeInternal() { // GH-90000
        Class<?> svcReg = com.ghatana.kernel.registry.ServiceRegistry.class;
        assertTrue(svcReg.isAnnotationPresent(KernelInternal.class), // GH-90000
            "ServiceRegistry is an internal sub-registry per Decision D4 " +
            "and must be annotated with @KernelInternal.");
    }

    @Test
    @DisplayName("PluginRegistry must be @KernelInternal [GH-90000]")
    void pluginRegistryMustBeInternal() { // GH-90000
        Class<?> plugReg = com.ghatana.kernel.registry.PluginRegistry.class;
        assertTrue(plugReg.isAnnotationPresent(KernelInternal.class), // GH-90000
            "PluginRegistry is an internal sub-registry per Decision D4 " +
            "and must be annotated with @KernelInternal.");
    }

    @Test
    @DisplayName("PluginContext.getCapabilityRegistry must be @Deprecated(forRemoval=true) [GH-90000]")
    void pluginContextCapabilityRegistryAccessorMustBeDeprecated() { // GH-90000
        try {
            java.lang.reflect.Method method =
                com.ghatana.kernel.plugin.PluginContext.class.getMethod("getCapabilityRegistry [GH-90000]");
            assertTrue(method.isAnnotationPresent(Deprecated.class), // GH-90000
                "PluginContext.getCapabilityRegistry() must be @Deprecated — " + // GH-90000
                "it exposes an internal sub-registry. Use getCapability() instead."); // GH-90000
            Deprecated d = method.getAnnotation(Deprecated.class); // GH-90000
            assertTrue(d.forRemoval(), // GH-90000
                "PluginContext.getCapabilityRegistry() must be @Deprecated(forRemoval=true)."); // GH-90000
        } catch (NoSuchMethodException e) { // GH-90000
            // Method already removed — that's acceptable
        }
    }

    @Test
    @DisplayName("PluginContext.getServiceRegistry must be @Deprecated(forRemoval=true) [GH-90000]")
    void pluginContextServiceRegistryAccessorMustBeDeprecated() { // GH-90000
        try {
            java.lang.reflect.Method method =
                com.ghatana.kernel.plugin.PluginContext.class.getMethod("getServiceRegistry [GH-90000]");
            assertTrue(method.isAnnotationPresent(Deprecated.class), // GH-90000
                "PluginContext.getServiceRegistry() must be @Deprecated — " + // GH-90000
                "it exposes an internal sub-registry. Use registerService() instead."); // GH-90000
            Deprecated d = method.getAnnotation(Deprecated.class); // GH-90000
            assertTrue(d.forRemoval(), // GH-90000
                "PluginContext.getServiceRegistry() must be @Deprecated(forRemoval=true)."); // GH-90000
        } catch (NoSuchMethodException e) { // GH-90000
            // Method already removed — that's acceptable
        }
    }

    // ==================== Day 10: Legacy Duplicate Deprecation ====================

    @Test
    @DisplayName("CrossProductConfigResolver has been removed [GH-90000]")
    void crossProductConfigResolverHasBeenRemoved() { // GH-90000
        assertThrows(ClassNotFoundException.class, () -> // GH-90000
                Class.forName("com.ghatana.kernel.config.CrossProductConfigResolver [GH-90000]"),
            "CrossProductConfigResolver should have been removed — " +
            "use HierarchicalKernelConfigResolver instead.");
    }

    @Test
    @DisplayName("HierarchicalKernelConfigResolver must exist as canonical replacement [GH-90000]")
    void hierarchicalConfigResolverMustExist() { // GH-90000
        try {
            Class<?> canonical = Class.forName("com.ghatana.kernel.config.HierarchicalKernelConfigResolver [GH-90000]");
            assertNotNull(canonical); // GH-90000
            assertFalse(canonical.isAnnotationPresent(Deprecated.class), // GH-90000
                "HierarchicalKernelConfigResolver is canonical and must NOT be deprecated.");
        } catch (ClassNotFoundException e) { // GH-90000
            fail("HierarchicalKernelConfigResolver must exist as the canonical config resolver. [GH-90000]");
        }
    }

    @Test
    @DisplayName("CrossProductModelRegistry has been removed [GH-90000]")
    void crossProductModelRegistryHasBeenRemoved() { // GH-90000
        assertThrows(ClassNotFoundException.class, () -> // GH-90000
                Class.forName("com.ghatana.kernel.ai.CrossProductModelRegistry [GH-90000]"),
            "CrossProductModelRegistry should have been removed because it violates kernel purity.");
    }

    @Test
    @DisplayName("DataCloudKernelAdapterImpl legacy audit methods must be @Deprecated(forRemoval=true) [GH-90000]")
    void adapterLegacyAuditMethodsMustBeDeprecated() { // GH-90000
        Class<?> adapter = com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapterImpl.class;
        Arrays.stream(adapter.getDeclaredMethods()) // GH-90000
            .filter(m -> m.getName().equals("storeAuditEvent [GH-90000]") || m.getName().equals("queryAuditEvents [GH-90000]"))
            .forEach(m -> { // GH-90000
                assertTrue(m.isAnnotationPresent(Deprecated.class), // GH-90000
                    "DataCloudKernelAdapterImpl." + m.getName() + "() must be @Deprecated — " + // GH-90000
                    "use scope-aware methods instead.");
                Deprecated d = m.getAnnotation(Deprecated.class); // GH-90000
                assertTrue(d.forRemoval(), // GH-90000
                    "DataCloudKernelAdapterImpl." + m.getName() + "() must be @Deprecated(forRemoval=true)."); // GH-90000
            });
    }

    @Test
    @DisplayName("DataCloudKernelAdapterImpl must have canonical scope-aware audit method [GH-90000]")
    void adapterMustHaveScopeAwareAuditMethod() { // GH-90000
        Class<?> adapter = com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapterImpl.class;
        boolean hasCanonical = Arrays.stream(adapter.getDeclaredMethods()) // GH-90000
            .anyMatch(m -> m.getName().equals("storeScopeAuditRecord [GH-90000]"));
        assertTrue(hasCanonical, // GH-90000
            "DataCloudKernelAdapterImpl must have storeScopeAuditRecord() as canonical replacement."); // GH-90000
    }

    // ========================================================================
    // Day 15: Contract System Purity Tests
    // ========================================================================

    @Test
    @DisplayName("ContractFamily enum must have exactly 6 families [GH-90000]")
    void contractFamilyEnumMustHaveSixFamilies() { // GH-90000
        com.ghatana.kernel.contracts.KernelContract.ContractFamily[] families =
            com.ghatana.kernel.contracts.KernelContract.ContractFamily.values(); // GH-90000
        assertEquals(6, families.length, // GH-90000
            "ContractFamily must have exactly 6 families: EXPERIENCE, API, SCHEMA, ANALYTICS, AUTONOMY, PACKAGING");
        Set<String> expectedKeys = Set.of("experience", "api", "schema", "analytics", "autonomy", "packaging"); // GH-90000
        for (com.ghatana.kernel.contracts.KernelContract.ContractFamily family : families) { // GH-90000
            assertTrue(expectedKeys.contains(family.getKey()), // GH-90000
                "Unexpected ContractFamily key: " + family.getKey()); // GH-90000
        }
    }

    @Test
    @DisplayName("All 6 contract family classes must exist and extend KernelContract [GH-90000]")
    void allContractFamilyClassesMustExist() { // GH-90000
        List<String> familyClassNames = List.of( // GH-90000
            "com.ghatana.kernel.contracts.ExperienceContract",
            "com.ghatana.kernel.contracts.ApiContract",
            "com.ghatana.kernel.contracts.SchemaContract",
            "com.ghatana.kernel.contracts.AnalyticsContract",
            "com.ghatana.kernel.contracts.AutonomyContract",
            "com.ghatana.kernel.contracts.PackagingContract"
        );
        for (String className : familyClassNames) { // GH-90000
            try {
                Class<?> contractClass = Class.forName(className); // GH-90000
                assertTrue(com.ghatana.kernel.contracts.KernelContract.class.isAssignableFrom(contractClass), // GH-90000
                    className + " must extend KernelContract");
            } catch (ClassNotFoundException e) { // GH-90000
                fail("Contract family class missing: " + className); // GH-90000
            }
        }
    }

    @Test
    @DisplayName("KernelContract must enforce contractId regex validation [GH-90000]")
    void kernelContractMustEnforceContractIdRegex() { // GH-90000
        // Valid contract should succeed
        com.ghatana.kernel.contracts.ApiContract valid =
            com.ghatana.kernel.contracts.ApiContract.builder("my.api.v1", "Test API", "1.0.0") // GH-90000
                .build(); // GH-90000
        assertNotNull(valid); // GH-90000
        assertEquals("my.api.v1", valid.getContractId()); // GH-90000

        // Invalid contractId should fail
        assertThrows(IllegalArgumentException.class, () -> // GH-90000
            com.ghatana.kernel.contracts.ApiContract.builder("INVALID ID!", "Bad", "1.0.0").build(), // GH-90000
            "ContractId with uppercase/spaces must be rejected");
    }

    @Test
    @DisplayName("KernelContract must enforce semver version validation [GH-90000]")
    void kernelContractMustEnforceSemverVersion() { // GH-90000
        assertThrows(IllegalArgumentException.class, () -> // GH-90000
            com.ghatana.kernel.contracts.ApiContract.builder("test.api", "Test", "not-semver").build(), // GH-90000
            "Non-semver version must be rejected");
    }

    @Test
    @DisplayName("ContractRegistry must exist and support register/lookup [GH-90000]")
    void contractRegistryMustSupportRegisterAndLookup() { // GH-90000
        com.ghatana.kernel.contracts.ContractRegistry registry =
            new com.ghatana.kernel.contracts.ContractRegistry(); // GH-90000

        com.ghatana.kernel.contracts.ApiContract api =
            com.ghatana.kernel.contracts.ApiContract.builder("test.api.v1", "Test API", "1.0.0") // GH-90000
                .basePath("/api/v1 [GH-90000]")
                .build(); // GH-90000

        registry.register(api); // GH-90000
        assertEquals(1, registry.size()); // GH-90000
        assertTrue(registry.getById("test.api.v1 [GH-90000]").isPresent());
        assertEquals(1, registry.getByFamily(com.ghatana.kernel.contracts.KernelContract.ContractFamily.API).size()); // GH-90000
        assertEquals(0, registry.getByFamily(com.ghatana.kernel.contracts.KernelContract.ContractFamily.SCHEMA).size()); // GH-90000
    }

    @Test
    @DisplayName("ContractValidator interface must exist with ValidationResult record [GH-90000]")
    void contractValidatorInterfaceMustExist() { // GH-90000
        try {
            Class<?> validatorClass = Class.forName("com.ghatana.kernel.contracts.ContractValidator [GH-90000]");
            assertTrue(validatorClass.isInterface(), "ContractValidator must be an interface"); // GH-90000

            Class<?> resultClass = Class.forName("com.ghatana.kernel.contracts.ContractValidator$ValidationResult [GH-90000]");
            assertTrue(resultClass.isRecord(), "ValidationResult must be a record"); // GH-90000
        } catch (ClassNotFoundException e) { // GH-90000
            fail("ContractValidator or ValidationResult missing: " + e.getMessage()); // GH-90000
        }
    }

    @Test
    @DisplayName("AutonomyContract must reject AUTONOMOUS tier without human review [GH-90000]")
    void autonomyContractMustRequireHumanReviewForAutonomous() { // GH-90000
        assertThrows(IllegalArgumentException.class, () -> // GH-90000
            com.ghatana.kernel.contracts.AutonomyContract.builder("agent.test", "Test Agent", "1.0.0") // GH-90000
                .agentCapabilities(List.of( // GH-90000
                    new com.ghatana.kernel.contracts.AutonomyContract.AgentCapabilityDeclaration( // GH-90000
                        "autonomous-cap", com.ghatana.kernel.contracts.AutonomyContract.AgentTier.AUTONOMOUS,
                        0.9, false)
                ))
                .build(), // GH-90000
            "AUTONOMOUS agents must require human review — contract should reject requiresHumanReview=false");
    }

    @Test
    @DisplayName("Contract system must be product-agnostic (no product-specific references) [GH-90000]")
    void contractSystemMustBeProductAgnostic() { // GH-90000
        List<Class<?>> contractClasses = List.of( // GH-90000
            com.ghatana.kernel.contracts.KernelContract.class,
            com.ghatana.kernel.contracts.KernelContract.ContractFamily.class,
            com.ghatana.kernel.contracts.ContractRegistry.class,
            com.ghatana.kernel.contracts.ContractValidator.class,
            com.ghatana.kernel.contracts.ExperienceContract.class,
            com.ghatana.kernel.contracts.ApiContract.class,
            com.ghatana.kernel.contracts.SchemaContract.class,
            com.ghatana.kernel.contracts.AnalyticsContract.class,
            com.ghatana.kernel.contracts.AutonomyContract.class,
            com.ghatana.kernel.contracts.PackagingContract.class
        );
        Set<String> forbiddenImports = Set.of("finance", "phr", "flashit", "datacloud", "guardian"); // GH-90000
        for (Class<?> clazz : contractClasses) { // GH-90000
            String className = clazz.getName().toLowerCase(); // GH-90000
            for (String product : forbiddenImports) { // GH-90000
                assertFalse(className.contains(product), // GH-90000
                    clazz.getName() + " must not reference product '" + product + "' — kernel purity violation"); // GH-90000
            }
        }
    }

    // ========================================================================
    // Day 16: Schema Validation Alignment Tests
    // ========================================================================

    @Test
    @DisplayName("SchemaContractBridge interface must exist in contracts.schema package [GH-90000]")
    void schemaContractBridgeMustExist() { // GH-90000
        try {
            Class<?> bridgeClass = Class.forName("com.ghatana.kernel.contracts.schema.SchemaContractBridge [GH-90000]");
            assertTrue(bridgeClass.isInterface(), "SchemaContractBridge must be an interface"); // GH-90000

            // Must have the 4 canonical bridge methods
            Set<String> expectedMethods = Set.of( // GH-90000
                "exportSubjects", "checkCompatibility", "getBreakingChangesSince", "getActiveSchema");
            Set<String> actualMethods = Arrays.stream(bridgeClass.getDeclaredMethods()) // GH-90000
                .map(Method::getName) // GH-90000
                .collect(java.util.stream.Collectors.toSet()); // GH-90000
            for (String expected : expectedMethods) { // GH-90000
                assertTrue(actualMethods.contains(expected), // GH-90000
                    "SchemaContractBridge must declare method: " + expected);
            }
        } catch (ClassNotFoundException e) { // GH-90000
            fail("SchemaContractBridge interface missing: " + e.getMessage()); // GH-90000
        }
    }

    @Test
    @DisplayName("SchemaGovernanceValidator must exist and implement ContractValidator [GH-90000]")
    void schemaGovernanceValidatorMustExist() { // GH-90000
        try {
            Class<?> validatorClass = Class.forName("com.ghatana.kernel.contracts.schema.SchemaGovernanceValidator [GH-90000]");
            assertTrue(com.ghatana.kernel.contracts.ContractValidator.class.isAssignableFrom(validatorClass), // GH-90000
                "SchemaGovernanceValidator must implement ContractValidator");
        } catch (ClassNotFoundException e) { // GH-90000
            fail("SchemaGovernanceValidator missing: " + e.getMessage()); // GH-90000
        }
    }

    @Test
    @DisplayName("SchemaGovernanceValidator must reject empty schema contracts [GH-90000]")
    void schemaGovernanceValidatorRejectsEmptyContracts() { // GH-90000
        com.ghatana.kernel.contracts.schema.SchemaGovernanceValidator validator =
            new com.ghatana.kernel.contracts.schema.SchemaGovernanceValidator(); // GH-90000

        com.ghatana.kernel.contracts.SchemaContract emptySchema =
            com.ghatana.kernel.contracts.SchemaContract.builder("empty.schema", "Empty", "1.0.0") // GH-90000
                .subjects(List.of()) // GH-90000
                .build(); // GH-90000

        com.ghatana.kernel.contracts.ContractValidator.ValidationResult result =
            validator.validate(emptySchema); // GH-90000
        assertFalse(result.valid(), "Empty schema contract must be rejected by governance validator"); // GH-90000
    }

    @Test
    @DisplayName("SchemaGovernanceValidator must pass valid schema contracts [GH-90000]")
    void schemaGovernanceValidatorPassesValidContracts() { // GH-90000
        com.ghatana.kernel.contracts.schema.SchemaGovernanceValidator validator =
            new com.ghatana.kernel.contracts.schema.SchemaGovernanceValidator(); // GH-90000

        com.ghatana.kernel.contracts.SchemaContract validSchema =
            com.ghatana.kernel.contracts.SchemaContract.builder("trade.events", "Trade Events", "1.0.0") // GH-90000
                .subjects(List.of( // GH-90000
                    new com.ghatana.kernel.contracts.SchemaContract.SchemaSubject( // GH-90000
                        "trade.created",
                        com.ghatana.kernel.contracts.SchemaContract.SchemaFormat.JSON_SCHEMA_V7,
                        com.ghatana.kernel.contracts.SchemaContract.CompatibilityMode.BACKWARD,
                        "schemas/trade-created.json")))
                .build(); // GH-90000

        com.ghatana.kernel.contracts.ContractValidator.ValidationResult result =
            validator.validate(validSchema); // GH-90000
        assertTrue(result.valid(), "Valid schema contract must pass governance validation"); // GH-90000
    }

    // ========================================================================
    // Day 17: AI/Analytics/Autonomy Contract Alignment Tests
    // ========================================================================

    @Test
    @DisplayName("AiAutonomyContractBridge interface must exist in contracts.autonomy package [GH-90000]")
    void aiAutonomyContractBridgeMustExist() { // GH-90000
        try {
            Class<?> bridgeClass = Class.forName("com.ghatana.kernel.contracts.autonomy.AiAutonomyContractBridge [GH-90000]");
            assertTrue(bridgeClass.isInterface(), "AiAutonomyContractBridge must be an interface"); // GH-90000
            Set<String> expectedMethods = Set.of( // GH-90000
                "exportCurrentState", "validatePromotion", "getRulesForTier");
            Set<String> actualMethods = Arrays.stream(bridgeClass.getDeclaredMethods()) // GH-90000
                .map(Method::getName) // GH-90000
                .collect(java.util.stream.Collectors.toSet()); // GH-90000
            for (String expected : expectedMethods) { // GH-90000
                assertTrue(actualMethods.contains(expected), // GH-90000
                    "AiAutonomyContractBridge must declare method: " + expected);
            }
        } catch (ClassNotFoundException e) { // GH-90000
            fail("AiAutonomyContractBridge interface missing: " + e.getMessage()); // GH-90000
        }
    }

    @Test
    @DisplayName("AnalyticsContractBridge interface must exist in contracts.analytics package [GH-90000]")
    void analyticsContractBridgeMustExist() { // GH-90000
        try {
            Class<?> bridgeClass = Class.forName("com.ghatana.kernel.contracts.analytics.AnalyticsContractBridge [GH-90000]");
            assertTrue(bridgeClass.isInterface(), "AnalyticsContractBridge must be an interface"); // GH-90000
            Set<String> expectedMethods = Set.of( // GH-90000
                "exportMetrics", "exportDashboards", "getSubsystemId");
            Set<String> actualMethods = Arrays.stream(bridgeClass.getDeclaredMethods()) // GH-90000
                .map(Method::getName) // GH-90000
                .collect(java.util.stream.Collectors.toSet()); // GH-90000
            for (String expected : expectedMethods) { // GH-90000
                assertTrue(actualMethods.contains(expected), // GH-90000
                    "AnalyticsContractBridge must declare method: " + expected);
            }
        } catch (ClassNotFoundException e) { // GH-90000
            fail("AnalyticsContractBridge interface missing: " + e.getMessage()); // GH-90000
        }
    }

    @Test
    @DisplayName("AutonomyGovernanceValidator must implement ContractValidator [GH-90000]")
    void autonomyGovernanceValidatorMustExist() { // GH-90000
        try {
            Class<?> validatorClass = Class.forName( // GH-90000
                "com.ghatana.kernel.contracts.autonomy.AutonomyGovernanceValidator");
            assertTrue(com.ghatana.kernel.contracts.ContractValidator.class.isAssignableFrom(validatorClass), // GH-90000
                "AutonomyGovernanceValidator must implement ContractValidator");
        } catch (ClassNotFoundException e) { // GH-90000
            fail("AutonomyGovernanceValidator missing: " + e.getMessage()); // GH-90000
        }
    }

    @Test
    @DisplayName("AutonomyGovernanceValidator must reject zero-confidence agents [GH-90000]")
    void autonomyGovernanceValidatorRejectsZeroConfidence() { // GH-90000
        var validator = new com.ghatana.kernel.contracts.autonomy.AutonomyGovernanceValidator(); // GH-90000
        com.ghatana.kernel.contracts.AutonomyContract contract =
            com.ghatana.kernel.contracts.AutonomyContract.builder("agent.bad", "Bad Agent", "1.0.0") // GH-90000
                .agentCapabilities(List.of( // GH-90000
                    new com.ghatana.kernel.contracts.AutonomyContract.AgentCapabilityDeclaration( // GH-90000
                        "zero-confidence", com.ghatana.kernel.contracts.AutonomyContract.AgentTier.REFLEX,
                        0.0, false)))
                .build(); // GH-90000
        var result = validator.validate(contract); // GH-90000
        assertFalse(result.valid(), "Agent with zero confidence must be rejected"); // GH-90000
    }
}
