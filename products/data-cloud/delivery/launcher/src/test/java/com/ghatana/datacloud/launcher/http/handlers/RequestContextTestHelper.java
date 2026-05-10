package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.launcher.http.security.RequestContext;
import com.ghatana.datacloud.launcher.http.security.RequestContextResolver;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.http.security.filter.TenantExtractor;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * Test helper for creating properly configured HttpRequest objects that work with RequestContextResolver.
 *
 * This helper creates HttpRequest mocks that have all the necessary stubs for RequestContextResolver
 * to successfully resolve tenant and context in test scenarios.
 *
 * @doc.type test helper
 * @doc.purpose Simplify RequestContextResolver test setup
 * @doc.layer product
 */
public final class RequestContextTestHelper {

    private RequestContextTestHelper() {
        // Utility class
    }

    /**
     * Creates a test HttpRequest with proper tenant resolution stubs for local profile testing.
     *
     * @param tenantId the tenant ID to use
     * @param connectionId the connection ID path parameter (for handler tests)
     * @param path the request path
     * @return a properly stubbed HttpRequest mock
     */
    public static HttpRequest createTestRequest(String tenantId, String connectionId, String path) {
        HttpRequest request = Mockito.mock(HttpRequest.class);
        
        // Stub path parameter (for handler path parameters)
        if (connectionId != null) {
            lenient().when(request.getPathParameter("connectionId")).thenReturn(connectionId);
        }
        
        // Stub header with TenantExtractor constant (required for RequestContextResolver)
        lenient().when(request.getHeader(TenantExtractor.TENANT_HEADER)).thenReturn(tenantId);
        
        // Stub path (required for RequestContextResolver logging)
        lenient().when(request.getPath()).thenReturn(path);
        
        // Stub query parameter (required for RequestContextResolver spoofing protection)
        lenient().when(request.getQueryParameter("tenantId")).thenReturn(null);
        
        // Stub method (required for RequestContext)
        lenient().when(request.getMethod()).thenReturn(io.activej.http.HttpMethod.POST);
        
        return request;
    }

    /**
     * Creates a test HttpRequest with body support.
     *
     * @param tenantId the tenant ID to use
     * @param connectionId the connection ID path parameter
     * @param path the request path
     * @param body the request body
     * @return a properly stubbed HttpRequest mock
     */
    public static HttpRequest createTestRequestWithBody(
            String tenantId,
            String connectionId,
            String path,
            io.activej.bytebuf.ByteBuf body) {
        HttpRequest request = createTestRequest(tenantId, connectionId, path);
        lenient().when(request.loadBody()).thenReturn(io.activej.promise.Promise.of(body));
        return request;
    }
}
