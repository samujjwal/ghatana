package com.ghatana.kernel.capability;

import java.util.*;

/**
 * Generic KernelCapability class with NO product coupling.
 * 
 * This class represents capabilities that the kernel provides without
 * any knowledge of specific products. Products register their capabilities
 * dynamically through plugins.
 */
public class KernelCapability {
    private final String capabilityId;
    private final String name;
    private final String description;
    private final CapabilityType type;
    private final Map<String, Object> metadata;
    private final Set<String> requiredServices;
    private final Set<String> optionalDependencies;

    public enum CapabilityType {
        DATA_MANAGEMENT,
        EVENT_PROCESSING,
        SECURITY,
        COMPLIANCE,
        AI_ML,
        WORKFLOW,
        INTEGRATION,
        MONITORING,
        UI_UX_FRAMEWORK,
        API_FRAMEWORK,
        BUSINESS_LOGIC,
        OBSERVABILITY
    }

    // Generic constructor - NO product coupling
    public KernelCapability(String capabilityId, String name, String description, 
                           CapabilityType type, Map<String, Object> metadata) {
        this.capabilityId = Objects.requireNonNull(capabilityId);
        this.name = Objects.requireNonNull(name);
        this.description = Objects.requireNonNull(description);
        this.type = Objects.requireNonNull(type);
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        this.requiredServices = extractRequiredServices(this.metadata);
        this.optionalDependencies = extractOptionalDependencies(this.metadata);
    }

    // Generic capability factory methods - NO product knowledge
    public static final class Core {
        // Core kernel capabilities
        public static final KernelCapability DATA_STORAGE = new KernelCapability(
            "data.storage", "Data Storage", "Unified data storage abstraction",
            CapabilityType.DATA_MANAGEMENT, Map.of(
                "backends", "postgresql,redis,s3",
                "replication", "true",
                "encryption", "at_rest,in_transit",
                "required_services", "storage_manager,encryption_service"
            )
        );

        public static final KernelCapability USER_AUTHENTICATION = new KernelCapability(
            "user.authentication", "User Authentication", "Multi-factor authentication system",
            CapabilityType.SECURITY, Map.of(
                "methods", "password,mfa,sso,oauth",
                "session_management", "true",
                "audit", "true",
                "required_services", "auth_service,session_manager"
            )
        );

        public static final KernelCapability API_FRAMEWORK = new KernelCapability(
            "api.framework", "API Framework", "RESTful API development framework",
            CapabilityType.API_FRAMEWORK, Map.of(
                "protocols", "rest,graphql,websocket",
                "validation", "json_schema",
                "documentation", "openapi",
                "required_services", "router,validator,documentation_generator"
            )
        );

        public static final KernelCapability WORKFLOW_ENGINE = new KernelCapability(
            "workflow.engine", "Workflow Engine", "Business workflow orchestration",
            CapabilityType.WORKFLOW, Map.of(
                "engine", "temporal,camunda",
                "persistence", "durable",
                "retries", "exponential_backoff",
                "required_services", "workflow_orchestrator,persistence_service"
            )
        );

        public static final KernelCapability EVENT_PROCESSING = new KernelCapability(
            "event.processing", "Event Processing", "High-performance event processing",
            CapabilityType.EVENT_PROCESSING, Map.of(
                "throughput", "100k_tps",
                "latency", "<1ms",
                "persistence", "event_sourcing",
                "required_services", "event_bus,event_store,processor"
            )
        );

        public static final KernelCapability AI_ML_FRAMEWORK = new KernelCapability(
            "ai.ml.framework", "AI/ML Framework", "Machine learning and AI capabilities",
            CapabilityType.AI_ML, Map.of(
                "models", "classification,generation,embedding",
                "frameworks", "tensorflow,pytorch,huggingface",
                "required_services", "model_service,inference_engine,training_service"
            )
        );

        public static final KernelCapability OBSERVABILITY_FRAMEWORK = new KernelCapability(
            "observability.framework", "Observability Framework", "Comprehensive observability stack",
            CapabilityType.OBSERVABILITY, Map.of(
                "components", "logging,metrics,tracing,alerting",
                "backends", "prometheus,grafana,jaeger,elasticsearch",
                "required_services", "log_collector,metric_collector,trace_collector"
            )
        );

        public static final KernelCapability SECURITY_FRAMEWORK = new KernelCapability(
            "security.framework", "Security Framework", "Comprehensive security capabilities",
            CapabilityType.SECURITY, Map.of(
                "features", "encryption,authorization,audit,threat_detection",
                "standards", "oauth2,jwt,rbac",
                "required_services", "encryption_service,auth_service,audit_service"
            )
        );
    }

    // Generic capability methods - NO product knowledge
    public boolean requiresService(String serviceId) {
        return requiredServices.contains(serviceId);
    }

    public boolean hasDependency(String capabilityId) {
        return optionalDependencies.contains(capabilityId);
    }

    public boolean isCompatibleWith(KernelCapability other) {
        return this.type == other.type || 
               this.requiredServices.stream().anyMatch(other.requiredServices::contains);
    }

    // Service dependency extraction
    private Set<String> extractRequiredServices(Map<String, Object> metadata) {
        Object services = metadata.get("required_services");
        if (services instanceof String) {
            return Set.of(((String) services).split(","));
        } else if (services instanceof List) {
            return new HashSet<>((List<String>) services);
        }
        return Set.of();
    }

    private Set<String> extractOptionalDependencies(Map<String, Object> metadata) {
        Object dependencies = metadata.get("optional_dependencies");
        if (dependencies instanceof String) {
            return Set.of(((String) dependencies).split(","));
        } else if (dependencies instanceof List) {
            return new HashSet<>((List<String>) dependencies);
        }
        return Set.of();
    }

    // Getters
    public String getCapabilityId() { return capabilityId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public CapabilityType getType() { return type; }
    public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
    public Set<String> getRequiredServices() { return new HashSet<>(requiredServices); }
    public Set<String> getOptionalDependencies() { return new HashSet<>(optionalDependencies); }

    // Equality and hashCode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KernelCapability that = (KernelCapability) o;
        return Objects.equals(capabilityId, that.capabilityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(capabilityId);
    }

    @Override
    public String toString() {
        return String.format("KernelCapability{id='%s', name='%s', type=%s}", 
                           capabilityId, name, type);
    }
}
