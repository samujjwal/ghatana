package com.ghatana.audio.video.common.observability;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Metrics Server Interceptor Tests")
class MetricsServerInterceptorTest {

    @Test
    @DisplayName("Should record started handled and duration metrics in Prometheus format")
    void shouldRecordMetricsInPrometheusFormat() { // GH-90000
        MetricsServerInterceptor interceptor = new MetricsServerInterceptor(); // GH-90000
        Metadata headers = new Metadata(); // GH-90000
        @SuppressWarnings("unchecked")
        ServerCallHandler<String, String> next = mock(ServerCallHandler.class); // GH-90000
        when(next.startCall(any(), eq(headers))).thenReturn(new ServerCall.Listener<>() {}); // GH-90000

        interceptor.interceptCall(createServerCall("audio.Video/Transcribe"), headers, next);
        interceptor.interceptCall(createServerCall("audio.Video/Transcribe"), headers, next);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ServerCall<String, String>> wrappedCallCaptor = ArgumentCaptor.forClass(ServerCall.class); // GH-90000
        verify(next, org.mockito.Mockito.times(2)).startCall(wrappedCallCaptor.capture(), eq(headers)); // GH-90000

        wrappedCallCaptor.getAllValues().get(0).close(Status.OK, new Metadata()); // GH-90000
        wrappedCallCaptor.getAllValues().get(1).close(Status.INVALID_ARGUMENT, new Metadata()); // GH-90000

        String scrape = interceptor.scrape(); // GH-90000

        assertThat(scrape).contains("grpc_server_started_total{grpc_method=\"audio.Video/Transcribe\"} 2"); // GH-90000
        assertThat(scrape).contains("grpc_server_handled_total{grpc_method=\"audio.Video/Transcribe\",grpc_code=\"OK\"} 1"); // GH-90000
        assertThat(scrape).contains("grpc_server_handled_total{grpc_method=\"audio.Video/Transcribe\",grpc_code=\"INVALID_ARGUMENT\"} 1"); // GH-90000
        assertThat(scrape).contains("grpc_server_handling_seconds_sum{grpc_method=\"audio.Video/Transcribe\"}"); // GH-90000
        assertThat(scrape).contains("grpc_server_handling_seconds_count{grpc_method=\"audio.Video/Transcribe\"} 2"); // GH-90000
    }

    @Test
    @DisplayName("Should clear collected metrics on reset")
    void shouldClearMetricsOnReset() { // GH-90000
        MetricsServerInterceptor interceptor = new MetricsServerInterceptor(); // GH-90000
        Metadata headers = new Metadata(); // GH-90000
        @SuppressWarnings("unchecked")
        ServerCallHandler<String, String> next = mock(ServerCallHandler.class); // GH-90000
        when(next.startCall(any(), eq(headers))).thenReturn(new ServerCall.Listener<>() {}); // GH-90000

        interceptor.interceptCall(createServerCall("audio.Video/Health"), headers, next);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ServerCall<String, String>> wrappedCallCaptor = ArgumentCaptor.forClass(ServerCall.class); // GH-90000
        verify(next).startCall(wrappedCallCaptor.capture(), eq(headers)); // GH-90000
        wrappedCallCaptor.getValue().close(Status.OK, new Metadata()); // GH-90000

        interceptor.reset(); // GH-90000

        String scrape = interceptor.scrape(); // GH-90000
        assertThat(scrape).doesNotContain("audio.Video/Health");
        assertThat(scrape).contains("# HELP grpc_server_started_total");
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
