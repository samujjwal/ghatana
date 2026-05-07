package com.ghatana.digitalmarketing.domain.agency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for AgencyContract domain model.
 *
 * @doc.type test
 * @doc.purpose Validates AgencyContract lifecycle and validation (P3-002)
 * @doc.layer product
 */
@DisplayName("AgencyContract Tests")
class AgencyContractTest {

    @Test
    @DisplayName("Should build valid AgencyContract")
    void shouldBuildValidAgencyContract() {
        AgencyContract contract = AgencyContract.builder()
            .id("contract-123")
            .agencyTenantId("agency-456")
            .clientId("client-789")
            .contractNumber("CTR-2024-001")
            .contractType("Retainer")
            .startDate(LocalDate.of(2024, 1, 1))
            .endDate(LocalDate.of(2024, 12, 31))
            .monthlyRetainer(new BigDecimal("5000.00"))
            .currency("USD")
            .status(AgencyContractStatus.DRAFT)
            .terms("Standard retainer agreement")
            .createdAt(Instant.now())
            .build();

        assertThat(contract.getId()).isEqualTo("contract-123");
        assertThat(contract.getAgencyTenantId()).isEqualTo("agency-456");
        assertThat(contract.getClientId()).isEqualTo("client-789");
        assertThat(contract.getContractNumber()).isEqualTo("CTR-2024-001");
        assertThat(contract.getContractType()).isEqualTo("Retainer");
        assertThat(contract.getMonthlyRetainer()).isEqualByComparingTo("5000.00");
        assertThat(contract.getStatus()).isEqualTo(AgencyContractStatus.DRAFT);
    }

    @Test
    @DisplayName("Should activate contract from DRAFT status")
    void shouldActivateContract() {
        AgencyContract contract = AgencyContract.builder()
            .id("contract-123")
            .agencyTenantId("agency-456")
            .clientId("client-789")
            .contractNumber("CTR-2024-001")
            .contractType("Retainer")
            .startDate(LocalDate.of(2024, 1, 1))
            .endDate(LocalDate.of(2024, 12, 31))
            .monthlyRetainer(new BigDecimal("5000.00"))
            .currency("USD")
            .status(AgencyContractStatus.DRAFT)
            .createdAt(Instant.now())
            .build();

        AgencyContract activated = contract.activate();

        assertThat(activated.getStatus()).isEqualTo(AgencyContractStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should activate contract from PENDING status")
    void shouldActivateFromPending() {
        AgencyContract contract = AgencyContract.builder()
            .id("contract-123")
            .agencyTenantId("agency-456")
            .clientId("client-789")
            .contractNumber("CTR-2024-001")
            .contractType("Retainer")
            .startDate(LocalDate.of(2024, 1, 1))
            .endDate(LocalDate.of(2024, 12, 31))
            .monthlyRetainer(new BigDecimal("5000.00"))
            .currency("USD")
            .status(AgencyContractStatus.PENDING)
            .createdAt(Instant.now())
            .build();

        AgencyContract activated = contract.activate();

        assertThat(activated.getStatus()).isEqualTo(AgencyContractStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should throw when activating non-DRAFT/PENDING contract")
    void shouldThrowWhenActivatingNonDraftPending() {
        AgencyContract contract = AgencyContract.builder()
            .id("contract-123")
            .agencyTenantId("agency-456")
            .clientId("client-789")
            .contractNumber("CTR-2024-001")
            .contractType("Retainer")
            .startDate(LocalDate.of(2024, 1, 1))
            .endDate(LocalDate.of(2024, 12, 31))
            .monthlyRetainer(new BigDecimal("5000.00"))
            .currency("USD")
            .status(AgencyContractStatus.ACTIVE)
            .createdAt(Instant.now())
            .build();

        assertThatThrownBy(() -> contract.activate())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot activate contract in status");
    }

    @Test
    @DisplayName("Should terminate contract from ACTIVE status")
    void shouldTerminateContract() {
        AgencyContract contract = AgencyContract.builder()
            .id("contract-123")
            .agencyTenantId("agency-456")
            .clientId("client-789")
            .contractNumber("CTR-2024-001")
            .contractType("Retainer")
            .startDate(LocalDate.of(2024, 1, 1))
            .endDate(LocalDate.of(2024, 12, 31))
            .monthlyRetainer(new BigDecimal("5000.00"))
            .currency("USD")
            .status(AgencyContractStatus.ACTIVE)
            .createdAt(Instant.now())
            .build();

        AgencyContract terminated = contract.terminate("Client requested");

        assertThat(terminated.getStatus()).isEqualTo(AgencyContractStatus.TERMINATED);
    }

    @Test
    @DisplayName("Should throw when terminating non-ACTIVE contract")
    void shouldThrowWhenTerminatingNonActive() {
        AgencyContract contract = AgencyContract.builder()
            .id("contract-123")
            .agencyTenantId("agency-456")
            .clientId("client-789")
            .contractNumber("CTR-2024-001")
            .contractType("Retainer")
            .startDate(LocalDate.of(2024, 1, 1))
            .endDate(LocalDate.of(2024, 12, 31))
            .monthlyRetainer(new BigDecimal("5000.00"))
            .currency("USD")
            .status(AgencyContractStatus.DRAFT)
            .createdAt(Instant.now())
            .build();

        assertThatThrownBy(() -> contract.terminate("Client requested"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot terminate contract in status");
    }

    @Test
    @DisplayName("Should renew contract with new end date")
    void shouldRenewContract() {
        AgencyContract contract = AgencyContract.builder()
            .id("contract-123")
            .agencyTenantId("agency-456")
            .clientId("client-789")
            .contractNumber("CTR-2024-001")
            .contractType("Retainer")
            .startDate(LocalDate.of(2024, 1, 1))
            .endDate(LocalDate.of(2024, 12, 31))
            .monthlyRetainer(new BigDecimal("5000.00"))
            .currency("USD")
            .status(AgencyContractStatus.ACTIVE)
            .createdAt(Instant.now())
            .build();

        LocalDate newEndDate = LocalDate.of(2025, 12, 31);
        AgencyContract renewed = contract.renew(newEndDate);

        assertThat(renewed.getEndDate()).isEqualTo(newEndDate);
    }

    @Test
    @DisplayName("Should throw when renewing non-ACTIVE contract")
    void shouldThrowWhenRenewingNonActive() {
        AgencyContract contract = AgencyContract.builder()
            .id("contract-123")
            .agencyTenantId("agency-456")
            .clientId("client-789")
            .contractNumber("CTR-2024-001")
            .contractType("Retainer")
            .startDate(LocalDate.of(2024, 1, 1))
            .endDate(LocalDate.of(2024, 12, 31))
            .monthlyRetainer(new BigDecimal("5000.00"))
            .currency("USD")
            .status(AgencyContractStatus.DRAFT)
            .createdAt(Instant.now())
            .build();

        assertThatThrownBy(() -> contract.renew(LocalDate.of(2025, 12, 31)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot renew contract in status");
    }

    @Test
    @DisplayName("Should return true for active contract")
    void shouldReturnTrueForActiveContract() {
        AgencyContract contract = AgencyContract.builder()
            .id("contract-123")
            .agencyTenantId("agency-456")
            .clientId("client-789")
            .contractNumber("CTR-2024-001")
            .contractType("Retainer")
            .startDate(LocalDate.of(2024, 1, 1))
            .endDate(LocalDate.of(2024, 12, 31))
            .monthlyRetainer(new BigDecimal("5000.00"))
            .currency("USD")
            .status(AgencyContractStatus.ACTIVE)
            .createdAt(Instant.now())
            .build();

        assertThat(contract.isActive()).isTrue();
    }

    @Test
    @DisplayName("Should return false for non-active contract")
    void shouldReturnFalseForNonActiveContract() {
        AgencyContract contract = AgencyContract.builder()
            .id("contract-123")
            .agencyTenantId("agency-456")
            .clientId("client-789")
            .contractNumber("CTR-2024-001")
            .contractType("Retainer")
            .startDate(LocalDate.of(2024, 1, 1))
            .endDate(LocalDate.of(2024, 12, 31))
            .monthlyRetainer(new BigDecimal("5000.00"))
            .currency("USD")
            .status(AgencyContractStatus.DRAFT)
            .createdAt(Instant.now())
            .build();

        assertThat(contract.isActive()).isFalse();
    }

    @Test
    @DisplayName("Should return true for expired contract")
    void shouldReturnTrueForExpiredContract() {
        LocalDate pastDate = LocalDate.of(2020, 12, 31);
        AgencyContract contract = AgencyContract.builder()
            .id("contract-123")
            .agencyTenantId("agency-456")
            .clientId("client-789")
            .contractNumber("CTR-2024-001")
            .contractType("Retainer")
            .startDate(LocalDate.of(2020, 1, 1))
            .endDate(pastDate)
            .monthlyRetainer(new BigDecimal("5000.00"))
            .currency("USD")
            .status(AgencyContractStatus.ACTIVE)
            .createdAt(Instant.now())
            .build();

        assertThat(contract.isExpired()).isTrue();
    }

    @Test
    @DisplayName("Should return false for non-expired contract")
    void shouldReturnFalseForNonExpiredContract() {
        LocalDate futureDate = LocalDate.of(2025, 12, 31);
        AgencyContract contract = AgencyContract.builder()
            .id("contract-123")
            .agencyTenantId("agency-456")
            .clientId("client-789")
            .contractNumber("CTR-2024-001")
            .contractType("Retainer")
            .startDate(LocalDate.of(2024, 1, 1))
            .endDate(futureDate)
            .monthlyRetainer(new BigDecimal("5000.00"))
            .currency("USD")
            .status(AgencyContractStatus.ACTIVE)
            .createdAt(Instant.now())
            .build();

        assertThat(contract.isExpired()).isFalse();
    }

    @Test
    @DisplayName("Should throw when id is blank")
    void shouldThrowWhenIdIsBlank() {
        assertThatThrownBy(() -> AgencyContract.builder()
            .id("")
            .agencyTenantId("agency-456")
            .clientId("client-789")
            .contractNumber("CTR-2024-001")
            .status(AgencyContractStatus.DRAFT)
            .createdAt(Instant.now())
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("id must not be blank");
    }

    @Test
    @DisplayName("Should throw when agencyTenantId is blank")
    void shouldThrowWhenAgencyTenantIdIsBlank() {
        assertThatThrownBy(() -> AgencyContract.builder()
            .id("contract-123")
            .agencyTenantId("")
            .clientId("client-789")
            .contractNumber("CTR-2024-001")
            .status(AgencyContractStatus.DRAFT)
            .createdAt(Instant.now())
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("agencyTenantId must not be blank");
    }

    @Test
    @DisplayName("Should throw when clientId is blank")
    void shouldThrowWhenClientIdIsBlank() {
        assertThatThrownBy(() -> AgencyContract.builder()
            .id("contract-123")
            .agencyTenantId("agency-456")
            .clientId("")
            .contractNumber("CTR-2024-001")
            .status(AgencyContractStatus.DRAFT)
            .createdAt(Instant.now())
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("clientId must not be blank");
    }

    @Test
    @DisplayName("Should throw when contractNumber is blank")
    void shouldThrowWhenContractNumberIsBlank() {
        assertThatThrownBy(() -> AgencyContract.builder()
            .id("contract-123")
            .agencyTenantId("agency-456")
            .clientId("client-789")
            .contractNumber("")
            .status(AgencyContractStatus.DRAFT)
            .createdAt(Instant.now())
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("contractNumber must not be blank");
    }

    @Test
    @DisplayName("Should support toBuilder pattern")
    void shouldSupportToBuilderPattern() {
        AgencyContract original = AgencyContract.builder()
            .id("contract-123")
            .agencyTenantId("agency-456")
            .clientId("client-789")
            .contractNumber("CTR-2024-001")
            .contractType("Retainer")
            .startDate(LocalDate.of(2024, 1, 1))
            .endDate(LocalDate.of(2024, 12, 31))
            .monthlyRetainer(new BigDecimal("5000.00"))
            .currency("USD")
            .status(AgencyContractStatus.DRAFT)
            .terms("Standard retainer")
            .createdAt(Instant.now())
            .build();

        AgencyContract modified = original.toBuilder()
            .status(AgencyContractStatus.ACTIVE)
            .updatedAt(Instant.now())
            .build();

        assertThat(modified.getId()).isEqualTo(original.getId());
        assertThat(modified.getStatus()).isEqualTo(AgencyContractStatus.ACTIVE);
        assertThat(modified.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should implement equals and hashCode based on id")
    void shouldImplementEqualsAndHashCode() {
        AgencyContract contract1 = AgencyContract.builder()
            .id("contract-123")
            .agencyTenantId("agency-456")
            .clientId("client-789")
            .contractNumber("CTR-2024-001")
            .status(AgencyContractStatus.DRAFT)
            .createdAt(Instant.now())
            .build();

        AgencyContract contract2 = AgencyContract.builder()
            .id("contract-123")
            .agencyTenantId("agency-999")
            .clientId("client-999")
            .contractNumber("CTR-9999-999")
            .status(AgencyContractStatus.ACTIVE)
            .createdAt(Instant.now())
            .build();

        assertThat(contract1).isEqualTo(contract2);
        assertThat(contract1.hashCode()).isEqualTo(contract2.hashCode());
    }
}
