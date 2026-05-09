package com.ghatana.digitalmarketing.domain.agency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for AgencyDeliverable domain model.
 *
 * @doc.type test
 * @doc.purpose Validates AgencyDeliverable lifecycle and validation (P3-002)
 * @doc.layer product
 */
@DisplayName("AgencyDeliverable Tests")
class AgencyDeliverableTest {

    @Test
    @DisplayName("Should build valid AgencyDeliverable")
    void shouldBuildValidAgencyDeliverable() {
        AgencyDeliverable deliverable = AgencyDeliverable.builder()
            .id("deliverable-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .deliverableType("Campaign Report")
            .title("Q1 Performance Report")
            .description("Monthly performance report for Q1")
            .dueDate(LocalDate.of(2024, 4, 15))
            .assignedTo("user-202")
            .metadata(Map.of("priority", "high", "complexity", "medium"))
            .status(AgencyDeliverableStatus.PENDING)
            .createdAt(Instant.now())
            .build();

        assertThat(deliverable.getId()).isEqualTo("deliverable-123");
        assertThat(deliverable.getContractId()).isEqualTo("contract-456");
        assertThat(deliverable.getAgencyTenantId()).isEqualTo("agency-789");
        assertThat(deliverable.getClientId()).isEqualTo("client-101");
        assertThat(deliverable.getDeliverableType()).isEqualTo("Campaign Report");
        assertThat(deliverable.getTitle()).isEqualTo("Q1 Performance Report");
        assertThat(deliverable.getStatus()).isEqualTo(AgencyDeliverableStatus.PENDING);
        assertThat(deliverable.getMetadata()).hasSize(2);
    }

    @Test
    @DisplayName("Should start deliverable from PENDING status")
    void shouldStartDeliverable() {
        AgencyDeliverable deliverable = AgencyDeliverable.builder()
            .id("deliverable-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .deliverableType("Campaign Report")
            .title("Q1 Performance Report")
            .status(AgencyDeliverableStatus.PENDING)
            .createdAt(Instant.now())
            .build();

        AgencyDeliverable started = deliverable.start();

        assertThat(started.getStatus()).isEqualTo(AgencyDeliverableStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("Should throw when starting non-PENDING deliverable")
    void shouldThrowWhenStartingNonPending() {
        AgencyDeliverable deliverable = AgencyDeliverable.builder()
            .id("deliverable-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .deliverableType("Campaign Report")
            .title("Q1 Performance Report")
            .status(AgencyDeliverableStatus.IN_PROGRESS)
            .createdAt(Instant.now())
            .build();

        assertThatThrownBy(() -> deliverable.start())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot start deliverable in status");
    }

    @Test
    @DisplayName("Should complete deliverable from IN_PROGRESS status")
    void shouldCompleteDeliverable() {
        AgencyDeliverable deliverable = AgencyDeliverable.builder()
            .id("deliverable-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .deliverableType("Campaign Report")
            .title("Q1 Performance Report")
            .status(AgencyDeliverableStatus.IN_PROGRESS)
            .createdAt(Instant.now())
            .build();

        AgencyDeliverable completed = deliverable.complete();

        assertThat(completed.getStatus()).isEqualTo(AgencyDeliverableStatus.COMPLETED);
        assertThat(completed.getCompletedDate()).isNotNull();
    }

    @Test
    @DisplayName("Should throw when completing non-IN_PROGRESS deliverable")
    void shouldThrowWhenCompletingNonInProgress() {
        AgencyDeliverable deliverable = AgencyDeliverable.builder()
            .id("deliverable-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .deliverableType("Campaign Report")
            .title("Q1 Performance Report")
            .status(AgencyDeliverableStatus.PENDING)
            .createdAt(Instant.now())
            .build();

        assertThatThrownBy(() -> deliverable.complete())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot complete deliverable in status");
    }

    @Test
    @DisplayName("Should reject deliverable from SUBMITTED status")
    void shouldRejectDeliverable() {
        AgencyDeliverable deliverable = AgencyDeliverable.builder()
            .id("deliverable-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .deliverableType("Campaign Report")
            .title("Q1 Performance Report")
            .status(AgencyDeliverableStatus.SUBMITTED)
            .createdAt(Instant.now())
            .build();

        AgencyDeliverable rejected = deliverable.reject("Quality issues");

        assertThat(rejected.getStatus()).isEqualTo(AgencyDeliverableStatus.REJECTED);
    }

    @Test
    @DisplayName("Should reject deliverable from IN_REVIEW status")
    void shouldRejectDeliverableFromReview() {
        AgencyDeliverable deliverable = AgencyDeliverable.builder()
            .id("deliverable-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .deliverableType("Campaign Report")
            .title("Q1 Performance Report")
            .status(AgencyDeliverableStatus.IN_REVIEW)
            .createdAt(Instant.now())
            .build();

        AgencyDeliverable rejected = deliverable.reject("Missing data");

        assertThat(rejected.getStatus()).isEqualTo(AgencyDeliverableStatus.REJECTED);
    }

    @Test
    @DisplayName("Should throw when rejecting non-SUBMITTED/IN_REVIEW deliverable")
    void shouldThrowWhenRejectingNonSubmitted() {
        AgencyDeliverable deliverable = AgencyDeliverable.builder()
            .id("deliverable-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .deliverableType("Campaign Report")
            .title("Q1 Performance Report")
            .status(AgencyDeliverableStatus.IN_PROGRESS)
            .createdAt(Instant.now())
            .build();

        assertThatThrownBy(() -> deliverable.reject("Quality issues"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot reject deliverable in status");
    }

    @Test
    @DisplayName("Should submit deliverable for review")
    void shouldSubmitDeliverableForReview() {
        AgencyDeliverable deliverable = AgencyDeliverable.builder()
            .id("deliverable-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .deliverableType("Campaign Report")
            .title("Q1 Performance Report")
            .status(AgencyDeliverableStatus.IN_PROGRESS)
            .createdAt(Instant.now())
            .build();

        AgencyDeliverable submitted = deliverable.submitForReview();

        assertThat(submitted.getStatus()).isEqualTo(AgencyDeliverableStatus.SUBMITTED);
    }

    @Test
    @DisplayName("Should throw when submitting non-IN_PROGRESS deliverable")
    void shouldThrowWhenSubmittingNonInProgress() {
        AgencyDeliverable deliverable = AgencyDeliverable.builder()
            .id("deliverable-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .deliverableType("Campaign Report")
            .title("Q1 Performance Report")
            .status(AgencyDeliverableStatus.PENDING)
            .createdAt(Instant.now())
            .build();

        assertThatThrownBy(() -> deliverable.submitForReview())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot submit deliverable in status");
    }

    @Test
    @DisplayName("Should return true for overdue deliverable")
    void shouldReturnTrueForOverdueDeliverable() {
        LocalDate pastDate = LocalDate.of(2020, 1, 1);
        AgencyDeliverable deliverable = AgencyDeliverable.builder()
            .id("deliverable-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .deliverableType("Campaign Report")
            .title("Q1 Performance Report")
            .dueDate(pastDate)
            .status(AgencyDeliverableStatus.IN_PROGRESS)
            .createdAt(Instant.now())
            .build();

        assertThat(deliverable.isOverdue()).isTrue();
    }

    @Test
    @DisplayName("Should return false for non-overdue deliverable")
    void shouldReturnFalseForNonOverdueDeliverable() {
        LocalDate futureDate = LocalDate.now().plusDays(30);
        AgencyDeliverable deliverable = AgencyDeliverable.builder()
            .id("deliverable-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .deliverableType("Campaign Report")
            .title("Q1 Performance Report")
            .dueDate(futureDate)
            .status(AgencyDeliverableStatus.IN_PROGRESS)
            .createdAt(Instant.now())
            .build();

        assertThat(deliverable.isOverdue()).isFalse();
    }

    @Test
    @DisplayName("Should return false for overdue completed deliverable")
    void shouldReturnFalseForOverdueCompletedDeliverable() {
        LocalDate pastDate = LocalDate.of(2020, 1, 1);
        AgencyDeliverable deliverable = AgencyDeliverable.builder()
            .id("deliverable-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .deliverableType("Campaign Report")
            .title("Q1 Performance Report")
            .dueDate(pastDate)
            .status(AgencyDeliverableStatus.COMPLETED)
            .completedDate(LocalDate.of(2020, 1, 15))
            .createdAt(Instant.now())
            .build();

        assertThat(deliverable.isOverdue()).isFalse();
    }

    @Test
    @DisplayName("Should throw when id is blank")
    void shouldThrowWhenIdIsBlank() {
        assertThatThrownBy(() -> AgencyDeliverable.builder()
            .id("")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .title("Q1 Performance Report")
            .status(AgencyDeliverableStatus.PENDING)
            .createdAt(Instant.now())
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("id must not be blank");
    }

    @Test
    @DisplayName("Should throw when contractId is blank")
    void shouldThrowWhenContractIdIsBlank() {
        assertThatThrownBy(() -> AgencyDeliverable.builder()
            .id("deliverable-123")
            .contractId("")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .title("Q1 Performance Report")
            .status(AgencyDeliverableStatus.PENDING)
            .createdAt(Instant.now())
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("contractId must not be blank");
    }

    @Test
    @DisplayName("Should throw when agencyTenantId is blank")
    void shouldThrowWhenAgencyTenantIdIsBlank() {
        assertThatThrownBy(() -> AgencyDeliverable.builder()
            .id("deliverable-123")
            .contractId("contract-456")
            .agencyTenantId("")
            .clientId("client-101")
            .title("Q1 Performance Report")
            .status(AgencyDeliverableStatus.PENDING)
            .createdAt(Instant.now())
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("agencyTenantId must not be blank");
    }

    @Test
    @DisplayName("Should throw when clientId is blank")
    void shouldThrowWhenClientIdIsBlank() {
        assertThatThrownBy(() -> AgencyDeliverable.builder()
            .id("deliverable-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("")
            .title("Q1 Performance Report")
            .status(AgencyDeliverableStatus.PENDING)
            .createdAt(Instant.now())
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("clientId must not be blank");
    }

    @Test
    @DisplayName("Should throw when title is blank")
    void shouldThrowWhenTitleIsBlank() {
        assertThatThrownBy(() -> AgencyDeliverable.builder()
            .id("deliverable-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .title("")
            .status(AgencyDeliverableStatus.PENDING)
            .createdAt(Instant.now())
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("title must not be blank");
    }

    @Test
    @DisplayName("Should support toBuilder pattern")
    void shouldSupportToBuilderPattern() {
        AgencyDeliverable original = AgencyDeliverable.builder()
            .id("deliverable-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .deliverableType("Campaign Report")
            .title("Q1 Performance Report")
            .metadata(Map.of("priority", "high"))
            .status(AgencyDeliverableStatus.PENDING)
            .createdAt(Instant.now())
            .build();

        AgencyDeliverable modified = original.toBuilder()
            .status(AgencyDeliverableStatus.IN_PROGRESS)
            .updatedAt(Instant.now())
            .build();

        assertThat(modified.getId()).isEqualTo(original.getId());
        assertThat(modified.getStatus()).isEqualTo(AgencyDeliverableStatus.IN_PROGRESS);
        assertThat(modified.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should implement equals and hashCode based on id")
    void shouldImplementEqualsAndHashCode() {
        AgencyDeliverable deliverable1 = AgencyDeliverable.builder()
            .id("deliverable-123")
            .contractId("contract-456")
            .agencyTenantId("agency-789")
            .clientId("client-101")
            .title("Q1 Performance Report")
            .status(AgencyDeliverableStatus.PENDING)
            .createdAt(Instant.now())
            .build();

        AgencyDeliverable deliverable2 = AgencyDeliverable.builder()
            .id("deliverable-123")
            .contractId("contract-999")
            .agencyTenantId("agency-999")
            .clientId("client-999")
            .title("Q2 Performance Report")
            .status(AgencyDeliverableStatus.COMPLETED)
            .createdAt(Instant.now())
            .build();

        assertThat(deliverable1).isEqualTo(deliverable2);
        assertThat(deliverable1.hashCode()).isEqualTo(deliverable2.hashCode());
    }
}
