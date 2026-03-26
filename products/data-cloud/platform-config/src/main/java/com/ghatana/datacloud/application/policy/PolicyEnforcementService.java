package com.ghatana.datacloud.application.policy;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.datacloud.entity.policy.PolicyDecision;
import com.ghatana.datacloud.entity.policy.PolicyEngine;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Service for enforcing governance policies via policy-as-code (OPA/Rego).
 *
 * <p><b>Purpose</b><br>
 * Provides approval workflow enforcement for schema changes, RBAC updates,
 * and collection lifecycle operations. Uses Open Policy Agent (OPA) with Rego policies
 * for declarative policy definitions.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * PolicyEnforcementService policyService = new PolicyEnforcementService(
 *     policyEngine,
 *     metrics
 * );
 *
 * // Evaluate schema change policy
 * PolicyDecision decision = runPromise(() -> 
 *     policyService.evaluateSchemaChange(
 *         "tenant-123",
 *         "orders",
 *         schemaChanges,
 *         "user-456"
 *     )
 * );
 *
 * if (decision.isAllowed()) {
 *     // Proceed with schema change
 * } else {
 *     // Reject with decision.getReason()
 * }
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Service in application layer (hexagonal architecture)
 * - Uses PolicyEngine port (domain abstraction)
 * - Infrastructure adapters provide OPA/Rego integration
 * - Emits policy evaluation metrics for observability
 *
 * <p><b>Thread Safety</b><br>
 * Stateless service - thread-safe. All state managed by PolicyEngine.
 *
 * <p><b>Policy Types</b><br>
 * - Schema changes: field additions, removals, type changes
 * - RBAC updates: role assignments, permission grants
 * - Collection lifecycle: creation, archival, deletion
 * - Data operations: bulk imports, exports, purges
 *
 * @see PolicyEngine
 * @see PolicyDecision
 * @doc.type class
 * @doc.purpose Policy enforcement via OPA/Rego for governance workflows
 * @doc.layer product
 * @doc.pattern Service (Application Layer)
 */
public class PolicyEnforcementService {

    private static final Logger logger = LoggerFactory.getLogger(PolicyEnforcementService.class);

    private final PolicyEngine policyEngine;
    private final MetricsCollector metrics;

    /**
     * Creates a new policy enforcement service.
     *
     * @param policyEngine the policy engine (required)
     * @param metrics the metrics collector (required)
     * @throws NullPointerException if any parameter is null
     */
    public PolicyEnforcementService(
            PolicyEngine policyEngine,
            MetricsCollector metrics) {
        this.policyEngine = Objects.requireNonNull(policyEngine, "PolicyEngine must not be null");
        this.metrics = Objects.requireNonNull(metrics, "MetricsCollector must not be null");
    }

    /**
     * Evaluates schema change policy.
     *
     * <p><b>Policy Inputs</b><br>
     * - Tenant context
     * - Collection name
     * - Schema changes (additions, removals, type changes)
     * - User requesting change
     * - Current schema version
     *
     * <p><b>Policy Rules</b><br>
     * - Breaking changes require approval
     * - Non-breaking changes auto-approved
     * - Schema version increments validated
     * - User has schema:update permission
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @param schemaChanges the schema change descriptor (required)
     * @param userId the user requesting change (required)
     * @return Promise of PolicyDecision (allowed or denied with reason)
     */
    public Promise<PolicyDecision> evaluateSchemaChange(
            String tenantId,
            String collectionName,
            SchemaChanges schemaChanges,
            String userId) {
        validateTenantId(tenantId);
        Objects.requireNonNull(collectionName, "Collection name must not be null");
        Objects.requireNonNull(schemaChanges, "Schema changes must not be null");
        Objects.requireNonNull(userId, "User ID must not be null");

        Map<String, Object> policyInput = Map.of(
            "tenantId", tenantId,
            "collectionName", collectionName,
            "changes", schemaChanges.toMap(),
            "userId", userId,
            "timestamp", System.currentTimeMillis()
        );

        return policyEngine.evaluate("schema_change", policyInput)
            .whenComplete((decision, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("policy.schema_change.evaluated",
                        "tenant", tenantId,
                        "collection", collectionName,
                        "allowed", String.valueOf(decision.isAllowed()));
                    
                    if (decision.isAllowed()) {
                        logger.info("Schema change approved: tenant={}, collection={}, user={}, changes={}",
                            tenantId, collectionName, userId, schemaChanges.summary());
                    } else {
                        logger.warn("Schema change denied: tenant={}, collection={}, user={}, reason={}",
                            tenantId, collectionName, userId, decision.getReason());
                    }
                } else {
                    metrics.incrementCounter("policy.schema_change.error",
                        "tenant", tenantId,
                        "error", ex.getClass().getSimpleName());
                    logger.error("Policy evaluation failed: tenant={}, collection={}",
                        tenantId, collectionName, ex);
                }
            });
    }

    /**
     * Evaluates RBAC update policy.
     *
     * <p><b>Policy Inputs</b><br>
     * - Tenant context
     * - Role or permission being modified
     * - User requesting change
     * - Target user or group
     *
     * <p><b>Policy Rules</b><br>
     * - User has rbac:admin permission
     * - Cannot elevate own privileges
     * - Role assignments audited
     * - Permission grants validated
     *
     * @param tenantId the tenant identifier (required)
     * @param rbacChange the RBAC change descriptor (required)
     * @param userId the user requesting change (required)
     * @return Promise of PolicyDecision
     */
    public Promise<PolicyDecision> evaluateRbacChange(
            String tenantId,
            RbacChange rbacChange,
            String userId) {
        validateTenantId(tenantId);
        Objects.requireNonNull(rbacChange, "RBAC change must not be null");
        Objects.requireNonNull(userId, "User ID must not be null");

        Map<String, Object> policyInput = Map.of(
            "tenantId", tenantId,
            "changeType", rbacChange.getType(),
            "targetUserId", rbacChange.getTargetUserId(),
            "role", rbacChange.getRole(),
            "userId", userId,
            "timestamp", System.currentTimeMillis()
        );

        return policyEngine.evaluate("rbac_change", policyInput)
            .whenComplete((decision, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("policy.rbac_change.evaluated",
                        "tenant", tenantId,
                        "changeType", rbacChange.getType(),
                        "allowed", String.valueOf(decision.isAllowed()));
                    
                    if (decision.isAllowed()) {
                        logger.info("RBAC change approved: tenant={}, type={}, user={}, target={}",
                            tenantId, rbacChange.getType(), userId, rbacChange.getTargetUserId());
                    } else {
                        logger.warn("RBAC change denied: tenant={}, type={}, reason={}",
                            tenantId, rbacChange.getType(), decision.getReason());
                    }
                } else {
                    metrics.incrementCounter("policy.rbac_change.error",
                        "tenant", tenantId,
                        "error", ex.getClass().getSimpleName());
                }
            });
    }

    /**
     * Evaluates collection lifecycle policy.
     *
     * <p><b>Policy Inputs</b><br>
     * - Tenant context
     * - Operation (create, archive, delete)
     * - Collection metadata
     * - User requesting operation
     *
     * <p><b>Policy Rules</b><br>
     * - Create: tenant within collection quota
     * - Archive: collection has no active entities
     * - Delete: requires explicit approval
     * - User has collection:admin permission
     *
     * @param tenantId the tenant identifier (required)
     * @param operation the lifecycle operation (required)
     * @param collectionName the collection name (required)
     * @param userId the user requesting operation (required)
     * @return Promise of PolicyDecision
     */
    public Promise<PolicyDecision> evaluateCollectionLifecycle(
            String tenantId,
            LifecycleOperation operation,
            String collectionName,
            String userId) {
        validateTenantId(tenantId);
        Objects.requireNonNull(operation, "Operation must not be null");
        Objects.requireNonNull(collectionName, "Collection name must not be null");
        Objects.requireNonNull(userId, "User ID must not be null");

        Map<String, Object> policyInput = Map.of(
            "tenantId", tenantId,
            "operation", operation.name(),
            "collectionName", collectionName,
            "userId", userId,
            "timestamp", System.currentTimeMillis()
        );

        return policyEngine.evaluate("collection_lifecycle", policyInput)
            .whenComplete((decision, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("policy.collection_lifecycle.evaluated",
                        "tenant", tenantId,
                        "operation", operation.name(),
                        "allowed", String.valueOf(decision.isAllowed()));
                    
                    if (decision.isAllowed()) {
                        logger.info("Collection lifecycle approved: tenant={}, operation={}, collection={}, user={}",
                            tenantId, operation, collectionName, userId);
                    } else {
                        logger.warn("Collection lifecycle denied: tenant={}, operation={}, collection={}, reason={}",
                            tenantId, operation, collectionName, decision.getReason());
                    }
                } else {
                    metrics.incrementCounter("policy.collection_lifecycle.error",
                        "tenant", tenantId,
                        "error", ex.getClass().getSimpleName());
                }
            });
    }

    /**
     * Evaluates bulk data operation policy.
     *
     * <p><b>Policy Inputs</b><br>
     * - Tenant context
     * - Operation (import, export, purge)
     * - Collection name
     * - Record count
     * - User requesting operation
     *
     * <p><b>Policy Rules</b><br>
     * - Import/export within rate limits
     * - Purge requires explicit approval
     * - Large operations (>10k records) throttled
     * - User has data:bulk permission
     *
     * @param tenantId the tenant identifier (required)
     * @param operation the bulk operation (required)
     * @param collectionName the collection name (required)
     * @param recordCount the number of records (required)
     * @param userId the user requesting operation (required)
     * @return Promise of PolicyDecision
     */
    public Promise<PolicyDecision> evaluateBulkDataOperation(
            String tenantId,
            BulkOperation operation,
            String collectionName,
            int recordCount,
            String userId) {
        validateTenantId(tenantId);
        Objects.requireNonNull(operation, "Operation must not be null");
        Objects.requireNonNull(collectionName, "Collection name must not be null");
        Objects.requireNonNull(userId, "User ID must not be null");

        if (recordCount < 1) {
            throw new IllegalArgumentException("Record count must be positive");
        }

        Map<String, Object> policyInput = Map.of(
            "tenantId", tenantId,
            "operation", operation.name(),
            "collectionName", collectionName,
            "recordCount", recordCount,
            "userId", userId,
            "timestamp", System.currentTimeMillis()
        );

        return policyEngine.evaluate("bulk_data_operation", policyInput)
            .whenComplete((decision, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("policy.bulk_operation.evaluated",
                        "tenant", tenantId,
                        "operation", operation.name(),
                        "allowed", String.valueOf(decision.isAllowed()));
                    
                    if (decision.isAllowed()) {
                        logger.info("Bulk operation approved: tenant={}, operation={}, collection={}, count={}, user={}",
                            tenantId, operation, collectionName, recordCount, userId);
                    } else {
                        logger.warn("Bulk operation denied: tenant={}, operation={}, collection={}, count={}, reason={}",
                            tenantId, operation, collectionName, recordCount, decision.getReason());
                    }
                } else {
                    metrics.incrementCounter("policy.bulk_operation.error",
                        "tenant", tenantId,
                        "error", ex.getClass().getSimpleName());
                }
            });
    }

    /**
     * Validates tenant ID is not null or empty.
     *
     * @param tenantId the tenant ID to validate
     * @throws IllegalArgumentException if tenantId is null or empty
     */
    private void validateTenantId(String tenantId) {
        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new IllegalArgumentException("Tenant ID must not be null or empty");
        }
    }

    /**
     * Lifecycle operations for collections.
     */
    public enum LifecycleOperation {
        CREATE,
        ARCHIVE,
        DELETE,
        RESTORE
    }

    /**
     * Bulk data operations.
     */
    public enum BulkOperation {
        IMPORT,
        EXPORT,
        PURGE
    }
}
