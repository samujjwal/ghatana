// Minimal fallback-only inference implementation.

use anyhow::Result;
use tracing::{debug, warn, instrument};
use std::path::Path;
use super::features::FeatureExtractor;

/// Configuration for the inference engine.
///
/// Controls model path, thresholds and runtime tuning used by the
/// fallback inference engine for PII detection.
#[derive(Debug, Clone)]
pub struct InferenceConfig {
    /// Filesystem path to the inference model used for PII detection.
    pub model_path: String,
    /// Confidence threshold above which a detection is considered PII.
    pub confidence_threshold: f64,
    /// Inference timeout in milliseconds for single requests.
    pub timeout_ms: u64,
    /// Whether to attempt GPU-accelerated inference.
    pub use_gpu: bool,
    /// Batch size used for batched inference calls.
    pub batch_size: usize,
}

impl Default for InferenceConfig {
    fn default() -> Self {
        Self {
            model_path: "models/pii_detector.onnx".to_string(),
            confidence_threshold: 0.75,
            timeout_ms: 100,
            use_gpu: false,
            batch_size: 1,
        }
    }
}

/// Result produced by a single inference call.
#[derive(Debug, Clone)]
pub struct InferenceResult {
    /// Confidence score produced by the model (0.0..1.0).
    pub confidence: f64,
    /// Detected PII type.
    pub pii_type: PiiType,
    /// Whether the result is considered PII given the configured threshold.
    pub is_pii: bool,
    /// Time taken to perform inference in milliseconds.
    pub inference_time_ms: u64,
}

/// Enumerates possible PII (personally-identifying) types detected
/// by the inference engine.
#[derive(Debug, Clone, PartialEq)]
pub enum PiiType {
    /// No PII detected.
    None,
    /// Email address detected.
    Email,
    /// Credit card number detected.
    CreditCard,
    /// Social Security Number (or similar national ID) detected.
    SSN,
    /// Phone number detected.
    Phone,
    /// Unknown or unclassified PII type.
    Unknown,
}

// Allow simple integer -> PiiType conversions used in tests and other places.
impl From<u8> for PiiType {
    fn from(v: u8) -> Self {
        match v {
            1 => PiiType::Email,
            2 => PiiType::CreditCard,
            3 => PiiType::SSN,
            4 => PiiType::Phone,
            0 | _ => PiiType::Unknown,
        }
    }
}

/// Lightweight inference engine wrapper.
///
/// Provides a simple fallback inference implementation and a small
/// API used by the redaction pipeline.
pub struct InferenceEngine {
    config: InferenceConfig,
    extractor: FeatureExtractor,
    #[cfg(feature = "onnx")]
    _session: Option<()>,
}

impl InferenceEngine {
    /// Create a new inference engine with the provided configuration.
    pub fn new(config: InferenceConfig) -> Result<Self> {
        Ok(Self {
            extractor: FeatureExtractor::default(),
            config,
            #[cfg(feature = "onnx")]
            _session: None,
        })
    }

    #[instrument(skip(self))]
    /// Initialize the inference engine (load model or prepare heuristics).
    pub async fn initialize(&mut self) -> Result<()> {
        debug!("initialize inference engine");
        if !Path::new(&self.config.model_path).exists() {
            warn!("model not found, using fallback heuristics");
        }
        Ok(())
    }

    #[instrument(skip(self))]
    /// Run inference on a single input string, returning the detection result.
    pub async fn infer(&self, input: &str) -> Result<InferenceResult> {
        let start = std::time::Instant::now();
        let features = self.extractor.extract(input)?;
        let _vec = self.extractor.vectorize(&features);

        let (pii_type, confidence) = if features.has_at_symbol && features.has_domain_pattern {
            (PiiType::Email, 0.9)
        } else if features.length >= 13 && features.digit_ratio > 0.8 {
            (PiiType::CreditCard, 0.8)
        } else if features.length >= 10 && features.digit_ratio > 0.7 {
            (PiiType::Phone, 0.7)
        } else {
            (PiiType::None, 0.0)
        };

        let elapsed = start.elapsed().as_millis() as u64;
        Ok(InferenceResult {
            confidence,
            pii_type,
            is_pii: confidence > self.config.confidence_threshold,
            inference_time_ms: elapsed,
        })
    }

    /// Run inference for a batch of inputs.
    pub async fn infer_batch(&self, inputs: &[String]) -> Result<Vec<InferenceResult>> {
        let mut out = Vec::with_capacity(inputs.len());
        for s in inputs {
            out.push(self.infer(s).await?);
        }
        Ok(out)
    }

    /// Return true if a native/ONNX model was successfully loaded.
    pub fn is_model_loaded(&self) -> bool {
        #[cfg(feature = "onnx")]
        {
            self._session.is_some()
        }
        #[cfg(not(feature = "onnx"))]
        {
            false
        }
    }

    /// Retrieve runtime statistics about the inference engine/config.
    pub fn get_stats(&self) -> InferenceStats {
        InferenceStats {
            model_loaded: self.is_model_loaded(),
            model_path: self.config.model_path.clone(),
            confidence_threshold: self.config.confidence_threshold,
        }
    }
}

/// Lightweight statistics reported for an inference engine instance.
#[derive(Debug, Clone)]
pub struct InferenceStats {
    /// Whether a model was successfully loaded for inference.
    pub model_loaded: bool,
    /// Path to the model currently configured.
    pub model_path: String,
    /// Configured confidence threshold for PII classification.
    pub confidence_threshold: f64,
}

/// Create a small in-memory mock model payload used by tests.
pub fn create_mock_model_bytes() -> Vec<u8> {
    Vec::new()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn smoke() {
        let mut eng = InferenceEngine::new(InferenceConfig::default()).unwrap();
        eng.initialize().await.unwrap();
        let r = eng.infer("john@example.com").await.unwrap();
        assert!(r.is_pii);
    }

    #[tokio::test]
    async fn test_inference_engine_initialization() {
        let config = InferenceConfig::default();
        let mut engine = InferenceEngine::new(config).unwrap();
        let result = engine.initialize().await;
        assert!(result.is_ok());
        assert!(!engine.is_model_loaded());
    }

    #[tokio::test]
    async fn test_fallback_inference() {
        let config = InferenceConfig::default();
        let mut engine = InferenceEngine::new(config).unwrap();
        engine.initialize().await.unwrap();
        let result = engine.infer("john.doe@example.com").await.unwrap();
        assert_eq!(result.pii_type, PiiType::Email);
        assert!(result.confidence > 0.8);
        assert!(result.is_pii);
    }

    #[tokio::test]
    async fn test_batch_inference() {
        let config = InferenceConfig::default();
        let mut engine = InferenceEngine::new(config).unwrap();
        engine.initialize().await.unwrap();
        let inputs = vec![
            "john@example.com".to_string(),
            "hello world".to_string(),
            "4111-1111-1111-1111".to_string(),
        ];
        let results = engine.infer_batch(&inputs).await.unwrap();
        assert_eq!(results.len(), 3);
        assert_eq!(results[0].pii_type, PiiType::Email);
        assert_eq!(results[1].pii_type, PiiType::None);
        assert_eq!(results[2].pii_type, PiiType::CreditCard);
    }

    #[test]
    fn test_pii_type_conversion() {
        assert_eq!(PiiType::from(0), PiiType::Unknown);
        assert_eq!(PiiType::from(1), PiiType::Email);
    }
}