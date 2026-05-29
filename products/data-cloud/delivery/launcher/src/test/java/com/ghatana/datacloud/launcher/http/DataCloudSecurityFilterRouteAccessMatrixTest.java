/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.governance.PolicyEngine;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.governance.security.ApiKeyResolver;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies route-level authorization behavior from canonical route metadata.
 *
 * <p>This test runs the real filter decision path against every authenticated route
 * from {@link RouteSecurityRegistry}. For each route, it validates one role that
 * must be allowed and one role that must be denied based on the route's required access.
 *
 * @doc.type class
 * @doc.purpose Route-level authorization matrix validation for Data Cloud security filter
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudSecurityFilter route access matrix")
class DataCloudSecurityFilterRouteAccessMatrixTest extends EventloopTestBase {

    private static final String TENANT_ID = "test-tenant";
    private static final String KEY_ADMIN = "k-admin";
    private static final String KEY_OPERATOR = "k-operator";
    private static final String KEY_AUDITOR = "k-auditor";
    private static final String KEY_VIEWER = "k-viewer";
    private static final String KEY_API_CLIENT = "k-api-client";

    private static final AsyncServlet OK_DELEGATE = request -> Promise.of(HttpResponse.ok200().build());

    private ApiKeyResolver apiKeyResolver;
    private PolicyEngine policyEngine;
    private AuditService auditService;
    private AsyncServlet secured;

    @BeforeEach
    void setUp() {
        apiKeyResolver = mock(ApiKeyResolver.class);
        policyEngine = mock(PolicyEngine.class);
        auditService = mock(AuditService.class);

        Map<String, Principal> principalsByKey = Map.of(
                KEY_ADMIN, new Principal("admin-user", List.of("admin"), TENANT_ID),
                KEY_OPERATOR, new Principal("operator-user", List.of("operator"), TENANT_ID),
                KEY_AUDITOR, new Principal("auditor-user", List.of("auditor"), TENANT_ID),
                KEY_VIEWER, new Principal("viewer-user", List.of("viewer"), TENANT_ID),
                KEY_API_CLIENT, new Principal("api-client-user", List.of("api_client"), TENANT_ID)
        );

        when(apiKeyResolver.resolve(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(principalsByKey.get(invocation.getArgument(0))));
        when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.TRUE));
        when(auditService.record(any(AuditEvent.class))).thenReturn(Promise.of((Void) null));

        DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                .apiKeyResolver(apiKeyResolver)
                .policyEngine(policyEngine)
                .auditService(auditService)
                .deploymentProfile("production")
                .enforcing(true)
                .build();

        secured = filter.apply(OK_DELEGATE);
    }

    @Test
    @DisplayName("authenticated routes enforce required access for allowed and denied roles")
    void authenticatedRoutesEnforceRequiredAccess() {
        RouteSecurityRegistry.allRoutes().forEach((routeKey, metadata) -> {
            if (!metadata.requiresAuth()) {
                return;
            }

            AccessExpectation expectation = expectationFor(metadata.requiredAccess());
            String runtimePath = toRuntimePath(metadata.canonicalPath());

            int allowedStatus = runPromise(() -> secured
                    .serve(request(metadata.method(), runtimePath, expectation.allowedKey()))
                    .map(HttpResponse::getCode));

            assertThat(allowedStatus)
                    .as("allowed role should pass for %s", routeKey)
                    .isEqualTo(200);

            if (expectation.deniedKey() != null) {
                int deniedStatus = runPromise(() -> secured
                        .serve(request(metadata.method(), runtimePath, expectation.deniedKey()))
                        .map(HttpResponse::getCode));

                assertThat(deniedStatus)
                        .as("denied role should be blocked for %s", routeKey)
                        .isEqualTo(403);
            }
        });
    }

    private static AccessExpectation expectationFor(DataCloudSecurityFilter.AccessLevel requiredAccess) {
        return switch (requiredAccess) {
            case NONE -> new AccessExpectation(KEY_VIEWER, null);
            case VIEWER -> new AccessExpectation(KEY_VIEWER, KEY_API_CLIENT);
            case AUDITOR -> new AccessExpectation(KEY_AUDITOR, KEY_OPERATOR);
            case OPERATOR -> new AccessExpectation(KEY_OPERATOR, KEY_VIEWER);
            case ADMIN -> new AccessExpectation(KEY_ADMIN, KEY_OPERATOR);
        };
    }

    private static String toRuntimePath(String canonicalPath) {
        return canonicalPath.replaceAll("\\{[^}]+}", "sample");
    }

    private static HttpRequest request(String method, String path, String apiKey) {
        HttpRequest.Builder builder = switch (method) {
            case "POST" -> HttpRequest.post("http://localhost" + path);
            case "PUT" -> HttpRequest.put("http://localhost" + path);
            case "DELETE" -> HttpRequest.builder(HttpMethod.DELETE, "http://localhost" + path);
            case "PATCH" -> HttpRequest.builder(HttpMethod.PATCH, "http://localhost" + path);
            default -> HttpRequest.get("http://localhost" + path);
        };

        return builder
                .withHeader(HttpHeaders.of("X-API-Key"), apiKey)
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TENANT_ID)
                .withHeader(HttpHeaders.HOST, "localhost")
                .build();
    }

    private record AccessExpectation(String allowedKey, String deniedKey) {
    }
}