/// Feature extraction for PII detection ML models
/// 
/// This module implements feature engineering for lightweight edge ML models
/// used in Capability 1: Edge PII Redaction Model

use anyhow::Result;
use std::collections::HashMap;

/// Feature vector for ML-based PII detection
#[derive(Debug, Clone)]
pub struct FeatureVector {
    /// Wordshape features (e.g., "Aaa-999" for "ABC-123")
    pub wordshape: String,
    /// Character n-gram features
    pub char_ngrams: Vec<String>,
    /// Shannon entropy of the string
    pub entropy: f64,
    /// Token type classifications
    pub token_types: Vec<TokenType>,
    /// String length
    pub length: usize,
    /// Digit ratio (0.0 to 1.0)
    pub digit_ratio: f64,
    /// Special character ratio
    pub special_char_ratio: f64,
    /// Uppercase ratio
    pub uppercase_ratio: f64,
    /// Contains @ symbol (email indicator)
    pub has_at_symbol: bool,
    /// Contains domain patterns
    pub has_domain_pattern: bool,
    /// Starts with protocol (http/https/ftp)
    pub starts_with_protocol: bool,
}

/// Token type classifications for contextual features
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub enum TokenType {
    /// Token that looks like an ordinary word (alphabetic)
    Word,
    /// Numeric token
    Number,
    /// Token that resembles an email address
    Email,
    /// Token that represents a URL
    Url,
    /// Token that resembles a credit card number
    CreditCard,
    /// Token that matches SSN pattern
    SSN,
    /// Token that resembles a phone number
    Phone,
    /// IP address token
    IPAddress,
    /// Token that is a hash/hex string
    Hash,
    /// UUID token
    UUID,
    /// Mixed alphanumeric token
    Mixed,
    /// Unknown or unclassified token
    Unknown,
}

/// Feature extractor for PII detection
pub struct FeatureExtractor {
    /// N-gram size for character features
    pub ngram_size: usize,
    /// Maximum feature vector length
    pub max_features: usize,
}

impl Default for FeatureExtractor {
    fn default() -> Self {
        Self {
            ngram_size: 3,
            max_features: 1000,
        }
    }
}

impl FeatureExtractor {
    /// Create new feature extractor with custom parameters
    pub fn new(ngram_size: usize, max_features: usize) -> Self {
        Self { ngram_size, max_features }
    }

    /// Extract features from input string
    pub fn extract(&self, input: &str) -> Result<FeatureVector> {
        let trimmed = input.trim();
        if trimmed.is_empty() {
            return Ok(self.empty_features());
        }

        Ok(FeatureVector {
            wordshape: self.extract_wordshape(trimmed),
            char_ngrams: self.extract_char_ngrams(trimmed),
            entropy: self.calculate_entropy(trimmed),
            token_types: self.classify_tokens(trimmed),
            length: trimmed.len(),
            digit_ratio: self.calculate_digit_ratio(trimmed),
            special_char_ratio: self.calculate_special_char_ratio(trimmed),
            uppercase_ratio: self.calculate_uppercase_ratio(trimmed),
            has_at_symbol: trimmed.contains('@'),
            has_domain_pattern: self.has_domain_pattern(trimmed),
            starts_with_protocol: self.starts_with_protocol(trimmed),
        })
    }

    /// Convert feature vector to dense numeric representation for ML model
    pub fn vectorize(&self, features: &FeatureVector) -> Vec<f32> {
        let mut vector = Vec::with_capacity(50); // Fixed size for consistency

        // Basic numeric features
        vector.push(features.length as f32);
        vector.push(features.entropy as f32);
        vector.push(features.digit_ratio as f32);
        vector.push(features.special_char_ratio as f32);
        vector.push(features.uppercase_ratio as f32);
        
        // Boolean features as 0.0/1.0
        vector.push(if features.has_at_symbol { 1.0 } else { 0.0 });
        vector.push(if features.has_domain_pattern { 1.0 } else { 0.0 });
        vector.push(if features.starts_with_protocol { 1.0 } else { 0.0 });

        // Token type one-hot encoding
        let mut token_counts = HashMap::new();
        for token_type in &features.token_types {
            *token_counts.entry(token_type.clone()).or_insert(0) += 1;
        }

        let total_tokens = features.token_types.len() as f32;
        vector.push(*token_counts.get(&TokenType::Email).unwrap_or(&0) as f32 / total_tokens.max(1.0));
        vector.push(*token_counts.get(&TokenType::Url).unwrap_or(&0) as f32 / total_tokens.max(1.0));
        vector.push(*token_counts.get(&TokenType::CreditCard).unwrap_or(&0) as f32 / total_tokens.max(1.0));
        vector.push(*token_counts.get(&TokenType::SSN).unwrap_or(&0) as f32 / total_tokens.max(1.0));
        vector.push(*token_counts.get(&TokenType::Phone).unwrap_or(&0) as f32 / total_tokens.max(1.0));
        vector.push(*token_counts.get(&TokenType::IPAddress).unwrap_or(&0) as f32 / total_tokens.max(1.0));
        vector.push(*token_counts.get(&TokenType::UUID).unwrap_or(&0) as f32 / total_tokens.max(1.0));

        // Pad or truncate to fixed size
        vector.resize(50, 0.0);
        vector
    }

    fn empty_features(&self) -> FeatureVector {
        FeatureVector {
            wordshape: String::new(),
            char_ngrams: Vec::new(),
            entropy: 0.0,
            token_types: Vec::new(),
            length: 0,
            digit_ratio: 0.0,
            special_char_ratio: 0.0,
            uppercase_ratio: 0.0,
            has_at_symbol: false,
            has_domain_pattern: false,
            starts_with_protocol: false,
        }
    }

    fn extract_wordshape(&self, input: &str) -> String {
        input.chars().map(|c| {
            match c {
                'A'..='Z' => 'A',
                'a'..='z' => 'a',
                '0'..='9' => '9',
                _ => c,
            }
        }).collect()
    }

    fn extract_char_ngrams(&self, input: &str) -> Vec<String> {
        if input.len() < self.ngram_size {
            return vec![input.to_string()];
        }

        let chars: Vec<char> = input.chars().collect();
        let mut ngrams = Vec::new();

        for i in 0..=(chars.len() - self.ngram_size) {
            let ngram: String = chars[i..i + self.ngram_size].iter().collect();
            ngrams.push(ngram);
        }

        // Limit to prevent memory explosion
        ngrams.truncate(self.max_features);
        ngrams
    }

    fn calculate_entropy(&self, input: &str) -> f64 {
        let mut counts = HashMap::new();
        let total = input.len() as f64;

        for ch in input.chars() {
            *counts.entry(ch).or_insert(0) += 1;
        }

        counts.values()
            .map(|&count| {
                let p = count as f64 / total;
                -p * p.log2()
            })
            .sum()
    }

    fn calculate_digit_ratio(&self, input: &str) -> f64 {
        let digit_count = input.chars().filter(|c| c.is_ascii_digit()).count();
        digit_count as f64 / input.len().max(1) as f64
    }

    fn calculate_special_char_ratio(&self, input: &str) -> f64 {
        let special_count = input.chars()
            .filter(|c| !c.is_alphanumeric() && !c.is_whitespace())
            .count();
        special_count as f64 / input.len().max(1) as f64
    }

    fn calculate_uppercase_ratio(&self, input: &str) -> f64 {
        let upper_count = input.chars().filter(|c| c.is_uppercase()).count();
        upper_count as f64 / input.len().max(1) as f64
    }

    fn classify_tokens(&self, input: &str) -> Vec<TokenType> {
        let tokens: Vec<&str> = input.split_whitespace().collect();
        let mut types = Vec::new();

        for token in tokens {
            types.push(self.classify_token(token));
        }

        if types.is_empty() {
            types.push(self.classify_token(input));
        }

        types
    }

    fn classify_token(&self, token: &str) -> TokenType {
        // Email pattern
        if token.contains('@') && token.contains('.') {
            return TokenType::Email;
        }

        // URL pattern
        if token.starts_with("http://") || token.starts_with("https://") || token.starts_with("ftp://") {
            return TokenType::Url;
        }

        // Credit card pattern (simplified)
        if token.len() >= 13 && token.len() <= 19 && token.chars().all(|c| c.is_ascii_digit() || c == '-') {
            return TokenType::CreditCard;
        }

        // SSN pattern (XXX-XX-XXXX)
        if token.len() == 11 && token.chars().nth(3) == Some('-') && token.chars().nth(6) == Some('-') {
            return TokenType::SSN;
        }

        // Phone pattern (simplified)
        if token.len() >= 10 && token.chars().filter(|c| c.is_ascii_digit()).count() >= 10 {
            return TokenType::Phone;
        }

        // IP Address pattern
        if self.is_ip_address(token) {
            return TokenType::IPAddress;
        }

        // UUID pattern
        if token.len() == 36 && token.chars().filter(|&c| c == '-').count() == 4 {
            return TokenType::UUID;
        }

        // Hash pattern (hex string of reasonable length)
        if token.len() >= 32 && token.len() <= 128 && token.chars().all(|c| c.is_ascii_hexdigit()) {
            return TokenType::Hash;
        }

        // Pure number
        if token.chars().all(|c| c.is_ascii_digit()) {
            return TokenType::Number;
        }

        // Pure word
        if token.chars().all(|c| c.is_alphabetic()) {
            return TokenType::Word;
        }

        // Mixed alphanumeric
        if token.chars().any(|c| c.is_alphabetic()) && token.chars().any(|c| c.is_ascii_digit()) {
            return TokenType::Mixed;
        }

        TokenType::Unknown
    }

    fn is_ip_address(&self, token: &str) -> bool {
        let parts: Vec<&str> = token.split('.').collect();
        if parts.len() != 4 {
            return false;
        }

        parts.iter().all(|part| {
            part.parse::<u8>().is_ok()
        })
    }

    fn has_domain_pattern(&self, input: &str) -> bool {
        // Simple domain pattern detection
        input.contains('.') && 
        input.chars().any(|c| c.is_alphabetic()) &&
        !input.starts_with("http")
    }

    fn starts_with_protocol(&self, input: &str) -> bool {
        input.starts_with("http://") || 
        input.starts_with("https://") || 
        input.starts_with("ftp://") ||
        input.starts_with("file://")
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_feature_extraction() {
        let extractor = FeatureExtractor::default();
        
        // Test email
        let features = extractor.extract("john.doe@example.com").unwrap();
        assert!(features.has_at_symbol);
        assert!(features.has_domain_pattern);
        assert!(!features.token_types.is_empty());
        
        // Test entropy calculation
        let low_entropy = extractor.extract("aaaaaaa").unwrap();
        let high_entropy = extractor.extract("a1B2c3D4").unwrap();
        assert!(high_entropy.entropy > low_entropy.entropy);
        
        // Test vectorization
        let vector = extractor.vectorize(&features);
        assert_eq!(vector.len(), 50);
    }

    #[test]
    fn test_token_classification() {
        let extractor = FeatureExtractor::default();
        
        assert_eq!(extractor.classify_token("test@example.com"), TokenType::Email);
        assert_eq!(extractor.classify_token("https://example.com"), TokenType::Url);
        assert_eq!(extractor.classify_token("192.168.1.1"), TokenType::IPAddress);
        assert_eq!(extractor.classify_token("12345"), TokenType::Number);
        assert_eq!(extractor.classify_token("hello"), TokenType::Word);
    }

    #[test]
    fn test_wordshape() {
        let extractor = FeatureExtractor::default();
        assert_eq!(extractor.extract_wordshape("John123"), "Aaaa999");
        assert_eq!(extractor.extract_wordshape("TEST-456"), "AAAA-999");
    }
}