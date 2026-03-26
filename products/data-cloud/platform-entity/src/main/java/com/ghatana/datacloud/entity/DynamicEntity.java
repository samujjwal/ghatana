package com.ghatana.datacloud.entity;

import java.util.Map;

/**
 * Simple dynamic entity wrapper used by bulk APIs.
 *
 * <p>
 * This class exists primarily to carry arbitrary entity data together with
 * tenant and collection context for bulk operations. It avoids coupling the
 * HTTP layer directly to the JPA-backed {@link Entity} type.
 *
 * @doc.type class
 * @doc.purpose Wrapper for dynamic entity data in bulk operations
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public class DynamicEntity {

    private final String collectionId;
    private final String tenantId;
    private final Map<String, Object> data;

    public DynamicEntity(String collectionId, String tenantId, Map<String, Object> data) {
        this.collectionId = collectionId;
        this.tenantId = tenantId;
        this.data = data;
    }

    public String getCollectionId() {
        return collectionId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
