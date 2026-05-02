/*
 * Copyright (c) 2026 Ghatana Inc. 
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
 * All values are deterministic (not random) to ensure reproducible tests. 
 *
 * <p><strong>Example:</strong>
 * <pre>
 * {@code
 * Map<String, Object> entity = EntityBuilder.create("products")
 *     .withId("prod-001")
 *     .withField("name", "Widget") 
 *     .withField("price", 19.99) 
 *     .withField("quantity", 100) 
 *     .withTenant("tenant-alpha")
 *     .withVersion(1) 
 *     .build(); 
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
    private final Map<String, Object> fields = new HashMap<>(); 
    private String tenantId = "tenant-default";
    private int version = 1;
    private Instant createdAt;
    private Instant updatedAt;

    private EntityBuilder(String collection) { 
        this.collection = collection;
        this.id = UUID.randomUUID().toString(); 
        this.createdAt = Instant.parse("2026-01-01T00:00:00Z");
        this.updatedAt = createdAt;
    }

    /**
     * Start building an entity for the specified collection.
     *
     * @param collection collection name
     * @return new builder instance
     */
    public static EntityBuilder create(String collection) { 
        return new EntityBuilder(collection); 
    }

    /**
     * Set entity ID.
     *
     * @param id entity ID
     * @return this builder
     */
    public EntityBuilder withId(String id) { 
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
    public EntityBuilder withField(String name, Object value) { 
        this.fields.put(name, value); 
        return this;
    }

    /**
     * Add multiple fields at once.
     *
     * @param fields map of field names to values
     * @return this builder
     */
    public EntityBuilder withFields(Map<String, Object> fields) { 
        this.fields.putAll(fields); 
        return this;
    }

    /**
     * Set tenant ID.
     *
     * @param tenantId tenant identifier
     * @return this builder
     */
    public EntityBuilder withTenant(String tenantId) { 
        this.tenantId = tenantId;
        return this;
    }

    /**
     * Set entity version for optimistic locking.
     *
     * @param version version number
     * @return this builder
     */
    public EntityBuilder withVersion(int version) { 
        this.version = version;
        return this;
    }

    /**
     * Set creation timestamp.
     *
     * @param createdAt creation instant
     * @return this builder
     */
    public EntityBuilder withCreatedAt(Instant createdAt) { 
        this.createdAt = createdAt;
        return this;
    }

    /**
     * Set update timestamp.
     *
     * @param updatedAt update instant
     * @return this builder
     */
    public EntityBuilder withUpdatedAt(Instant updatedAt) { 
        this.updatedAt = updatedAt;
        return this;
    }

    /**
     * Build the entity as a Map.
     *
     * @return entity as Map<String, Object>
     */
    public Map<String, Object> build() { 
        Map<String, Object> entity = new HashMap<>(); 
        entity.put("id", id); 
        entity.put("collection", collection); 
        entity.put("tenantId", tenantId); 
        entity.put("version", version); 
        entity.put("createdAt", createdAt.toString()); 
        entity.put("updatedAt", updatedAt.toString()); 
        entity.putAll(fields); 
        return entity;
    }

    /**
     * Build just the data fields (for create requests). 
     *
     * @return fields map without metadata
     */
    public Map<String, Object> buildFields() { 
        return new HashMap<>(fields); 
    }

    /**
     * Get the entity ID that will be used.
     *
     * @return entity ID
     */
    public String getId() { 
        return id;
    }

    /**
     * Get the collection name.
     *
     * @return collection name
     */
    public String getCollection() { 
        return collection;
    }

    // Common entity templates

    /**
     * Create a product entity with standard fields.
     *
     * @return product entity builder
     */
    public static EntityBuilder product() { 
        return EntityBuilder.create("products")
            .withField("name", "Standard Product") 
            .withField("sku", "SKU-001") 
            .withField("price", 99.99) 
            .withField("quantity", 50) 
            .withField("category", "general"); 
    }

    /**
     * Create a customer entity with standard fields.
     *
     * @return customer entity builder
     */
    public static EntityBuilder customer() { 
        return EntityBuilder.create("customers")
            .withField("name", "John Doe") 
            .withField("email", "john@example.com") 
            .withField("phone", "+1-555-0100") 
            .withField("status", "active"); 
    }

    /**
     * Create an order entity with standard fields.
     *
     * @return order entity builder
     */
    public static EntityBuilder order() { 
        return EntityBuilder.create("orders")
            .withField("customerId", "cust-001") 
            .withField("status", "pending") 
            .withField("total", 199.98) 
            .withField("items", java.util.List.of( 
                Map.of("productId", "prod-001", "qty", 2, "price", 99.99) 
            ));
    }
}
