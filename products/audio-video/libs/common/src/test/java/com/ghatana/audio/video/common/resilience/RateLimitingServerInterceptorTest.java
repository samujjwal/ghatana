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

/**
 * @doc.type class
 * @doc.purpose Verify audio-video gRPC throttling reuses the shared platform token bucket
 * @doc.layer product
 * @doc.pattern Unit Test
 */
@DisplayName("Rate Limiting Server Interceptor Tests")
class RateLimitingServerInterceptorTest {

    @Test
    @DisplayName("Should reject a second in-flight call when concurrency is exhausted")
    void shouldRejectWhenConcurrencyIsExhausted() { // GH-90000
        RateLimitingServerInterceptor interceptor = new RateLimitingServerInterceptor(50.0, 10L, 1); // GH-90000
        Metadata headers = new Metadata(); // GH-90000
        ServerCall<String, String> firstCall = createServerCall("audio.Video/Recognize");
        ServerCall<String, String> secondCall = createServerCall("audio.Video/Recognize");
        @SuppressWarnings("unchecked")
        ServerCallHandler<String, String> next = mock(ServerCallHandler.class); // GH-90000
        when(next.startCall(any(), eq(headers))).thenReturn(new ServerCall.Listener<>() {}); // GH-90000

        interceptor.interceptCall(firstCall, headers, next); // GH-90000
        interceptor.interceptCall(secondCall, headers, next); // GH-90000

        verify(secondCall).close(any(Status.class), any(Metadata.class)); // GH-90000

        ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class); // GH-90000
        verify(secondCall).close(statusCaptor.capture(), any(Metadata.class)); // GH-90000
        assertThat(statusCaptor.getValue().getCode()).isEqualTo(Status.Code.RESOURCE_EXHAUSTED); // GH-90000
        assertThat(statusCaptor.getValue().getDescription()).contains("overloaded");
    }

    @Test
    @DisplayName("Should reject repeated calls when the per-method shared rate limit is exceeded")
    void shouldRejectWhenRateLimitIsExceeded() { // GH-90000
        RateLimitingServerInterceptor interceptor = new RateLimitingServerInterceptor(1.0, 1L, 2); // GH-90000
        Metadata headers = new Metadata(); // GH-90000
        ServerCall<String, String> firstCall = createServerCall("audio.Video/Transcribe");
        ServerCall<String, String> secondCall = createServerCall("audio.Video/Transcribe");
        @SuppressWarnings("unchecked")
        ServerCallHandler<String, String> next = mock(ServerCallHandler.class); // GH-90000
        when(next.startCall(any(), eq(headers))).thenReturn(new ServerCall.Listener<>() {}); // GH-90000

        interceptor.interceptCall(firstCall, headers, next); // GH-90000

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ServerCall<String, String>> wrappedCallCaptor = ArgumentCaptor.forClass(ServerCall.class); // GH-90000
        verify(next).startCall(wrappedCallCaptor.capture(), eq(headers)); // GH-90000
        wrappedCallCaptor.getValue().close(Status.OK, new Metadata()); // GH-90000

        interceptor.interceptCall(secondCall, headers, next); // GH-90000

        ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class); // GH-90000
        verify(secondCall).close(statusCaptor.capture(), any(Metadata.class)); // GH-90000
        assertThat(statusCaptor.getValue().getCode()).isEqualTo(Status.Code.RESOURCE_EXHAUSTED); // GH-90000
        assertThat(statusCaptor.getValue().getDescription()).contains("Rate limit exceeded");
    }

    @Test
    @DisplayName("Should allow separate methods to use independent shared rate buckets")
    void shouldAllowSeparateMethodsToUseIndependentBuckets() { // GH-90000
        RateLimitingServerInterceptor interceptor = new RateLimitingServerInterceptor(1.0, 1L, 2); // GH-90000
        Metadata headers = new Metadata(); // GH-90000
        ServerCall<String, String> transcribeCall = createServerCall("audio.Video/Transcribe");
        ServerCall<String, String> synthesizeCall = createServerCall("audio.Video/Synthesize");
        @SuppressWarnings("unchecked")
        ServerCallHandler<String, String> next = mock(ServerCallHandler.class); // GH-90000
        when(next.startCall(any(), eq(headers))).thenReturn(new ServerCall.Listener<>() {}); // GH-90000

        interceptor.interceptCall(transcribeCall, headers, next); // GH-90000
        interceptor.interceptCall(synthesizeCall, headers, next); // GH-90000

        verify(next, times(2)).startCall(any(), eq(headers)); // GH-90000
        verify(synthesizeCall, never()).close(any(Status.class), any(Metadata.class)); // GH-90000
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
