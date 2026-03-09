//! Error types and results for the agent

use std::{
    io,
    path::{Path, PathBuf},
    time::Duration,
};

/// A type alias for `std::result::Result<T, agent_rs::Error>`
pub type Result<T> = std::result::Result<T, Error>;

/// A type alias for gRPC status errors
pub type GrpcStatus = tonic::Status;

/// Top-level error type for the agent
#[derive(thiserror::Error, Debug, Clone)]
pub enum Error {
    // I/O and System Errors
    /// I/O errors
    #[error("I/O error: {0}")]
    Io(String),

    /// File system errors with path context
    #[error("File system error at {path}: {message}")]
    FileSystem {
        /// Path associated with the failure
        path: PathBuf,
        /// Explanation of what went wrong
        message: String,
    },

    // TLS and Security Errors
    /// Certificate generation error
    #[error("Failed to generate certificate: {0}")]
    CertificateGeneration(String),

    /// Certificate validation error
    #[error("Certificate validation failed: {0}")]
    CertificateValidation(String),

    /// Certificate parsing error
    #[error("Failed to parse certificate: {0}")]
    CertificateParse(String),

    /// Private key error
    #[error("Private key error: {0}")]
    PrivateKey(String),

    /// TLS handshake error
    #[error("TLS handshake failed: {0}")]
    TlsHandshake(String),

    /// TLS configuration error
    #[error("TLS configuration error: {0}")]
    TlsConfig(String),

    /// TLS protocol error
    #[error("TLS protocol error: {0}")]
    TlsProtocol(String),

    /// mTLS authentication error
    #[error("mTLS authentication failed: {0}")]
    MtlsAuth(String),

    // Configuration Errors
    /// Configuration errors
    #[error("Configuration error: {0}")]
    Config(String),

    /// Missing required configuration
    #[error("Missing required configuration: {0}")]
    MissingConfig(String),

    /// Invalid configuration value
    #[error("Invalid configuration value for {field}: {value} - {reason}")]
    InvalidConfig {
        /// The configuration field that failed validation
        field: String,
        /// The provided value that was invalid
        value: String,
        /// The reason the value was considered invalid
        reason: String,
    },

    // gRPC and Network Errors
    /// gRPC client errors
    #[error("gRPC client error: {0}")]
    GrpcClient(String),

    /// gRPC status errors
    #[error("gRPC status error: {0}")]
    GrpcStatus(#[from] tonic::Status),

    /// Transport errors
    #[error("Transport error: {0}")]
    Transport(String),

    /// Retry limit exceeded
    #[error("Retry limit exceeded after {attempt} attempts: {message}")]
    RetryLimitExceeded {
        /// Human-readable explanation of the failure
        message: String,
        /// Number of attempts that were made
        attempt: u32,
    },

    /// Certificate error
    #[error("Certificate error: {0}")]
    Certificate(String),

    /// Network errors
    #[error("Network error: {0}")]
    Network(String),

    /// Connection errors
    #[error("Connection error: {0}")]
    Connection(String),

    /// Timeout errors
    #[error("Operation timed out after {duration:?}: {context}")]
    Timeout {
        /// Length of time that elapsed before timing out
        duration: Duration,
        /// Additional context describing the operation
        context: String,
    },

    // Data Processing Errors
    /// Serialization/deserialization errors
    #[error("Serialization error: {0}")]
    Serialization(String),

    /// Validation errors
    #[error("Validation error: {0}")]
    Validation(String),

    /// Parsing errors
    #[error("Failed to parse {what}: {message}")]
    Parse {
        /// The entity that failed to parse
        what: String,
        /// Explanation of the parsing failure
        message: String,
    },

    // Storage and Database Errors
    /// Storage errors
    #[error("Storage error: {0}")]
    Storage(String),

    /// Database errors
    #[error("Database error: {0}")]
    Database(String),

    /// Migration errors
    #[error("Migration error: {0}")]
    Migration(String),

    /// Record not found
    #[error("Record not found: {entity} with ID {id:?}")]
    NotFound {
        /// The type of entity that was requested
        entity: &'static str,
        /// Identifier of the missing entity
        id: String,
    },

    // Security and Authentication
    /// Authentication/authorization errors
    #[error("Authentication error: {0}")]
    Auth(String),

    /// Permission denied errors
    #[error("Permission denied: {0}")]
    PermissionDenied(String),

    /// TLS/SSL errors
    #[error("TLS error: {0}")]
    Tls(String),

    // System and Resource Errors
    /// Resource exhaustion errors
    #[error("Resource exhausted: {0}")]
    ResourceExhausted(String),

    /// System errors
    #[error("System error: {0}")]
    System(String),

    /// External service errors
    #[error("External service error: {service} - {message}")]
    ExternalService {
        /// The external service that produced the error
        service: String,
        /// Details returned from the external service
        message: String,
    },

    // Plugin and Extension Errors
    /// Plugin loading/execution errors
    #[error("Plugin error: {0}")]
    Plugin(String),

    /// WASM module errors
    #[error("WASM error: {0}")]
    Wasm(String),

    // Other Errors
    /// Internal server errors
    #[error("Internal server error: {0}")]
    Internal(String),

    /// Other uncategorized errors
    #[error("Error: {0}")]
    Other(String),
}

// Standard Library and Common Crates
impl From<std::time::SystemTimeError> for Error {
    fn from(err: std::time::SystemTimeError) -> Self {
        Self::System(format!("System time error: {}", err))
    }
}

// Serialization
impl From<serde_json::Error> for Error {
    fn from(err: serde_json::Error) -> Self {
        Self::Serialization(format!("JSON error: {}", err))
    }
}

impl From<serde_yaml::Error> for Error {
    fn from(err: serde_yaml::Error) -> Self {
        Self::Serialization(format!("YAML error: {}", err))
    }
}

// Configuration
impl From<toml::de::Error> for Error {
    fn from(err: toml::de::Error) -> Self {
        Self::Config(format!("TOML deserialization error: {}", err))
    }
}

impl From<toml::ser::Error> for Error {
    fn from(err: toml::ser::Error) -> Self {
        Self::Config(format!("TOML serialization error: {}", err))
    }
}

// Network and HTTP
impl From<url::ParseError> for Error {
    fn from(err: url::ParseError) -> Self {
        Self::Config(format!("URL parse error: {}", err))
    }
}

impl From<hyper::Error> for Error {
    fn from(err: hyper::Error) -> Self {
        Self::Network(format!("HTTP error: {}", err))
    }
}

// Database
impl From<sqlx::Error> for Error {
    fn from(err: sqlx::Error) -> Self {
        match err {
            sqlx::Error::RowNotFound => Self::NotFound {
                entity: "database record",
                id: "unknown".to_string(),
            },
            sqlx::Error::Database(e) => Self::Database(format!("Database error: {}", e.message())),
            _ => Self::Database(err.to_string()),
        }
    }
}

// UUID
impl From<io::Error> for Error {
    fn from(err: io::Error) -> Self {
        Self::Io(err.to_string())
    }
}

impl From<tonic::transport::Error> for Error {
    fn from(err: tonic::transport::Error) -> Self {
        Self::Transport(err.to_string())
    }
}

impl From<uuid::Error> for Error {
    fn from(err: uuid::Error) -> Self {
        Self::Validation(format!("Invalid UUID: {}", err))
    }
}

// Filesystem utilities
impl From<walkdir::Error> for Error {
    fn from(err: walkdir::Error) -> Self {
        Self::FileSystem {
            path: err.path().unwrap_or_else(|| Path::new("")).to_path_buf(),
            message: err
                .io_error()
                .map(|e| e.to_string())
                .unwrap_or_else(|| "Unknown filesystem error".to_string()),
        }
    }
}

// System information
// Note: sysinfo 0.30+ doesn't have SysError type

impl From<rustls::Error> for Error {
    fn from(err: rustls::Error) -> Self {
        Error::TlsProtocol(err.to_string())
    }
}

// TLS errors are handled via rustls::Error directly

impl From<rcgen::Error> for Error {
    fn from(err: rcgen::Error) -> Self {
        Error::CertificateGeneration(err.to_string())
    }
}

impl From<rustls_pemfile::Error> for Error {
    fn from(err: rustls_pemfile::Error) -> Self {
        Error::CertificateParse(format!("{:?}", err))
    }
}

// Note: rustls::sign::SignError was removed in newer versions

impl From<webpki::Error> for Error {
    fn from(err: webpki::Error) -> Self {
        Error::CertificateValidation(err.to_string())
    }
}

// Note: Do not implement From<tonic::Status> manually; it's covered by the #[from] on the GrpcStatus variant.

impl Error {
    /// Create a new gRPC client error
    pub fn grpc_client<S: Into<String>>(msg: S) -> Self {
        Error::GrpcClient(msg.into())
    }

    /// Create a new validation error with the provided message
    pub fn validation<S: Into<String>>(msg: S) -> Self {
        Self::Validation(msg.into())
    }

    /// Create a new not found error
    pub fn not_found(entity: &'static str, id: impl ToString) -> Self {
        Self::NotFound {
            entity,
            id: id.to_string(),
        }
    }

    /// Create a new timeout error
    pub fn timeout(duration: std::time::Duration, context: impl Into<String>) -> Self {
        Self::Timeout {
            duration,
            context: context.into(),
        }
    }

    /// Check if the error is a not found error
    pub fn is_not_found(&self) -> bool {
        matches!(self, Self::NotFound { .. })
    }

    /// Check if the error is a validation error
    pub fn is_validation(&self) -> bool {
        matches!(self, Self::Validation(_))
    }

    /// Check if the error is a timeout error
    pub fn is_timeout(&self) -> bool {
        matches!(self, Self::Timeout { .. })
    }

    /// Create a new certificate generation error
    pub fn cert_gen<S: Into<String>>(msg: S) -> Self {
        Error::CertificateGeneration(msg.into())
    }

    /// Create a new certificate validation error
    pub fn cert_validation<S: Into<String>>(msg: S) -> Self {
        Error::CertificateValidation(msg.into())
    }

    /// Create a new private key error
    pub fn private_key<S: Into<String>>(msg: S) -> Self {
        Error::PrivateKey(msg.into())
    }

    /// Create a new TLS handshake error
    pub fn tls_handshake<S: Into<String>>(msg: S) -> Self {
        Error::TlsHandshake(msg.into())
    }

    /// Create a new mTLS authentication error
    pub fn mtls_auth<S: Into<String>>(msg: S) -> Self {
        Error::MtlsAuth(msg.into())
    }
}

impl From<tonic::metadata::errors::InvalidMetadataValue> for Error {
    fn from(err: tonic::metadata::errors::InvalidMetadataValue) -> Self {
        Self::Config(format!("Invalid metadata value: {}", err))
    }
}

impl From<crate::storage::StorageError> for Error {
    fn from(err: crate::storage::StorageError) -> Self {
        Self::Storage(err.to_string())
    }
}
