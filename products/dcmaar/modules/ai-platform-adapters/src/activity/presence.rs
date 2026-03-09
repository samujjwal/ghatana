//! Presence detection heuristics (platform-neutral).
//!
//! This module provides a small, testable utility to infer whether the user is
//! "present" (actively interacting with the device) based on idle thresholds.

/// Presence result.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Presence {
    /// Whether the user is currently considered active.
    pub active: bool,
    /// Number of whole seconds since the last observed user input.
    pub idle_secs: u64,
}

impl Presence {
    /// Construct an inactive presence state with the supplied idle duration.
    pub fn inactive(idle_secs: u64) -> Self {
        Self {
            active: false,
            idle_secs,
        }
    }
    /// Construct an active presence state with the supplied idle duration.
    pub fn active(idle_secs: u64) -> Self {
        Self {
            active: true,
            idle_secs,
        }
    }
}

/// Detect presence given the timestamp of last user input and current time.
///
/// - `last_input_ms`: epoch ms of the last observed user input (keyboard/mouse).
/// - `now_ms`: current epoch ms.
/// - `threshold_secs`: idle seconds threshold beyond which the user is considered inactive.
pub fn detect_presence(last_input_ms: Option<u64>, now_ms: u64, threshold_secs: u64) -> Presence {
    let idle_secs = match last_input_ms {
        Some(t) if now_ms > t => ((now_ms - t) / 1000) as u64,
        _ => 0,
    };
    if idle_secs > threshold_secs {
        Presence::inactive(idle_secs)
    } else {
        Presence::active(idle_secs)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn presence_active_when_under_threshold() {
        let now = 1_000_000;
        let last = now - 10_000; // 10s ago
        let p = detect_presence(Some(last), now, 30);
        assert!(p.active);
        assert_eq!(p.idle_secs, 10);
    }

    #[test]
    fn presence_inactive_when_over_threshold() {
        let now = 1_000_000;
        let last = now - 120_000; // 120s ago
        let p = detect_presence(Some(last), now, 60);
        assert!(!p.active);
        assert_eq!(p.idle_secs, 120);
    }

    #[test]
    fn presence_active_when_no_signal() {
        let p = detect_presence(None, 1_000_000, 60);
        assert!(p.active);
        assert_eq!(p.idle_secs, 0);
    }
}
