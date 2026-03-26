package com.ghatana.tts.core.api;

/**
 * User-configurable settings for a TTS profile.
 *
 * @doc.type record
 * @doc.purpose Carries personalisation and privacy preferences for a profile
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ProfileSettings(
        String preferredLanguage,
        String defaultVoiceId,
        AdaptationMode adaptationMode,
        PrivacyLevel privacyLevel
) {
    public static ProfileSettings defaults() {
        return new ProfileSettings("en-US", null, AdaptationMode.BALANCED, PrivacyLevel.HIGH);
    }
}
