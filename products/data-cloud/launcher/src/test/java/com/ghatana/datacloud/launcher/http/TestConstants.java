/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;


/**
 * Shared test constants for all Data Cloud test suites.
 *
 * <p><strong>No Duplication Rule:</strong> All tests use constants from this class.
 * If you define a test constant (token, tenant ID, entity ID, etc.) in multiple test files, // GH-90000
 * it belongs here.
 *
 * <p>Example usage:
 * <pre>
 * {@code
 * @Test
 * void test() throws Exception { // GH-90000
 *   Map<String, String> headers = withAuthAndTenant( // GH-90000
 *       TestConstants.VALID_AUTH_TOKEN,
 *       TestConstants.TENANT_ALPHA
 *   );
 *   HttpResponse<String> resp = postJson("/api/v1/entities/products", // GH-90000
 *       TestConstants.validPipeline(), headers); // GH-90000
 * }
 * }
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Shared test constants and fixtures (REUSABLE, NO DUPLICATION) // GH-90000
 * @doc.layer product
 * @doc.pattern Constant
 */
public final class TestConstants {

    private TestConstants() {} // GH-90000

    // ─────────────────────────────────────────────────────────────────────────
    // Authentication & Tenant
    // ─────────────────────────────────────────────────────────────────────────

    /** Valid JWT-like bearer token (for authentication tests). */ // GH-90000
    public static final String VALID_AUTH_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
            + "eyJzdWIiOiJ1c2VyLWEiLCJ0ZW5hbnQiOiJ0ZW5hbnQtYSIsImlhdCI6MTcwMDAwMDAwMH0."
            + "test_signature";

    /** Invalid/expired bearer token (for auth failure tests). */ // GH-90000
    public static final String INVALID_AUTH_TOKEN = "invalid.auth.token";

    /** Default tenant ID for tests (isolation boundary). */ // GH-90000
    public static final String TENANT_DEFAULT = "tenant-default";

    /** Tenant Alpha (for multi-tenant isolation tests). */ // GH-90000
    public static final String TENANT_ALPHA = "tenant-alpha";

    /** Tenant Beta (for multi-tenant isolation tests). */ // GH-90000
    public static final String TENANT_BETA = "tenant-beta";

    /** Tenant Gamma (for multi-tenant isolation tests). */ // GH-90000
    public static final String TENANT_GAMMA = "tenant-gamma";

    /**  Admin user ID (for rbac tests). */ // GH-90000
    public static final String USER_ADMIN = "user-admin";

    /** Regular user ID (for permission boundary tests). */ // GH-90000
    public static final String USER_REGULAR = "user-regular";

    // ─────────────────────────────────────────────────────────────────────────
    // Collections & Entities
    // ─────────────────────────────────────────────────────────────────────────

    /** Default collection name for entity tests. */
    public static final String COLLECTION_PRODUCTS = "products";

    /** Alternative collection name for isolation tests. */
    public static final String COLLECTION_CUSTOMERS = "customers";

    /** Valid entity ID (UUID format). */ // GH-90000
    public static final String ENTITY_ID_1 = "550e8400-e29b-41d4-a716-446655440000";

    /** Alternative valid entity ID. */
    public static final String ENTITY_ID_2 = "550e8400-e29b-41d4-a716-446655440001";

    /** Invalid entity ID (non-UUID). */ // GH-90000
    public static final String ENTITY_ID_INVALID = "not-a-uuid";

    // ─────────────────────────────────────────────────────────────────────────
    // Pipelines & Workflows
    // ─────────────────────────────────────────────────────────────────────────

    /** Valid pipeline ID (string). */ // GH-90000
    public static final String PIPELINE_ID_1 = "pipeline-001";

    /** Alternative valid pipeline ID. */
    public static final String PIPELINE_ID_2 = "pipeline-002";

    /** Default pipeline name for tests. */
    public static final String PIPELINE_NAME_DEFAULT = "Default ETL Pipeline";

    // ─────────────────────────────────────────────────────────────────────────
    // Checkpoints & Events
    // ─────────────────────────────────────────────────────────────────────────

    /** Valid checkpoint ID. */
    public static final String CHECKPOINT_ID_1 = "checkpoint-001";

    /** Event offset (starting point for stream reads). */ // GH-90000
    public static final String EVENT_OFFSET_0 = "0";

    /** Event offset after append. */
    public static final String EVENT_OFFSET_1 = "1";

    // ─────────────────────────────────────────────────────────────────────────
    // Memory & Brain
    // ─────────────────────────────────────────────────────────────────────────

    /** Agent ID for memory tests. */
    public static final String AGENT_ID_1 = "agent-ai-001";

    /** Memory tier: episodic (short-term). */ // GH-90000
    public static final String MEMORY_TIER_EPISODIC = "episodic";

    /** Memory tier: semantic (long-term knowledge). */ // GH-90000
    public static final String MEMORY_TIER_SEMANTIC = "semantic";

    /** Memory tier: procedural (skills/patterns). */ // GH-90000
    public static final String MEMORY_TIER_PROCEDURAL = "procedural";

    // ─────────────────────────────────────────────────────────────────────────
    // HTTP Status Codes (for assertion clarity) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    public static final int HTTP_OK = 200;
    public static final int HTTP_CREATED = 201;
    public static final int HTTP_NO_CONTENT = 204;
    public static final int HTTP_BAD_REQUEST = 400;
    public static final int HTTP_UNAUTHORIZED = 401;
    public static final int HTTP_FORBIDDEN = 403;
    public static final int HTTP_NOT_FOUND = 404;
    public static final int HTTP_CONFLICT = 409;
    public static final int HTTP_UNPROCESSABLE_ENTITY = 422;
    public static final int HTTP_INTERNAL_ERROR = 500;
    public static final int HTTP_NOT_IMPLEMENTED = 501;

    // ─────────────────────────────────────────────────────────────────────────
    // Timeouts
    // ─────────────────────────────────────────────────────────────────────────

    /** Server startup timeout (milliseconds). */ // GH-90000
    public static final long TIMEOUT_SERVER_START_MS = 5000;

    /** HTTP request timeout (milliseconds). */ // GH-90000
    public static final long TIMEOUT_HTTP_REQUEST_MS = 10000;

    /** Async operation timeout (milliseconds). */ // GH-90000
    public static final long TIMEOUT_ASYNC_MS = 3000;
}
