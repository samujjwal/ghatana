use once_cell::sync::Lazy;

#[derive(Debug, Clone, Copy)]
pub struct AudioRuntimeConfig {
    pub waveform_bucket_limit: usize,
    pub playback_poll_interval_ms: u64,
    pub recording_initial_buffer_seconds: usize,
    pub circuit_breaker_failure_threshold: u32,
    pub circuit_breaker_cooldown_ms: u64,
}

impl AudioRuntimeConfig {
    pub fn current() -> &'static Self {
        static CONFIG: Lazy<AudioRuntimeConfig> = Lazy::new(AudioRuntimeConfig::from_env);
        &CONFIG
    }

    fn from_env() -> Self {
        Self {
            waveform_bucket_limit: read_env("AI_VOICE_WAVEFORM_BUCKET_LIMIT", 2048usize),
            playback_poll_interval_ms: read_env("AI_VOICE_PLAYBACK_POLL_MS", 10u64),
            recording_initial_buffer_seconds: read_env("AI_VOICE_RECORDING_BUFFER_SECONDS", 30usize),
            circuit_breaker_failure_threshold: read_env("AI_VOICE_AUDIO_BREAKER_FAILURES", 3u32),
            circuit_breaker_cooldown_ms: read_env("AI_VOICE_AUDIO_BREAKER_COOLDOWN_MS", 1500u64),
        }
    }
}

fn read_env<T>(name: &str, default: T) -> T
where
    T: std::str::FromStr + Copy,
{
    std::env::var(name)
        .ok()
        .and_then(|value| value.parse::<T>().ok())
        .unwrap_or(default)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn default_config_is_sane() {
        let config = AudioRuntimeConfig::from_env();
        assert!(config.waveform_bucket_limit > 0);
        assert!(config.playback_poll_interval_ms > 0);
        assert!(config.recording_initial_buffer_seconds > 0);
        assert!(config.circuit_breaker_failure_threshold > 0);
        assert!(config.circuit_breaker_cooldown_ms > 0);
    }
}