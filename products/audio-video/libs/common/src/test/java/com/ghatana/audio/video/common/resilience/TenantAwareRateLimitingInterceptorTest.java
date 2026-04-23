package com.ghatana.audio.video.common.resilience;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for per-tenant gRPC rate limiting (AV-P2-04) // GH-90000
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("Tenant-Aware Rate Limiting Interceptor Tests (AV-P2-04)")
class TenantAwareRateLimitingInterceptorTest {

    @Test
    @DisplayName("Should allow call within per-tenant limit")
    void shouldAllowCallWithinTenantLimit() { // GH-90000
        TenantAwareRateLimitingInterceptor interceptor =
                new TenantAwareRateLimitingInterceptor(50.0, Map.of("premium-tenant", 100.0)); // GH-90000

        Metadata headers = withTenantId("premium-tenant");
        @SuppressWarnings("unchecked")
        ServerCall<String, String> call = createServerCall("av.STT/Transcribe");
        @SuppressWarnings("unchecked")
        ServerCallHandler<String, String> next = mock(ServerCallHandler.class); // GH-90000
        when(next.startCall(any(), eq(headers))).thenReturn(new ServerCall.Listener<>() {}); // GH-90000

        interceptor.interceptCall(call, headers, next); // GH-90000

        verify(next).startCall(any(), eq(headers)); // GH-90000
        verify(call, never()).close(any(), any()); // GH-90000
    }

    @Test
    @DisplayName("Should reject call when rate exhausted for premium tenant with low override")
    void shouldRejectWhenTenantQuotaExhausted() { // GH-90000
        // Set a very low per-tenant quota (1 TPS, burst 1) to exhaust immediately // GH-90000
        TenantAwareRateLimitingInterceptor interceptor =
                new TenantAwareRateLimitingInterceptor(50.0, Map.of("low-quota-tenant", 0.001)); // GH-90000

        Metadata headers = withTenantId("low-quota-tenant");
        @SuppressWarnings("unchecked")
        ServerCallHandler<String, String> next = mock(ServerCallHandler.class); // GH-90000
        when(next.startCall(any(), any())).thenReturn(new ServerCall.Listener<>() {}); // GH-90000

        // Exhaust the burst
        for (int i = 0; i < 20; i++) { // GH-90000
            interceptor.interceptCall(createServerCall("av.STT/Transcribe"), headers, next);
        }

        // Next call should be rejected
        @SuppressWarnings("unchecked")
        ServerCall<String, String> rejectedCall = createServerCall("av.STT/Transcribe");
        interceptor.interceptCall(rejectedCall, headers, next); // GH-90000

        ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class); // GH-90000
        verify(rejectedCall, atLeastOnce()).close(statusCaptor.capture(), any()); // GH-90000

        boolean anyResourceExhausted = statusCaptor.getAllValues().stream() // GH-90000
                .anyMatch(s -> s.getCode() == Status.Code.RESOURCE_EXHAUSTED); // GH-90000
        assertThat(anyResourceExhausted).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Tenant without explicit quota falls back to global default")
    void shouldUseDefaultLimitForUnknownTenant() { // GH-90000
        TenantAwareRateLimitingInterceptor interceptor =
                new TenantAwareRateLimitingInterceptor(50.0, Map.of()); // GH-90000

        Metadata headers = withTenantId("unknown-tenant");
        @SuppressWarnings("unchecked")
        ServerCall<String, String> call = createServerCall("av.STT/Transcribe");
        @SuppressWarnings("unchecked")
        ServerCallHandler<String, String> next = mock(ServerCallHandler.class); // GH-90000
        when(next.startCall(any(), eq(headers))).thenReturn(new ServerCall.Listener<>() {}); // GH-90000

        interceptor.interceptCall(call, headers, next); // GH-90000

        // Should proceed without rejection
        verify(next).startCall(any(), eq(headers)); // GH-90000
    }

    @Test
    @DisplayName("Null tenant ID uses global default limiter")
    void shouldHandleNullTenantId() { // GH-90000
        TenantAwareRateLimitingInterceptor interceptor =
                new TenantAwareRateLimitingInterceptor(50.0, Map.of()); // GH-90000

        Metadata headers = new Metadata(); // no tenant header // GH-90000
        @SuppressWarnings("unchecked")
        ServerCall<String, String> call = createServerCall("av.Vision/Detect");
        @SuppressWarnings("unchecked")
        ServerCallHandler<String, String> next = mock(ServerCallHandler.class); // GH-90000
        when(next.startCall(any(), eq(headers))).thenReturn(new ServerCall.Listener<>() {}); // GH-90000

        interceptor.interceptCall(call, headers, next); // GH-90000

        verify(next).startCall(any(), eq(headers)); // GH-90000
    }

    @Test
    @DisplayName("updateTenantQuota replaces existing limit at runtime")
    void shouldUpdateTenantQuotaAtRuntime() { // GH-90000
        TenantAwareRateLimitingInterceptor interceptor =
                new TenantAwareRateLimitingInterceptor(50.0, Map.of()); // GH-90000

        // Apply a very high quota so calls proceed
        interceptor.updateTenantQuota("dynamic-tenant", 100.0); // GH-90000

        Metadata headers = withTenantId("dynamic-tenant");
        @SuppressWarnings("unchecked")
        ServerCall<String, String> call = createServerCall("av.STT/Transcribe");
        @SuppressWarnings("unchecked")
        ServerCallHandler<String, String> next = mock(ServerCallHandler.class); // GH-90000
        when(next.startCall(any(), eq(headers))).thenReturn(new ServerCall.Listener<>() {}); // GH-90000

        interceptor.interceptCall(call, headers, next); // GH-90000

        verify(next).startCall(any(), eq(headers)); // GH-90000
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private static Metadata withTenantId(String tenantId) { // GH-90000
        Metadata headers = new Metadata(); // GH-90000
        headers.put(TenantAwareRateLimitingInterceptor.TENANT_ID_KEY, tenantId); // GH-90000
        return headers;
    }

    @SuppressWarnings("unchecked")
    private static <ReqT, RespT> ServerCall<ReqT, RespT> createServerCall(String fullMethodName) { // GH-90000
        ServerCall<ReqT, RespT> call = mock(ServerCall.class); // GH-90000
        MethodDescriptor<ReqT, RespT> descriptor = MethodDescriptor.<ReqT, RespT>newBuilder() // GH-90000
                .setType(MethodDescriptor.MethodType.UNARY) // GH-90000
                .setFullMethodName(fullMethodName) // GH-90000
                .setRequestMarshaller(byteMarshaller()) // GH-90000
                .setResponseMarshaller(byteMarshaller()) // GH-90000
                .build(); // GH-90000
        when(call.getMethodDescriptor()).thenReturn(descriptor); // GH-90000
        return call;
    }

    @SuppressWarnings("unchecked")
    private static <T> MethodDescriptor.Marshaller<T> byteMarshaller() { // GH-90000
        return (MethodDescriptor.Marshaller<T>) new MethodDescriptor.Marshaller<String>() { // GH-90000
            @Override
            public InputStream stream(String value) { // GH-90000
                return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)); // GH-90000
            }

            @Override
            public String parse(InputStream stream) { // GH-90000
                return "parsed";
            }
        };
    }
}

