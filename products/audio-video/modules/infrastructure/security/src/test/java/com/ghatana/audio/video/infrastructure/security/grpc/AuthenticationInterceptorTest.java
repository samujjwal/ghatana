package com.ghatana.audio.video.infrastructure.security.grpc;

import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.platform.security.auth.AuthenticationProvider;
import com.ghatana.platform.security.auth.Credentials;
import com.ghatana.platform.security.model.User;
import io.activej.promise.Promise;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import io.grpc.protobuf.ProtoUtils;
import com.google.protobuf.StringValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Unit tests for gRPC authentication interceptor behavior
 * @doc.layer test
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("AuthenticationInterceptor Tests [GH-90000]")
class AuthenticationInterceptorTest {

    @Mock
    private AuthenticationProvider authenticationProvider;

    @AfterEach
    void cleanup() { // GH-90000
        TenantContext.clear(); // GH-90000
    }

    @Test
    @DisplayName("GIVEN public method WHEN no auth header THEN request bypasses authentication [GH-90000]")
    void shouldBypassAuthenticationForPublicMethod() { // GH-90000
        AuthenticationInterceptor interceptor = new AuthenticationInterceptor(authenticationProvider); // GH-90000
        TestServerCall call = new TestServerCall("grpc.health.v1.Health/Check [GH-90000]");
        Metadata headers = new Metadata(); // GH-90000
        AtomicReference<Boolean> started = new AtomicReference<>(false); // GH-90000

        ServerCallHandler<StringValue, StringValue> handler = (c, h) -> { // GH-90000
            started.set(true); // GH-90000
            return new ServerCall.Listener<>() {}; // GH-90000
        };

        interceptor.interceptCall(call, headers, handler); // GH-90000

        assertThat(started.get()).isTrue(); // GH-90000
        verify(authenticationProvider, never()).authenticate(any(Credentials.class)); // GH-90000
        assertThat(call.closedStatus).isNull(); // GH-90000
    }

    @Test
    @DisplayName("GIVEN missing auth header WHEN protected method THEN closes as UNAUTHENTICATED [GH-90000]")
    void shouldRejectMissingAuthorizationHeader() { // GH-90000
        AuthenticationInterceptor interceptor = new AuthenticationInterceptor(authenticationProvider); // GH-90000
        TestServerCall call = new TestServerCall("stt.STTService/Transcribe [GH-90000]");
        Metadata headers = new Metadata(); // GH-90000

        interceptor.interceptCall(call, headers, (c, h) -> new ServerCall.Listener<>() {}); // GH-90000

        assertThat(call.closedStatus).isNotNull(); // GH-90000
        assertThat(call.closedStatus.getCode()).isEqualTo(Status.UNAUTHENTICATED.getCode()); // GH-90000
    }

    @Test
    @DisplayName("GIVEN invalid token WHEN provider returns empty THEN closes as UNAUTHENTICATED [GH-90000]")
    void shouldRejectInvalidToken() { // GH-90000
        AuthenticationInterceptor interceptor = new AuthenticationInterceptor(authenticationProvider); // GH-90000
        TestServerCall call = new TestServerCall("stt.STTService/Transcribe [GH-90000]");
        Metadata headers = new Metadata(); // GH-90000
        headers.put(AuthenticationInterceptor.AUTHORIZATION_KEY, "Bearer invalid-token"); // GH-90000

        when(authenticationProvider.authenticate(any(Credentials.class))) // GH-90000
            .thenReturn(Promise.of(Optional.empty())); // GH-90000

        interceptor.interceptCall(call, headers, (c, h) -> new ServerCall.Listener<>() {}); // GH-90000

        assertThat(call.closedStatus).isNotNull(); // GH-90000
        assertThat(call.closedStatus.getCode()).isEqualTo(Status.UNAUTHENTICATED.getCode()); // GH-90000
    }

    @Test
    @DisplayName("GIVEN expired token WHEN provider throws THEN closes as UNAUTHENTICATED [GH-90000]")
    void shouldRejectExpiredToken() { // GH-90000
        AuthenticationInterceptor interceptor = new AuthenticationInterceptor(authenticationProvider); // GH-90000
        TestServerCall call = new TestServerCall("stt.STTService/Transcribe [GH-90000]");
        Metadata headers = new Metadata(); // GH-90000
        headers.put(AuthenticationInterceptor.AUTHORIZATION_KEY, "Bearer expired-token"); // GH-90000

        when(authenticationProvider.authenticate(any(Credentials.class))) // GH-90000
            .thenReturn(Promise.ofException(new IllegalStateException("token expired [GH-90000]")));

        interceptor.interceptCall(call, headers, (c, h) -> new ServerCall.Listener<>() {}); // GH-90000

        assertThat(call.closedStatus).isNotNull(); // GH-90000
        assertThat(call.closedStatus.getCode()).isEqualTo(Status.UNAUTHENTICATED.getCode()); // GH-90000
    }

    @Test
    @DisplayName("GIVEN valid token and tenant header WHEN protected method THEN auth context and tenant context are set [GH-90000]")
    void shouldPopulateAuthAndTenantContextForValidToken() { // GH-90000
        AuthenticationInterceptor interceptor = new AuthenticationInterceptor(authenticationProvider); // GH-90000
        TestServerCall call = new TestServerCall("stt.STTService/Transcribe [GH-90000]");
        Metadata headers = new Metadata(); // GH-90000
        headers.put(AuthenticationInterceptor.AUTHORIZATION_KEY, "Bearer valid-token"); // GH-90000
        headers.put(AuthenticationInterceptor.TENANT_ID_KEY, "tenant-123"); // GH-90000

        User user = new User("user-1", "alice", Set.of("ADMIN", "USER")); // GH-90000
        when(authenticationProvider.authenticate(any(Credentials.class))) // GH-90000
            .thenReturn(Promise.of(Optional.of(user))); // GH-90000

        AtomicReference<AuthenticationInterceptor.AuthContext> observed = new AtomicReference<>(); // GH-90000
        ServerCallHandler<StringValue, StringValue> handler = (c, h) -> { // GH-90000
            observed.set(AuthenticationInterceptor.getCurrentAuth().orElse(null)); // GH-90000
            return new ServerCall.Listener<>() {}; // GH-90000
        };

        interceptor.interceptCall(call, headers, handler); // GH-90000

        assertThat(call.closedStatus).isNull(); // GH-90000
        assertThat(observed.get()).isNotNull(); // GH-90000
        assertThat(observed.get().userId()).isEqualTo("user-1 [GH-90000]");
        assertThat(observed.get().tenantId()).isEqualTo("tenant-123 [GH-90000]");
        assertThat(observed.get().isAdmin()).isTrue(); // GH-90000
        assertThat(TenantContext.getCurrentTenantId()).isEqualTo("tenant-123 [GH-90000]");
    }

    private static final class TestServerCall extends ServerCall<StringValue, StringValue> {
        private final MethodDescriptor<StringValue, StringValue> method;
        private Status closedStatus;

        private TestServerCall(String fullMethodName) { // GH-90000
            this.method = MethodDescriptor.<StringValue, StringValue>newBuilder() // GH-90000
                .setType(MethodDescriptor.MethodType.UNARY) // GH-90000
                .setFullMethodName(fullMethodName) // GH-90000
                .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance())) // GH-90000
                .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance())) // GH-90000
                .build(); // GH-90000
        }

        @Override
        public void request(int numMessages) { // GH-90000
        }

        @Override
        public void sendHeaders(Metadata headers) { // GH-90000
        }

        @Override
        public void sendMessage(StringValue message) { // GH-90000
        }

        @Override
        public void close(Status status, Metadata trailers) { // GH-90000
            this.closedStatus = status;
        }

        @Override
        public boolean isCancelled() { // GH-90000
            return false;
        }

        @Override
        public MethodDescriptor<StringValue, StringValue> getMethodDescriptor() { // GH-90000
            return method;
        }
    }
}



