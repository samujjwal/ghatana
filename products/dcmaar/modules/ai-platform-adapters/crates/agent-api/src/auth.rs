//! Authentication and authorization middleware
//!
//! This module provides:
//! - JWT token validation
//! - API key authentication
//! - Role-based access control (RBAC)
//! - Token generation utilities

use axum::{
    extract::{Request, State},
    http::{header, StatusCode},
    middleware::Next,
    response::{IntoResponse, Response},
};
use jsonwebtoken::{decode, encode, DecodingKey, EncodingKey, Header, Validation};
use serde::{Deserialize, Serialize};
use std::{collections::HashMap, sync::Arc, time::{SystemTime, UNIX_EPOCH}};
use tokio::sync::RwLock;

use crate::error::ApiError;

/// User roles for RBAC
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "lowercase")]
pub enum Role {
    /// Administrator with full access
    Admin,
    /// Operator with read/write access
    Operator,
    /// Viewer with read-only access
    Viewer,
}

/// JWT claims
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Claims {
    /// Subject (user ID)
    pub sub: String,
    /// User role
    pub role: Role,
    /// Issued at (timestamp)
    pub iat: u64,
    /// Expiration time (timestamp)
    pub exp: u64,
    /// Issuer
    pub iss: String,
}

impl Claims {
    /// Create new claims for a user
    pub fn new(user_id: String, role: Role, ttl_seconds: u64) -> Self {
        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs();

        Self {
            sub: user_id,
            role,
            iat: now,
            exp: now + ttl_seconds,
            iss: "dcmaar-agent".to_string(),
        }
    }

    /// Check if the token is expired
    pub fn is_expired(&self) -> bool {
        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs();

        self.exp < now
    }

    /// Check if user has required role (or higher)
    pub fn has_role(&self, required: &Role) -> bool {
        match (&self.role, required) {
            (Role::Admin, _) => true,
            (Role::Operator, Role::Operator) => true,
            (Role::Operator, Role::Viewer) => true,
            (Role::Viewer, Role::Viewer) => true,
            _ => false,
        }
    }
}

/// API key information
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ApiKey {
    /// API key ID
    pub id: String,
    /// Hashed key value
    pub key_hash: String,
    /// Owner/description
    pub owner: String,
    /// Role associated with this key
    pub role: Role,
    /// Creation timestamp
    pub created_at: u64,
    /// Last used timestamp
    pub last_used: Option<u64>,
    /// Enabled status
    pub enabled: bool,
}

/// Authentication state
#[derive(Clone)]
pub struct AuthState {
    /// JWT secret for signing tokens
    jwt_secret: String,
    /// API keys storage
    api_keys: Arc<RwLock<HashMap<String, ApiKey>>>,
}

impl AuthState {
    /// Create new authentication state
    pub fn new(jwt_secret: String) -> Self {
        Self {
            jwt_secret,
            api_keys: Arc::new(RwLock::new(HashMap::new())),
        }
    }

    /// Generate a JWT token for a user
    pub fn generate_token(&self, user_id: String, role: Role, ttl_seconds: u64) -> Result<String, ApiError> {
        let claims = Claims::new(user_id, role, ttl_seconds);

        encode(
            &Header::default(),
            &claims,
            &EncodingKey::from_secret(self.jwt_secret.as_bytes()),
        )
        .map_err(|e| ApiError::Internal(format!("Failed to generate token: {}", e)))
    }

    /// Verify a JWT token and return claims
    pub fn verify_token(&self, token: &str) -> Result<Claims, ApiError> {
        let validation = Validation::default();

        decode::<Claims>(
            token,
            &DecodingKey::from_secret(self.jwt_secret.as_bytes()),
            &validation,
        )
        .map(|data| data.claims)
        .map_err(|e| ApiError::Unauthorized(format!("Invalid token: {}", e)))
    }

    /// Add an API key
    pub async fn add_api_key(&self, api_key: ApiKey) {
        let mut keys = self.api_keys.write().await;
        keys.insert(api_key.id.clone(), api_key);
    }

    /// Verify an API key and return associated information
    pub async fn verify_api_key(&self, key: &str) -> Result<ApiKey, ApiError> {
        // Hash the provided key
        let key_hash = format!("{:x}", md5::compute(key));

        let mut keys = self.api_keys.write().await;

        // Find matching API key
        if let Some(api_key) = keys.values_mut().find(|k| k.key_hash == key_hash && k.enabled) {
            // Update last used timestamp
            api_key.last_used = Some(
                SystemTime::now()
                    .duration_since(UNIX_EPOCH)
                    .unwrap()
                    .as_secs(),
            );

            Ok(api_key.clone())
        } else {
            Err(ApiError::Unauthorized("Invalid API key".to_string()))
        }
    }

    /// Revoke an API key
    pub async fn revoke_api_key(&self, key_id: &str) -> Result<(), ApiError> {
        let mut keys = self.api_keys.write().await;

        if let Some(api_key) = keys.get_mut(key_id) {
            api_key.enabled = false;
            Ok(())
        } else {
            Err(ApiError::NotFound(format!("API key {} not found", key_id)))
        }
    }

    /// List all API keys
    pub async fn list_api_keys(&self) -> Vec<ApiKey> {
        self.api_keys.read().await.values().cloned().collect()
    }
}

/// Extract user information from request
#[derive(Debug, Clone)]
pub struct AuthUser {
    /// User ID
    pub id: String,
    /// User role
    pub role: Role,
    /// Authentication method used
    pub auth_method: String,
}

/// Authentication middleware
pub async fn auth_middleware(
    State(auth_state): State<AuthState>,
    mut request: Request,
    next: Next,
) -> Result<Response, ApiError> {
    // Try JWT authentication first
    if let Some(auth_header) = request.headers().get(header::AUTHORIZATION) {
        if let Ok(auth_str) = auth_header.to_str() {
            if auth_str.starts_with("Bearer ") {
                let token = auth_str.trim_start_matches("Bearer ");

                match auth_state.verify_token(token) {
                    Ok(claims) => {
                        if claims.is_expired() {
                            return Err(ApiError::Unauthorized("Token expired".to_string()));
                        }

                        // Add user info to request extensions
                        request.extensions_mut().insert(AuthUser {
                            id: claims.sub,
                            role: claims.role,
                            auth_method: "jwt".to_string(),
                        });

                        return Ok(next.run(request).await);
                    }
                    Err(_) => {
                        // Fall through to API key authentication
                    }
                }
            }
        }
    }

    // Try API key authentication
    if let Some(api_key_header) = request.headers().get("X-API-Key") {
        if let Ok(api_key) = api_key_header.to_str() {
            match auth_state.verify_api_key(api_key).await {
                Ok(key_info) => {
                    // Add user info to request extensions
                    request.extensions_mut().insert(AuthUser {
                        id: key_info.id.clone(),
                        role: key_info.role,
                        auth_method: "api_key".to_string(),
                    });

                    return Ok(next.run(request).await);
                }
                Err(e) => {
                    return Err(e);
                }
            }
        }
    }

    // No valid authentication found
    Err(ApiError::Unauthorized(
        "Authentication required".to_string(),
    ))
}

/// Require specific role middleware
pub fn require_role(required_role: Role) -> impl Fn(Request, Next) -> std::pin::Pin<Box<dyn std::future::Future<Output = Result<Response, Response>> + Send>> + Clone {
    move |request: Request, next: Next| {
        let required = required_role.clone();
        Box::pin(async move {
            // Get auth user from extensions
            if let Some(user) = request.extensions().get::<AuthUser>() {
                if user.role.has_role(&required) {
                    Ok(next.run(request).await)
                } else {
                    Err((
                        StatusCode::FORBIDDEN,
                        format!("Requires {:?} role", required),
                    )
                        .into_response())
                }
            } else {
                Err((
                    StatusCode::UNAUTHORIZED,
                    "Authentication required".to_string(),
                )
                    .into_response())
            }
        })
    }
}

impl Role {
    fn has_role(&self, required: &Role) -> bool {
        match (self, required) {
            (Role::Admin, _) => true,
            (Role::Operator, Role::Operator) => true,
            (Role::Operator, Role::Viewer) => true,
            (Role::Viewer, Role::Viewer) => true,
            _ => false,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_claims_creation() {
        let claims = Claims::new("user123".to_string(), Role::Admin, 3600);

        assert_eq!(claims.sub, "user123");
        assert_eq!(claims.role, Role::Admin);
        assert_eq!(claims.iss, "dcmaar-agent");
        assert!(!claims.is_expired());
    }

    #[test]
    fn test_claims_expiration() {
        let mut claims = Claims::new("user123".to_string(), Role::Admin, 3600);

        // Not expired yet
        assert!(!claims.is_expired());

        // Make it expired
        claims.exp = 0;
        assert!(claims.is_expired());
    }

    #[test]
    fn test_role_hierarchy() {
        let admin_claims = Claims::new("admin".to_string(), Role::Admin, 3600);
        let operator_claims = Claims::new("operator".to_string(), Role::Operator, 3600);
        let viewer_claims = Claims::new("viewer".to_string(), Role::Viewer, 3600);

        // Admin has all roles
        assert!(admin_claims.has_role(&Role::Admin));
        assert!(admin_claims.has_role(&Role::Operator));
        assert!(admin_claims.has_role(&Role::Viewer));

        // Operator has operator and viewer
        assert!(!operator_claims.has_role(&Role::Admin));
        assert!(operator_claims.has_role(&Role::Operator));
        assert!(operator_claims.has_role(&Role::Viewer));

        // Viewer only has viewer
        assert!(!viewer_claims.has_role(&Role::Admin));
        assert!(!viewer_claims.has_role(&Role::Operator));
        assert!(viewer_claims.has_role(&Role::Viewer));
    }

    #[tokio::test]
    async fn test_token_generation_and_verification() {
        let auth_state = AuthState::new("test_secret".to_string());

        // Generate token
        let token = auth_state
            .generate_token("user123".to_string(), Role::Operator, 3600)
            .unwrap();

        // Verify token
        let claims = auth_state.verify_token(&token).unwrap();

        assert_eq!(claims.sub, "user123");
        assert_eq!(claims.role, Role::Operator);
        assert!(!claims.is_expired());
    }

    #[tokio::test]
    async fn test_api_key_operations() {
        let auth_state = AuthState::new("test_secret".to_string());

        // Add API key
        let key_hash = format!("{:x}", md5::compute("test_key_123"));
        let api_key = ApiKey {
            id: "key1".to_string(),
            key_hash,
            owner: "test_user".to_string(),
            role: Role::Operator,
            created_at: 0,
            last_used: None,
            enabled: true,
        };

        auth_state.add_api_key(api_key).await;

        // Verify API key
        let verified = auth_state.verify_api_key("test_key_123").await.unwrap();
        assert_eq!(verified.id, "key1");
        assert_eq!(verified.role, Role::Operator);
        assert!(verified.last_used.is_some());

        // List keys
        let keys = auth_state.list_api_keys().await;
        assert_eq!(keys.len(), 1);

        // Revoke key
        auth_state.revoke_api_key("key1").await.unwrap();

        // Verification should fail now
        let result = auth_state.verify_api_key("test_key_123").await;
        assert!(result.is_err());
    }
}
