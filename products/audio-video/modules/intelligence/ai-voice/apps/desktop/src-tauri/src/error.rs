//! Error types for the AI Voice desktop application.

use serde::Serialize;
use thiserror::Error;

/// Stable error categories shared across desktop audio operations.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
pub enum AppErrorCategory {
    InputValidation,
    AudioProcessing,
    PythonBridge,
    ModelLifecycle,
    Internal,
}

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

impl AppError {
    pub fn category(&self) -> AppErrorCategory {
        match self {
            AppError::InvalidRequest(_) | AppError::FileNotFound(_) => AppErrorCategory::InputValidation,
            AppError::Audio(_) | AppError::Separation(_) | AppError::Conversion(_) => {
                AppErrorCategory::AudioProcessing
            }
            AppError::Python(_) | AppError::PythonError(_) => AppErrorCategory::PythonBridge,
            AppError::Model(_) | AppError::Training(_) => AppErrorCategory::ModelLifecycle,
            AppError::Io(_) | AppError::NotInitialized(_) => AppErrorCategory::Internal,
        }
    }

    pub fn code(&self) -> &'static str {
        match self {
            AppError::Audio(_) => "audio.processing_failed",
            AppError::FileNotFound(_) => "input.file_not_found",
            AppError::Io(_) => "internal.io_failed",
            AppError::Python(_) | AppError::PythonError(_) => "python.bridge_failed",
            AppError::Model(_) => "model.lifecycle_failed",
            AppError::Training(_) => "model.training_failed",
            AppError::Separation(_) => "audio.separation_failed",
            AppError::Conversion(_) => "audio.conversion_failed",
            AppError::InvalidRequest(_) => "input.invalid_request",
            AppError::NotInitialized(_) => "internal.not_initialized",
        }
    }

    pub fn is_retryable(&self) -> bool {
        matches!(
            self,
            AppError::Io(_) | AppError::Python(_) | AppError::PythonError(_) | AppError::Model(_)
        )
    }
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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn classifies_validation_errors_consistently() {
        let error = AppError::InvalidRequest("unsupported format".to_string());

        assert_eq!(error.category(), AppErrorCategory::InputValidation);
        assert_eq!(error.code(), "input.invalid_request");
        assert!(!error.is_retryable());
    }

    #[test]
    fn marks_python_bridge_failures_retryable() {
        let error = AppError::PythonError("temporary python startup failure".to_string());

        assert_eq!(error.category(), AppErrorCategory::PythonBridge);
        assert_eq!(error.code(), "python.bridge_failed");
        assert!(error.is_retryable());
    }
}
