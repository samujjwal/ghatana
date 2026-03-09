use std::collections::HashMap;
use std::sync::Arc;
use tokio::sync::RwLock;
use serde::Deserialize;
use anyhow::{Result, Context};

/// Novelty detector using Count-Min Sketch sketches for memory-efficient
/// tracking of observed patterns.
///
/// Provides methods to calculate novelty scores, inspect memory stats,
/// and reset or reconfigure the detector.
pub struct NoveltyDetector {
    /// Internal novelty detector state and configuration.
    /// Sketch-based novelty detector state and config.
    /// Internal sketches used for approximate counting.
    sketches: Vec<CountMinSketch>,
    /// Hash function seeds used by the sketches.
    _hash_functions: Vec<u64>,
    /// Multiplicative decay factor applied periodically.
    decay_factor: f64,
    /// Threshold above which an event is considered novel.
    novelty_threshold: f64,
    /// Sketch table width (columns).
    sketch_width: usize,
    /// Sketch table depth (rows/hash functions).
    sketch_depth: usize,
    /// Time window in milliseconds for decay operations.
    time_window_ms: u64,
    /// Instant of the last decay operation.
    last_decay: std::time::Instant,
}

/// NoveltyDetector provides lightweight, sketch-based novelty scoring
/// for event streams. It exposes methods to calculate novelty scores,
/// manage internal sketches, and inspect memory statistics.

/// Count-Min Sketch implementation for approximate counting.
///
/// Compact probabilistic data structure used to track approximate
/// frequencies with bounded memory.
#[derive(Debug, Clone)]
pub struct CountMinSketch {
    /// Internal Count-Min Sketch table and sizing metadata.
    /// Internal table storing approximate counts and sizing.
    table: Vec<Vec<u32>>,
    width: usize,
    _depth: usize,
    hash_seeds: Vec<u64>,
}

/// Count-Min Sketch approximate frequency counter used by the novelty
/// detector. Supports incrementing, estimation and time-based decay.

impl CountMinSketch {
    /// Create a new Count-Min Sketch with the given width and depth.
    pub fn new(width: usize, depth: usize) -> Self {
        let hash_seeds: Vec<u64> = (0..depth).map(|i| i as u64 * 2654435761).collect();
        let table = vec![vec![0u32; width]; depth];
        
        Self {
            table,
            width,
            _depth: depth,
            hash_seeds,
        }
    }

    /// Increment the approximate count for `item` in the sketch.
    pub fn increment(&mut self, item: &str) {
        for (i, &seed) in self.hash_seeds.iter().enumerate() {
            let hash = self.hash_with_seed(item, seed);
            let index = (hash as usize) % self.width;
            self.table[i][index] = self.table[i][index].saturating_add(1);
        }
    }

    /// Estimate the approximate count for `item` using the sketch.
    pub fn estimate(&self, item: &str) -> u32 {
        let mut min_count = u32::MAX;
        for (i, &seed) in self.hash_seeds.iter().enumerate() {
            let hash = self.hash_with_seed(item, seed);
            let index = (hash as usize) % self.width;
            min_count = min_count.min(self.table[i][index]);
        }
        min_count
    }

    /// Apply a multiplicative decay factor to the sketch counts.
    pub fn decay(&mut self, factor: f64) {
        for row in &mut self.table {
            for cell in row {
                *cell = (*cell as f64 * factor) as u32;
            }
        }
    }

    fn hash_with_seed(&self, item: &str, seed: u64) -> u64 {
        use std::collections::hash_map::DefaultHasher;
        use std::hash::{Hash, Hasher};
        
        let mut hasher = DefaultHasher::new();
        seed.hash(&mut hasher);
        item.hash(&mut hasher);
        hasher.finish()
    }
}

/// Feature extractor for event novelty calculation.
#[derive(Debug, Clone)]
pub struct EventFeatures {
    /// Normalized, extracted features for an event used in detection.
    /// Extracted structural features for an event used by detectors.
    /// Type of the event (e.g. "auth", "error").
    pub event_type: String,
    /// Path or component that produced the event.
    pub source_path: String,
    /// Log level (e.g. "INFO", "ERROR").
    pub log_level: String,
    /// Normalized message pattern with dynamic fields removed.
    pub message_pattern: String,
    /// Structured fields as key -> normalized string value.
    pub structured_fields: HashMap<String, String>,
    /// Time bucket string used for temporal grouping.
    pub timestamp_bucket: String, // Time-based bucketing
    /// Rate bucket string used for rate-based grouping.
    pub rate_bucket: String,      // Rate-based bucketing
}

/// Extracted event features used by novelty detection. This struct
/// captures a normalized view of an event suitable for pattern hashing
/// and sketch updates.

impl EventFeatures {
    /// Extract a set of string patterns representing structural features
    /// of the event used for novelty detection.
    pub fn extract_patterns(&self) -> Vec<String> {
        let mut patterns = Vec::new();
        
        // Basic event signature
        patterns.push(format!("{}:{}:{}", self.event_type, self.source_path, self.log_level));
        
        // Message pattern
        patterns.push(format!("msg:{}", self.message_pattern));
        
        // Structured field patterns
        for (key, value) in &self.structured_fields {
            patterns.push(format!("field:{}:{}", key, value));
            
            // Key-only pattern for schema novelty
            patterns.push(format!("key:{}", key));
        }
        
        // Temporal patterns
        patterns.push(format!("time:{}", self.timestamp_bucket));
        patterns.push(format!("rate:{}", self.rate_bucket));
        
        // Combined patterns for correlation detection
        patterns.push(format!("{}:msg:{}", self.event_type, self.message_pattern));
        patterns.push(format!("{}:time:{}", self.source_path, self.timestamp_bucket));
        
        patterns
    }

    /// Build a compact composite key representing the main identifying
    /// features of this event.
    pub fn get_composite_key(&self) -> String {
        format!("{}:{}:{}:{}", 
            self.event_type, 
            self.source_path, 
            self.log_level, 
            self.message_pattern
        )
    }
}

/// Event information used by novelty detectors.
#[derive(Debug, Clone)]
pub struct EventInfo {
    /// Public event metadata and payload passed to detectors.
    /// Public event data passed into novelty detectors.
    /// Unique event identifier
    pub event_id: String,
    /// Identifier of the event source (used to separate per-source sketches)
    pub source_id: String,
    /// Event type name
    pub event_type: String,
    /// Original event message text
    pub message: String,
    /// Structured JSON data associated with the event
    pub structured_data: HashMap<String, serde_json::Value>,
    /// Event timestamp
    pub timestamp: std::time::SystemTime,
    /// Log level string
    pub log_level: String,
    /// Path or component that produced the event
    pub source_path: String,
}

/// Public event information passed into novelty detectors. Contains
/// message text, structured data and metadata used to extract features.

impl NoveltyDetector {
    /// Create a new novelty detector configured with sketch dimensions,
    /// decay behavior and novelty threshold.
    pub fn new(
        sketch_width: usize,
        sketch_depth: usize,
        decay_factor: f64,
        novelty_threshold: f64,
        time_window_ms: u64,
    ) -> Self {
        let sketches = vec![CountMinSketch::new(sketch_width, sketch_depth)];
        let hash_functions = vec![2654435761u64]; // Simple hash function seed
        
        Self {
            sketches,
            _hash_functions: hash_functions,
            decay_factor,
            novelty_threshold,
            sketch_width,
            sketch_depth,
            time_window_ms,
            last_decay: std::time::Instant::now(),
        }
    }

    /// Calculate a novelty score for an event (0.0 = seen, 1.0 = novel).
    ///
    /// The returned value is in the range 0.0..1.0 and larger values
    /// indicate more novel/unseen patterns.
    pub fn calculate_novelty(&mut self, event: &EventInfo) -> Result<f64> {
        self.maybe_decay()?;
        
        let features = self.extract_features(event)?;
        let patterns = features.extract_patterns();
        
        let mut novelty_scores = Vec::new();
        
        for pattern in &patterns {
            let current_count = self.sketches[0].estimate(pattern);
            
            // Increment count for this pattern
            self.sketches[0].increment(pattern);
            
            // Calculate novelty score based on frequency
            // Novel patterns have low counts, familiar patterns have high counts
            let novelty = {
                // Use ln(count + 1) so that 0 -> 0, 1 -> ln(2), etc. Then invert
                let freq_score = ((current_count as f64) + 1.0).ln() / 10.0;
                (1.0 - freq_score.min(1.0)).max(0.0)
            };
            
            novelty_scores.push(novelty);
        }
        
        // Combine novelty scores - use max for any novel pattern detected
        let max_novelty = novelty_scores.iter()
            .copied()
            .fold(0.0f64, f64::max);
        
        // Weighted average with emphasis on structural novelty
        let avg_novelty = novelty_scores.iter().sum::<f64>() / novelty_scores.len() as f64;
        let combined_novelty = 0.7 * max_novelty + 0.3 * avg_novelty;
        
        Ok(combined_novelty)
    }

    /// Extract features from event for novelty detection
    /// Extract event features used by the novelty detector.
    /// Extract EventFeatures from raw EventInfo. This is a helper used
    /// by calculate_novelty to normalize event content.
    fn extract_features(&self, event: &EventInfo) -> Result<EventFeatures> {
        // Extract message pattern by replacing dynamic content with placeholders
        let message_pattern = self.extract_message_pattern(&event.message);
        
        // Create time buckets for temporal pattern detection
        let timestamp_bucket = self.create_time_bucket(event.timestamp)?;
        
        // Create rate bucket based on current event frequency
        let rate_bucket = "normal".to_string(); // Simplified for now
        
        // Convert structured data to string map
        let mut structured_fields = HashMap::new();
        for (key, value) in &event.structured_data {
            structured_fields.insert(
                key.clone(),
                self.normalize_value(value)
            );
        }
        
        Ok(EventFeatures {
            event_type: event.event_type.clone(),
            source_path: event.source_path.clone(),
            log_level: event.log_level.clone(),
            message_pattern,
            structured_fields,
            timestamp_bucket,
            rate_bucket,
        })
    }

    /// Extract generalized message pattern by replacing dynamic values
    /// Normalize a message string into a pattern with dynamic tokens
    /// replaced by placeholders (IPs, UUIDs, numbers, emails, etc.).
    fn extract_message_pattern(&self, message: &str) -> String {
        let mut pattern = message.to_string();
        
        // Replace common dynamic patterns with placeholders
        // Order matters: match IPs and UUIDs before generic numbers to avoid numeric replacement inside IPs
        let replacements = vec![
            (regex::Regex::new(r"\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b").unwrap(), "<UUID>"),
            (regex::Regex::new(r"\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b").unwrap(), "<IP>"),
            (regex::Regex::new(r"\b[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\b").unwrap(), "<TIMESTAMP>"),
            (regex::Regex::new(r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b").unwrap(), "<EMAIL>"),
            (regex::Regex::new(r"\b(https?://\S+)\b").unwrap(), "<URL>"),
            (regex::Regex::new(r"\b[A-Fa-f0-9]{40,}\b").unwrap(), "<HASH>"),
            (regex::Regex::new(r"\d+").unwrap(), "<NUM>"),
        ];
        
        for (regex, replacement) in replacements {
            pattern = regex.replace_all(&pattern, replacement).to_string();
        }
        
        // Limit pattern length to prevent memory issues
        if pattern.len() > 200 {
            pattern.truncate(200);
        }
        
        pattern
    }

    /// Create time bucket for temporal pattern analysis
    /// Create a compact time bucket identifier used for temporal
    /// grouping of patterns.
    fn create_time_bucket(&self, timestamp: std::time::SystemTime) -> Result<String> {
        let duration = timestamp.duration_since(std::time::UNIX_EPOCH)
            .context("Invalid timestamp")?;
        
        let secs = duration.as_secs();
        
        // Create 5-minute buckets for temporal pattern detection
        let bucket = secs / 300; // 300 seconds = 5 minutes
        
        Ok(format!("t{}", bucket))
    }

    /// Normalize JSON value to string for pattern detection
    /// Convert a serde_json::Value into a normalized string used for
    /// pattern matching and sketch keys.
    fn normalize_value(&self, value: &serde_json::Value) -> String {
        match value {
            serde_json::Value::String(s) => {
                // Apply same pattern extraction as message
                self.extract_message_pattern(s)
            }
            serde_json::Value::Number(n) => {
                if n.is_i64() || n.is_u64() {
                    "<INT>".to_string()
                } else {
                    "<FLOAT>".to_string()
                }
            }
            serde_json::Value::Bool(_) => "<BOOL>".to_string(),
            serde_json::Value::Array(_) => "<ARRAY>".to_string(),
            serde_json::Value::Object(_) => "<OBJECT>".to_string(),
            serde_json::Value::Null => "<NULL>".to_string(),
        }
    }

    /// Apply time-based decay to reduce influence of old patterns
    /// Apply time-based decay to sketches when the configured time window has elapsed.
    /// Apply time-windowed decay to sketches when the configured window
    /// has elapsed.
    fn maybe_decay(&mut self) -> Result<()> {
        let now = std::time::Instant::now();
        let elapsed = now.duration_since(self.last_decay);
        
        if elapsed.as_millis() > self.time_window_ms as u128 {
            for sketch in &mut self.sketches {
                sketch.decay(self.decay_factor);
            }
            self.last_decay = now;
        }
        
        Ok(())
    }

    /// Get current memory and operational statistics for this detector.
    pub fn get_memory_stats(&self) -> NoveltyDetectorStats {
        let total_entries = self.sketches.iter()
            .map(|s| s.table.iter().map(|row| row.iter().sum::<u32>() as u64).sum::<u64>())
            .sum();
        
        let memory_bytes = self.sketches.len() * self.sketch_width * self.sketch_depth * 4; // 4 bytes per u32
        
        NoveltyDetectorStats {
            total_patterns_tracked: total_entries,
            memory_usage_bytes: memory_bytes,
            sketch_count: self.sketches.len(),
            last_decay_ms: self.last_decay.elapsed().as_millis() as u64,
        }
    }

    /// Reset internal sketch counts and timers.
    pub fn reset(&mut self) {
        for sketch in &mut self.sketches {
            sketch.table = vec![vec![0u32; self.sketch_width]; self.sketch_depth];
        }
        self.last_decay = std::time::Instant::now();
    }

    /// Reconfigure decay, novelty threshold and time window parameters.
    pub fn configure(&mut self, decay_factor: f64, novelty_threshold: f64, time_window_ms: u64) {
        self.decay_factor = decay_factor;
        self.novelty_threshold = novelty_threshold;
        self.time_window_ms = time_window_ms;
    }
}

/// Memory and operational statistics for a novelty detector instance.
#[derive(Debug, Clone)]
pub struct NoveltyDetectorStats {
    /// Number of distinct patterns tracked (approximate).
    pub total_patterns_tracked: u64,
    /// Estimated memory usage in bytes for sketches.
    pub memory_usage_bytes: usize,
    /// Number of sketches used (depth of detectors).
    pub sketch_count: usize,
    /// Milliseconds since the last decay operation.
    pub last_decay_ms: u64,
}

/// Multi-source novelty manager for handling different event sources.
/// Manages per-source detectors and a global detector.
pub struct MultiSourceNoveltyManager {
    detectors: Arc<RwLock<HashMap<String, NoveltyDetector>>>,
    global_detector: Arc<RwLock<NoveltyDetector>>,
    config: NoveltyConfig,
}

/// MultiSourceNoveltyManager provides a safe asynchronous API for
/// querying novelty scores across many sources.

#[derive(Debug, Clone, Deserialize)]
pub struct NoveltyConfig {
    /// Sketch table width (number of columns) for Count-Min Sketch.
    pub sketch_width: usize,
    /// Sketch table depth (number of hash functions / rows).
    pub sketch_depth: usize,
    /// Multiplicative decay factor applied periodically to sketch counts.
    pub decay_factor: f64,
    /// Threshold above which an event is considered novel (0.0..1.0).
    pub novelty_threshold: f64,
    /// Time window in milliseconds used for decay scheduling.
    pub time_window_ms: u64,
    /// Maximum number of per-source detectors to keep.
    pub max_sources: usize,
    /// Enable global cross-source novelty detection when true.
    pub enable_global_detection: bool,
}

/// Configuration parameters for novelty detection and sketch sizing.

impl Default for NoveltyConfig {
    fn default() -> Self {
        Self {
            sketch_width: 1024,
            sketch_depth: 4,
            decay_factor: 0.95,
            novelty_threshold: 0.7,
            time_window_ms: 300_000, // 5 minutes
            max_sources: 1000,
            enable_global_detection: true,
        }
    }
}

impl MultiSourceNoveltyManager {
    /// Create a new multi-source novelty manager with the given config.
    pub fn new(config: NoveltyConfig) -> Self {
        let global_detector = Arc::new(RwLock::new(NoveltyDetector::new(
            config.sketch_width,
            config.sketch_depth,
            config.decay_factor,
            config.novelty_threshold,
            config.time_window_ms,
        )));

        Self {
            detectors: Arc::new(RwLock::new(HashMap::new())),
            global_detector,
            config,
        }
    }

    /// Calculate novelty score for an event from specific source
    pub async fn calculate_novelty(&self, event: &EventInfo) -> Result<f64> {
        let source_novelty = self.calculate_source_novelty(event).await?;
        
        let global_novelty = if self.config.enable_global_detection {
            self.calculate_global_novelty(event).await?
        } else {
            0.0
        };

        // Combine source-specific and global novelty
        // Higher weight on source-specific to catch source-specific patterns
        let combined_novelty = 0.7 * source_novelty + 0.3 * global_novelty;
        
        Ok(combined_novelty)
    }

    async fn calculate_source_novelty(&self, event: &EventInfo) -> Result<f64> {
        let mut detectors = self.detectors.write().await;
        
        // Check if we've exceeded max sources
        if detectors.len() >= self.config.max_sources && !detectors.contains_key(&event.source_id) {
            // Use global detector as fallback
            drop(detectors);
            return self.calculate_global_novelty(event).await;
        }

        let detector = detectors.entry(event.source_id.clone()).or_insert_with(|| {
            NoveltyDetector::new(
                self.config.sketch_width,
                self.config.sketch_depth,
                self.config.decay_factor,
                self.config.novelty_threshold,
                self.config.time_window_ms,
            )
        });

        detector.calculate_novelty(event)
    }

    async fn calculate_global_novelty(&self, event: &EventInfo) -> Result<f64> {
        let mut detector = self.global_detector.write().await;
        detector.calculate_novelty(event)
    }

    /// Get statistics for all detectors
    pub async fn get_all_stats(&self) -> HashMap<String, NoveltyDetectorStats> {
        let detectors = self.detectors.read().await;
        let mut stats = HashMap::new();

        for (source_id, detector) in detectors.iter() {
            stats.insert(source_id.clone(), detector.get_memory_stats());
        }

        if self.config.enable_global_detection {
            let global_detector = self.global_detector.read().await;
            stats.insert("__global__".to_string(), global_detector.get_memory_stats());
        }

        stats
    }

    /// Reset specific source detector
    pub async fn reset_source(&self, source_id: &str) {
        let mut detectors = self.detectors.write().await;
        if let Some(detector) = detectors.get_mut(source_id) {
            detector.reset();
        }
    }

    /// Reset all detectors
    pub async fn reset_all(&self) {
        let mut detectors = self.detectors.write().await;
        for detector in detectors.values_mut() {
            detector.reset();
        }

        if self.config.enable_global_detection {
            let mut global_detector = self.global_detector.write().await;
            global_detector.reset();
        }
    }

    /// Clean up inactive sources
    pub async fn cleanup_inactive_sources(&self, inactive_threshold_ms: u64) {
        let mut detectors = self.detectors.write().await;
        let cutoff = std::time::Instant::now() - std::time::Duration::from_millis(inactive_threshold_ms);
        
        detectors.retain(|_source_id, detector| {
            detector.last_decay > cutoff
        });
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn create_test_event(message: &str, event_type: &str, source_id: &str) -> EventInfo {
        EventInfo {
            event_id: "test_event".to_string(),
            source_id: source_id.to_string(),
            event_type: event_type.to_string(),
            message: message.to_string(),
            structured_data: HashMap::new(),
            timestamp: std::time::SystemTime::now(),
            log_level: "INFO".to_string(),
            source_path: "/test/path".to_string(),
        }
    }

    #[test]
    fn test_count_min_sketch() {
        let mut sketch = CountMinSketch::new(100, 4);
        
        // First occurrence should have count 0, then 1 after increment
        assert_eq!(sketch.estimate("test_item"), 0);
        sketch.increment("test_item");
        assert_eq!(sketch.estimate("test_item"), 1);
        
        // Multiple increments
        sketch.increment("test_item");
        sketch.increment("test_item");
        assert_eq!(sketch.estimate("test_item"), 3);
    }

    #[test]
    fn test_message_pattern_extraction() {
        let detector = NoveltyDetector::new(100, 4, 0.95, 0.7, 60000);
        
        let test_cases = vec![
            ("User 12345 logged in", "User <NUM> logged in"),
            ("Error at 192.168.1.1", "Error at <IP>"),
            ("UUID: 550e8400-e29b-41d4-a716-446655440000", "UUID: <UUID>"),
            ("Email: user@example.com sent", "Email: <EMAIL> sent"),
            ("Visit https://example.com now", "Visit <URL> now"),
        ];
        
        for (input, expected) in test_cases {
            let pattern = detector.extract_message_pattern(input);
            assert_eq!(pattern, expected);
        }
    }

    #[tokio::test]
    async fn test_novelty_detection() {
        let mut detector = NoveltyDetector::new(100, 4, 0.95, 0.7, 60000);
        
        let event1 = create_test_event("User login successful", "auth", "source1");
        let event2 = create_test_event("User login successful", "auth", "source1"); // Same event
        let event3 = create_test_event("Database connection failed", "error", "source1"); // Different event
        
        // First occurrence should be more novel
        let novelty1 = detector.calculate_novelty(&event1).unwrap();
        let novelty2 = detector.calculate_novelty(&event2).unwrap();
        let novelty3 = detector.calculate_novelty(&event3).unwrap();
        
        // Second occurrence of same pattern should be less novel
        assert!(novelty1 > novelty2);
        
        // Different pattern should be highly novel
        assert!(novelty3 > novelty2);
        
        // All novelty scores should be in valid range
        assert!((0.0..=1.0).contains(&novelty1));
        assert!((0.0..=1.0).contains(&novelty2));
        assert!((0.0..=1.0).contains(&novelty3));
    }

    #[tokio::test]
    async fn test_multi_source_novelty() {
        let config = NoveltyConfig::default();
        let manager = MultiSourceNoveltyManager::new(config);
        
        let event1 = create_test_event("Login attempt", "auth", "source1");
        let event2 = create_test_event("Login attempt", "auth", "source2");
        
        let novelty1 = manager.calculate_novelty(&event1).await.unwrap();
        let novelty2 = manager.calculate_novelty(&event2).await.unwrap();
        
        // Both should be novel since they're from different sources
        assert!(novelty1 > 0.5);
        assert!(novelty2 > 0.5);
        
        // Second occurrence from same source should be less novel
        let novelty1_repeat = manager.calculate_novelty(&event1).await.unwrap();
        assert!(novelty1_repeat < novelty1);
    }

    #[test]
    fn test_sketch_decay() {
        let mut sketch = CountMinSketch::new(10, 2);
        
        sketch.increment("test");
        sketch.increment("test");
        assert_eq!(sketch.estimate("test"), 2);
        
        sketch.decay(0.5);
        assert_eq!(sketch.estimate("test"), 1); // 2 * 0.5 = 1
    }

    #[tokio::test]
    async fn test_detector_stats() {
        let mut detector = NoveltyDetector::new(100, 4, 0.95, 0.7, 60000);
        
        let event = create_test_event("Test message", "info", "test_source");
        detector.calculate_novelty(&event).unwrap();
        
        let stats = detector.get_memory_stats();
        assert!(stats.total_patterns_tracked > 0);
        assert!(stats.memory_usage_bytes > 0);
        assert_eq!(stats.sketch_count, 1);
    }

    #[test]
    fn test_feature_extraction() {
        let detector = NoveltyDetector::new(100, 4, 0.95, 0.7, 60000);
        
        let mut structured_data = HashMap::new();
        structured_data.insert("user_id".to_string(), serde_json::Value::Number(serde_json::Number::from(12345)));
        structured_data.insert("status".to_string(), serde_json::Value::String("success".to_string()));
        
        let event = EventInfo {
            event_id: "test".to_string(),
            source_id: "source1".to_string(),
            event_type: "auth".to_string(),
            message: "User 12345 login successful".to_string(),
            structured_data,
            timestamp: std::time::SystemTime::now(),
            log_level: "INFO".to_string(),
            source_path: "/app/auth".to_string(),
        };
        
        let features = detector.extract_features(&event).unwrap();
        
        assert_eq!(features.event_type, "auth");
        assert_eq!(features.source_path, "/app/auth");
        assert_eq!(features.log_level, "INFO");
        assert_eq!(features.message_pattern, "User <NUM> login successful");
        assert!(features.structured_fields.contains_key("user_id"));
        assert!(features.structured_fields.contains_key("status"));
        
        let patterns = features.extract_patterns();
        assert!(!patterns.is_empty());
        assert!(patterns.iter().any(|p| p.contains("auth:/app/auth:INFO")));
    }
}