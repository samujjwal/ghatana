package com.ghatana.tts.core.api;

import java.util.List;

/**
 * Voice information.
 * 
 * @doc.type record
 * @doc.purpose Voice metadata container
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record VoiceInfo(
    String voiceId,
    String name,
    String description,
    List<String> languages,
    String gender,
    long sizeBytes,
    boolean isLoaded,
    boolean isCloned
) {
    public String getVoiceId() { return voiceId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<String> getLanguages() { return languages; }
    public String getGender() { return gender; }
    public long getSizeBytes() { return sizeBytes; }
    public boolean isLoaded() { return isLoaded; }
    public boolean isCloned() { return isCloned; }
}
