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

@ExtendWith(MockitoExtension.class) 
@DisplayName("PersistentTtsGrpcService")
class PersistentTtsGrpcServiceTest {

    @Mock
    private AudioVideoLibrary library;

    @Mock
    private TtsEngine engine;

    private PersistentTtsGrpcService service;

    @BeforeEach
    void setUp() { 
        service = new PersistentTtsGrpcService( 
            library,
            mock(AudioFileService.class), 
            new SimpleMeterRegistry() 
        );
        lenient().when(library.getTtsEngine()).thenReturn(engine); 
        MDC.clear(); 
    }

    @Test
    @DisplayName("streamSynthesize rejects requests without tenant context")
    void streamSynthesizeRejectsRequestsWithoutTenantContext() { 
        CapturingObserver<AudioChunk> observer = new CapturingObserver<>(); 

        service.streamSynthesize(SynthesizeRequest.newBuilder().setText("hello").build(), observer);

        assertThat(observer.error).isInstanceOf(StatusRuntimeException.class); 
        assertThat(((StatusRuntimeException) observer.error).getStatus().getCode()) 
            .isEqualTo(Status.UNAUTHENTICATED.getCode()); 
    }

    @Test
    @DisplayName("streamSynthesize delegates to base TTS streaming implementation")
    void streamSynthesizeDelegatesToBaseImplementation() { 
        MDC.put("tenantId", "tenant-a"); 
        doAnswer(invocation -> { 
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<com.ghatana.media.common.AudioChunk> consumer = invocation.getArgument(2); 
            consumer.accept(new com.ghatana.media.common.AudioChunk(new byte[] {1, 2, 3}, 1, false, 0)); 
            consumer.accept(new com.ghatana.media.common.AudioChunk(new byte[] {4, 5}, 2, true, 1)); 
            return null;
        }).when(engine).synthesizeStreaming(anyString(), any(), any()); 

        CapturingObserver<AudioChunk> observer = new CapturingObserver<>(); 
        service.streamSynthesize(SynthesizeRequest.newBuilder().setText("hello").build(), observer);

        assertThat(observer.error).isNull(); 
        assertThat(observer.completed).isTrue(); 
        assertThat(observer.value).isNotNull(); 
        assertThat(observer.value.getAudioData().toByteArray()).containsExactly(4, 5); 
        assertThat(observer.value.getIsFinal()).isTrue(); 
    }

    private static final class CapturingObserver<T> implements StreamObserver<T> {
        private T value;
        private Throwable error;
        private boolean completed;

        @Override
        public void onNext(T value) { 
            this.value = value;
        }

        @Override
        public void onError(Throwable t) { 
            this.error = t;
        }

        @Override
        public void onCompleted() { 
            this.completed = true;
        }
    }
}