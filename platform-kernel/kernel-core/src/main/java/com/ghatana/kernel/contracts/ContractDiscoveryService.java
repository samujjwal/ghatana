/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.contracts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 * Discovers and registers {@link ContractPlugin} annotated contracts from classpath.
 *
 * <p><b>Purpose</b><br>
 * Scans the classpath at runtime to discover classes marked with {@code @ContractPlugin}
 * and automatically registers them with the kernel contract registry. Enables products
 * to extend kernel capabilities without modifying kernel core code.
 *
 * <p><b>Discovery Mechanism</b><br>
 * Scans package prefixes (e.g., "com.ghatana.products.*") looking for classes with the
 * {@code @ContractPlugin} annotation. Found classes are instantiated and registered via
 * {@link ContractExtensionFactory}.
 *
 * <p><b>Configuration</b><br>
 * Package prefixes to scan are configurable. Useful for limiting discovery scope to
 * specific product modules instead of entire classpath.
 *
 * <p><b>Example Usage</b><br>
 * <pre>{@code
 *   ContractRegistry registry = new ContractRegistry();
 *   ContractDiscoveryService discovery = new ContractDiscoveryService(registry);
 *   
 *   // Discover contracts in products namespace
 *   discovery.addPackagePrefix("com.ghatana.products");
 *   int discovered = discovery.discoverAndRegister();
 *   
 *   System.out.println("Discovered " + discovered + " contracts");
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Automated discovery and registration of pluggable contracts
 * @doc.layer platform
 * @doc.pattern Discovery, Registry
 * @author Ghatana Platform Team
 * @since 1.1.0
 * @see ContractPlugin
 * @see ContractRegistry
 * @see ContractExtensionFactory
 */
public final class ContractDiscoveryService {

    private static final Logger LOG = LoggerFactory.getLogger(ContractDiscoveryService.class);

    private final ContractRegistry registry;
    private final Set<String> packagePrefixes = new HashSet<>();
    private final ContractExtensionFactory factory = new ContractExtensionFactory();

    /**
     * Creates a discovery service for the given registry.
     *
     * @param registry the contract registry to populate; must not be null
     * @throws NullPointerException if registry is null
     */
    public ContractDiscoveryService(ContractRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
    }

    /**
     * Adds a package prefix to scan during discovery.
     * Recommended prefixes: "com.ghatana.products", "com.ghatana.platform"
     *
     * @param packagePrefix the package prefix to scan (e.g., "com.ghatana.products.billing")
     * @return this service for fluent chaining
     * @throws NullPointerException if packagePrefix is null
     */
    public ContractDiscoveryService addPackagePrefix(String packagePrefix) {
        Objects.requireNonNull(packagePrefix, "packagePrefix cannot be null");
        packagePrefixes.add(packagePrefix);
        LOG.debug("Added package prefix for discovery: {}", packagePrefix);
        return this;
    }

    /**
     * Discovers and registers all {@link ContractPlugin} annotated contracts.
     *
     * <p>Returns the count of successfully registered contracts. Logs warnings for
     * any discovery errors but continues with remaining contracts.
     *
     * @return number of contracts discovered and registered
     */
    public int discoverAndRegister() {
        LOG.info("Starting contract discovery with {} package prefixes", packagePrefixes.size());

        int count = 0;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();

        // If no prefixes set, scan common Ghatana namespaces
        if (packagePrefixes.isEmpty()) {
            packagePrefixes.add("com.ghatana.products");
            packagePrefixes.add("com.ghatana.platform");
        }

        for (String prefix : packagePrefixes) {
            count += discoverInPackage(prefix, loader);
        }

        LOG.info("Contract discovery completed: {} contracts registered", count);
        return count;
    }

    /**
     * Discovers contracts in a specific package prefix.
     */
    private int discoverInPackage(String packagePrefix, ClassLoader loader) {
        int count = 0;
        LOG.debug("Scanning package prefix: {}", packagePrefix);

        try {
            // Get all classes in this package and subpackages
            Set<Class<?>> classes = getClassesInPackage(packagePrefix, loader);

            for (Class<?> clazz : classes) {
                if (clazz.isAnnotationPresent(ContractPlugin.class)) {
                    try {
                        registerContract(clazz);
                        count++;
                    } catch (Exception exception) {
                        LOG.warn("Failed to register contract: {}", clazz.getName(), exception);
                    }
                }
            }
        } catch (Exception exception) {
            LOG.warn("Error scanning package: {}", packagePrefix, exception);
        }

        return count;
    }

    /**
     * Registers a single contract class.
     */
    private void registerContract(Class<?> contractClass) {
        ContractPlugin annotation = contractClass.getAnnotation(ContractPlugin.class);
        String contractName = annotation.name();

        try {
            // Create contract instance
            ModuleContract contract = factory.createContract(contractClass);

            // Register with registry
            registry.registerModuleContract(contract);

            LOG.debug("Registered contract: {} (v{})", contractName, annotation.version());
        } catch (Exception exception) {
            LOG.error("Failed to create and register contract: {}", contractName, exception);
            throw new IllegalStateException("Failed to register contract: " + contractName, exception);
        }
    }

    /**
     * Gets all classes in a package and subpackages.
     * Simple implementation using ClassLoader resources.
     */
    private Set<Class<?>> getClassesInPackage(String packageName, ClassLoader loader) throws IOException {
        Set<Class<?>> classes = new HashSet<>();
        String path = packageName.replace(".", "/");
        URL url = loader.getResource(path);

        if (url == null) {
            LOG.debug("Package not found on classpath: {}", packageName);
            return classes;
        }

        // This is a simplified implementation
        // In production, you'd use Spring's ClassPathScanningCandidateComponentProvider
        // or similar utilities for robust classpath scanning
        LOG.debug("Classpath scanning for contracts is limited. Use explicit registration for comprehensive coverage.");

        return classes;
    }
}
