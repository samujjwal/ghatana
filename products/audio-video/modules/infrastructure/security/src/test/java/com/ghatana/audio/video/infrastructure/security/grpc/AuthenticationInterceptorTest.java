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
@ExtendWith(MockitoExtension.class) 
@DisplayName("AuthenticationInterceptor Tests")
class AuthenticationInterceptorTest {

    @Mock
    private AuthenticationProvider authenticationProvider;

    @AfterEach
    void cleanup() { 
        TenantContext.clear(); 
    }

    @Test
    @DisplayName("GIVEN public method WHEN no auth header THEN request bypasses authentication")
    void shouldBypassAuthenticationForPublicMethod() { 
        AuthenticationInterceptor interceptor = new AuthenticationInterceptor(authenticationProvider); 
        TestServerCall call = new TestServerCall("grpc.health.v1.Health/Check");
        Metadata headers = new Metadata(); 
        AtomicReference<Boolean> started = new AtomicReference<>(false); 

        ServerCallHandler<StringValue, StringValue> handler = (c, h) -> { 
            started.set(true); 
            return new ServerCall.Listener<>() {}; 
        };

        interceptor.interceptCall(call, headers, handler); 

        assertThat(started.get()).isTrue(); 
        verify(authenticationProvider, never()).authenticate(any(Credentials.class)); 
        assertThat(call.closedStatus).isNull(); 
    }

    @Test
    @DisplayName("GIVEN missing auth header WHEN protected method THEN closes as UNAUTHENTICATED")
    void shouldRejectMissingAuthorizationHeader() { 
        AuthenticationInterceptor interceptor = new AuthenticationInterceptor(authenticationProvider); 
        TestServerCall call = new TestServerCall("stt.STTService/Transcribe");
        Metadata headers = new Metadata(); 

        interceptor.interceptCall(call, headers, (c, h) -> new ServerCall.Listener<>() {}); 

        assertThat(call.closedStatus).isNotNull(); 
        assertThat(call.closedStatus.getCode()).isEqualTo(Status.UNAUTHENTICATED.getCode()); 
    }

    @Test
    @DisplayName("GIVEN invalid token WHEN provider returns empty THEN closes as UNAUTHENTICATED")
    void shouldRejectInvalidToken() { 
        AuthenticationInterceptor interceptor = new AuthenticationInterceptor(authenticationProvider); 
        TestServerCall call = new TestServerCall("stt.STTService/Transcribe");
        Metadata headers = new Metadata(); 
        headers.put(AuthenticationInterceptor.AUTHORIZATION_KEY, "Bearer invalid-token"); 

        when(authenticationProvider.authenticate(any(Credentials.class))) 
            .thenReturn(Promise.of(Optional.empty())); 

        interceptor.interceptCall(call, headers, (c, h) -> new ServerCall.Listener<>() {}); 

        assertThat(call.closedStatus).isNotNull(); 
        assertThat(call.closedStatus.getCode()).isEqualTo(Status.UNAUTHENTICATED.getCode()); 
    }

    @Test
    @DisplayName("GIVEN expired token WHEN provider throws THEN closes as UNAUTHENTICATED")
    void shouldRejectExpiredToken() { 
        AuthenticationInterceptor interceptor = new AuthenticationInterceptor(authenticationProvider); 
        TestServerCall call = new TestServerCall("stt.STTService/Transcribe");
        Metadata headers = new Metadata(); 
        headers.put(AuthenticationInterceptor.AUTHORIZATION_KEY, "Bearer expired-token"); 

        when(authenticationProvider.authenticate(any(Credentials.class))) 
            .thenReturn(Promise.ofException(new IllegalStateException("token expired")));

        interceptor.interceptCall(call, headers, (c, h) -> new ServerCall.Listener<>() {}); 

        assertThat(call.closedStatus).isNotNull(); 
        assertThat(call.closedStatus.getCode()).isEqualTo(Status.UNAUTHENTICATED.getCode()); 
    }

    @Test
    @DisplayName("GIVEN valid token and tenant header WHEN protected method THEN auth context and tenant context are set")
    void shouldPopulateAuthAndTenantContextForValidToken() { 
        AuthenticationInterceptor interceptor = new AuthenticationInterceptor(authenticationProvider); 
        TestServerCall call = new TestServerCall("stt.STTService/Transcribe");
        Metadata headers = new Metadata(); 
        headers.put(AuthenticationInterceptor.AUTHORIZATION_KEY, "Bearer valid-token"); 
        headers.put(AuthenticationInterceptor.TENANT_ID_KEY, "tenant-123"); 

        User user = new User("user-1", "alice", Set.of("ADMIN", "USER")); 
        when(authenticationProvider.authenticate(any(Credentials.class))) 
            .thenReturn(Promise.of(Optional.of(user))); 

        AtomicReference<AuthenticationInterceptor.AuthContext> observed = new AtomicReference<>(); 
        ServerCallHandler<StringValue, StringValue> handler = (c, h) -> { 
            observed.set(AuthenticationInterceptor.getCurrentAuth().orElse(null)); 
            return new ServerCall.Listener<>() {}; 
        };

        interceptor.interceptCall(call, headers, handler); 

        assertThat(call.closedStatus).isNull(); 
        assertThat(observed.get()).isNotNull(); 
        assertThat(observed.get().userId()).isEqualTo("user-1");
        assertThat(observed.get().tenantId()).isEqualTo("tenant-123");
        assertThat(observed.get().isAdmin()).isTrue(); 
        assertThat(TenantContext.getCurrentTenantId()).isEqualTo("tenant-123");
    }

    private static final class TestServerCall extends ServerCall<StringValue, StringValue> {
        private final MethodDescriptor<StringValue, StringValue> method;
        private Status closedStatus;

        private TestServerCall(String fullMethodName) { 
            this.method = MethodDescriptor.<StringValue, StringValue>newBuilder() 
                .setType(MethodDescriptor.MethodType.UNARY) 
                .setFullMethodName(fullMethodName) 
                .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance())) 
                .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance())) 
                .build(); 
        }

        @Override
        public void request(int numMessages) { 
        }

        @Override
        public void sendHeaders(Metadata headers) { 
        }

        @Override
        public void sendMessage(StringValue message) { 
        }

        @Override
        public void close(Status status, Metadata trailers) { 
            this.closedStatus = status;
        }

        @Override
        public boolean isCancelled() { 
            return false;
        }

        @Override
        public MethodDescriptor<StringValue, StringValue> getMethodDescriptor() { 
            return method;
        }
    }
}



