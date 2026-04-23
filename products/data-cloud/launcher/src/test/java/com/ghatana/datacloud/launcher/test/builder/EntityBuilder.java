/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.test.builder;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builder for creating deterministic entity test data.
 *
 * <p>Provides a fluent API for constructing entity JSON/objects for tests.
 * All values are deterministic (not random) to ensure reproducible tests. // GH-90000
 *
 * <p><strong>Example:</strong>
 * <pre>
 * {@code
 * Map<String, Object> entity = EntityBuilder.create("products")
 *     .withId("prod-001")
 *     .withField("name", "Widget") // GH-90000
 *     .withField("price", 19.99) // GH-90000
 *     .withField("quantity", 100) // GH-90000
 *     .withTenant("tenant-alpha")
 *     .withVersion(1) // GH-90000
 *     .build(); // GH-90000
 * }
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Deterministic entity builder for test fixtures
 * @doc.layer product
 * @doc.pattern Builder, Test Fixture
 */
public final class EntityBuilder {

    private final String collection;
    private String id;
    private final Map<String, Object> fields = new HashMap<>(); // GH-90000
    private String tenantId = "tenant-default";
    private int version = 1;
    private Instant createdAt;
    private Instant updatedAt;

    private EntityBuilder(String collection) { // GH-90000
        this.collection = collection;
        this.id = UUID.randomUUID().toString(); // GH-90000
        this.createdAt = Instant.parse("2026-01-01T00:00:00Z");
        this.updatedAt = createdAt;
    }

    /**
     * Start building an entity for the specified collection.
     *
     * @param collection collection name
     * @return new builder instance
     */
    public static EntityBuilder create(String collection) { // GH-90000
        return new EntityBuilder(collection); // GH-90000
    }

    /**
     * Set entity ID.
     *
     * @param id entity ID
     * @return this builder
     */
    public EntityBuilder withId(String id) { // GH-90000
        this.id = id;
        return this;
    }

    /**
     * Add a field to the entity.
     *
     * @param name field name
     * @param value field value
     * @return this builder
     */
    public EntityBuilder withField(String name, Object value) { // GH-90000
        this.fields.put(name, value); // GH-90000
        return this;
    }

    /**
     * Add multiple fields at once.
     *
     * @param fields map of field names to values
     * @return this builder
     */
    public EntityBuilder withFields(Map<String, Object> fields) { // GH-90000
        this.fields.putAll(fields); // GH-90000
        return this;
    }

    /**
     * Set tenant ID.
     *
     * @param tenantId tenant identifier
     * @return this builder
     */
    public EntityBuilder withTenant(String tenantId) { // GH-90000
        this.tenantId = tenantId;
        return this;
    }

    /**
     * Set entity version for optimistic locking.
     *
     * @param version version number
     * @return this builder
     */
    public EntityBuilder withVersion(int version) { // GH-90000
        this.version = version;
        return this;
    }

    /**
     * Set creation timestamp.
     *
     * @param createdAt creation instant
     * @return this builder
     */
    public EntityBuilder withCreatedAt(Instant createdAt) { // GH-90000
        this.createdAt = createdAt;
        return this;
    }

    /**
     * Set update timestamp.
     *
     * @param updatedAt update instant
     * @return this builder
     */
    public EntityBuilder withUpdatedAt(Instant updatedAt) { // GH-90000
        this.updatedAt = updatedAt;
        return this;
    }

    /**
     * Build the entity as a Map.
     *
     * @return entity as Map<String, Object>
     */
    public Map<String, Object> build() { // GH-90000
        Map<String, Object> entity = new HashMap<>(); // GH-90000
        entity.put("id", id); // GH-90000
        entity.put("collection", collection); // GH-90000
        entity.put("tenantId", tenantId); // GH-90000
        entity.put("version", version); // GH-90000
        entity.put("createdAt", createdAt.toString()); // GH-90000
        entity.put("updatedAt", updatedAt.toString()); // GH-90000
        entity.putAll(fields); // GH-90000
        return entity;
    }

    /**
     * Build just the data fields (for create requests). // GH-90000
     *
     * @return fields map without metadata
     */
    public Map<String, Object> buildFields() { // GH-90000
        return new HashMap<>(fields); // GH-90000
    }

    /**
     * Get the entity ID that will be used.
     *
     * @return entity ID
     */
    public String getId() { // GH-90000
        return id;
    }

    /**
     * Get the collection name.
     *
     * @return collection name
     */
    public String getCollection() { // GH-90000
        return collection;
    }

    // Common entity templates

    /**
     * Create a product entity with standard fields.
     *
     * @return product entity builder
     */
    public static EntityBuilder product() { // GH-90000
        return EntityBuilder.create("products")
            .withField("name", "Standard Product") // GH-90000
            .withField("sku", "SKU-001") // GH-90000
            .withField("price", 99.99) // GH-90000
            .withField("quantity", 50) // GH-90000
            .withField("category", "general"); // GH-90000
    }

    /**
     * Create a customer entity with standard fields.
     *
     * @return customer entity builder
     */
    public static EntityBuilder customer() { // GH-90000
        return EntityBuilder.create("customers")
            .withField("name", "John Doe") // GH-90000
            .withField("email", "john@example.com") // GH-90000
            .withField("phone", "+1-555-0100") // GH-90000
            .withField("status", "active"); // GH-90000
    }

    /**
     * Create an order entity with standard fields.
     *
     * @return order entity builder
     */
    public static EntityBuilder order() { // GH-90000
        return EntityBuilder.create("orders")
            .withField("customerId", "cust-001") // GH-90000
            .withField("status", "pending") // GH-90000
            .withField("total", 199.98) // GH-90000
            .withField("items", java.util.List.of( // GH-90000
                Map.of("productId", "prod-001", "qty", 2, "price", 99.99) // GH-90000
            ));
    }
}
