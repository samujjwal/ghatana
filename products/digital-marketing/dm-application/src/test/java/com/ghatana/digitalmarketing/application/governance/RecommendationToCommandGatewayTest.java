package com.ghatana.digitalmarketing.application.governance;

import com.ghatana.digitalmarketing.application.command.DmCommandService;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.transparency.AiActionLogEntry;
import com.ghatana.digitalmarketing.domain.transparency.AiActionLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for RecommendationToCommandGateway (DMOS-P1-020).
 *
 * @doc.type test
 * @doc.purpose Verify recommendation-to-command gateway with governance
 * @doc.layer application
 */
@DisplayName("RecommendationToCommandGateway")
class RecommendationToCommandGatewayTest {

    @Test
    @DisplayName("convertToCommand blocks null recommendation")
    void convertToCommand_blocksNullRecommendation() {
        DmCommandService commandService = mock(DmCommandService.class);
        AiActionLogRepository aiActionLogRepository = mock(AiActionLogRepository.class);

        RecommendationToCommandGateway gateway = new RecommendationToCommandGateway(commandService, aiActionLogRepository);

        RecommendationToCommandGateway.GatewayResult result = gateway.convertToCommand(
            null,
            new DmTenantId("tenant-123"),
            new DmWorkspaceId("workspace-456"),
            "user-789"
        ).getResult();

        assertThat(result.status()).isEqualTo(RecommendationToCommandGateway.GatewayStatus.BLOCKED);
        assertThat(result.reason()).contains("null");
    }

    @Test
    @DisplayName("convertToCommand blocks recommendation with missing target type")
    void convertToCommand_blocksRecommendationWithMissingTargetType() {
        DmCommandService commandService = mock(DmCommandService.class);
        AiActionLogRepository aiActionLogRepository = mock(AiActionLogRepository.class);

        RecommendationToCommandGateway gateway = new RecommendationToCommandGateway(commandService, aiActionLogRepository);

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

        RecommendationToCommandGateway.GatewayResult result = gateway.convertToCommand(
            recommendation,
            new DmTenantId("tenant-123"),
            new DmWorkspaceId("workspace-456"),
            "user-789"
        ).getResult();

        assertThat(result.status()).isEqualTo(RecommendationToCommandGateway.GatewayStatus.BLOCKED);
        assertThat(result.reason()).contains("Target type is required");
    }

    @Test
    @DisplayName("convertToCommand blocks recommendation with missing target ID")
    void convertToCommand_blocksRecommendationWithMissingTargetId() {
        DmCommandService commandService = mock(DmCommandService.class);
        AiActionLogRepository aiActionLogRepository = mock(AiActionLogRepository.class);

        RecommendationToCommandGateway gateway = new RecommendationToCommandGateway(commandService, aiActionLogRepository);

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

        RecommendationToCommandGateway.GatewayResult result = gateway.convertToCommand(
            recommendation,
            new DmTenantId("tenant-123"),
            new DmWorkspaceId("workspace-456"),
            "user-789"
        ).getResult();

        assertThat(result.status()).isEqualTo(RecommendationToCommandGateway.GatewayStatus.BLOCKED);
        assertThat(result.reason()).contains("Target ID is required");
    }

    @Test
    @DisplayName("convertToCommand requires approval for high-risk recommendation")
    void convertToCommand_requiresApprovalForHighRiskRecommendation() {
        DmCommandService commandService = mock(DmCommandService.class);
        AiActionLogRepository aiActionLogRepository = mock(AiActionLogRepository.class);

        when(aiActionLogRepository.save(any(AiActionLogEntry.class)))
            .thenReturn(mock(AiActionLogEntry.class));

        RecommendationToCommandGateway gateway = new RecommendationToCommandGateway(commandService, aiActionLogRepository);

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

        RecommendationToCommandGateway.GatewayResult result = gateway.convertToCommand(
            recommendation,
            new DmTenantId("tenant-123"),
            new DmWorkspaceId("workspace-456"),
            "user-789"
        ).getResult();

        assertThat(result.status()).isEqualTo(RecommendationToCommandGateway.GatewayStatus.REQUIRES_APPROVAL);
        assertThat(result.reason()).contains("requires approval");
        assertThat(result.logEntryId()).isNotNull();
    }

    @Test
    @DisplayName("convertToCommand creates command for low-risk recommendation")
    void convertToCommand_createsCommandForLowRiskRecommendation() {
        DmCommandService commandService = mock(DmCommandService.class);
        AiActionLogRepository aiActionLogRepository = mock(AiActionLogRepository.class);

        when(aiActionLogRepository.save(any(AiActionLogEntry.class)))
            .thenReturn(mock(AiActionLogEntry.class));

        RecommendationToCommandGateway gateway = new RecommendationToCommandGateway(commandService, aiActionLogRepository);

        RecommendationToCommandGateway.Recommendation recommendation = new RecommendationToCommandGateway.Recommendation(
            "strategy-generator",
            "STRATEGY",
            "target-123",
            "Generate a strategy",
            "gpt-4",
            "Strategy output",
            0.95, // High confidence -> low risk
            "kernel://evidence/123",
            500
        );

        RecommendationToCommandGateway.GatewayResult result = gateway.convertToCommand(
            recommendation,
            new DmTenantId("tenant-123"),
            new DmWorkspaceId("workspace-456"),
            "user-789"
        ).getResult();

        assertThat(result.status()).isEqualTo(RecommendationToCommandGateway.GatewayStatus.CREATED);
        assertThat(result.reason()).contains("Command created");
        assertThat(result.logEntryId()).isNotNull();
    }
}
