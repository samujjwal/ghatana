//! Foreground window sampling (stub)
//!
//! Real-world implementations are platform-specific. This module exposes a
//! small interface and a default "stub" sampler that can be replaced by OS
//! shims. This keeps downstream classifiers testable.

/// Foreground window snapshot capturing owning application and title.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct FGWindow {
    /// Name of the owning process/application.
    pub app: String,
    /// Title of the top-level window.
    pub title: String,
}

/// Trait for foreground window samplers.
pub trait Sampler: Send + Sync {
    /// Capture the current foreground window state.
    fn sample(&self) -> FGWindow;
}

/// A no-op sampler used in tests and unsupported platforms.
#[derive(Default)]
pub struct StubSampler;

impl Sampler for StubSampler {
    fn sample(&self) -> FGWindow {
        FGWindow::default()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn stub_sampler_returns_default() {
        let s = StubSampler;
        let w = s.sample();
        assert_eq!(w.app, "");
        assert_eq!(w.title, "");
    }
}
