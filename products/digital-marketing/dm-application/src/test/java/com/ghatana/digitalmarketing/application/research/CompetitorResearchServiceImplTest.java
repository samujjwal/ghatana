package com.ghatana.digitalmarketing.application.research;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.research.CompetitorResearchSnapshot;
import com.ghatana.digitalmarketing.domain.research.KeywordIntent;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CompetitorResearchServiceImpl")
class CompetitorResearchServiceImplTest extends EventloopTestBase {

    private RecordingKernelAdapter kernelAdapter;
    private InMemoryRepository repository;
    private CompetitorResearchServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        kernelAdapter = new RecordingKernelAdapter();
        repository = new InMemoryRepository();
        service = new CompetitorResearchServiceImpl(kernelAdapter, repository);

        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("owner-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    @Test
    @DisplayName("generates research snapshot with competitor findings and keywords")
    void shouldGenerateResearchWithCompetitors() {
        CompetitorResearchSnapshot snap = runPromise(() -> service.runResearch(
            ctx,
            new CompetitorResearchService.RunCompetitorResearchCommand(
                List.of("rival.com", "other.com"),
                "New York",
                "plumbing services"
            )
        ));

        assertThat(snap.getCompetitorFindings()).hasSize(2);
        assertThat(snap.getKeywordFindings()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(snap.getOpportunitySummary()).contains("competitor(s)");
        assertThat(kernelAdapter.auditActions).contains("competitor-research-generated");
    }

    @Test
    @DisplayName("generates fallback finding when no competitors provided")
    void shouldHandleNoCompetitors() {
        CompetitorResearchSnapshot snap = runPromise(() -> service.runResearch(
            ctx,
            new CompetitorResearchService.RunCompetitorResearchCommand(
                List.of(),
                "Boston",
                "HVAC repair"
            )
        ));

        assertThat(snap.getCompetitorFindings()).hasSize(1);
        assertThat(snap.getCompetitorFindings().get(0).competitorDomain()).isEqualTo("unknown");
        assertThat(snap.getOpportunitySummary()).contains("No direct competitors");
    }

    @Test
    @DisplayName("keywords include transactional and commercial investigation intents")
    void shouldIncludeMixedKeywordIntents() {
        CompetitorResearchSnapshot snap = runPromise(() -> service.runResearch(
            ctx,
            new CompetitorResearchService.RunCompetitorResearchCommand(
                List.of(),
                "Chicago",
                "roofing"
            )
        ));

        assertThat(snap.getKeywordFindings())
            .anyMatch(k -> k.intent() == KeywordIntent.TRANSACTIONAL)
            .anyMatch(k -> k.intent() == KeywordIntent.COMMERCIAL_INVESTIGATION);
    }

    @Test
    @DisplayName("all keyword findings have non-blank source")
    void shouldHaveSourceOnAllKeywords() {
        CompetitorResearchSnapshot snap = runPromise(() -> service.runResearch(
            ctx,
            new CompetitorResearchService.RunCompetitorResearchCommand(
                List.of("acme.com"),
                "Seattle",
                "electrician"
            )
        ));

        snap.getKeywordFindings().forEach(k -> assertThat(k.source()).isNotBlank());
        snap.getCompetitorFindings().forEach(c -> assertThat(c.source()).isNotBlank());
    }

    @Test
    @DisplayName("getLatestResearch returns most recent snapshot")
    void shouldGetLatestResearch() {
        runPromise(() -> service.runResearch(
            ctx,
            new CompetitorResearchService.RunCompetitorResearchCommand(
                List.of("rival.com"),
                "Miami",
                "landscaping"
            )
        ));

        CompetitorResearchSnapshot latest = runPromise(() -> service.getLatestResearch(ctx));
        assertThat(latest.getGeneratedBy()).isEqualTo("owner-1");
    }

    @Test
    @DisplayName("getLatestResearch throws NoSuchElementException when nothing stored")
    void shouldThrowWhenNoSnapshotExists() {
        DmOperationContext other = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-99"))
            .actor(ActorRef.user("owner-1"))
            .correlationId(DmCorrelationId.of("corr-2"))
            .idempotencyKey(DmIdempotencyKey.of("idk-2"))
            .build();

        assertThatExceptionOfType(java.util.NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.getLatestResearch(other)));
    }

    @Test
    @DisplayName("rejects unauthorized read and write operations")
    void shouldRejectUnauthorizedOperations() {
        kernelAdapter.authorized = false;

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.runResearch(
                ctx,
                new CompetitorResearchService.RunCompetitorResearchCommand(
                    List.of(), "Austin", "pest control"
                )
            )));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.getLatestResearch(ctx)));
    }

    @Test
    @DisplayName("constructor rejects null dependencies")
    void shouldRejectNullDependencies() {
        assertThatThrownBy(() -> new CompetitorResearchServiceImpl(null, repository))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CompetitorResearchServiceImpl(kernelAdapter, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("runResearch and getLatestResearch reject null context and command")
    void shouldRejectNullInputs() {
        CompetitorResearchService.RunCompetitorResearchCommand cmd =
            new CompetitorResearchService.RunCompetitorResearchCommand(List.of(), "Dallas", "fencing");

        assertThatThrownBy(() -> runPromise(() -> service.runResearch(null, cmd)))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("ctx");

        assertThatThrownBy(() -> runPromise(() -> service.runResearch(ctx, null)))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("command");

        assertThatThrownBy(() -> runPromise(() -> service.getLatestResearch(null)))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("ctx");
    }

    @Test
    @DisplayName("command rejects blank serviceArea and primaryOffer")
    void shouldRejectInvalidCommandInputs() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new CompetitorResearchService.RunCompetitorResearchCommand(
                List.of(), " ", "fencing"
            ));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new CompetitorResearchService.RunCompetitorResearchCommand(
                List.of(), "Dallas", " "
            ));
    }

    @Test
    @DisplayName("null competitorDomains list is treated as empty")
    void shouldNormaliseNullCompetitorDomainsList() {
        CompetitorResearchSnapshot snap = runPromise(() -> service.runResearch(
            ctx,
            new CompetitorResearchService.RunCompetitorResearchCommand(
                null,
                "Phoenix",
                "solar installation"
            )
        ));

        assertThat(snap.getCompetitorFindings()).hasSize(1);
        assertThat(snap.getCompetitorFindings().get(0).competitorDomain()).isEqualTo("unknown");
    }

    // ---- test doubles ----

    private static final class InMemoryRepository implements CompetitorResearchRepository {
        private final ConcurrentHashMap<String, CompetitorResearchSnapshot> store = new ConcurrentHashMap<>();

        @Override
        public Promise<CompetitorResearchSnapshot> save(CompetitorResearchSnapshot snapshot) {
            store.put(snapshot.getWorkspaceId().getValue(), snapshot);
            return Promise.of(snapshot);
        }

        @Override
        public Promise<Optional<CompetitorResearchSnapshot>> findLatestByWorkspace(DmWorkspaceId workspaceId) {
            return Promise.of(Optional.ofNullable(store.get(workspaceId.getValue())));
        }
    }

    private static final class RecordingKernelAdapter implements DigitalMarketingKernelAdapter {
        private boolean authorized = true;
        private final java.util.List<String> auditActions = new java.util.concurrent.CopyOnWriteArrayList<>();

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
