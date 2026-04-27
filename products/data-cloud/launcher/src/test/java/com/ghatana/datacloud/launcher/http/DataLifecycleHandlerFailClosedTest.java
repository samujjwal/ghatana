/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.http.handlers.DataLifecycleHandler;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P0.4/P0.5: Governance fail-closed tests — verify handlers refuse to operate
 * when mandatory dependencies (audit service, event log store) are missing.
 */
@DisplayName("DataLifecycleHandler fail-closed behavior")
class DataLifecycleHandlerFailClosedTest {

    @Test
    @DisplayName("emitAudit throws when audit service is null")
    void emitAuditThrowsWhenAuditServiceNull() {
        DataCloudClient client = mock(DataCloudClient.class);
        when(client.entityStore()).thenReturn(null);

        HttpHandlerSupport http = new HttpHandlerSupport(
            new ObjectMapper(), "http://localhost:5173", "GET,POST,PUT,DELETE,OPTIONS", "Content-Type,X-Tenant-Id", false, "local");

        DataLifecycleHandler handler = new DataLifecycleHandler(client, new ObjectMapper(), http, null);

        // Use reflection to invoke the private emitAudit method through a public handler entry point
        // Since we can't easily call the private method, we verify through a public handler that calls it
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/compliance/summary").build();

        assertThatThrownBy(() -> {
            // handleComplianceSummary calls emitAudit indirectly through the audit service check
            // Since auditService is null, it should fail when trying to use it
            Promise<HttpResponse> result = handler.handleComplianceSummary(request);
            result.getResult();
        }).isInstanceOf(Exception.class)
          .hasMessageContaining("AuditService is required");
    }

    @Test
    @DisplayName("handler throws when entity store is not configured")
    void handlerThrowsWhenEntityStoreNotConfigured() {
        DataCloudClient client = mock(DataCloudClient.class);
        when(client.entityStore()).thenReturn(null);

        HttpHandlerSupport http = new HttpHandlerSupport(
            new ObjectMapper(), "http://localhost:5173", "GET,POST,PUT,DELETE,OPTIONS", "Content-Type,X-Tenant-Id", false, "local");

        DataLifecycleHandler handler = new DataLifecycleHandler(client, new ObjectMapper(), http, null);

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/compliance/summary").build();

        assertThatThrownBy(() -> {
            Promise<HttpResponse> result = handler.handleComplianceSummary(request);
            result.getResult();
        }).isInstanceOf(Exception.class)
          .hasMessageContaining("EntityStore is not configured");
    }
}
