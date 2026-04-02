package com.ghatana.datacloud.client;

import io.activej.promise.Promise;
import java.util.Optional;

/**
 * DataCloud client for entity persistence and queries.
 *
 * @doc.type class
 * @doc.purpose DataCloud HTTP client
 * @doc.layer product
 * @doc.pattern Client
 */
public class DataCloudClient {

    public DataCloudClient() {
        // Stub
    }

    public Promise<Optional<?>> findById(String tenantId, String collection, String id) {
        return Promise.of(Optional.empty());
    }

    public Promise<?> save(String tenantId, String collection, Object data) {
        return Promise.of(data);
    }

    public Promise<Void> delete(String tenantId, String collection, String id) {
        return Promise.complete();
    }

    public Promise<?> query(String tenantId, String collection, Query query) {
        return Promise.of(java.util.Collections.emptyList());
    }

    public static class Query {
        private int limit;

        private Query(int limit) {
            this.limit = limit;
        }

        public static Query limit(int limit) {
            return new Query(limit);
        }

        public int getLimit() {
            return limit;
        }
    }

    public static class Entity {
        private String id;
        private String collection;
        private java.util.Map<String, Object> data;

        public Entity(String id, String collection, java.util.Map<String, Object> data) {
            this.id = id;
            this.collection = collection;
            this.data = data;
        }

        public String id() { return id; }
        public String collection() { return collection; }
        public java.util.Map<String, Object> data() { return data; }
    }
}
