package com.ghatana.tutorputor.ai.service;

import com.ghatana.ai.service.LLMService;
import com.ghatana.tutorputor.contracts.ai.AiLearningProto;
import com.ghatana.tutorputor.contracts.ai.AiLearningServiceGrpc;
import io.activej.promise.Promise;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * @doc.type test
 * @doc.purpose Unit tests for AiLearningServiceImpl
 */
@DisplayName("AiLearningService Tests")
class AiLearningServiceImplTest {

    @Mock
    private LLMService llmService;

    private AiLearningServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new AiLearningServiceImpl(llmService);
    }

    @Test
    @DisplayName("Should generate learning path successfully")
    void shouldGenerateLearningPath() {
        // GIVEN
        when(llmService.generate(anyString())).thenReturn(Promise.of("{\"title\":\"Math Mastery\",\"nodes\":[{\"id\":\"1\",\"title\":\"Basics\"},{\"id\":\"2\",\"title\":\"Advanced\"}]}"));

        AiLearningProto.GeneratePathRequest request = AiLearningProto.GeneratePathRequest.newBuilder()
                .setSubject("Math")
                .setGoal("Learn Calculus")
                .setLearnerLevel("Beginner")
                .build();

        AtomicReference<AiLearningProto.GeneratePathResponse> responseRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        // WHEN
        service.generateLearningPath(request, new StreamObserver<>() {
            @Override
            public void onNext(AiLearningProto.GeneratePathResponse value) {
                responseRef.set(value);
            }

            @Override
            public void onError(Throwable t) {
                errorRef.set(t);
            }

            @Override
            public void onCompleted() {
                // Done
            }
        });

        // THEN
        assertNull(errorRef.get());
        assertNotNull(responseRef.get());
        assertEquals("Math Mastery", responseRef.get().getTitle());
        assertEquals(2, responseRef.get().getNodesCount());
    }

    @Test
    @DisplayName("Should handle AI failures gracefully")
    void shouldHandleAiFailures() {
        // GIVEN
        when(llmService.generate(anyString())).thenReturn(Promise.ofException(new RuntimeException("AI Down")));

        AiLearningProto.GeneratePathRequest request = AiLearningProto.GeneratePathRequest.newBuilder()
                .setSubject("Math")
                .build();

        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        // WHEN
        service.generateLearningPath(request, new StreamObserver<>() {
            @Override
            public void onNext(AiLearningProto.GeneratePathResponse value) {
            }

            @Override
            public void onError(Throwable t) {
                errorRef.set(t);
            }

            @Override
            public void onCompleted() {
            }
        });

        // THEN
        assertNotNull(errorRef.get());
        assertEquals("AI Down", errorRef.get().getMessage());
    }
}
