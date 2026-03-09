//! JWT-based authentication
//!
//! Provides JSON Web Token generation and validation for stateless authentication.

use chrono::{Duration, Utc};
use jsonwebtoken::{decode, encode, Algorithm, DecodingKey, EncodingKey, Header, Validation};
use serde::{Deserialize, Serialize};

use crate::{error::AuthError, rbac::Role, Result};

/// JWT claims structure
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Claims {
    /// Subject (user ID or API key ID)
    pub sub: String,
    /// Issued at (Unix timestamp)
    pub iat: i64,
    /// Expiration time (Unix timestamp)
    pub exp: i64,
    /// User role
    pub role: Role,
    /// Optional custom claims
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub custom: Option<serde_json::Value>,
}

impl Claims {
    /// Create new claims with the given parameters
    pub fn new(subject: String, role: Role, valid_for: Duration) -> Self {
        let now = Utc::now();
        Self {
            sub: subject,
            iat: now.timestamp(),
            exp: (now + valid_for).timestamp(),
            role,
            custom: None,
        }
    }

    /// Check if the token is expired
    pub fn is_expired(&self) -> bool {
        let now = Utc::now().timestamp();
        self.exp < now
    }
}

/// JWT authentication provider
#[derive(Clone)]
pub struct JwtAuth {
    encoding_key: EncodingKey,
    decoding_key: DecodingKey,
    algorithm: Algorithm,
}

impl JwtAuth {
    /// Create a new JWT auth provider with a secret key
    pub fn new(secret: &str) -> Self {
        Self {
            encoding_key: EncodingKey::from_secret(secret.as_bytes()),
            decoding_key: DecodingKey::from_secret(secret.as_bytes()),
            algorithm: Algorithm::HS256,
        }
    }

    /// Generate a JWT token from claims
    pub fn generate_token(&self, claims: &Claims) -> Result<String> {
        let header = Header::new(self.algorithm);
        encode(&header, claims, &self.encoding_key)
            .map_err(|e| AuthError::Internal(anyhow::anyhow!("Failed to encode JWT: {}", e)))
    }

    /// Validate and decode a JWT token
    pub fn validate_token(&self, token: &str) -> Result<Claims> {
        let mut validation = Validation::new(self.algorithm);
        validation.validate_exp = true;

        let token_data = decode::<Claims>(token, &self.decoding_key, &validation).map_err(|e| {
            match e.kind() {
                jsonwebtoken::errors::ErrorKind::ExpiredSignature => AuthError::TokenExpired,
                _ => AuthError::InvalidToken(e.to_string()),
            }
        })?;

        Ok(token_data.claims)
    }

    /// Extract token from Authorization header ("Bearer <token>")
    pub fn extract_from_header(&self, auth_header: &str) -> Result<Claims> {
        let parts: Vec<&str> = auth_header.split_whitespace().collect();
        if parts.len() != 2 || parts[0] != "Bearer" {
            return Err(AuthError::InvalidAuthHeaderFormat);
        }

        self.validate_token(parts[1])
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_jwt_generation_and_validation() {
        let jwt_auth = JwtAuth::new("test-secret-key");
        let claims = Claims::new(
            "user-123".to_string(),
            Role::Admin,
            Duration::hours(1),
        );

        // Generate token
        let token = jwt_auth.generate_token(&claims).unwrap();
        assert!(!token.is_empty());

        // Validate token
        let decoded = jwt_auth.validate_token(&token).unwrap();
        assert_eq!(decoded.sub, "user-123");
        assert_eq!(decoded.role, Role::Admin);
        assert!(!decoded.is_expired());
    }

    #[test]
    fn test_expired_token() {
        let jwt_auth = JwtAuth::new("test-secret-key");

        // Create claims that expired 5 minutes ago (well past any clock skew leeway)
        let now = Utc::now();
        let claims = Claims {
            sub: "user-123".to_string(),
            iat: (now - Duration::minutes(10)).timestamp(),
            exp: (now - Duration::minutes(5)).timestamp(), // Expired 5 minutes ago
            role: Role::User,
            custom: None,
        };

        let token = jwt_auth.generate_token(&claims).unwrap();
        let result = jwt_auth.validate_token(&token);
        match &result {
            Err(AuthError::TokenExpired) => {
                // Expected
            }
            other => {
                panic!("Expected TokenExpired error, got: {:?}", other);
            }
        }
    }

    #[test]
    fn test_invalid_token() {
        let jwt_auth = JwtAuth::new("test-secret-key");
        let result = jwt_auth.validate_token("invalid.token.here");
        assert!(matches!(result, Err(AuthError::InvalidToken(_))));
    }

    #[test]
    fn test_extract_from_header() {
        let jwt_auth = JwtAuth::new("test-secret-key");
        let claims = Claims::new(
            "user-456".to_string(),
            Role::Writer,
            Duration::hours(2),
        );

        let token = jwt_auth.generate_token(&claims).unwrap();
        let auth_header = format!("Bearer {}", token);

        let extracted = jwt_auth.extract_from_header(&auth_header).unwrap();
        assert_eq!(extracted.sub, "user-456");
        assert_eq!(extracted.role, Role::Writer);
    }

    #[test]
    fn test_invalid_header_format() {
        let jwt_auth = JwtAuth::new("test-secret-key");

        // Missing "Bearer" prefix
        let result = jwt_auth.extract_from_header("some-token");
        assert!(matches!(result, Err(AuthError::InvalidAuthHeaderFormat)));

        // Wrong prefix
        let result = jwt_auth.extract_from_header("Basic some-token");
        assert!(matches!(result, Err(AuthError::InvalidAuthHeaderFormat)));
    }
}
