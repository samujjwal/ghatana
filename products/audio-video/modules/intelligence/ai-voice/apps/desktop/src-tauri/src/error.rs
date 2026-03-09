//! Error types for the AI Voice desktop application.

use serde::Serialize;
use thiserror::Error;

/// Application errors.
#[derive(Error, Debug)]
pub enum AppError {
    #[error("Audio error: {0}")]
    Audio(String),

    #[error("File not found: {0}")]
    FileNotFound(String),

    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),

    #[error("Python error: {0}")]
    Python(String),

    #[error("Python error: {0}")]
    PythonError(String),

    #[error("Model error: {0}")]
    Model(String),

    #[error("Training error: {0}")]
    Training(String),

    #[error("Separation error: {0}")]
    Separation(String),

    #[error("Conversion error: {0}")]
    Conversion(String),

    #[error("Invalid request: {0}")]
    InvalidRequest(String),

    #[error("Not initialized: {0}")]
    NotInitialized(String),
}

// Implement From<PyErr> for AppError to handle Python errors
impl From<pyo3::PyErr> for AppError {
    fn from(err: pyo3::PyErr) -> Self {
        AppError::PythonError(err.to_string())
    }
}

impl Serialize for AppError {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: serde::Serializer,
    {
        serializer.serialize_str(&self.to_string())
    }
}

/// Result type for AI Voice operations.
pub type AppResult<T> = Result<T, AppError>;
