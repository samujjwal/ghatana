package com.ghatana.digitalmarketing.application.preflight;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.preflight.DmPreflightCheckResult;
import com.ghatana.digitalmarketing.domain.preflight.DmPreflightChecklist;
import com.ghatana.digitalmarketing.domain.preflight.DmPreflightStatus;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("DmPreflightChecklistServiceImpl")
class DmPreflightChecklistServiceImplTest extends EventloopTestBase {

    private InMemoryPreflightRepository repository;
    private DmPreflightChecklistServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPreflightRepository();
        service = new DmPreflightChecklistServiceImpl(repository, new StubKernelAdapter(true));
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    @Test
    @DisplayName("evaluate with all passing items returns PASSED status")
    void evaluateAllPassed() {
        DmPreflightChecklist result = runPromise(() -> service.evaluate(ctx, passingCommand()));

        assertThat(result.getStatus()).isEqualTo(DmPreflightStatus.PASSED);
        assertThat(result.allRequiredPassed()).isTrue();
        assertThat(result.getTenantId()).isEqualTo("tenant-1");
    }

    @Test
    @DisplayName("evaluate with failed required item returns BLOCKED status")
    void evaluateRequiredFailed() {
        DmPreflightChecklist.DmPreflightCheckItem failedItem =
            new DmPreflightChecklist.DmPreflightCheckItem("budget-check", "Budget validation", true, DmPreflightCheckResult.FAILED, "Over budget");

        DmPreflightChecklistService.EvaluatePreflightCommand cmd =
            new DmPreflightChecklistService.EvaluatePreflightCommand("campaign-1", List.of(failedItem));

        DmPreflightChecklist result = runPromise(() -> service.evaluate(ctx, cmd));
        assertThat(result.getStatus()).isEqualTo(DmPreflightStatus.BLOCKED);
    }

    @Test
    @DisplayName("evaluate with warning item returns WARNING status")
    void evaluateWarning() {
        DmPreflightChecklist.DmPreflightCheckItem warnItem =
            new DmPreflightChecklist.DmPreflightCheckItem("cta-check", "CTA review", false, DmPreflightCheckResult.WARNING, "Weak CTA");

        DmPreflightChecklistService.EvaluatePreflightCommand cmd =
            new DmPreflightChecklistService.EvaluatePreflightCommand("campaign-1", List.of(warnItem));

        DmPreflightChecklist result = runPromise(() -> service.evaluate(ctx, cmd));
        assertThat(result.getStatus()).isEqualTo(DmPreflightStatus.WARNING);
    }

    @Test
    @DisplayName("evaluate rejects unauthorized actor")
    void evaluateUnauthorized() {
        service = new DmPreflightChecklistServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.evaluate(ctx, passingCommand())));
    }

    @Test
    @DisplayName("findById enforces tenant isolation")
    void findByIdTenantIsolation() {
        DmPreflightChecklist saved = runPromise(() -> service.evaluate(ctx, passingCommand()));

        DmOperationContext other = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-2"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-2"))
            .correlationId(DmCorrelationId.of("corr-2"))
            .idempotencyKey(DmIdempotencyKey.of("idk-2"))
            .build();

        Optional<DmPreflightChecklist> result = runPromise(() -> service.findById(other, saved.getId()));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("listByCampaign returns only tenant-scoped results")
    void listByCampaignTenantScope() {
        runPromise(() -> service.evaluate(ctx, passingCommand()));
        List<DmPreflightChecklist> results = runPromise(() -> service.listByCampaign(ctx, "campaign-1"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCampaignId()).isEqualTo("campaign-1");
    }

    @Test
    @DisplayName("findLatestByCampaign returns most recent checklist")
    void findLatestByCampaign() {
        runPromise(() -> service.evaluate(ctx, passingCommand()));
        runPromise(() -> service.evaluate(ctx, passingCommand()));

        Optional<DmPreflightChecklist> latest = runPromise(() -> service.findLatestByCampaign(ctx, "campaign-1"));
        assertThat(latest).isPresent();
    }

    @Test
    @DisplayName("command validates blank campaignId")
    void commandValidatesCampaignId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmPreflightChecklistService.EvaluatePreflightCommand(
                "", List.of(passingItem())));
    }

    @Test
    @DisplayName("command validates empty items")
    void commandValidatesEmptyItems() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmPreflightChecklistService.EvaluatePreflightCommand("campaign-1", List.of()));
    }

    private DmPreflightChecklistService.EvaluatePreflightCommand passingCommand() {
        return new DmPreflightChecklistService.EvaluatePreflightCommand("campaign-1", List.of(passingItem()));
    }

    private DmPreflightChecklist.DmPreflightCheckItem passingItem() {
        return new DmPreflightChecklist.DmPreflightCheckItem("audience-check", "Audience validated", true, DmPreflightCheckResult.PASSED, null);
    }

    static final class InMemoryPreflightRepository implements DmPreflightChecklistRepository {
        private final Map<String, DmPreflightChecklist> store = new ConcurrentHashMap<>();
        private final List<DmPreflightChecklist> ordered = new ArrayList<>();

        @Override
        public Promise<DmPreflightChecklist> save(DmPreflightChecklist c) {
            store.put(c.getId(), c);
            ordered.add(c);
            return Promise.of(c);
        }

        @Override
        public Promise<DmPreflightChecklist> update(DmPreflightChecklist c) {
            store.put(c.getId(), c);
            return Promise.of(c);
        }

        @Override
        public Promise<Optional<DmPreflightChecklist>> findById(String id) {
            return Promise.of(Optional.ofNullable(store.get(id)));
        }

        @Override
        public Promise<List<DmPreflightChecklist>> listByCampaign(String tenantId, String campaignId) {
            return Promise.of(ordered.stream()
                .filter(c -> c.getTenantId().equals(tenantId) && c.getCampaignId().equals(campaignId))
                .toList());
        }

        @Override
        public Promise<Optional<DmPreflightChecklist>> findLatestByCampaign(String tenantId, String campaignId) {
            List<DmPreflightChecklist> matches = ordered.stream()
                .filter(c -> c.getTenantId().equals(tenantId) && c.getCampaignId().equals(campaignId))
                .toList();
            return Promise.of(matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(matches.size() - 1)));
        }
    }

    static final class StubKernelAdapter implements DigitalMarketingKernelAdapter {
        private final boolean allowed;

        StubKernelAdapter(boolean allowed) { this.allowed = allowed; }

        @Override public void start() {}
        @Override public void stop() {}
        @Override public Promise<Boolean> isAuthorized(DmOperationContext ctx, String resource, String action) {
            return Promise.of(allowed);
        }
        @Override public Promise<Boolean> verifyConsent(DmOperationContext ctx, String subjectId, String purpose) {
            return Promise.of(true);
        }
        @Override public Promise<String> requestApproval(DmOperationContext ctx, String type, String subjectId, String desc) {
            return Promise.of("approval-1");
        }
        @Override public Promise<String> recordAudit(DmOperationContext ctx, String entityId, String action, Map<String, Object> meta) {
            return Promise.of("audit-1");
        }
    }
}
