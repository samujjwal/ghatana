package com.ghatana.platform.core.exception;

/**
 * Utility methods to map ErrorCode enums from other modules into the
 * canonical {@link ErrorCode} defined in this module.
 *
 * <p>This keeps migrations incremental: callers can translate foreign enums to
 * the canonical type without changing call sites all at once.
 *
 * @doc.type class
 * @doc.purpose Utility for mapping external error codes to canonical ErrorCode enumeration
 * @doc.layer core
 * @doc.pattern Utility, Adapter (enum translator)
 */
public final class ErrorCodeMappers {

    private ErrorCodeMappers() {
    }

    public static ErrorCode fromIngress(String ingressName) {
        if (ingressName == null) return ErrorCode.UNKNOWN_ERROR;
        switch (ingressName) {
            case "INVALID_REQUEST":
            case "VALIDATION_ERROR":
                return ErrorCode.VALIDATION_ERROR;
            case "UNAUTHORIZED":
                return ErrorCode.AUTHENTICATION_ERROR;
            case "FORBIDDEN":
                return ErrorCode.AUTHORIZATION_ERROR;
            case "NOT_FOUND":
                return ErrorCode.RESOURCE_NOT_FOUND;
            case "CONFLICT":
                return ErrorCode.RESOURCE_CONFLICT;
            case "RATE_LIMITED":
                return ErrorCode.SERVICE_UNAVAILABLE;
            case "INTERNAL_ERROR":
                return ErrorCode.SERVICE_ERROR;
            case "SERVICE_UNAVAILABLE":
                return ErrorCode.SERVICE_UNAVAILABLE;
            default:
                return ErrorCode.UNKNOWN_ERROR;
        }
    }

    public static ErrorCode fromDcmaar(String dcmaarName) {
        if (dcmaarName == null) return ErrorCode.UNKNOWN_ERROR;
        switch (dcmaarName) {
            case "UNKNOWN_ERROR":
                return ErrorCode.UNKNOWN_ERROR;
            case "INVALID_REQUEST":
            case "VALIDATION_ERROR":
                return ErrorCode.VALIDATION_ERROR;
            case "RESOURCE_NOT_FOUND":
                return ErrorCode.RESOURCE_NOT_FOUND;
            case "RESOURCE_ALREADY_EXISTS":
                return ErrorCode.RESOURCE_ALREADY_EXISTS;
            case "UNAUTHORIZED":
                return ErrorCode.AUTHENTICATION_ERROR;
            case "FORBIDDEN":
                return ErrorCode.AUTHORIZATION_ERROR;
            case "TIMEOUT":
                return ErrorCode.TIMEOUT_ERROR;
            case "CONNECTION_ERROR":
                return ErrorCode.CONNECTION_ERROR;
            case "DATABASE_ERROR":
                return ErrorCode.DATABASE_ERROR;
            case "SERVICE_UNAVAILABLE":
            case "RATE_LIMITED":
                return ErrorCode.SERVICE_UNAVAILABLE;
            default:
                return ErrorCode.UNKNOWN_ERROR;
        }
    }

    public static ErrorCode fromKg(String kgName) {
        if (kgName == null) return ErrorCode.UNKNOWN_ERROR;
        switch (kgName) {
            case "INVALID_REQUEST":
            case "VALIDATION_FAILED":
                return ErrorCode.VALIDATION_ERROR;
            case "RESOURCE_NOT_FOUND":
                return ErrorCode.RESOURCE_NOT_FOUND;
            case "DUPLICATE_RESOURCE":
                return ErrorCode.RESOURCE_ALREADY_EXISTS;
            case "UNAUTHORIZED":
                return ErrorCode.AUTHENTICATION_ERROR;
            case "FORBIDDEN":
                return ErrorCode.AUTHORIZATION_ERROR;
            case "INTERNAL_ERROR":
                return ErrorCode.SERVICE_ERROR;
            case "SERVICE_UNAVAILABLE":
                return ErrorCode.SERVICE_UNAVAILABLE;
            case "TIMEOUT_ERROR":
                return ErrorCode.TIMEOUT_ERROR;
            default:
                return ErrorCode.UNKNOWN_ERROR;
        }
    }
}
