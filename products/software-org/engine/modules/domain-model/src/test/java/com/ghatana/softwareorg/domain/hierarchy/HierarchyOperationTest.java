package com.ghatana.softwareorg.domain.hierarchy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for HierarchyOperation domain model.
 *
 * @doc.type class
 * @doc.purpose Tests for hierarchy operation domain model
 * @doc.layer product
 * @doc.pattern Unit Test
 */
@DisplayName("HierarchyOperation Tests")
class HierarchyOperationTest {

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("should create operation with valid parameters")
        void shouldCreateOperationWithValidParameters() {
            // GIVEN
            String operationId = UUID.randomUUID().toString();
            String targetId = "dept-001";
            String initiatorId = "user-001";

            // WHEN
            HierarchyOperation operation = HierarchyOperation.builder()
                    .operationId(operationId)
                    .type(HierarchyOperation.OperationType.CREATE)
                    .targetId(targetId)
                    .targetType(HierarchyOperation.TargetType.DEPARTMENT)
                    .initiatorId(initiatorId)
                    .initiatorLayer(HierarchyLayer.EXECUTIVE)
                    .parameter("name", "Engineering")
                    .parameter("parentId", "org-001")
                    .build();

            // THEN
            assertThat(operation.operationId()).isEqualTo(operationId);
            assertThat(operation.type()).isEqualTo(HierarchyOperation.OperationType.CREATE);
            assertThat(operation.targetId()).isEqualTo(targetId);
            assertThat(operation.targetType()).isEqualTo(HierarchyOperation.TargetType.DEPARTMENT);
            assertThat(operation.initiatorId()).isEqualTo(initiatorId);
            assertThat(operation.initiatorLayer()).isEqualTo(HierarchyLayer.EXECUTIVE);
            assertThat(operation.status()).isEqualTo(HierarchyOperation.OperationStatus.PENDING);
            assertThat(operation.parameters()).containsEntry("name", "Engineering");
        }

        @Test
        @DisplayName("should reject null operationId")
        void shouldRejectNullOperationId() {
            assertThatThrownBy(() -> HierarchyOperation.builder()
                    .operationId(null)
                    .type(HierarchyOperation.OperationType.CREATE)
                    .targetId("dept-001")
                    .targetType(HierarchyOperation.TargetType.DEPARTMENT)
                    .initiatorId("user-001")
                    .initiatorLayer(HierarchyLayer.EXECUTIVE)
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("operationId");
        }

        @Test
        @DisplayName("should reject null type")
        void shouldRejectNullType() {
            assertThatThrownBy(() -> HierarchyOperation.builder()
                    .operationId(UUID.randomUUID().toString())
                    .type(null)
                    .targetId("dept-001")
                    .targetType(HierarchyOperation.TargetType.DEPARTMENT)
                    .initiatorId("user-001")
                    .initiatorLayer(HierarchyLayer.EXECUTIVE)
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("type");
        }
    }

    @Nested
    @DisplayName("Approval Requirements")
    class ApprovalRequirementTests {

        @Test
        @DisplayName("organization changes should always require approval")
        void organizationChangesShouldRequireApproval() {
            // GIVEN
            HierarchyOperation operation = createOperation(
                    HierarchyOperation.OperationType.MOVE,
                    HierarchyOperation.TargetType.ORGANIZATION,
                    HierarchyLayer.ORGANIZATION);

            // THEN
            assertThat(operation.requiresApproval()).isTrue();
        }

        @Test
        @DisplayName("department changes by manager should require approval")
        void departmentChangesByManagerShouldRequireApproval() {
            // GIVEN
            HierarchyOperation operation = createOperation(
                    HierarchyOperation.OperationType.CREATE,
                    HierarchyOperation.TargetType.DEPARTMENT,
                    HierarchyLayer.MANAGEMENT);

            // THEN
            assertThat(operation.requiresApproval()).isTrue();
        }

        @Test
        @DisplayName("department changes by executive should not require approval")
        void departmentChangesByExecutiveShouldNotRequireApproval() {
            // GIVEN
            HierarchyOperation operation = createOperation(
                    HierarchyOperation.OperationType.CREATE,
                    HierarchyOperation.TargetType.DEPARTMENT,
                    HierarchyLayer.EXECUTIVE);

            // THEN
            assertThat(operation.requiresApproval()).isFalse();
        }

        @Test
        @DisplayName("merge operations should always require approval")
        void mergeOperationsShouldRequireApproval() {
            // GIVEN
            HierarchyOperation operation = createOperation(
                    HierarchyOperation.OperationType.MERGE,
                    HierarchyOperation.TargetType.TEAM,
                    HierarchyLayer.MANAGEMENT);

            // THEN
            assertThat(operation.requiresApproval()).isTrue();
        }

        @Test
        @DisplayName("promotions should always require approval")
        void promotionsShouldRequireApproval() {
            // GIVEN
            HierarchyOperation operation = createOperation(
                    HierarchyOperation.OperationType.PROMOTE,
                    HierarchyOperation.TargetType.PERSON,
                    HierarchyLayer.MANAGEMENT);

            // THEN
            assertThat(operation.requiresApproval()).isTrue();
        }
    }

    @Nested
    @DisplayName("Approval Authority")
    class ApprovalAuthorityTests {

        @Test
        @DisplayName("organization operations require organization-level approval")
        void organizationOperationsRequireOrganizationApproval() {
            // GIVEN
            HierarchyOperation operation = createOperation(
                    HierarchyOperation.OperationType.CREATE,
                    HierarchyOperation.TargetType.ORGANIZATION,
                    HierarchyLayer.EXECUTIVE);

            // THEN
            assertThat(operation.getMinimumApprovalLayer()).isEqualTo(HierarchyLayer.ORGANIZATION);
            assertThat(operation.canBeApprovedBy(HierarchyLayer.ORGANIZATION)).isTrue();
            assertThat(operation.canBeApprovedBy(HierarchyLayer.EXECUTIVE)).isFalse();
        }

        @Test
        @DisplayName("department operations require executive-level approval")
        void departmentOperationsRequireExecutiveApproval() {
            // GIVEN
            HierarchyOperation operation = createOperation(
                    HierarchyOperation.OperationType.CREATE,
                    HierarchyOperation.TargetType.DEPARTMENT,
                    HierarchyLayer.MANAGEMENT);

            // THEN
            assertThat(operation.getMinimumApprovalLayer()).isEqualTo(HierarchyLayer.EXECUTIVE);
            assertThat(operation.canBeApprovedBy(HierarchyLayer.EXECUTIVE)).isTrue();
            assertThat(operation.canBeApprovedBy(HierarchyLayer.ORGANIZATION)).isTrue();
            assertThat(operation.canBeApprovedBy(HierarchyLayer.MANAGEMENT)).isFalse();
        }

        @Test
        @DisplayName("team operations require management-level approval")
        void teamOperationsRequireManagementApproval() {
            // GIVEN
            HierarchyOperation operation = createOperation(
                    HierarchyOperation.OperationType.CREATE,
                    HierarchyOperation.TargetType.TEAM,
                    HierarchyLayer.CONTRIBUTOR);

            // THEN
            assertThat(operation.getMinimumApprovalLayer()).isEqualTo(HierarchyLayer.MANAGEMENT);
            assertThat(operation.canBeApprovedBy(HierarchyLayer.MANAGEMENT)).isTrue();
            assertThat(operation.canBeApprovedBy(HierarchyLayer.EXECUTIVE)).isTrue();
            assertThat(operation.canBeApprovedBy(HierarchyLayer.CONTRIBUTOR)).isFalse();
        }
    }

    @Nested
    @DisplayName("Status Transitions")
    class StatusTransitionTests {

        @Test
        @DisplayName("should update status with new timestamp")
        void shouldUpdateStatusWithNewTimestamp() {
            // GIVEN
            Instant originalTime = Instant.now().minusSeconds(60);
            HierarchyOperation original = HierarchyOperation.builder()
                    .operationId(UUID.randomUUID().toString())
                    .type(HierarchyOperation.OperationType.CREATE)
                    .targetId("dept-001")
                    .targetType(HierarchyOperation.TargetType.DEPARTMENT)
                    .initiatorId("user-001")
                    .initiatorLayer(HierarchyLayer.EXECUTIVE)
                    .status(HierarchyOperation.OperationStatus.PENDING)
                    .createdAt(originalTime)
                    .updatedAt(originalTime)
                    .build();

            // WHEN
            HierarchyOperation updated = original.withStatus(HierarchyOperation.OperationStatus.APPROVED);

            // THEN
            assertThat(updated.status()).isEqualTo(HierarchyOperation.OperationStatus.APPROVED);
            assertThat(updated.createdAt()).isEqualTo(originalTime);
            assertThat(updated.updatedAt()).isAfter(originalTime);
        }

        @Test
        @DisplayName("should add approval ID")
        void shouldAddApprovalId() {
            // GIVEN
            HierarchyOperation original = createOperation(
                    HierarchyOperation.OperationType.CREATE,
                    HierarchyOperation.TargetType.DEPARTMENT,
                    HierarchyLayer.MANAGEMENT);

            // WHEN
            String approvalId = "approval-001";
            HierarchyOperation updated = original.withApprovalId(approvalId);

            // THEN
            assertThat(updated.approvalId()).isEqualTo(approvalId);
            assertThat(updated.operationId()).isEqualTo(original.operationId());
        }
    }

    // Helper method to create test operations
    private HierarchyOperation createOperation(
            HierarchyOperation.OperationType type,
            HierarchyOperation.TargetType targetType,
            HierarchyLayer initiatorLayer) {
        return HierarchyOperation.builder()
                .operationId(UUID.randomUUID().toString())
                .type(type)
                .targetId("target-001")
                .targetType(targetType)
                .initiatorId("user-001")
                .initiatorLayer(initiatorLayer)
                .build();
    }
}
