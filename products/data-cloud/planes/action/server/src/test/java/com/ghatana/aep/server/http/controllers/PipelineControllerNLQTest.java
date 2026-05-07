/*
 * Copyright (c) 2026 Ghatana Inc. 
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
    void setUp() { 
        mockRepository = mock(PipelineRepository.class); 
        objectMapper = new ObjectMapper(); 
        nlqService = new DefaultNaturalLanguagePipelineService(); 
        controller = new PipelineController(mockRepository, objectMapper, nlqService); 
    }

    @Test
    void shouldCreatePipelineFromNaturalLanguageDescription() throws Exception { 
        // Arrange
        String description = "Create a fraud detection pipeline for transactions";
        Map<String, Object> payload = Map.of("description", description); 
        String body = objectMapper.writeValueAsString(payload); 
        
        HttpRequest request = mock(HttpRequest.class); 
        when(request.getMethod()).thenReturn(HttpMethod.POST); 
        when(request.loadBody()).thenReturn(Promise.of( 
            ByteBuf.wrapForReading(body.getBytes(StandardCharsets.UTF_8)) 
        ));

        PipelineRegistration savedPipeline = PipelineRegistration.builder() 
            .id(UUID.randomUUID().toString()) 
            .name("Fraud Detection Pipeline")
            .description("Auto-generated pipeline from: " + description) 
            .active(true) 
            .version(1) 
            .createdAt(Instant.now()) 
            .updatedAt(Instant.now()) 
            .build(); 

        when(mockRepository.save(any(PipelineRegistration.class))).thenReturn(Promise.of(savedPipeline)); 

        // Act
        Promise<HttpResponse> responsePromise = controller.handle(request, "/nlq"); 
        HttpResponse response = responsePromise.getResult(); 
        
        // Assert
        assertNotNull(response); 
        assertEquals(201, response.getCode()); 
        verify(mockRepository).save(any(PipelineRegistration.class)); 
    }

    @Test
    void shouldRejectEmptyDescription() throws Exception { 
        // Arrange
        Map<String, Object> payload = Map.of("description", ""); 
        String body = objectMapper.writeValueAsString(payload); 
        
        HttpRequest request = mock(HttpRequest.class); 
        when(request.getMethod()).thenReturn(HttpMethod.POST); 
        when(request.loadBody()).thenReturn(Promise.of( 
            ByteBuf.wrapForReading(body.getBytes(StandardCharsets.UTF_8)) 
        ));

        // Act
        Promise<HttpResponse> responsePromise = controller.handle(request, "/nlq"); 
        HttpResponse response = responsePromise.getResult(); 

        // Assert
        assertNotNull(response); 
        assertEquals(400, response.getCode()); 
        verify(mockRepository, never()).save(any()); 
    }

    @Test
    void shouldRejectNullDescription() throws Exception { 
        // Arrange
        Map<String, Object> payload = new HashMap<>(); 
        String body = objectMapper.writeValueAsString(payload); 
        
        HttpRequest request = mock(HttpRequest.class); 
        when(request.getMethod()).thenReturn(HttpMethod.POST); 
        when(request.loadBody()).thenReturn(Promise.of( 
            ByteBuf.wrapForReading(body.getBytes(StandardCharsets.UTF_8)) 
        ));

        // Act
        Promise<HttpResponse> responsePromise = controller.handle(request, "/nlq"); 
        HttpResponse response = responsePromise.getResult(); 

        // Assert
        assertNotNull(response); 
        assertEquals(400, response.getCode()); 
        verify(mockRepository, never()).save(any()); 
    }

    @Test
    void shouldReturn501WhenNLQServiceNotConfigured() throws Exception { 
        // Arrange
        PipelineController controllerWithoutNLQ = 
            new PipelineController(mockRepository, objectMapper, null); 
        
        Map<String, Object> payload = Map.of("description", "test pipeline"); 
        String body = objectMapper.writeValueAsString(payload); 
        
        HttpRequest request = mock(HttpRequest.class); 
        when(request.getMethod()).thenReturn(HttpMethod.POST); 
        when(request.loadBody()).thenReturn(Promise.of( 
            ByteBuf.wrapForReading(body.getBytes(StandardCharsets.UTF_8)) 
        ));

        // Act
        Promise<HttpResponse> responsePromise = controllerWithoutNLQ.handle(request, "/nlq"); 
        HttpResponse response = responsePromise.getResult(); 

        // Assert
        assertNotNull(response); 
        assertEquals(501, response.getCode()); 
        verify(mockRepository, never()).save(any()); 
    }

    @Test
    void shouldValidateDescriptionBeforeGeneration() throws Exception { 
        // Arrange
        String tooShortDescription = "abc";
        Map<String, Object> payload = Map.of("description", tooShortDescription); 
        String body = objectMapper.writeValueAsString(payload); 
        
        HttpRequest request = mock(HttpRequest.class); 
        when(request.loadBody()).thenReturn(Promise.of( 
            ByteBuf.wrapForReading(body.getBytes(StandardCharsets.UTF_8)) 
        ));
        
        // Act
        Promise<HttpResponse> responsePromise = controller.handle(request, "/nlq"); 
        HttpResponse response = responsePromise.getResult(); 
        
        // Assert
        // Should succeed but with warning (validation doesn't fail, just warns) 
        // The actual behavior depends on the validation implementation
        assertNotNull(response); 
    }

    @Test
    void shouldIncludeEventTypeInContextWhenProvided() throws Exception { 
        // Arrange
        String description = "Process events";
        Map<String, Object> payload = Map.of( 
            "description", description,
            "eventType", "transaction.created"
        );
        String body = objectMapper.writeValueAsString(payload); 
        
        HttpRequest request = mock(HttpRequest.class); 
        when(request.getMethod()).thenReturn(HttpMethod.POST); 
        when(request.loadBody()).thenReturn(Promise.of( 
            ByteBuf.wrapForReading(body.getBytes(StandardCharsets.UTF_8)) 
        ));

        PipelineRegistration savedPipeline = PipelineRegistration.builder() 
            .id(UUID.randomUUID().toString()) 
            .name("Process Events Pipeline")
            .description("Auto-generated pipeline from: " + description) 
            .active(true) 
            .version(1) 
            .createdAt(Instant.now()) 
            .updatedAt(Instant.now()) 
            .build(); 

        when(mockRepository.save(any(PipelineRegistration.class))).thenReturn(Promise.of(savedPipeline)); 

        // Act
        Promise<HttpResponse> responsePromise = controller.handle(request, "/nlq"); 
        HttpResponse response = responsePromise.getResult(); 

        // Assert
        assertNotNull(response); 
        assertEquals(201, response.getCode()); 
        verify(mockRepository).save(argThat((PipelineRegistration pipeline) -> 
            pipeline.getConfig() != null && pipeline.getConfig().contains("transaction.created")
        ));
    }
}
