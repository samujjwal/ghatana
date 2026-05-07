package com.ghatana.digitalmarketing.domain.agency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for AgencyRetainer domain model.
 *
 * @doc.type test
 * @doc.purpose Validates AgencyRetainer lifecycle and validation (P3-002)
 * @doc.layer product
 */
@DisplayName("AgencyRetainer Tests")
class AgencyRetainerTest {

    @Test
    @DisplayName("Should build valid AgencyRetainer")
    void shouldBuildValidAgencyRetainer() {
        AgencyRetainer retainer = AgencyRetainer.builder()
            .id("retainer-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .monthlyAmount(new BigDecimal("5000.00"))
            .currency("USD")
            .billingCycleStart(LocalDate.of(2024, 1, 1))
            .billingDayOfMonth(1)
            .serviceAllowances(Map.of("campaigns", 10, "reports", 5))
            .overageRate(new BigDecimal("100.00"))
            .status(AgencyRetainerStatus.PENDING)
            .createdAt(Instant.now())
            .build();

        assertThat(retainer.getId()).isEqualTo("retainer-123");
        assertThat(retainer.getContractId()).isEqualTo("contract-456");
        assertThat(retainer.getAgencyTenantId()).isEqualTo("agency-789");
        assertThat(retainer.getClientId()).isEqualTo("client-101");
        assertThat(retainer.getMonthlyAmount()).isEqualByComparingTo("5000.00");
        assertThat(retainer.getCurrency()).isEqualTo("USD");
        assertThat(retainer.getBillingDayOfMonth()).isEqualTo(1);
        assertThat(retainer.getServiceAllowances()).hasSize(2);
        assertThat(retainer.getStatus()).isEqualTo(AgencyRetainerStatus.PENDING);
    }

    @Test
    @DisplayName("Should activate retainer from PENDING status")
    void shouldActivateRetainer() {
        AgencyRetainer retainer = AgencyRetainer.builder()
            .id("retainer-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .monthlyAmount(new BigDecimal("5000.00"))
            .currency("USD")
            .status(AgencyRetainerStatus.PENDING)
            .createdAt(Instant.now())
            .build();

        AgencyRetainer activated = retainer.activate();

        assertThat(activated.getStatus()).isEqualTo(AgencyRetainerStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should throw when activating non-PENDING retainer")
    void shouldThrowWhenActivatingNonPending() {
        AgencyRetainer retainer = AgencyRetainer.builder()
            .id("retainer-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .monthlyAmount(new BigDecimal("5000.00"))
            .currency("USD")
            .status(AgencyRetainerStatus.ACTIVE)
            .createdAt(Instant.now())
            .build();

        assertThatThrownBy(() -> retainer.activate())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot activate retainer in status");
    }

    @Test
    @DisplayName("Should suspend retainer from ACTIVE status")
    void shouldSuspendRetainer() {
        AgencyRetainer retainer = AgencyRetainer.builder()
            .id("retainer-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .monthlyAmount(new BigDecimal("5000.00"))
            .currency("USD")
            .status(AgencyRetainerStatus.ACTIVE)
            .createdAt(Instant.now())
            .build();

        AgencyRetainer suspended = retainer.suspend("Payment issue");

        assertThat(suspended.getStatus()).isEqualTo(AgencyRetainerStatus.SUSPENDED);
    }

    @Test
    @DisplayName("Should throw when suspending non-ACTIVE retainer")
    void shouldThrowWhenSuspendingNonActive() {
        AgencyRetainer retainer = AgencyRetainer.builder()
            .id("retainer-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .monthlyAmount(new BigDecimal("5000.00"))
            .currency("USD")
            .status(AgencyRetainerStatus.PENDING)
            .createdAt(Instant.now())
            .build();

        assertThatThrownBy(() -> retainer.suspend("Payment issue"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot suspend retainer in status");
    }

    @Test
    @DisplayName("Should cancel retainer")
    void shouldCancelRetainer() {
        AgencyRetainer retainer = AgencyRetainer.builder()
            .id("retainer-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .monthlyAmount(new BigDecimal("5000.00"))
            .currency("USD")
            .status(AgencyRetainerStatus.ACTIVE)
            .createdAt(Instant.now())
            .build();

        AgencyRetainer cancelled = retainer.cancel("Client requested");

        assertThat(cancelled.getStatus()).isEqualTo(AgencyRetainerStatus.CANCELLED);
    }

    @Test
    @DisplayName("Should throw when cancelling already cancelled retainer")
    void shouldThrowWhenCancellingAlreadyCancelled() {
        AgencyRetainer retainer = AgencyRetainer.builder()
            .id("retainer-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .monthlyAmount(new BigDecimal("5000.00"))
            .currency("USD")
            .status(AgencyRetainerStatus.CANCELLED)
            .createdAt(Instant.now())
            .build();

        assertThatThrownBy(() -> retainer.cancel("Another reason"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Retainer already cancelled");
    }

    @Test
    @DisplayName("Should return true for active retainer")
    void shouldReturnTrueForActiveRetainer() {
        AgencyRetainer retainer = AgencyRetainer.builder()
            .id("retainer-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .monthlyAmount(new BigDecimal("5000.00"))
            .currency("USD")
            .status(AgencyRetainerStatus.ACTIVE)
            .createdAt(Instant.now())
            .build();

        assertThat(retainer.isActive()).isTrue();
    }

    @Test
    @DisplayName("Should return false for non-active retainer")
    void shouldReturnFalseForNonActiveRetainer() {
        AgencyRetainer retainer = AgencyRetainer.builder()
            .id("retainer-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .monthlyAmount(new BigDecimal("5000.00"))
            .currency("USD")
            .status(AgencyRetainerStatus.PENDING)
            .createdAt(Instant.now())
            .build();

        assertThat(retainer.isActive()).isFalse();
    }

    @Test
    @DisplayName("Should return true for service with allowance")
    void shouldReturnTrueForServiceWithAllowance() {
        AgencyRetainer retainer = AgencyRetainer.builder()
            .id("retainer-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .monthlyAmount(new BigDecimal("5000.00"))
            .currency("USD")
            .serviceAllowances(Map.of("campaigns", 10, "reports", 5))
            .status(AgencyRetainerStatus.ACTIVE)
            .createdAt(Instant.now())
            .build();

        assertThat(retainer.hasAllowanceForService("campaigns")).isTrue();
    }

    @Test
    @DisplayName("Should return false for service without allowance")
    void shouldReturnFalseForServiceWithoutAllowance() {
        AgencyRetainer retainer = AgencyRetainer.builder()
            .id("retainer-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .monthlyAmount(new BigDecimal("5000.00"))
            .currency("USD")
            .serviceAllowances(Map.of("campaigns", 10, "reports", 5))
            .status(AgencyRetainerStatus.ACTIVE)
            .createdAt(Instant.now())
            .build();

        assertThat(retainer.hasAllowanceForService("consulting")).isFalse();
    }

    @Test
    @DisplayName("Should throw when id is blank")
    void shouldThrowWhenIdIsBlank() {
        assertThatThrownBy(() -> AgencyRetainer.builder()
            .id("")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .monthlyAmount(new BigDecimal("5000.00"))
            .status(AgencyRetainerStatus.PENDING)
            .createdAt(Instant.now())
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("id must not be blank");
    }

    @Test
    @DisplayName("Should throw when contractId is blank")
    void shouldThrowWhenContractIdIsBlank() {
        assertThatThrownBy(() -> AgencyRetainer.builder()
            .id("retainer-123")
            .contractId("")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .monthlyAmount(new BigDecimal("5000.00"))
            .status(AgencyRetainerStatus.PENDING)
            .createdAt(Instant.now())
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("contractId must not be blank");
    }

    @Test
    @DisplayName("Should throw when agencyTenantId is blank")
    void shouldThrowWhenAgencyTenantIdIsBlank() {
        assertThatThrownBy(() -> AgencyRetainer.builder()
            .id("retainer-123")
            .contractId("contract-456")
            .agencyTenantId("")
            .clientId("client-101")
            .monthlyAmount(new BigDecimal("5000.00"))
            .status(AgencyRetainerStatus.PENDING)
            .createdAt(Instant.now())
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("agencyTenantId must not be blank");
    }

    @Test
    @DisplayName("Should throw when clientId is blank")
    void shouldThrowWhenClientIdIsBlank() {
        assertThatThrownBy(() -> AgencyRetainer.builder()
            .id("retainer-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("")
            .monthlyAmount(new BigDecimal("5000.00"))
            .status(AgencyRetainerStatus.PENDING)
            .createdAt(Instant.now())
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("clientId must not be blank");
    }

    @Test
    @DisplayName("Should throw when monthlyAmount is negative")
    void shouldThrowWhenMonthlyAmountIsNegative() {
        assertThatThrownBy(() -> AgencyRetainer.builder()
            .id("retainer-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .monthlyAmount(new BigDecimal("-100.00"))
            .status(AgencyRetainerStatus.PENDING)
            .createdAt(Instant.now())
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("monthlyAmount must be non-negative");
    }

    @Test
    @DisplayName("Should support toBuilder pattern")
    void shouldSupportToBuilderPattern() {
        AgencyRetainer original = AgencyRetainer.builder()
            .id("retainer-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .monthlyAmount(new BigDecimal("5000.00"))
            .currency("USD")
            .serviceAllowances(Map.of("campaigns", 10))
            .status(AgencyRetainerStatus.PENDING)
            .createdAt(Instant.now())
            .build();

        AgencyRetainer modified = original.toBuilder()
            .status(AgencyRetainerStatus.ACTIVE)
            .updatedAt(Instant.now())
            .build();

        assertThat(modified.getId()).isEqualTo(original.getId());
        assertThat(modified.getStatus()).isEqualTo(AgencyRetainerStatus.ACTIVE);
        assertThat(modified.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should implement equals and hashCode based on id")
    void shouldImplementEqualsAndHashCode() {
        AgencyRetainer retainer1 = AgencyRetainer.builder()
            .id("retainer-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .monthlyAmount(new BigDecimal("5000.00"))
            .status(AgencyRetainerStatus.PENDING)
            .createdAt(Instant.now())
            .build();

        AgencyRetainer retainer2 = AgencyRetainer.builder()
            .id("retainer-123")
            .contractId("contract-999")
            .agencyTenantId("agency-999")
            .clientId("client-999")
            .monthlyAmount(new BigDecimal("10000.00"))
            .status(AgencyRetainerStatus.ACTIVE)
            .createdAt(Instant.now())
            .build();

        assertThat(retainer1).isEqualTo(retainer2);
        assertThat(retainer1.hashCode()).isEqualTo(retainer2.hashCode());
    }
}
