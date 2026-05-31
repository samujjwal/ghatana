package com.ghatana.datacloud.operations;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Process-local operation recorder used by the standalone launcher.
 *
 * <p>The response contract discloses this as {@code volatile} storage so
 * operators do not mistake embedded/runtime-local records for durable audit
 * evidence. Durable promotion can replace this port without changing callers.
 *
 * @doc.type class
 * @doc.purpose Runtime-local operation recorder for embedded Data Cloud launcher
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class InMemoryOperationRecorder implements OperationRecorder {

    private final ConcurrentMap<String, OperationRecord> records = new ConcurrentHashMap<>();

    @Override
    public OperationRecord record(OperationRecord record) {
        records.put(scopedKey(record.tenantId(), record.operationId()), record);
        return record;
    }

    @Override
    public Optional<OperationRecord> find(String tenantId, String operationId) {
        return Optional.ofNullable(records.get(scopedKey(tenantId, operationId)));
    }

    @Override
    public List<OperationRecord> listRecent(String tenantId, int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, 500));
        return records.values().stream()
                .filter(record -> record.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(OperationRecord::createdAt).reversed())
                .limit(normalizedLimit)
                .toList();
    }

    @Override
    public OperationRecord transition(String tenantId, String operationId, OperationStatus status, String detail, Map<String, Object> metadata) {
        String key = scopedKey(tenantId, operationId);
        return records.compute(key, (ignored, existing) -> {
            if (existing == null) {
                throw new IllegalArgumentException("Operation not found: " + operationId);
            }
            return existing.transition(status, detail, metadata);
        });
    }

    public List<OperationRecord> allRecordsSnapshot() {
        return new ArrayList<>(records.values());
    }

    private static String scopedKey(String tenantId, String operationId) {
        return tenantId + ":" + operationId;
    }
}
