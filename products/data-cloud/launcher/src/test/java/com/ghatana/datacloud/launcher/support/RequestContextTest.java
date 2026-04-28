/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Tests for RequestContext MDC binding
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("RequestContext")
class RequestContextTest {

    @AfterEach
    void tearDown() {
        // Clean up MDC after each test
        org.slf4j.MDC.clear();
    }

    @Test
    @DisplayName("bind requestId and tenantId to MDC")
    void bind_setsRequestIdAndTenantId() {
        try (RequestContext ctx = RequestContext.bind("req-123", "tenant-abc")) {
            assertThat(org.slf4j.MDC.get("requestId")).isEqualTo("req-123");
            assertThat(org.slf4j.MDC.get("tenantId")).isEqualTo("tenant-abc");
        }
        // After close, MDC should be cleared
        assertThat(org.slf4j.MDC.get("requestId")).isNull();
        assertThat(org.slf4j.MDC.get("tenantId")).isNull();
    }

    @Test
    @DisplayName("bind with full request context")
    void bindWithFullContext_setsAllKeys() {
        try (RequestContext ctx = RequestContext.bind("req-123", "tenant-abc", "trace-456", "GET", "/api/test")) {
            assertThat(org.slf4j.MDC.get("requestId")).isEqualTo("req-123");
            assertThat(org.slf4j.MDC.get("tenantId")).isEqualTo("tenant-abc");
            assertThat(org.slf4j.MDC.get("traceId")).isEqualTo("trace-456");
            assertThat(org.slf4j.MDC.get("httpMethod")).isEqualTo("GET");
            assertThat(org.slf4j.MDC.get("httpPath")).isEqualTo("/api/test");
        }
    }

    @Test
    @DisplayName("bindRequestId sets only requestId")
    void bindRequestId_setsOnlyRequestId() {
        try (RequestContext ctx = RequestContext.bindRequestId("req-123")) {
            assertThat(org.slf4j.MDC.get("requestId")).isEqualTo("req-123");
            assertThat(org.slf4j.MDC.get("tenantId")).isNull();
        }
    }

    @Test
    @DisplayName("bindRequest sets request-related keys")
    void bindRequest_setsRequestKeys() {
        try (RequestContext ctx = RequestContext.bindRequest("req-123", "trace-456", "GET", "/api/test")) {
            assertThat(org.slf4j.MDC.get("requestId")).isEqualTo("req-123");
            assertThat(org.slf4j.MDC.get("traceId")).isEqualTo("trace-456");
            assertThat(org.slf4j.MDC.get("httpMethod")).isEqualTo("GET");
            assertThat(org.slf4j.MDC.get("httpPath")).isEqualTo("/api/test");
            assertThat(org.slf4j.MDC.get("tenantId")).isNull();
        }
    }

    @Test
    @DisplayName("bindPrincipal sets principal")
    void bindPrincipal_setsPrincipal() {
        try (RequestContext ctx = RequestContext.bindPrincipal("user-123")) {
            assertThat(org.slf4j.MDC.get("principal")).isEqualTo("user-123");
        }
    }

    @Test
    @DisplayName("close clears all bound keys")
    void close_clearsBoundKeys() {
        RequestContext ctx = RequestContext.bind("req-123", "tenant-abc");
        assertThat(org.slf4j.MDC.get("requestId")).isEqualTo("req-123");
        ctx.close();
        assertThat(org.slf4j.MDC.get("requestId")).isNull();
    }

    static void clearAll() {
        org.slf4j.MDC.clear();
    }
}
