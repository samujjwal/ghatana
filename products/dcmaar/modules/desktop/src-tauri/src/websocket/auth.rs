// JWT authentication for WebSocket connections

use anyhow::{Context, Result};
use jsonwebtoken::{decode, encode, Algorithm, DecodingKey, EncodingKey, Header, Validation};
use serde::{Deserialize, Serialize};
use std::time::{SystemTime, UNIX_EPOCH};

/// JWT claims for extension authentication
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Claims {
    pub sub: String,        // Extension ID
    pub exp: u64,           // Expiration time
    pub iat: u64,           // Issued at
    pub extension_id: String,
    pub capabilities: Vec<String>,
}

/// JWT authenticator
pub struct JwtAuthenticator {
    secret: String,
    token_ttl: u64, // seconds
}

impl JwtAuthenticator {
    pub fn new(secret: String, token_ttl: u64) -> Self {
        Self { secret, token_ttl }
    }

    /// Generate a new JWT token for an extension
    pub fn generate_token(&self, extension_id: &str, capabilities: Vec<String>) -> Result<String> {
        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .context("Failed to get current time")?
            .as_secs();

        let claims = Claims {
            sub: extension_id.to_string(),
            exp: now + self.token_ttl,
            iat: now,
            extension_id: extension_id.to_string(),
            capabilities,
        };

        let token = encode(
            &Header::new(Algorithm::HS256),
            &claims,
            &EncodingKey::from_secret(self.secret.as_bytes()),
        )
        .context("Failed to encode JWT")?;

        Ok(token)
    }

    /// Validate a JWT token and extract claims
    pub fn validate_token(&self, token: &str) -> Result<Claims> {
        let mut validation = Validation::new(Algorithm::HS256);
        validation.validate_exp = true;

        let token_data = decode::<Claims>(
            token,
            &DecodingKey::from_secret(self.secret.as_bytes()),
            &validation,
        )
        .context("Failed to decode JWT")?;

        Ok(token_data.claims)
    }

    /// Check if a token has a specific capability
    pub fn has_capability(claims: &Claims, capability: &str) -> bool {
        claims.capabilities.iter().any(|c| c == capability)
    }

    /// Refresh a token (generate new token with same claims)
    pub fn refresh_token(&self, old_token: &str) -> Result<String> {
        let claims = self.validate_token(old_token)?;
        self.generate_token(&claims.extension_id, claims.capabilities)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_token_generation_and_validation() {
        let auth = JwtAuthenticator::new("test-secret".to_string(), 3600);
        let capabilities = vec!["read".to_string(), "write".to_string()];

        let token = auth.generate_token("ext-123", capabilities.clone()).unwrap();
        let claims = auth.validate_token(&token).unwrap();

        assert_eq!(claims.extension_id, "ext-123");
        assert_eq!(claims.capabilities, capabilities);
    }

    #[test]
    fn test_capability_check() {
        let claims = Claims {
            sub: "ext-123".to_string(),
            exp: 9999999999,
            iat: 1234567890,
            extension_id: "ext-123".to_string(),
            capabilities: vec!["read".to_string(), "write".to_string()],
        };

        assert!(JwtAuthenticator::has_capability(&claims, "read"));
        assert!(JwtAuthenticator::has_capability(&claims, "write"));
        assert!(!JwtAuthenticator::has_capability(&claims, "admin"));
    }

    #[test]
    fn test_invalid_token() {
        let auth = JwtAuthenticator::new("test-secret".to_string(), 3600);
        let result = auth.validate_token("invalid-token");
        assert!(result.is_err());
    }
}
