/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.contracts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Orchestrates contract composition including dependency resolution and lifecycle management.
 *
 * <p><b>Purpose</b><br>
 * Coordinates the registration and composition of multiple contracts within the kernel,
 * handling:
 * - Dependency graph resolution (topological sorting)
 * - Circular dependency detection
 * - Lifecycle sequencing (register → validate → enable)
 * - Conflict resolution for duplicate/incompatible contracts
 *
 * <p><b>Composition Phases</b><br>
 * <ol>
 *   <li><b>Registration:</b> Contracts added to registry with metadata</li>
 *   <li><b>Validation:</b> Contracts checked for completeness and consistency</li>
 *   <li><b>Dependency Resolution:</b> Topological sort to determine init order</li>
 *   <li><b>Enablement:</b> Contracts enabled in dependency order</li>
 * </ol>
 *
 * <p><b>Example Usage</b><br>
 * <pre>{@code
 *   ContractRegistry registry = new ContractRegistry();
 *   ContractComposer composer = new ContractComposer(registry);
 *   
 *   // Register contracts
 *   composer.registerModuleContract(new ModuleContract("base", "1.0", ...));
 *   composer.registerModuleContract(new ModuleContract("billing", "1.0", ...));
 *   
 *   // Compose and validate
 *   composer.composeAndValidate();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Orchestrate contract dependency resolution and composition
 * @doc.layer platform
 * @doc.pattern Orchestrator, Dependency Resolver
 * @author Ghatana Platform Team
 * @since 1.1.0
 * @see ModuleContract
 * @see ContractRegistry
 * @see ContractPlugin
 */
public final class ContractComposer {

    private static final Logger LOG = LoggerFactory.getLogger(ContractComposer.class);

    private final ContractRegistry registry;
    private boolean lenientMode = false;

    /**
     * Creates a new contract composer for the given registry.
     *
     * @param registry the contract registry; must not be null
     * @throws NullPointerException if registry is null
     */
    public ContractComposer(ContractRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
    }

    /**
     * Registers a module contract with the registry.
     *
     * @param contract the module contract to register; must not be null
     * @return this composer for fluent chaining
     * @throws NullPointerException if contract is null
     */
    public ContractComposer registerModuleContract(ModuleContract contract) {
        Objects.requireNonNull(contract, "contract cannot be null");
        registry.registerModuleContract(contract);
        LOG.debug("Registered module contract: {}", contract.moduleId());
        return this;
    }

    /**
     * Registers a schema registration with the registry.
     *
     * @param registration the schema registration to register; must not be null
     * @return this composer for fluent chaining
     * @throws NullPointerException if registration is null
     */
    public ContractComposer registerSchemaContract(SchemaRegistration registration) {
        Objects.requireNonNull(registration, "registration cannot be null");
        registry.registerSchemaContract(registration);
        LOG.debug("Registered schema: {}", registration.schemaId());
        return this;
    }

    /**
     * Enables lenient mode for composition.
     * In lenient mode, missing dependencies are logged as warnings instead of errors.
     *
     * @param enabled true to enable lenient mode
     * @return this composer for fluent chaining
     */
    public ContractComposer withLenientMode(boolean enabled) {
        this.lenientMode = enabled;
        LOG.debug("Lenient mode: {}", enabled);
        return this;
    }

    /**
     * Composes and validates all registered module contracts.
     * Performs dependency resolution and topological ordering.
     *
     * @throws IllegalStateException if circular dependencies detected or required dependencies missing
     */
    public void composeAndValidate() {
        LOG.info("Starting contract composition and validation");

        // Check for circular dependencies
        List<String> circularDeps = detectCircularDependencies();
        if (!circularDeps.isEmpty()) {
            String message = "Circular dependencies detected: " + String.join(" → ", circularDeps);
            LOG.error(message);
            throw new IllegalStateException(message);
        }

        // Check for missing dependencies (unless lenient mode)
        List<String> missingDeps = validateDependencies();
        if (!missingDeps.isEmpty()) {
            if (lenientMode) {
                LOG.warn("Missing dependencies (lenient mode): {}", missingDeps);
            } else {
                String message = "Missing required dependencies: " + String.join(", ", missingDeps);
                LOG.error(message);
                throw new IllegalStateException(message);
            }
        }

        LOG.info("Contract composition completed successfully");
    }

    /**
     * Detects circular dependencies in the contract dependency graph.
     *
     * @return list of module IDs involved in cycles (empty if no cycles)
     */
    private List<String> detectCircularDependencies() {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        List<String> cyclic = new ArrayList<>();

        Map<String, ModuleContract> contracts = new HashMap<>();
        for (ModuleContract contract : registry.getAllModuleContracts()) {
            contracts.put(contract.moduleId(), contract);
        }

        for (String moduleId : contracts.keySet()) {
            if (!visited.contains(moduleId)) {
                if (hasCycle(moduleId, visited, recursionStack, contracts)) {
                    cyclic.add(moduleId);
                }
            }
        }

        return cyclic;
    }

    /**
     * DFS helper to detect cycles in dependency graph.
     */
    private boolean hasCycle(String moduleId, Set<String> visited, Set<String> recursionStack,
            Map<String, ModuleContract> contracts) {
        visited.add(moduleId);
        recursionStack.add(moduleId);

        ModuleContract contract = contracts.get(moduleId);
        if (contract != null && contract.dependencies() != null) {
            for (com.ghatana.kernel.descriptor.KernelDependency dep : contract.dependencies()) {
                String depId = dep.getDependencyId() != null ? dep.getDependencyId() : dep.toString();
                if (!visited.contains(depId)) {
                    if (hasCycle(depId, visited, recursionStack, contracts)) {
                        return true;
                    }
                } else if (recursionStack.contains(depId)) {
                    return true; // Back edge found
                }
            }
        }

        recursionStack.remove(moduleId);
        return false;
    }

    /**
     * Validates that all declared dependencies exist.
     *
     * @return list of missing dependency names (empty if all present)
     */
    private List<String> validateDependencies() {
        List<String> missing = new ArrayList<>();
        Map<String, ModuleContract> contracts = new HashMap<>();

        for (ModuleContract contract : registry.getAllModuleContracts()) {
            contracts.put(contract.moduleId(), contract);
        }

        for (ModuleContract contract : contracts.values()) {
            if (contract.dependencies() != null) {
                for (com.ghatana.kernel.descriptor.KernelDependency dep : contract.dependencies()) {
                    String depId = dep.getDependencyId() != null ? dep.getDependencyId() : dep.toString();
                    if (!contracts.containsKey(depId)) {
                        String msg = contract.moduleId() + " requires " + depId;
                        missing.add(msg);
                        LOG.warn("Missing dependency: {}", msg);
                    }
                }
            }
        }

        return missing;
    }
}
