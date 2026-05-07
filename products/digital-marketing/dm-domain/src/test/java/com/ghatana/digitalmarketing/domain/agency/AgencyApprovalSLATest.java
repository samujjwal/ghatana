package com.ghatana.digitalmarketing.domain.agency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for AgencyApprovalSLA domain model.
 *
 * @doc.type test
 * @doc.purpose Validates AgencyApprovalSLA lifecycle and validation (P3-002)
 * @doc.layer product
 */
@DisplayName("AgencyApprovalSLA Tests")
class AgencyApprovalSLATest {

    @Test
    @DisplayName("Should build valid AgencyApprovalSLA")
    void shouldBuildValidAgencyApprovalSLA() {
        AgencyApprovalSLA sla = AgencyApprovalSLA.builder()
            .id("sla-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .approvalType("Campaign Launch")
            .maxApprovalTime(Duration.ofHours(48))
            .escalationLevel(0)
            .escalationTimeouts(Map.of("level1", Duration.ofHours(24), "level2", Duration.ofHours(48)))
            .escalationProcedure("Email manager, then escalate to director")
            .active(true)
            .createdAt(Instant.now())
            .build();

        assertThat(sla.getId()).isEqualTo("sla-123");
        assertThat(sla.getContractId()).isEqualTo("contract-456");
        assertThat(sla.getAgencyTenantId()).isEqualTo("agency-789");
        assertThat(sla.getClientId()).isEqualTo("client-101");
        assertThat(sla.getApprovalType()).isEqualTo("Campaign Launch");
        assertThat(sla.getMaxApprovalTime()).isEqualTo(Duration.ofHours(48));
        assertThat(sla.getEscalationLevel()).isEqualTo(0);
        assertThat(sla.getEscalationTimeouts()).hasSize(2);
        assertThat(sla.isActive()).isTrue();
    }

    @Test
    @DisplayName("Should activate SLA")
    void shouldActivateSLA() {
        AgencyApprovalSLA sla = AgencyApprovalSLA.builder()
            .id("sla-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .approvalType("Campaign Launch")
            .maxApprovalTime(Duration.ofHours(48))
            .active(false)
            .createdAt(Instant.now())
            .build();

        AgencyApprovalSLA activated = sla.activate();

        assertThat(activated.isActive()).isTrue();
    }

    @Test
    @DisplayName("Should deactivate SLA")
    void shouldDeactivateSLA() {
        AgencyApprovalSLA sla = AgencyApprovalSLA.builder()
            .id("sla-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .approvalType("Campaign Launch")
            .maxApprovalTime(Duration.ofHours(48))
            .active(true)
            .createdAt(Instant.now())
            .build();

        AgencyApprovalSLA deactivated = sla.deactivate("Contract ended");

        assertThat(deactivated.isActive()).isFalse();
    }

    @Test
    @DisplayName("Should update escalation level")
    void shouldUpdateEscalationLevel() {
        AgencyApprovalSLA sla = AgencyApprovalSLA.builder()
            .id("sla-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .approvalType("Campaign Launch")
            .maxApprovalTime(Duration.ofHours(48))
            .escalationLevel(0)
            .active(true)
            .createdAt(Instant.now())
            .build();

        AgencyApprovalSLA updated = sla.updateEscalationLevel(2);

        assertThat(updated.getEscalationLevel()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should throw when updating escalation level to negative")
    void shouldThrowWhenUpdatingEscalationLevelToNegative() {
        AgencyApprovalSLA sla = AgencyApprovalSLA.builder()
            .id("sla-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .approvalType("Campaign Launch")
            .maxApprovalTime(Duration.ofHours(48))
            .escalationLevel(0)
            .active(true)
            .createdAt(Instant.now())
            .build();

        assertThatThrownBy(() -> sla.updateEscalationLevel(-1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Escalation level must be between 0 and 5");
    }

    @Test
    @DisplayName("Should throw when updating escalation level above 5")
    void shouldThrowWhenUpdatingEscalationLevelAbove5() {
        AgencyApprovalSLA sla = AgencyApprovalSLA.builder()
            .id("sla-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .approvalType("Campaign Launch")
            .maxApprovalTime(Duration.ofHours(48))
            .escalationLevel(0)
            .active(true)
            .createdAt(Instant.now())
            .build();

        assertThatThrownBy(() -> sla.updateEscalationLevel(6))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Escalation level must be between 0 and 5");
    }

    @Test
    @DisplayName("Should return true for active SLA")
    void shouldReturnTrueForActiveSLA() {
        AgencyApprovalSLA sla = AgencyApprovalSLA.builder()
            .id("sla-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .approvalType("Campaign Launch")
            .maxApprovalTime(Duration.ofHours(48))
            .active(true)
            .createdAt(Instant.now())
            .build();

        assertThat(sla.isActive()).isTrue();
    }

    @Test
    @DisplayName("Should return false for inactive SLA")
    void shouldReturnFalseForInactiveSLA() {
        AgencyApprovalSLA sla = AgencyApprovalSLA.builder()
            .id("sla-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .approvalType("Campaign Launch")
            .maxApprovalTime(Duration.ofHours(48))
            .active(false)
            .createdAt(Instant.now())
            .build();

        assertThat(sla.isActive()).isFalse();
    }

    @Test
    @DisplayName("Should return true for overdue approval request")
    void shouldReturnTrueForOverdueApprovalRequest() {
        Instant pastRequest = Instant.now().minus(Duration.ofHours(50));
        AgencyApprovalSLA sla = AgencyApprovalSLA.builder()
            .id("sla-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .approvalType("Campaign Launch")
            .maxApprovalTime(Duration.ofHours(48))
            .active(true)
            .createdAt(Instant.now())
            .build();

        assertThat(sla.isOverdue(pastRequest)).isTrue();
    }

    @Test
    @DisplayName("Should return false for non-overdue approval request")
    void shouldReturnFalseForNonOverdueApprovalRequest() {
        Instant recentRequest = Instant.now().minus(Duration.ofHours(24));
        AgencyApprovalSLA sla = AgencyApprovalSLA.builder()
            .id("sla-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .approvalType("Campaign Launch")
            .maxApprovalTime(Duration.ofHours(48))
            .active(true)
            .createdAt(Instant.now())
            .build();

        assertThat(sla.isOverdue(recentRequest)).isFalse();
    }

    @Test
    @DisplayName("Should return false for overdue when SLA is inactive")
    void shouldReturnFalseForOverdueWhenInactive() {
        Instant pastRequest = Instant.now().minus(Duration.ofHours(50));
        AgencyApprovalSLA sla = AgencyApprovalSLA.builder()
            .id("sla-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .approvalType("Campaign Launch")
            .maxApprovalTime(Duration.ofHours(48))
            .active(false)
            .createdAt(Instant.now())
            .build();

        assertThat(sla.isOverdue(pastRequest)).isFalse();
    }

    @Test
    @DisplayName("Should return null for time until deadline when SLA is inactive")
    void shouldReturnNullForTimeUntilDeadlineWhenInactive() {
        Instant recentRequest = Instant.now().minus(Duration.ofHours(24));
        AgencyApprovalSLA sla = AgencyApprovalSLA.builder()
            .id("sla-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .approvalType("Campaign Launch")
            .maxApprovalTime(Duration.ofHours(48))
            .active(false)
            .createdAt(Instant.now())
            .build();

        assertThat(sla.getTimeUntilDeadline(recentRequest)).isNull();
    }

    @Test
    @DisplayName("Should return null for time until deadline when maxApprovalTime is null")
    void shouldReturnNullForTimeUntilDeadlineWhenMaxApprovalTimeIsNull() {
        Instant recentRequest = Instant.now().minus(Duration.ofHours(24));
        AgencyApprovalSLA sla = AgencyApprovalSLA.builder()
            .id("sla-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .approvalType("Campaign Launch")
            .maxApprovalTime(null)
            .active(true)
            .createdAt(Instant.now())
            .build();

        assertThat(sla.getTimeUntilDeadline(recentRequest)).isNull();
    }

    @Test
    @DisplayName("Should return positive duration for time until deadline")
    void shouldReturnPositiveDurationForTimeUntilDeadline() {
        Instant recentRequest = Instant.now().minus(Duration.ofHours(24));
        AgencyApprovalSLA sla = AgencyApprovalSLA.builder()
            .id("sla-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .approvalType("Campaign Launch")
            .maxApprovalTime(Duration.ofHours(48))
            .active(true)
            .createdAt(Instant.now())
            .build();

        Duration remaining = sla.getTimeUntilDeadline(recentRequest);
        assertThat(remaining).isNotNull();
        assertThat(remaining).isGreaterThan(Duration.ZERO);
        assertThat(remaining).isLessThan(Duration.ofHours(25));
    }

    @Test
    @DisplayName("Should return zero duration for overdue request")
    void shouldReturnZeroDurationForOverdueRequest() {
        Instant pastRequest = Instant.now().minus(Duration.ofHours(50));
        AgencyApprovalSLA sla = AgencyApprovalSLA.builder()
            .id("sla-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .approvalType("Campaign Launch")
            .maxApprovalTime(Duration.ofHours(48))
            .active(true)
            .createdAt(Instant.now())
            .build();

        Duration remaining = sla.getTimeUntilDeadline(pastRequest);
        assertThat(remaining).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("Should throw when id is blank")
    void shouldThrowWhenIdIsBlank() {
        assertThatThrownBy(() -> AgencyApprovalSLA.builder()
            .id("")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .approvalType("Campaign Launch")
            .active(true)
            .createdAt(Instant.now())
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("id must not be blank");
    }

    @Test
    @DisplayName("Should throw when contractId is blank")
    void shouldThrowWhenContractIdIsBlank() {
        assertThatThrownBy(() -> AgencyApprovalSLA.builder()
            .id("sla-123")
            .contractId("")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .approvalType("Campaign Launch")
            .active(true)
            .createdAt(Instant.now())
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("contractId must not be blank");
    }

    @Test
    @DisplayName("Should throw when agencyTenantId is blank")
    void shouldThrowWhenAgencyTenantIdIsBlank() {
        assertThatThrownBy(() -> AgencyApprovalSLA.builder()
            .id("sla-123")
            .contractId("contract-456")
            .agencyTenantId("")
            .clientId("client-101")
            .approvalType("Campaign Launch")
            .active(true)
            .createdAt(Instant.now())
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("agencyTenantId must not be blank");
    }

    @Test
    @DisplayName("Should throw when clientId is blank")
    void shouldThrowWhenClientIdIsBlank() {
        assertThatThrownBy(() -> AgencyApprovalSLA.builder()
            .id("sla-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("")
            .approvalType("Campaign Launch")
            .active(true)
            .createdAt(Instant.now())
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("clientId must not be blank");
    }

    @Test
    @DisplayName("Should throw when approvalType is blank")
    void shouldThrowWhenApprovalTypeIsBlank() {
        assertThatThrownBy(() -> AgencyApprovalSLA.builder()
            .id("sla-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .approvalType("")
            .active(true)
            .createdAt(Instant.now())
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("approvalType must not be blank");
    }

    @Test
    @DisplayName("Should support toBuilder pattern")
    void shouldSupportToBuilderPattern() {
        AgencyApprovalSLA original = AgencyApprovalSLA.builder()
            .id("sla-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .approvalType("Campaign Launch")
            .maxApprovalTime(Duration.ofHours(48))
            .escalationTimeouts(Map.of("level1", Duration.ofHours(24)))
            .active(true)
            .createdAt(Instant.now())
            .build();

        AgencyApprovalSLA modified = original.toBuilder()
            .active(false)
            .updatedAt(Instant.now())
            .build();

        assertThat(modified.getId()).isEqualTo(original.getId());
        assertThat(modified.isActive()).isFalse();
        assertThat(modified.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should implement equals and hashCode based on id")
    void shouldImplementEqualsAndHashCode() {
        AgencyApprovalSLA sla1 = AgencyApprovalSLA.builder()
            .id("sla-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .approvalType("Campaign Launch")
            .active(true)
            .createdAt(Instant.now())
            .build();

        AgencyApprovalSLA sla2 = AgencyApprovalSLA.builder()
            .id("sla-123")
            .contractId("contract-999")
            .agencyTenantId("agency-999")
            .clientId("client-999")
            .approvalType("Budget Approval")
            .active(false)
            .createdAt(Instant.now())
            .build();

        assertThat(sla1).isEqualTo(sla2);
        assertThat(sla1.hashCode()).isEqualTo(sla2.hashCode());
    }
}
