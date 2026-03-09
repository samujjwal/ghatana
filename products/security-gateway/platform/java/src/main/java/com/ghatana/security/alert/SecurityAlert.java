package com.ghatana.security.alert;

import com.ghatana.platform.domain.domain.Severity;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a security alert.
 
 *
 * @doc.type class
 * @doc.purpose Security alert
 * @doc.layer core
 * @doc.pattern Component
*/
public class SecurityAlert {
    public static final String TYPE_RATE_LIMIT = "RATE_LIMIT";
    public static final String TYPE_AUTH_FAILURE = "AUTH_FAILURE";
    public static final String TYPE_AUTH_SUCCESS = "AUTH_SUCCESS";
    public static final String TYPE_PERMISSION_DENIED = "PERMISSION_DENIED";
    public static final String TYPE_SUSPICIOUS_ACTIVITY = "SUSPICIOUS_ACTIVITY";
    public static final String TYPE_CONFIG_CHANGE = "CONFIG_CHANGE";
    
    private final String type;
    private final String message;
    private final String source;
    private final Instant timestamp;
    private final Map<String, Object> details;
    private Severity severity;
    private String status;

    public SecurityAlert(String type, String message, String source) {
        this.type = type;
        this.message = message;
        this.source = source;
        this.timestamp = Instant.now();
        this.details = new HashMap<>();
        this.severity = Severity.MEDIUM;
        this.status = "OPEN";
    }

    public String getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public String getSource() {
        return source;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Map<String, Object> getDetails() {
        return new HashMap<>(details);
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public SecurityAlert withDetail(String key, Object value) {
        details.put(key, value);
        return this;
    }

    public SecurityAlert withSeverity(Severity severity) {
        this.severity = severity;
        return this;
    }

    public SecurityAlert withStatus(String status) {
        this.status = status;
        return this;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "Alert{" +
                "type='" + type + '\'' +
                ", message='" + message + '\'' +
                ", source='" + source + '\'' +
                ", timestamp=" + timestamp +
                ", severity='" + severity + '\'' +
                ", status='" + status + '\'' +
                ", details=" + details +
                '}';
    }

    public static class Builder {
        private String type;
        private String message;
        private String source;
        private Severity severity = Severity.MEDIUM;
        private String status = "OPEN";
        private final Map<String, Object> details = new HashMap<>();

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder severity(Severity severity) {
            this.severity = severity;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder detail(String key, Object value) {
            this.details.put(key, value);
            return this;
        }

        public SecurityAlert build() {
            SecurityAlert alert = new SecurityAlert(type, message, source);
            alert.setSeverity(severity);
            alert.setStatus(status);
            details.forEach(alert::withDetail);
            return alert;
        }
    }
}
