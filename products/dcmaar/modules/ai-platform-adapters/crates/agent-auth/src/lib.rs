//! Authentication and authorization for DCMAAR Agent
//!
//! Provides API key-based authentication, JWT tokens, and role-based access control
//! for securing agent connectors and HTTP endpoints.

pub mod api_key;
pub mod error;
pub mod jwt;
pub mod middleware;
pub mod rate_limit;
pub mod rbac;
pub mod tls;
pub mod validation;

pub use api_key::{ApiKey, ApiKeyStore};
pub use error::{AuthError, Result};
pub use jwt::{Claims, JwtAuth};
pub use middleware::auth_middleware;
pub use rate_limit::{InMemoryRateLimiter, MultiRateLimiter, RateLimitConfig, RateLimiterStore};
pub use rbac::{Permission, Role};
pub use tls::{TlsClientConfig, TlsServerConfig, generate_self_signed_cert};
pub use validation::{EventValidator, ValidatableEvent, ValidationConfig, ValidationError};
