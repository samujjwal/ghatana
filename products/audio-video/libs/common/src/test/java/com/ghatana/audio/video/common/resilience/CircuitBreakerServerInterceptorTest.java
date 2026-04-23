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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Circuit Breaker Server Interceptor Tests")
class CircuitBreakerServerInterceptorTest {

    @Test
    @DisplayName("Should open after consecutive transport failures and reject subsequent calls")
    void shouldOpenAfterConsecutiveTransportFailures() { // GH-90000
        CircuitBreakerServerInterceptor interceptor = new CircuitBreakerServerInterceptor(2, 1_000); // GH-90000
        Metadata headers = new Metadata(); // GH-90000
        @SuppressWarnings("unchecked")
        ServerCallHandler<String, String> next = mock(ServerCallHandler.class); // GH-90000
        when(next.startCall(any(), eq(headers))).thenReturn(new ServerCall.Listener<>() {}); // GH-90000

        ServerCall<String, String> firstCall = createServerCall("audio.Video/Transcribe");
        ServerCall<String, String> secondCall = createServerCall("audio.Video/Transcribe");
        ServerCall<String, String> rejectedCall = createServerCall("audio.Video/Transcribe");

        interceptor.interceptCall(firstCall, headers, next); // GH-90000
        interceptor.interceptCall(secondCall, headers, next); // GH-90000

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ServerCall<String, String>> wrappedCallCaptor = ArgumentCaptor.forClass(ServerCall.class); // GH-90000
        verify(next, times(2)).startCall(wrappedCallCaptor.capture(), eq(headers)); // GH-90000

        wrappedCallCaptor.getAllValues().get(0).close(Status.INTERNAL, new Metadata()); // GH-90000
        wrappedCallCaptor.getAllValues().get(1).close(Status.UNAVAILABLE, new Metadata()); // GH-90000

        interceptor.interceptCall(rejectedCall, headers, next); // GH-90000

        ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class); // GH-90000
        verify(rejectedCall).close(statusCaptor.capture(), any(Metadata.class)); // GH-90000
        assertThat(statusCaptor.getValue().getCode()).isEqualTo(Status.Code.UNAVAILABLE); // GH-90000
        assertThat(statusCaptor.getValue().getDescription()).contains("Circuit breaker OPEN");
        verify(next, times(2)).startCall(any(), eq(headers)); // GH-90000
    }

    @Test
    @DisplayName("Should ignore business failures when tracking breaker state")
    void shouldIgnoreBusinessFailures() { // GH-90000
        CircuitBreakerServerInterceptor interceptor = new CircuitBreakerServerInterceptor(1, 1_000); // GH-90000
        Metadata headers = new Metadata(); // GH-90000
        @SuppressWarnings("unchecked")
        ServerCallHandler<String, String> next = mock(ServerCallHandler.class); // GH-90000
        when(next.startCall(any(), eq(headers))).thenReturn(new ServerCall.Listener<>() {}); // GH-90000

        ServerCall<String, String> firstCall = createServerCall("audio.Video/Analyze");
        ServerCall<String, String> secondCall = createServerCall("audio.Video/Analyze");

        interceptor.interceptCall(firstCall, headers, next); // GH-90000

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ServerCall<String, String>> wrappedCallCaptor = ArgumentCaptor.forClass(ServerCall.class); // GH-90000
        verify(next).startCall(wrappedCallCaptor.capture(), eq(headers)); // GH-90000
        wrappedCallCaptor.getValue().close(Status.INVALID_ARGUMENT, new Metadata()); // GH-90000

        interceptor.interceptCall(secondCall, headers, next); // GH-90000

        verify(next, times(2)).startCall(any(), eq(headers)); // GH-90000
        verify(secondCall, never()).close(any(Status.class), any(Metadata.class)); // GH-90000
    }

    @Test
    @DisplayName("Should transition through half-open and close again after success")
    void shouldRecoverFromHalfOpen() { // GH-90000
        CircuitBreakerServerInterceptor interceptor = new CircuitBreakerServerInterceptor(1, 0); // GH-90000
        Metadata headers = new Metadata(); // GH-90000
        @SuppressWarnings("unchecked")
        ServerCallHandler<String, String> next = mock(ServerCallHandler.class); // GH-90000
        when(next.startCall(any(), eq(headers))).thenReturn(new ServerCall.Listener<>() {}); // GH-90000

        ServerCall<String, String> failingCall = createServerCall("audio.Video/Synthesize");
        ServerCall<String, String> probeCall = createServerCall("audio.Video/Synthesize");
        ServerCall<String, String> recoveredCall = createServerCall("audio.Video/Synthesize");

        interceptor.interceptCall(failingCall, headers, next); // GH-90000

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ServerCall<String, String>> wrappedCallCaptor = ArgumentCaptor.forClass(ServerCall.class); // GH-90000
        verify(next).startCall(wrappedCallCaptor.capture(), eq(headers)); // GH-90000
        wrappedCallCaptor.getValue().close(Status.INTERNAL, new Metadata()); // GH-90000

        interceptor.interceptCall(probeCall, headers, next); // GH-90000
        verify(next, times(2)).startCall(wrappedCallCaptor.capture(), eq(headers)); // GH-90000
        wrappedCallCaptor.getAllValues().get(1).close(Status.OK, new Metadata()); // GH-90000

        interceptor.interceptCall(recoveredCall, headers, next); // GH-90000

        verify(next, times(3)).startCall(any(), eq(headers)); // GH-90000
        verify(recoveredCall, never()).close(any(Status.class), any(Metadata.class)); // GH-90000
    }

    @SuppressWarnings("unchecked")
    private static ServerCall<String, String> createServerCall(String fullMethodName) { // GH-90000
        ServerCall<String, String> call = mock(ServerCall.class); // GH-90000
        when(call.getMethodDescriptor()).thenReturn(MethodDescriptor.<String, String>newBuilder() // GH-90000
                .setType(MethodDescriptor.MethodType.UNARY) // GH-90000
                .setFullMethodName(fullMethodName) // GH-90000
                .setRequestMarshaller(new StringMarshaller()) // GH-90000
                .setResponseMarshaller(new StringMarshaller()) // GH-90000
                .build()); // GH-90000
        return call;
    }

    private static final class StringMarshaller implements MethodDescriptor.Marshaller<String> {
        @Override
        public InputStream stream(String value) { // GH-90000
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)); // GH-90000
        }

        @Override
        public String parse(InputStream stream) { // GH-90000
            try {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8); // GH-90000
            } catch (java.io.IOException exception) { // GH-90000
                throw new IllegalStateException("Failed to parse gRPC payload", exception); // GH-90000
            }
        }
    }
}
