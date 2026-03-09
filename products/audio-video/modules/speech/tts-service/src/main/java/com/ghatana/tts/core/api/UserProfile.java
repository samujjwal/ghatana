package com.ghatana.tts.core.api;

/**
 * User profile for TTS.
 * 
 * @doc.type record
 * @doc.purpose User profile container
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record UserProfile(
    String profileId,
    String displayName,
    ProfileSettings settings,
    ProfileStats stats
) {
    public String getProfileId() { return profileId; }
    public String getDisplayName() { return displayName; }
    public ProfileSettings getSettings() { return settings; }
    public ProfileStats getStats() { return stats; }
}
