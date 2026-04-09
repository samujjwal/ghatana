package com.ghatana.security.grpc;

import com.ghatana.platform.security.model.User;
import com.ghatana.security.audit.AuditEmitter;
import com.ghatana.security.govern.IamPolicyEnforcer;
import com.ghatana.security.govern.PolicyEnforcementResult;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Tests gRPC policy enforcement correlation and context propagation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PolicyEnforcementInterceptor")
@SuppressWarnings("deprecation")
class PolicyEnforcementInterceptorTest {

    private static final String METHOD_NAME = "ghatana.contracts.learning.v1.PatternLearningService/RecommendPatterns";

    @Test
    @DisplayName("propagates provided trace ID into gRPC context and response headers")
    void propagatesProvidedTraceIdIntoContextAndHeaders() throws Exception {
        IamPolicyEnforcer policyEnforcer = mock(IamPolicyEnforcer.class);
        AuditEmitter auditEmitter = mock(AuditEmitter.class);
        UserAuthenticationService authService = mock(UserAuthenticationService.class);

        User user = new User("user-1", "alice", Set.of("pattern-admin"));
        when(authService.authenticate("jwt-token")).thenReturn(user);
        when(policyEnforcer.enforcePatternRecommendPolicy(eq(user), eq("tenant-42"), eq("patterns/recommend")))
                .thenReturn(PolicyEnforcementResult.granted("ok"));

        PolicyEnforcementInterceptor interceptor = new PolicyEnforcementInterceptor(policyEnforcer, auditEmitter, authService);
        ServerCall<Object, Object> call = mockCall(METHOD_NAME);
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer jwt-token");
        headers.put(Metadata.Key.of("x-tenant-id", Metadata.ASCII_STRING_MARSHALLER), "tenant-42");
        headers.put(Metadata.Key.of("x-trace-id", Metadata.ASCII_STRING_MARSHALLER), "trace-123");

        AtomicReference<String> contextCorrelation = new AtomicReference<>();
        AtomicReference<String> contextTenant = new AtomicReference<>();
        AtomicReference<String> mdcCorrelation = new AtomicReference<>();
        @SuppressWarnings("unchecked")
        ServerCallHandler<Object, Object> handler = mock(ServerCallHandler.class);
        when(handler.startCall(any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ServerCall<Object, Object> interceptedCall = invocation.getArgument(0, ServerCall.class);
            contextCorrelation.set(PolicyEnforcementInterceptor.getCurrentCorrelationId());
            contextTenant.set(PolicyEnforcementInterceptor.getCurrentTenantId());
            mdcCorrelation.set(MDC.get("correlationId"));
            interceptedCall.sendHeaders(new Metadata());
            return mock(ServerCall.Listener.class);
        });

        interceptor.interceptCall(call, headers, handler);

        assertThat(contextCorrelation.get()).isEqualTo("trace-123");
        assertThat(contextTenant.get()).isEqualTo("tenant-42");
        assertThat(mdcCorrelation.get()).isEqualTo("trace-123");
        assertThat(MDC.get("correlationId")).isNull();

        ArgumentCaptor<Metadata> headerCaptor = ArgumentCaptor.forClass(Metadata.class);
        verify(call).sendHeaders(headerCaptor.capture());
        assertThat(headerCaptor.getValue().get(Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER)))
                .isEqualTo("trace-123");
        verify(call, never()).close(any(), any());
    }

    @Test
    @DisplayName("adds generated correlation ID to unauthenticated failure trailers")
    void addsGeneratedCorrelationIdToUnauthenticatedFailureTrailers() {
        IamPolicyEnforcer policyEnforcer = mock(IamPolicyEnforcer.class);
        AuditEmitter auditEmitter = mock(AuditEmitter.class);
        UserAuthenticationService authService = mock(UserAuthenticationService.class);

        PolicyEnforcementInterceptor interceptor = new PolicyEnforcementInterceptor(policyEnforcer, auditEmitter, authService);
        ServerCall<Object, Object> call = mockCall(METHOD_NAME);
        @SuppressWarnings("unchecked")
        ServerCallHandler<Object, Object> handler = mock(ServerCallHandler.class);

        interceptor.interceptCall(call, new Metadata(), handler);

        ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class);
        ArgumentCaptor<Metadata> trailerCaptor = ArgumentCaptor.forClass(Metadata.class);
        verify(call).close(statusCaptor.capture(), trailerCaptor.capture());
        assertThat(statusCaptor.getValue().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(trailerCaptor.getValue().get(Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER)))
                .isNotBlank();
    }

    @SuppressWarnings("unchecked")
    private static <ReqT, RespT> ServerCall<ReqT, RespT> mockCall(String fullMethod) {
        ServerCall<ReqT, RespT> call = mock(ServerCall.class);
        MethodDescriptor<ReqT, RespT> descriptor = MethodDescriptor.<ReqT, RespT>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(fullMethod)
                .setRequestMarshaller(new PassThroughMarshaller<>())
                .setResponseMarshaller(new PassThroughMarshaller<>())
                .build();
        when(call.getMethodDescriptor()).thenReturn(descriptor);
        return call;
    }

    private static class PassThroughMarshaller<T> implements MethodDescriptor.Marshaller<T> {
        @Override
        public InputStream stream(T value) {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        @SuppressWarnings("unchecked")
        public T parse(InputStream stream) {
            return (T) new Object();
        }
    }
}
