package com.ghatana.digitalmarketing.bridge;

import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import com.ghatana.digitalmarketing.domain.campaign.CampaignStatus;
import com.ghatana.digitalmarketing.domain.campaign.CampaignType;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.ghatana.digitalmarketing.bridge.CampaignEventSourcingAdapter.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CampaignEventSourcingAdapter} — KERNEL-P2-1.
 */
@DisplayName("CampaignEventSourcingAdapter")
class CampaignEventSourcingAdapterTest extends EventloopTestBase {

    private TestEventLogStore eventStore;
    private CampaignEventSourcingAdapter adapter;
    private DmOperationContext ctx;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        eventStore = new TestEventLogStore();
        adapter = new CampaignEventSourcingAdapter(eventStore);

        ctx = DmOperationContext.builder()
                .tenantId(DmTenantId.of("tenant-1"))
                .workspaceId(DmWorkspaceId.of("ws-1"))
                .actor(ActorRef.user("user-123"))
                .correlationId(DmCorrelationId.of("corr-1"))
                .idempotencyKey(DmIdempotencyKey.of("idem-1"))
                .build();

        campaign = Campaign.builder()
                .id("camp-001")
                .workspaceId(DmWorkspaceId.of("ws-1"))
                .name("Black Friday Campaign")
                .status(CampaignStatus.DRAFT)
                .type(CampaignType.EMAIL)
                .createdBy("user-123")
                .createdAt(Instant.parse("2025-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2025-01-01T00:00:00Z"))
                .build();
    }

    @Test
    @DisplayName("rejects null eventStore at construction time")
    void rejectsNullEventStore() {
        assertThatThrownBy(() -> new CampaignEventSourcingAdapter(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("eventStore");
    }

    @Test
    @DisplayName("publishCreated appends dmos.campaign.created event with correct headers")
    void publishCreatedAppendsCorrectEventType() {
        Offset offset = runPromise(() -> adapter.publishCreated(ctx, campaign));

        assertThat(offset).isEqualTo(Offset.of(1L));
        assertThat(eventStore.lastTenant).isNotNull();
        EventLogStore.EventEntry entry = eventStore.lastEntry;
        assertThat(entry.eventType()).isEqualTo(EVT_CAMPAIGN_CREATED);
        assertThat(entry.eventVersion()).isEqualTo(EVENT_VERSION);
        assertThat(entry.headers()).containsEntry("campaignId", "camp-001");
        assertThat(entry.headers()).containsEntry("correlationId", "corr-1");
        assertThat(entry.headers()).containsEntry("actor", "user-123");
    }

    @Test
    @DisplayName("publishCreated embeds campaign fields in JSON payload with typed DTO")
    void publishCreatedEmbedsPayload() {
        runPromise(() -> adapter.publishCreated(ctx, campaign));

        String payload = new String(eventStore.lastEntry.payload().array());
        assertThat(payload).contains("\"campaignId\":\"camp-001\"");
        assertThat(payload).contains("\"name\":\"Black Friday Campaign\"");
        assertThat(payload).contains("\"status\":\"DRAFT\"");
        // P0-010: Verify proper JSON structure from Jackson serialization
        assertThat(payload).contains("\"workspaceId\":\"ws-1\"");
        assertThat(payload).contains("\"type\":\"EMAIL\"");
    }

    @Test
    @DisplayName("publishLaunched uses dmos.campaign.launched event type")
    void publishLaunchedUsesCorrectType() {
        Campaign launched = campaign.launch();
        runPromise(() -> adapter.publishLaunched(ctx, launched));

        assertThat(eventStore.lastEntry.eventType()).isEqualTo(EVT_CAMPAIGN_LAUNCHED);
    }

    @Test
    @DisplayName("publishPaused uses dmos.campaign.paused event type")
    void publishPausedUsesCorrectType() {
        Campaign launched = campaign.launch();
        Campaign paused = launched.pause();
        runPromise(() -> adapter.publishPaused(ctx, paused));

        assertThat(eventStore.lastEntry.eventType()).isEqualTo(EVT_CAMPAIGN_PAUSED);
    }

    @Test
    @DisplayName("publishRolledBack includes previousStatus in payload")
    void publishRolledBackIncludesPreviousStatus() {
        runPromise(() -> adapter.publishRolledBack(ctx, campaign, CampaignStatus.LAUNCHED));

        EventLogStore.EventEntry entry = eventStore.lastEntry;
        assertThat(entry.eventType()).isEqualTo(EVT_CAMPAIGN_ROLLED_BACK);
        String payload = new String(entry.payload().array());
        assertThat(payload).contains("\"previousStatus\":\"LAUNCHED\"");
    }

    @Test
    @DisplayName("append passes tenant context with tenantId and workspaceId")
    void appendPassesTenantContext() {
        runPromise(() -> adapter.publishCreated(ctx, campaign));

        TenantContext tenant = eventStore.lastTenant;
        assertThat(tenant.tenantId()).isEqualTo("tenant-1");
        assertThat(tenant.workspaceId()).contains("ws-1");
    }

    @Test
    @DisplayName("replay returns entries matching campaignId header using offset-based read")
    void replayFiltersEntriesByCampaignId() {
        EventLogStore.EventEntry matchingEntry = EventLogStore.EventEntry.builder()
                .eventType(EVT_CAMPAIGN_CREATED)
                .payload("{}".getBytes())
                .headers(Map.of("campaignId", "camp-001"))
                .build();
        EventLogStore.EventEntry otherEntry = EventLogStore.EventEntry.builder()
                .eventType(EVT_CAMPAIGN_CREATED)
                .payload("{}".getBytes())
                .headers(Map.of("campaignId", "camp-999"))
                .build();
        eventStore.readResult = List.of(matchingEntry, otherEntry);

        List<EventLogStore.EventEntry> replayed = runPromise(() -> adapter.replay("tenant-1", "camp-001"));

        assertThat(replayed).hasSize(1);
        assertThat(replayed.get(0).headers()).containsEntry("campaignId", "camp-001");
    }

    @Test
    @DisplayName("P0-009: replay uses offset-based read without 10k event cap")
    void replayUsesOffsetBasedRead() {
        // Create more than 10k events to verify no cap
        List<EventLogStore.EventEntry> manyEntries = new java.util.ArrayList<>();
        for (int i = 0; i < 15_000; i++) {
            manyEntries.add(EventLogStore.EventEntry.builder()
                    .eventType(EVT_CAMPAIGN_CREATED)
                    .payload("{}".getBytes())
                    .headers(Map.of("campaignId", "camp-001"))
                    .build());
        }
        eventStore.readResult = manyEntries;

        List<EventLogStore.EventEntry> replayed = runPromise(() -> adapter.replay("tenant-1", "camp-001"));

        // P0-009: Should return all events, not capped at 10k
        assertThat(replayed).hasSize(15_000);
    }

    // ─── Test double ─────────────────────────────────────────────────────────

    private static final class TestEventLogStore implements EventLogStore {

        TenantContext lastTenant;
        EventLogStore.EventEntry lastEntry;
        List<EventLogStore.EventEntry> readResult = List.of();
        private long nextOffset = 1L;

        @Override
        public Promise<Offset> append(TenantContext tenant, EventLogStore.EventEntry entry) {
            this.lastTenant = tenant;
            this.lastEntry = entry;
            return Promise.of(Offset.of(nextOffset++));
        }

        @Override
        public Promise<List<Offset>> appendBatch(TenantContext tenant, List<EventLogStore.EventEntry> entries) {
            List<Offset> offsets = new ArrayList<>();
            for (EventLogStore.EventEntry e : entries) {
                lastTenant = tenant;
                lastEntry = e;
                offsets.add(Offset.of(nextOffset++));
            }
            return Promise.of(offsets);
        }

        @Override
        public Promise<List<EventLogStore.EventEntry>> read(TenantContext tenant, Offset from, int limit) {
            return Promise.of(readResult);
        }

        @Override
        public Promise<List<EventLogStore.EventEntry>> readByTimeRange(TenantContext tenant, Instant startTime, Instant endTime, int limit) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<List<EventLogStore.EventEntry>> readByType(TenantContext tenant, String eventType, Offset from, int limit) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<Offset> getLatestOffset(TenantContext tenant) {
            return Promise.of(Offset.of(nextOffset - 1));
        }

        @Override
        public Promise<Offset> getEarliestOffset(TenantContext tenant) {
            return Promise.of(Offset.zero());
        }

        @Override
        public Promise<EventLogStore.Subscription> tail(TenantContext tenant, Offset from, Consumer<EventLogStore.EventEntry> handler) {
            return Promise.of(new EventLogStore.Subscription() {
                private boolean cancelled = false;

                @Override
                public void cancel() {
                    cancelled = true;
                }

                @Override
                public boolean isCancelled() {
                    return cancelled;
                }

                @Override
                public void setErrorHandler(java.util.function.Consumer<Throwable> handler) {
                    // No-op for test
                }

                @Override
                public com.ghatana.platform.domain.eventstore.EventLogStore.SubscriptionState getState() {
                    return com.ghatana.platform.domain.eventstore.EventLogStore.SubscriptionState.ACTIVE;
                }
            });
        }
    }
}
