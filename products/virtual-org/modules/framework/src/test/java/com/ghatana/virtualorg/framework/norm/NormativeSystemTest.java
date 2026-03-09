package com.ghatana.virtualorg.framework.norm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the Normative System (Norm, NormRegistry, NormativeMonitor).
 */
@DisplayName("Normative System Tests")
class NormativeSystemTest {

    private InMemoryNormRegistry normRegistry;
    private NormativeMonitor monitor;

    @BeforeEach
    void setUp() {
        normRegistry = new InMemoryNormRegistry();
        monitor = new NormativeMonitor(normRegistry);
    }

    @Test
    @DisplayName("Should create obligation norm with deadline")
    void shouldCreateObligationNormWithDeadline() {
        Norm norm = Norm.obligation("respond-p1")
                .description("Respond to P1 incidents within 15 minutes")
                .action("acknowledge")
                .deadline(Duration.ofMinutes(15))
                .penalty(0.8)
                .build();

        assertThat(norm.isObligation()).isTrue();
        assertThat(norm.id()).isEqualTo("respond-p1");
        assertThat(norm.action()).isEqualTo("acknowledge");
        assertThat(norm.deadline()).isPresent();
        assertThat(norm.deadline().get()).isEqualTo(Duration.ofMinutes(15));
        assertThat(norm.penaltyWeight()).isEqualTo(0.8);
    }

    @Test
    @DisplayName("Should create prohibition norm")
    void shouldCreateProhibitionNorm() {
        Norm norm = Norm.prohibition("no-friday-deploy")
                .description("No production deployments on Fridays")
                .action("deploy-production")
                .penalty(0.7)
                .build();

        assertThat(norm.isProhibition()).isTrue();
        assertThat(norm.action()).isEqualTo("deploy-production");
    }

    @Test
    @DisplayName("Should create permission norm")
    void shouldCreatePermissionNorm() {
        Norm norm = Norm.permission("approve-architecture")
                .description("Tech leads can approve architecture changes")
                .action("approve-architecture")
                .targetRole("tech-lead")
                .build();

        assertThat(norm.isPermission()).isTrue();
        assertThat(norm.targetRole()).isPresent();
        assertThat(norm.targetRole().get()).isEqualTo("tech-lead");
    }

    @Test
    @DisplayName("Should register and retrieve norms from registry")
    void shouldRegisterAndRetrieveNorms() {
        Norm obligation = Norm.obligation("test-obligation")
                .action("test-action")
                .build();

        Norm prohibition = Norm.prohibition("test-prohibition")
                .action("forbidden-action")
                .build();

        normRegistry.register(obligation).getResult();
        normRegistry.register(prohibition).getResult();

        List<Norm> obligations = normRegistry.getObligations(null).getResult();
        List<Norm> prohibitions = normRegistry.getProhibitions(null).getResult();

        assertThat(obligations).hasSize(1);
        assertThat(prohibitions).hasSize(1);
    }

    @Test
    @DisplayName("Should track obligation and detect violation on deadline miss")
    void shouldDetectObligationViolation() {
        Norm obligation = Norm.obligation("fast-response")
                .action("respond")
                .deadline(Duration.ofMillis(1)) // Very short deadline for testing
                .build();

        normRegistry.register(obligation).getResult();

        monitor.trackObligation("agent-1", "dept-1", obligation);

        // Wait for deadline to pass
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        List<NormViolation> violations = monitor.checkDeadlines().getResult();

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).agentId()).isEqualTo("agent-1");
        assertThat(violations.get(0).violationType())
                .isEqualTo(NormViolation.ViolationType.MISSED_DEADLINE);
    }

    @Test
    @DisplayName("Should detect prohibition violation when forbidden action performed")
    void shouldDetectProhibitionViolation() {
        Norm prohibition = Norm.prohibition("no-deploy")
                .action("deploy")
                .build();

        normRegistry.register(prohibition).getResult();

        List<NormViolation> violations = monitor.reportAction(
                "agent-1", "deploy", java.util.Map.of("departmentId", "dept-1")
        ).getResult();

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).violationType())
                .isEqualTo(NormViolation.ViolationType.FORBIDDEN_ACTION);
    }

    @Test
    @DisplayName("Should fulfill obligation when action is performed")
    void shouldFulfillObligationOnAction() {
        Norm obligation = Norm.obligation("respond-task")
                .action("respond")
                .deadline(Duration.ofHours(1))
                .build();

        normRegistry.register(obligation).getResult();

        String trackingId = monitor.trackObligation("agent-1", "dept-1", obligation);

        // Perform the required action
        monitor.reportAction("agent-1", "respond", java.util.Map.of()).getResult();

        // Check deadlines - should be no violations
        List<NormViolation> violations = monitor.checkDeadlines().getResult();
        assertThat(violations).isEmpty();
    }
}
