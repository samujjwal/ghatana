use once_cell::sync::Lazy;
use std::sync::atomic::{AtomicU64, Ordering};

#[derive(Debug, Clone, Copy, Default)]
pub struct AudioMetricsSnapshot {
    pub audio_load_attempts: u64,
    pub audio_load_failures: u64,
    pub playbacks_started: u64,
    pub playback_failures: u64,
    pub recordings_started: u64,
    pub recording_failures: u64,
}

#[derive(Default)]
pub struct AudioRuntimeMetrics {
    audio_load_attempts: AtomicU64,
    audio_load_failures: AtomicU64,
    playbacks_started: AtomicU64,
    playback_failures: AtomicU64,
    recordings_started: AtomicU64,
    recording_failures: AtomicU64,
}

impl AudioRuntimeMetrics {
    pub fn global() -> &'static Self {
        static METRICS: Lazy<AudioRuntimeMetrics> = Lazy::new(AudioRuntimeMetrics::default);
        &METRICS
    }

    pub fn record_audio_load_attempt(&self) {
        self.audio_load_attempts.fetch_add(1, Ordering::Relaxed);
    }

    pub fn record_audio_load_failure(&self) {
        self.audio_load_failures.fetch_add(1, Ordering::Relaxed);
    }

    pub fn record_playback_started(&self) {
        self.playbacks_started.fetch_add(1, Ordering::Relaxed);
    }

    pub fn record_playback_failure(&self) {
        self.playback_failures.fetch_add(1, Ordering::Relaxed);
    }

    pub fn record_recording_started(&self) {
        self.recordings_started.fetch_add(1, Ordering::Relaxed);
    }

    pub fn record_recording_failure(&self) {
        self.recording_failures.fetch_add(1, Ordering::Relaxed);
    }

    pub fn snapshot(&self) -> AudioMetricsSnapshot {
        AudioMetricsSnapshot {
            audio_load_attempts: self.audio_load_attempts.load(Ordering::Relaxed),
            audio_load_failures: self.audio_load_failures.load(Ordering::Relaxed),
            playbacks_started: self.playbacks_started.load(Ordering::Relaxed),
            playback_failures: self.playback_failures.load(Ordering::Relaxed),
            recordings_started: self.recordings_started.load(Ordering::Relaxed),
            recording_failures: self.recording_failures.load(Ordering::Relaxed),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn metrics_snapshot_reflects_updates() {
        let metrics = AudioRuntimeMetrics::default();
        metrics.record_audio_load_attempt();
        metrics.record_audio_load_failure();
        metrics.record_playback_started();
        metrics.record_recording_started();

        let snapshot = metrics.snapshot();
        assert_eq!(snapshot.audio_load_attempts, 1);
        assert_eq!(snapshot.audio_load_failures, 1);
        assert_eq!(snapshot.playbacks_started, 1);
        assert_eq!(snapshot.recordings_started, 1);
    }
}