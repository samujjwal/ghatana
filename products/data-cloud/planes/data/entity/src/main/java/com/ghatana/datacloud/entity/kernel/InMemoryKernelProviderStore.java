package com.ghatana.datacloud.entity.kernel;

import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of KernelProviderStorePort for testing.
 *
 * <p><b>Purpose</b><br>
 * Provides a volatile in-memory store for Kernel provider records. Intended for
 * testing and development. Production should use JpaKernelProviderStore or
 * other durable implementation.
 *
 * <p><b>Thread Safety</b><br>
 * Uses ConcurrentHashMap for thread-safe operations.
 *
 * <p><b>Limitations</b><br>
 * - Data is lost on process restart
 * - No persistence guarantees
 * - Not suitable for production use
 *
 * @doc.type class
 * @doc.purpose In-memory Kernel provider store for testing
 * @doc.layer product
 * @doc.pattern In-Memory Store (Testing)
 */
public class InMemoryKernelProviderStore implements KernelProviderStorePort {

    private final ConcurrentHashMap<String, KernelProviderRecord> storage = new ConcurrentHashMap<>();

    @Override
    public Promise<KernelProviderRecord> save(KernelProviderRecord record) {
        storage.put(record.providerRef(), record);
        return Promise.of(record);
    }

    @Override
    public Promise<Optional<KernelProviderRecord>> findByRef(String tenantId, String providerRef) {
        KernelProviderRecord record = storage.get(providerRef);
        if (record == null) {
            return Promise.of(Optional.empty());
        }
        if (!record.tenantId().equals(tenantId)) {
            return Promise.of(Optional.empty());
        }
        if (record.isExpired()) {
            return Promise.of(Optional.empty());
        }
        return Promise.of(Optional.of(record));
    }

    @Override
    public Promise<Optional<KernelProviderRecord>> findLatestByProviderType(
        String tenantId,
        String providerType,
        Map<String, Object> filters
    ) {
        return listByProviderType(tenantId, providerType, filters, 100)
            .map(records -> records.stream()
                .max((a, b) -> a.createdAt().compareTo(b.createdAt()))
            );
    }

    @Override
    public Promise<List<KernelProviderRecord>> listByProviderType(
        String tenantId,
        String providerType,
        Map<String, Object> filters,
        int limit
    ) {
        List<KernelProviderRecord> result = new ArrayList<>();
        for (KernelProviderRecord record : storage.values()) {
            if (!record.tenantId().equals(tenantId)) {
                continue;
            }
            if (!record.providerType().equals(providerType)) {
                continue;
            }
            if (record.isExpired()) {
                continue;
            }
            if (matchesFilters(record.data(), filters)) {
                result.add(record);
                if (result.size() >= limit) {
                    break;
                }
            }
        }
        // Sort by creation time descending (newest first)
        result.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));
        return Promise.of(result);
    }

    @Override
    public Promise<Long> deleteExpired(String tenantId) {
        long count = 0;
        Instant now = Instant.now();
        for (KernelProviderRecord record : storage.values()) {
            if (record.tenantId().equals(tenantId) && record.isExpired()) {
                storage.remove(record.providerRef());
                count++;
            }
        }
        return Promise.of(count);
    }

    @Override
    public Promise<Boolean> deleteByRef(String tenantId, String providerRef) {
        KernelProviderRecord record = storage.get(providerRef);
        if (record == null) {
            return Promise.of(false);
        }
        if (!record.tenantId().equals(tenantId)) {
            return Promise.of(false);
        }
        storage.remove(providerRef);
        return Promise.of(true);
    }

    @Override
    public Promise<Long> countByProviderType(String tenantId, String providerType) {
        long count = 0;
        for (KernelProviderRecord record : storage.values()) {
            if (record.tenantId().equals(tenantId)
                && record.providerType().equals(providerType)
                && !record.isExpired()) {
                count++;
            }
        }
        return Promise.of(count);
    }

    /**
     * Clears all records (for testing only).
     */
    public void clear() {
        storage.clear();
    }

    /**
     * Checks if a record's data matches all filter criteria.
     */
    private boolean matchesFilters(Map<String, Object> data, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            Object value = data.get(entry.getKey());
            if (value == null || !value.equals(entry.getValue())) {
                return false;
            }
        }
        return true;
    }
}
