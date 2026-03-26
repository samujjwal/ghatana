package com.ghatana.tts.core.api;

/**
 * A user profile that stores voice preferences and usage history.
 *
 * @doc.type record
 * @doc.purpose Domain aggregate for a TTS user profile
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record UserProfile(
        String profileId,
        String displayName,
        ProfileSettings settings,
        ProfileStats stats
) {}
