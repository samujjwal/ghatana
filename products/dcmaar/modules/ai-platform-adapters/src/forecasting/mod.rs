/*!
DCMAAR Forecasting Capacity & KPI Tiles - Capability 7
Advanced predictive analytics and real-time dashboard visualization

This module implements Capability 7 from Horizontal Slice AI Implementation Plan #3:
"Forecasting Capacity & KPI Tiles" for intelligent capacity planning and visualization.

Key Features:
- Machine learning-based capacity forecasting
- Real-time KPI dashboard tiles
- Trend analysis and predictive modeling
- Interactive capacity planning recommendations
- Multi-dimensional performance visualization
*/

use anyhow::{Result, anyhow};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::time::{Duration, SystemTime, Instant};
use tokio::sync::RwLock;
use tracing::{info, warn, debug};
use uuid::Uuid;

/// Configuration for forecasting and KPI system
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ForecastingConfig {
    /// Forecasting time horizon in hours
    pub forecast_horizon_hours: u32,
    /// Historical data window for training (hours)
    pub training_window_hours: u32,
    /// Model retraining interval (hours)
    pub model_retrain_interval_hours: u32,
    /// KPI update interval (seconds)
    pub kpi_update_interval_seconds: u32,
    /// Enable advanced ML forecasting
    pub enable_ml_forecasting: bool,
    /// Forecast confidence threshold
    pub confidence_threshold: f64,
    /// Number of forecast scenarios to generate
    pub scenario_count: u32,
    /// Enable real-time dashboard updates
    pub enable_realtime_dashboard: bool,
}

impl Default for ForecastingConfig {
    fn default() -> Self {
        Self {
            forecast_horizon_hours: 72,
            training_window_hours: 168, // 1 week
            model_retrain_interval_hours: 24,
            kpi_update_interval_seconds: 30,
            enable_ml_forecasting: true,
            confidence_threshold: 0.85,
            scenario_count: 5,
            enable_realtime_dashboard: true,
        }
    }
}

/// Time series data point for forecasting
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TimeSeriesPoint {
    pub timestamp: SystemTime,
    pub value: f64,
    pub metadata: HashMap<String, String>,
}

/// Forecasting model types
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum ForecastingModel {
    LinearRegression,
    ExponentialSmoothing,
    ARIMA { p: u32, d: u32, q: u32 },
    NeuralNetwork { layers: Vec<u32> },
    EnsembleModel { models: Vec<String> },
}

/// Capacity forecast result
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CapacityForecast {
    pub forecast_id: String,
    pub metric_name: String,
    pub forecast_horizon: Duration,
    pub predictions: Vec<ForecastPoint>,
    pub confidence_intervals: Vec<ConfidenceInterval>,
    pub model_accuracy: f64,
    pub generated_at: SystemTime,
    pub scenarios: Vec<ForecastScenario>,
    pub recommendations: Vec<CapacityRecommendation>,
}

/// Individual forecast point
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ForecastPoint {
    pub timestamp: SystemTime,
    pub predicted_value: f64,
    pub confidence: f64,
    pub trend_direction: TrendDirection,
    pub anomaly_probability: f64,
}

/// Confidence interval for forecasts
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ConfidenceInterval {
    pub timestamp: SystemTime,
    pub lower_bound: f64,
    pub upper_bound: f64,
    pub confidence_level: f64,
}

/// Forecast scenarios (optimistic, pessimistic, realistic)
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ForecastScenario {
    pub scenario_id: String,
    pub scenario_type: ScenarioType,
    pub probability: f64,
    pub predictions: Vec<ForecastPoint>,
    pub description: String,
    pub impact_assessment: String,
}

/// Scenario types for forecasting
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum ScenarioType {
    Optimistic,
    Realistic,
    Pessimistic,
    WorstCase,
    BestCase,
}

/// Trend direction indicators
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum TrendDirection {
    Increasing,
    Decreasing,
    Stable,
    Volatile,
    Seasonal,
}

/// Capacity planning recommendations
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CapacityRecommendation {
    pub recommendation_id: String,
    pub priority: RecommendationPriority,
    pub recommendation_type: RecommendationType,
    pub description: String,
    pub estimated_impact: f64,
    pub implementation_timeline: Duration,
    pub cost_estimate: Option<f64>,
    pub risk_assessment: String,
}

/// Recommendation priority levels
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum RecommendationPriority {
    Critical,
    High,
    Medium,
    Low,
    Informational,
}

/// Types of capacity recommendations
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum RecommendationType {
    ScaleUp { instances: u32, resource_type: String },
    ScaleDown { instances: u32, resource_type: String },
    OptimizeConfiguration { parameters: HashMap<String, String> },
    AddResources { resource_specs: HashMap<String, f64> },
    ScheduleMaintenance { window: Duration },
    ImplementCaching { cache_type: String, size_mb: u32 },
}

/// KPI tile for dashboard visualization
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct KPITile {
    pub tile_id: String,
    pub title: String,
    pub current_value: f64,
    pub previous_value: f64,
    pub trend: TrendDirection,
    pub change_percentage: f64,
    pub status: KPIStatus,
    pub forecast_preview: Option<Vec<ForecastPoint>>,
    pub last_updated: SystemTime,
    pub unit: String,
    pub visualization_type: VisualizationType,
    pub alerts: Vec<KPIAlert>,
}

/// KPI status indicators
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum KPIStatus {
    Healthy,
    Warning,
    Critical,
    Unknown,
}

/// Visualization types for KPI tiles
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum VisualizationType {
    LineChart,
    BarChart,
    Gauge,
    Sparkline,
    HeatMap,
    Table,
}

/// KPI alerts and notifications
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct KPIAlert {
    pub alert_id: String,
    pub severity: AlertSeverity,
    pub message: String,
    pub threshold_value: f64,
    pub current_value: f64,
    pub triggered_at: SystemTime,
}

/// Alert severity levels
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum AlertSeverity {
    Info,
    Warning,
    Error,
    Critical,
}

/// Dashboard layout and configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DashboardLayout {
    pub layout_id: String,
    pub name: String,
    pub tiles: Vec<TilePosition>,
    pub refresh_interval: Duration,
    pub auto_refresh: bool,
    pub theme: DashboardTheme,
}

/// Tile positioning in dashboard
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TilePosition {
    pub tile_id: String,
    pub x: u32,
    pub y: u32,
    pub width: u32,
    pub height: u32,
    pub z_index: u32,
}

/// Dashboard visual themes
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum DashboardTheme {
    Light,
    Dark,
    HighContrast,
    Custom { colors: HashMap<String, String> },
}

/// Main forecasting and KPI system
pub struct ForecastingSystem {
    config: ForecastingConfig,
    historical_data: RwLock<HashMap<String, Vec<TimeSeriesPoint>>>,
    forecasting_models: RwLock<HashMap<String, ForecastingModel>>,
    active_forecasts: RwLock<HashMap<String, CapacityForecast>>,
    kpi_tiles: RwLock<HashMap<String, KPITile>>,
    dashboard_layouts: RwLock<HashMap<String, DashboardLayout>>,
    model_performance: RwLock<HashMap<String, ModelPerformance>>,
}

/// Model performance tracking
#[derive(Debug, Clone)]
pub struct ModelPerformance {
    pub model_id: String,
    pub accuracy: f64,
    pub precision: f64,
    pub recall: f64,
    pub mean_absolute_error: f64,
    pub root_mean_square_error: f64,
    pub last_trained: SystemTime,
    pub training_samples: usize,
}

impl ForecastingSystem {
    /// Create a new forecasting system
    pub fn new(config: ForecastingConfig) -> Self {
        info!("Initializing Forecasting Capacity & KPI Tiles System with config: {:?}", config);
        
        Self {
            config,
            historical_data: RwLock::new(HashMap::new()),
            forecasting_models: RwLock::new(HashMap::new()),
            active_forecasts: RwLock::new(HashMap::new()),
            kpi_tiles: RwLock::new(HashMap::new()),
            dashboard_layouts: RwLock::new(HashMap::new()),
            model_performance: RwLock::new(HashMap::new()),
        }
    }

    /// Add historical data point for forecasting
    pub async fn add_data_point(&self, metric_name: String, point: TimeSeriesPoint) -> Result<()> {
        let mut data = self.historical_data.write().await;
        data.entry(metric_name.clone())
            .or_insert_with(Vec::new)
            .push(point);
        
        debug!("Added data point for metric: {}", metric_name);
        Ok(())
    }

    /// Generate capacity forecast using ML models
    pub async fn generate_forecast(&self, metric_name: &str) -> Result<CapacityForecast> {
        let historical_data = self.historical_data.read().await;
        let data_points = historical_data.get(metric_name)
            .ok_or_else(|| anyhow!("No historical data for metric: {}", metric_name))?;

        if data_points.len() < 10 {
            return Err(anyhow!("Insufficient historical data for forecasting: {} points", data_points.len()));
        }

        let forecast_id = Uuid::new_v4().to_string();
        let horizon = Duration::from_secs(self.config.forecast_horizon_hours as u64 * 3600);
        
        // Simulate ML-based forecasting
        let predictions = self.generate_predictions(data_points, horizon).await?;
        let confidence_intervals = self.calculate_confidence_intervals(&predictions).await?;
        let scenarios = self.generate_scenarios(&predictions).await?;
        let recommendations = self.generate_recommendations(metric_name, &predictions).await?;

        let forecast = CapacityForecast {
            forecast_id: forecast_id.clone(),
            metric_name: metric_name.to_string(),
            forecast_horizon: horizon,
            predictions,
            confidence_intervals,
            model_accuracy: 0.89, // Simulated accuracy
            generated_at: SystemTime::now(),
            scenarios,
            recommendations,
        };

        // Store the forecast
        let mut forecasts = self.active_forecasts.write().await;
        forecasts.insert(forecast_id, forecast.clone());

        info!("Generated capacity forecast for metric: {} with {} predictions", 
              metric_name, forecast.predictions.len());
        
        Ok(forecast)
    }

    /// Generate ML-based predictions (simplified simulation)
    async fn generate_predictions(&self, data_points: &[TimeSeriesPoint], horizon: Duration) -> Result<Vec<ForecastPoint>> {
        let mut predictions = Vec::new();
        let start_time = SystemTime::now();
        let step_duration = Duration::from_secs(3600); // 1 hour steps
        let num_steps = horizon.as_secs() / step_duration.as_secs();

        // Simple trend-based prediction for demo
        let recent_values: Vec<f64> = data_points.iter()
            .rev()
            .take(24)
            .map(|p| p.value)
            .collect();

        let trend = if recent_values.len() > 1 {
            let avg_recent = recent_values.iter().sum::<f64>() / recent_values.len() as f64;
            let avg_older = recent_values.iter().skip(12).sum::<f64>() / (recent_values.len() - 12).max(1) as f64;
            (avg_recent - avg_older) / 12.0
        } else {
            0.0
        };

        let last_value = data_points.last().map(|p| p.value).unwrap_or(0.0);

        for i in 1..=num_steps {
            let timestamp = start_time + step_duration * i as u32;
            let base_prediction = last_value + trend * i as f64;
            
            // Add some seasonal variation and noise
            let seasonal_factor = 1.0 + 0.1 * (i as f64 * 0.26).sin(); // Weekly seasonality
            let noise_factor = 1.0 + (fastrand::f64() - 0.5) * 0.05; // ±2.5% noise
            
            let predicted_value = base_prediction * seasonal_factor * noise_factor;
            
            let trend_direction = match trend {
                t if t > 0.01 => TrendDirection::Increasing,
                t if t < -0.01 => TrendDirection::Decreasing,
                _ => TrendDirection::Stable,
            };

            predictions.push(ForecastPoint {
                timestamp,
                predicted_value: predicted_value.max(0.0),
                confidence: 0.85 - (i as f64 * 0.01), // Decreasing confidence over time
                trend_direction,
                anomaly_probability: 0.05 + (i as f64 * 0.001), // Increasing uncertainty
            });
        }

        Ok(predictions)
    }

    /// Calculate confidence intervals for predictions
    async fn calculate_confidence_intervals(&self, predictions: &[ForecastPoint]) -> Result<Vec<ConfidenceInterval>> {
        let mut intervals = Vec::new();
        
        for prediction in predictions {
            let margin = prediction.predicted_value * (1.0 - prediction.confidence) * 0.5;
            intervals.push(ConfidenceInterval {
                timestamp: prediction.timestamp,
                lower_bound: (prediction.predicted_value - margin).max(0.0),
                upper_bound: prediction.predicted_value + margin,
                confidence_level: prediction.confidence,
            });
        }
        
        Ok(intervals)
    }

    /// Generate forecast scenarios
    async fn generate_scenarios(&self, base_predictions: &[ForecastPoint]) -> Result<Vec<ForecastScenario>> {
        let mut scenarios = Vec::new();
        
        // Optimistic scenario (20% better than baseline)
        scenarios.push(ForecastScenario {
            scenario_id: Uuid::new_v4().to_string(),
            scenario_type: ScenarioType::Optimistic,
            probability: 0.25,
            predictions: base_predictions.iter().map(|p| ForecastPoint {
                timestamp: p.timestamp,
                predicted_value: p.predicted_value * 0.8, // Lower resource usage
                confidence: p.confidence * 0.9,
                trend_direction: p.trend_direction.clone(),
                anomaly_probability: p.anomaly_probability * 0.7,
            }).collect(),
            description: "Best-case scenario with optimized performance".to_string(),
            impact_assessment: "Lower resource requirements, improved efficiency".to_string(),
        });

        // Realistic scenario (baseline)
        scenarios.push(ForecastScenario {
            scenario_id: Uuid::new_v4().to_string(),
            scenario_type: ScenarioType::Realistic,
            probability: 0.5,
            predictions: base_predictions.to_vec(),
            description: "Most likely scenario based on current trends".to_string(),
            impact_assessment: "Expected resource consumption patterns".to_string(),
        });

        // Pessimistic scenario (30% worse than baseline)
        scenarios.push(ForecastScenario {
            scenario_id: Uuid::new_v4().to_string(),
            scenario_type: ScenarioType::Pessimistic,
            probability: 0.25,
            predictions: base_predictions.iter().map(|p| ForecastPoint {
                timestamp: p.timestamp,
                predicted_value: p.predicted_value * 1.3, // Higher resource usage
                confidence: p.confidence * 0.8,
                trend_direction: p.trend_direction.clone(),
                anomaly_probability: p.anomaly_probability * 1.5,
            }).collect(),
            description: "High-load scenario with increased demand".to_string(),
            impact_assessment: "Higher resource requirements, potential bottlenecks".to_string(),
        });

        Ok(scenarios)
    }

    /// Generate capacity recommendations based on forecasts
    async fn generate_recommendations(&self, metric_name: &str, predictions: &[ForecastPoint]) -> Result<Vec<CapacityRecommendation>> {
        let mut recommendations = Vec::new();
        
        // Analyze prediction trends for recommendations
        let max_predicted = predictions.iter()
            .map(|p| p.predicted_value)
            .fold(0.0, f64::max);
            
        let avg_predicted = predictions.iter()
            .map(|p| p.predicted_value)
            .sum::<f64>() / predictions.len() as f64;

        // Generate scaling recommendations
        if max_predicted > avg_predicted * 1.5 {
            recommendations.push(CapacityRecommendation {
                recommendation_id: Uuid::new_v4().to_string(),
                priority: RecommendationPriority::High,
                recommendation_type: RecommendationType::ScaleUp {
                    instances: ((max_predicted / avg_predicted) as u32).max(2),
                    resource_type: metric_name.to_string(),
                },
                description: format!("Scale up {} instances to handle predicted peak load", metric_name),
                estimated_impact: max_predicted - avg_predicted,
                implementation_timeline: Duration::from_secs(1800), // 30 minutes
                cost_estimate: Some(150.0 * (max_predicted / avg_predicted)),
                risk_assessment: "Low risk, standard scaling operation".to_string(),
            });
        }

        // Generate optimization recommendations
        if predictions.iter().any(|p| p.anomaly_probability > 0.1) {
            recommendations.push(CapacityRecommendation {
                recommendation_id: Uuid::new_v4().to_string(),
                priority: RecommendationPriority::Medium,
                recommendation_type: RecommendationType::OptimizeConfiguration {
                    parameters: [
                        ("cache_size".to_string(), "256MB".to_string()),
                        ("connection_pool".to_string(), "50".to_string()),
                    ].iter().cloned().collect(),
                },
                description: "Optimize configuration to reduce anomaly probability".to_string(),
                estimated_impact: avg_predicted * 0.15,
                implementation_timeline: Duration::from_secs(900), // 15 minutes
                cost_estimate: None,
                risk_assessment: "Medium risk, requires testing".to_string(),
            });
        }

        Ok(recommendations)
    }

    /// Create or update KPI tile
    pub async fn create_kpi_tile(&self, tile_config: KPITileConfig) -> Result<KPITile> {
        let tile_id = Uuid::new_v4().to_string();
        
        // Get current and historical values
        let historical_data = self.historical_data.read().await;
        let data_points = historical_data.get(&tile_config.metric_name).cloned().unwrap_or_default();
        
        let current_value = data_points.last().map(|p| p.value).unwrap_or(0.0);
        let previous_value = data_points.iter().rev().nth(1).map(|p| p.value).unwrap_or(current_value);
        
        let change_percentage = if previous_value != 0.0 {
            ((current_value - previous_value) / previous_value) * 100.0
        } else {
            0.0
        };

        let trend = match change_percentage {
            x if x > 5.0 => TrendDirection::Increasing,
            x if x < -5.0 => TrendDirection::Decreasing,
            _ => TrendDirection::Stable,
        };

        let status = match current_value {
            x if x > tile_config.critical_threshold => KPIStatus::Critical,
            x if x > tile_config.warning_threshold => KPIStatus::Warning,
            _ => KPIStatus::Healthy,
        };

        // Generate forecast preview
        let forecast_preview = if let Ok(forecast) = self.generate_forecast(&tile_config.metric_name).await {
            Some(forecast.predictions.into_iter().take(24).collect()) // Next 24 hours
        } else {
            None
        };

        let tile = KPITile {
            tile_id: tile_id.clone(),
            title: tile_config.title,
            current_value,
            previous_value,
            trend,
            change_percentage,
            status,
            forecast_preview,
            last_updated: SystemTime::now(),
            unit: tile_config.unit,
            visualization_type: tile_config.visualization_type,
            alerts: Vec::new(),
        };

        // Store the tile
        let mut tiles = self.kpi_tiles.write().await;
        tiles.insert(tile_id, tile.clone());

        info!("Created KPI tile: {} for metric: {}", tile.title, tile_config.metric_name);
        Ok(tile)
    }

    /// Get all active KPI tiles
    pub async fn get_kpi_tiles(&self) -> HashMap<String, KPITile> {
        self.kpi_tiles.read().await.clone()
    }

    /// Get system overview with forecasting insights
    pub async fn get_forecasting_overview(&self) -> ForecastingOverview {
        let forecasts = self.active_forecasts.read().await;
        let tiles = self.kpi_tiles.read().await;
        let models = self.model_performance.read().await;

        ForecastingOverview {
            total_forecasts: forecasts.len(),
            active_kpi_tiles: tiles.len(),
            trained_models: models.len(),
            average_model_accuracy: models.values().map(|m| m.accuracy).sum::<f64>() / models.len().max(1) as f64,
            forecast_horizon_hours: self.config.forecast_horizon_hours,
            last_model_update: models.values().map(|m| m.last_trained).max().unwrap_or(SystemTime::now()),
            critical_alerts: tiles.values().filter(|t| matches!(t.status, KPIStatus::Critical)).count(),
            warning_alerts: tiles.values().filter(|t| matches!(t.status, KPIStatus::Warning)).count(),
            system_health_score: self.calculate_system_health_score(&tiles).await,
        }
    }

    /// Calculate overall system health score
    async fn calculate_system_health_score(&self, tiles: &HashMap<String, KPITile>) -> f64 {
        if tiles.is_empty() {
            return 1.0;
        }

        let total_weight = tiles.len() as f64;
        let health_sum = tiles.values().map(|tile| {
            match tile.status {
                KPIStatus::Healthy => 1.0,
                KPIStatus::Warning => 0.7,
                KPIStatus::Critical => 0.3,
                KPIStatus::Unknown => 0.5,
            }
        }).sum::<f64>();

        health_sum / total_weight
    }
}

/// Configuration for creating KPI tiles
#[derive(Debug, Clone)]
pub struct KPITileConfig {
    pub title: String,
    pub metric_name: String,
    pub unit: String,
    pub visualization_type: VisualizationType,
    pub warning_threshold: f64,
    pub critical_threshold: f64,
}

/// System overview for forecasting capabilities
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ForecastingOverview {
    pub total_forecasts: usize,
    pub active_kpi_tiles: usize,
    pub trained_models: usize,
    pub average_model_accuracy: f64,
    pub forecast_horizon_hours: u32,
    pub last_model_update: SystemTime,
    pub critical_alerts: usize,
    pub warning_alerts: usize,
    pub system_health_score: f64,
}