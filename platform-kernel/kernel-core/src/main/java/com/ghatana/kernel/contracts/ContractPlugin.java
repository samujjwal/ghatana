/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.contracts;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a pluggable contract extension for automatic discovery and registration.
 *
 * <p><b>Purpose</b><br>
 * Enables declarative registration of custom contracts into the kernel's contract registry.
 * Classes marked with {@code @ContractPlugin} are automatically discovered and registered
 * at module initialization time, allowing products to extend kernel capabilities without
 * modifying kernel core code.
 *
 * <p><b>Discovery Mechanism</b><br>
 * Contracts are discovered via:
 * <ol>
 *   <li><b>Classpath Scanning:</b> {@link ContractDiscoveryService} scans classpath at startup</li>
 *   <li><b>ServiceLoader:</b> META-INF/services registration for SPI-based discovery</li>
 *   <li><b>Manual Registration:</b> Explicit registration via {@link ContractRegistry}</li>
 * </ol>
 *
 * <p><b>Requirements</b><br>
 * Annotated classes must:
 * - Have a unique {@code name()} value across the system
 * - Optionally implement {@link com.ghatana.kernel.module.KernelModule} to expose capabilities
 * - Be loadable from the current classpath
 *
 * <p><b>Example Usage</b><br>
 * <pre>{@code
 *   @ContractPlugin(
 *       name = "custom.billing",
 *       version = "1.0",
 *       description = "Custom billing integration contract"
 *   )
 *   public final class CustomBillingContractModule extends AbstractKernelModule {
 *       // Module implementation
 *   }
 * }</pre>
 *
 * <p><b>Product Integration</b><br>
 * During product initialization, the kernel calls:
 * <pre>{@code
 *   ContractRegistry registry = context.getDependency(ContractRegistry.class);
 *   ContractDiscoveryService discovery = new ContractDiscoveryService(registry);
 *   discovery.discoverAndRegister();  // Scans classpath, registers @ContractPlugin classes
 * }</pre>
 *
 * @doc.type annotation
 * @doc.purpose Declare pluggable contract extensions for automatic registration
 * @doc.layer platform
 * @doc.pattern Annotation, Discovery
 * @author Ghatana Platform Team
 * @since 1.1.0
 * @see ModuleContract
 * @see ContractRegistry
 * @see ContractExtensionFactory
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ContractPlugin {

    /**
     * Unique identifier for this contract within the system.
     * Convention: dot-separated namespace, e.g., "product.domain.feature.contract"
     *
     * @return the contract name; must be non-empty and unique
     */
    String name();

    /**
     * Semantic version of this contract (e.g., "1.0.0", "2.1.3").
     * Used for compatibility checking when multiple versions exist.
     *
     * @return the contract version; must follow semantic versioning
     */
    String version();

    /**
     * Human-readable description of what this contract provides.
     * Displayed in tooling, logs, and documentation.
     *
     * @return the contract description
     */
    String description() default "";

    /**
     * Optional list of contract names this contract depends on.
     * The kernel ensures dependencies are registered before this contract.
     *
     * @return array of dependent contract names; default is no dependencies
     */
    String[] dependsOn() default {};

    /**
     * Whether this contract is enabled by default at composition time.
     *
     * @return true to include by default, false for opt-in composition
     */
    boolean autoEnable() default true;
}
