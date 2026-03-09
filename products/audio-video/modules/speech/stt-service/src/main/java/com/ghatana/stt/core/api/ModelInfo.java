package com.ghatana.stt.core.api;

import java.util.List;
import java.util.Objects;

/**
 * Information about an available STT model.
 * 
 * @doc.type record
 * @doc.purpose Model metadata
 * @doc.layer api
 */
public record ModelInfo(
    /** Unique model identifier */
    String modelId,
    
    /** Human-readable name */
    String name,
    
    /** Model version */
    String version,
    
    /** Supported languages */
    List<String> languages,
    
    /** Model size in bytes */
    long sizeBytes,
    
    /** Whether the model is currently loaded */
    boolean isLoaded,
    
    /** Model format (ONNX, GGML, etc.) */
    ModelFormat format,
    
    /** Expected accuracy (WER on LibriSpeech test-clean) */
    Float expectedWer
) {
    public ModelInfo {
        Objects.requireNonNull(modelId, "modelId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        languages = languages != null ? List.copyOf(languages) : List.of();
    }

    /**
     * Get human-readable size string.
     */
    public String sizeString() {
        if (sizeBytes < 1024) return sizeBytes + " B";
        if (sizeBytes < 1024 * 1024) return String.format("%.1f KB", sizeBytes / 1024.0);
        if (sizeBytes < 1024 * 1024 * 1024) return String.format("%.1f MB", sizeBytes / (1024.0 * 1024));
        return String.format("%.2f GB", sizeBytes / (1024.0 * 1024 * 1024));
    }

    /**
     * Check if model supports a language.
     */
    public boolean supportsLanguage(String language) {
        return languages.stream().anyMatch(l -> 
            l.equalsIgnoreCase(language) || 
            l.startsWith(language.split("-")[0])
        );
    }

    public enum ModelFormat {
        ONNX,
        GGML,
        PYTORCH,
        TENSORFLOW
    }
}
