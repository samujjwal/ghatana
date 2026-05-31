package com.ghatana.datacloud.operations;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Records and queries Data Cloud operation lifecycle events.
 *
 * @doc.type interface
 * @doc.purpose Shared operation recording port for Data Cloud runtime surfaces
 * @doc.layer product
 * @doc.pattern Port
 */
public interface OperationRecorder {

    OperationRecord record(OperationRecord record);

    Optional<OperationRecord> find(String tenantId, String operationId);

    List<OperationRecord> listRecent(String tenantId, int limit);

    OperationRecord transition(String tenantId, String operationId, OperationStatus status, String detail, Map<String, Object> metadata);

    default OperationRecord start(
            String tenantId,
            OperationKind kind,
            String resourceType,
            String resourceId,
            String action,
            String summary,
            String actorId,
            String correlationId,
            boolean cancellable,
            Map<String, Object> metadata) {
        return record(OperationRecord.create(
                tenantId,
                kind,
                OperationStatus.RUNNING,
                resourceType,
                resourceId,
                action,
                summary,
                actorId,
                correlationId,
                cancellable,
                metadata));
    }
}
