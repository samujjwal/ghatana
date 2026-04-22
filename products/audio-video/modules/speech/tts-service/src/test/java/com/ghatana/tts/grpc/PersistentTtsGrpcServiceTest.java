package com.ghatana.tts.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.ghatana.audio.video.infrastructure.persistence.service.AudioFileService;
import com.ghatana.media.AudioVideoLibrary;
import com.ghatana.media.tts.api.TtsEngine;
import com.ghatana.tts.core.grpc.proto.AudioChunk;
import com.ghatana.tts.core.grpc.proto.SynthesizeRequest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("PersistentTtsGrpcService [GH-90000]")
class PersistentTtsGrpcServiceTest {

    @Mock
    private AudioVideoLibrary library;

    @Mock
    private TtsEngine engine;

    private PersistentTtsGrpcService service;

    @BeforeEach
    void setUp() { // GH-90000
        service = new PersistentTtsGrpcService( // GH-90000
            library,
            mock(AudioFileService.class), // GH-90000
            new SimpleMeterRegistry() // GH-90000
        );
        lenient().when(library.getTtsEngine()).thenReturn(engine); // GH-90000
        MDC.clear(); // GH-90000
    }

    @Test
    @DisplayName("streamSynthesize rejects requests without tenant context [GH-90000]")
    void streamSynthesizeRejectsRequestsWithoutTenantContext() { // GH-90000
        CapturingObserver<AudioChunk> observer = new CapturingObserver<>(); // GH-90000

        service.streamSynthesize(SynthesizeRequest.newBuilder().setText("hello [GH-90000]").build(), observer);

        assertThat(observer.error).isInstanceOf(StatusRuntimeException.class); // GH-90000
        assertThat(((StatusRuntimeException) observer.error).getStatus().getCode()) // GH-90000
            .isEqualTo(Status.UNAUTHENTICATED.getCode()); // GH-90000
    }

    @Test
    @DisplayName("streamSynthesize delegates to base TTS streaming implementation [GH-90000]")
    void streamSynthesizeDelegatesToBaseImplementation() { // GH-90000
        MDC.put("tenantId", "tenant-a"); // GH-90000
        doAnswer(invocation -> { // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            java.util.function.Consumer<com.ghatana.media.common.AudioChunk> consumer = invocation.getArgument(2); // GH-90000
            consumer.accept(new com.ghatana.media.common.AudioChunk(new byte[] {1, 2, 3}, 1, false, 0)); // GH-90000
            consumer.accept(new com.ghatana.media.common.AudioChunk(new byte[] {4, 5}, 2, true, 1)); // GH-90000
            return null;
        }).when(engine).synthesizeStreaming(anyString(), any(), any()); // GH-90000

        CapturingObserver<AudioChunk> observer = new CapturingObserver<>(); // GH-90000
        service.streamSynthesize(SynthesizeRequest.newBuilder().setText("hello [GH-90000]").build(), observer);

        assertThat(observer.error).isNull(); // GH-90000
        assertThat(observer.completed).isTrue(); // GH-90000
        assertThat(observer.value).isNotNull(); // GH-90000
        assertThat(observer.value.getAudioData().toByteArray()).containsExactly(4, 5); // GH-90000
        assertThat(observer.value.getIsFinal()).isTrue(); // GH-90000
    }

    private static final class CapturingObserver<T> implements StreamObserver<T> {
        private T value;
        private Throwable error;
        private boolean completed;

        @Override
        public void onNext(T value) { // GH-90000
            this.value = value;
        }

        @Override
        public void onError(Throwable t) { // GH-90000
            this.error = t;
        }

        @Override
        public void onCompleted() { // GH-90000
            this.completed = true;
        }
    }
}