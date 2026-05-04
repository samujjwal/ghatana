package com.ghatana.digitalmarketing.application.governance;

import com.ghatana.digitalmarketing.application.command.DmCommandRepository;
import com.ghatana.digitalmarketing.application.command.DmCommandService;
import com.ghatana.digitalmarketing.application.command.DmCommandServiceImpl;
import com.ghatana.digitalmarketing.application.transparency.AiActionLogRepository;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.command.DmCommand;
import com.ghatana.digitalmarketing.domain.command.DmCommandStatus;
import com.ghatana.digitalmarketing.domain.command.DmCommandType;
import com.ghatana.digitalmarketing.domain.transparency.AiActionLogEntry;
import com.ghatana.digitalmarketing.domain.transparency.AiActionStatus;
import com.ghatana.digitalmarketing.domain.transparency.AiActionType;
import com.ghatana.digitalmarketing.infra.transparency.InMemoryAiActionLogRepository;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.compliance.CompliancePlugin;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for RecommendationToCommandGateway (DMOS-P1-020).
 *
 * @doc.type test
 * @doc.purpose Verify recommendation-to-command gateway with governance
 * @doc.layer application
 */
@DisplayName("RecommendationToCommandGateway")
class RecommendationToCommandGatewayTest extends EventloopTestBase {

    private InMemoryCommandRepository commandRepository;
    private DmCommandService commandService;
    private AiActionLogRepository aiActionLogRepository;
    private RecommendationToCommandGateway gateway;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        commandRepository = new InMemoryCommandRepository();
        commandService = new DmCommandServiceImpl(commandRepository, new AllowingKernelAdapter(true));
        aiActionLogRepository = new InMemoryAiActionLogRepository();
        gateway = new RecommendationToCommandGateway(commandService, aiActionLogRepository, new AllowingCompliancePlugin(true));
    }

    @Test
    @DisplayName("convertToCommand blocks null recommendation")
    void convertToCommand_blocksNullRecommendation() {

        RecommendationToCommandGateway.GatewayResult result = runPromise(() -> gateway.convertToCommand(
            null,
            DmTenantId.of("tenant-123"),
            DmWorkspaceId.of("workspace-456"),
            "user-789"
        ));

        assertThat(result.status()).isEqualTo(RecommendationToCommandGateway.GatewayStatus.BLOCKED);
        assertThat(result.reason()).contains("null");
    }

    @Test
    @DisplayName("convertToCommand blocks recommendation with missing target type")
    void convertToCommand_blocksRecommendationWithMissingTargetType() {
        RecommendationToCommandGateway.Recommendation recommendation = new RecommendationToCommandGateway.Recommendation(
            "strategy-generator",
            null,
            "target-123",
            "Generate a strategy",
            "gpt-4",
            "Strategy output",
            0.95,
            "kernel://evidence/123",
            500
        );

        RecommendationToCommandGateway.GatewayResult result = runPromise(() -> gateway.convertToCommand(
            recommendation,
            DmTenantId.of("tenant-123"),
            DmWorkspaceId.of("workspace-456"),
            "user-789"
        ));

        assertThat(result.status()).isEqualTo(RecommendationToCommandGateway.GatewayStatus.BLOCKED);
        assertThat(result.reason()).contains("Target type is required");
    }

    @Test
    @DisplayName("convertToCommand blocks recommendation with missing target ID")
    void convertToCommand_blocksRecommendationWithMissingTargetId() {
        RecommendationToCommandGateway.Recommendation recommendation = new RecommendationToCommandGateway.Recommendation(
            "strategy-generator",
            "STRATEGY",
            "",
            "Generate a strategy",
            "gpt-4",
            "Strategy output",
            0.95,
            "kernel://evidence/123",
            500
        );

        RecommendationToCommandGateway.GatewayResult result = runPromise(() -> gateway.convertToCommand(
            recommendation,
            DmTenantId.of("tenant-123"),
            DmWorkspaceId.of("workspace-456"),
            "user-789"
        ));

        assertThat(result.status()).isEqualTo(RecommendationToCommandGateway.GatewayStatus.BLOCKED);
        assertThat(result.reason()).contains("Target ID is required");
    }

    @Test
    @DisplayName("convertToCommand requires approval for high-risk recommendation")
    void convertToCommand_requiresApprovalForHighRiskRecommendation() {
        RecommendationToCommandGateway.Recommendation recommendation = new RecommendationToCommandGateway.Recommendation(
            "strategy-generator",
            "STRATEGY",
            "target-123",
            "Generate a strategy",
            "gpt-4",
            "Strategy output",
            0.65, // Low confidence -> high risk
            "kernel://evidence/123",
            500
        );

        RecommendationToCommandGateway.GatewayResult result = runPromise(() -> gateway.convertToCommand(
            recommendation,
            DmTenantId.of("tenant-123"),
            DmWorkspaceId.of("workspace-456"),
            "user-789"
        ));

        assertThat(result.status()).isEqualTo(RecommendationToCommandGateway.GatewayStatus.REQUIRES_APPROVAL);
        assertThat(result.reason()).contains("requires approval");
        assertThat(result.logEntryId()).isNotNull();
    }

    @Test
    @DisplayName("convertToCommand creates command for low-risk recommendation")
    void convertToCommand_createsCommandForLowRiskRecommendation() {
        RecommendationToCommandGateway.Recommendation recommendation = new RecommendationToCommandGateway.Recommendation(
            "strategy-generator",
            "CAMPAIGN",
            "target-123",
            "Generate a campaign",
            "gpt-4",
            "Campaign output",
            0.95, // High confidence -> low risk
            "kernel://evidence/123",
            500
        );

        RecommendationToCommandGateway.GatewayResult result = runPromise(() -> gateway.convertToCommand(
            recommendation,
            DmTenantId.of("tenant-123"),
            DmWorkspaceId.of("workspace-456"),
            "user-789"
        ));

        assertThat(result.status()).isEqualTo(RecommendationToCommandGateway.GatewayStatus.CREATED);
        assertThat(result.reason()).contains("Command created");
        assertThat(result.logEntryId()).isNotNull();
        assertThat(result.commandId()).isNotNull();
        assertThat(result.command()).isNotNull();
        assertThat(result.command().getCommandType()).isEqualTo(DmCommandType.CAMPAIGN_CREATE);
    }

    @Test
    @DisplayName("processApprovedRecommendation retrieves log entry and creates command")
    void processApprovedRecommendation_retrievesLogAndCreatesCommand() {
        String logEntryId = UUID.randomUUID().toString();
        String workspaceId = "workspace-456";
        AiActionLogEntry entry = new AiActionLogEntry(
            logEntryId,
            workspaceId,
            "corr-123",
            AiActionType.RECOMMENDATION_GENERATED,
            AiActionStatus.PROPOSED,
            "user-789",
            true,
            0.95,
            List.of(),
            List.of(),
            "AI recommendation: CAMPAIGN",
            "Campaign recommendation details",
            "campaign-123",
            Instant.now(),
            0L
        );
        runPromise(() -> aiActionLogRepository.save(entry));

        RecommendationToCommandGateway.GatewayResult result = runPromise(() -> gateway.processApprovedRecommendation(
            logEntryId,
            DmTenantId.of("tenant-123"),
            DmWorkspaceId.of(workspaceId),
            "user-789"
        ));

        assertThat(result.status()).isEqualTo(RecommendationToCommandGateway.GatewayStatus.PROCESSED);
        assertThat(result.command()).isNotNull();
        assertThat(result.commandId()).isNotNull();
        assertThat(result.reason()).contains("processed from approval");
    }

    @Test
    @DisplayName("processApprovedRecommendation blocks when log entry not found")
    void processApprovedRecommendation_blocksWhenLogEntryNotFound() {
        RecommendationToCommandGateway.GatewayResult result = runPromise(() -> gateway.processApprovedRecommendation(
            "missing-id",
            DmTenantId.of("tenant-123"),
            DmWorkspaceId.of("workspace-456"),
            "user-789"
        ));

        assertThat(result.status()).isEqualTo(RecommendationToCommandGateway.GatewayStatus.BLOCKED);
        assertThat(result.reason()).contains("not found");
    }

    @Test
    @DisplayName("convertToCommand blocks when policy check fails")
    void convertToCommand_blocksWhenPolicyCheckFails() {
        gateway = new RecommendationToCommandGateway(commandService, aiActionLogRepository, new AllowingCompliancePlugin(false));

        RecommendationToCommandGateway.Recommendation recommendation = new RecommendationToCommandGateway.Recommendation(
            "strategy-generator",
            "CAMPAIGN",
            "target-123",
            "Generate a campaign",
            "gpt-4",
            "Campaign output",
            0.95,
            "kernel://evidence/123",
            500
        );

        RecommendationToCommandGateway.GatewayResult result = runPromise(() -> gateway.convertToCommand(
            recommendation,
            DmTenantId.of("tenant-123"),
            DmWorkspaceId.of("workspace-456"),
            "user-789"
        ));

        assertThat(result.status()).isEqualTo(RecommendationToCommandGateway.GatewayStatus.BLOCKED);
        assertThat(result.reason()).contains("Policy check failed");
    }

    // ── test doubles ─────────────────────────────────────────────────────────

    private static final class AllowingKernelAdapter implements DigitalMarketingKernelAdapter {
        private final boolean allowed;

        AllowingKernelAdapter(boolean allowed) {
            this.allowed = allowed;
        }

        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public Promise<Boolean> isAuthorized(DmOperationContext context, String resource, String action) {
            return Promise.of(allowed);
        }

        @Override
        public Promise<Boolean> isFeatureEnabled(DmOperationContext context, String flagKey) {
            return Promise.of(true);
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
                java.util.Map<String, Object> attributes) {
            return Promise.of("audit-1");
        }
    }

    private static final class AllowingCompliancePlugin implements CompliancePlugin {
        private final boolean compliant;

        AllowingCompliancePlugin(boolean compliant) {
            this.compliant = compliant;
        }

        @Override
        public Promise<ComplianceResult> evaluate(String ruleSetId, ComplianceContext context) {
            return Promise.of(new ComplianceResult(
                compliant,
                compliant ? List.of() : List.of(new ComplianceViolation(
                    "DM-CES-001",
                    "Connector credentials must not be expired or revoked",
                    ComplianceRule.Severity.CRITICAL,
                    Map.of("reason", "Test compliance failure")
                )),
                ruleSetId,
                Instant.now()
            ));
        }

        @Override
        public Promise<Void> registerRuleSet(String ruleSetId, List<ComplianceRule> rules) {
            return Promise.of(null);
        }

        @Override
        public Promise<Void> addRule(String ruleSetId, ComplianceRule rule) {
            return Promise.of(null);
        }

        @Override
        public Promise<List<AuditEntry>> getAuditTrail(String entityId) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<List<ComplianceViolation>> getActiveViolations(String ruleSetId) {
            return Promise.of(List.of());
        }
    }

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
        public Promise<Long> countByStatus(String tenantId, DmCommandStatus status) {
            long count = 0;
            for (DmCommand cmd : store.values()) {
                if (cmd.getTenantId().equals(tenantId) && cmd.getStatus() == status) {
                    count++;
                }
            }
            return Promise.of(count);
        }

        @Override
        public Promise<List<DmCommand>> findByTypeAndStatus(
                String tenantId, com.ghatana.digitalmarketing.domain.command.DmCommandType commandType, DmCommandStatus status, int limit) {
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
    }
}
