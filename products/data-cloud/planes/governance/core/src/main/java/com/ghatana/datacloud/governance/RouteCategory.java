/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance;

/**
 * Enumeration of route categories for policy evaluation.
 *
 * <p>Each route in the Data-Cloud API belongs to a category, and policies
 * can be applied at the category level to enforce governance rules.
 *
 * @doc.type enum
 * @doc.purpose Route category taxonomy for policy evaluation
 * @doc.layer product
 * @doc.pattern Enumeration
 */
public enum RouteCategory {

    /**
     * Entity CRUD operations (create, read, update, delete entities).
     */
    ENTITY_CRUD,

    /**
     * Event operations (append, read, tail events).
     */
    EVENT_OPERATIONS,

    /**
     * Configuration operations (manage collections, schemas, fields).
     */
    CONFIGURATION,

    /**
     * Governance operations (policies, audit, retention).
     */
    GOVERNANCE,

    /**
     * Operations operations (health checks, metrics, backup).
     */
    OPERATIONS,

    /**
     * Analytics operations (queries, exports, reports).
     */
    ANALYTICS,

    /**
     * Admin operations (tenant management, user management).
     */
    ADMIN
}
