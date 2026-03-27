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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DynamicEntity that)) return false;

        if (collectionId != null ? !collectionId.equals(that.collectionId) : that.collectionId != null) return false;
        if (tenantId != null ? !tenantId.equals(that.tenantId) : that.tenantId != null) return false;
        return data != null ? data.equals(that.data) : that.data == null;
    }

    @Override
    public int hashCode() {
        int result = collectionId != null ? collectionId.hashCode() : 0;
        result = 31 * result + (tenantId != null ? tenantId.hashCode() : 0);
        result = 31 * result + (data != null ? data.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DynamicEntity{" +
                "collectionId='" + collectionId + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", data=" + data +
                '}';
    }
}
