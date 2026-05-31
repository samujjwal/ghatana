package com.ghatana.phr.hie;

import io.activej.promise.Promise;

import java.util.Map;
import java.util.Set;

/**
 * Kernel-facing contract for health information exchange integrations.
 *
 * @doc.type interface
 * @doc.purpose Defines plugin-backed HIE export, import, sync, and status operations without binding routes to a country-specific implementation
 * @doc.layer product
 * @doc.pattern Contract
 */
public interface HieIntegrationContract {

    String contractId();

    Set<Operation> supportedOperations();

    Promise<HieIntegrationResult> submit(HieIntegrationRequest request);

    Promise<HieIntegrationStatus> getStatus(String requestId, String correlationId);

    enum Operation {
        EXPORT,
        IMPORT,
        SYNC
    }

    record HieIntegrationRequest(
        Operation operation,
        String patientId,
        String correlationId,
        String requestedBy,
        String tenantId,
        String payloadFormat,
        Map<String, String> metadata
    ) {
        public HieIntegrationRequest {
            metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        }
    }

    record HieIntegrationResult(
        String requestId,
        Operation operation,
        String contractId,
        String status,
        boolean accepted,
        String safeReasonCode,
        String message
    ) {}

    record HieIntegrationStatus(
        String requestId,
        Operation operation,
        String contractId,
        String status,
        String safeReasonCode,
        String message
    ) {}
}
