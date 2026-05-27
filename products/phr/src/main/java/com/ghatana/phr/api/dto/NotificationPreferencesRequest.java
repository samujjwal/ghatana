package com.ghatana.phr.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for notification preferences update.
 *
 * @doc.type class
 * @doc.purpose Request DTO for notification preferences with validation
 * @doc.layer product
 * @doc.pattern DTO
 */
public class NotificationPreferencesRequest {

    @NotNull(message = "emailEnabled is required")
    private boolean emailEnabled;

    @NotNull(message = "smsEnabled is required")
    private boolean smsEnabled;

    @NotNull(message = "inAppEnabled is required")
    private boolean inAppEnabled;

    @JsonProperty("emailEnabled")
    public boolean isEmailEnabled() {
        return emailEnabled;
    }

    public void setEmailEnabled(boolean emailEnabled) {
        this.emailEnabled = emailEnabled;
    }

    @JsonProperty("smsEnabled")
    public boolean isSmsEnabled() {
        return smsEnabled;
    }

    public void setSmsEnabled(boolean smsEnabled) {
        this.smsEnabled = smsEnabled;
    }

    @JsonProperty("inAppEnabled")
    public boolean isInAppEnabled() {
        return inAppEnabled;
    }

    public void setInAppEnabled(boolean inAppEnabled) {
        this.inAppEnabled = inAppEnabled;
    }
}
