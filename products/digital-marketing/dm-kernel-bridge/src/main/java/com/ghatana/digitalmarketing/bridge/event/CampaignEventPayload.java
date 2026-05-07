package com.ghatana.digitalmarketing.bridge.event;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * P0-010: Typed event payload DTOs for campaign events.
 * 
 * <p>Replaces manual JSON string building with Jackson-serialized DTOs.
 * This ensures proper JSON escaping, schema validation, and type safety.
 * All event payloads are versioned and serializable/deserializable.
 *
 * @doc.type interface
 * @doc.purpose Typed event payload DTOs for campaign lifecycle events
 * @doc.layer product
 * @doc.pattern Data Transfer Object
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface CampaignEventPayload permits BaseCampaignEvent, CampaignCreated, CampaignLaunched, CampaignPaused, CampaignCompleted, CampaignArchived, CampaignRolledBack {
}

