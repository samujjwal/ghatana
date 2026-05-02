package com.ghatana.stt.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ghatana.audio.video.infrastructure.persistence.service.AudioFileService;
import com.ghatana.audio.video.infrastructure.persistence.service.TranscriptionService;
import com.ghatana.media.AudioVideoLibrary;
import com.ghatana.stt.core.grpc.proto.TranscribeRequest;
import com.ghatana.stt.core.grpc.proto.TranscribeResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

@DisplayName("PersistentSttGrpcService")
class PersistentSttGrpcServiceTest {

    private PersistentSttGrpcService service;

    @BeforeEach
    void setUp() { 
        service = new PersistentSttGrpcService( 
            mock(AudioVideoLibrary.class), 
            mock(AudioFileService.class), 
            mock(TranscriptionService.class), 
            new SimpleMeterRegistry() 
        );
        MDC.clear(); 
    }

    @Test
    @DisplayName("transcribe rejects requests without tenant context")
    void transcribeRejectsRequestsWithoutTenantContext() { 
        CapturingObserver<TranscribeResponse> observer = new CapturingObserver<>(); 

        service.transcribe(TranscribeRequest.newBuilder().build(), observer); 

        assertThat(observer.error).isInstanceOf(StatusRuntimeException.class); 
        assertThat(((StatusRuntimeException) observer.error).getStatus().getCode()) 
            .isEqualTo(Status.UNAUTHENTICATED.getCode()); 
    }

    private static final class CapturingObserver<T> implements StreamObserver<T> {
        private Throwable error;

        @Override
        public void onNext(T value) { 
        }

        @Override
        public void onError(Throwable t) { 
            this.error = t;
        }

        @Override
        public void onCompleted() { 
        }
    }
}
