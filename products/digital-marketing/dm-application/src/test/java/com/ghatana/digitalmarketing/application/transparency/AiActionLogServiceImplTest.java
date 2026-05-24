package com.ghatana.digitalmarketing.application.transparency;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.transparency.AiActionLogEntry;
import com.ghatana.digitalmarketing.domain.transparency.AiActionStatus;
import com.ghatana.digitalmarketing.domain.transparency.AiActionType;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AiActionLogServiceImpl")
class AiActionLogServiceImplTest extends EventloopTestBase {

    private RecordingKernelAdapter kernel;
    private EphemeralRepo repo;
    private AiActionLogServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        kernel = new RecordingKernelAdapter();
        repo = new EphemeralRepo();
        service = new AiActionLogServiceImpl(kernel, repo);
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-alice"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .build();
    }

    @Test
    @DisplayName("recordAction persists entry")
    void shouldRecordAction() {
        AiActionLogEntry entry = runPromise(() -> service.recordAction(ctx,
            new AiActionLogService.RecordActionCommand(
                null,
                AiActionType.DRAFT_GENERATED,
                AiActionStatus.PROPOSED,
                null,
                true,
                0.82,
                List.of("https://evidence"),
                List.of("policy:ok"),
                "Generated ad draft",
                "Generated from approved strategy",
                "content-1"
            )));

        assertThat(entry.actionId()).isNotBlank();
        assertThat(entry.actor()).isEqualTo("user-alice");
        assertThat(repo.entries).hasSize(1);
    }

    @Test
    @DisplayName("listActions redacts details without sensitive permission")
    void shouldRedactWhenSensitivePermissionMissing() {
        runPromise(() -> service.recordAction(ctx,
            new AiActionLogService.RecordActionCommand(
                "corr-1", AiActionType.VALIDATION_RESULT, AiActionStatus.BLOCKED,
                "agent", true, 0.4, List.of("https://evidence"), List.of(),
                "Validation blocked", "Contains sensitive payload", "ver-1"
            )));

        List<AiActionLogEntry> entries = runPromise(() ->
            service.listActions(ctx, new AiActionLogService.ListActionsQuery(null, null, 20)));

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).details()).isEqualTo("REDACTED");
        assertThat(entries.get(0).evidenceLinks()).isEmpty();
    }

    @Test
    @DisplayName("getAction returns full details with sensitive permission")
    void shouldReturnFullEntryWithSensitivePermission() {
        kernel.allowSensitive = true;
        AiActionLogEntry created = runPromise(() -> service.recordAction(ctx,
            new AiActionLogService.RecordActionCommand(
                "corr-1", AiActionType.ACTION_EXECUTED, AiActionStatus.EXECUTED,
                "system", false, null, List.of(), List.of(),
                "Executed connector command", "Posted campaign to Google Ads", "cmd-1"
            )));

        AiActionLogEntry loaded = runPromise(() -> service.getAction(ctx, created.actionId()));
        assertThat(loaded.details()).isEqualTo("Posted campaign to Google Ads");
    }

    @Test
    @DisplayName("recordAction throws when not authorized")
    void shouldDenyWrite() {
        kernel.deny = true;
        assertThatThrownBy(() -> runPromise(() -> service.recordAction(ctx,
            new AiActionLogService.RecordActionCommand(
                "corr-1", AiActionType.ACTION_EXECUTED, AiActionStatus.EXECUTED,
                "system", false, null, List.of(), List.of(),
                "x", "y", null
            )))).isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("listActions throws when not authorized")
    void listActionsUnauthorized() {
        kernel.deny = true;
        assertThatThrownBy(() -> runPromise(() ->
            service.listActions(ctx, new AiActionLogService.ListActionsQuery(null, null, 20))))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("listActions returns full entry when sensitive permission granted")
    void listActionsFullWhenSensitivePermitted() {
        kernel.allowSensitive = true;
        runPromise(() -> service.recordAction(ctx,
            new AiActionLogService.RecordActionCommand(
                "corr-1", AiActionType.VALIDATION_RESULT, AiActionStatus.BLOCKED,
                "agent", true, 0.4, List.of("https://evidence"), List.of(),
                "Summary", "Sensitive payload", "ver-2"
            )));

        List<AiActionLogEntry> entries = runPromise(() ->
            service.listActions(ctx, new AiActionLogService.ListActionsQuery(null, null, 20)));

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).details()).isEqualTo("Sensitive payload");
    }

    @Test
    @DisplayName("listActions with zero limit uses default of 50")
    void listActionsZeroLimitUsesDefault() {
        runPromise(() -> service.recordAction(ctx,
            new AiActionLogService.RecordActionCommand(
                null, AiActionType.ACTION_EXECUTED, AiActionStatus.EXECUTED,
                null, false, null, List.of(), List.of(), "s", "d", null
            )));

        List<AiActionLogEntry> entries = runPromise(() ->
            service.listActions(ctx, new AiActionLogService.ListActionsQuery(null, null, 0)));

        assertThat(entries).hasSize(1);
    }

    @Test
    @DisplayName("getAction throws when not authorized")
    void getActionUnauthorized() {
        kernel.deny = true;
        assertThatThrownBy(() -> runPromise(() -> service.getAction(ctx, "any-id")))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("getAction throws for blank action id")
    void getActionBlankId() {
        assertThatThrownBy(() -> runPromise(() -> service.getAction(ctx, "")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("getAction redacts entry without sensitive permission")
    void getActionRedactsWithoutSensitivePermission() {
        AiActionLogEntry created = runPromise(() -> service.recordAction(ctx,
            new AiActionLogService.RecordActionCommand(
                "corr-1", AiActionType.ACTION_EXECUTED, AiActionStatus.EXECUTED,
                "system", false, null, List.of("https://link"), List.of(),
                "Summary", "Internal details", "ent-1"
            )));

        AiActionLogEntry loaded = runPromise(() -> service.getAction(ctx, created.actionId()));
        assertThat(loaded.details()).isEqualTo("REDACTED");
    }

    private static final class EphemeralRepo implements AiActionLogRepository {
        private final List<AiActionLogEntry> entries = new ArrayList<>();

        @Override
        public Promise<AiActionLogEntry> save(AiActionLogEntry entry) {
            entries.add(entry);
            return Promise.of(entry);
        }

        @Override
        public Promise<Optional<AiActionLogEntry>> findById(String workspaceId, String actionId) {
            return Promise.of(entries.stream()
                .filter(e -> e.workspaceId().equals(workspaceId) && e.actionId().equals(actionId))
                .findFirst());
        }

        @Override
        public Promise<List<AiActionLogEntry>> findByWorkspace(String workspaceId, String correlationId, String relatedEntityId, int limit) {
            return Promise.of(entries.stream()
                .filter(e -> e.workspaceId().equals(workspaceId))
                .filter(e -> correlationId == null || correlationId.isBlank() || e.correlationId().equals(correlationId))
                .filter(e -> relatedEntityId == null || relatedEntityId.isBlank() || relatedEntityId.equals(e.relatedEntityId()))
                .limit(limit)
                .toList());
        }
    }

    private static final class RecordingKernelAdapter implements DigitalMarketingKernelAdapter {
        boolean deny = false;
        boolean allowSensitive = false;

        @Override public void start() {}
        @Override public void stop() {}

        @Override
        public Promise<Boolean> isAuthorized(DmOperationContext context, String resource, String action) {
            if (deny) return Promise.of(false);
            if ("ai-action-log-sensitive".equals(resource)) return Promise.of(allowSensitive);
            return Promise.of(true);
        }

        @Override
        public Promise<Boolean> verifyConsent(DmOperationContext context, String subjectId, String purpose) {
            return Promise.of(true);
        }

        @Override
        public Promise<String> requestApproval(DmOperationContext context, String operationType, String subjectId, String description) {
            return Promise.of("req-1");
        }

        @Override
        public Promise<String> recordAudit(DmOperationContext context, String entityId, String action, Map<String, Object> attributes) {
            return Promise.of("audit-1");
        }
    }
}
