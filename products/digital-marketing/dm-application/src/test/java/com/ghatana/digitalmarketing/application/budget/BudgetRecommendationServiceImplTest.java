package com.ghatana.digitalmarketing.application.budget;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.budget.BudgetRecommendation;
import com.ghatana.digitalmarketing.domain.budget.BudgetRecommendationStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BudgetRecommendationServiceImpl")
class BudgetRecommendationServiceImplTest extends EventloopTestBase {

    private RecordingKernelAdapter kernelAdapter;
    private EphemeralBudgetRecommendationRepository repository;
    private BudgetRecommendationServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        kernelAdapter = new RecordingKernelAdapter();
        repository = new EphemeralBudgetRecommendationRepository();
        service = new BudgetRecommendationServiceImpl(kernelAdapter, repository);
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("owner-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    private static BudgetRecommendationService.GenerateBudgetCommand validCommand() {
        return new BudgetRecommendationService.GenerateBudgetCommand("strat-1", 3000.0, 10.0);
    }

    @Test
    @DisplayName("generates recommendation in DRAFT status with 3 channel allocations")
    void shouldRecommendBudget() {
        BudgetRecommendation rec = runPromise(() -> service.recommendBudget(ctx, validCommand()));

        assertThat(rec.getStatus()).isEqualTo(BudgetRecommendationStatus.DRAFT);
        assertThat(rec.getWorkspaceId()).isEqualTo(DmWorkspaceId.of("ws-1"));
        assertThat(rec.getChannelAllocations()).hasSize(3);
        assertThat(rec.getTotalMonthlyCap()).isEqualTo(3000.0);
        assertThat(rec.getModelVersion()).isEqualTo(BudgetRecommendationServiceImpl.MODEL_VERSION);
        assertThat(kernelAdapter.lastAuditAction).isEqualTo("budget-recommendation-generated");
    }

    @Test
    @DisplayName("zero budget cap produces zero-allocation recommendation")
    void zeroBudgetProducesZeroAllocation() {
        BudgetRecommendationService.GenerateBudgetCommand zeroBudget =
            new BudgetRecommendationService.GenerateBudgetCommand("strat-1", 0.0, 10.0);
        BudgetRecommendation rec = runPromise(() -> service.recommendBudget(ctx, zeroBudget));
        assertThat(rec.getTotalMonthlyCap()).isEqualTo(0.0);
        assertThat(rec.getChannelAllocations()).allMatch(a -> a.recommendedAmount() == 0.0);
    }

    @Test
    @DisplayName("getLatestRecommendation returns NoSuchElement when none exists")
    void getLatestReturnsNoSuchElement() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.getLatestRecommendation(ctx)));
    }

    @Test
    @DisplayName("getLatestRecommendation returns the saved recommendation")
    void getLatestReturnsSavedRecommendation() {
        runPromise(() -> service.recommendBudget(ctx, validCommand()));
        BudgetRecommendation latest = runPromise(() -> service.getLatestRecommendation(ctx));
        assertThat(latest.getWorkspaceId()).isEqualTo(DmWorkspaceId.of("ws-1"));
    }

    @Test
    @DisplayName("submitForApproval transitions DRAFT to PENDING_APPROVAL")
    void submitForApprovalTransitionsToPending() {
        BudgetRecommendation draft = runPromise(() -> service.recommendBudget(ctx, validCommand()));
        BudgetRecommendation pending = runPromise(
            () -> service.submitForApproval(ctx, draft.getRecommendationId()));
        assertThat(pending.getStatus()).isEqualTo(BudgetRecommendationStatus.PENDING_APPROVAL);
        assertThat(kernelAdapter.lastAuditAction).isEqualTo("budget-recommendation-submitted");
    }

    @Test
    @DisplayName("submitForApproval throws NoSuchElement for unknown ID")
    void submitForApprovalThrowsForUnknownId() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.submitForApproval(ctx, "nonexistent")));
    }

    @Test
    @DisplayName("approveRecommendation transitions PENDING_APPROVAL to APPROVED")
    void approveRecommendation() {
        BudgetRecommendation draft = runPromise(() -> service.recommendBudget(ctx, validCommand()));
        runPromise(() -> service.submitForApproval(ctx, draft.getRecommendationId()));
        BudgetRecommendation approved = runPromise(
            () -> service.approveRecommendation(ctx, draft.getRecommendationId()));
        assertThat(approved.getStatus()).isEqualTo(BudgetRecommendationStatus.APPROVED);
        assertThat(approved.getApprovedBy()).isEqualTo("owner-1");
        assertThat(kernelAdapter.lastAuditAction).isEqualTo("budget-recommendation-approved");
    }

    @Test
    @DisplayName("approveRecommendation on DRAFT throws IllegalStateException")
    void approveFromDraftThrowsIllegalState() {
        BudgetRecommendation draft = runPromise(() -> service.recommendBudget(ctx, validCommand()));
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> runPromise(
                () -> service.approveRecommendation(ctx, draft.getRecommendationId())));
    }

    @Test
    @DisplayName("unauthorized recommendBudget throws SecurityException")
    void unauthorizedGenerateThrows() {
        kernelAdapter.denyAll = true;
        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.recommendBudget(ctx, validCommand())));
    }

    @Test
    @DisplayName("unauthorized getLatestRecommendation throws SecurityException")
    void unauthorizedGetThrows() {
        kernelAdapter.denyAll = true;
        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.getLatestRecommendation(ctx)));
    }

    @Test
    @DisplayName("null dependencies throw in constructor")
    void nullDepsThrow() {
        assertThatThrownBy(() -> new BudgetRecommendationServiceImpl(null, repository))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BudgetRecommendationServiceImpl(kernelAdapter, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("null ctx throws in recommendBudget")
    void nullCtxThrowsInRecommend() {
        assertThatThrownBy(() -> runPromise(() -> service.recommendBudget(null, validCommand())))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("null command throws in recommendBudget")
    void nullCommandThrowsInRecommend() {
        assertThatThrownBy(() -> runPromise(() -> service.recommendBudget(ctx, null)))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("command rejects blank strategyId")
    void commandRejectsBlankStrategyId() {
        assertThatThrownBy(() -> new BudgetRecommendationService.GenerateBudgetCommand("", 1000.0, 10.0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("command rejects negative totalMonthlyCap")
    void commandRejectsNegativeCap() {
        assertThatThrownBy(() -> new BudgetRecommendationService.GenerateBudgetCommand("strat-1", -1.0, 10.0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("command rejects changeThreshold above 100")
    void commandRejectsChangeThresholdOutOfRange() {
        assertThatThrownBy(() -> new BudgetRecommendationService.GenerateBudgetCommand("strat-1", 1000.0, 101.0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- test doubles ----

    private static final class RecordingKernelAdapter implements DigitalMarketingKernelAdapter {
        boolean denyAll = false;
        String lastAuditAction;

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public Promise<Boolean> isAuthorized(DmOperationContext ctx, String resource, String action) {
            return Promise.of(!denyAll);
        }

        @Override
        public Promise<String> recordAudit(DmOperationContext ctx, String entityId, String action,
                Map<String, Object> attributes) {
            this.lastAuditAction = action;
            return Promise.of("audit-id");
        }

        @Override
        public Promise<Boolean> verifyConsent(DmOperationContext ctx, String subjectId, String purpose) {
            return Promise.of(true);
        }

        @Override
        public Promise<String> requestApproval(
                DmOperationContext ctx,
                String operationType,
                String subjectId,
                String description) {
            return Promise.of("approval-1");
        }

        @Override
        public Promise<Double> evaluateRisk(
                DmOperationContext context,
                String entityId,
                String riskModelId,
                Map<String, Object> factors) {
            return Promise.of(0.0);
        }

        @Override
        public Promise<Void> notifyUser(
                DmOperationContext context,
                String recipientId,
                String template,
                Map<String, String> attributes) {
            return Promise.complete();
        }
    }

    private static final class EphemeralBudgetRecommendationRepository
            implements BudgetRecommendationRepository {
        private final ConcurrentHashMap<String, BudgetRecommendation> store = new ConcurrentHashMap<>();
        private volatile String latestId;

        @Override
        public Promise<BudgetRecommendation> save(BudgetRecommendation rec) {
            store.put(rec.getRecommendationId(), rec);
            if (rec.getWorkspaceId().getValue().equals("ws-1")) {
                latestId = rec.getRecommendationId();
            }
            return Promise.of(rec);
        }

        @Override
        public Promise<Optional<BudgetRecommendation>> findLatestByWorkspace(DmWorkspaceId workspaceId) {
            return Promise.of(Optional.ofNullable(latestId).map(store::get));
        }

        @Override
        public Promise<Optional<BudgetRecommendation>> findById(String id) {
            return Promise.of(Optional.ofNullable(store.get(id)));
        }

            @Override
            public Promise<Optional<BudgetRecommendation>> findById(DmWorkspaceId workspaceId, String id) {
                BudgetRecommendation recommendation = store.get(id);
                if (recommendation == null) {
                    return Promise.of(Optional.empty());
                }
                if (!recommendation.getWorkspaceId().equals(workspaceId)) {
                    return Promise.of(Optional.empty());
                }
                return Promise.of(Optional.of(recommendation));
            }
    }
}
