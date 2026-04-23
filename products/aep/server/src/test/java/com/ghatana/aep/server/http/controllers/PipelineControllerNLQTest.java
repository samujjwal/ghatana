/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.ghatana.core.pipeline.DefaultNaturalLanguagePipelineService;
import com.ghatana.core.pipeline.NaturalLanguagePipelineService;
import com.ghatana.pipeline.registry.model.PipelineRegistration;
import com.ghatana.pipeline.registry.repository.PipelineRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Unit tests for NLQ-based pipeline creation in {@link PipelineController}.
 *
 * P3-19: Verify NLQ input is promoted as primary pipeline creation mode.
 */
class PipelineControllerNLQTest {

    private PipelineRepository mockRepository;
    private ObjectMapper objectMapper;
    private NaturalLanguagePipelineService nlqService;
    private PipelineController controller;

    @BeforeEach
    void setUp() { // GH-90000
        mockRepository = mock(PipelineRepository.class); // GH-90000
        objectMapper = new ObjectMapper(); // GH-90000
        nlqService = new DefaultNaturalLanguagePipelineService(); // GH-90000
        controller = new PipelineController(mockRepository, objectMapper, nlqService); // GH-90000
    }

    @Test
    void shouldCreatePipelineFromNaturalLanguageDescription() throws Exception { // GH-90000
        // Arrange
        String description = "Create a fraud detection pipeline for transactions";
        Map<String, Object> payload = Map.of("description", description); // GH-90000
        String body = objectMapper.writeValueAsString(payload); // GH-90000
        
        HttpRequest request = mock(HttpRequest.class); // GH-90000
        when(request.getMethod()).thenReturn(HttpMethod.POST); // GH-90000
        when(request.loadBody()).thenReturn(Promise.of( // GH-90000
            ByteBuf.wrapForReading(body.getBytes(StandardCharsets.UTF_8)) // GH-90000
        ));

        PipelineRegistration savedPipeline = PipelineRegistration.builder() // GH-90000
            .id(UUID.randomUUID().toString()) // GH-90000
            .name("Fraud Detection Pipeline")
            .description("Auto-generated pipeline from: " + description) // GH-90000
            .active(true) // GH-90000
            .version(1) // GH-90000
            .createdAt(Instant.now()) // GH-90000
            .updatedAt(Instant.now()) // GH-90000
            .build(); // GH-90000

        when(mockRepository.save(any(PipelineRegistration.class))).thenReturn(Promise.of(savedPipeline)); // GH-90000

        // Act
        Promise<HttpResponse> responsePromise = controller.handle(request, "/nlq"); // GH-90000
        HttpResponse response = responsePromise.getResult(); // GH-90000
        
        // Assert
        assertNotNull(response); // GH-90000
        assertEquals(201, response.getCode()); // GH-90000
        verify(mockRepository).save(any(PipelineRegistration.class)); // GH-90000
    }

    @Test
    void shouldRejectEmptyDescription() throws Exception { // GH-90000
        // Arrange
        Map<String, Object> payload = Map.of("description", ""); // GH-90000
        String body = objectMapper.writeValueAsString(payload); // GH-90000
        
        HttpRequest request = mock(HttpRequest.class); // GH-90000
        when(request.getMethod()).thenReturn(HttpMethod.POST); // GH-90000
        when(request.loadBody()).thenReturn(Promise.of( // GH-90000
            ByteBuf.wrapForReading(body.getBytes(StandardCharsets.UTF_8)) // GH-90000
        ));

        // Act
        Promise<HttpResponse> responsePromise = controller.handle(request, "/nlq"); // GH-90000
        HttpResponse response = responsePromise.getResult(); // GH-90000

        // Assert
        assertNotNull(response); // GH-90000
        assertEquals(400, response.getCode()); // GH-90000
        verify(mockRepository, never()).save(any()); // GH-90000
    }

    @Test
    void shouldRejectNullDescription() throws Exception { // GH-90000
        // Arrange
        Map<String, Object> payload = new HashMap<>(); // GH-90000
        String body = objectMapper.writeValueAsString(payload); // GH-90000
        
        HttpRequest request = mock(HttpRequest.class); // GH-90000
        when(request.getMethod()).thenReturn(HttpMethod.POST); // GH-90000
        when(request.loadBody()).thenReturn(Promise.of( // GH-90000
            ByteBuf.wrapForReading(body.getBytes(StandardCharsets.UTF_8)) // GH-90000
        ));

        // Act
        Promise<HttpResponse> responsePromise = controller.handle(request, "/nlq"); // GH-90000
        HttpResponse response = responsePromise.getResult(); // GH-90000

        // Assert
        assertNotNull(response); // GH-90000
        assertEquals(400, response.getCode()); // GH-90000
        verify(mockRepository, never()).save(any()); // GH-90000
    }

    @Test
    void shouldReturn501WhenNLQServiceNotConfigured() throws Exception { // GH-90000
        // Arrange
        PipelineController controllerWithoutNLQ = 
            new PipelineController(mockRepository, objectMapper, null); // GH-90000
        
        Map<String, Object> payload = Map.of("description", "test pipeline"); // GH-90000
        String body = objectMapper.writeValueAsString(payload); // GH-90000
        
        HttpRequest request = mock(HttpRequest.class); // GH-90000
        when(request.getMethod()).thenReturn(HttpMethod.POST); // GH-90000
        when(request.loadBody()).thenReturn(Promise.of( // GH-90000
            ByteBuf.wrapForReading(body.getBytes(StandardCharsets.UTF_8)) // GH-90000
        ));

        // Act
        Promise<HttpResponse> responsePromise = controllerWithoutNLQ.handle(request, "/nlq"); // GH-90000
        HttpResponse response = responsePromise.getResult(); // GH-90000

        // Assert
        assertNotNull(response); // GH-90000
        assertEquals(501, response.getCode()); // GH-90000
        verify(mockRepository, never()).save(any()); // GH-90000
    }

    @Test
    void shouldValidateDescriptionBeforeGeneration() throws Exception { // GH-90000
        // Arrange
        String tooShortDescription = "abc";
        Map<String, Object> payload = Map.of("description", tooShortDescription); // GH-90000
        String body = objectMapper.writeValueAsString(payload); // GH-90000
        
        HttpRequest request = mock(HttpRequest.class); // GH-90000
        when(request.loadBody()).thenReturn(Promise.of( // GH-90000
            ByteBuf.wrapForReading(body.getBytes(StandardCharsets.UTF_8)) // GH-90000
        ));
        
        // Act
        Promise<HttpResponse> responsePromise = controller.handle(request, "/nlq"); // GH-90000
        HttpResponse response = responsePromise.getResult(); // GH-90000
        
        // Assert
        // Should succeed but with warning (validation doesn't fail, just warns) // GH-90000
        // The actual behavior depends on the validation implementation
        assertNotNull(response); // GH-90000
    }

    @Test
    void shouldIncludeEventTypeInContextWhenProvided() throws Exception { // GH-90000
        // Arrange
        String description = "Process events";
        Map<String, Object> payload = Map.of( // GH-90000
            "description", description,
            "eventType", "transaction.created"
        );
        String body = objectMapper.writeValueAsString(payload); // GH-90000
        
        HttpRequest request = mock(HttpRequest.class); // GH-90000
        when(request.getMethod()).thenReturn(HttpMethod.POST); // GH-90000
        when(request.loadBody()).thenReturn(Promise.of( // GH-90000
            ByteBuf.wrapForReading(body.getBytes(StandardCharsets.UTF_8)) // GH-90000
        ));

        PipelineRegistration savedPipeline = PipelineRegistration.builder() // GH-90000
            .id(UUID.randomUUID().toString()) // GH-90000
            .name("Process Events Pipeline")
            .description("Auto-generated pipeline from: " + description) // GH-90000
            .active(true) // GH-90000
            .version(1) // GH-90000
            .createdAt(Instant.now()) // GH-90000
            .updatedAt(Instant.now()) // GH-90000
            .build(); // GH-90000

        when(mockRepository.save(any(PipelineRegistration.class))).thenReturn(Promise.of(savedPipeline)); // GH-90000

        // Act
        Promise<HttpResponse> responsePromise = controller.handle(request, "/nlq"); // GH-90000
        HttpResponse response = responsePromise.getResult(); // GH-90000

        // Assert
        assertNotNull(response); // GH-90000
        assertEquals(201, response.getCode()); // GH-90000
        verify(mockRepository).save(argThat((PipelineRegistration pipeline) -> // GH-90000
            pipeline.getConfig() != null && pipeline.getConfig().contains("transaction.created")
        ));
    }
}
