//! Collectors module - data collection components

pub mod platform;
pub mod window_tracker;
pub mod browser_tracker;
pub mod idle_detector;

// Re-exports
pub use window_tracker::WindowTracker;
pub use browser_tracker::BrowserTracker;
pub use idle_detector::IdleDetector;
