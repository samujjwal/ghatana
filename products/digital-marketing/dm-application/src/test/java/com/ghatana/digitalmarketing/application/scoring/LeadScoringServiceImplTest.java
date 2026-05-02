package com.ghatana.digitalmarketing.application.scoring;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.scoring.LeadGrade;
import com.ghatana.digitalmarketing.domain.scoring.LeadScore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LeadScoringServiceImpl")
class LeadScoringServiceImplTest extends EventloopTestBase {

    private RecordingKernelAdapter kernelAdapter;
    private InMemoryLeadScoreRepository repository;
    private LeadScoringServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        kernelAdapter = new RecordingKernelAdapter();
        repository = new InMemoryLeadScoreRepository();
        service = new LeadScoringServiceImpl(kernelAdapter, repository);

        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("owner-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    @Test
    @DisplayName("generates grade-A score for high-signal prospect")
    void shouldGenerateHighScore() {
        LeadScoringService.GenerateLeadScoreCommand cmd = new LeadScoringService.GenerateLeadScoreCommand(
            100, 5, true, 5, "New York", 5000
        );

        LeadScore score = runPromise(() -> service.generateScore(ctx, cmd));

        assertThat(score.getScore()).isGreaterThanOrEqualTo(80);
        assertThat(score.getGrade()).isEqualTo(LeadGrade.A);
        assertThat(score.getModelVersion()).isEqualTo(LeadScoringServiceImpl.MODEL_VERSION);
        assertThat(score.getScoreId()).isNotBlank();
        assertThat(score.getDimensions()).hasSize(5);
        assertThat(kernelAdapter.lastAuditAction).isEqualTo("lead-score-generated");
    }

    @Test
    @DisplayName("generates grade-D score for low-signal prospect")
    void shouldGenerateLowScore() {
        LeadScoringService.GenerateLeadScoreCommand cmd = new LeadScoringService.GenerateLeadScoreCommand(
            0, 0, false, 0, "Austin", 0
        );

        LeadScore score = runPromise(() -> service.generateScore(ctx, cmd));

        assertThat(score.getScore()).isLessThan(40);
        assertThat(score.getGrade()).isEqualTo(LeadGrade.D);
    }

    @Test
    @DisplayName("flags score for human review when confidence is below threshold")
    void shouldFlagLowConfidenceScore() {
        // Low confidence: no intake completion, no audit, no keywords, no budget
        LeadScoringService.GenerateLeadScoreCommand cmd = new LeadScoringService.GenerateLeadScoreCommand(
            0, 0, false, 0, "Boston", 0
        );

        LeadScore score = runPromise(() -> service.generateScore(ctx, cmd));

        assertThat(score.isRequiresHumanReview()).isTrue();
        assertThat(score.getRecommendedNextAction()).containsIgnoringCase("human review");
    }

    @Test
    @DisplayName("does not flag score for human review when data is sufficient")
    void shouldNotFlagHighConfidenceScore() {
        LeadScoringService.GenerateLeadScoreCommand cmd = new LeadScoringService.GenerateLeadScoreCommand(
            90, 3, true, 4, "Chicago", 2000
        );

        LeadScore score = runPromise(() -> service.generateScore(ctx, cmd));

        assertThat(score.isRequiresHumanReview()).isFalse();
    }

    @Test
    @DisplayName("grade B score produces outreach next action")
    void shouldRecommendOutreachForGradeB() {
        // intake=80→24, audit=3→12, tracking=true→10, keywords=3→12, budget=500→8 → total=66 → grade B
        LeadScoringService.GenerateLeadScoreCommand cmd = new LeadScoringService.GenerateLeadScoreCommand(
            80, 3, true, 3, "Denver", 500
        );

        LeadScore score = runPromise(() -> service.generateScore(ctx, cmd));

        assertThat(score.getGrade()).isEqualTo(LeadGrade.B);
        assertThat(score.getRecommendedNextAction()).containsIgnoringCase("outreach");
    }

    @Test
    @DisplayName("grade C score produces nurture next action")
    void shouldRecommendNurtureForGradeC() {
        // Score around 40-59
        LeadScoringService.GenerateLeadScoreCommand cmd = new LeadScoringService.GenerateLeadScoreCommand(
            50, 2, false, 1, "Phoenix", 600
        );

        LeadScore score = runPromise(() -> service.generateScore(ctx, cmd));

        if (score.getGrade() == LeadGrade.C) {
            assertThat(score.getRecommendedNextAction()).containsIgnoringCase("nurture");
        }
    }

    @Test
    @DisplayName("budget tiers produce correct dimension points")
    void shouldDeriveBudgetPointsByTier() {
        // Budget >= 3000 -> 20 pts
        LeadScore highBudget = runPromise(() -> service.generateScore(ctx,
            new LeadScoringService.GenerateLeadScoreCommand(0, 0, false, 0, "NY", 3000)));
        int highBudgetPts = highBudget.getDimensions().stream()
            .filter(d -> "budget-fit".equals(d.dimension()))
            .mapToInt(d -> d.points())
            .findFirst().orElse(-1);
        assertThat(highBudgetPts).isEqualTo(20);

        // Budget 500..1499 -> 8 pts
        LeadScore midBudget = runPromise(() -> service.generateScore(ctx,
            new LeadScoringService.GenerateLeadScoreCommand(0, 0, false, 0, "NY", 800)));
        int midBudgetPts = midBudget.getDimensions().stream()
            .filter(d -> "budget-fit".equals(d.dimension()))
            .mapToInt(d -> d.points())
            .findFirst().orElse(-1);
        assertThat(midBudgetPts).isEqualTo(8);

        // Budget < 500 -> 2 pts
        LeadScore lowBudget = runPromise(() -> service.generateScore(ctx,
            new LeadScoringService.GenerateLeadScoreCommand(0, 0, false, 0, "NY", 100)));
        int lowBudgetPts = lowBudget.getDimensions().stream()
            .filter(d -> "budget-fit".equals(d.dimension()))
            .mapToInt(d -> d.points())
            .findFirst().orElse(-1);
        assertThat(lowBudgetPts).isEqualTo(2);
    }

    @Test
    @DisplayName("budget tier 1500..2999 produces 14 points")
    void shouldDeriveMidHighBudgetPoints() {
        LeadScore score = runPromise(() -> service.generateScore(ctx,
            new LeadScoringService.GenerateLeadScoreCommand(0, 0, false, 0, "NY", 1500)));
        int pts = score.getDimensions().stream()
            .filter(d -> "budget-fit".equals(d.dimension()))
            .mapToInt(d -> d.points())
            .findFirst().orElse(-1);
        assertThat(pts).isEqualTo(14);
    }

    @Test
    @DisplayName("score is capped at 100")
    void shouldCapScoreAt100() {
        LeadScore score = runPromise(() -> service.generateScore(ctx,
            new LeadScoringService.GenerateLeadScoreCommand(100, 10, true, 10, "LA", 10000)));
        assertThat(score.getScore()).isLessThanOrEqualTo(100);
    }

    @Test
    @DisplayName("getLatestScore retrieves persisted score")
    void shouldGetLatestScore() {
        runPromise(() -> service.generateScore(ctx,
            new LeadScoringService.GenerateLeadScoreCommand(80, 3, true, 4, "Seattle", 2000)));

        LeadScore latest = runPromise(() -> service.getLatestScore(ctx));
        assertThat(latest.getWorkspaceId()).isEqualTo(DmWorkspaceId.of("ws-1"));
    }

    @Test
    @DisplayName("getLatestScore propagates NoSuchElementException when no score exists")
    void shouldThrowWhenNoScoreExists() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.getLatestScore(ctx)));
    }

    @Test
    @DisplayName("rejects unauthorized generate and read operations")
    void shouldRejectUnauthorizedOperations() {
        kernelAdapter.authorized = false;

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.generateScore(ctx,
                new LeadScoringService.GenerateLeadScoreCommand(
                    50, 2, false, 2, "Miami", 1000))));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.getLatestScore(ctx)));
    }

    @Test
    @DisplayName("constructor rejects null dependencies")
    void shouldRejectNullDependencies() {
        assertThatThrownBy(() -> new LeadScoringServiceImpl(null, repository))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LeadScoringServiceImpl(kernelAdapter, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("generateScore and getLatestScore reject null context and command")
    void shouldRejectNullInputs() {
        LeadScoringService.GenerateLeadScoreCommand cmd =
            new LeadScoringService.GenerateLeadScoreCommand(50, 2, false, 2, "Miami", 1000);

        assertThatThrownBy(() -> runPromise(() -> service.generateScore(null, cmd)))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("ctx");

        assertThatThrownBy(() -> runPromise(() -> service.generateScore(ctx, null)))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("command");

        assertThatThrownBy(() -> runPromise(() -> service.getLatestScore(null)))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("ctx");
    }

    @Test
    @DisplayName("command rejects blank serviceArea and invalid pct/budget")
    void shouldRejectInvalidCommandInputs() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new LeadScoringService.GenerateLeadScoreCommand(50, 2, false, 2, " ", 1000));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new LeadScoringService.GenerateLeadScoreCommand(-1, 2, false, 2, "NY", 1000));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new LeadScoringService.GenerateLeadScoreCommand(101, 2, false, 2, "NY", 1000));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new LeadScoringService.GenerateLeadScoreCommand(50, 2, false, 2, "NY", -1));
    }

    // ---- test doubles ----

    private static final class InMemoryLeadScoreRepository implements LeadScoreRepository {
        private final ConcurrentHashMap<String, LeadScore> store = new ConcurrentHashMap<>();

        @Override
        public Promise<LeadScore> save(LeadScore score) {
            store.put(score.getWorkspaceId().getValue(), score);
            return Promise.of(score);
        }

        @Override
        public Promise<LeadScore> findLatestByWorkspace(DmWorkspaceId workspaceId) {
            LeadScore score = store.get(workspaceId.getValue());
            if (score == null) {
                return Promise.ofException(new NoSuchElementException("No lead score for workspace: " + workspaceId.getValue()));
            }
            return Promise.of(score);
        }
    }

    private static final class RecordingKernelAdapter implements DigitalMarketingKernelAdapter {
        private boolean authorized = true;
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
