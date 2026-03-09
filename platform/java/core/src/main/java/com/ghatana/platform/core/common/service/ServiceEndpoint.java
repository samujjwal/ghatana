package com.ghatana.platform.core.common.service;

/**
 * Represents a service endpoint for testing purposes (chaos testing, performance testing, etc.).
 * This is a simple data class that identifies a service endpoint with its connection details.
 * 
 * <p><b>Note:</b> This was renamed from {@code TargetService} to {@code ServiceEndpoint} to avoid
 * naming conflict with {@link com.ghatana.testing.service.TargetService}, which is an enumeration
 * of EventCloud service names. This record represents endpoint configuration (name + endpoint + type),
 * while the enum represents service identity.
 * 
 * @see com.ghatana.testing.service.TargetService
 * @doc.type record
 * @doc.purpose Service endpoint definition for testing and service discovery
 * @doc.layer core
 * @doc.pattern Value Object, Record
 */
public record ServiceEndpoint(
    String name,
    String endpoint,
    ServiceType type
) {
    public enum ServiceType {
        HTTP,
        GRPC,
        DATABASE,
        CACHE,
        QUEUE
    }
    
    public ServiceEndpoint {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Service name cannot be null or blank");
        }
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("Service endpoint cannot be null or blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("Service type cannot be null");
        }
    }
}
