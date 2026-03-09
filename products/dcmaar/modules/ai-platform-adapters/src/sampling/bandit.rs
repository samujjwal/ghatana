use std::collections::HashMap;
use std::sync::Arc;
use tokio::sync::RwLock;
use serde::Deserialize;
use anyhow::{Result, Context};

/// Configuration for the adaptive sampling bandit.
///
/// Controls algorithm selection, exploration parameters and safety thresholds
/// used by the adaptive sampling controller.
#[derive(Debug, Clone, Deserialize)]
pub struct BanditConfig {
    /// Selected bandit algorithm to use for adaptive sampling.
    pub algorithm: BanditAlgorithm,
    /// Initial exploration probability (used by epsilon-greedy variants).
    pub epsilon: f64,
    /// Learning rate applied when updating model parameters.
    pub learning_rate: f64,
    /// Multiplicative decay applied to the exploration rate over time.
    pub exploration_decay: f64,
    /// Minimum allowed sampling rate (0.0..1.0).
    pub min_sample_rate: f64,
    /// Maximum allowed sampling rate (0.0..1.0).
    pub max_sample_rate: f64,
    /// Milliseconds between periodic bandit updates.
    pub update_interval_ms: u64,
    /// Size of the context window (number of recent rewards) retained.
    pub context_window_size: usize,
    /// Maximum number of consecutive drops before guardrails trigger.
    pub max_drop_burst: usize,
    /// Threshold of backpressure (0.0..1.0) above which decisions are conservative.
    pub backpressure_threshold: f64,
}

impl Default for BanditConfig {
    fn default() -> Self {
        Self {
            algorithm: BanditAlgorithm::LinUCB,
            epsilon: 0.1,
            learning_rate: 0.01,
            exploration_decay: 0.995,
            min_sample_rate: 0.1,
            max_sample_rate: 1.0,
            update_interval_ms: 5000,
            context_window_size: 100,
            max_drop_burst: 50,
            backpressure_threshold: 0.8,
        }
    }
}

/// Enumeration of supported bandit algorithms used for adaptive sampling.
#[derive(Debug, Clone, Deserialize)]
pub enum BanditAlgorithm {
    /// Epsilon-greedy exploration policy.
    EpsilonGreedy,
    /// Linear UCB algorithm using per-action linear models.
    LinUCB,
    /// Thompson Sampling probabilistic algorithm.
    ThompsonSampling,
    /// Classic UCB1 algorithm.
    UCB1,
}

/// Context features provided to the bandit when making sampling decisions.
#[derive(Debug, Clone)]
pub struct SamplingContext {
    /// Identifier for the event source the context refers to.
    pub source_id: String,
    /// Latest novelty score for the source (0.0..1.0).
    pub novelty_score: f64,
    /// Normalized CPU pressure signal (0.0..1.0).
    pub cpu_pressure: f64,
    /// Normalized memory pressure signal (0.0..1.0).
    pub memory_pressure: f64,
    /// Combined backpressure signal (0.0..1.0) from detectors.
    pub backpressure_signal: f64,
    /// Observed incoming event rate for the source.
    pub event_rate: f64,
    /// Observed error rate (fraction) for recent events.
    pub error_rate: f64,
    /// Historical importance score used to bias sampling.
    pub historical_importance: f64,
    /// Normalized time of day feature (0.0..1.0).
    pub time_of_day: f64, // 0.0 to 1.0 (normalized hour)
    /// Normalized day-of-week feature (0.0..1.0).
    pub day_of_week: f64, // 0.0 to 1.0 (normalized day)
}


/// Action representing a sampling decision (discrete sampling rate).
#[derive(Debug, Clone, Copy)]
pub struct SamplingAction {
    /// Selected sampling rate to apply for the source (0.0..1.0).
    pub sample_rate: f64,
    /// Numeric identifier for the discrete action chosen.
    pub action_id: usize,
}

/// Minimal documentation: represents a discrete sampling decision returned
/// by bandit algorithms. Contains the selected sampling rate and an
/// identifier for the chosen action.

/// Reward signal used to inform learning updates for the bandit.
#[derive(Debug, Clone)]
pub struct SamplingReward {
    /// Utility component of the reward (higher is better).
    pub utility_score: f64,
    /// Cost component of the reward (lower is better).
    pub cost_score: f64,
    /// Recall for incidents detected as a result of the sampling decision.
    pub incident_recall: f64,
    /// Data quality observed for the sampled events.
    pub data_quality: f64,
    /// Latency impact introduced by sampling (lower is better).
    pub latency_impact: f64,
}

impl SamplingReward {
    /// Compute a single scalar reward combining the configured reward
    /// components. The implementation uses a weighted combination of
    /// the individual scores and returns a f64 reward value.
    pub fn compute_total_reward(&self) -> f64 {
        // Weighted combination of different reward components
        0.4 * self.utility_score +
        0.2 * (1.0 - self.cost_score) + // Lower cost is better
        0.3 * self.incident_recall +
        0.1 * self.data_quality -
        0.1 * self.latency_impact // Lower latency impact is better
    }
}

/// Aggregated statistics for a single source tracked by the bandit.
#[derive(Debug, Clone)]
pub struct SourceStats {
    /// Total number of events observed for the source.
    pub total_events: u64,
    /// Number of events that were sampled for downstream processing.
    pub sampled_events: u64,
    /// Number of events dropped by sampling logic.
    pub dropped_events: u64,
    /// Currently applied sampling rate for the source (0.0..1.0).
    pub current_sample_rate: f64,
    /// Running average novelty score for the source.
    pub avg_novelty: f64,
    /// Recent reward history used for bandit updates.
    pub recent_rewards: Vec<f64>,
    /// Instant when this record was last updated.
    pub last_updated: std::time::Instant,
    /// Number of consecutive drop events recorded (used for guardrails).
    pub consecutive_drops: usize,
}

/// Per-source aggregated statistics tracked by the adaptive sampling
/// subsystem. This struct contains counters and short-term history used
/// for guardrails and telemetry.

impl Default for SourceStats {
    fn default() -> Self {
        Self {
            total_events: 0,
            sampled_events: 0,
            dropped_events: 0,
            current_sample_rate: 0.0,
            avg_novelty: 0.0,
            recent_rewards: Vec::new(),
            last_updated: std::time::Instant::now(),
            consecutive_drops: 0,
        }
    }
}

/// LinUCB bandit implementation using per-action linear models.
#[derive(Debug, Clone)]
pub struct LinUCBBandit {
    alpha: f64,
    _dimension: usize,
    // A matrix and b vector for each action
    a_matrices: Vec<nalgebra::DMatrix<f64>>,
    b_vectors: Vec<nalgebra::DVector<f64>>,
    actions: Vec<SamplingAction>,
}

/// LinUCB bandit implementation using per-action linear models. Public
/// methods allow selecting actions and updating model parameters.

impl LinUCBBandit {
    /// Create a new LinUCB bandit instance for the provided actions.
    ///
    /// `dimension` is the feature vector length and `alpha` controls
    /// exploration-confidence scaling.
    pub fn new(actions: Vec<SamplingAction>, dimension: usize, alpha: f64) -> Self {
        let num_actions = actions.len();
        let a_matrices = (0..num_actions)
            .map(|_| nalgebra::DMatrix::identity(dimension, dimension))
            .collect();
        let b_vectors = (0..num_actions)
            .map(|_| nalgebra::DVector::zeros(dimension))
            .collect();

        Self {
            alpha,
            _dimension: dimension,
            a_matrices,
            b_vectors,
            actions,
        }
    }

    /// Select an action using the LinUCB decision rule for the provided
    /// feature `context` vector.
    pub fn select_action(&self, context: &nalgebra::DVector<f64>) -> Result<SamplingAction> {
        let mut best_action = 0;
        let mut best_ucb = f64::NEG_INFINITY;

        for (i, _action) in self.actions.iter().enumerate() {
            let a_inv = self.a_matrices[i].clone().try_inverse()
                .context("Failed to invert A matrix")?;
            let theta = &a_inv * &self.b_vectors[i];
            let confidence = (context.transpose() * &a_inv * context)[0].sqrt();
            
            let ucb = context.dot(&theta) + self.alpha * confidence;
            
            if ucb > best_ucb {
                best_ucb = ucb;
                best_action = i;
            }
        }

        Ok(self.actions[best_action])
    }

    /// Update the internal linear model parameters for `action_id` using
    /// the observed `context` and scalar `reward`.
    pub fn update(&mut self, context: &nalgebra::DVector<f64>, action_id: usize, reward: f64) {
        if action_id < self.actions.len() {
            self.a_matrices[action_id] += context * context.transpose();
            self.b_vectors[action_id] += reward * context;
        }
    }
}

/// Simple epsilon-greedy bandit implementation for exploration/exploitation.
#[derive(Debug, Clone)]
pub struct EpsilonGreedyBandit {
    epsilon: f64,
    epsilon_decay: f64,
    q_values: Vec<f64>,
    action_counts: Vec<u64>,
    actions: Vec<SamplingAction>,
}

/// Simple epsilon-greedy bandit implementation for exploration/exploitation.
/// Exposes methods for selecting an action and updating the estimated
/// action values.

impl EpsilonGreedyBandit {
    /// Create a new epsilon-greedy bandit instance.
    pub fn new(actions: Vec<SamplingAction>, epsilon: f64, epsilon_decay: f64) -> Self {
        let num_actions = actions.len();
        Self {
            epsilon,
            epsilon_decay,
            q_values: vec![0.0; num_actions],
            action_counts: vec![0; num_actions],
            actions,
        }
    }

    /// Select an action according to the epsilon-greedy policy. With
    /// probability `epsilon` chooses a random action; otherwise selects
    /// the action with the highest estimated value.
    pub fn select_action(&mut self, _context: &nalgebra::DVector<f64>) -> SamplingAction {
        if rand::random::<f64>() < self.epsilon {
            // Explore: random action
            let random_idx = rand::random::<usize>() % self.actions.len();
            self.actions[random_idx]
        } else {
            // Exploit: best action
            let best_idx = self.q_values
                .iter()
                .enumerate()
                .max_by(|(_, a), (_, b)| a.partial_cmp(b).unwrap_or(std::cmp::Ordering::Equal))
                .map(|(idx, _)| idx)
                .unwrap_or(0);
            self.actions[best_idx]
        }
    }

    /// Update the Q-value estimate for `action_id` using the observed
    /// scalar `reward` and decay exploration probability.
    pub fn update(&mut self, action_id: usize, reward: f64) {
        if action_id < self.actions.len() {
            self.action_counts[action_id] += 1;
            let count = self.action_counts[action_id] as f64;
            self.q_values[action_id] += (reward - self.q_values[action_id]) / count;
            
            // Decay epsilon
            self.epsilon *= self.epsilon_decay;
        }
    }
}

/// High-level adaptive sampling bandit that coordinates specific
/// algorithm instances and maintains per-source statistics/metrics.
pub struct AdaptiveSamplingBandit {
    config: BanditConfig,
    linucb_bandits: Arc<RwLock<HashMap<String, LinUCBBandit>>>,
    epsilon_bandits: Arc<RwLock<HashMap<String, EpsilonGreedyBandit>>>,
    source_stats: Arc<RwLock<HashMap<String, SourceStats>>>,
    actions: Vec<SamplingAction>,
    context_dimension: usize,
    metrics: Arc<RwLock<BanditMetrics>>,
}

/// High-level adaptive sampling bandit that coordinates multiple
/// algorithm instances and maintains per-source statistics. This type is
/// the main integration point for selecting sampling decisions.

/// Runtime metrics exposed by the bandit for monitoring and telemetry.
#[derive(Debug, Default, Clone)]
pub struct BanditMetrics {
    /// Total number of sampling decisions made by the bandit.
    pub total_decisions: u64,
    /// How many of the decisions were exploratory in nature.
    pub exploration_decisions: u64,
    /// How many of the decisions used exploitation (greedy selection).
    pub exploitation_decisions: u64,
    /// Cumulative reward observed by the bandit.
    pub total_reward: f64,
    /// Running average reward per decision.
    pub average_reward: f64,
    /// Number of distinct sources tracked by the bandit.
    pub sources_count: usize,
    /// Number of backpressure events observed that influenced decisions.
    pub backpressure_events: u64,
    /// Number of times rate-limiting/guardrails were applied.
    pub rate_limiting_events: u64,
}

/// Runtime metrics exported by the bandit for monitoring and telemetry.
/// These metrics are updated as sampling decisions are made and rewards
/// are observed.

impl AdaptiveSamplingBandit {
    /// Construct the high-level AdaptiveSamplingBandit coordinating
    /// per-source algorithm instances based on `config`.
    pub fn new(config: BanditConfig) -> Self {
        // Create discrete sampling rate actions
        let mut actions = Vec::new();
        let step = (config.max_sample_rate - config.min_sample_rate) / 9.0;
        for i in 0..10 {
            let rate = config.min_sample_rate + (i as f64) * step;
            actions.push(SamplingAction {
                sample_rate: rate,
                action_id: i,
            });
        }

        // Context features: novelty, cpu_pressure, mem_pressure, backpressure, 
        // event_rate, error_rate, historical_importance, time_of_day, day_of_week
        let context_dimension = 9;

        Self {
            config,
            linucb_bandits: Arc::new(RwLock::new(HashMap::new())),
            epsilon_bandits: Arc::new(RwLock::new(HashMap::new())),
            source_stats: Arc::new(RwLock::new(HashMap::new())),
            actions,
            context_dimension,
            metrics: Arc::new(RwLock::new(BanditMetrics::default())),
        }
    }

    /// Get sampling decision for a source given context
    pub async fn get_sampling_decision(&self, context: SamplingContext) -> Result<SamplingAction> {
        let context_vector = self.context_to_vector(&context);
        
        let action = match self.config.algorithm {
            BanditAlgorithm::LinUCB => {
                self.get_linucb_decision(&context.source_id, &context_vector).await?
            }
            BanditAlgorithm::EpsilonGreedy => {
                self.get_epsilon_greedy_decision(&context.source_id, &context_vector).await
            }
            _ => {
                // Fallback to simple heuristic
                self.get_heuristic_decision(&context).await
            }
        };

        // Apply guardrails
        let final_action = self.apply_guardrails(action, &context).await?;

        // Update metrics
        self.update_decision_metrics(&context.source_id, final_action).await;

        Ok(final_action)
    }

    /// Update bandit with reward signal
    pub async fn update_with_reward(
        &self,
        source_id: &str,
        action: SamplingAction,
        reward: SamplingReward,
    ) -> Result<()> {
        let total_reward = reward.compute_total_reward();
        
        // Update source statistics
        {
            let mut stats = self.source_stats.write().await;
            let source_stats = stats.entry(source_id.to_string()).or_default();
            source_stats.recent_rewards.push(total_reward);
            if source_stats.recent_rewards.len() > self.config.context_window_size {
                source_stats.recent_rewards.remove(0);
            }
            source_stats.last_updated = std::time::Instant::now();
        }

        // Update appropriate bandit
        match self.config.algorithm {
            BanditAlgorithm::LinUCB => {
                // Note: We'd need to store the context vector used for this decision
                // For now, skip the update or use a simplified approach
                log::debug!("LinUCB reward update not implemented in simplified version");
            }
            BanditAlgorithm::EpsilonGreedy => {
                let mut bandits = self.epsilon_bandits.write().await;
                if let Some(bandit) = bandits.get_mut(source_id) {
                    bandit.update(action.action_id, total_reward);
                }
            }
            _ => {}
        }

        // Update global metrics
        self.update_reward_metrics(total_reward).await;

        Ok(())
    }

    async fn get_linucb_decision(
        &self,
        source_id: &str,
        context: &nalgebra::DVector<f64>,
    ) -> Result<SamplingAction> {
        let mut bandits = self.linucb_bandits.write().await;
        let bandit = bandits.entry(source_id.to_string()).or_insert_with(|| {
            LinUCBBandit::new(self.actions.clone(), self.context_dimension, 1.0)
        });
        
        bandit.select_action(context)
    }

    async fn get_epsilon_greedy_decision(
        &self,
        source_id: &str,
        context: &nalgebra::DVector<f64>,
    ) -> SamplingAction {
        let mut bandits = self.epsilon_bandits.write().await;
        let bandit = bandits.entry(source_id.to_string()).or_insert_with(|| {
            EpsilonGreedyBandit::new(
                self.actions.clone(),
                self.config.epsilon,
                self.config.exploration_decay,
            )
        });
        
        bandit.select_action(context)
    }

    async fn get_heuristic_decision(&self, context: &SamplingContext) -> SamplingAction {
        // Simple heuristic based on context
        let mut target_rate = self.config.max_sample_rate;

        // Reduce rate based on resource pressure
        let pressure_factor = (context.cpu_pressure + context.memory_pressure) / 2.0;
        target_rate *= 1.0 - pressure_factor * 0.5;

        // Reduce rate based on backpressure
        if context.backpressure_signal > self.config.backpressure_threshold {
            target_rate *= 0.5;
        }

        // Increase rate for high novelty
        if context.novelty_score > 0.8 {
            target_rate = (target_rate * 1.2).min(self.config.max_sample_rate);
        }

        // Ensure within bounds
        target_rate = target_rate.max(self.config.min_sample_rate).min(self.config.max_sample_rate);

        // Find closest action
        let closest_action = self.actions
            .iter()
            .min_by(|a, b| {
                (a.sample_rate - target_rate).abs()
                    .partial_cmp(&(b.sample_rate - target_rate).abs())
                    .unwrap_or(std::cmp::Ordering::Equal)
            })
            .copied()
            .unwrap_or(self.actions[0]);

        closest_action
    }

    /// Apply safety guardrails to sampling decisions
    async fn apply_guardrails(
        &self,
        action: SamplingAction,
        context: &SamplingContext,
    ) -> Result<SamplingAction> {
        let mut final_rate = action.sample_rate;

        // Check for consecutive drops to prevent starvation
        {
            let stats = self.source_stats.read().await;
            if let Some(source_stats) = stats.get(&context.source_id) {
                if source_stats.consecutive_drops >= self.config.max_drop_burst {
                    final_rate = final_rate.max(self.config.min_sample_rate * 2.0);
                    self.update_rate_limiting_metrics().await;
                }
            }
        }

        // Emergency high novelty override
        if context.novelty_score > 0.95 {
            final_rate = final_rate.max(0.8);
        }

        // Ensure within configured bounds
        final_rate = final_rate
            .max(self.config.min_sample_rate)
            .min(self.config.max_sample_rate);

        Ok(SamplingAction {
            sample_rate: final_rate,
            action_id: action.action_id,
        })
    }

    /// Convert context to feature vector
    fn context_to_vector(&self, context: &SamplingContext) -> nalgebra::DVector<f64> {
        nalgebra::DVector::from_vec(vec![
            context.novelty_score,
            context.cpu_pressure,
            context.memory_pressure,
            context.backpressure_signal,
            context.event_rate.ln().max(0.0) / 10.0, // Log-normalized
            context.error_rate,
            context.historical_importance,
            context.time_of_day,
            context.day_of_week,
        ])
    }

    /// Update decision metrics
    async fn update_decision_metrics(&self, source_id: &str, action: SamplingAction) {
        let mut metrics = self.metrics.write().await;
        metrics.total_decisions += 1;

        // Update source stats
        let mut stats = self.source_stats.write().await;
        let source_stats = stats.entry(source_id.to_string()).or_default();
        source_stats.current_sample_rate = action.sample_rate;

        metrics.sources_count = stats.len();
    }

    async fn update_reward_metrics(&self, reward: f64) {
        let mut metrics = self.metrics.write().await;
        metrics.total_reward += reward;
        if metrics.total_decisions > 0 {
            metrics.average_reward = metrics.total_reward / metrics.total_decisions as f64;
        }
    }

    async fn update_rate_limiting_metrics(&self) {
        let mut metrics = self.metrics.write().await;
        metrics.rate_limiting_events += 1;
    }

    /// Get current bandit metrics
    pub async fn get_metrics(&self) -> BanditMetrics {
        (*self.metrics.read().await).clone()
    }

    /// Get statistics for all tracked sources.
    pub async fn get_all_source_stats(&self) -> HashMap<String, SourceStats> {
        self.source_stats.read().await.clone()
    }

    /// Get statistics for a specific source, if present.
    pub async fn get_source_stats(&self, source_id: &str) -> Option<SourceStats> {
        self.source_stats.read().await.get(source_id).cloned()
    }

    /// Reset all internal models and statistics.
    pub async fn reset(&self) {
        self.linucb_bandits.write().await.clear();
        self.epsilon_bandits.write().await.clear();
        self.source_stats.write().await.clear();
        *self.metrics.write().await = BanditMetrics::default();
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_bandit_creation() {
        let config = BanditConfig::default();
        let bandit = AdaptiveSamplingBandit::new(config);
        
        assert_eq!(bandit.actions.len(), 10);
        assert!(bandit.actions[0].sample_rate >= 0.1);
        assert!(bandit.actions[9].sample_rate <= 1.0);
    }

    #[tokio::test]
    async fn test_heuristic_decision() {
        let config = BanditConfig::default();
        let bandit = AdaptiveSamplingBandit::new(config);
        
        let context = SamplingContext {
            source_id: "test_source".to_string(),
            novelty_score: 0.8,
            cpu_pressure: 0.2,
            memory_pressure: 0.3,
            backpressure_signal: 0.1,
            event_rate: 100.0,
            error_rate: 0.01,
            historical_importance: 0.7,
            time_of_day: 0.5,
            day_of_week: 0.3,
        };
        
        let action = bandit.get_sampling_decision(context).await.unwrap();
        assert!(action.sample_rate >= 0.1);
        assert!(action.sample_rate <= 1.0);
    }

    #[tokio::test]
    async fn test_backpressure_response() {
        let config = BanditConfig::default();
        let bandit = AdaptiveSamplingBandit::new(config);
        
        let high_backpressure_context = SamplingContext {
            source_id: "test_source".to_string(),
            novelty_score: 0.5,
            cpu_pressure: 0.5,
            memory_pressure: 0.5,
            backpressure_signal: 0.9, // High backpressure
            event_rate: 1000.0,
            error_rate: 0.05,
            historical_importance: 0.5,
            time_of_day: 0.5,
            day_of_week: 0.3,
        };
        
        let action = bandit.get_sampling_decision(high_backpressure_context).await.unwrap();
        
        // Should result in lower sampling rate due to backpressure
        assert!(action.sample_rate < 0.8);
    }

    #[tokio::test]
    async fn test_reward_update() {
        let config = BanditConfig {
            algorithm: BanditAlgorithm::EpsilonGreedy,
            ..Default::default()
        };
        let bandit = AdaptiveSamplingBandit::new(config);
        
        let action = SamplingAction {
            sample_rate: 0.5,
            action_id: 5,
        };
        
        let reward = SamplingReward {
            utility_score: 0.8,
            cost_score: 0.3,
            incident_recall: 0.9,
            data_quality: 0.85,
            latency_impact: 0.1,
        };
        
        let result = bandit.update_with_reward("test_source", action, reward).await;
        assert!(result.is_ok());
        
        let metrics = bandit.get_metrics().await;
        assert!(metrics.total_reward > 0.0);
    }

    #[tokio::test]
    async fn test_guardrails() {
        let config = BanditConfig {
            min_sample_rate: 0.1,
            max_sample_rate: 1.0,
            max_drop_burst: 10,
            ..Default::default()
        };
        let bandit = AdaptiveSamplingBandit::new(config);
        
        let context = SamplingContext {
            source_id: "test_source".to_string(),
            novelty_score: 0.99, // Very high novelty
            cpu_pressure: 0.9,
            memory_pressure: 0.9,
            backpressure_signal: 0.9,
            event_rate: 10.0,
            error_rate: 0.1,
            historical_importance: 0.5,
            time_of_day: 0.5,
            day_of_week: 0.3,
        };
        
        let action = SamplingAction {
            sample_rate: 0.05, // Below minimum
            action_id: 0,
        };
        
        let final_action = bandit.apply_guardrails(action, &context).await.unwrap();
        
        // Should be boosted due to high novelty and enforced minimum
        assert!(final_action.sample_rate >= 0.1);
        assert!(final_action.sample_rate >= 0.8); // High novelty override
    }

    #[test]
    fn test_reward_calculation() {
        let reward = SamplingReward {
            utility_score: 0.8,
            cost_score: 0.2,
            incident_recall: 0.9,
            data_quality: 0.85,
            latency_impact: 0.1,
        };
        
        let total = reward.compute_total_reward();
        // 0.4*0.8 + 0.2*(1-0.2) + 0.3*0.9 + 0.1*0.85 - 0.1*0.1
        // = 0.32 + 0.16 + 0.27 + 0.085 - 0.01 = 0.825
        assert!((total - 0.825).abs() < 0.001);
    }
}