//! PII redactor enricher plugin for the DCMaar agent.
//!
//! This enricher redacts sensitive data based on configurable rules and optional
//! automatic detection of common PII patterns.

use agent_plugin::sdk::{Enricher, EnricherConfig, EnricherContext, EnricherExt, SdkError, SdkResult};
use async_trait::async_trait;
use lazy_static::lazy_static;
use regex::Regex;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use tracing::debug;

/// Enricher configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PiiRedactorConfig {
    /// Base enricher configuration
    #[serde(flatten)]
    pub base: EnricherConfig,
    /// Whether to install the default redaction rules
    #[serde(default = "default_true")]
    pub use_default_rules: bool,
    /// Custom redaction rules supplied by configuration
    #[serde(default)]
    pub rules: Vec<RedactionRuleConfig>,
    /// Perform automatic PII detection on string fields
    #[serde(default = "default_true")]
    pub auto_detect: bool,
}

fn default_true() -> bool {
    true
}

/// Redaction rule supplied via configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RedactionRuleConfig {
    /// JSON path (dot notation, supports `*` wildcard)
    pub field_path: String,
    /// Strategy to apply
    pub strategy: RedactionStrategy,
    /// Replacement string for `Fixed` strategy
    #[serde(default)]
    pub replacement: Option<String>,
    /// Number of characters to preserve for `Partial`
    #[serde(default)]
    pub preserve_chars: Option<usize>,
    /// Position to preserve (`start`, `end`, `middle`)
    #[serde(default)]
    pub preserve_position: Option<String>,
    /// Preserve formatting (for `Fixed` strategy)
    #[serde(default)]
    pub preserve_format: bool,
}

/// Strategies supported by the enricher
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum RedactionStrategy {
    Fixed,
    Hash,
    Partial,
    Token,
}

/// Compiled rule used at runtime
#[derive(Debug, Clone)]
struct CompiledRule {
    path: Vec<String>,
    strategy: RedactionStrategy,
    replacement: Option<String>,
    preserve_chars: Option<usize>,
    preserve_position: Option<String>,
    preserve_format: bool,
}

/// Enricher implementation
pub struct PiiRedactorEnricher {
    config: PiiRedactorConfig,
    rules: Vec<CompiledRule>,
    logger: Option<slog::Logger>,
}

impl PiiRedactorEnricher {
    fn compile_rules(config: &PiiRedactorConfig) -> Vec<CompiledRule> {
        let mut compiled = Vec::new();
        if config.use_default_rules {
            compiled.extend(default_rules());
        }
        for rule in &config.rules {
            compiled.push(CompiledRule {
                path: rule
                    .field_path
                    .split('.')
                    .map(|s| s.trim().to_string())
                    .filter(|s| !s.is_empty())
                    .collect(),
                strategy: rule.strategy.clone(),
                replacement: rule.replacement.clone(),
                preserve_chars: rule.preserve_chars,
                preserve_position: rule.preserve_position.clone(),
                preserve_format: rule.preserve_format,
            });
        }
        compiled
    }

    fn apply_rules(&self, value: &mut Value) {
        for rule in &self.rules {
            apply_rule(value, rule);
        }
    }

    fn apply_auto_detect(&self, value: &mut Value) {
        if !self.config.auto_detect {
            return;
        }
        auto_redact_value(value);
    }
}

#[async_trait]
impl Enricher for PiiRedactorEnricher {
    type Config = PiiRedactorConfig;
    type Input = Value;
    type Output = Value;

    fn new(config: Self::Config) -> SdkResult<Self> {
        Ok(Self {
            rules: Self::compile_rules(&config),
            config,
            logger: None,
        })
    }

    async fn enrich(&self, input: Self::Input) -> SdkResult<Self::Output> {
        let mut value = input;
        if !value.is_object() {
            return Err(SdkError::Execution("PII redactor expects JSON object input".into()));
        }
        self.apply_rules(&mut value);
        self.apply_auto_detect(&mut value);
        debug!("redaction_applied" = true, "rules" = self.rules.len());
        Ok(value)
    }
}

#[async_trait]
impl EnricherExt for PiiRedactorEnricher {
    fn name(&self) -> &'static str {
        "pii_redactor"
    }

    fn version(&self) -> &'static str {
        env!("CARGO_PKG_VERSION")
    }

    fn description(&self) -> &'static str {
        "Redacts sensitive fields from events using configurable rules"
    }

    fn input_schema(&self) -> Value {
        json!({"type": "object"})
    }

    fn output_schema(&self) -> Value {
        json!({"type": "object"})
    }

    fn validate_config(&self, config: &Self::Config) -> SdkResult<()> {
        if config.rules.iter().any(|r| r.field_path.trim().is_empty()) {
            return Err(SdkError::Config("field_path must not be empty".into()));
        }
        Ok(())
    }

    async fn init(&mut self, ctx: EnricherContext) -> SdkResult<()> {
        self.logger = Some(ctx.logger);
        Ok(())
    }
}

fn apply_rule(value: &mut Value, rule: &CompiledRule) {
    apply_rule_recursive(value, rule, 0);
}

fn apply_rule_recursive(value: &mut Value, rule: &CompiledRule, depth: usize) {
    if depth >= rule.path.len() {
        *value = redact_value(value, rule);
        return;
    }

    let key = &rule.path[depth];
    match value {
        Value::Object(map) => {
            if key == "*" {
                for v in map.values_mut() {
                    apply_rule_recursive(v, rule, depth + 1);
                }
            } else if let Some(v) = map.get_mut(key) {
                apply_rule_recursive(v, rule, depth + 1);
            }
        }
        Value::Array(arr) => {
            if key == "*" {
                for v in arr.iter_mut() {
                    apply_rule_recursive(v, rule, depth + 1);
                }
            } else if let Ok(index) = key.parse::<usize>() {
                if let Some(v) = arr.get_mut(index) {
                    apply_rule_recursive(v, rule, depth + 1);
                }
            }
        }
        _ => {}
    }
}

fn redact_value(original: &Value, rule: &CompiledRule) -> Value {
    if original.is_null() {
        return original.clone();
    }

    let as_string = match original {
        Value::String(s) => s.clone(),
        _ => original.to_string(),
    };

    let redacted = match rule.strategy {
        RedactionStrategy::Fixed => {
            let replacement = rule
                .replacement
                .clone()
                .unwrap_or_else(|| "***".to_string());
            if rule.preserve_format {
                preserve_format(&as_string, &replacement)
            } else {
                replacement
            }
        }
        RedactionStrategy::Hash => hash_value(&as_string),
        RedactionStrategy::Partial => {
            let preserve = rule.preserve_chars.unwrap_or(4);
            match rule
                .preserve_position
                .as_deref()
                .unwrap_or("end")
            {
                "start" => {
                    if as_string.len() <= preserve {
                        as_string
                    } else {
                        let visible = &as_string[..preserve];
                        format!("{}{}", visible, "*".repeat(as_string.len() - preserve))
                    }
                }
                "middle" => {
                    if as_string.len() <= preserve * 2 {
                        as_string
                    } else {
                        let start = &as_string[..preserve];
                        let end = &as_string[as_string.len() - preserve..];
                        format!("{}{}{}", start, "*".repeat(as_string.len() - preserve * 2), end)
                    }
                }
                _ => {
                    if as_string.len() <= preserve {
                        as_string
                    } else {
                        let visible = &as_string[as_string.len() - preserve..];
                        format!("{}{}", "*".repeat(as_string.len() - preserve), visible)
                    }
                }
            }
        }
        RedactionStrategy::Token => format!("TOKEN_{}", &hash_value(&as_string)[..8]),
    };

    match original {
        Value::Number(_) => Value::String(redacted),
        Value::String(_) => Value::String(redacted),
        _ => Value::String(redacted),
    }
}

fn preserve_format(original: &str, replacement: &str) -> String {
    let mut result = String::with_capacity(original.len());
    let mut replacements = replacement.chars().cycle();
    for ch in original.chars() {
        if ch.is_alphanumeric() {
            if let Some(r) = replacements.next() {
                result.push(if ch.is_uppercase() {
                    r.to_uppercase().next().unwrap_or(r)
                } else {
                    r
                });
            } else {
                result.push('*');
            }
        } else {
            result.push(ch);
        }
    }
    result
}

fn hash_value(value: &str) -> String {
    use sha2::Digest;
    let mut hasher = sha2::Sha256::new();
    hasher.update(value.as_bytes());
    format!("{:x}", hasher.finalize())
}

fn auto_redact_value(value: &mut Value) {
    match value {
        Value::String(text) => {
            let redacted = AUTO_REDACTION.replace_all(text, "[PII REDACTED]");
            if redacted != *text {
                *text = redacted.into_owned();
            }
        }
        Value::Object(map) => {
            for val in map.values_mut() {
                auto_redact_value(val);
            }
        }
        Value::Array(arr) => {
            for val in arr.iter_mut() {
                auto_redact_value(val);
            }
        }
        _ => {}
    }
}

lazy_static! {
    static ref AUTO_REDACTION: Regex = Regex::new(
        r"(?i)(?:[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,})|(?:\b(?:\d[ -]*?){13,16}\b)|(?:\b\d{3}[- ]?\d{2}[- ]?\d{4}\b)|(?:\b(\+\d{1,2}\s)?\(?\d{3}\)?[\s.-]?\d{3}[\s.-]?\d{4}\b)"
    )
    .expect("valid auto redaction regex");
}

fn default_rules() -> Vec<CompiledRule> {
    vec![
        CompiledRule {
            path: vec!["user".into(), "email".into()],
            strategy: RedactionStrategy::Partial,
            replacement: None,
            preserve_chars: Some(3),
            preserve_position: Some("start".into()),
            preserve_format: false,
        },
        CompiledRule {
            path: vec!["user".into(), "password".into()],
            strategy: RedactionStrategy::Fixed,
            replacement: Some("********".into()),
            preserve_chars: None,
            preserve_position: None,
            preserve_format: false,
        },
        CompiledRule {
            path: vec!["payment".into(), "credit_card".into()],
            strategy: RedactionStrategy::Partial,
            replacement: None,
            preserve_chars: Some(4),
            preserve_position: Some("end".into()),
            preserve_format: true,
        },
    ]
}

#[cfg(test)]
mod tests {
    use super::*;

    fn enricher() -> PiiRedactorEnricher {
        PiiRedactorEnricher::new(PiiRedactorConfig {
            base: EnricherConfig {
                id: "pii-redactor".into(),
                input_types: vec!["event".into()],
                enabled: true,
                options: json!({}),
            },
            use_default_rules: true,
            rules: vec![RedactionRuleConfig {
                field_path: "session.user.ssn".into(),
                strategy: RedactionStrategy::Token,
                replacement: None,
                preserve_chars: None,
                preserve_position: None,
                preserve_format: false,
            }],
            auto_detect: true,
        })
        .unwrap()
    }

    #[tokio::test]
    async fn test_redacts_configured_fields() {
        let enricher = enricher();
        let input = json!({
            "user": {
                "email": "user@example.com",
                "password": "secret"
            },
            "payment": {"credit_card": "4111111111111111"},
            "session": {"user": {"ssn": "123-45-6789"}}
        });
        let output = enricher.enrich(input).await.unwrap();
        assert!(output["user"]["email"].as_str().unwrap().starts_with("use"));
        assert_eq!(output["user"]["password"], "********");
        assert!(output["payment"]["credit_card"].as_str().unwrap().ends_with("1111"));
        assert!(output["session"]["user"]["ssn"].as_str().unwrap().starts_with("TOKEN_"));
    }

    #[tokio::test]
    async fn test_auto_detection() {
        let enricher = enricher();
        let input = json!({
            "message": "Email john@example.com, phone 555-123-4567"
        });
        let output = enricher.enrich(input).await.unwrap();
        assert!(output["message"].as_str().unwrap().contains("[PII REDACTED]"));
    }
}
