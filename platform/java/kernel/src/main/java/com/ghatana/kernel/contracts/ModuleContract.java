/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.contracts;

import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Describes a kernel module's contract: its capabilities, dependencies, version, and metadata.
 *
 * <p>Promoted from the inner type {@code ContractValidator.ModuleContract} in the removed
 * {@code kernel.contract} package.</p>
 *
 * @doc.type record
 * @doc.purpose Module-level contract registration record
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public record ModuleContract(
        String moduleId,
        String version,
        Set<KernelCapability> capabilities,
        Set<KernelDependency> dependencies,
        Map<String, Object> moduleMetadata
) {
    public ModuleContract {
        Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(version,  "version");
        capabilities   = capabilities   != null ? Set.copyOf(capabilities)   : Set.of();
        dependencies   = dependencies   != null ? Set.copyOf(dependencies)   : Set.of();
        moduleMetadata = moduleMetadata != null ? Map.copyOf(moduleMetadata) : Map.of();
    }
}
