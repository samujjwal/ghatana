package com.ghatana.digitalmarketing.bridge.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.InstantDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializer;

import java.time.Instant;
import java.util.Objects;

/**
 * Base campaign event payload with common fields.
 *
 * @doc.type record
 * @doc.purpose Base campaign event payload with common fields
 * @doc.layer product
 * @doc.pattern Data Transfer Object
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BaseCampaignEvent(
    @JsonProperty("campaignId") String campaignId,
    @JsonProperty("workspaceId") String workspaceId,
    @JsonProperty("name") String name,
    @JsonProperty("status") String status,
    @JsonProperty("type") String type,
    @JsonProperty("createdBy") String createdBy,
    @JsonProperty("createdAt")
    @JsonSerialize(using = InstantSerializer.class)
    @JsonDeserialize(using = InstantDeserializer.class)
    Instant createdAt,
    @JsonProperty("updatedAt")
    @JsonSerialize(using = InstantSerializer.class)
    @JsonDeserialize(using = InstantDeserializer.class)
    Instant updatedAt
) implements CampaignEventPayload {
    public BaseCampaignEvent {
        Objects.requireNonNull(campaignId, "campaignId required");
        Objects.requireNonNull(workspaceId, "workspaceId required");
        Objects.requireNonNull(name, "name required");
        Objects.requireNonNull(status, "status required");
        Objects.requireNonNull(type, "type required");
        Objects.requireNonNull(createdBy, "createdBy required");
        Objects.requireNonNull(createdAt, "createdAt required");
        Objects.requireNonNull(updatedAt, "updatedAt required");
    }
}
