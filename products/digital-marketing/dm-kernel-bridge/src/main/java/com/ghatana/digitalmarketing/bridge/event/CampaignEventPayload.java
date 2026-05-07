package com.ghatana.digitalmarketing.bridge.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.InstantDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializer;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * P0-010: Typed event payload DTOs for campaign events.
 * 
 * <p>Replaces manual JSON string building with Jackson-serialized DTOs.
 * This ensures proper JSON escaping, schema validation, and type safety.
 * All event payloads are versioned and serializable/deserializable.
 *
 * @doc.type class
 * @doc.purpose Typed event payload DTOs for campaign lifecycle events
 * @doc.layer product
 * @doc.pattern Data Transfer Object
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed class CampaignEventPayload {

    private CampaignEventPayload() {}

    /**
     * Base campaign event payload with common fields.
     */
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
    ) {
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

    /**
     * Payload for dmos.campaign.created event.
     */
    public record CampaignCreated(
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
    ) extends CampaignEventPayload {
        public CampaignCreated {
            Objects.requireNonNull(campaignId, "campaignId required");
            Objects.requireNonNull(workspaceId, "workspaceId required");
            Objects.requireNonNull(name, "name required");
            Objects.requireNonNull(status, "status required");
            Objects.requireNonNull(type, "type required");
            Objects.requireNonNull(createdBy, "createdBy required");
            Objects.requireNonNull(createdAt, "createdAt required");
            Objects.requireNonNull(updatedAt, "updatedAt required");
        }

        public static CampaignCreated from(BaseCampaignEvent base) {
            return new CampaignCreated(
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

    /**
     * Payload for dmos.campaign.launched event.
     */
    public record CampaignLaunched(
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
    ) extends CampaignEventPayload {
        public CampaignLaunched {
            Objects.requireNonNull(campaignId, "campaignId required");
            Objects.requireNonNull(workspaceId, "workspaceId required");
            Objects.requireNonNull(name, "name required");
            Objects.requireNonNull(status, "status required");
            Objects.requireNonNull(type, "type required");
            Objects.requireNonNull(createdBy, "createdBy required");
            Objects.requireNonNull(createdAt, "createdAt required");
            Objects.requireNonNull(updatedAt, "updatedAt required");
        }

        public static CampaignLaunched from(BaseCampaignEvent base) {
            return new CampaignLaunched(
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

    /**
     * Payload for dmos.campaign.paused event.
     */
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
    ) extends CampaignEventPayload {
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

    /**
     * Payload for dmos.campaign.completed event.
     */
    public record CampaignCompleted(
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
    ) extends CampaignEventPayload {
        public CampaignCompleted {
            Objects.requireNonNull(campaignId, "campaignId required");
            Objects.requireNonNull(workspaceId, "workspaceId required");
            Objects.requireNonNull(name, "name required");
            Objects.requireNonNull(status, "status required");
            Objects.requireNonNull(type, "type required");
            Objects.requireNonNull(createdBy, "createdBy required");
            Objects.requireNonNull(createdAt, "createdAt required");
            Objects.requireNonNull(updatedAt, "updatedAt required");
        }

        public static CampaignCompleted from(BaseCampaignEvent base) {
            return new CampaignCompleted(
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

    /**
     * Payload for dmos.campaign.archived event.
     */
    public record CampaignArchived(
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
    ) extends CampaignEventPayload {
        public CampaignArchived {
            Objects.requireNonNull(campaignId, "campaignId required");
            Objects.requireNonNull(workspaceId, "workspaceId required");
            Objects.requireNonNull(name, "name required");
            Objects.requireNonNull(status, "status required");
            Objects.requireNonNull(type, "type required");
            Objects.requireNonNull(createdBy, "createdBy required");
            Objects.requireNonNull(createdAt, "createdAt required");
            Objects.requireNonNull(updatedAt, "updatedAt required");
        }

        public static CampaignArchived from(BaseCampaignEvent base) {
            return new CampaignArchived(
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

    /**
     * Payload for dmos.campaign.rolledBack event.
     */
    public record CampaignRolledBack(
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
        Instant updatedAt,
        @JsonProperty("previousStatus") String previousStatus
    ) extends CampaignEventPayload {
        public CampaignRolledBack {
            Objects.requireNonNull(campaignId, "campaignId required");
            Objects.requireNonNull(workspaceId, "workspaceId required");
            Objects.requireNonNull(name, "name required");
            Objects.requireNonNull(status, "status required");
            Objects.requireNonNull(type, "type required");
            Objects.requireNonNull(createdBy, "createdBy required");
            Objects.requireNonNull(createdAt, "createdAt required");
            Objects.requireNonNull(updatedAt, "updatedAt required");
            Objects.requireNonNull(previousStatus, "previousStatus required");
        }

        public static CampaignRolledBack from(BaseCampaignEvent base, String previousStatus) {
            return new CampaignRolledBack(
                base.campaignId(),
                base.workspaceId(),
                base.name(),
                base.status(),
                base.type(),
                base.createdBy(),
                base.createdAt(),
                base.updatedAt(),
                previousStatus
            );
        }
    }
}
