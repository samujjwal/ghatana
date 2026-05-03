package com.ghatana.digitalmarketing.application.recommendation;

import com.ghatana.digitalmarketing.application.command.DmCommandRepository;
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
import com.ghatana.digitalmarketing.domain.recommendation.DmAgentRecommendation;
import com.ghatana.digitalmarketing.domain.recommendation.DmRecommendationStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Unit tests for {@link DmRecommendationGatewayImpl}.
 *
 * @doc.type class
 * @doc.purpose Verifies recommendation-to-command gateway lifecycle (DMOS-F2-005)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DmRecommendationGatewayImpl Tests")
class DmRecommendationGatewayImplTest extends EventloopTestBase {

    private InMemoryRecommendationRepository recRepo;
    private InMemoryCommandRepository cmdRepo;
    private AllowingKernelAdapter kernelAdapter;
    private DmRecommendationGatewayImpl gateway;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        recRepo       = new InMemoryRecommendationRepository();
        cmdRepo       = new InMemoryCommandRepository();
        kernelAdapter = new AllowingKernelAdapter(true);
        gateway       = new DmRecommendationGatewayImpl(recRepo, cmdRepo, kernelAdapter);

        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    private DmRecommendationGateway.SubmitRecommendationRequest sampleRequest() {
        return new DmRecommendationGateway.SubmitRecommendationRequest(
            "agent-1",
            DmCommandType.CAMPAIGN_CREATE,
            Map.of("budget", 500),
            "High ROI expected"
        );
    }

    // ── submit ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("submit creates PENDING recommendation")
    void submitSuccess() {
        DmAgentRecommendation rec = runPromise(() -> gateway.submit(ctx, sampleRequest()));
        assertThat(rec.getId()).isNotBlank();
        assertThat(rec.getStatus()).isEqualTo(DmRecommendationStatus.PENDING);
        assertThat(rec.getTenantId()).isEqualTo("tenant-1");
        assertThat(rec.getAgentId()).isEqualTo("agent-1");
    }

    @Test
    @DisplayName("submit rejects null ctx")
    void submitNullCtx() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> runPromise(() -> gateway.submit(null, sampleRequest())));
    }

    @Test
    @DisplayName("submit rejects null request")
    void submitNullRequest() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> runPromise(() -> gateway.submit(ctx, null)));
    }

    @Test
    @DisplayName("submit throws SecurityException when unauthorized")
    void submitUnauthorized() {
        gateway = new DmRecommendationGatewayImpl(recRepo, cmdRepo, new AllowingKernelAdapter(false));
        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> gateway.submit(ctx, sampleRequest())));
    }

    // ── accept ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("accept transitions PENDING to ACCEPTED and creates command")
    void acceptSuccess() {
        DmAgentRecommendation rec = runPromise(() -> gateway.submit(ctx, sampleRequest()));
        DmAgentRecommendation accepted = runPromise(() -> gateway.accept(ctx, rec.getId()));

        assertThat(accepted.getStatus()).isEqualTo(DmRecommendationStatus.ACCEPTED);
        assertThat(accepted.getCommandId()).isNotBlank();
        assertThat(cmdRepo.store).containsKey(accepted.getCommandId());
    }

    @Test
    @DisplayName("accept rejects blank id")
    void acceptBlankId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> gateway.accept(ctx, "")));
    }

    @Test
    @DisplayName("accept throws NoSuchElementException for unknown id")
    void acceptUnknownId() {
        assertThatExceptionOfType(java.util.NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> gateway.accept(ctx, "nonexistent")));
    }

    @Test
    @DisplayName("accept throws SecurityException when unauthorized")
    void acceptUnauthorized() {
        DmAgentRecommendation rec = runPromise(() -> gateway.submit(ctx, sampleRequest()));
        gateway = new DmRecommendationGatewayImpl(recRepo, cmdRepo, new AllowingKernelAdapter(false));
        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> gateway.accept(ctx, rec.getId())));
    }

    // ── reject ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("reject transitions PENDING to REJECTED")
    void rejectSuccess() {
        DmAgentRecommendation rec = runPromise(() -> gateway.submit(ctx, sampleRequest()));
        DmAgentRecommendation rejected = runPromise(() -> gateway.reject(ctx, rec.getId(), "policy"));

        assertThat(rejected.getStatus()).isEqualTo(DmRecommendationStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).isEqualTo("policy");
    }

    @Test
    @DisplayName("reject rejects null reason")
    void rejectNullReason() {
        DmAgentRecommendation rec = runPromise(() -> gateway.submit(ctx, sampleRequest()));
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> runPromise(() -> gateway.reject(ctx, rec.getId(), null)));
    }

    @Test
    @DisplayName("reject throws IllegalArgumentException for blank id")
    void rejectBlankId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> gateway.reject(ctx, "", "reason")));
    }

    @Test
    @DisplayName("reject throws IllegalArgumentException for null id")
    void rejectNullId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> gateway.reject(ctx, null, "reason")));
    }

    @Test
    @DisplayName("reject throws SecurityException when unauthorized")
    void rejectUnauthorized() {
        DmAgentRecommendation rec = runPromise(() -> gateway.submit(ctx, sampleRequest()));
        gateway = new DmRecommendationGatewayImpl(recRepo, cmdRepo, new AllowingKernelAdapter(false));
        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> gateway.reject(ctx, rec.getId(), "policy")));
    }

    // ── expire ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("expire transitions PENDING to EXPIRED")
    void expireSuccess() {
        DmAgentRecommendation rec = runPromise(() -> gateway.submit(ctx, sampleRequest()));
        DmAgentRecommendation expired = runPromise(() -> gateway.expire(ctx, rec.getId()));

        assertThat(expired.getStatus()).isEqualTo(DmRecommendationStatus.EXPIRED);
    }

    @Test
    @DisplayName("expire throws IllegalArgumentException for blank id")
    void expireBlankId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> gateway.expire(ctx, "")));
    }

    @Test
    @DisplayName("expire throws IllegalArgumentException for null id")
    void expireNullId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> gateway.expire(ctx, null)));
    }

    @Test
    @DisplayName("expire throws SecurityException when unauthorized")
    void expireUnauthorized() {
        DmAgentRecommendation rec = runPromise(() -> gateway.submit(ctx, sampleRequest()));
        gateway = new DmRecommendationGatewayImpl(recRepo, cmdRepo, new AllowingKernelAdapter(false));
        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> gateway.expire(ctx, rec.getId())));
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById returns empty for unknown id")
    void findByIdEmpty() {
        Optional<DmAgentRecommendation> result = runPromise(() -> gateway.findById(ctx, "unknown"));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findById throws IllegalArgumentException for blank id")
    void findByIdBlankId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> gateway.findById(ctx, "")));
    }

    @Test
    @DisplayName("findById throws IllegalArgumentException for null id")
    void findByIdNullId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> gateway.findById(ctx, null)));
    }

    @Test
    @DisplayName("findById filters cross-tenant access")
    void findByIdCrossTenant() {
        DmAgentRecommendation rec = runPromise(() -> gateway.submit(ctx, sampleRequest()));
        DmOperationContext otherCtx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("other-tenant"))
            .workspaceId(DmWorkspaceId.of("ws-2"))
            .actor(ActorRef.user("user-2"))
            .correlationId(DmCorrelationId.of("corr-2"))
            .idempotencyKey(DmIdempotencyKey.of("idk-2"))
            .build();
        Optional<DmAgentRecommendation> result = runPromise(() -> gateway.findById(otherCtx, rec.getId()));
        assertThat(result).isEmpty();
    }

    // ── listPending ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("listPending returns only PENDING recommendations")
    void listPending() {
        runPromise(() -> gateway.submit(ctx, sampleRequest()));
        runPromise(() -> gateway.submit(ctx, sampleRequest()));
        List<DmAgentRecommendation> pending = runPromise(() -> gateway.listPending(ctx, 10));
        assertThat(pending).hasSize(2);
        assertThat(pending).allMatch(r -> r.getStatus() == DmRecommendationStatus.PENDING);
    }

    // ── countByStatus ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("countByStatus returns correct count")
    void countByStatus() {
        runPromise(() -> gateway.submit(ctx, sampleRequest()));
        runPromise(() -> gateway.submit(ctx, sampleRequest()));
        Long count = runPromise(() -> gateway.countByStatus(ctx, DmRecommendationStatus.PENDING));
        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("countByStatus rejects null status")
    void countByStatusNullStatus() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> runPromise(() -> gateway.countByStatus(ctx, null)));
    }

    // ── Test Doubles ──────────────────────────────────────────────────────────

    private static final class InMemoryRecommendationRepository implements DmRecommendationRepository {
        private final ConcurrentHashMap<String, DmAgentRecommendation> store = new ConcurrentHashMap<>();

        @Override
        public Promise<DmAgentRecommendation> save(DmAgentRecommendation rec) {
            store.put(rec.getId(), rec);
            return Promise.of(rec);
        }

        @Override
        public Promise<Optional<DmAgentRecommendation>> findById(String id) {
            return Promise.of(Optional.ofNullable(store.get(id)));
        }

        @Override
        public Promise<List<DmAgentRecommendation>> findByStatus(
                String tenantId, DmRecommendationStatus status, int limit) {
            List<DmAgentRecommendation> result = new ArrayList<>();
            for (DmAgentRecommendation r : store.values()) {
                if (r.getTenantId().equals(tenantId) && r.getStatus() == status) {
                    result.add(r);
                    if (result.size() >= limit) break;
                }
            }
            return Promise.of(result);
        }

        @Override
        public Promise<DmAgentRecommendation> update(DmAgentRecommendation rec) {
            store.put(rec.getId(), rec);
            return Promise.of(rec);
        }

        @Override
        public Promise<Long> countByStatus(String tenantId, DmRecommendationStatus status) {
            long count = store.values().stream()
                .filter(r -> r.getTenantId().equals(tenantId) && r.getStatus() == status)
                .count();
            return Promise.of(count);
        }
    }

    private static final class InMemoryCommandRepository implements DmCommandRepository {
        final ConcurrentHashMap<String, DmCommand> store = new ConcurrentHashMap<>();

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

        @Override public void start() {}
        @Override public void stop() {}

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
