package com.ghatana.tts.core.registry;

import java.util.List;

/**
 * Persistent metadata entry for a registered voice model.
 *
 * @param voiceId        unique voice identifier
 * @param name           human-readable name
 * @param description    optional description
 * @param languages      supported language codes (e.g. "en-US")
 * @param gender         gender tag ("male", "female", "neutral")
 * @param sizeBytes      model file size in bytes
 * @param loaded         whether the model is currently resident in memory
 * @param cloned         whether this is a cloned (user-created) voice
 * @param modelPath      absolute path to the model artefact on disk
 * @param registeredAtMs epoch-millisecond timestamp of registration
 */
public record VoiceRegistryEntry(
        String voiceId,
        String name,
        String description,
        List<String> languages,
        String gender,
        long sizeBytes,
        boolean loaded,
        boolean cloned,
        String modelPath,
        long registeredAtMs) {
}
