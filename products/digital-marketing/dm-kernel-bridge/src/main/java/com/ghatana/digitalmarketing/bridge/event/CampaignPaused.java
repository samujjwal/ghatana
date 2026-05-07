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
 * Payload for dmos.campaign.paused event.
 *
 * @doc.type record
 * @doc.purpose Campaign paused event payload
 * @doc.layer product
 * @doc.pattern Data Transfer Object
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CampaignPaused(
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
    public CampaignPaused {
        Objects.requireNonNull(campaignId, "campaignId required");
        Objects.requireNonNull(workspaceId, "workspaceId required");
        Objects.requireNonNull(name, "name required");
        Objects.requireNonNull(status, "status required");
        Objects.requireNonNull(type, "type required");
        Objects.requireNonNull(createdBy, "createdBy required");
        Objects.requireNonNull(createdAt, "createdAt required");
        Objects.requireNonNull(updatedAt, "updatedAt required");
    }

    public static CampaignPaused from(BaseCampaignEvent base) {
        return new CampaignPaused(
            base.campaignId(),
            base.workspaceId(),
            base.name(),
            base.status(),
            base.type(),
            base.createdBy(),
            base.createdAt(),
            base.updatedAt()
        );
    }
}
