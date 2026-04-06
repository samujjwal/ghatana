package com.ghatana.virtualorg.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SLA tracking behavior shared by task and interaction configuration.
 *
 * @doc.type record
 * @doc.purpose Shared SLA tracking configuration for Virtual-Org workflows
 * @doc.layer core
 * @doc.pattern Value Object
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SlaTrackingConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("excludeWeekends")
        Boolean excludeWeekends,
        @JsonProperty("businessHours")
        BusinessHoursConfig businessHours,
        @JsonProperty("pauseOnBlocked")
        Boolean pauseOnBlocked
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public Boolean pauseOnBlocked() {
        return pauseOnBlocked != null ? pauseOnBlocked : true;
    }
}