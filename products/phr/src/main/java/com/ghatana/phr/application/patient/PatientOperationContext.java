package com.ghatana.phr.application.patient;

import java.util.Objects;

/**
 * Request-scoped PHR application context.
 *
 * @doc.type record
 * @doc.purpose Carry tenant, workspace, user, patient, and correlation identity through PHR application services
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record PatientOperationContext(
        String tenantId,
        String workspaceId,
        String userId,
        String patientId,
        String correlationId
) {
    public PatientOperationContext {
        tenantId = requireNonBlank(tenantId, "tenantId");
        workspaceId = workspaceId == null || workspaceId.isBlank() ? "default" : workspaceId;
        userId = requireNonBlank(userId, "userId");
        patientId = patientId == null || patientId.isBlank() ? "unknown" : patientId;
        correlationId = correlationId == null || correlationId.isBlank() ? "none" : correlationId;
    }

    public PatientOperationContext(String tenantId, String userId, String patientId) {
        this(tenantId, "default", userId, patientId, "none");
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
