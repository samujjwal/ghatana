package com.ghatana.kernel.descriptor;

import java.time.Instant;
import java.util.*;

/**
 * Immutable value object describing a kernel component's identity, capabilities, and dependencies.
 *
 * <p>This class provides a comprehensive descriptor for all kernel components including
 * modules, plugins, extensions, and capabilities. It follows the builder pattern for
 * flexible construction while maintaining immutability.</p>
 *
 * @doc.type class
 * @doc.purpose Kernel component descriptor with identity, capabilities, and dependency metadata
 * @doc.layer core
 * @doc.pattern ValueObject, Builder
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class KernelDescriptor {
    
    // Core identity fields
    private final String descriptorId;
    private final String name;
    private final String version;
    private final String description;
    private final String owner;
    private final DescriptorType type;
    
    // Classification fields
    private final Set<String> tags;
    private final Map<String, String> metadata;
    
    // Capability and dependency fields
    private final Set<KernelCapability> capabilities;
    private final Set<KernelDependency> dependencies;
    private final Set<KernelCompatibility> compatibility;
    
    // Operational fields
    private final LifecyclePolicy lifecyclePolicy;
    private final ResourceRequirements resourceRequirements;
    private final SecurityPolicy securityPolicy;
    
    // Deployment fields
    private final Set<String> supportedTenants;
    private final Set<String> requiredFeatures;
    private final Set<String> optionalFeatures;
    private final List<ValidationRule> validationRules;
    private final Set<String> complianceRequirements;
    private final AuditPolicy auditPolicy;
    
    // Build information
    private final BuildInformation buildInfo;
    private final DeploymentConfiguration deploymentConfig;
    
    // Timestamps
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * Private constructor - use Builder to create instances.
     */
    private KernelDescriptor(Builder builder) {
        // Validate required fields
        validateRequiredFields(builder);
        
        // Core identity
        this.descriptorId = Objects.requireNonNull(builder.descriptorId, "descriptorId cannot be null");
        this.name = Objects.requireNonNull(builder.name, "name cannot be null");
        this.version = Objects.requireNonNull(builder.version, "version cannot be null");
        this.description = builder.description != null ? builder.description : "";
        this.owner = builder.owner != null ? builder.owner : "system";
        this.type = Objects.requireNonNull(builder.type, "type cannot be null");
        
        // Classification (immutable copies)
        this.tags = Collections.unmodifiableSet(new HashSet<>(builder.tags));
        this.metadata = Collections.unmodifiableMap(new HashMap<>(builder.metadata));
        
        // Capabilities and dependencies (immutable copies)
        this.capabilities = Collections.unmodifiableSet(new HashSet<>(builder.capabilities));
        this.dependencies = Collections.unmodifiableSet(new HashSet<>(builder.dependencies));
        this.compatibility = Collections.unmodifiableSet(new HashSet<>(builder.compatibility));
        
        // Operational (use defaults if not specified)
        this.lifecyclePolicy = builder.lifecyclePolicy != null 
            ? builder.lifecyclePolicy 
            : LifecyclePolicy.defaultPolicy();
        this.resourceRequirements = builder.resourceRequirements != null 
            ? builder.resourceRequirements 
            : ResourceRequirements.defaultRequirements();
        this.securityPolicy = builder.securityPolicy != null 
            ? builder.securityPolicy 
            : SecurityPolicy.defaultPolicy();
        
        // Deployment (immutable copies with defaults)
        this.supportedTenants = Collections.unmodifiableSet(new HashSet<>(builder.supportedTenants));
        this.requiredFeatures = Collections.unmodifiableSet(new HashSet<>(builder.requiredFeatures));
        this.optionalFeatures = Collections.unmodifiableSet(new HashSet<>(builder.optionalFeatures));
        this.validationRules = Collections.unmodifiableList(new ArrayList<>(builder.validationRules));
        this.complianceRequirements = Collections.unmodifiableSet(new HashSet<>(builder.complianceRequirements));
        this.auditPolicy = builder.auditPolicy != null 
            ? builder.auditPolicy 
            : AuditPolicy.defaultPolicy();
        
        // Build info and deployment config
        this.buildInfo = builder.buildInfo;
        this.deploymentConfig = builder.deploymentConfig;
        
        // Timestamps
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.updatedAt = builder.updatedAt != null ? builder.updatedAt : Instant.now();
    }

    /**
     * Validates required fields in the builder.
     *
     * @param builder the builder to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateRequiredFields(Builder builder) {
        List<String> errors = new ArrayList<>();
        
        if (builder.descriptorId == null || builder.descriptorId.trim().isEmpty()) {
            errors.add("descriptorId is required and cannot be empty");
        } else if (!builder.descriptorId.matches("^[a-z0-9-]+$")) {
            errors.add("descriptorId must contain only lowercase letters, numbers, and hyphens");
        }
        
        if (builder.name == null || builder.name.trim().isEmpty()) {
            errors.add("name is required and cannot be empty");
        }
        
        if (builder.version == null || builder.version.trim().isEmpty()) {
            errors.add("version is required and cannot be empty");
        } else if (!isValidVersion(builder.version)) {
            errors.add("version must follow semantic versioning (e.g., 1.0.0)");
        }
        
        if (builder.type == null) {
            errors.add("type is required");
        }
        
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("KernelDescriptor validation failed: " + String.join(", ", errors));
        }
    }

    /**
     * Validates semantic version format.
     */
    private boolean isValidVersion(String version) {
        return version.matches("^\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)*)?(\\+[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)*)?$");
    }

    // ==================== Getters ====================

    public String getDescriptorId() { return descriptorId; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getDescription() { return description; }
    public String getOwner() { return owner; }
    public DescriptorType getType() { return type; }
    public Set<String> getTags() { return tags; }
    public Map<String, String> getMetadata() { return metadata; }
    public Set<KernelCapability> getCapabilities() { return capabilities; }
    public Set<KernelDependency> getDependencies() { return dependencies; }
    public Set<KernelCompatibility> getCompatibility() { return compatibility; }
    public LifecyclePolicy getLifecyclePolicy() { return lifecyclePolicy; }
    public ResourceRequirements getResourceRequirements() { return resourceRequirements; }
    public SecurityPolicy getSecurityPolicy() { return securityPolicy; }
    public Set<String> getSupportedTenants() { return supportedTenants; }
    public Set<String> getRequiredFeatures() { return requiredFeatures; }
    public Set<String> getOptionalFeatures() { return optionalFeatures; }
    public List<ValidationRule> getValidationRules() { return validationRules; }
    public Set<String> getComplianceRequirements() { return complianceRequirements; }
    public AuditPolicy getAuditPolicy() { return auditPolicy; }
    public BuildInformation getBuildInfo() { return buildInfo; }
    public DeploymentConfiguration getDeploymentConfig() { return deploymentConfig; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // ==================== Business Methods ====================

    /**
     * Checks if this descriptor has a specific capability.
     *
     * @param capability the capability to check
     * @return true if the descriptor has the capability
     */
    public boolean hasCapability(KernelCapability capability) {
        return capabilities.contains(capability);
    }

    /**
     * Checks if this descriptor has a capability by ID.
     *
     * @param capabilityId the capability ID to check
     * @return true if the descriptor has the capability
     */
    public boolean hasCapability(String capabilityId) {
        return capabilities.stream()
            .anyMatch(c -> c.getCapabilityId().equals(capabilityId));
    }

    /**
     * Checks if this descriptor supports a specific tenant.
     *
     * @param tenantId the tenant ID to check
     * @return true if the tenant is supported (empty set means all tenants)
     */
    public boolean supportsTenant(String tenantId) {
        return supportedTenants.isEmpty() || supportedTenants.contains(tenantId);
    }

    /**
     * Checks if this descriptor requires a specific feature.
     *
     * @param featureId the feature ID to check
     * @return true if the feature is required
     */
    public boolean requiresFeature(String featureId) {
        return requiredFeatures.contains(featureId);
    }

    /**
     * Checks if this descriptor is compatible with a kernel version.
     *
     * @param kernelVersion the kernel version to check
     * @return true if compatible
     */
    public boolean isCompatibleWith(String kernelVersion) {
        return compatibility.stream()
            .anyMatch(comp -> comp.isCompatible(kernelVersion));
    }

    /**
     * Checks if this descriptor meets a compliance requirement.
     *
     * @param requirement the compliance requirement to check
     * @return true if the requirement is met
     */
    public boolean meetsComplianceRequirement(String requirement) {
        return complianceRequirements.contains(requirement);
    }

    /**
     * Gets metadata value with default.
     *
     * @param key the metadata key
     * @param defaultValue the default value if key not found
     * @return the metadata value or default
     */
    public String getMetadata(String key, String defaultValue) {
        return metadata.getOrDefault(key, defaultValue);
    }

    /**
     * Creates a new builder with values from this descriptor.
     * Useful for creating modified copies.
     *
     * @return a new builder pre-populated with this descriptor's values
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    // ==================== Object Methods ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KernelDescriptor that = (KernelDescriptor) o;
        return Objects.equals(descriptorId, that.descriptorId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(descriptorId);
    }

    @Override
    public String toString() {
        return String.format("KernelDescriptor{id=%s, name=%s, version=%s, type=%s, capabilities=%d}",
            descriptorId, name, version, type, capabilities.size());
    }

    // ==================== Builder ====================

    /**
     * Builder for constructing KernelDescriptor instances.
     */
    public static class Builder {
        // Required fields
        private String descriptorId;
        private String name;
        private String version;
        private DescriptorType type;
        
        // Optional fields with defaults
        private String description = "";
        private String owner = "system";
        private Set<String> tags = new HashSet<>();
        private Map<String, String> metadata = new HashMap<>();
        private Set<KernelCapability> capabilities = new HashSet<>();
        private Set<KernelDependency> dependencies = new HashSet<>();
        private Set<KernelCompatibility> compatibility = new HashSet<>();
        private LifecyclePolicy lifecyclePolicy;
        private ResourceRequirements resourceRequirements;
        private SecurityPolicy securityPolicy;
        private Set<String> supportedTenants = new HashSet<>();
        private Set<String> requiredFeatures = new HashSet<>();
        private Set<String> optionalFeatures = new HashSet<>();
        private List<ValidationRule> validationRules = new ArrayList<>();
        private Set<String> complianceRequirements = new HashSet<>();
        private AuditPolicy auditPolicy;
        private BuildInformation buildInfo;
        private DeploymentConfiguration deploymentConfig;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder() {}

        /**
         * Constructor to create a builder from an existing descriptor.
         */
        private Builder(KernelDescriptor descriptor) {
            this.descriptorId = descriptor.descriptorId;
            this.name = descriptor.name;
            this.version = descriptor.version;
            this.description = descriptor.description;
            this.owner = descriptor.owner;
            this.type = descriptor.type;
            this.tags = new HashSet<>(descriptor.tags);
            this.metadata = new HashMap<>(descriptor.metadata);
            this.capabilities = new HashSet<>(descriptor.capabilities);
            this.dependencies = new HashSet<>(descriptor.dependencies);
            this.compatibility = new HashSet<>(descriptor.compatibility);
            this.lifecyclePolicy = descriptor.lifecyclePolicy;
            this.resourceRequirements = descriptor.resourceRequirements;
            this.securityPolicy = descriptor.securityPolicy;
            this.supportedTenants = new HashSet<>(descriptor.supportedTenants);
            this.requiredFeatures = new HashSet<>(descriptor.requiredFeatures);
            this.optionalFeatures = new HashSet<>(descriptor.optionalFeatures);
            this.validationRules = new ArrayList<>(descriptor.validationRules);
            this.complianceRequirements = new HashSet<>(descriptor.complianceRequirements);
            this.auditPolicy = descriptor.auditPolicy;
            this.buildInfo = descriptor.buildInfo;
            this.deploymentConfig = descriptor.deploymentConfig;
            this.createdAt = descriptor.createdAt;
            this.updatedAt = descriptor.updatedAt;
        }

        // Required field setters
        public Builder withDescriptorId(String descriptorId) {
            this.descriptorId = descriptorId;
            return this;
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withVersion(String version) {
            this.version = version;
            return this;
        }

        public Builder withType(DescriptorType type) {
            this.type = type;
            return this;
        }

        // Optional field setters
        public Builder withDescription(String description) {
            this.description = description;
            return this;
        }

        public Builder withOwner(String owner) {
            this.owner = owner;
            return this;
        }

        public Builder withTag(String tag) {
            this.tags.add(tag);
            return this;
        }

        public Builder withTags(Set<String> tags) {
            this.tags = new HashSet<>(tags);
            return this;
        }

        public Builder withMetadata(String key, String value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder withMetadata(Map<String, String> metadata) {
            this.metadata = new HashMap<>(metadata);
            return this;
        }

        public Builder withCapability(KernelCapability capability) {
            this.capabilities.add(capability);
            return this;
        }

        public Builder withCapabilities(Set<KernelCapability> capabilities) {
            this.capabilities = new HashSet<>(capabilities);
            return this;
        }

        public Builder withDependency(KernelDependency dependency) {
            this.dependencies.add(dependency);
            return this;
        }

        public Builder withDependencies(Set<KernelDependency> dependencies) {
            this.dependencies = new HashSet<>(dependencies);
            return this;
        }

        public Builder withCompatibility(KernelCompatibility compatibility) {
            this.compatibility.add(compatibility);
            return this;
        }

        public Builder withLifecyclePolicy(LifecyclePolicy policy) {
            this.lifecyclePolicy = policy;
            return this;
        }

        public Builder withResourceRequirements(ResourceRequirements requirements) {
            this.resourceRequirements = requirements;
            return this;
        }

        public Builder withSecurityPolicy(SecurityPolicy policy) {
            this.securityPolicy = policy;
            return this;
        }

        public Builder withSupportedTenant(String tenantId) {
            this.supportedTenants.add(tenantId);
            return this;
        }

        public Builder withRequiredFeature(String featureId) {
            this.requiredFeatures.add(featureId);
            return this;
        }

        public Builder withOptionalFeature(String featureId) {
            this.optionalFeatures.add(featureId);
            return this;
        }

        public Builder withValidationRule(ValidationRule rule) {
            this.validationRules.add(rule);
            return this;
        }

        public Builder withComplianceRequirement(String requirement) {
            this.complianceRequirements.add(requirement);
            return this;
        }

        public Builder withAuditPolicy(AuditPolicy policy) {
            this.auditPolicy = policy;
            return this;
        }

        public Builder withBuildInfo(BuildInformation buildInfo) {
            this.buildInfo = buildInfo;
            return this;
        }

        public Builder withDeploymentConfig(DeploymentConfiguration config) {
            this.deploymentConfig = config;
            return this;
        }

        public Builder withCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder withUpdatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        /**
         * Builds the KernelDescriptor instance.
         *
         * @return a new immutable KernelDescriptor
         * @throws IllegalArgumentException if validation fails
         */
        public KernelDescriptor build() {
            return new KernelDescriptor(this);
        }
    }

    /**
     * Descriptor types for classification.
     */
    public enum DescriptorType {
        MODULE,
        PLUGIN,
        EXTENSION,
        CAPABILITY,
        SERVICE,
        ADAPTER,
        CONFIGURATION
    }
}
