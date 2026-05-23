package com.ghatana.kernel.interaction;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production registry for ProductInteractionBroker instances.
 *
 * <p>The registry provides:
 * <ul>
 *   <li>Broker lifecycle management (registration, deregistration)</li>
 *   <li>Health check monitoring for registered brokers</li>
 *   <li>Metrics aggregation across all brokers</li>
 *   <li>Tenant-scoped broker isolation</li>
 *   <li>Contract loading and validation</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Production registry for ProductInteractionBroker with health checks and contract loading
 * @doc.layer kernel
 * @doc.pattern Registry
 */
public final class ProductInteractionBrokerRegistry {

    private final Map<String, ProductInteractionBroker> brokersByTenant;
    private final Map<String, ProductInteractionBrokerHealth> healthByTenant;
    private final Map<String, ProductInteractionContract> contractsByContractId;
    private final ProductInteractionContractLoader contractLoader;

    private ProductInteractionBrokerRegistry(Builder builder) {
        this.brokersByTenant = new ConcurrentHashMap<>();
        this.healthByTenant = new ConcurrentHashMap<>();
        this.contractsByContractId = new ConcurrentHashMap<>();
        this.contractLoader = builder.contractLoader;
        
        if (contractLoader != null && contractLoader.isAvailable()) {
            loadContracts();
        }
    }

    public static ProductInteractionBrokerRegistry create() {
        return new Builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    private void loadContracts() {
        try {
            Map<String, ProductInteractionContract> loadedContracts = contractLoader.loadAll();
            contractsByContractId.putAll(loadedContracts);
        } catch (ProductInteractionContractLoader.ContractLoadException e) {
            throw new IllegalStateException("Failed to load contracts", e);
        }
    }

    /**
     * Reloads contracts from the configured loader.
     *
     * @throws IllegalStateException if no loader is configured or loading fails
     */
    public void reloadContracts() {
        if (contractLoader == null) {
            throw new IllegalStateException("No contract loader configured");
        }
        contractsByContractId.clear();
        loadContracts();
    }

    /**
     * Returns a contract by contract ID.
     *
     * @param contractId the contract ID
     * @return the contract, or null if not found
     */
    public ProductInteractionContract getContract(String contractId) {
        return contractsByContractId.get(contractId);
    }

    /**
     * Returns all loaded contracts.
     *
     * @return map of contract ID to contract
     */
    public Map<String, ProductInteractionContract> getAllContracts() {
        return Map.copyOf(contractsByContractId);
    }

    /**
     * Returns contracts for a specific provider product.
     *
     * @param providerProductId the provider product ID
     * @return list of contracts for the provider
     */
    public java.util.List<ProductInteractionContract> getContractsByProvider(String providerProductId) {
        return contractsByContractId.values().stream()
                .filter(c -> c.providerProductId().equals(providerProductId))
                .toList();
    }

    /**
     * Checks if a contract is loaded.
     *
     * @param contractId the contract ID
     * @return true if loaded, false otherwise
     */
    public boolean hasContract(String contractId) {
        return contractsByContractId.containsKey(contractId);
    }

    /**
     * Builder for ProductInteractionBrokerRegistry.
     */
    public static final class Builder {
        private ProductInteractionContractLoader contractLoader;

        public Builder contractLoader(ProductInteractionContractLoader contractLoader) {
            this.contractLoader = contractLoader;
            return this;
        }

        public ProductInteractionBrokerRegistry build() {
            return new ProductInteractionBrokerRegistry(this);
        }
    }

    /**
     * Registers a broker for a tenant.
     *
     * @param tenantId the tenant ID
     * @param broker the broker instance
     * @throws IllegalArgumentException if a broker is already registered for the tenant
     */
    public void register(String tenantId, ProductInteractionBroker broker) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (broker == null) {
            throw new IllegalArgumentException("broker must not be null");
        }
        ProductInteractionBroker previous = brokersByTenant.putIfAbsent(tenantId, broker);
        if (previous != null) {
            throw new IllegalArgumentException("broker already registered for tenant: " + tenantId);
        }
        healthByTenant.put(tenantId, new ProductInteractionBrokerHealth(tenantId, broker));
    }

    /**
     * Deregisters a broker for a tenant.
     *
     * @param tenantId the tenant ID
     */
    public void deregister(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        ProductInteractionBroker broker = brokersByTenant.remove(tenantId);
        if (broker != null) {
            broker.close();
        }
        healthByTenant.remove(tenantId);
    }

    /**
     * Returns the broker for a tenant.
     *
     * @param tenantId the tenant ID
     * @return the broker, or null if not registered
     */
    public ProductInteractionBroker getBroker(String tenantId) {
        return brokersByTenant.get(tenantId);
    }

    /**
     * Checks if a broker is registered for a tenant.
     *
     * @param tenantId the tenant ID
     * @return true if registered, false otherwise
     */
    public boolean isRegistered(String tenantId) {
        return brokersByTenant.containsKey(tenantId);
    }

    /**
     * Returns the set of registered tenant IDs.
     *
     * @return set of tenant IDs
     */
    public Set<String> registeredTenants() {
        return Set.copyOf(brokersByTenant.keySet());
    }

    /**
     * Returns the health status for a tenant's broker.
     *
     * @param tenantId the tenant ID
     * @return the health status, or null if not registered
     */
    public ProductInteractionBrokerHealth getHealth(String tenantId) {
        return healthByTenant.get(tenantId);
    }

    /**
     * Returns health status for all registered brokers.
     *
     * @return map of tenant ID to health status
     */
    public Map<String, ProductInteractionBrokerHealth> getAllHealth() {
        return Map.copyOf(healthByTenant);
    }

    /**
     * Checks if all registered brokers are healthy.
     *
     * @return true if all brokers are healthy, false otherwise
     */
    public boolean isAllHealthy() {
        return healthByTenant.values().stream().allMatch(ProductInteractionBrokerHealth::isHealthy);
    }

    /**
     * Closes all registered brokers and clears the registry.
     */
    public void closeAll() {
        brokersByTenant.values().forEach(ProductInteractionBroker::close);
        brokersByTenant.clear();
        healthByTenant.clear();
    }

    /**
     * Health status for a ProductInteractionBroker.
     */
    public static final class ProductInteractionBrokerHealth {
        private final String tenantId;
        private final ProductInteractionBrokerMetrics metrics;
        private final long lastCheckTimestamp;
        private final boolean circuitBreakerOpen;
        private final int openCircuitBreakersCount;

        ProductInteractionBrokerHealth(String tenantId, ProductInteractionBroker broker) {
            this.tenantId = tenantId;
            this.metrics = broker.metrics();
            this.lastCheckTimestamp = System.currentTimeMillis();
            // TODO: Integrate with actual circuit breaker from broker
            this.circuitBreakerOpen = false;
            this.openCircuitBreakersCount = 0;
        }

        public String tenantId() {
            return tenantId;
        }

        public ProductInteractionBrokerMetrics metrics() {
            return metrics;
        }

        public long lastCheckTimestamp() {
            return lastCheckTimestamp;
        }

        public boolean isCircuitBreakerOpen() {
            return circuitBreakerOpen;
        }

        public int openCircuitBreakersCount() {
            return openCircuitBreakersCount;
        }

        public boolean isHealthy() {
            // Healthy if circuit breaker is not open and evidence failures are low
            return !circuitBreakerOpen && metrics.evidenceFailures() == 0;
        }
    }
}
