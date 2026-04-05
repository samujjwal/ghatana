/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.voice;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;

/**
 * Handler for voice command endpoints.
 *
 * <p>Processes voice commands and returns structured responses.
 *
 * @doc.type interface
 * @doc.purpose Voice command processing
 * @doc.layer product
 * @doc.pattern Handler, REST API
 */
public interface VoiceCommandHandler {

    /**
     * Process voice command audio.
     *
     * @param audioData base64 encoded audio
     * @param format audio format (wav, mp3, etc.)
     * @param context voice context
     * @return promise of command result
     */
    Promise<VoiceResult> processCommand(String audioData, String format, VoiceContext context);

    /**
     * Process text command (post-ASR).
     *
     * @param text transcribed text
     * @param context voice context
     * @return promise of command result
     */
    Promise<VoiceResult> processText(String text, VoiceContext context);

    /**
     * Get supported voice commands.
     *
     * @param tenantId tenant identifier
     * @return promise of command definitions
     */
    Promise<List<VoiceCommand>> getSupportedCommands(String tenantId);

    /**
     * Validate voice command.
     *
     * @param text command text
     * @param tenantId tenant identifier
     * @return promise of validation result
     */
    Promise<ValidationResult> validateCommand(String text, String tenantId);

    /**
     * Get voice session history.
     *
     * @param sessionId session identifier
     * @return promise of session history
     */
    Promise<List<VoiceResult>> getSessionHistory(String sessionId);

    /**
     * Voice context.
     */
    record VoiceContext(
        String sessionId,
        String userId,
        String tenantId,
        String language,
        String previousCommand,
        Map<String, Object> slots
    ) {}

    /**
     * Voice command definition.
     */
    record VoiceCommand(
        String intent,
        String description,
        List<String> examples,
        List<SlotDefinition> slots,
        boolean requiresConfirmation
    ) {}

    /**
     * Slot definition.
     */
    record SlotDefinition(
        String name,
        String type,
        boolean required,
        List<String> validValues
    ) {}

    /**
     * Voice processing result.
     */
    record VoiceResult(
        String sessionId,
        String recognizedText,
        String intent,
        double confidence,
        Map<String, Object> slots,
        String response,
        Action action,
        boolean requiresConfirmation,
        long processingTimeMs
    ) {
        public enum Action {
            QUERY, CREATE, UPDATE, DELETE, NAVIGATE, UNKNOWN
        }
    }

    /**
     * Validation result.
     */
    record ValidationResult(
        boolean valid,
        String intent,
        List<String> missingSlots,
        List<String> errors
    ) {}
}
