package com.ghatana.datacloud.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

/**
 * Represents a field definition within a collection schema.
 *
 * <p><b>Purpose</b><br>
 * Defines individual fields in a collection with type, validation rules, and UI configuration.
 * Supports flexible schema evolution and multi-application field visibility.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * MetaField field = MetaField.builder()
 *     .collectionId(collectionId)
 *     .name("price")
 *     .label("Product Price")
 *     .type(DataType.NUMBER)
 *     .required(true)
 *     .validation(Map.of("min", 0, "max", 999999))
 *     .uiConfig(Map.of("visible", true, "order", 1))
 *     .build();
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Domain model in hexagonal architecture
 * - Child entity of MetaCollection (one-to-many relationship)
 * - Persisted via JPA/Hibernate
 * - Used by DynamicQueryBuilder for SQL generation
 * - Used by ValidationService for field validation
 * - Used by UI layer for form rendering
 *
 * <p><b>Thread Safety</b><br>
 * Mutable JPA entity - not thread-safe. Use within transaction boundaries only.
 *
 * @see MetaCollection
 * @see DataType
 * @see com.ghatana.datacloud.application.DynamicQueryBuilder
 * @see com.ghatana.datacloud.application.ValidationService
 * @doc.type class
 * @doc.purpose Field definition within collection schema
 * @doc.layer product
 * @doc.pattern Domain Model (JPA Entity)
 */
@jakarta.persistence.Entity
@Table(
    name = "meta_fields",
    indexes = {
        @Index(name = "idx_meta_fields_collection", columnList = "collection_id"),
        @Index(name = "idx_meta_fields_active", columnList = "collection_id, active"),
        @Index(name = "idx_meta_fields_type", columnList = "type")
    }
)
public class MetaField {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull(message = "Collection ID is required")
    @Column(name = "collection_id", nullable = false)
    private UUID collectionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id", insertable = false, updatable = false)
    private MetaCollection collection;

    @NotBlank(message = "Field name is required")
    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 255)
    private String label;

    @NotNull(message = "Field type is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private DataType type;

    /**
     * For REFERENCE type: name of the referenced collection.
     */
    @Column(name = "reference_collection", length = 255)
    private String referenceCollection;

    /**
     * Validation rules as JSONB.
     * Format: {"min": 0, "max": 100, "pattern": "^[A-Z].*", "required": true}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> validation = new HashMap<>();

    /**
     * UI configuration as JSONB.
     * Format: {"visible": true, "order": 1, "span": 6, "placeholder": "Enter value"}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ui_config", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> uiConfig = new HashMap<>();

    @Column(nullable = false)
    private Boolean required = false;

    @Column(name = "unique_constraint", nullable = false)
    private Boolean uniqueConstraint = false;

    @Column(name = "default_value", columnDefinition = "TEXT")
    private String defaultValue;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // ============ Getters & Setters ============

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(UUID collectionId) {
        this.collectionId = collectionId;
    }

    public MetaCollection getCollection() {
        return collection;
    }

    public void setCollection(MetaCollection collection) {
        this.collection = collection;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public DataType getType() {
        return type;
    }

    public void setType(DataType type) {
        this.type = type;
    }

    public String getReferenceCollection() {
        return referenceCollection;
    }

    public void setReferenceCollection(String referenceCollection) {
        this.referenceCollection = referenceCollection;
    }

    /**
     * Returns the validation rules as a type-safe record.
     *
     * @return validation rules as FieldValidation
     */
    public FieldValidation getValidationTyped() {
        if (validation == null || validation.isEmpty()) {
            return FieldValidation.empty();
        }
        return FieldValidation.fromMap(validation);
    }

    /**
     * Sets the validation rules from a type-safe record.
     *
     * @param fieldValidation validation rules
     */
    public void setValidationTyped(FieldValidation fieldValidation) {
        this.validation = fieldValidation != null ? fieldValidation.toMap() : new HashMap<>();
    }

    /**
     * Returns the UI config as a type-safe record.
     *
     * @return UI config as FieldUiConfig
     */
    public FieldUiConfig getUiConfigTyped() {
        if (uiConfig == null || uiConfig.isEmpty()) {
            return FieldUiConfig.empty();
        }
        return FieldUiConfig.fromMap(uiConfig);
    }

    /**
     * Sets the UI config from a type-safe record.
     *
     * @param fieldUiConfig UI config
     */
    public void setUiConfigTyped(FieldUiConfig fieldUiConfig) {
        this.uiConfig = fieldUiConfig != null ? fieldUiConfig.toMap() : new HashMap<>();
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public Boolean getUniqueConstraint() {
        return uniqueConstraint;
    }

    public void setUniqueConstraint(Boolean uniqueConstraint) {
        this.uniqueConstraint = uniqueConstraint;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    // ============ Builder Pattern ============

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private UUID collectionId;
        private String name;
        private String label;
        private DataType type;
        private String referenceCollection;
        private Map<String, Object> validation = new HashMap<>();
        private Map<String, Object> uiConfig = new HashMap<>();
        private Boolean required = false;
        private Boolean uniqueConstraint = false;
        private String defaultValue;
        private Integer displayOrder = 0;
        private Boolean active = true;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder collectionId(UUID collectionId) {
            this.collectionId = collectionId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder type(DataType type) {
            this.type = type;
            return this;
        }

        public Builder referenceCollection(String referenceCollection) {
            this.referenceCollection = referenceCollection;
            return this;
        }

        /**
         * Sets validation rules from a type-safe record.
         *
         * @param validation validation rules
         * @return this builder
         */
        public Builder validation(FieldValidation validation) {
            this.validation = validation != null ? validation.toMap() : new HashMap<>();
            return this;
        }

        /**
         * Sets UI config from a type-safe record.
         *
         * @param uiConfig UI config
         * @return this builder
         */
        public Builder uiConfig(FieldUiConfig uiConfig) {
            this.uiConfig = uiConfig != null ? uiConfig.toMap() : new HashMap<>();
            return this;
        }

        public Builder required(Boolean required) {
            this.required = required;
            return this;
        }

        public Builder uniqueConstraint(Boolean uniqueConstraint) {
            this.uniqueConstraint = uniqueConstraint;
            return this;
        }

        public Builder defaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public Builder displayOrder(Integer displayOrder) {
            this.displayOrder = displayOrder;
            return this;
        }

        public Builder active(Boolean active) {
            this.active = active;
            return this;
        }

        public MetaField build() {
            MetaField field = new MetaField();
            field.id = this.id;
            field.collectionId = this.collectionId;
            field.name = this.name;
            field.label = this.label;
            field.type = this.type;
            field.referenceCollection = this.referenceCollection;
            field.validation = this.validation;
            field.uiConfig = this.uiConfig;
            field.required = this.required;
            field.uniqueConstraint = this.uniqueConstraint;
            field.defaultValue = this.defaultValue;
            field.displayOrder = this.displayOrder;
            field.active = this.active;
            return field;
        }
    }

    @Override
    public String toString() {
        return "MetaField{" +
                "id=" + id +
                ", collectionId=" + collectionId +
                ", name='" + name + '\'' +
                ", type=" + type +
                ", required=" + required +
                ", active=" + active +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetaField metaField = (MetaField) o;
        return Objects.equals(id, metaField.id) &&
                Objects.equals(collectionId, metaField.collectionId) &&
                Objects.equals(name, metaField.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, collectionId, name);
    }
}
