package com.ghatana.digitalmarketing.application.command;

import com.ghatana.digitalmarketing.domain.DmosCommandHandlerNotFoundException;
import com.ghatana.digitalmarketing.domain.command.DmCommand;
import com.ghatana.digitalmarketing.domain.command.DmCommandStatus;
import com.ghatana.digitalmarketing.domain.command.DmCommandType;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DmCommandDispatcher}.
 *
 * @doc.type class
 * @doc.purpose Verifies command routing in DmCommandDispatcher (DMOS-P1-007)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DmCommandDispatcher")
class DmCommandDispatcherTest extends EventloopTestBase {

    private static DmCommand buildCommand(DmCommandType type) {
        return DmCommand.builder()
            .id("cmd-1")
            .commandType(type)
            .tenantId("tenant-1")
            .workspaceId("ws-1")
            .correlationId("corr-1")
            .issuedBy("user-1")
            .serializedPayload("{}")
            .status(DmCommandStatus.PENDING)
            .attemptCount(0)
            .createdAt(Instant.now())
            .scheduledAt(Instant.now())
            .build();
    }

    @Test
    @DisplayName("dispatch — routes to registered handler and returns success")
    void dispatch_routesToHandler() {
        boolean[] called = {false};
        DmCommandHandler handler = cmd -> {
            called[0] = true;
            return Promise.of(null);
        };

        DmCommandDispatcher dispatcher = new DmCommandDispatcher(
            Map.of(DmCommandType.CAMPAIGN_CREATE, handler));

        runPromise(() -> dispatcher.dispatch(buildCommand(DmCommandType.CAMPAIGN_CREATE)));

        assertThat(called[0]).isTrue();
    }

    @Test
    @DisplayName("dispatch — fails with DmosCommandHandlerNotFoundException for unregistered type")
    void dispatch_failsForUnregisteredType() {
        DmCommandDispatcher dispatcher = new DmCommandDispatcher(
            Map.of(DmCommandType.CAMPAIGN_CREATE, cmd -> Promise.of(null)));

        assertThatThrownBy(() ->
            runPromise(() -> dispatcher.dispatch(buildCommand(DmCommandType.EMAIL_SEND))))
            .isInstanceOf(DmosCommandHandlerNotFoundException.class)
            .hasMessageContaining("EMAIL_SEND");
    }

    @Test
    @DisplayName("dispatch — propagates handler exception")
    void dispatch_propagatesHandlerException() {
        DmCommandHandler handler = cmd ->
            Promise.ofException(new RuntimeException("handler blew up"));

        DmCommandDispatcher dispatcher = new DmCommandDispatcher(
            Map.of(DmCommandType.BUDGET_ADJUST, handler));

        assertThatThrownBy(() ->
            runPromise(() -> dispatcher.dispatch(buildCommand(DmCommandType.BUDGET_ADJUST))))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("handler blew up");
    }

    @Test
    @DisplayName("dispatch — rejects null command")
    void dispatch_rejectsNullCommand() {
        DmCommandDispatcher dispatcher = new DmCommandDispatcher(
            Map.of(DmCommandType.CAMPAIGN_CREATE, cmd -> Promise.of(null)));

        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> dispatcher.dispatch(null));
    }

    @Test
    @DisplayName("constructor — rejects empty handler map")
    void constructor_rejectsEmptyMap() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmCommandDispatcher(Map.of()));
    }

    @Test
    @DisplayName("hasHandler — returns true for registered type, false for unregistered")
    void hasHandler_reflectsRegistry() {
        DmCommandDispatcher dispatcher = new DmCommandDispatcher(
            Map.of(DmCommandType.CAMPAIGN_CREATE, cmd -> Promise.of(null)));

        assertThat(dispatcher.hasHandler(DmCommandType.CAMPAIGN_CREATE)).isTrue();
        assertThat(dispatcher.hasHandler(DmCommandType.EMAIL_SEND)).isFalse();
    }

    @Test
    @DisplayName("handlerCount — returns number of registered handlers")
    void handlerCount_returnsRegistrySize() {
        DmCommandDispatcher dispatcher = new DmCommandDispatcher(Map.of(
            DmCommandType.CAMPAIGN_CREATE, cmd -> Promise.of(null),
            DmCommandType.BUDGET_ADJUST,   cmd -> Promise.of(null)
        ));
        assertThat(dispatcher.handlerCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("dispatch — multiple handlers route to correct handler each time")
    void dispatch_correctHandlerCalledForType() {
        int[] campaignCalls = {0};
        int[] budgetCalls = {0};

        DmCommandDispatcher dispatcher = new DmCommandDispatcher(Map.of(
            DmCommandType.CAMPAIGN_CREATE, cmd -> { campaignCalls[0]++; return Promise.of(null); },
            DmCommandType.BUDGET_ADJUST,   cmd -> { budgetCalls[0]++;   return Promise.of(null); }
        ));

        runPromise(() -> dispatcher.dispatch(buildCommand(DmCommandType.CAMPAIGN_CREATE)));
        runPromise(() -> dispatcher.dispatch(buildCommand(DmCommandType.BUDGET_ADJUST)));
        runPromise(() -> dispatcher.dispatch(buildCommand(DmCommandType.CAMPAIGN_CREATE)));

        assertThat(campaignCalls[0]).isEqualTo(2);
        assertThat(budgetCalls[0]).isEqualTo(1);
    }
}
