package com.ghatana.tts.core.api;

/**
 * Options for text-to-speech synthesis.
 * 
 * @doc.type record
 * @doc.purpose Synthesis configuration
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SynthesisOptions(
    String voiceId,
    String profileId,
    float speed,
    float pitch,
    float energy,
    String emotion,
    String language
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String voiceId;
        private String profileId;
        private float speed = 1.0f;
        private float pitch = 0.0f;
        private float energy = 1.0f;
        private String emotion = "neutral";
        private String language = "en-US";

        public Builder voiceId(String voiceId) {
            this.voiceId = voiceId;
            return this;
        }

        public Builder profileId(String profileId) {
            this.profileId = profileId;
            return this;
        }

        public Builder speed(float speed) {
            this.speed = speed;
            return this;
        }

        public Builder pitch(float pitch) {
            this.pitch = pitch;
            return this;
        }

        public Builder energy(float energy) {
            this.energy = energy;
            return this;
        }

        public Builder emotion(String emotion) {
            this.emotion = emotion;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public SynthesisOptions build() {
            return new SynthesisOptions(voiceId, profileId, speed, pitch, energy, emotion, language);
        }
    }
}
