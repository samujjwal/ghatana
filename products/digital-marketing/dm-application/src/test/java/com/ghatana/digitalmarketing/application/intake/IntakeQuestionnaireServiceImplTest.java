package com.ghatana.digitalmarketing.application.intake;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.intake.BusinessIntakeProfile;
import com.ghatana.digitalmarketing.domain.intake.IntakeStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("IntakeQuestionnaireServiceImpl")
class IntakeQuestionnaireServiceImplTest extends EventloopTestBase {

    private RecordingKernelAdapter kernelAdapter;
    private EphemeralRepository repository;
    private IntakeQuestionnaireServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        kernelAdapter = new RecordingKernelAdapter();
        repository = new EphemeralRepository();
        service = new IntakeQuestionnaireServiceImpl(kernelAdapter, repository);

        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("owner-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    @Test
    @DisplayName("saves and resumes intake draft")
    void shouldSaveAndResumeDraft() {
        BusinessIntakeProfile saved = runPromise(() -> service.saveDraft(
            ctx,
            new IntakeQuestionnaireService.SaveDraftCommand(
                "Acme",
                "https://acme.test",
                "SEO retainers",
                "SMBs",
                "US",
                new BigDecimal("1800"),
                List.of("comp-a.test"),
                List.of("No cold calling"),
                "Book 20 consultations",
                "MEDIUM"
            )
        ));

        BusinessIntakeProfile resumed = runPromise(() -> service.getDraft(ctx));

        assertThat(saved.getStatus()).isEqualTo(IntakeStatus.DRAFT);
        assertThat(resumed.getBusinessName()).isEqualTo("Acme");
        assertThat(resumed.getCompetitorDomains()).containsExactly("comp-a.test");
    }

    @Test
    @DisplayName("submits intake with summary unknowns and audit event")
    void shouldSubmitIntake() {
        runPromise(() -> service.saveDraft(
            ctx,
            new IntakeQuestionnaireService.SaveDraftCommand(
                "Acme",
                "https://acme.test",
                "SEO retainers",
                "SMBs",
                "US",
                new BigDecimal("1800"),
                List.of("comp-a.test"),
                List.of("No cold calling"),
                "Book 20 consultations",
                "MEDIUM"
            )
        ));

        BusinessIntakeProfile submitted = runPromise(() -> service.submitIntake(
            ctx,
            new IntakeQuestionnaireService.SubmitIntakeCommand(
                "Use conversion-first landing pages and local-intent keywords.",
                0.81,
                List.of("No historic CAC data")
            )
        ));

        assertThat(submitted.getStatus()).isEqualTo(IntakeStatus.SUBMITTED);
        assertThat(submitted.getAiUnknowns()).containsExactly("No historic CAC data");
        assertThat(kernelAdapter.auditActions).contains("intake-submitted");
    }

    @Test
    @DisplayName("rejects submit when critical inputs are missing")
    void shouldRejectSubmitWhenCriticalInputsMissing() {
        runPromise(() -> service.saveDraft(
            ctx,
            new IntakeQuestionnaireService.SaveDraftCommand(
                "Acme",
                "",
                "",
                "",
                "",
                null,
                List.of(),
                List.of(),
                "",
                ""
            )
        ));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.submitIntake(
                ctx,
                new IntakeQuestionnaireService.SubmitIntakeCommand("summary", 0.5, List.of("unknown"))
            )));
    }

    @Test
    @DisplayName("rejects unauthorized write and read operations")
    void shouldRejectUnauthorizedOperations() {
        kernelAdapter.authorized = false;

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.saveDraft(
                ctx,
                new IntakeQuestionnaireService.SaveDraftCommand("Acme", "", "", "", "", null, List.of(), List.of(), "", "")
            )));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.getDraft(ctx)));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.submitIntake(
                ctx,
                new IntakeQuestionnaireService.SubmitIntakeCommand("summary", 0.4, List.of())
            )));
    }

    @Test
    @DisplayName("constructor rejects null dependencies")
    void shouldRejectNullDependencies() {
        assertThatThrownBy(() -> new IntakeQuestionnaireServiceImpl(null, repository))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IntakeQuestionnaireServiceImpl(kernelAdapter, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("submit fails when draft is missing")
    void shouldFailSubmitWhenDraftMissing() {
        assertThatExceptionOfType(java.util.NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.submitIntake(
                ctx,
                new IntakeQuestionnaireService.SubmitIntakeCommand("summary", 0.5, List.of())
            )));
    }

    private static final class EphemeralRepository implements IntakeQuestionnaireRepository {
        private final ConcurrentHashMap<String, BusinessIntakeProfile> store = new ConcurrentHashMap<>();

        @Override
        public Promise<BusinessIntakeProfile> save(BusinessIntakeProfile profile) {
            store.put(profile.getWorkspaceId().getValue(), profile);
            return Promise.of(profile);
        }

        @Override
        public Promise<Optional<BusinessIntakeProfile>> findByWorkspaceId(DmWorkspaceId workspaceId) {
            return Promise.of(Optional.ofNullable(store.get(workspaceId.getValue())));
        }
    }

    private static final class RecordingKernelAdapter implements DigitalMarketingKernelAdapter {
        private boolean authorized = true;
        private final List<String> auditActions = new java.util.concurrent.CopyOnWriteArrayList<>();

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
            auditActions.add(action);
            return Promise.of("audit-1");
        }
    }
}
