package com.ghatana.audio.video.common.observability;

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
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Contract tests for {@link TracingServerInterceptor}.
 *
 * @doc.type class
 * @doc.purpose Verify trace propagation and MDC lifecycle for gRPC tracing interceptor
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Tracing Server Interceptor Tests")
class TracingServerInterceptorTest {

    private static final Metadata.Key<String> TRACE_ID_KEY =
            Metadata.Key.of("x-trace-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> SPAN_ID_KEY =
            Metadata.Key.of("x-span-id", Metadata.ASCII_STRING_MARSHALLER);

    @Test
    @DisplayName("Should propagate incoming trace id in response headers")
    void shouldPropagateIncomingTraceId() { // GH-90000
        TracingServerInterceptor interceptor = new TracingServerInterceptor(); // GH-90000
        Metadata headers = new Metadata(); // GH-90000
        headers.put(TRACE_ID_KEY, "trace-incoming-123"); // GH-90000

        @SuppressWarnings("unchecked")
        ServerCall<String, String> call = createServerCall("audio.Video/Transcribe"); // GH-90000
        @SuppressWarnings("unchecked")
        ServerCallHandler<String, String> next = mock(ServerCallHandler.class); // GH-90000
        when(next.startCall(any(), eq(headers))).thenReturn(new ServerCall.Listener<>() {}); // GH-90000

        interceptor.interceptCall(call, headers, next); // GH-90000

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ServerCall<String, String>> wrappedCallCaptor = ArgumentCaptor.forClass(ServerCall.class); // GH-90000
        verify(next).startCall(wrappedCallCaptor.capture(), eq(headers)); // GH-90000

        Metadata responseMetadata = new Metadata(); // GH-90000
        wrappedCallCaptor.getValue().sendHeaders(responseMetadata); // GH-90000

        ArgumentCaptor<Metadata> sentHeadersCaptor = ArgumentCaptor.forClass(Metadata.class); // GH-90000
        verify(call).sendHeaders(sentHeadersCaptor.capture()); // GH-90000

        Metadata sentHeaders = sentHeadersCaptor.getValue(); // GH-90000
        assertThat(sentHeaders.get(TRACE_ID_KEY)).isEqualTo("trace-incoming-123");
        assertThat(sentHeaders.get(SPAN_ID_KEY)).isNotBlank();
    }

    @Test
    @DisplayName("Should generate trace id when request does not include one")
    void shouldGenerateTraceIdWhenMissing() { // GH-90000
        TracingServerInterceptor interceptor = new TracingServerInterceptor(); // GH-90000
        Metadata headers = new Metadata(); // GH-90000

        @SuppressWarnings("unchecked")
        ServerCall<String, String> call = createServerCall("audio.Video/HealthCheck"); // GH-90000
        @SuppressWarnings("unchecked")
        ServerCallHandler<String, String> next = mock(ServerCallHandler.class); // GH-90000
        when(next.startCall(any(), eq(headers))).thenReturn(new ServerCall.Listener<>() {}); // GH-90000

        interceptor.interceptCall(call, headers, next); // GH-90000

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ServerCall<String, String>> wrappedCallCaptor = ArgumentCaptor.forClass(ServerCall.class); // GH-90000
        verify(next).startCall(wrappedCallCaptor.capture(), eq(headers)); // GH-90000

        wrappedCallCaptor.getValue().sendHeaders(new Metadata()); // GH-90000

        ArgumentCaptor<Metadata> sentHeadersCaptor = ArgumentCaptor.forClass(Metadata.class); // GH-90000
        verify(call).sendHeaders(sentHeadersCaptor.capture()); // GH-90000

        Metadata sentHeaders = sentHeadersCaptor.getValue(); // GH-90000
        assertThat(sentHeaders.get(TRACE_ID_KEY)).isNotBlank();
        assertThat(sentHeaders.get(TRACE_ID_KEY)).hasSize(32);
        assertThat(sentHeaders.get(SPAN_ID_KEY)).isNotBlank();
        assertThat(sentHeaders.get(SPAN_ID_KEY)).hasSize(16);
    }

    @Test
    @DisplayName("Should clear MDC context when gRPC call closes")
    void shouldClearMdcWhenCallCloses() { // GH-90000
        TracingServerInterceptor interceptor = new TracingServerInterceptor(); // GH-90000
        Metadata headers = new Metadata(); // GH-90000

        @SuppressWarnings("unchecked")
        ServerCall<String, String> call = createServerCall("audio.Video/Analyze"); // GH-90000
        @SuppressWarnings("unchecked")
        ServerCallHandler<String, String> next = mock(ServerCallHandler.class); // GH-90000
        when(next.startCall(any(), eq(headers))).thenReturn(new ServerCall.Listener<>() {}); // GH-90000

        interceptor.interceptCall(call, headers, next); // GH-90000

        assertThat(MDC.get("traceId")).isNotBlank();
        assertThat(MDC.get("spanId")).isNotBlank();
        assertThat(MDC.get("grpcService")).isEqualTo("audio.Video");
        assertThat(MDC.get("grpcMethod")).isEqualTo("Analyze");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ServerCall<String, String>> wrappedCallCaptor = ArgumentCaptor.forClass(ServerCall.class); // GH-90000
        verify(next).startCall(wrappedCallCaptor.capture(), eq(headers)); // GH-90000

        wrappedCallCaptor.getValue().close(Status.OK, new Metadata()); // GH-90000

        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("spanId")).isNull();
        assertThat(MDC.get("grpcService")).isNull();
        assertThat(MDC.get("grpcMethod")).isNull();
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
