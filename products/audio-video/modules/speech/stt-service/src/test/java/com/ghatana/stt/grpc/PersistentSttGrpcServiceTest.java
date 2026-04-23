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
    void setUp() { // GH-90000
        service = new PersistentSttGrpcService( // GH-90000
            mock(AudioVideoLibrary.class), // GH-90000
            mock(AudioFileService.class), // GH-90000
            mock(TranscriptionService.class), // GH-90000
            new SimpleMeterRegistry() // GH-90000
        );
        MDC.clear(); // GH-90000
    }

    @Test
    @DisplayName("transcribe rejects requests without tenant context")
    void transcribeRejectsRequestsWithoutTenantContext() { // GH-90000
        CapturingObserver<TranscribeResponse> observer = new CapturingObserver<>(); // GH-90000

        service.transcribe(TranscribeRequest.newBuilder().build(), observer); // GH-90000

        assertThat(observer.error).isInstanceOf(StatusRuntimeException.class); // GH-90000
        assertThat(((StatusRuntimeException) observer.error).getStatus().getCode()) // GH-90000
            .isEqualTo(Status.UNAUTHENTICATED.getCode()); // GH-90000
    }

    private static final class CapturingObserver<T> implements StreamObserver<T> {
        private Throwable error;

        @Override
        public void onNext(T value) { // GH-90000
        }

        @Override
        public void onError(Throwable t) { // GH-90000
            this.error = t;
        }

        @Override
        public void onCompleted() { // GH-90000
        }
    }
}