package com.ghatana.audio.video.common.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link JwtServerInterceptor} — covering startup validation, token rejection,
 * and health-method exemption.
 *
 * @doc.type class
 * @doc.purpose JWT interceptor startup-fail and token validation tests (Day 6 / ADR-019) // GH-90000
 * @doc.layer product
 * @doc.pattern ValidationTest
 */
@DisplayName("JWT Server Interceptor Tests")
class JwtServerInterceptorTest {

    private static final String HEALTH_METHOD = "/grpc.health.v1.Health/Check";
    private static final String SECURE_METHOD = "/com.ghatana.tts.grpc.TtsService/Synthesize";

    /**
    * Env variables cannot be set at runtime in Java, so we test startup
    * behavior by reading the actual env values (expected to be absent in CI). // GH-90000
     */
    @Nested
    @DisplayName("Startup validation")
    class StartupValidation {

        @Test
        @DisplayName("Constructor throws when AV_JWT_SECRET is absent")
        void constructorThrowsWhenSecretAbsent() { // GH-90000
            String secret = System.getenv("AV_JWT_SECRET");
            if (secret != null && !secret.isBlank()) { // GH-90000
                return;
            }
            assertThatThrownBy(JwtServerInterceptor::new) // GH-90000
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("AV_JWT_SECRET")
                    .hasMessageContaining("does not support permissive bypass mode");
        }
    }

    @Nested
    @DisplayName("Health method exemption")
    class HealthMethodExemption {

        @Test
        @DisplayName("Health Check method bypasses JWT validation")
        void healthCheckBypassesValidation() { // GH-90000
            @SuppressWarnings("unchecked")
            ConfigurableJWTProcessor<SecurityContext> processor = mock(ConfigurableJWTProcessor.class); // GH-90000
            JwtServerInterceptor interceptor = new JwtServerInterceptor(processor, disabledTokenValidator()); // GH-90000

            ServerCall<Object, Object> call = mockCall(HEALTH_METHOD); // GH-90000
            @SuppressWarnings("unchecked")
            ServerCallHandler<Object, Object> handler = mock(ServerCallHandler.class); // GH-90000
            @SuppressWarnings("unchecked")
            ServerCall.Listener<Object> listener = mock(ServerCall.Listener.class); // GH-90000
            when(handler.startCall(any(), any())).thenReturn(listener); // GH-90000

            interceptor.interceptCall(call, new Metadata(), handler); // GH-90000

            // Health check must be passed through — no close() call // GH-90000
            verify(call, never()).close(any(), any()); // GH-90000
            verify(handler).startCall(eq(call), any()); // GH-90000
        }
    }

    @Nested
    @DisplayName("Missing token rejection")
    class MissingTokenRejection {

        @Test
        @DisplayName("Strict mode rejects requests with no Authorization header")
        void strictModeRejectsMissingAuthorizationHeader() { // GH-90000
            String secret = System.getenv("AV_JWT_SECRET");
            if (secret == null || secret.isBlank()) { // GH-90000
                // Cannot run strict-mode test without a real secret
                return;
            }

            JwtServerInterceptor interceptor = new JwtServerInterceptor(); // GH-90000
            ServerCall<Object, Object> call = mockCall(SECURE_METHOD); // GH-90000
            @SuppressWarnings("unchecked")
            ServerCallHandler<Object, Object> handler = mock(ServerCallHandler.class); // GH-90000

            interceptor.interceptCall(call, new Metadata(), handler); // GH-90000

            ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class); // GH-90000
            verify(call).close(statusCaptor.capture(), any()); // GH-90000
            assertThat(statusCaptor.getValue().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED); // GH-90000
            verify(handler, never()).startCall(any(), any()); // GH-90000
        }
    }

    @Nested
    @DisplayName("Tenant context propagation")
    class TenantContextPropagation {

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("validated JWT exposes subject and tenant in gRPC context")
        void validatedJwtExposesSubjectAndTenantContext() throws Exception { // GH-90000
            ConfigurableJWTProcessor<SecurityContext> processor = mock(ConfigurableJWTProcessor.class); // GH-90000
            when(processor.process(any(SignedJWT.class), eq(null))).thenReturn(null); // GH-90000

            JwtServerInterceptor interceptor = new JwtServerInterceptor( // GH-90000
                    processor,
                    disabledTokenValidator() // GH-90000
            );

            Metadata headers = new Metadata(); // GH-90000
            headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), // GH-90000
                    "Bearer " + signedToken("user-1", "tenant-42")); // GH-90000

            ServerCall<Object, Object> call = mockCall(SECURE_METHOD); // GH-90000
            ServerCallHandler<Object, Object> handler = mock(ServerCallHandler.class); // GH-90000
            AtomicReference<String> subjectRef = new AtomicReference<>(); // GH-90000
            AtomicReference<String> tenantRef = new AtomicReference<>(); // GH-90000

            when(handler.startCall(any(), any())).thenAnswer(invocation -> { // GH-90000
                subjectRef.set(JwtServerInterceptor.CTX_SUBJECT.get()); // GH-90000
                tenantRef.set(JwtServerInterceptor.CTX_TENANT.get()); // GH-90000
                return mock(ServerCall.Listener.class); // GH-90000
            });

            interceptor.interceptCall(call, headers, handler); // GH-90000

            assertThat(subjectRef.get()).isEqualTo("user-1");
            assertThat(tenantRef.get()).isEqualTo("tenant-42");
            verify(call, never()).close(any(), any()); // GH-90000
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("validated JWT without tenant claim is rejected")
        void validatedJwtWithoutTenantClaimIsRejected() throws Exception { // GH-90000
            ConfigurableJWTProcessor<SecurityContext> processor = mock(ConfigurableJWTProcessor.class); // GH-90000
            when(processor.process(any(SignedJWT.class), eq(null))).thenReturn(null); // GH-90000

            JwtServerInterceptor interceptor = new JwtServerInterceptor( // GH-90000
                    processor,
                    disabledTokenValidator() // GH-90000
            );

            Metadata headers = new Metadata(); // GH-90000
            headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), // GH-90000
                    "Bearer " + signedToken("user-1", null)); // GH-90000

            ServerCall<Object, Object> call = mockCall(SECURE_METHOD); // GH-90000
            ServerCallHandler<Object, Object> handler = mock(ServerCallHandler.class); // GH-90000

            interceptor.interceptCall(call, headers, handler); // GH-90000

            ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class); // GH-90000
            verify(call).close(statusCaptor.capture(), any()); // GH-90000
            assertThat(statusCaptor.getValue().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED); // GH-90000
            assertThat(statusCaptor.getValue().getDescription()).contains("Missing tenant claim");
            verify(handler, never()).startCall(any(), any()); // GH-90000
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static <ReqT, RespT> ServerCall<ReqT, RespT> mockCall(String fullMethod) { // GH-90000
        ServerCall<ReqT, RespT> call = mock(ServerCall.class); // GH-90000
        MethodDescriptor<ReqT, RespT> descriptor = MethodDescriptor.<ReqT, RespT>newBuilder() // GH-90000
                .setType(MethodDescriptor.MethodType.UNARY) // GH-90000
                .setFullMethodName(fullMethod) // GH-90000
                .setRequestMarshaller(new PassThroughMarshaller<>()) // GH-90000
                .setResponseMarshaller(new PassThroughMarshaller<>()) // GH-90000
                .build(); // GH-90000
        when(call.getMethodDescriptor()).thenReturn(descriptor); // GH-90000
        return call;
    }

    private JwtServerInterceptor.TokenValidator disabledTokenValidator() { // GH-90000
        return new JwtServerInterceptor.TokenValidator() { // GH-90000
            @Override
            public boolean isEnabled() { // GH-90000
                return false;
            }

            @Override
            public com.ghatana.audio.video.common.platform.AuthGatewayClient.ValidationResult validate(String token) { // GH-90000
                return new com.ghatana.audio.video.common.platform.AuthGatewayClient.ValidationResult(false, null, null); // GH-90000
            }
        };
    }

    private String signedToken(String subject, String tenantId) throws JOSEException { // GH-90000
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder() // GH-90000
                .subject(subject) // GH-90000
                .expirationTime(Date.from(Instant.now().plusSeconds(300))); // GH-90000
        if (tenantId != null) { // GH-90000
            claims.claim("tenantId", tenantId); // GH-90000
        }

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build()); // GH-90000
        jwt.sign(new MACSigner("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8))); // GH-90000
        return jwt.serialize(); // GH-90000
    }

    /** Trivial marshaller for test purposes. */
    private static class PassThroughMarshaller<T> implements MethodDescriptor.Marshaller<T> {
        @Override
        public InputStream stream(T value) { // GH-90000
            return new ByteArrayInputStream(new byte[0]); // GH-90000
        }

        @Override
        @SuppressWarnings("unchecked")
        public T parse(InputStream stream) { // GH-90000
            return (T) new Object(); // GH-90000
        }
    }
}
