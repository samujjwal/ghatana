//! Runtime utilities for supervising long-running async tasks.
//!
//! This module currently exposes the [`supervisor`] which provides a light
//! weight supervision tree for Tokio tasks. Additional runtime helpers can be
//! added here (e.g. backoff strategies, cancellation utilities, etc.).

pub mod supervisor;
