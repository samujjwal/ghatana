/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.security;

/**
 * WS4-11: Canonical action permissions for AEP.
 *
 * <p>Defines action-specific permissions for role-based access control.
 * These permissions are owned by the action/security module and are
 * used to control access to action execution, pattern management,
 * workflow operations, and agent interactions.
 *
 * @doc.type class
 * @doc.purpose Canonical action permission definitions for AEP
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class ActionPermissions {

    // ==================== Action Execution Permissions ====================

    /**
     * Permission to read action definitions.
     */
    public static final String ACTION_READ = "action:read";

    /**
     * Permission to create actions.
     */
    public static final String ACTION_CREATE = "action:create";

    /**
     * Permission to update actions.
     */
    public static final String ACTION_UPDATE = "action:update";

    /**
     * Permission to delete actions.
     */
    public static final String ACTION_DELETE = "action:delete";

    /**
     * Permission to execute actions.
     */
    public static final String ACTION_EXECUTE = "action:execute";

    /**
     * Permission to read action execution results.
     */
    public static final String ACTION_RESULT_READ = "action:result:read";

    // ==================== Pattern Permissions ====================

    /**
     * Permission to read pattern definitions.
     */
    public static final String PATTERN_READ = "pattern:read";

    /**
     * Permission to create patterns.
     */
    public static final String PATTERN_CREATE = "pattern:create";

    /**
     * Permission to update patterns.
     */
    public static final String PATTERN_UPDATE = "pattern:update";

    /**
     * Permission to delete patterns.
     */
    public static final String PATTERN_DELETE = "pattern:delete";

    /**
     * Permission to activate patterns.
     */
    public static final String PATTERN_ACTIVATE = "pattern:activate";

    /**
     * Permission to deactivate patterns.
     */
    public static final String PATTERN_DEACTIVATE = "pattern:deactivate";

    /**
     * Permission to approve patterns.
     */
    public static final String PATTERN_APPROVE = "pattern:approve";

    // ==================== Workflow Permissions ====================

    /**
     * Permission to read workflow definitions.
     */
    public static final String WORKFLOW_READ = "workflow:read";

    /**
     * Permission to create workflows.
     */
    public static final String WORKFLOW_CREATE = "workflow:create";

    /**
     * Permission to update workflows.
     */
    public static final String WORKFLOW_UPDATE = "workflow:update";

    /**
     * Permission to delete workflows.
     */
    public static final String WORKFLOW_DELETE = "workflow:delete";

    /**
     * Permission to execute workflows.
     */
    public static final String WORKFLOW_EXECUTE = "workflow:execute";

    /**
     * Permission to approve workflows.
     */
    public static final String WORKFLOW_APPROVE = "workflow:approve";

    // ==================== Agent Permissions ====================

    /**
     * Permission to read agent descriptors.
     */
    public static final String AGENT_READ = "agent:read";

    /**
     * Permission to execute agents.
     */
    public static final String AGENT_EXECUTE = "agent:execute";

    /**
     * Permission to manage agents.
     */
    public static final String AGENT_MANAGE = "agent:manage";

    /**
     * Permission to read agent memory.
     */
    public static final String AGENT_MEMORY_READ = "agent:memory:read";

    /**
     * Permission to write agent memory.
     */
    public static final String AGENT_MEMORY_WRITE = "agent:memory:write";

    // ==================== Tool Permissions ====================

    /**
     * Permission to invoke tools.
     */
    public static final String TOOL_INVOKE = "tool:invoke";

    /**
     * Permission to read tool definitions.
     */
    public static final String TOOL_READ = "tool:read";

    /**
     * Permission to register tools.
     */
    public static final String TOOL_REGISTER = "tool:register";

    // ==================== Learning Permissions ====================

    /**
     * Permission to read learning data.
     */
    public static final String LEARNING_READ = "learning:read";

    /**
     * Permission to provide learning feedback.
     */
    public static final String LEARNING_FEEDBACK = "learning:feedback";

    /**
     * Permission to manage learning policies.
     */
    public static final String LEARNING_MANAGE = "learning:manage";

    // ==================== Governance Permissions ====================

    /**
     * Permission to read policy decisions.
     */
    public static final String POLICY_READ = "policy:read";

    /**
     * Permission to approve policy decisions.
     */
    public static final String POLICY_APPROVE = "policy:approve";

    /**
     * Permission to override policy decisions.
     */
    public static final String POLICY_OVERRIDE = "policy:override";

    /**
     * Permission to read audit logs.
     */
    public static final String AUDIT_READ = "audit:read";

    // ==================== HITL Permissions ====================

    /**
     * Permission to read approval requests.
     */
    public static final String APPROVAL_READ = "approval:read";

    /**
     * Permission to approve requests.
     */
    public static final String APPROVAL_APPROVE = "approval:approve";

    /**
     * Permission to deny requests.
     */
    public static final String APPROVAL_DENY = "approval:deny";

    /**
     * Permission to escalate requests.
     */
    public static final String APPROVAL_ESCALATE = "approval:escalate";

    // ==================== Admin Permissions ====================

    /**
     * Permission to manage action system configuration.
     */
    public static final String SYSTEM_CONFIG = "system:config";

    /**
     * Permission to manage action system metrics.
     */
    public static final String SYSTEM_METRICS = "system:metrics";

    /**
     * Permission to manage action system health.
     */
    public static final String SYSTEM_HEALTH = "system:health";

    private ActionPermissions() {
        // Prevent instantiation
    }
}
