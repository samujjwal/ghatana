package com.ghatana.tts.core.api;

/**
 * Profile settings for TTS.
 * 
 * @doc.type record
 * @doc.purpose Profile settings container
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ProfileSettings(
    String preferredLanguage,
    String defaultVoiceId,
    AdaptationMode adaptationMode,
    PrivacyLevel privacyLevel
) {
    public String getPreferredLanguage() { return preferredLanguage; }
    public String getDefaultVoiceId() { return defaultVoiceId; }
    public AdaptationMode getAdaptationMode() { return adaptationMode; }
    public PrivacyLevel getPrivacyLevel() { return privacyLevel; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String preferredLanguage = "en-US";
        private String defaultVoiceId;
        private AdaptationMode adaptationMode = AdaptationMode.BALANCED;
        private PrivacyLevel privacyLevel = PrivacyLevel.HIGH;

        public Builder preferredLanguage(String preferredLanguage) {
            this.preferredLanguage = preferredLanguage;
            return this;
        }

        public Builder defaultVoiceId(String defaultVoiceId) {
            this.defaultVoiceId = defaultVoiceId;
            return this;
        }

        public Builder adaptationMode(AdaptationMode adaptationMode) {
            this.adaptationMode = adaptationMode;
            return this;
        }

        public Builder privacyLevel(PrivacyLevel privacyLevel) {
            this.privacyLevel = privacyLevel;
            return this;
        }

        public ProfileSettings build() {
            return new ProfileSettings(preferredLanguage, defaultVoiceId, adaptationMode, privacyLevel);
        }
    }
}
