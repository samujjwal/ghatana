package com.ghatana.digitalmarketing.application.event;

import com.ghatana.digitalmarketing.domain.event.DmEventType;
import com.ghatana.digitalmarketing.domain.event.DmOutboxEntry;
import com.ghatana.digitalmarketing.domain.event.DmOutboxStatus;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DmEventDispatcher}.
 */
@DisplayName("DmEventDispatcher")
class DmEventDispatcherTest {

    @Test
    @DisplayName("noop dispatcher completes without error")
    void noopDispatcher_completesWithoutError() {
        DmEventDispatcher dispatcher = (entry) -> Promise.of(null);

        DmOutboxEntry entry = DmOutboxEntry.builder()
            .id("id-1")
            .eventId("event-1")
            .eventType(DmEventType.CAMPAIGN_CREATED)
            .tenantId("tenant-1")
            .workspaceId("ws-1")
            .correlationId("corr-1")
            .serializedPayload("{}")
            .status(DmOutboxStatus.PENDING)
            .createdAt(Instant.now())
            .scheduledAt(Instant.now())
            .build();

        Promise<Void> result = dispatcher.dispatch(entry);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("dispatcher can be created as lambda")
    void dispatcher_canBeCreatedAsLambda() {
        DmEventDispatcher dispatcher = entry -> Promise.of(null);
        assertThat(dispatcher).isNotNull();
    }
}
