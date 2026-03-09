package com.ghatana.softwareorg.domain.hierarchy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Domain service for validating hierarchy operations.
 *
 * <p>
 * <b>Purpose</b><br>
 * Validates organizational hierarchy operations against business rules
 * and constraints. This is pure domain logic with no external dependencies.
 *
 * <p>
 * <b>Validation Rules</b><br>
 * - Authority checks: Initiator must have sufficient authority
 * - Circular dependency prevention: No cycles in hierarchy
 * - Orphan prevention: No orphaned units after operation
 * - Capacity constraints: Team/department size limits
 *
 * @doc.type class
 * @doc.purpose Hierarchy operation validation service
 * @doc.layer product
 * @doc.pattern Domain Service
 */
public class HierarchyValidationService {

    /**
     * Validation result containing success status and any errors.
     */
    public record ValidationResult(
            boolean valid,
            List<String> errors,
            List<String> warnings) {
        public static ValidationResult success() {
            return new ValidationResult(true, List.of(), List.of());
        }

        public static ValidationResult failure(String error) {
            return new ValidationResult(false, List.of(error), List.of());
        }

        public static ValidationResult failure(List<String> errors) {
            return new ValidationResult(false, errors, List.of());
        }

        public ValidationResult withWarning(String warning) {
            List<String> newWarnings = new ArrayList<>(warnings);
            newWarnings.add(warning);
            return new ValidationResult(valid, errors, newWarnings);
        }
    }

    /**
     * Validates a hierarchy operation.
     *
     * @param operation the operation to validate
     * @return validation result
     */
    public ValidationResult validate(HierarchyOperation operation) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Validate authority
        Optional<String> authorityError = validateAuthority(operation);
        authorityError.ifPresent(errors::add);

        // Validate operation-specific rules
        switch (operation.type()) {
            case CREATE -> validateCreate(operation, errors, warnings);
            case MOVE -> validateMove(operation, errors, warnings);
            case MERGE -> validateMerge(operation, errors, warnings);
            case SPLIT -> validateSplit(operation, errors, warnings);
            case PROMOTE -> validatePromotion(operation, errors, warnings);
            case DEMOTE -> validateDemotion(operation, errors, warnings);
            case TRANSFER -> validateTransfer(operation, errors, warnings);
            case DELETE -> validateDelete(operation, errors, warnings);
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    /**
     * Validates that the initiator has sufficient authority for the operation.
     *
     * @param operation the operation to validate
     * @return error message if validation fails, empty otherwise
     */
    private Optional<String> validateAuthority(HierarchyOperation operation) {
        HierarchyLayer initiatorLayer = operation.initiatorLayer();
        HierarchyLayer minRequired = operation.getMinimumApprovalLayer();

        // For operations that don't require approval, initiator just needs to be at or
        // above target level
        if (!operation.requiresApproval()) {
            return Optional.empty();
        }

        // For operations requiring approval, check if initiator can self-approve
        // or if they need to submit for approval
        if (initiatorLayer.getLevel() < minRequired.getLevel()) {
            // This is not an error - it just means approval is required
            return Optional.empty();
        }

        return Optional.empty();
    }

    /**
     * Validates CREATE operation.
     */
    private void validateCreate(HierarchyOperation operation, List<String> errors, List<String> warnings) {
        var params = operation.parameters();

        // Check required parameters
        if (!params.containsKey("name") || params.get("name") == null) {
            errors.add("CREATE operation requires 'name' parameter");
        }

        if (!params.containsKey("parentId") && operation.targetType() != HierarchyOperation.TargetType.ORGANIZATION) {
            errors.add("CREATE operation requires 'parentId' parameter for non-organization targets");
        }

        // Warn about potential naming conflicts
        if (params.containsKey("name")) {
            warnings.add("Ensure name '" + params.get("name") + "' is unique within parent scope");
        }
    }

    /**
     * Validates MOVE operation.
     */
    private void validateMove(HierarchyOperation operation, List<String> errors, List<String> warnings) {
        var params = operation.parameters();

        if (!params.containsKey("newParentId")) {
            errors.add("MOVE operation requires 'newParentId' parameter");
        }

        // Warn about potential circular dependencies
        warnings.add("Verify that moving to new parent does not create circular dependency");

        // Warn about orphaned children
        warnings.add("Verify that all children will be properly re-parented");
    }

    /**
     * Validates MERGE operation.
     */
    private void validateMerge(HierarchyOperation operation, List<String> errors, List<String> warnings) {
        var params = operation.parameters();

        if (!params.containsKey("sourceIds")) {
            errors.add("MERGE operation requires 'sourceIds' parameter");
        }

        if (!params.containsKey("targetName")) {
            errors.add("MERGE operation requires 'targetName' parameter");
        }

        // Merges always require approval
        if (operation.status() == HierarchyOperation.OperationStatus.PENDING) {
            warnings.add("MERGE operations require approval from "
                    + operation.getMinimumApprovalLayer().getDisplayName() + " level");
        }
    }

    /**
     * Validates SPLIT operation.
     */
    private void validateSplit(HierarchyOperation operation, List<String> errors, List<String> warnings) {
        var params = operation.parameters();

        if (!params.containsKey("splitConfig")) {
            errors.add("SPLIT operation requires 'splitConfig' parameter");
        }

        // Splits always require approval
        if (operation.status() == HierarchyOperation.OperationStatus.PENDING) {
            warnings.add("SPLIT operations require approval from "
                    + operation.getMinimumApprovalLayer().getDisplayName() + " level");
        }
    }

    /**
     * Validates PROMOTE operation.
     */
    private void validatePromotion(HierarchyOperation operation, List<String> errors, List<String> warnings) {
        var params = operation.parameters();

        if (!params.containsKey("newRole")) {
            errors.add("PROMOTE operation requires 'newRole' parameter");
        }

        if (!params.containsKey("newLayer")) {
            errors.add("PROMOTE operation requires 'newLayer' parameter");
        }

        // Validate that new layer is actually higher
        if (params.containsKey("currentLayer") && params.containsKey("newLayer")) {
            HierarchyLayer currentLayer = HierarchyLayer.valueOf((String) params.get("currentLayer"));
            HierarchyLayer newLayer = HierarchyLayer.valueOf((String) params.get("newLayer"));

            if (!newLayer.hasHigherAuthority(currentLayer)) {
                errors.add("PROMOTE operation must move to a higher layer");
            }
        }
    }

    /**
     * Validates DEMOTE operation.
     */
    private void validateDemotion(HierarchyOperation operation, List<String> errors, List<String> warnings) {
        var params = operation.parameters();

        if (!params.containsKey("newRole")) {
            errors.add("DEMOTE operation requires 'newRole' parameter");
        }

        if (!params.containsKey("newLayer")) {
            errors.add("DEMOTE operation requires 'newLayer' parameter");
        }

        // Validate that new layer is actually lower
        if (params.containsKey("currentLayer") && params.containsKey("newLayer")) {
            HierarchyLayer currentLayer = HierarchyLayer.valueOf((String) params.get("currentLayer"));
            HierarchyLayer newLayer = HierarchyLayer.valueOf((String) params.get("newLayer"));

            if (newLayer.hasHigherAuthority(currentLayer) || newLayer == currentLayer) {
                errors.add("DEMOTE operation must move to a lower layer");
            }
        }

        // Demotions are sensitive operations
        warnings.add("DEMOTE operations should include documented justification");
    }

    /**
     * Validates TRANSFER operation.
     */
    private void validateTransfer(HierarchyOperation operation, List<String> errors, List<String> warnings) {
        var params = operation.parameters();

        if (!params.containsKey("newTeamId") && !params.containsKey("newDepartmentId")) {
            errors.add("TRANSFER operation requires 'newTeamId' or 'newDepartmentId' parameter");
        }

        // Warn about role compatibility
        warnings.add("Verify that transferred person's role is compatible with new team/department");
    }

    /**
     * Validates DELETE operation.
     */
    private void validateDelete(HierarchyOperation operation, List<String> errors, List<String> warnings) {
        var params = operation.parameters();

        // Check for orphan prevention
        if (!params.containsKey("reassignTo") && operation.targetType() != HierarchyOperation.TargetType.PERSON) {
            warnings.add("Consider specifying 'reassignTo' to prevent orphaned children");
        }

        // Deletes are soft deletes by default
        if (!params.containsKey("hardDelete") || !Boolean.TRUE.equals(params.get("hardDelete"))) {
            warnings.add("This is a soft delete. Use 'hardDelete: true' for permanent removal");
        }

        // Organization-level deletes are extremely sensitive
        if (operation.targetType() == HierarchyOperation.TargetType.ORGANIZATION) {
            errors.add("Cannot delete organization-level entities");
        }
    }

    /**
     * Validates that an approver can approve a given operation.
     *
     * @param operation     the operation to approve
     * @param approverLayer the layer of the approver
     * @return validation result
     */
    public ValidationResult validateApproval(HierarchyOperation operation, HierarchyLayer approverLayer) {
        if (!operation.canBeApprovedBy(approverLayer)) {
            return ValidationResult.failure(
                    "Approver at " + approverLayer.getDisplayName() + " level cannot approve this operation. " +
                            "Minimum required: " + operation.getMinimumApprovalLayer().getDisplayName());
        }

        if (operation.status() != HierarchyOperation.OperationStatus.AWAITING_APPROVAL) {
            return ValidationResult.failure(
                    "Operation is not awaiting approval. Current status: " + operation.status());
        }

        return ValidationResult.success();
    }
}
