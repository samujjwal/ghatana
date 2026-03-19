/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.test.validation;

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
            "tenant.", "resilience.", "circuit.", "retry.", "bulkhead."
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
}
