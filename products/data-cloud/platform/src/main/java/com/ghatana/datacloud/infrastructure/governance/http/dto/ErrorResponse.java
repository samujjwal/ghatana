package com.ghatana.datacloud.infrastructure.governance.http.dto;

import java.util.Objects;
import java.util.List;
import java.util.ArrayList;

/**
 * HTTP response DTO for error responses.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates error information for HTTP error responses. Provides
 * consistent error format across all governance endpoints.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ErrorResponse error = ErrorResponse.builder()
 *     .status(400)
 *     .error("VALIDATION_ERROR")
 *     .message("Role name is required")
 *     .timestamp(System.currentTimeMillis())
 *     .addDetail("field", "roleName", "error", "cannot be blank")
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose HTTP error response DTO
 * @doc.layer infrastructure
 * @doc.pattern Data Transfer Object (DTO)
 */
public final class ErrorResponse {
    private final int status;
    private final String error;
    private final String message;
    private final long timestamp;
    private final List<ErrorDetail> details;
    private final String path;

    private ErrorResponse(
            int status,
            String error,
            String message,
            long timestamp,
            List<ErrorDetail> details,
            String path) {
        this.status = status;
        this.error = Objects.requireNonNull(error, "error cannot be null");
        this.message = Objects.requireNonNull(message, "message cannot be null");
        this.timestamp = timestamp;
        this.details = new ArrayList<>(details != null ? details : List.of());
        this.path = path != null ? path : "";
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public List<ErrorDetail> getDetails() {
        return new ArrayList<>(details);
    }

    public String getPath() {
        return path;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Error detail object for validation errors.
     */
    public static final class ErrorDetail {
        private final String field;
        private final String code;
        private final String message;

        public ErrorDetail(String field, String code, String message) {
            this.field = Objects.requireNonNull(field);
            this.code = Objects.requireNonNull(code);
            this.message = Objects.requireNonNull(message);
        }

        public String getField() {
            return field;
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return "ErrorDetail{" +
                    "field='" + field + '\'' +
                    ", code='" + code + '\'' +
                    '}';
        }
    }

    public static class Builder {
        private int status = 500;
        private String error = "INTERNAL_SERVER_ERROR";
        private String message = "Internal server error";
        private long timestamp = System.currentTimeMillis();
        private List<ErrorDetail> details = new ArrayList<>();
        private String path = "";

        public Builder status(int status) {
            this.status = status;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder addDetail(String field, String code, String message) {
            this.details.add(new ErrorDetail(field, code, message));
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public ErrorResponse build() {
            return new ErrorResponse(status, error, message, timestamp, details, path);
        }
    }

    @Override
    public String toString() {
        return "ErrorResponse{" +
                "status=" + status +
                ", error='" + error + '\'' +
                ", detailCount=" + details.size() +
                '}';
    }
}
