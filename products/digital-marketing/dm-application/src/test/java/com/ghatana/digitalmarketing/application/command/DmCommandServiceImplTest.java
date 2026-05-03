package com.ghatana.digitalmarketing.application.command;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.command.DmCommand;
import com.ghatana.digitalmarketing.domain.command.DmCommandStatus;
import com.ghatana.digitalmarketing.domain.command.DmCommandType;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Unit tests for {@link DmCommandServiceImpl}.
 *
 * @doc.type class
 * @doc.purpose Verifies DMOS command store lifecycle (DMOS-F2-003)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DmCommandServiceImpl Tests")
class DmCommandServiceImplTest extends EventloopTestBase {

    private InMemoryCommandRepository repo;
    private AllowingKernelAdapter kernelAdapter;
    private DmCommandServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        repo          = new InMemoryCommandRepository();
        kernelAdapter = new AllowingKernelAdapter(true);
        service       = new DmCommandServiceImpl(repo, kernelAdapter);

        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    // ── issue ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("issue stores command with PENDING status")
    void issueStoresCommandAsPending() {
        DmCommandService.IssueCommandRequest request = new DmCommandService.IssueCommandRequest(
            DmCommandType.CAMPAIGN_CREATE,
            "{\"campaignId\":\"c-1\"}"
        );

        DmCommand cmd = runPromise(() -> service.issue(ctx, request));

        assertThat(cmd.getId()).isNotBlank();
        assertThat(cmd.getStatus()).isEqualTo(DmCommandStatus.PENDING);
        assertThat(cmd.getCommandType()).isEqualTo(DmCommandType.CAMPAIGN_CREATE);
        assertThat(cmd.getTenantId()).isEqualTo("tenant-1");
        assertThat(cmd.getWorkspaceId()).isEqualTo("ws-1");
        assertThat(cmd.getAttemptCount()).isZero();
        assertThat(cmd.isRetryable()).isTrue();
    }

    @Test
    @DisplayName("issue rejects null ctx")
    void issueRejectsNullCtx() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> runPromise(() -> service.issue(null, new DmCommandService.IssueCommandRequest(
                DmCommandType.BUDGET_ADJUST, "{}"))));
    }

    @Test
    @DisplayName("issue rejects null request")
    void issueRejectsNullRequest() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> runPromise(() -> service.issue(ctx, null)));
    }

    @Test
    @DisplayName("issue rejects blank payload")
    void issueRejectsBlankPayload() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmCommandService.IssueCommandRequest(
                DmCommandType.CAMPAIGN_PAUSE, "   "));
    }

    @Test
    @DisplayName("issue rejects null command type")
    void issueRejectsNullCommandType() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> new DmCommandService.IssueCommandRequest(null, "{}"));
    }

    @Test
    @DisplayName("issue fails when not authorized")
    void issueFailsWhenNotAuthorized() {
        kernelAdapter = new AllowingKernelAdapter(false);
        service = new DmCommandServiceImpl(repo, kernelAdapter);

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.issue(ctx, new DmCommandService.IssueCommandRequest(
                DmCommandType.CAMPAIGN_CREATE, "{}"))));
    }

    // ── findById ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById returns empty when command not found")
    void findByIdReturnsEmptyWhenNotFound() {
        Optional<DmCommand> result = runPromise(() -> service.findById(ctx, "non-existent-id"));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findById returns empty for cross-tenant access")
    void findByIdHidesCrossTenantCommands() {
        DmCommand stored = runPromise(() -> service.issue(ctx, new DmCommandService.IssueCommandRequest(
            DmCommandType.CAMPAIGN_CREATE, "{}")));

        DmOperationContext otherCtx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("other-tenant"))
            .workspaceId(DmWorkspaceId.of("ws-2"))
            .actor(ActorRef.user("user-2"))
            .correlationId(DmCorrelationId.of("corr-2"))
            .idempotencyKey(DmIdempotencyKey.of("idk-2"))
            .build();

        Optional<DmCommand> result = runPromise(() -> service.findById(otherCtx, stored.getId()));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findById rejects blank id")
    void findByIdRejectsBlankId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.findById(ctx, "  ")));
    }

    // ── listPending ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("listPending returns empty list when no pending commands")
    void listPendingEmptyWhenNone() {
        List<DmCommand> result = runPromise(() -> service.listPending(ctx, 10));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("listPending returns issued commands")
    void listPendingReturnsPending() {
        runPromise(() -> service.issue(ctx, new DmCommandService.IssueCommandRequest(
            DmCommandType.CAMPAIGN_CREATE, "{\"c\":1}")));
        runPromise(() -> service.issue(ctx, new DmCommandService.IssueCommandRequest(
            DmCommandType.AD_GROUP_CREATE, "{\"c\":2}")));

        List<DmCommand> result = runPromise(() -> service.listPending(ctx, 10));
        assertThat(result).hasSize(2);
    }

    // ── mark transitions ─────────────────────────────────────────────────────

    @Test
    @DisplayName("markExecuting sets EXECUTING status and increments attempt count")
    void markExecutingSetsStatus() {
        DmCommand cmd = runPromise(() -> service.issue(ctx, new DmCommandService.IssueCommandRequest(
            DmCommandType.CAMPAIGN_PAUSE, "{}")));

        DmCommand executing = runPromise(() -> service.markExecuting(ctx, cmd.getId()));

        assertThat(executing.getStatus()).isEqualTo(DmCommandStatus.EXECUTING);
        assertThat(executing.getAttemptCount()).isEqualTo(1);
        assertThat(executing.getExecutedAt()).isNotNull();
    }

    @Test
    @DisplayName("markSucceeded sets SUCCEEDED status")
    void markSucceededSetsStatus() {
        DmCommand cmd = runPromise(() -> service.issue(ctx, new DmCommandService.IssueCommandRequest(
            DmCommandType.CAMPAIGN_RESUME, "{}")));
        runPromise(() -> service.markExecuting(ctx, cmd.getId()));

        DmCommand succeeded = runPromise(() -> service.markSucceeded(ctx, cmd.getId()));

        assertThat(succeeded.getStatus()).isEqualTo(DmCommandStatus.SUCCEEDED);
        assertThat(succeeded.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("markFailed sets FAILED status with reason")
    void markFailedSetsStatus() {
        DmCommand cmd = runPromise(() -> service.issue(ctx, new DmCommandService.IssueCommandRequest(
            DmCommandType.BUDGET_ADJUST, "{}")));
        runPromise(() -> service.markExecuting(ctx, cmd.getId()));

        DmCommand failed = runPromise(() -> service.markFailed(ctx, cmd.getId(), "quota-exceeded"));

        assertThat(failed.getStatus()).isEqualTo(DmCommandStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo("quota-exceeded");
    }

    @Test
    @DisplayName("markRolledBack sets ROLLED_BACK status from FAILED")
    void markRolledBackSetsStatus() {
        DmCommand cmd = runPromise(() -> service.issue(ctx, new DmCommandService.IssueCommandRequest(
            DmCommandType.CAMPAIGN_DELETE, "{}")));
        runPromise(() -> service.markExecuting(ctx, cmd.getId()));
        runPromise(() -> service.markFailed(ctx, cmd.getId(), "api-error"));

        DmCommand rb = runPromise(() -> service.markRolledBack(ctx, cmd.getId()));

        assertThat(rb.getStatus()).isEqualTo(DmCommandStatus.ROLLED_BACK);
    }

    @Test
    @DisplayName("markRolledBack fails when command is not FAILED")
    void markRolledBackFailsWhenNotFailed() {
        DmCommand cmd = runPromise(() -> service.issue(ctx, new DmCommandService.IssueCommandRequest(
            DmCommandType.CAMPAIGN_CREATE, "{}")));

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> runPromise(() -> service.markRolledBack(ctx, cmd.getId())));
    }

    @Test
    @DisplayName("markExecuting fails for unknown command id")
    void markExecutingFailsForUnknownId() {
        assertThatExceptionOfType(java.util.NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.markExecuting(ctx, "not-a-real-id")));
    }

    @Test
    @DisplayName("markExecuting rejects blank command id")
    void markExecutingRejectsBlankId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.markExecuting(ctx, "")));
    }

    // ── countByStatus ────────────────────────────────────────────────────────

    @Test
    @DisplayName("countByStatus returns zero when no commands")
    void countByStatusReturnsZeroWhenNone() {
        long count = runPromise(() -> service.countByStatus(ctx, DmCommandStatus.PENDING));
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("countByStatus counts only matching status")
    void countByStatusCountsCorrectly() {
        runPromise(() -> service.issue(ctx, new DmCommandService.IssueCommandRequest(
            DmCommandType.CAMPAIGN_CREATE, "{\"a\":1}")));
        runPromise(() -> service.issue(ctx, new DmCommandService.IssueCommandRequest(
            DmCommandType.AD_GROUP_CREATE, "{\"a\":2}")));

        long pending = runPromise(() -> service.countByStatus(ctx, DmCommandStatus.PENDING));
        long succeeded = runPromise(() -> service.countByStatus(ctx, DmCommandStatus.SUCCEEDED));

        assertThat(pending).isEqualTo(2);
        assertThat(succeeded).isZero();
    }

    // ── test doubles ─────────────────────────────────────────────────────────

    private static final class InMemoryCommandRepository implements DmCommandRepository {
        private final ConcurrentHashMap<String, DmCommand> store = new ConcurrentHashMap<>();

        @Override
        public Promise<DmCommand> save(DmCommand command) {
            store.put(command.getId(), command);
            return Promise.of(command);
        }

        @Override
        public Promise<Optional<DmCommand>> findById(String id) {
            return Promise.of(Optional.ofNullable(store.get(id)));
        }

        @Override
        public Promise<List<DmCommand>> findPending(String tenantId, int limit) {
            List<DmCommand> result = new ArrayList<>();
            for (DmCommand cmd : store.values()) {
                if (cmd.getTenantId().equals(tenantId) && cmd.getStatus() == DmCommandStatus.PENDING) {
                    result.add(cmd);
                    if (result.size() >= limit) break;
                }
            }
            return Promise.of(result);
        }

        @Override
        public Promise<List<DmCommand>> findByTypeAndStatus(
                String tenantId, DmCommandType commandType, DmCommandStatus status, int limit) {
            List<DmCommand> result = new ArrayList<>();
            for (DmCommand cmd : store.values()) {
                if (cmd.getTenantId().equals(tenantId)
                        && cmd.getCommandType() == commandType
                        && cmd.getStatus() == status) {
                    result.add(cmd);
                    if (result.size() >= limit) break;
                }
            }
            return Promise.of(result);
        }

        @Override
        public Promise<DmCommand> update(DmCommand command) {
            store.put(command.getId(), command);
            return Promise.of(command);
        }

        @Override
        public Promise<Long> countByStatus(String tenantId, DmCommandStatus status) {
            long count = store.values().stream()
                .filter(c -> c.getTenantId().equals(tenantId) && c.getStatus() == status)
                .count();
            return Promise.of(count);
        }
    }

    private static final class AllowingKernelAdapter implements DigitalMarketingKernelAdapter {
        private final boolean authorized;

        AllowingKernelAdapter(boolean authorized) {
            this.authorized = authorized;
        }

        @Override
        public void start() {}

        @Override
        public void stop() {}

        @Override
        public Promise<Boolean> isAuthorized(DmOperationContext context, String resource, String action) {
            return Promise.of(authorized);
        }

        @Override
        public Promise<Boolean> verifyConsent(DmOperationContext context, String subjectId, String purpose) {
            return Promise.of(true);
        }

        @Override
        public Promise<String> requestApproval(
                DmOperationContext context, String operationType, String subjectId, String description) {
            return Promise.of("approval-1");
        }

        @Override
        public Promise<String> recordAudit(
                DmOperationContext context, String entityId, String action,
                Map<String, Object> attributes) {
            return Promise.of("audit-1");
        }
    }
}
