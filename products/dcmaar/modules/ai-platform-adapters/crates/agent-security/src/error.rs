//! Error types for security operations

use std::fmt;

/// Errors that can occur during security operations
#[derive(Debug)]
pub enum SecurityError {
    /// Invalid configuration
    Config(String),

    /// Certificate error
    Certificate(String),

    /// Key error
    Key(String),

    /// Keychain error
    Keychain(String),

    /// I/O error
    Io(std::io::Error),

    /// Base64 decode error
    Base64(base64::DecodeError),

    /// JSON error
    Json(serde_json::Error),

    /// Rustls error
    Tls(String),

    /// Other error
    Other(String),
}

impl fmt::Display for SecurityError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            SecurityError::Config(msg) => write!(f, "Invalid configuration: {}", msg),
            SecurityError::Certificate(msg) => write!(f, "Certificate error: {}", msg),
            SecurityError::Key(msg) => write!(f, "Key error: {}", msg),
            SecurityError::Keychain(msg) => write!(f, "Keychain error: {}", msg),
            SecurityError::Io(err) => write!(f, "I/O error: {}", err),
            SecurityError::Base64(err) => write!(f, "Base64 decode error: {}", err),
            SecurityError::Json(err) => write!(f, "JSON error: {}", err),
            SecurityError::Tls(msg) => write!(f, "TLS error: {}", msg),
            SecurityError::Other(msg) => write!(f, "Security error: {}", msg),
        }
    }
}

impl std::error::Error for SecurityError {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        match self {
            SecurityError::Io(e) => Some(e),
            SecurityError::Base64(e) => Some(e),
            SecurityError::Json(e) => Some(e),
            _ => None,
        }
    }
}

impl From<std::io::Error> for SecurityError {
    fn from(err: std::io::Error) -> Self {
        SecurityError::Io(err)
    }
}

impl From<base64::DecodeError> for SecurityError {
    fn from(err: base64::DecodeError) -> Self {
        SecurityError::Base64(err)
    }
}

impl From<serde_json::Error> for SecurityError {
    fn from(err: serde_json::Error) -> Self {
        SecurityError::Json(err)
    }
}

impl From<ring::error::Unspecified> for SecurityError {
    fn from(err: ring::error::Unspecified) -> Self {
        SecurityError::Other(format!("Crypto error: {:?}", err))
    }
}

impl From<rcgen::Error> for SecurityError {
    fn from(err: rcgen::Error) -> Self {
        SecurityError::Certificate(format!("{:?}", err))
    }
}

impl From<rustls::Error> for SecurityError {
    fn from(err: rustls::Error) -> Self {
        SecurityError::Tls(format!("{:?}", err))
    }
}

impl From<aes_gcm::Error> for SecurityError {
    fn from(err: aes_gcm::Error) -> Self {
        SecurityError::Other(format!("Encryption error: {:?}", err))
    }
}

impl From<ring::error::KeyRejected> for SecurityError {
    fn from(err: ring::error::KeyRejected) -> Self {
        SecurityError::Key(format!("Key rejected: {:?}", err))
    }
}

// Helper for converting string errors
impl From<&str> for SecurityError {
    fn from(err: &str) -> Self {
        SecurityError::Other(err.to_string())
    }
}

impl From<String> for SecurityError {
    fn from(err: String) -> Self {
        SecurityError::Other(err)
    }
}
