package com.ghatana.digitalmarketing.bridge;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import com.ghatana.digitalmarketing.domain.campaign.CampaignStatus;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * KERNEL-P2-1: Event-sourced campaign aggregate adapter.
 *
 * <p>Publishes DMOS campaign lifecycle events to the platform {@link EventLogStore},
 * enabling full event-sourced reconstruction of campaign state and audit history.
 * Consumers (reporting, audit, replication) subscribe via the store's {@code tail()} API.
 *
 * <p>Event types follow the {@code dmos.campaign.*} namespace and use JSON payloads.
 * All appends carry the DMOS idempotency key so that retries are safe.
 *
 * @doc.type class
 * @doc.purpose Event-sourced campaign aggregate — publishes campaign domain events to the platform event store
 * @doc.layer product
 * @doc.pattern Adapter, Event Sourcing
 */
public final class CampaignEventSourcingAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(CampaignEventSourcingAdapter.class);

    // ─── Canonical event type names ───────────────────────────────────────────
    public static final String EVT_CAMPAIGN_CREATED  = "dmos.campaign.created";
    public static final String EVT_CAMPAIGN_LAUNCHED = "dmos.campaign.launched";
    public static final String EVT_CAMPAIGN_PAUSED   = "dmos.campaign.paused";
    public static final String EVT_CAMPAIGN_COMPLETED = "dmos.campaign.completed";
    public static final String EVT_CAMPAIGN_ARCHIVED  = "dmos.campaign.archived";
    public static final String EVT_CAMPAIGN_ROLLED_BACK = "dmos.campaign.rolledBack";

    static final String EVENT_VERSION = "1.0.0";

    private final EventLogStore eventStore;

    /**
     * @param eventStore platform event log store (append-only, tenant-scoped)
     */
    public CampaignEventSourcingAdapter(EventLogStore eventStore) {
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore required");
    }

    /**
     * Records that a new campaign was created.
     *
     * @param ctx      operation context (tenant, correlation ID, idempotency key)
     * @param campaign the newly-created campaign
     * @return offset of the appended event
     */
    public Promise<com.ghatana.platform.types.identity.Offset> publishCreated(
            DmOperationContext ctx, Campaign campaign) {
        return publish(ctx, campaign, EVT_CAMPAIGN_CREATED);
    }

    /**
     * Records that a campaign transitioned to {@link CampaignStatus#LAUNCHED}.
     */
    public Promise<com.ghatana.platform.types.identity.Offset> publishLaunched(
            DmOperationContext ctx, Campaign campaign) {
        return publish(ctx, campaign, EVT_CAMPAIGN_LAUNCHED);
    }

    /**
     * Records that a campaign transitioned to {@link CampaignStatus#PAUSED}.
     */
    public Promise<com.ghatana.platform.types.identity.Offset> publishPaused(
            DmOperationContext ctx, Campaign campaign) {
        return publish(ctx, campaign, EVT_CAMPAIGN_PAUSED);
    }

    /**
     * Records that a campaign transitioned to {@link CampaignStatus#COMPLETED}.
     */
    public Promise<com.ghatana.platform.types.identity.Offset> publishCompleted(
            DmOperationContext ctx, Campaign campaign) {
        return publish(ctx, campaign, EVT_CAMPAIGN_COMPLETED);
    }

    /**
     * Records that a campaign was archived.
     */
    public Promise<com.ghatana.platform.types.identity.Offset> publishArchived(
            DmOperationContext ctx, Campaign campaign) {
        return publish(ctx, campaign, EVT_CAMPAIGN_ARCHIVED);
    }

    /**
     * Records that a campaign was rolled back to a previous status.
     *
     * @param previousStatus the status before rollback
     */
    public Promise<com.ghatana.platform.types.identity.Offset> publishRolledBack(
            DmOperationContext ctx, Campaign campaign, CampaignStatus previousStatus) {
        String payload = buildPayload(campaign, Map.of("previousStatus", previousStatus.name()));
        return appendEntry(ctx, campaign.getId(), EVT_CAMPAIGN_ROLLED_BACK, payload);
    }

    /**
     * Replays all recorded events for a campaign aggregate (ordered by offset).
     *
     * @param tenantId   tenant identifier
     * @param campaignId the campaign's aggregate ID
     * @return ordered list of event entries for this campaign
     */
    public Promise<List<EventLogStore.EventEntry>> replay(String tenantId, String campaignId) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(campaignId, "campaignId required");
        TenantContext tenant = TenantContext.of(tenantId);
        return eventStore.readByType(
                tenant,
                EVT_CAMPAIGN_CREATED,
                null,
                Integer.MAX_VALUE
        ).then(created -> {
            // Read all lifecycle events for this campaign by filtering on campaignId header
            return eventStore.readByTimeRange(
                    tenant,
                    Instant.EPOCH,
                    Instant.now(),
                    10_000
            );
        }).map(entries -> entries.stream()
                .filter(e -> campaignId.equals(e.headers().get("campaignId")))
                .toList());
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private Promise<com.ghatana.platform.types.identity.Offset> publish(
            DmOperationContext ctx, Campaign campaign, String eventType) {
        String payload = buildPayload(campaign, Map.of());
        return appendEntry(ctx, campaign.getId(), eventType, payload);
    }

    private Promise<com.ghatana.platform.types.identity.Offset> appendEntry(
            DmOperationContext ctx,
            String campaignId,
            String eventType,
            String jsonPayload) {

        TenantContext tenant = TenantContext.of(
                ctx.getTenantId().getValue(),
                ctx.getWorkspaceId().getValue()
        );

        Map<String, String> headers = new HashMap<>();
        headers.put("campaignId", campaignId);
        headers.put("correlationId", ctx.getCorrelationId().getValue());
        headers.put("actor", ctx.getActor().getPrincipalId());

        EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
                .eventType(eventType)
                .eventVersion(EVENT_VERSION)
                .payload(jsonPayload.getBytes(StandardCharsets.UTF_8))
                .contentType("application/json")
                .headers(headers)
                .idempotencyKey(ctx.getIdempotencyKey().getValue() + ":" + eventType)
                .build();

        return eventStore.append(tenant, entry)
                .whenResult(offset -> LOG.info(
                        "[DMOS][ES] Campaign event appended: type={} campaignId={} offset={} tenantId={}",
                        eventType, campaignId, offset, ctx.getTenantId().getValue()))
                .whenException(ex -> LOG.error(
                        "[DMOS][ES] Failed to append campaign event: type={} campaignId={} error={}",
                        eventType, campaignId, ex.getMessage(), ex));
    }

    private static String buildPayload(Campaign campaign, Map<String, String> extra) {
        StringBuilder json = new StringBuilder("{");
        json.append("\"campaignId\":\"").append(campaign.getId()).append("\",");
        json.append("\"workspaceId\":\"").append(campaign.getWorkspaceId().getValue()).append("\",");
        json.append("\"name\":\"").append(escapeJson(campaign.getName())).append("\",");
        json.append("\"status\":\"").append(campaign.getStatus().name()).append("\",");
        json.append("\"type\":\"").append(campaign.getType().name()).append("\",");
        json.append("\"createdBy\":\"").append(escapeJson(campaign.getCreatedBy())).append("\",");
        json.append("\"createdAt\":\"").append(campaign.getCreatedAt()).append("\",");
        json.append("\"updatedAt\":\"").append(campaign.getUpdatedAt()).append("\"");
        extra.forEach((k, v) -> json.append(",\"").append(k).append("\":\"").append(escapeJson(v)).append("\""));
        json.append("}");
        return json.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
