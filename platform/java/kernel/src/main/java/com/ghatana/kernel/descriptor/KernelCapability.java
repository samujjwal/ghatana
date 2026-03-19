package com.ghatana.kernel.descriptor;

import java.util.*;

/**
 * Represents a capability that the kernel or a kernel module can provide.
 *
 * <p>Capabilities are the fundamental building blocks of the kernel platform.
 * They define what functionality is available and can be requested by modules.
 * This class is generic and has NO product-specific coupling.</p>
 *
 * @doc.type class
 * @doc.purpose Kernel capability definition with metadata and service requirements
 * @doc.layer core
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class KernelCapability {
    
    private final String capabilityId;
    private final String name;
    private final String description;
    private final CapabilityType type;
    private final Map<String, Object> metadata;
    private final Set<String> requiredServices;
    private final Set<String> optionalDependencies;
    private final Set<String> supportedProducts;
    private final boolean isShared;

    /**
     * Creates a new kernel capability.
     *
     * @param capabilityId unique identifier for the capability
     * @param name human-readable name
     * @param description detailed description
     * @param type the capability type
     * @param metadata additional configuration metadata
     */
    public KernelCapability(String capabilityId, String name, String description, 
                           CapabilityType type, Map<String, Object> metadata) {
        this.capabilityId = Objects.requireNonNull(capabilityId, "capabilityId cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.description = description != null ? description : "";
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        
        // Extract service dependencies from metadata
        this.requiredServices = extractRequiredServices(this.metadata);
        this.optionalDependencies = extractOptionalDependencies(this.metadata);
        
        // Determine if shared and supported products
        this.isShared = this.metadata.getOrDefault("is_shared", "true").equals("true");
        this.supportedProducts = extractSupportedProducts(this.metadata);
        
        validate();
    }

    /**
     * Creates a core capability with minimal configuration.
     */
    private KernelCapability(String capabilityId, String name, CapabilityType type) {
        this(capabilityId, name, "", type, new HashMap<>());
    }

    /**
     * Validates the capability configuration.
     */
    private void validate() {
        if (!capabilityId.matches("^[a-z0-9-._]+$")) {
            throw new IllegalArgumentException(
                "capabilityId must contain only lowercase letters, numbers, hyphens, dots, and underscores: " + capabilityId);
        }
    }

    /**
     * Extracts required services from metadata.
     */
    private Set<String> extractRequiredServices(Map<String, Object> metadata) {
        Object services = metadata.get("required_services");
        if (services instanceof String) {
            return new HashSet<>(Arrays.asList(((String) services).split(",")));
        } else if (services instanceof List) {
            return new HashSet<>((List<String>) services);
        }
        return Collections.emptySet();
    }

    /**
     * Extracts optional dependencies from metadata.
     */
    private Set<String> extractOptionalDependencies(Map<String, Object> metadata) {
        Object dependencies = metadata.get("optional_dependencies");
        if (dependencies instanceof String) {
            return new HashSet<>(Arrays.asList(((String) dependencies).split(",")));
        } else if (dependencies instanceof List) {
            return new HashSet<>((List<String>) dependencies);
        }
        return Collections.emptySet();
    }

    /**
     * Extracts supported products from metadata.
     */
    private Set<String> extractSupportedProducts(Map<String, Object> metadata) {
        Object products = metadata.get("supported_products");
        if (products instanceof String) {
            return new HashSet<>(Arrays.asList(((String) products).split(",")));
        } else if (products instanceof List) {
            return new HashSet<>((List<String>) products);
        }
        return Collections.emptySet();
    }

    // ==================== Core Capabilities ====================

    /**
     * Core kernel capabilities available to all products.
     */
    public static final class Core {
        
        public static final KernelCapability DATA_STORAGE = new KernelCapability(
            "data.storage", "Data Storage", 
            "Unified data storage abstraction with multi-tier support",
            CapabilityType.DATA_MANAGEMENT, 
            Map.of(
                "backends", "postgresql,redis,minio",
                "replication", "true",
                "encryption", "at_rest,in_transit",
                "required_services", "storage_manager,encryption_service"
            )
        );

        public static final KernelCapability USER_AUTHENTICATION = new KernelCapability(
            "user.authentication", "User Authentication",
            "Multi-factor authentication system with session management",
            CapabilityType.SECURITY,
            Map.of(
                "methods", "password,mfa,sso,oauth",
                "session_management", "true",
                "audit", "true",
                "required_services", "auth_service,session_manager"
            )
        );

        public static final KernelCapability API_FRAMEWORK = new KernelCapability(
            "api.framework", "API Framework",
            "RESTful API development framework with validation",
            CapabilityType.API_FRAMEWORK,
            Map.of(
                "protocols", "rest,graphql,websocket",
                "validation", "json_schema",
                "documentation", "openapi",
                "required_services", "router,validator,documentation_generator"
            )
        );

        public static final KernelCapability WORKFLOW_ENGINE = new KernelCapability(
            "workflow.engine", "Workflow Engine",
            "Business workflow orchestration with durability",
            CapabilityType.WORKFLOW,
            Map.of(
                "engine", "temporal,camunda",
                "persistence", "durable",
                "retries", "exponential_backoff",
                "required_services", "workflow_orchestrator,persistence_service"
            )
        );

        public static final KernelCapability EVENT_PROCESSING = new KernelCapability(
            "event.processing", "Event Processing",
            "High-performance event processing with event sourcing",
            CapabilityType.EVENT_PROCESSING,
            Map.of(
                "throughput", "100k_tps",
                "latency", "<1ms",
                "persistence", "event_sourcing",
                "required_services", "event_bus,event_store,processor"
            )
        );

        public static final KernelCapability AI_ML_FRAMEWORK = new KernelCapability(
            "ai.ml.framework", "AI/ML Framework",
            "Machine learning and AI capabilities with model management",
            CapabilityType.AI_ML,
            Map.of(
                "models", "classification,generation,embedding",
                "frameworks", "tensorflow,pytorch,huggingface",
                "required_services", "model_service,inference_engine,training_service"
            )
        );

        public static final KernelCapability OBSERVABILITY_FRAMEWORK = new KernelCapability(
            "observability.framework", "Observability Framework",
            "Comprehensive observability stack with metrics, logs, and traces",
            CapabilityType.OBSERVABILITY,
            Map.of(
                "components", "logging,metrics,tracing,alerting",
                "backends", "prometheus,grafana,jaeger,elasticsearch",
                "required_services", "log_collector,metric_collector,trace_collector"
            )
        );

        public static final KernelCapability SECURITY_FRAMEWORK = new KernelCapability(
            "security.framework", "Security Framework",
            "Comprehensive security capabilities including encryption and audit",
            CapabilityType.SECURITY,
            Map.of(
                "features", "encryption,authorization,audit,threat_detection",
                "standards", "oauth2,jwt,rbac",
                "required_services", "encryption_service,auth_service,audit_service"
            )
        );

        public static final KernelCapability MULTI_FACTOR_AUTH = new KernelCapability(
            "mfa.framework", "Multi-Factor Authentication",
            "Multi-factor authentication with TOTP, SMS, and hardware keys",
            CapabilityType.SECURITY,
            Map.of(
                "methods", "totp,sms,email,hardware_key,biometric",
                "backup_codes", "true",
                "required_services", "mfa_service,totp_generator,notification_service"
            )
        );

        public static final KernelCapability OAUTH_FRAMEWORK = new KernelCapability(
            "oauth.framework", "OAuth Framework",
            "OAuth 2.0 and OpenID Connect authentication framework",
            CapabilityType.SECURITY,
            Map.of(
                "grant_types", "authorization_code,client_credentials,refresh_token",
                "providers", "google,github,azure,saml",
                "pkce", "true",
                "required_services", "oauth_service,token_service,jwks_provider"
            )
        );

        public static final KernelCapability CONFIG_MANAGEMENT = new KernelCapability(
            "config.management", "Configuration Management",
            "Hierarchical configuration resolution and management",
            CapabilityType.CONFIGURATION,
            Map.of(
                "hierarchy", "kernel,product,tenant,user",
                "reload", "true",
                "required_services", "config_service"
            )
        );

        public static final KernelCapability TENANT_ISOLATION = new KernelCapability(
            "tenant.isolation", "Tenant Isolation",
            "Multi-tenant data and resource isolation",
            CapabilityType.SECURITY,
            Map.of(
                "isolation_level", "strict",
                "data_separation", "true",
                "required_services", "tenant_service,isolation_enforcer"
            )
        );
    }

    // ==================== Getters ====================

    public String getCapabilityId() { return capabilityId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public CapabilityType getType() { return type; }
    public Map<String, Object> getMetadata() { return Collections.unmodifiableMap(metadata); }
    public Set<String> getRequiredServices() { return Collections.unmodifiableSet(requiredServices); }
    public Set<String> getOptionalDependencies() { return Collections.unmodifiableSet(optionalDependencies); }
    public Set<String> getSupportedProducts() { return Collections.unmodifiableSet(supportedProducts); }
    public boolean isShared() { return isShared; }

    // ==================== Product-Specific Capabilities ====================

    /**
     * Product-specific capabilities that extend the core kernel capabilities.
     * These capabilities are declared by product modules and define domain-specific functionality.
     */
    public static final class Products {

        // ==================== PHR (Personal Health Record) Capabilities ====================

        public static final KernelCapability PATIENT_RECORDS = new KernelCapability(
            "phr.patient-records", "Patient Records",
            "Healthcare patient record management with FHIR R4 support",
            CapabilityType.BUSINESS_LOGIC,
            Map.of(
                "domain", "healthcare",
                "standards", "fhir-r4,nepal-2081",
                "retention_years", "25",
                "is_shared", "false",
                "supported_products", "phr"
            )
        );

        public static final KernelCapability CONSENT_MANAGEMENT = new KernelCapability(
            "phr.consent-management", "Consent Management",
            "Patient consent management with regulatory compliance",
            CapabilityType.COMPLIANCE,
            Map.of(
                "domain", "healthcare",
                "regulations", "nepal-2081,privacy-act-2075,fhir-consent",
                "granularity", "field-level",
                "is_shared", "false",
                "supported_products", "phr"
            )
        );

        public static final KernelCapability FHIR_INTEROP = new KernelCapability(
            "phr.fhir-interop", "FHIR Interoperability",
            "FHIR R4 resource processing and interoperability",
            CapabilityType.INTEGRATION,
            Map.of(
                "domain", "healthcare",
                "version", "r4",
                "resources", "patient,observation,medication,appointment",
                "is_shared", "false",
                "supported_products", "phr"
            )
        );

        public static final KernelCapability CLINICAL_DOCUMENTS = new KernelCapability(
            "phr.clinical-documents", "Clinical Documents",
            "Medical document storage with OCR and imaging support",
            CapabilityType.DATA_MANAGEMENT,
            Map.of(
                "domain", "healthcare",
                "types", "lab-reports,imaging,discharge-summary",
                "ocr", "true",
                "is_shared", "false",
                "supported_products", "phr"
            )
        );

        public static final KernelCapability MEDICATION_MANAGEMENT = new KernelCapability(
            "phr.medication-management", "Medication Management",
            "Prescription management and medication tracking",
            CapabilityType.BUSINESS_LOGIC,
            Map.of(
                "domain", "healthcare",
                "features", "prescriptions,refills,interactions",
                "is_shared", "false",
                "supported_products", "phr"
            )
        );

        // ==================== Finance Capabilities ====================

        public static final KernelCapability TRADE_PROCESSING = new KernelCapability(
            "finance.trade-processing", "Trade Processing",
            "High-frequency trade order processing and execution",
            CapabilityType.BUSINESS_LOGIC,
            Map.of(
                "domain", "finance",
                "latency", "microsecond",
                "throughput", "100k-tps",
                "is_shared", "false",
                "supported_products", "finance"
            )
        );

        public static final KernelCapability RISK_MANAGEMENT = new KernelCapability(
            "finance.risk-management", "Risk Management",
            "Real-time risk assessment and position monitoring",
            CapabilityType.BUSINESS_LOGIC,
            Map.of(
                "domain", "finance",
                "types", "market,credit,operational,liquidity",
                "calculation", "real-time",
                "is_shared", "false",
                "supported_products", "finance"
            )
        );

        public static final KernelCapability COMPLIANCE_CHECKING = new KernelCapability(
            "finance.compliance-checking", "Compliance Checking",
            "Financial compliance monitoring and regulatory reporting",
            CapabilityType.COMPLIANCE,
            Map.of(
                "domain", "finance",
                "regulations", "securities,aml,kyc,mifid",
                "reporting", "automated",
                "is_shared", "false",
                "supported_products", "finance"
            )
        );

        public static final KernelCapability LEDGER_MANAGEMENT = new KernelCapability(
            "finance.ledger-management", "Ledger Management",
            "Double-entry bookkeeping and ledger operations",
            CapabilityType.DATA_MANAGEMENT,
            Map.of(
                "domain", "finance",
                "type", "double-entry",
                "currencies", "multi-currency",
                "is_shared", "false",
                "supported_products", "finance"
            )
        );

        public static final KernelCapability PORTFOLIO_MANAGEMENT = new KernelCapability(
            "finance.portfolio-management", "Portfolio Management",
            "Investment portfolio tracking and performance analysis",
            CapabilityType.BUSINESS_LOGIC,
            Map.of(
                "domain", "finance",
                "analytics", "real-time",
                "rebalancing", "automated",
                "is_shared", "false",
                "supported_products", "finance"
            )
        );

        // ==================== FlashIt Capabilities ====================

        public static final KernelCapability MOMENT_CAPTURE = new KernelCapability(
            "flashit.moment-capture", "Moment Capture",
            "Multi-modal content capture (text, voice, video, images)",
            CapabilityType.BUSINESS_LOGIC,
            Map.of(
                "domain", "personal-ai",
                "modalities", "text,voice,video,image",
                "offline_support", "true",
                "is_shared", "false",
                "supported_products", "flashit"
            )
        );

        public static final KernelCapability REFLECTION_ENGINE = new KernelCapability(
            "flashit.reflection-engine", "Reflection Engine",
            "AI-powered reflection and insight generation",
            CapabilityType.AI_ML,
            Map.of(
                "domain", "personal-ai",
                "model", "adaptive",
                "privacy", "local-processing",
                "is_shared", "false",
                "supported_products", "flashit"
            )
        );

        public static final KernelCapability CONTEXT_SEARCH = new KernelCapability(
            "flashit.context-search", "Context Search",
            "Semantic search across captured moments",
            CapabilityType.AI_ML,
            Map.of(
                "domain", "personal-ai",
                "type", "semantic",
                "embeddings", "true",
                "is_shared", "false",
                "supported_products", "flashit"
            )
        );

        // ==================== Aura Capabilities ====================

        public static final KernelCapability RECOMMENDATION_ENGINE = new KernelCapability(
            "aura.recommendation-engine", "Recommendation Engine",
            "Personalized content and product recommendations",
            CapabilityType.AI_ML,
            Map.of(
                "domain", "recommendations",
                "algorithms", "collaborative-filtering,content-based,hybrid",
                "real_time", "true",
                "is_shared", "false",
                "supported_products", "aura"
            )
        );

        public static final KernelCapability ONTOLOGY_MANAGER = new KernelCapability(
            "aura.ontology-manager", "Ontology Manager",
            "Knowledge graph and ontology management",
            CapabilityType.AI_ML,
            Map.of(
                "domain", "knowledge-graph",
                "graph", "semantic",
                "inference", "true",
                "is_shared", "false",
                "supported_products", "aura"
            )
        );

        public static final KernelCapability ANALYTICS_FRAMEWORK = new KernelCapability(
            "aura.analytics-framework", "Analytics Framework",
            "Comprehensive user behavior and engagement analytics",
            CapabilityType.OBSERVABILITY,
            Map.of(
                "domain", "analytics",
                "real_time", "true",
                "funnels", "true",
                "is_shared", "false",
                "supported_products", "aura"
            )
        );

        // ==================== Shared Product Capabilities ====================

        public static final KernelCapability USER_AUTHENTICATION = new KernelCapability(
            "shared.user-authentication", "User Authentication",
            "Multi-factor authentication across all products",
            CapabilityType.SECURITY,
            Map.of(
                "methods", "password,mfa,sso,oauth,biometric",
                "session_management", "true",
                "is_shared", "true"
            )
        );

        public static final KernelCapability DATA_STORAGE = new KernelCapability(
            "shared.data-storage", "Data Storage",
            "Unified data storage abstraction across products",
            CapabilityType.DATA_MANAGEMENT,
            Map.of(
                "backends", "postgresql,redis,minio",
                "multi_tenant", "true",
                "encryption", "true",
                "is_shared", "true"
            )
        );

        public static final KernelCapability API_FRAMEWORK = new KernelCapability(
            "shared.api-framework", "API Framework",
            "RESTful API framework with validation and documentation",
            CapabilityType.API_FRAMEWORK,
            Map.of(
                "protocols", "rest,graphql,websocket",
                "validation", "json-schema",
                "documentation", "openapi",
                "is_shared", "true"
            )
        );

        public static final KernelCapability WORKFLOW_ENGINE = new KernelCapability(
            "shared.workflow-engine", "Workflow Engine",
            "Business workflow orchestration across products",
            CapabilityType.WORKFLOW,
            Map.of(
                "durability", "true",
                "retries", "exponential-backoff",
                "saga", "true",
                "is_shared", "true"
            )
        );

        public static final KernelCapability NOTIFICATION_SERVICE = new KernelCapability(
            "shared.notification-service", "Notification Service",
            "Multi-channel notification delivery system",
            CapabilityType.INTEGRATION,
            Map.of(
                "channels", "email,sms,push,in-app",
                "templating", "true",
                "priority", "true",
                "is_shared", "true"
            )
        );
    }

    // ==================== Business Methods ====================

    /**
     * Checks if this capability requires a specific service.
     *
     * @param serviceId the service identifier
     * @return true if the service is required
     */
    public boolean requiresService(String serviceId) {
        return requiredServices.contains(serviceId);
    }

    /**
     * Checks if this capability has a dependency on another capability.
     *
     * @param capabilityId the capability identifier
     * @return true if there's a dependency
     */
    public boolean hasDependency(String capabilityId) {
        return optionalDependencies.contains(capabilityId);
    }

    /**
     * Checks if this capability supports a specific product.
     *
     * @param productId the product identifier
     * @return true if the product is supported (empty means all products)
     */
    public boolean supportsProduct(String productId) {
        return supportedProducts.isEmpty() || supportedProducts.contains(productId);
    }

    /**
     * Checks if this capability is compatible with another capability.
     * Capabilities are compatible if they share the same type or have
     * overlapping service requirements.
     *
     * @param other the other capability
     * @return true if compatible
     */
    public boolean isCompatibleWith(KernelCapability other) {
        if (this.type == other.type) {
            return true;
        }
        
        // Check for overlapping required services
        Set<String> intersection = new HashSet<>(this.requiredServices);
        intersection.retainAll(other.requiredServices);
        return !intersection.isEmpty();
    }

    /**
     * Gets metadata value with type safety.
     *
     * @param key the metadata key
     * @param type the expected type
     * @param <T> the type parameter
     * @return the metadata value or null
     */
    public <T> T getMetadata(String key, Class<T> type) {
        Object value = metadata.get(key);
        if (value == null) return null;
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        return null;
    }

    /**
     * Gets metadata value with default.
     *
     * @param key the metadata key
     * @param defaultValue the default value
     * @return the metadata value or default
     */
    public String getMetadata(String key, String defaultValue) {
        Object value = metadata.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    // ==================== Object Methods ====================

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
        return String.format("KernelCapability{id='%s', name='%s', type=%s, shared=%b}",
            capabilityId, name, type, isShared);
    }

    // ==================== Capability Types ====================

    /**
     * Types of kernel capabilities.
     */
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
        OBSERVABILITY,
        CONFIGURATION
    }
}
