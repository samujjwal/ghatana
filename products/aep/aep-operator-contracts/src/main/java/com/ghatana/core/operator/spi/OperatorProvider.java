/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Task 5.2 — Custom Operator SPI with ServiceLoader discovery.
 * Allows external operator implementations to be discovered and registered in the OperatorCatalog.
 */
package com.ghatana.core.operator.spi;

import com.ghatana.core.operator.OperatorConfig;
import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.OperatorType;
import com.ghatana.core.operator.UnifiedOperator;

import java.util.Set;

/**
 * @doc.type interface
 * @doc.purpose Defines a ServiceLoader SPI for discovering and creating external operators.
 * @doc.layer product
 * @doc.pattern Provider
 *
 * Service Provider Interface for external operator implementations.
 *
 * <p>Third-party operator providers implement this interface and register via
 * {@code META-INF/services/com.ghatana.core.operator.spi.OperatorProvider}. The platform
 * discovers providers at startup via {@link java.util.ServiceLoader}.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * public final class CustomFilterProvider implements OperatorProvider {
 *     @Override
 *     public String getProviderId() { return "acme-filters"; }
 *
 *     @Override
 *     public String getProviderName() { return "ACME Filters"; }
 *
 *     @Override
 *     public Set<OperatorId> getOperatorIds() {
 *         return Set.of(OperatorId.parse("acme:filter:1.0"));
 *     }
 *
 *     @Override
 *     public Set<OperatorType> getOperatorTypes() {
 *         return Set.of(OperatorType.FILTER);
 *     }
 *
 *     @Override
 *     public UnifiedOperator createOperator(OperatorId operatorId, OperatorConfig config) {
 *         throw new UnsupportedOperationException("example");
 *     }
 * }
 *
 * // Register in META-INF/services/com.ghatana.core.operator.spi.OperatorProvider:
 * // com.acme.CustomFilterProvider
 * }</pre>
 *
 * @see OperatorProviderRegistry discovery and registration coordinator
 * @see com.ghatana.core.operator.UnifiedOperator core operator contract
 */
public interface OperatorProvider {

    /**
     * Unique identifier for this provider.
     *
     * @return provider identifier (e.g., "acme-filters", "ghatana-ml-operators")
     */
    String getProviderId();

    /**
     * Human-readable name of this provider.
     *
     * @return display name
     */
    String getProviderName();

    /**
     * The set of operator IDs this provider can create.
     * Each ID follows the format {@code namespace:type:version}.
     *
     * @return set of supported operator IDs
     */
    Set<OperatorId> getOperatorIds();

    /**
     * The operator types this provider supplies.
     *
     * @return set of supported operator types
     */
    Set<OperatorType> getOperatorTypes();

    /**
     * Creates an operator instance from the given ID and configuration.
     *
     * @param operatorId the operator ID to create
     * @param config the operator configuration
     * @return the created operator
     * @throws IllegalArgumentException if the operatorId is not supported
     */
    UnifiedOperator createOperator(OperatorId operatorId, OperatorConfig config);

    /**
     * Checks whether this provider supports the given operator ID.
     *
     * @param operatorId the operator ID
     * @return true if this provider can create the operator
     */
    default boolean supports(OperatorId operatorId) {
        return getOperatorIds().contains(operatorId);
    }

    /**
     * Priority for provider ordering. Lower values = higher priority. Default 1000.
     *
     * @return priority value
     */
    default int priority() {
        return 1000;
    }

    /**
     * Whether this provider is enabled.
     *
     * @return true if enabled
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * Version of this provider.
     *
     * @return version string
     */
    default String getVersion() {
        return "1.0.0";
    }
}
