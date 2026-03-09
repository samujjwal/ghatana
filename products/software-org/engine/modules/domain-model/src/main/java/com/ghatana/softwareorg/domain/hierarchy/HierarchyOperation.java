package com.ghatana.softwareorg.domain.hierarchy;

import java.time.Instant;
import java.util.Map;
import java.util.Collections;
import java.util.HashMap;

/**
 * Domain model for hierarchy operations.
 *
 * <p>
 * <b>Purpose</b><br>
 * Represents an organizational hierarchy operation such as
 * creating, moving, merging, or splitting organizational units.
 *
 * <p>
 * <b>Operation Types</b><br>
 * - CREATE: Create new org unit (department, team, role)
 * - MOVE: Move org unit to different parent
 * - MERGE: Merge two org units into one
 * - SPLIT: Split one org unit into multiple
 * - PROMOTE: Promote person to higher level
 * - DEMOTE: Demote person to lower level
 * - TRANSFER: Transfer person between units
 * - DELETE: Remove org unit (soft delete)
 *
 * @doc.type record
 * @doc.purpose Hierarchy operation domain model
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record HierarchyOperation(
        String operationId,
        OperationType type,
        String targetId,
        TargetType targetType,
        String initiatorId,
        HierarchyLayer initiatorLayer,
        Map<String, Object> parameters,
        OperationStatus status,
        String approvalId,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Operation type enumeration
     */
    public enum OperationType {
        CREATE,
        MOVE,
        MERGE,
        SPLIT,
        PROMOTE,
        DEMOTE,
        TRANSFER,
        DELETE
    }

    /**
     * Target type enumeration
     */
    public enum TargetType {
        ORGANIZATION,
        DEPARTMENT,
        TEAM,
        ROLE,
        PERSON
    }

    /**
     * Operation status enumeration
     */
    public enum OperationStatus {
        PENDING,
        AWAITING_APPROVAL,
        APPROVED,
        REJECTED,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    /**
     * Compact constructor with validation
     */
    public HierarchyOperation {
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalArgumentException("operationId cannot be null or blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("type cannot be null");
        }
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("targetId cannot be null or blank");
        }
        if (targetType == null) {
            throw new IllegalArgumentException("targetType cannot be null");
        }
        if (initiatorId == null || initiatorId.isBlank()) {
            throw new IllegalArgumentException("initiatorId cannot be null or blank");
        }
        if (initiatorLayer == null) {
            throw new IllegalArgumentException("initiatorLayer cannot be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }

        // Defensive copy for mutable map
        parameters = parameters != null
                ? Collections.unmodifiableMap(new HashMap<>(parameters))
                : Collections.emptyMap();
    }

    /**
     * Check if operation requires approval based on type and initiator layer.
     *
     * @return true if approval is required
     */
    public boolean requiresApproval() {
        // Merges and splits always require approval regardless of target
        if (type == OperationType.MERGE || type == OperationType.SPLIT) {
            return true;
        }

        // Person promotions/demotions require approval from higher layer
        if (type == OperationType.PROMOTE || type == OperationType.DEMOTE) {
            return true;
        }

        // Organization-level changes always require approval
        if (targetType == TargetType.ORGANIZATION) {
            return true;
        }

        // Department changes require executive or higher approval
        if (targetType == TargetType.DEPARTMENT) {
            return initiatorLayer.getLevel() < HierarchyLayer.EXECUTIVE.getLevel();
        }

        // Team changes require management or higher approval
        if (targetType == TargetType.TEAM) {
            return initiatorLayer.getLevel() < HierarchyLayer.MANAGEMENT.getLevel();
        }

        return false;
    }

    /**
     * Get the minimum layer required to approve this operation.
     *
     * @return minimum HierarchyLayer for approval
     */
    public HierarchyLayer getMinimumApprovalLayer() {
        return switch (targetType) {
            case ORGANIZATION -> HierarchyLayer.ORGANIZATION;
            case DEPARTMENT -> HierarchyLayer.EXECUTIVE;
            case TEAM -> HierarchyLayer.MANAGEMENT;
            case ROLE, PERSON -> HierarchyLayer.MANAGEMENT;
        };
    }

    /**
     * Check if a given layer can approve this operation.
     *
     * @param approverLayer the layer of the approver
     * @return true if the layer can approve
     */
    public boolean canBeApprovedBy(HierarchyLayer approverLayer) {
        HierarchyLayer minLayer = getMinimumApprovalLayer();
        return approverLayer.getLevel() >= minLayer.getLevel();
    }

    /**
     * Create a new operation with updated status.
     *
     * @param newStatus the new status
     * @return new HierarchyOperation with updated status
     */
    public HierarchyOperation withStatus(OperationStatus newStatus) {
        return new HierarchyOperation(
                operationId,
                type,
                targetId,
                targetType,
                initiatorId,
                initiatorLayer,
                parameters,
                newStatus,
                approvalId,
                createdAt,
                Instant.now());
    }

    /**
     * Create a new operation with approval ID.
     *
     * @param newApprovalId the approval ID
     * @return new HierarchyOperation with approval ID
     */
    public HierarchyOperation withApprovalId(String newApprovalId) {
        return new HierarchyOperation(
                operationId,
                type,
                targetId,
                targetType,
                initiatorId,
                initiatorLayer,
                parameters,
                status,
                newApprovalId,
                createdAt,
                Instant.now());
    }

    /**
     * Builder for creating HierarchyOperation instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for HierarchyOperation.
     */
    public static class Builder {
        private String operationId;
        private OperationType type;
        private String targetId;
        private TargetType targetType;
        private String initiatorId;
        private HierarchyLayer initiatorLayer;
        private Map<String, Object> parameters = new HashMap<>();
        private OperationStatus status = OperationStatus.PENDING;
        private String approvalId;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder operationId(String operationId) {
            this.operationId = operationId;
            return this;
        }

        public Builder type(OperationType type) {
            this.type = type;
            return this;
        }

        public Builder targetId(String targetId) {
            this.targetId = targetId;
            return this;
        }

        public Builder targetType(TargetType targetType) {
            this.targetType = targetType;
            return this;
        }

        public Builder initiatorId(String initiatorId) {
            this.initiatorId = initiatorId;
            return this;
        }

        public Builder initiatorLayer(HierarchyLayer initiatorLayer) {
            this.initiatorLayer = initiatorLayer;
            return this;
        }

        public Builder parameters(Map<String, Object> parameters) {
            this.parameters = parameters;
            return this;
        }

        public Builder parameter(String key, Object value) {
            this.parameters.put(key, value);
            return this;
        }

        public Builder status(OperationStatus status) {
            this.status = status;
            return this;
        }

        public Builder approvalId(String approvalId) {
            this.approvalId = approvalId;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public HierarchyOperation build() {
            return new HierarchyOperation(
                    operationId,
                    type,
                    targetId,
                    targetType,
                    initiatorId,
                    initiatorLayer,
                    parameters,
                    status,
                    approvalId,
                    createdAt,
                    updatedAt);
        }
    }
}
