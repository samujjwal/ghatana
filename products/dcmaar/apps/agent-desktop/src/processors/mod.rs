//! Processors module - data processing components

pub mod session_manager;
pub mod categorizer;
// pub mod activity_scorer;

pub use session_manager::{SessionManager, SessionManagerConfig};
pub use categorizer::Categorizer;
