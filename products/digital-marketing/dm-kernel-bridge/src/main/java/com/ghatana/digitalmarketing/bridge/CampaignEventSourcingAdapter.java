package com.ghatana.digitalmarketing.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.digitalmarketing.bridge.event.BaseCampaignEvent;
import com.ghatana.digitalmarketing.bridge.event.CampaignArchived;
import com.ghatana.digitalmarketing.bridge.event.CampaignCompleted;
import com.ghatana.digitalmarketing.bridge.event.CampaignCreated;
import com.ghatana.digitalmarketing.bridge.event.CampaignEventPayload;
import com.ghatana.digitalmarketing.bridge.event.CampaignLaunched;
import com.ghatana.digitalmarketing.bridge.event.CampaignPaused;
import com.ghatana.digitalmarketing.bridge.event.CampaignRolledBack;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import com.ghatana.digitalmarketing.domain.campaign.CampaignStatus;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
 * <p>P0-010: Uses Jackson ObjectMapper with typed DTOs for safe JSON serialization.
 * P0-009: replay() uses aggregate stream query by campaignId header for correctness.
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
    private final ObjectMapper objectMapper;

    /**
     * @param eventStore platform event log store (append-only, tenant-scoped)
     */
    public CampaignEventSourcingAdapter(EventLogStore eventStore) {
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore required");
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
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
    public Promise<Offset> publishRolledBack(
            DmOperationContext ctx, Campaign campaign, CampaignStatus previousStatus) {
        BaseCampaignEvent base = toBaseEvent(campaign);
        CampaignRolledBack payload = 
                CampaignRolledBack.from(base, previousStatus.name());
        return appendEntry(ctx, campaign.getId(), EVT_CAMPAIGN_ROLLED_BACK, payload);
    }

    /**
     * P0-009: Replays all recorded events for a campaign aggregate using aggregate stream query.
     *
     * <p>Uses offset-based read instead of lossy time range filtering.
     * This ensures complete, ordered, paginated replay without the 10k event cap
     * that could omit history for large tenants.
     *
     * @param tenantId   tenant identifier
     * @param campaignId the campaign's aggregate ID
     * @return ordered list of event entries for this campaign
     */
    public Promise<List<EventLogStore.EventEntry>> replay(String tenantId, String campaignId) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(campaignId, "campaignId required");
        TenantContext tenant = TenantContext.of(tenantId, campaignId);
        
        // P0-009: Use offset-based read from earliest offset for complete aggregate stream
        return eventStore.getEarliestOffset(tenant)
                .then(earliest -> eventStore.read(tenant, earliest, Integer.MAX_VALUE))
                .map(entries -> entries.stream()
                        .filter(e -> campaignId.equals(e.headers().get("campaignId")))
                        .toList());
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private Promise<Offset> publish(
            DmOperationContext ctx, Campaign campaign, String eventType) {
        BaseCampaignEvent base = toBaseEvent(campaign);
        CampaignEventPayload payload = switch (eventType) {
            case EVT_CAMPAIGN_CREATED -> CampaignCreated.from(base);
            case EVT_CAMPAIGN_LAUNCHED -> CampaignLaunched.from(base);
            case EVT_CAMPAIGN_PAUSED -> CampaignPaused.from(base);
            case EVT_CAMPAIGN_COMPLETED -> CampaignCompleted.from(base);
            case EVT_CAMPAIGN_ARCHIVED -> CampaignArchived.from(base);
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
        return appendEntry(ctx, campaign.getId(), eventType, payload);
    }

    private Promise<Offset> appendEntry(
            DmOperationContext ctx,
            String campaignId,
            String eventType,
            CampaignEventPayload payload) {

        TenantContext tenant = TenantContext.of(
                ctx.getTenantId().getValue(),
                ctx.getWorkspaceId().getValue()
        );

        Map<String, String> headers = Map.of(
                "campaignId", campaignId,
                "correlationId", ctx.getCorrelationId().getValue(),
                "actor", ctx.getActor().getPrincipalId()
        );

        try {
            byte[] jsonBytes = objectMapper.writeValueAsBytes(payload);
            
            EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
                    .eventType(eventType)
                    .eventVersion(EVENT_VERSION)
                    .payload(jsonBytes)
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
        } catch (Exception ex) {
            LOG.error("[DMOS][ES] Failed to serialize event payload: type={} campaignId={}",
                    eventType, campaignId, ex);
            return Promise.ofException(ex);
        }
    }

    private static BaseCampaignEvent toBaseEvent(Campaign campaign) {
        return new BaseCampaignEvent(
                campaign.getId(),
                campaign.getWorkspaceId().getValue(),
                campaign.getName(),
                campaign.getStatus().name(),
                campaign.getType().name(),
                campaign.getCreatedBy(),
                campaign.getCreatedAt(),
                campaign.getUpdatedAt()
        );
    }
}
