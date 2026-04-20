/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.contracts;

import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.module.KernelModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Factory for creating contract instances with reflection-based instantiation and validation.
 *
 * <p><b>Purpose</b><br>
 * Handles the creation of {@link ModuleContract} records from contract plugin classes,
 * including annotation parsing, optional reflection-based module probing, and validation.
 * Used by the contract registry and discovery service to build module contracts dynamically.
 *
 * <p><b>Instantiation Strategy</b><br>
 * <ol>
 *   <li>Verify class is concrete (not abstract, not interface)</li>
 *   <li>Read {@link ContractPlugin} annotation metadata</li>
 *   <li>Optionally probe capabilities/dependencies from a {@link KernelModule} implementation</li>
 *   <li>Merge annotation and probed metadata</li>
 *   <li>Build immutable {@link ModuleContract}</li>
 * </ol>
 *
 * <p><b>Example Usage</b><br>
 * <pre>{@code
 *   ContractExtensionFactory factory = new ContractExtensionFactory();
 *   
 *   // Instantiate from class
 *   ModuleContract contract = factory.createContract(CustomBillingContract.class);
 *   
 *   // Get metadata
 *   String name = factory.getContractName(CustomBillingContract.class);
 *   String version = factory.getContractVersion(CustomBillingContract.class);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Factory for dynamic contract instantiation and validation
 * @doc.layer platform
 * @doc.pattern Factory, Reflection
 * @author Ghatana Platform Team
 * @since 1.1.0
 * @see ModuleContract
 * @see ContractPlugin
 */
public final class ContractExtensionFactory {

    private static final Logger LOG = LoggerFactory.getLogger(ContractExtensionFactory.class);

    /**
     * Creates a contract instance from the given contract class.
     *
    * <p>The class must be annotated with {@link ContractPlugin}. If the class
    * also implements {@link KernelModule}, capabilities and dependencies are
    * extracted from a reflective no-arg instance.
     *
     * @param contractClass the contract class to instantiate; must not be null
     * @return newly created contract instance
     * @throws IllegalArgumentException if class is abstract/interface or missing annotation
     * @throws ExceptionInInitializerError if reflective probing fails
     * @throws NullPointerException if contractClass is null
     */
    public ModuleContract createContract(Class<?> contractClass) {
        Objects.requireNonNull(contractClass, "contractClass cannot be null");

        // Validate class is concrete
        if (contractClass.isInterface()) {
            throw new IllegalArgumentException("Contract class cannot be an interface: " + contractClass.getName());
        }
        if (java.lang.reflect.Modifier.isAbstract(contractClass.getModifiers())) {
            throw new IllegalArgumentException("Contract class cannot be abstract: " + contractClass.getName());
        }

        ContractPlugin annotation = contractClass.getAnnotation(ContractPlugin.class);
        if (annotation == null) {
            throw new IllegalArgumentException(
                "Contract class must be annotated with @ContractPlugin: " + contractClass.getName());
        }

        Set<KernelCapability> capabilities = new HashSet<>();
        Set<KernelDependency> dependencies = new HashSet<>();

        for (String dependencyId : annotation.dependsOn()) {
            if (dependencyId != null && !dependencyId.isBlank()) {
                dependencies.add(KernelDependency.onModule(dependencyId));
            }
        }

        if (KernelModule.class.isAssignableFrom(contractClass)) {
            try {
                Constructor<?> constructor = contractClass.getDeclaredConstructor();
                constructor.setAccessible(true);
                KernelModule module = (KernelModule) constructor.newInstance();
                capabilities.addAll(module.getCapabilities());
                dependencies.addAll(module.getDependencies());
            } catch (NoSuchMethodException ignored) {
                LOG.debug("Skipping reflective module probing for {}: no zero-arg constructor", contractClass.getName());
            } catch (ReflectiveOperationException exception) {
                LOG.error("Failed to probe module metadata for contract class: {}", contractClass.getName(), exception);
                throw new ExceptionInInitializerError(exception);
            }
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("description", annotation.description());
        metadata.put("sourceClass", contractClass.getName());
        metadata.put("autoEnable", annotation.autoEnable());

        LOG.debug("Built module contract from annotation: {}", annotation.name());
        return new ModuleContract(
            annotation.name(),
            annotation.version(),
            capabilities,
            dependencies,
            metadata
        );
    }

    /**
     * Extracts the contract name from the {@link ContractPlugin} annotation.
     *
     * @param contractClass the contract class; must have {@code @ContractPlugin} annotation
     * @return the contract name
     * @throws IllegalArgumentException if class is not annotated with {@code @ContractPlugin}
     */
    public String getContractName(Class<?> contractClass) {
        Objects.requireNonNull(contractClass, "contractClass cannot be null");

        ContractPlugin annotation = contractClass.getAnnotation(ContractPlugin.class);
        if (annotation == null) {
            throw new IllegalArgumentException(
                "Contract class must be annotated with @ContractPlugin: " + contractClass.getName());
        }

        return annotation.name();
    }

    /**
     * Extracts the contract version from the {@link ContractPlugin} annotation.
     *
     * @param contractClass the contract class; must have {@code @ContractPlugin} annotation
     * @return the contract version
     * @throws IllegalArgumentException if class is not annotated with {@code @ContractPlugin}
     */
    public String getContractVersion(Class<?> contractClass) {
        Objects.requireNonNull(contractClass, "contractClass cannot be null");

        ContractPlugin annotation = contractClass.getAnnotation(ContractPlugin.class);
        if (annotation == null) {
            throw new IllegalArgumentException(
                "Contract class must be annotated with @ContractPlugin: " + contractClass.getName());
        }

        return annotation.version();
    }

    /**
     * Extracts the contract description from the {@link ContractPlugin} annotation.
     *
     * @param contractClass the contract class; must have {@code @ContractPlugin} annotation
     * @return the contract description
     * @throws IllegalArgumentException if class is not annotated with {@code @ContractPlugin}
     */
    public String getContractDescription(Class<?> contractClass) {
        Objects.requireNonNull(contractClass, "contractClass cannot be null");

        ContractPlugin annotation = contractClass.getAnnotation(ContractPlugin.class);
        if (annotation == null) {
            throw new IllegalArgumentException(
                "Contract class must be annotated with @ContractPlugin: " + contractClass.getName());
        }

        return annotation.description();
    }

    /**
     * Extracts the dependencies from the {@link ContractPlugin} annotation.
     *
     * @param contractClass the contract class; must have {@code @ContractPlugin} annotation
     * @return array of dependent contract names (may be empty, never null)
     * @throws IllegalArgumentException if class is not annotated with {@code @ContractPlugin}
     */
    public String[] getContractDependencies(Class<?> contractClass) {
        Objects.requireNonNull(contractClass, "contractClass cannot be null");

        ContractPlugin annotation = contractClass.getAnnotation(ContractPlugin.class);
        if (annotation == null) {
            throw new IllegalArgumentException(
                "Contract class must be annotated with @ContractPlugin: " + contractClass.getName());
        }

        return annotation.dependsOn();
    }

    /**
     * Checks if this contract should be automatically enabled on registration.
     *
     * @param contractClass the contract class; must have {@code @ContractPlugin} annotation
     * @return true if contract should auto-enable, false for manual enable
     * @throws IllegalArgumentException if class is not annotated with {@code @ContractPlugin}
     */
    public boolean shouldAutoEnable(Class<?> contractClass) {
        Objects.requireNonNull(contractClass, "contractClass cannot be null");

        ContractPlugin annotation = contractClass.getAnnotation(ContractPlugin.class);
        if (annotation == null) {
            throw new IllegalArgumentException(
                "Contract class must be annotated with @ContractPlugin: " + contractClass.getName());
        }

        return annotation.autoEnable();
    }
}
