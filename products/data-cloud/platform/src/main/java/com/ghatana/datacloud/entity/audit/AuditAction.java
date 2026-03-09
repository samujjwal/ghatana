package com.ghatana.datacloud.entity.audit;

/**
 * Enumeration of auditable actions in the system.
 *
 * <p><b>Purpose</b><br>
 * Defines all actions that should be tracked for compliance and audit trails.
 * Each action represents a significant state change in the system.
 *
 * <p><b>Action Categories</b><br>
 * - Collection: CREATE_COLLECTION, UPDATE_COLLECTION, DELETE_COLLECTION
 * - Entity: CREATE_ENTITY, UPDATE_ENTITY, DELETE_ENTITY
 * - Schema: CREATE_SCHEMA, UPDATE_SCHEMA, DELETE_SCHEMA
 * - User: CREATE_USER, UPDATE_USER, DELETE_USER, ROLE_ASSIGNED, ROLE_REVOKED
 * - Policy: CREATE_POLICY, UPDATE_POLICY, DELETE_POLICY, POLICY_APPLIED, POLICY_VIOLATED
 * - Workflow: CREATE_WORKFLOW, APPROVE_WORKFLOW, REJECT_WORKFLOW
 * - Access: ACCESS_GRANTED, ACCESS_DENIED
 *
 * @doc.type enum
 * @doc.purpose Auditable action enumeration
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public enum AuditAction {

    // Collection operations
    CREATE_COLLECTION("create_collection", "Collection created"),
    UPDATE_COLLECTION("update_collection", "Collection updated"),
    DELETE_COLLECTION("delete_collection", "Collection deleted"),

    // Entity operations
    CREATE_ENTITY("create_entity", "Entity created"),
    UPDATE_ENTITY("update_entity", "Entity updated"),
    DELETE_ENTITY("delete_entity", "Entity deleted"),

    // Schema operations
    CREATE_SCHEMA("create_schema", "Schema created"),
    UPDATE_SCHEMA("update_schema", "Schema updated"),
    DELETE_SCHEMA("delete_schema", "Schema deleted"),

    // User operations
    CREATE_USER("create_user", "User created"),
    UPDATE_USER("update_user", "User updated"),
    DELETE_USER("delete_user", "User deleted"),
    ROLE_ASSIGNED("role_assigned", "Role assigned to user"),
    ROLE_REVOKED("role_revoked", "Role revoked from user"),

    // Policy operations
    CREATE_POLICY("create_policy", "Policy created"),
    UPDATE_POLICY("update_policy", "Policy updated"),
    DELETE_POLICY("delete_policy", "Policy deleted"),
    POLICY_APPLIED("policy_applied", "Policy applied to content"),
    POLICY_VIOLATED("policy_violated", "Policy violation detected"),

    // Workflow operations
    CREATE_WORKFLOW("create_workflow", "Workflow created"),
    APPROVE_WORKFLOW("approve_workflow", "Workflow approved"),
    REJECT_WORKFLOW("reject_workflow", "Workflow rejected"),

    // Access operations
    ACCESS_GRANTED("access_granted", "User access granted"),
    ACCESS_DENIED("access_denied", "User access denied"),

    // Admin operations
    EXPORT_DATA("export_data", "Data exported"),
    IMPORT_DATA("import_data", "Data imported"),
    BACKUP_CREATED("backup_created", "Backup created"),
    RESTORE_BACKUP("restore_backup", "Backup restored");

    private final String actionId;
    private final String description;

    /**
     * Constructs AuditAction.
     *
     * @param actionId unique action identifier
     * @param description human-readable description
     */
    AuditAction(String actionId, String description) {
        this.actionId = actionId;
        this.description = description;
    }

    /**
     * Gets action identifier.
     *
     * @return action ID (e.g., "create_entity")
     */
    public String getActionId() {
        return actionId;
    }

    /**
     * Gets action description.
     *
     * @return human-readable description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Parses action from string identifier.
     *
     * @param actionId action identifier (case-insensitive)
     * @return AuditAction enum member
     * @throws IllegalArgumentException if action ID not found
     * @throws NullPointerException if actionId is null
     */
    public static AuditAction fromActionId(String actionId) {
        if (actionId == null) {
            throw new NullPointerException("actionId cannot be null");
        }
        for (AuditAction action : values()) {
            if (action.actionId.equalsIgnoreCase(actionId)) {
                return action;
            }
        }
        throw new IllegalArgumentException("Unknown action ID: " + actionId);
    }
}
