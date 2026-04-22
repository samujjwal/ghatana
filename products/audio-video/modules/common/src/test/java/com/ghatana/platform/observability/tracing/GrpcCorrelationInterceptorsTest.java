/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.observability.tracing;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GrpcCorrelationInterceptors} (CP-002.1). // GH-90000
 */
@DisplayName("GrpcCorrelationInterceptors — CP-002.1 [GH-90000]")
class GrpcCorrelationInterceptorsTest {

    @BeforeEach
    void setUp() { // GH-90000
        MDC.clear(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        MDC.clear(); // GH-90000
    }

    @Test
    @DisplayName("clientInterceptor() injects MDC correlation ID into outbound metadata [GH-90000]")
    void clientInjectsCorrelationId() { // GH-90000
        MDC.put(GrpcCorrelationInterceptors.MDC_KEY, "test-corr-id-123"); // GH-90000

        ClientInterceptor interceptor = GrpcCorrelationInterceptors.clientInterceptor(); // GH-90000
        Metadata capturedHeaders = new Metadata(); // GH-90000

        // Build a minimal channel stub that captures the start call metadata
        Channel fakeChannel = new Channel() { // GH-90000
            @Override
            public <Req, Resp> ClientCall<Req, Resp> newCall( // GH-90000
                    MethodDescriptor<Req, Resp> method, CallOptions opts) {
                return new io.grpc.internal.NoopClientCall<>() { // GH-90000
                    @Override
                    public void start(Listener<Resp> listener, Metadata headers) { // GH-90000
                        capturedHeaders.merge(headers); // GH-90000
                    }
                };
            }

            @Override
            public String authority() { return "localhost"; } // GH-90000
        };

        @SuppressWarnings("unchecked [GH-90000]")
        MethodDescriptor<Object, Object> method = (MethodDescriptor<Object, Object>) // GH-90000
                mock(MethodDescriptor.class); // GH-90000
        when(method.getFullMethodName()).thenReturn("TestService/TestMethod [GH-90000]");

        ClientCall<Object, Object> call = interceptor.interceptCall(method, CallOptions.DEFAULT, fakeChannel); // GH-90000
        call.start(mock(ClientCall.Listener.class), new Metadata()); // GH-90000

        assertThat(capturedHeaders.get(GrpcCorrelationInterceptors.CORRELATION_ID_KEY)) // GH-90000
                .isEqualTo("test-corr-id-123 [GH-90000]");
    }

    @Test
    @DisplayName("clientInterceptor() generates correlation ID when MDC is empty [GH-90000]")
    void clientGeneratesCorrelationIdWhenMdcEmpty() { // GH-90000
        ClientInterceptor interceptor = GrpcCorrelationInterceptors.clientInterceptor(); // GH-90000
        Metadata capturedHeaders = new Metadata(); // GH-90000

        Channel fakeChannel = new Channel() { // GH-90000
            @Override
            public <Req, Resp> ClientCall<Req, Resp> newCall( // GH-90000
                    MethodDescriptor<Req, Resp> method, CallOptions opts) {
                return new io.grpc.internal.NoopClientCall<>() { // GH-90000
                    @Override
                    public void start(Listener<Resp> listener, Metadata headers) { // GH-90000
                        capturedHeaders.merge(headers); // GH-90000
                    }
                };
            }

            @Override
            public String authority() { return "localhost"; } // GH-90000
        };

        @SuppressWarnings("unchecked [GH-90000]")
        MethodDescriptor<Object, Object> method = (MethodDescriptor<Object, Object>) // GH-90000
                mock(MethodDescriptor.class); // GH-90000
        when(method.getFullMethodName()).thenReturn("Svc/Method [GH-90000]");

        ClientCall<Object, Object> call = interceptor.interceptCall(method, CallOptions.DEFAULT, fakeChannel); // GH-90000
        call.start(mock(ClientCall.Listener.class), new Metadata()); // GH-90000

        String correlationId = capturedHeaders.get(GrpcCorrelationInterceptors.CORRELATION_ID_KEY); // GH-90000
        assertThat(correlationId).isNotBlank(); // GH-90000
        // Must be a valid UUID
        assertThat(correlationId).matches("[0-9a-fA-F-]{36} [GH-90000]");
    }

    @Test
    @DisplayName("CORRELATION_ID_KEY constant is 'x-correlation-id' [GH-90000]")
    void correlationIdKeyName() { // GH-90000
        assertThat(GrpcCorrelationInterceptors.CORRELATION_ID_KEY.name()) // GH-90000
                .isEqualTo("x-correlation-id [GH-90000]");
    }

    @Test
    @DisplayName("MDC_KEY constant is 'correlationId' [GH-90000]")
    void mdcKeyName() { // GH-90000
        assertThat(GrpcCorrelationInterceptors.MDC_KEY).isEqualTo("correlationId [GH-90000]");
    }

    @Test
    @DisplayName("serverInterceptor() returns a non-null ServerInterceptor [GH-90000]")
    void serverInterceptorNonNull() { // GH-90000
        assertThat(GrpcCorrelationInterceptors.serverInterceptor()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("clientInterceptor() returns a non-null ClientInterceptor [GH-90000]")
    void clientInterceptorNonNull() { // GH-90000
        assertThat(GrpcCorrelationInterceptors.clientInterceptor()).isNotNull(); // GH-90000
    }
}

