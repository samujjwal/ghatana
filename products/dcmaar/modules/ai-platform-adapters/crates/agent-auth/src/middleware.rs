//! Authentication middleware for Axum web framework
//!
//! Provides middleware for protecting HTTP endpoints with API key or JWT authentication.

use axum::{
    extract::{Request, State},
    http::StatusCode,
    middleware::Next,
    response::{IntoResponse, Response},
    Json,
};
use serde_json::json;
use std::sync::Arc;
use tracing::warn;

use crate::{
    api_key::{verify_api_key, ApiKeyStore},
    error::AuthError,
    jwt::JwtAuth,
    rbac::{Permission, Role},
};

/// Authentication state shared across middleware
#[derive(Clone)]
pub struct AuthState {
    pub api_key_store: Arc<dyn ApiKeyStore>,
    pub jwt_auth: Arc<JwtAuth>,
}

/// Authenticated user information extracted from the request
#[derive(Debug, Clone)]
pub struct AuthUser {
    pub id: String,
    pub role: Role,
}

impl AuthUser {
    /// Check if user has the required permission
    pub fn has_permission(&self, permission: Permission) -> bool {
        self.role.has_permission(permission)
    }

    /// Require a specific permission (returns error if not authorized)
    pub fn require_permission(&self, permission: Permission) -> Result<(), AuthError> {
        if self.has_permission(permission) {
            Ok(())
        } else {
            Err(AuthError::InsufficientPermissions {
                required: format!("{:?}", permission),
                has: format!("{:?}", self.role.permissions()),
            })
        }
    }
}

/// Middleware function for API key or JWT authentication
pub async fn auth_middleware(
    State(auth_state): State<AuthState>,
    mut request: Request,
    next: Next,
) -> Result<Response, AuthResponse> {
    // Extract Authorization header
    let auth_header = request
        .headers()
        .get("Authorization")
        .and_then(|h| h.to_str().ok())
        .ok_or(AuthError::MissingAuthHeader)?;

    // Try to authenticate with either API key or JWT
    let auth_user = authenticate(&auth_state, auth_header).await?;

    // Insert authenticated user into request extensions
    request.extensions_mut().insert(auth_user);

    Ok(next.run(request).await)
}

/// Authenticate a request using API key or JWT
async fn authenticate(auth_state: &AuthState, auth_header: &str) -> Result<AuthUser, AuthError> {
    // Try API key authentication first
    let store_ref: &dyn ApiKeyStore = auth_state.api_key_store.as_ref();
    if let Ok(api_key) = verify_api_key(store_ref, auth_header).await {
        return Ok(AuthUser {
            id: api_key.id.to_string(),
            role: api_key.role,
        });
    }

    // Try JWT authentication
    if let Ok(claims) = auth_state.jwt_auth.extract_from_header(auth_header) {
        return Ok(AuthUser {
            id: claims.sub,
            role: claims.role,
        });
    }

    // Neither API key nor JWT worked
    Err(AuthError::InvalidCredentials)
}

/// Custom response type for authentication errors
pub struct AuthResponse(AuthError);

impl IntoResponse for AuthResponse {
    fn into_response(self) -> Response {
        let (status, message) = match self.0 {
            AuthError::InvalidApiKey => (StatusCode::UNAUTHORIZED, "Invalid API key"),
            AuthError::ApiKeyExpired => (StatusCode::UNAUTHORIZED, "API key expired"),
            AuthError::ApiKeyRevoked => (StatusCode::UNAUTHORIZED, "API key revoked"),
            AuthError::MissingAuthHeader => (StatusCode::UNAUTHORIZED, "Missing Authorization header"),
            AuthError::InvalidAuthHeaderFormat => {
                (StatusCode::UNAUTHORIZED, "Invalid Authorization header format")
            }
            AuthError::InvalidToken(_) => (StatusCode::UNAUTHORIZED, "Invalid JWT token"),
            AuthError::TokenExpired => (StatusCode::UNAUTHORIZED, "JWT token expired"),
            AuthError::InvalidCredentials => (StatusCode::UNAUTHORIZED, "Invalid credentials"),
            AuthError::InsufficientPermissions { .. } => (StatusCode::FORBIDDEN, "Insufficient permissions"),
            AuthError::RateLimitExceeded(_) => (StatusCode::TOO_MANY_REQUESTS, "Rate limit exceeded"),
            AuthError::Internal(_) => (StatusCode::INTERNAL_SERVER_ERROR, "Internal authentication error"),
        };

        warn!(error = ?self.0, "Authentication failed");

        (
            status,
            Json(json!({
                "error": message,
                "code": status.as_u16(),
            })),
        )
            .into_response()
    }
}

impl From<AuthError> for AuthResponse {
    fn from(err: AuthError) -> Self {
        AuthResponse(err)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{api_key::InMemoryApiKeyStore, rbac::Permission};

    #[test]
    fn test_auth_user_permissions() {
        let admin_user = AuthUser {
            id: "admin-1".to_string(),
            role: Role::Admin,
        };

        assert!(admin_user.has_permission(Permission::WriteEvents));
        assert!(admin_user.has_permission(Permission::ManageApiKeys));
        assert!(admin_user.require_permission(Permission::ModifyConfig).is_ok());

        let reader_user = AuthUser {
            id: "reader-1".to_string(),
            role: Role::Reader,
        };

        assert!(reader_user.has_permission(Permission::ReadEvents));
        assert!(!reader_user.has_permission(Permission::WriteEvents));
        assert!(reader_user.require_permission(Permission::WriteEvents).is_err());
    }
}
