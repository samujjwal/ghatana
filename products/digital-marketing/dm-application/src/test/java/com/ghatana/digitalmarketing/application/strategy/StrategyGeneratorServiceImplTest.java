package com.ghatana.digitalmarketing.application.strategy;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.DmosFeatureDisabledException;
import com.ghatana.digitalmarketing.domain.strategy.MarketingStrategy;
import com.ghatana.digitalmarketing.domain.strategy.StrategyStatus;
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

@DisplayName("StrategyGeneratorServiceImpl")
class StrategyGeneratorServiceImplTest extends EventloopTestBase {

    private RecordingKernelAdapter kernelAdapter;
    private InMemoryStrategyRepository repository;
    private StrategyGeneratorServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        kernelAdapter = new RecordingKernelAdapter();
        repository = new InMemoryStrategyRepository();
        service = new StrategyGeneratorServiceImpl(kernelAdapter, repository);
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("owner-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    private static StrategyGeneratorService.GenerateStrategyCommand validCommand() {
        return new StrategyGeneratorService.GenerateStrategyCommand(
            90, "New York", 3000, 3, true, 5, 2, "HVAC Installation"
        );
    }

    @Test
    @DisplayName("generates strategy in DRAFT status with 3 channel plans")
    void shouldGenerateStrategy() {
        MarketingStrategy strategy = runPromise(() -> service.generateStrategy(ctx, validCommand()));

        assertThat(strategy.getStatus()).isEqualTo(StrategyStatus.DRAFT);
        assertThat(strategy.getWorkspaceId()).isEqualTo(DmWorkspaceId.of("ws-1"));
        assertThat(strategy.getChannelPlans()).hasSize(3);
        assertThat(strategy.getModelVersion()).isEqualTo(StrategyGeneratorServiceImpl.MODEL_VERSION);
        assertThat(kernelAdapter.lastAuditAction).isEqualTo("strategy-generated");
    }

    @Test
    @DisplayName("generateStrategy throws DmosFeatureDisabledException when AI is disabled")
    void shouldThrowFeatureDisabledWhenAiDisabled() {
        kernelAdapter.aiFeatureEnabled = false;

        assertThatExceptionOfType(DmosFeatureDisabledException.class)
            .isThrownBy(() -> runPromise(() -> service.generateStrategy(ctx, validCommand())))
            .withMessageContaining("dmos.ai.enabled");
    }

    @Test
    @DisplayName("generated strategy includes tracking-setup goal when tracking gaps detected")
    void shouldIncludeTrackingGoalWhenGapsDetected() {
        MarketingStrategy strategy = runPromise(() -> service.generateStrategy(ctx, validCommand()));

        boolean hasTrackingGoal = strategy.getGoals().stream()
            .anyMatch(g -> "tracking-setup".equals(g.goalType()));
        assertThat(hasTrackingGoal).isTrue();
    }

    @Test
    @DisplayName("no tracking-setup goal when no gaps detected")
    void shouldNotIncludeTrackingGoalWhenNoGaps() {
        StrategyGeneratorService.GenerateStrategyCommand cmd = new StrategyGeneratorService.GenerateStrategyCommand(
            80, "Boston", 2000, 1, false, 3, 1, "Plumbing"
        );
        MarketingStrategy strategy = runPromise(() -> service.generateStrategy(ctx, cmd));

        boolean hasTrackingGoal = strategy.getGoals().stream()
            .anyMatch(g -> "tracking-setup".equals(g.goalType()));
        assertThat(hasTrackingGoal).isFalse();
    }

    @Test
    @DisplayName("no keyword goal when opportunity count is zero")
    void shouldNotIncludeKeywordGoalWhenNoOpportunities() {
        StrategyGeneratorService.GenerateStrategyCommand cmd = new StrategyGeneratorService.GenerateStrategyCommand(
            80, "Boston", 2000, 0, false, 0, 0, "Plumbing"
        );
        MarketingStrategy strategy = runPromise(() -> service.generateStrategy(ctx, cmd));

        boolean hasKeywordGoal = strategy.getGoals().stream()
            .anyMatch(g -> "keyword-targeting".equals(g.goalType()));
        assertThat(hasKeywordGoal).isFalse();
    }

    @Test
    @DisplayName("getLatestStrategy retrieves the most recently generated strategy")
    void shouldGetLatestStrategy() {
        runPromise(() -> service.generateStrategy(ctx, validCommand()));

        MarketingStrategy latest = runPromise(() -> service.getLatestStrategy(ctx));
        assertThat(latest.getWorkspaceId()).isEqualTo(DmWorkspaceId.of("ws-1"));
    }

    @Test
    @DisplayName("getLatestStrategy throws NoSuchElementException when nothing exists")
    void shouldThrowWhenNoStrategyExists() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.getLatestStrategy(ctx)));
    }

    @Test
    @DisplayName("submitForApproval transitions DRAFT to PENDING_APPROVAL")
    void shouldSubmitForApproval() {
        MarketingStrategy draft = runPromise(() -> service.generateStrategy(ctx, validCommand()));

        MarketingStrategy pending = runPromise(() -> service.submitForApproval(ctx, draft.getStrategyId()));

        assertThat(pending.getStatus()).isEqualTo(StrategyStatus.PENDING_APPROVAL);
    }

    @Test
    @DisplayName("submitForApproval throws NoSuchElementException when strategy not found")
    void shouldThrowWhenSubmittingUnknownStrategy() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.submitForApproval(ctx, "unknown-strat")));
    }

    @Test
    @DisplayName("approveStrategy transitions PENDING_APPROVAL to APPROVED and audits")
    void shouldApproveStrategy() {
        MarketingStrategy draft = runPromise(() -> service.generateStrategy(ctx, validCommand()));
        runPromise(() -> service.submitForApproval(ctx, draft.getStrategyId()));

        MarketingStrategy approved = runPromise(() -> service.approveStrategy(ctx, draft.getStrategyId()));

        assertThat(approved.getStatus()).isEqualTo(StrategyStatus.APPROVED);
        assertThat(approved.getApprovedBy()).isEqualTo("owner-1"); // getPrincipalId() returns the actor ID
        assertThat(approved.getApprovedAt()).isNotNull();
        assertThat(kernelAdapter.lastAuditAction).isEqualTo("strategy-approved");
    }

    @Test
    @DisplayName("approveStrategy throws NoSuchElementException when strategy not found")
    void shouldThrowWhenApprovingUnknownStrategy() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.approveStrategy(ctx, "no-such-strat")));
    }

    @Test
    @DisplayName("unauthorized operations throw SecurityException")
    void shouldRejectUnauthorizedOperations() {
        kernelAdapter.authorized = false;

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.generateStrategy(ctx, validCommand())));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.getLatestStrategy(ctx)));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.submitForApproval(ctx, "strat-1")));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.approveStrategy(ctx, "strat-1")));
    }

    @Test
    @DisplayName("constructor rejects null dependencies")
    void shouldRejectNullDependencies() {
        assertThatThrownBy(() -> new StrategyGeneratorServiceImpl(null, repository))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new StrategyGeneratorServiceImpl(kernelAdapter, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("generateStrategy and getLatestStrategy reject null ctx or command")
    void shouldRejectNullInputs() {
        assertThatThrownBy(() -> runPromise(() -> service.generateStrategy(null, validCommand())))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("ctx");
        assertThatThrownBy(() -> runPromise(() -> service.generateStrategy(ctx, null)))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("command");
        assertThatThrownBy(() -> runPromise(() -> service.getLatestStrategy(null)))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("ctx");
    }

    @Test
    @DisplayName("command rejects invalid inputs")
    void shouldRejectInvalidCommandInputs() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new StrategyGeneratorService.GenerateStrategyCommand(
                -1, "NY", 1000, 0, false, 0, 0, "Offer"));
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new StrategyGeneratorService.GenerateStrategyCommand(
                101, "NY", 1000, 0, false, 0, 0, "Offer"));
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new StrategyGeneratorService.GenerateStrategyCommand(
                50, " ", 1000, 0, false, 0, 0, "Offer"));
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new StrategyGeneratorService.GenerateStrategyCommand(
                50, "NY", -1, 0, false, 0, 0, "Offer"));
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new StrategyGeneratorService.GenerateStrategyCommand(
                50, "NY", 1000, 0, false, 0, 0, " "));
    }

    // ---- test doubles ----

    private static final class InMemoryStrategyRepository implements MarketingStrategyRepository {
        private final ConcurrentHashMap<String, MarketingStrategy> store = new ConcurrentHashMap<>();
        private String latestKey;

        @Override
        public Promise<MarketingStrategy> save(MarketingStrategy strategy) {
            store.put(strategy.getStrategyId(), strategy);
            latestKey = strategy.getStrategyId();
            // also overwrite latest for workspace
            store.put("latest:" + strategy.getWorkspaceId().getValue(), strategy);
            return Promise.of(strategy);
        }

        @Override
        public Promise<Optional<MarketingStrategy>> findLatestByWorkspace(DmWorkspaceId workspaceId) {
            return Promise.of(Optional.ofNullable(store.get("latest:" + workspaceId.getValue())));
        }

        @Override
        public Promise<Optional<MarketingStrategy>> findById(String strategyId) {
            return Promise.of(Optional.ofNullable(store.get(strategyId)));
        }
    }

    private static final class RecordingKernelAdapter implements DigitalMarketingKernelAdapter {
        boolean authorized = true;
        boolean aiFeatureEnabled = true;
        String lastAuditAction;

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public Promise<Boolean> isAuthorized(DmOperationContext context, String resource, String action) {
            return Promise.of(authorized);
        }

        @Override
        public Promise<Boolean> isFeatureEnabled(DmOperationContext context, String flagKey) {
            return Promise.of(aiFeatureEnabled);
        }

        @Override
        public Promise<Boolean> verifyConsent(DmOperationContext context, String subjectId, String purpose) {
            return Promise.of(true);
        }

        @Override
        public Promise<String> requestApproval(
                DmOperationContext context,
                String operationType,
                String subjectId,
                String description) {
            return Promise.of("approval-1");
        }

        @Override
        public Promise<String> recordAudit(
                DmOperationContext context,
                String entityId,
                String action,
                Map<String, Object> attributes) {
            lastAuditAction = action;
            return Promise.of("audit-1");
        }
    }
}
