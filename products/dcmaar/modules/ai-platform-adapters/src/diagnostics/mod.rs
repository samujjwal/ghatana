// Diagnostics module for crash analysis and system health monitoring
// Implements Capability 1 from Horizontal Slice AI Plan #4

/// Crash correlation and hinting utilities
pub mod crash;

/// Re-export commonly used diagnostics types
pub use crash::{CrashAnalyzer, CrashHint, CrashHintEvent, SignalEvent, SignalType};