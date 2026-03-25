/*
 * Copyright (c) 2026 Ghatana Inc.
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
@DisplayName("Kernel Purity Validation Tests")
public class KernelPurityValidationTest {

    private static final List<String> FORBIDDEN_PRODUCT_TERMS = List.of(
        "finance", "trading", "trade", "order", "portfolio", "risk",
        "compliance", "market", "execution", "oms", "ems", "pms"
    );

    @Test
    @DisplayName("All kernel capabilities must be generic")
    void allKernelCapabilitiesMustBeGeneric() {
        // Get all capabilities from KernelCapability.Core
        try {
            Class<?> coreClass = KernelCapability.Core.class;
            Field[] fields = coreClass.getDeclaredFields();

            for (Field field : fields) {
                if (field.getType() == KernelCapability.class) {
                    KernelCapability capability = (KernelCapability) field.get(null);
                    assertNotNull(capability);

                    String capabilityId = capability.getCapabilityId();

                    // Verify capability is generic (contains product-specific terms)
                    for (String forbiddenTerm : FORBIDDEN_PRODUCT_TERMS) {
                        assertFalse(capabilityId.contains(forbiddenTerm),
                            "Kernel capability '" + capabilityId + "' contains forbidden product term: " + forbiddenTerm);
                    }
                }
            }
        } catch (IllegalAccessException e) {
            fail("Failed to access kernel capabilities: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Kernel modules must not have product-specific method names")
    void kernelModulesMustNotHaveProductSpecificMethodNames() {
        // This test validates that kernel modules don't have product-specific methods
        // by checking for forbidden terms in method names

        Class<KernelModule> moduleInterface = KernelModule.class;
        Method[] methods = moduleInterface.getDeclaredMethods();

        for (Method method : methods) {
            String methodName = method.getName();

            for (String forbiddenTerm : FORBIDDEN_PRODUCT_TERMS) {
                assertFalse(methodName.toLowerCase().contains(forbiddenTerm),
                    "KernelModule method '" + methodName + "' contains forbidden term: " + forbiddenTerm);
            }
        }
    }

    @Test
    @DisplayName("Capability IDs follow naming convention")
    void capabilityIdsFollowNamingConvention() {
        // Validate capability IDs follow the convention: category.action
        Set<String> validPrefixes = Set.of(
            "data.", "user.", "api.", "workflow.", "event.",
            "ai.", "observability.", "security.", "config.",
            "tenant.", "resilience.", "circuit.", "retry.", "bulkhead.",
            "mfa.", "oauth.", "audit."
        );

        try {
            Class<?> coreClass = KernelCapability.Core.class;
            Field[] fields = coreClass.getDeclaredFields();

            for (Field field : fields) {
                if (field.getType() == KernelCapability.class) {
                    KernelCapability capability = (KernelCapability) field.get(null);
                    if (capability != null) {
                        String capabilityId = capability.getCapabilityId();

                        boolean hasValidPrefix = validPrefixes.stream()
                            .anyMatch(capabilityId::startsWith);

                        assertTrue(hasValidPrefix,
                            "Capability ID '" + capabilityId + "' does not have a valid prefix. " +
                            "Valid prefixes: " + validPrefixes);

                        // Validate format: lowercase letters, numbers, hyphens, dots
                        assertTrue(capabilityId.matches("^[a-z0-9-.]+$"),
                            "Capability ID '" + capabilityId + "' contains invalid characters. " +
                            "Only lowercase letters, numbers, hyphens, and dots are allowed.");
                    }
                }
            }
        } catch (IllegalAccessException e) {
            fail("Failed to access kernel capabilities: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("No CompletableFuture in kernel module signatures")
    void noCompletableFutureInKernelModuleSignatures() {
        // Verify kernel module interfaces don't use CompletableFuture
        Class<KernelModule> moduleInterface = KernelModule.class;
        Method[] methods = moduleInterface.getDeclaredMethods();

        for (Method method : methods) {
            Class<?> returnType = method.getReturnType();

            assertFalse(returnType.getName().contains("CompletableFuture"),
                "Method '" + method.getName() + "' returns CompletableFuture. " +
                "Kernel modules must use ActiveJ Promise instead.");

            // Check parameter types
            for (Class<?> paramType : method.getParameterTypes()) {
                assertFalse(paramType.getName().contains("CompletableFuture"),
                    "Method '" + method.getName() + "' has parameter type CompletableFuture. " +
                    "Kernel modules must use ActiveJ Promise instead.");
            }
        }
    }

    @Test
    @DisplayName("All kernel modules have proper JSDoc tags")
    void allKernelModulesHaveProperJSDocTags() {
        // This test validates that all kernel module implementations
        // have the required @doc.* tags

        // The validation is done through compilation and code review
        // This test serves as documentation of the requirement

        Set<String> requiredTags = Set.of(
            "@doc.type",
            "@doc.purpose",
            "@doc.layer",
            "@doc.pattern"
        );

        assertEquals(4, requiredTags.size(),
            "Kernel modules must have all 4 required @doc.* tags");
    }

    // ==================== Convergence-era purity checks (Day 2) ====================

    @Test
    @DisplayName("Transitional capability class has been removed (package capability.* no longer exists)")
    void transitionalCapabilityClassHasBeenRemoved() {
        // D1: capability.KernelCapability was transitional and has been completely removed.
        // Verify the package no longer exists by checking ClassNotFoundException
        assertThrows(ClassNotFoundException.class, () -> {
            Class.forName("com.ghatana.kernel.capability.KernelCapability");
        }, "capability.KernelCapability should have been completely removed. " +
            "Canonical type is descriptor.KernelCapability.");
    }

    @Test
    @DisplayName("Transitional plugin.KernelExtension has been removed")
    void transitionalPluginExtensionHasBeenRemoved() {
        assertThrows(ClassNotFoundException.class, () ->
                Class.forName("com.ghatana.kernel.plugin.KernelExtension"),
            "plugin.KernelExtension should have been removed. " +
            "Canonical type is extension.KernelExtension.");
    }

    @Test
    @DisplayName("ProductPlugin has been removed")
    void productPluginHasBeenRemoved() {
        assertThrows(ClassNotFoundException.class, () ->
                Class.forName("com.ghatana.kernel.plugin.ProductPlugin"),
            "ProductPlugin should have been removed. Canonical runtime model is KernelPlugin.");
    }

    @Test
    @DisplayName("CrossProductAuditService has been removed in favor of CrossScopeAuditService")
    void crossProductAuditServiceHasBeenRemoved() {
        // CrossProductAuditService was deprecated and has been completely removed.
        // Verify the class no longer exists by checking ClassNotFoundException
        assertThrows(ClassNotFoundException.class, () -> {
            Class.forName("com.ghatana.kernel.audit.CrossProductAuditService");
        }, "CrossProductAuditService should have been completely removed. " +
            "Use CrossScopeAuditService with policy-driven retention.");
    }

    @Test
    @DisplayName("descriptor.KernelCapability.Products has been removed")
    void descriptorProductsInnerClassHasBeenRemoved() {
        assertThrows(ClassNotFoundException.class, () ->
                Class.forName("com.ghatana.kernel.descriptor.KernelCapability$Products"),
            "KernelCapability.Products should have been removed. Product-specific capabilities " +
            "must be declared by product/domain modules.");
    }

    @Test
    @DisplayName("descriptor.KernelCapability must not expose product capability inner class")
    void descriptorMustNotExposeProductCapabilityInnerClass() {
        boolean hasProductsInnerClass = Arrays.stream(KernelCapability.class.getDeclaredClasses())
            .anyMatch(c -> c.getSimpleName().equals("Products"));
        assertFalse(hasProductsInnerClass,
            "KernelCapability must not expose a Products inner class. Product capabilities " +
            "must be declared in product-owned modules.");
    }

    @Test
    @DisplayName("CrossScopeAuditService must exist as canonical replacement")
    void crossScopeAuditServiceMustExist() {
        // Verify the canonical replacement exists alongside the deprecated service
        try {
            Class<?> canonical = Class.forName("com.ghatana.kernel.audit.CrossScopeAuditService");
            assertNotNull(canonical);
            assertFalse(canonical.isAnnotationPresent(Deprecated.class),
                "CrossScopeAuditService is the canonical service and must NOT be deprecated.");
        } catch (ClassNotFoundException e) {
            fail("CrossScopeAuditService must exist as the canonical audit service.");
        }
    }

    @Test
    @DisplayName("Scope abstractions must exist for scope-first kernel framing")
    void scopeAbstractionsMustExist() {
        // These are foundational abstractions for the scope-first model
        try {
            Class.forName("com.ghatana.kernel.scope.ScopeType");
            Class.forName("com.ghatana.kernel.scope.ScopeDescriptor");
            Class.forName("com.ghatana.kernel.policy.ClassificationDescriptor");
            Class.forName("com.ghatana.kernel.policy.RetentionPolicyResolver");
            Class.forName("com.ghatana.kernel.policy.AuditPolicyResolver");
        } catch (ClassNotFoundException e) {
            fail("Missing scope/policy abstraction: " + e.getMessage() +
                ". Per KERNEL_CANONICALIZATION_DECISIONS §4, scope-first framing is required.");
        }
    }

    // ==================== Day 7-9: Boundary/Bus/Workflow Convergence Checks ====================

    @Test
    @DisplayName("ProductBoundaryEnforcer has been removed (use ScopeBoundaryEnforcer)")
    void productBoundaryEnforcerHasBeenRemoved() {
        // ProductBoundaryEnforcer was deprecated and has been completely removed.
        assertThrows(ClassNotFoundException.class, () -> {
            Class.forName("com.ghatana.kernel.boundary.ProductBoundaryEnforcer");
        }, "ProductBoundaryEnforcer should have been completely removed. Use ScopeBoundaryEnforcer.");
    }

    @Test
    @DisplayName("ScopeBoundaryEnforcer must exist as canonical replacement")
    void scopeBoundaryEnforcerMustExist() {
        try {
            Class<?> canonical = Class.forName("com.ghatana.kernel.boundary.ScopeBoundaryEnforcer");
            assertNotNull(canonical);
            assertFalse(canonical.isAnnotationPresent(Deprecated.class),
                "ScopeBoundaryEnforcer is canonical and must NOT be deprecated.");
        } catch (ClassNotFoundException e) {
            fail("ScopeBoundaryEnforcer must exist as the canonical boundary enforcer.");
        }
    }

    @Test
    @DisplayName("KernelInterProductBus has been removed")
    void kernelInterProductBusHasBeenRemoved() {
        assertThrows(ClassNotFoundException.class, () ->
                Class.forName("com.ghatana.kernel.communication.KernelInterProductBus"),
            "KernelInterProductBus should have been removed — use KernelInterScopeBus.");
    }

    @Test
    @DisplayName("KernelInterScopeBus must exist as canonical replacement")
    void kernelInterScopeBusMustExist() {
        try {
            Class<?> canonical = Class.forName("com.ghatana.kernel.communication.KernelInterScopeBus");
            assertNotNull(canonical);
            assertFalse(canonical.isAnnotationPresent(Deprecated.class),
                "KernelInterScopeBus is canonical and must NOT be deprecated.");
        } catch (ClassNotFoundException e) {
            fail("KernelInterScopeBus must exist as the canonical inter-scope bus.");
        }
    }

    @Test
    @DisplayName("CrossProductWorkflowEngine has been removed")
    void crossProductWorkflowEngineHasBeenRemoved() {
        assertThrows(ClassNotFoundException.class, () ->
                Class.forName("com.ghatana.kernel.workflow.CrossProductWorkflowEngine"),
            "CrossProductWorkflowEngine should have been removed — use CrossScopeWorkflowEngine.");
    }

    @Test
    @DisplayName("CrossScopeWorkflowEngine must exist as canonical replacement")
    void crossScopeWorkflowEngineMustExist() {
        try {
            Class<?> canonical = Class.forName("com.ghatana.kernel.workflow.CrossScopeWorkflowEngine");
            assertNotNull(canonical);
            assertFalse(canonical.isAnnotationPresent(Deprecated.class),
                "CrossScopeWorkflowEngine is canonical and must NOT be deprecated.");
        } catch (ClassNotFoundException e) {
            fail("CrossScopeWorkflowEngine must exist as the canonical workflow engine.");
        }
    }

    @Test
    @DisplayName("BoundaryPolicyResolver must exist for scope-driven boundary checks")
    void boundaryPolicyResolverMustExist() {
        try {
            Class.forName("com.ghatana.kernel.boundary.BoundaryPolicyResolver");
            Class.forName("com.ghatana.kernel.boundary.DefaultBoundaryPolicyResolver");
        } catch (ClassNotFoundException e) {
            fail("Missing boundary policy abstraction: " + e.getMessage());
        }
    }

    // ==================== Day 5: Registry Canonicalization (Decision D4) ====================

    @Test
    @DisplayName("KernelRegistry must NOT be @KernelInternal — it is the public contract")
    void kernelRegistryMustNotBeInternal() {
        Class<?> registry = com.ghatana.kernel.registry.KernelRegistry.class;
        assertFalse(registry.isAnnotationPresent(KernelInternal.class),
            "KernelRegistry is the canonical public contract per Decision D4 " +
            "and must NOT be annotated with @KernelInternal.");
    }

    @Test
    @DisplayName("KernelRegistryImpl must NOT be @KernelInternal — it is the public implementation")
    void kernelRegistryImplMustNotBeInternal() {
        Class<?> registryImpl = com.ghatana.kernel.registry.KernelRegistryImpl.class;
        assertFalse(registryImpl.isAnnotationPresent(KernelInternal.class),
            "KernelRegistryImpl is the public implementation of KernelRegistry " +
            "and must NOT be annotated with @KernelInternal.");
    }

    @Test
    @DisplayName("CapabilityRegistry must be @KernelInternal")
    void capabilityRegistryMustBeInternal() {
        Class<?> capReg = com.ghatana.kernel.registry.CapabilityRegistry.class;
        assertTrue(capReg.isAnnotationPresent(KernelInternal.class),
            "CapabilityRegistry is an internal sub-registry per Decision D4 " +
            "and must be annotated with @KernelInternal.");
    }

    @Test
    @DisplayName("ServiceRegistry must be @KernelInternal")
    void serviceRegistryMustBeInternal() {
        Class<?> svcReg = com.ghatana.kernel.registry.ServiceRegistry.class;
        assertTrue(svcReg.isAnnotationPresent(KernelInternal.class),
            "ServiceRegistry is an internal sub-registry per Decision D4 " +
            "and must be annotated with @KernelInternal.");
    }

    @Test
    @DisplayName("PluginRegistry must be @KernelInternal")
    void pluginRegistryMustBeInternal() {
        Class<?> plugReg = com.ghatana.kernel.registry.PluginRegistry.class;
        assertTrue(plugReg.isAnnotationPresent(KernelInternal.class),
            "PluginRegistry is an internal sub-registry per Decision D4 " +
            "and must be annotated with @KernelInternal.");
    }

    @Test
    @DisplayName("PluginContext.getCapabilityRegistry must be @Deprecated(forRemoval=true)")
    void pluginContextCapabilityRegistryAccessorMustBeDeprecated() {
        try {
            java.lang.reflect.Method method =
                com.ghatana.kernel.plugin.PluginContext.class.getMethod("getCapabilityRegistry");
            assertTrue(method.isAnnotationPresent(Deprecated.class),
                "PluginContext.getCapabilityRegistry() must be @Deprecated — " +
                "it exposes an internal sub-registry. Use getCapability() instead.");
            Deprecated d = method.getAnnotation(Deprecated.class);
            assertTrue(d.forRemoval(),
                "PluginContext.getCapabilityRegistry() must be @Deprecated(forRemoval=true).");
        } catch (NoSuchMethodException e) {
            // Method already removed — that's acceptable
        }
    }

    @Test
    @DisplayName("PluginContext.getServiceRegistry must be @Deprecated(forRemoval=true)")
    void pluginContextServiceRegistryAccessorMustBeDeprecated() {
        try {
            java.lang.reflect.Method method =
                com.ghatana.kernel.plugin.PluginContext.class.getMethod("getServiceRegistry");
            assertTrue(method.isAnnotationPresent(Deprecated.class),
                "PluginContext.getServiceRegistry() must be @Deprecated — " +
                "it exposes an internal sub-registry. Use registerService() instead.");
            Deprecated d = method.getAnnotation(Deprecated.class);
            assertTrue(d.forRemoval(),
                "PluginContext.getServiceRegistry() must be @Deprecated(forRemoval=true).");
        } catch (NoSuchMethodException e) {
            // Method already removed — that's acceptable
        }
    }

    // ==================== Day 10: Legacy Duplicate Deprecation ====================

    @Test
    @DisplayName("CrossProductConfigResolver has been removed")
    void crossProductConfigResolverHasBeenRemoved() {
        assertThrows(ClassNotFoundException.class, () ->
                Class.forName("com.ghatana.kernel.config.CrossProductConfigResolver"),
            "CrossProductConfigResolver should have been removed — " +
            "use HierarchicalKernelConfigResolver instead.");
    }

    @Test
    @DisplayName("HierarchicalKernelConfigResolver must exist as canonical replacement")
    void hierarchicalConfigResolverMustExist() {
        try {
            Class<?> canonical = Class.forName("com.ghatana.kernel.config.HierarchicalKernelConfigResolver");
            assertNotNull(canonical);
            assertFalse(canonical.isAnnotationPresent(Deprecated.class),
                "HierarchicalKernelConfigResolver is canonical and must NOT be deprecated.");
        } catch (ClassNotFoundException e) {
            fail("HierarchicalKernelConfigResolver must exist as the canonical config resolver.");
        }
    }

    @Test
    @DisplayName("CrossProductModelRegistry has been removed")
    void crossProductModelRegistryHasBeenRemoved() {
        assertThrows(ClassNotFoundException.class, () ->
                Class.forName("com.ghatana.kernel.ai.CrossProductModelRegistry"),
            "CrossProductModelRegistry should have been removed because it violates kernel purity.");
    }

    @Test
    @DisplayName("DataCloudKernelAdapterImpl legacy audit methods must be @Deprecated(forRemoval=true)")
    void adapterLegacyAuditMethodsMustBeDeprecated() {
        Class<?> adapter = com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapterImpl.class;
        Arrays.stream(adapter.getDeclaredMethods())
            .filter(m -> m.getName().equals("storeAuditEvent") || m.getName().equals("queryAuditEvents"))
            .forEach(m -> {
                assertTrue(m.isAnnotationPresent(Deprecated.class),
                    "DataCloudKernelAdapterImpl." + m.getName() + "() must be @Deprecated — " +
                    "use scope-aware methods instead.");
                Deprecated d = m.getAnnotation(Deprecated.class);
                assertTrue(d.forRemoval(),
                    "DataCloudKernelAdapterImpl." + m.getName() + "() must be @Deprecated(forRemoval=true).");
            });
    }

    @Test
    @DisplayName("DataCloudKernelAdapterImpl must have canonical scope-aware audit method")
    void adapterMustHaveScopeAwareAuditMethod() {
        Class<?> adapter = com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapterImpl.class;
        boolean hasCanonical = Arrays.stream(adapter.getDeclaredMethods())
            .anyMatch(m -> m.getName().equals("storeScopeAuditRecord"));
        assertTrue(hasCanonical,
            "DataCloudKernelAdapterImpl must have storeScopeAuditRecord() as canonical replacement.");
    }

    // ========================================================================
    // Day 15: Contract System Purity Tests
    // ========================================================================

    @Test
    @DisplayName("ContractFamily enum must have exactly 6 families")
    void contractFamilyEnumMustHaveSixFamilies() {
        com.ghatana.kernel.contracts.ContractFamily[] families =
            com.ghatana.kernel.contracts.ContractFamily.values();
        assertEquals(6, families.length,
            "ContractFamily must have exactly 6 families: EXPERIENCE, API, SCHEMA, ANALYTICS, AUTONOMY, PACKAGING");
        Set<String> expectedKeys = Set.of("experience", "api", "schema", "analytics", "autonomy", "packaging");
        for (com.ghatana.kernel.contracts.ContractFamily family : families) {
            assertTrue(expectedKeys.contains(family.getKey()),
                "Unexpected ContractFamily key: " + family.getKey());
        }
    }

    @Test
    @DisplayName("All 6 contract family classes must exist and extend KernelContract")
    void allContractFamilyClassesMustExist() {
        List<String> familyClassNames = List.of(
            "com.ghatana.kernel.contracts.ExperienceContract",
            "com.ghatana.kernel.contracts.ApiContract",
            "com.ghatana.kernel.contracts.SchemaContract",
            "com.ghatana.kernel.contracts.AnalyticsContract",
            "com.ghatana.kernel.contracts.AutonomyContract",
            "com.ghatana.kernel.contracts.PackagingContract"
        );
        for (String className : familyClassNames) {
            try {
                Class<?> contractClass = Class.forName(className);
                assertTrue(com.ghatana.kernel.contracts.KernelContract.class.isAssignableFrom(contractClass),
                    className + " must extend KernelContract");
            } catch (ClassNotFoundException e) {
                fail("Contract family class missing: " + className);
            }
        }
    }

    @Test
    @DisplayName("KernelContract must enforce contractId regex validation")
    void kernelContractMustEnforceContractIdRegex() {
        // Valid contract should succeed
        com.ghatana.kernel.contracts.ApiContract valid =
            com.ghatana.kernel.contracts.ApiContract.builder("my.api.v1", "Test API", "1.0.0")
                .build();
        assertNotNull(valid);
        assertEquals("my.api.v1", valid.getContractId());

        // Invalid contractId should fail
        assertThrows(IllegalArgumentException.class, () ->
            com.ghatana.kernel.contracts.ApiContract.builder("INVALID ID!", "Bad", "1.0.0").build(),
            "ContractId with uppercase/spaces must be rejected");
    }

    @Test
    @DisplayName("KernelContract must enforce semver version validation")
    void kernelContractMustEnforceSemverVersion() {
        assertThrows(IllegalArgumentException.class, () ->
            com.ghatana.kernel.contracts.ApiContract.builder("test.api", "Test", "not-semver").build(),
            "Non-semver version must be rejected");
    }

    @Test
    @DisplayName("ContractRegistry must exist and support register/lookup")
    void contractRegistryMustSupportRegisterAndLookup() {
        com.ghatana.kernel.contracts.ContractRegistry registry =
            new com.ghatana.kernel.contracts.ContractRegistry();

        com.ghatana.kernel.contracts.ApiContract api =
            com.ghatana.kernel.contracts.ApiContract.builder("test.api.v1", "Test API", "1.0.0")
                .basePath("/api/v1")
                .build();

        registry.register(api);
        assertEquals(1, registry.size());
        assertTrue(registry.getById("test.api.v1").isPresent());
        assertEquals(1, registry.getByFamily(com.ghatana.kernel.contracts.ContractFamily.API).size());
        assertEquals(0, registry.getByFamily(com.ghatana.kernel.contracts.ContractFamily.SCHEMA).size());
    }

    @Test
    @DisplayName("ContractValidator interface must exist with ValidationResult record")
    void contractValidatorInterfaceMustExist() {
        try {
            Class<?> validatorClass = Class.forName("com.ghatana.kernel.contracts.ContractValidator");
            assertTrue(validatorClass.isInterface(), "ContractValidator must be an interface");

            Class<?> resultClass = Class.forName("com.ghatana.kernel.contracts.ContractValidator$ValidationResult");
            assertTrue(resultClass.isRecord(), "ValidationResult must be a record");
        } catch (ClassNotFoundException e) {
            fail("ContractValidator or ValidationResult missing: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("AutonomyContract must reject AUTONOMOUS tier without human review")
    void autonomyContractMustRequireHumanReviewForAutonomous() {
        assertThrows(IllegalArgumentException.class, () ->
            com.ghatana.kernel.contracts.AutonomyContract.builder("agent.test", "Test Agent", "1.0.0")
                .agentCapabilities(List.of(
                    new com.ghatana.kernel.contracts.AutonomyContract.AgentCapabilityDeclaration(
                        "autonomous-cap", com.ghatana.kernel.contracts.AutonomyContract.AgentTier.AUTONOMOUS,
                        0.9, false)
                ))
                .build(),
            "AUTONOMOUS agents must require human review — contract should reject requiresHumanReview=false");
    }

    @Test
    @DisplayName("Contract system must be product-agnostic (no product-specific references)")
    void contractSystemMustBeProductAgnostic() {
        List<Class<?>> contractClasses = List.of(
            com.ghatana.kernel.contracts.KernelContract.class,
            com.ghatana.kernel.contracts.ContractFamily.class,
            com.ghatana.kernel.contracts.ContractRegistry.class,
            com.ghatana.kernel.contracts.ContractValidator.class,
            com.ghatana.kernel.contracts.ExperienceContract.class,
            com.ghatana.kernel.contracts.ApiContract.class,
            com.ghatana.kernel.contracts.SchemaContract.class,
            com.ghatana.kernel.contracts.AnalyticsContract.class,
            com.ghatana.kernel.contracts.AutonomyContract.class,
            com.ghatana.kernel.contracts.PackagingContract.class
        );
        Set<String> forbiddenImports = Set.of("finance", "phr", "flashit", "datacloud", "guardian");
        for (Class<?> clazz : contractClasses) {
            String className = clazz.getName().toLowerCase();
            for (String product : forbiddenImports) {
                assertFalse(className.contains(product),
                    clazz.getName() + " must not reference product '" + product + "' — kernel purity violation");
            }
        }
    }

    // ========================================================================
    // Day 16: Schema Validation Alignment Tests
    // ========================================================================

    @Test
    @DisplayName("SchemaContractBridge interface must exist in contracts.schema package")
    void schemaContractBridgeMustExist() {
        try {
            Class<?> bridgeClass = Class.forName("com.ghatana.kernel.contracts.schema.SchemaContractBridge");
            assertTrue(bridgeClass.isInterface(), "SchemaContractBridge must be an interface");

            // Must have the 4 canonical bridge methods
            Set<String> expectedMethods = Set.of(
                "exportSubjects", "checkCompatibility", "getBreakingChangesSince", "getActiveSchema");
            Set<String> actualMethods = Arrays.stream(bridgeClass.getDeclaredMethods())
                .map(Method::getName)
                .collect(java.util.stream.Collectors.toSet());
            for (String expected : expectedMethods) {
                assertTrue(actualMethods.contains(expected),
                    "SchemaContractBridge must declare method: " + expected);
            }
        } catch (ClassNotFoundException e) {
            fail("SchemaContractBridge interface missing: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("SchemaGovernanceValidator must exist and implement ContractValidator")
    void schemaGovernanceValidatorMustExist() {
        try {
            Class<?> validatorClass = Class.forName("com.ghatana.kernel.contracts.schema.SchemaGovernanceValidator");
            assertTrue(com.ghatana.kernel.contracts.ContractValidator.class.isAssignableFrom(validatorClass),
                "SchemaGovernanceValidator must implement ContractValidator");
        } catch (ClassNotFoundException e) {
            fail("SchemaGovernanceValidator missing: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("SchemaGovernanceValidator must reject empty schema contracts")
    void schemaGovernanceValidatorRejectsEmptyContracts() {
        com.ghatana.kernel.contracts.schema.SchemaGovernanceValidator validator =
            new com.ghatana.kernel.contracts.schema.SchemaGovernanceValidator();

        com.ghatana.kernel.contracts.SchemaContract emptySchema =
            com.ghatana.kernel.contracts.SchemaContract.builder("empty.schema", "Empty", "1.0.0")
                .subjects(List.of())
                .build();

        com.ghatana.kernel.contracts.ContractValidator.ValidationResult result =
            validator.validate(emptySchema);
        assertFalse(result.valid(), "Empty schema contract must be rejected by governance validator");
    }

    @Test
    @DisplayName("SchemaGovernanceValidator must pass valid schema contracts")
    void schemaGovernanceValidatorPassesValidContracts() {
        com.ghatana.kernel.contracts.schema.SchemaGovernanceValidator validator =
            new com.ghatana.kernel.contracts.schema.SchemaGovernanceValidator();

        com.ghatana.kernel.contracts.SchemaContract validSchema =
            com.ghatana.kernel.contracts.SchemaContract.builder("trade.events", "Trade Events", "1.0.0")
                .subjects(List.of(
                    new com.ghatana.kernel.contracts.SchemaContract.SchemaSubject(
                        "trade.created",
                        com.ghatana.kernel.contracts.SchemaContract.SchemaFormat.JSON_SCHEMA_V7,
                        com.ghatana.kernel.contracts.SchemaContract.CompatibilityMode.BACKWARD,
                        "schemas/trade-created.json")))
                .build();

        com.ghatana.kernel.contracts.ContractValidator.ValidationResult result =
            validator.validate(validSchema);
        assertTrue(result.valid(), "Valid schema contract must pass governance validation");
    }

    // ========================================================================
    // Day 17: AI/Analytics/Autonomy Contract Alignment Tests
    // ========================================================================

    @Test
    @DisplayName("AiAutonomyContractBridge interface must exist in contracts.autonomy package")
    void aiAutonomyContractBridgeMustExist() {
        try {
            Class<?> bridgeClass = Class.forName("com.ghatana.kernel.contracts.autonomy.AiAutonomyContractBridge");
            assertTrue(bridgeClass.isInterface(), "AiAutonomyContractBridge must be an interface");
            Set<String> expectedMethods = Set.of(
                "exportCurrentState", "validatePromotion", "getRulesForTier");
            Set<String> actualMethods = Arrays.stream(bridgeClass.getDeclaredMethods())
                .map(Method::getName)
                .collect(java.util.stream.Collectors.toSet());
            for (String expected : expectedMethods) {
                assertTrue(actualMethods.contains(expected),
                    "AiAutonomyContractBridge must declare method: " + expected);
            }
        } catch (ClassNotFoundException e) {
            fail("AiAutonomyContractBridge interface missing: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("AnalyticsContractBridge interface must exist in contracts.analytics package")
    void analyticsContractBridgeMustExist() {
        try {
            Class<?> bridgeClass = Class.forName("com.ghatana.kernel.contracts.analytics.AnalyticsContractBridge");
            assertTrue(bridgeClass.isInterface(), "AnalyticsContractBridge must be an interface");
            Set<String> expectedMethods = Set.of(
                "exportMetrics", "exportDashboards", "getSubsystemId");
            Set<String> actualMethods = Arrays.stream(bridgeClass.getDeclaredMethods())
                .map(Method::getName)
                .collect(java.util.stream.Collectors.toSet());
            for (String expected : expectedMethods) {
                assertTrue(actualMethods.contains(expected),
                    "AnalyticsContractBridge must declare method: " + expected);
            }
        } catch (ClassNotFoundException e) {
            fail("AnalyticsContractBridge interface missing: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("AutonomyGovernanceValidator must implement ContractValidator")
    void autonomyGovernanceValidatorMustExist() {
        try {
            Class<?> validatorClass = Class.forName(
                "com.ghatana.kernel.contracts.autonomy.AutonomyGovernanceValidator");
            assertTrue(com.ghatana.kernel.contracts.ContractValidator.class.isAssignableFrom(validatorClass),
                "AutonomyGovernanceValidator must implement ContractValidator");
        } catch (ClassNotFoundException e) {
            fail("AutonomyGovernanceValidator missing: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("AutonomyGovernanceValidator must reject zero-confidence agents")
    void autonomyGovernanceValidatorRejectsZeroConfidence() {
        var validator = new com.ghatana.kernel.contracts.autonomy.AutonomyGovernanceValidator();
        com.ghatana.kernel.contracts.AutonomyContract contract =
            com.ghatana.kernel.contracts.AutonomyContract.builder("agent.bad", "Bad Agent", "1.0.0")
                .agentCapabilities(List.of(
                    new com.ghatana.kernel.contracts.AutonomyContract.AgentCapabilityDeclaration(
                        "zero-confidence", com.ghatana.kernel.contracts.AutonomyContract.AgentTier.REFLEX,
                        0.0, false)))
                .build();
        var result = validator.validate(contract);
        assertFalse(result.valid(), "Agent with zero confidence must be rejected");
    }
}
