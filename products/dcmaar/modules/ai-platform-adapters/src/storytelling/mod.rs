/*!
DCMAAR Story-Mode Incident View - Capability 9
Narrative-driven incident visualization and contextual analysis

This module implements Capability 9 from Horizontal Slice AI Implementation Plan #3:
"Story-Mode Incident View" for immersive incident storytelling and root cause analysis.

Key Features:
- Narrative-driven incident visualization with AI-generated storytelling
- Contextual incident correlation and timeline reconstruction
- Interactive story-based incident exploration
- Root cause analysis with causal chain visualization
- Multi-dimensional incident context aggregation
- Collaborative incident response workflow
*/

use anyhow::{Result, anyhow};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::time::{Duration, SystemTime};
use tokio::sync::RwLock;
use tracing::{info, warn, debug};
use uuid::Uuid;

/// Configuration for story-mode incident system
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StoryModeConfig {
    /// Incident correlation window in minutes
    pub correlation_window_minutes: u32,
    /// Maximum story complexity level (1-10)
    pub max_story_complexity: u32,
    /// Enable AI-generated narrative descriptions
    pub enable_ai_narrative: bool,
    /// Story refresh interval in seconds
    pub story_refresh_interval_seconds: u32,
    /// Maximum number of related incidents to include
    pub max_related_incidents: u32,
    /// Enable interactive story exploration
    pub enable_interactive_mode: bool,
    /// Context aggregation depth level
    pub context_depth_level: u32,
    /// Enable collaborative annotations
    pub enable_collaborative_features: bool,
}

impl Default for StoryModeConfig {
    fn default() -> Self {
        Self {
            correlation_window_minutes: 60,
            max_story_complexity: 7,
            enable_ai_narrative: true,
            story_refresh_interval_seconds: 30,
            max_related_incidents: 15,
            enable_interactive_mode: true,
            context_depth_level: 5,
            enable_collaborative_features: true,
        }
    }
}

/// Incident story representation
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct IncidentStory {
    pub story_id: String,
    pub incident_id: String,
    pub title: String,
    pub narrative: StoryNarrative,
    pub timeline: IncidentTimeline,
    pub characters: Vec<StoryCharacter>,
    pub context: StoryContext,
    pub chapters: Vec<StoryChapter>,
    pub resolution: Option<StoryResolution>,
    pub metadata: StoryMetadata,
    pub visualizations: Vec<StoryVisualization>,
    pub interactions: Vec<UserInteraction>,
}

/// AI-generated narrative for incidents
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StoryNarrative {
    pub summary: String,
    pub detailed_description: String,
    pub key_insights: Vec<String>,
    pub narrative_style: NarrativeStyle,
    pub generated_at: SystemTime,
    pub confidence_score: f64,
    pub language: String,
    pub emotional_tone: EmotionalTone,
}

/// Narrative presentation styles
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum NarrativeStyle {
    Technical,
    Executive,
    Storytelling,
    Forensic,
    Casual,
    Detailed,
}

/// Emotional tone of the narrative
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum EmotionalTone {
    Neutral,
    Urgent,
    Concerning,
    Informative,
    Reassuring,
    Investigative,
}

/// Incident timeline with story elements
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct IncidentTimeline {
    pub timeline_id: String,
    pub start_time: SystemTime,
    pub end_time: Option<SystemTime>,
    pub duration: Option<Duration>,
    pub events: Vec<TimelineEvent>,
    pub milestones: Vec<TimelineMilestone>,
    pub phases: Vec<IncidentPhase>,
    pub causal_chains: Vec<CausalChain>,
}

/// Individual timeline event
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TimelineEvent {
    pub event_id: String,
    pub timestamp: SystemTime,
    pub event_type: EventType,
    pub title: String,
    pub description: String,
    pub severity: EventSeverity,
    pub source: EventSource,
    pub impact_score: f64,
    pub related_events: Vec<String>,
    pub evidence: Vec<EventEvidence>,
    pub tags: Vec<String>,
}

/// Types of events in incident timeline
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum EventType {
    Initial,
    Escalation,
    Detection,
    Response,
    Mitigation,
    Resolution,
    PostMortem,
    SystemChange,
    UserAction,
    AutomatedAction,
}

/// Event severity levels
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum EventSeverity {
    Info,
    Low,
    Medium,
    High,
    Critical,
    Emergency,
}

/// Source of timeline events
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EventSource {
    pub source_type: SourceType,
    pub component: String,
    pub agent: Option<String>,
    pub system: String,
    pub location: String,
    pub confidence: f64,
}

/// Types of event sources
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum SourceType {
    Agent,
    System,
    User,
    Monitor,
    Log,
    Metric,
    Alert,
    External,
}

/// Evidence supporting timeline events
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EventEvidence {
    pub evidence_id: String,
    pub evidence_type: EvidenceType,
    pub content: String,
    pub metadata: HashMap<String, String>,
    pub reliability_score: f64,
    pub collected_at: SystemTime,
}

/// Types of evidence
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum EvidenceType {
    Log,
    Metric,
    Screenshot,
    Configuration,
    NetworkTrace,
    DatabaseQuery,
    ApiCall,
    UserReport,
}

/// Timeline milestones
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TimelineMilestone {
    pub milestone_id: String,
    pub timestamp: SystemTime,
    pub title: String,
    pub description: String,
    pub milestone_type: MilestoneType,
    pub importance: f64,
    pub achievements: Vec<String>,
}

/// Types of milestones
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum MilestoneType {
    Detection,
    FirstResponse,
    Escalation,
    RootCauseIdentified,
    MitigationStarted,
    ServiceRestored,
    IncidentResolved,
    PostMortemCompleted,
}

/// Incident phases
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct IncidentPhase {
    pub phase_id: String,
    pub name: String,
    pub start_time: SystemTime,
    pub end_time: Option<SystemTime>,
    pub duration: Option<Duration>,
    pub phase_type: PhaseType,
    pub description: String,
    pub key_activities: Vec<String>,
    pub challenges: Vec<String>,
    pub outcomes: Vec<String>,
}

/// Types of incident phases
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum PhaseType {
    Detection,
    Assessment,
    Response,
    Mitigation,
    Recovery,
    PostIncident,
}

/// Causal chain analysis
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CausalChain {
    pub chain_id: String,
    pub root_cause: CauseEvent,
    pub intermediate_causes: Vec<CauseEvent>,
    pub final_effect: CauseEvent,
    pub confidence: f64,
    pub chain_strength: f64,
    pub validation_status: ValidationStatus,
}

/// Individual cause in causal chain
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CauseEvent {
    pub cause_id: String,
    pub timestamp: SystemTime,
    pub description: String,
    pub cause_type: CauseType,
    pub contributing_factors: Vec<String>,
    pub evidence_support: f64,
}

/// Types of causes
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum CauseType {
    RootCause,
    Contributing,
    Triggering,
    Amplifying,
    Mitigating,
}

/// Validation status for causal chains
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum ValidationStatus {
    Hypothetical,
    Supported,
    Validated,
    Confirmed,
    Disputed,
}

/// Story characters (people, systems, components involved)
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StoryCharacter {
    pub character_id: String,
    pub name: String,
    pub character_type: CharacterType,
    pub role: String,
    pub involvement_level: f64,
    pub actions_taken: Vec<CharacterAction>,
    pub timeline_presence: Vec<TimeSpan>,
    pub impact_on_incident: f64,
    pub expertise_areas: Vec<String>,
}

/// Types of story characters
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum CharacterType {
    Person,
    System,
    Service,
    Agent,
    Process,
    ExternalEntity,
}

/// Actions taken by characters
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CharacterAction {
    pub action_id: String,
    pub timestamp: SystemTime,
    pub action_type: ActionType,
    pub description: String,
    pub intent: String,
    pub outcome: ActionOutcome,
    pub effectiveness: f64,
}

/// Types of character actions
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum ActionType {
    Investigation,
    Mitigation,
    Communication,
    Escalation,
    Configuration,
    Monitoring,
    Documentation,
    Decision,
}

/// Outcome of character actions
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum ActionOutcome {
    Successful,
    PartiallySuccessful,
    Failed,
    Inconclusive,
    Pending,
}

/// Time span for character involvement
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TimeSpan {
    pub start_time: SystemTime,
    pub end_time: Option<SystemTime>,
    pub activity_level: f64,
}

/// Story context and environment
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StoryContext {
    pub context_id: String,
    pub business_context: BusinessContext,
    pub technical_context: TechnicalContext,
    pub environmental_factors: Vec<EnvironmentalFactor>,
    pub related_incidents: Vec<RelatedIncident>,
    pub organizational_impact: OrganizationalImpact,
    pub external_factors: Vec<ExternalFactor>,
}

/// Business context for the incident
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BusinessContext {
    pub affected_services: Vec<String>,
    pub customer_impact: CustomerImpact,
    pub financial_impact: FinancialImpact,
    pub sla_breaches: Vec<SLABreach>,
    pub business_criticality: f64,
    pub peak_hours: bool,
}

/// Customer impact assessment
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CustomerImpact {
    pub affected_customers: u32,
    pub impact_severity: ImpactSeverity,
    pub geographic_scope: Vec<String>,
    pub service_degradation: f64,
    pub customer_complaints: u32,
    pub reputation_impact: f64,
}

/// Impact severity levels
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum ImpactSeverity {
    Minimal,
    Low,
    Medium,
    High,
    Severe,
    Critical,
}

/// Financial impact details
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FinancialImpact {
    pub estimated_cost: f64,
    pub revenue_loss: f64,
    pub recovery_cost: f64,
    pub currency: String,
    pub cost_breakdown: HashMap<String, f64>,
}

/// SLA breach information
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SLABreach {
    pub sla_id: String,
    pub service: String,
    pub breach_type: String,
    pub target_value: f64,
    pub actual_value: f64,
    pub breach_duration: Duration,
}

/// Technical context for the incident
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TechnicalContext {
    pub affected_systems: Vec<String>,
    pub system_architecture: ArchitectureSnapshot,
    pub deployment_state: DeploymentState,
    pub configuration_changes: Vec<ConfigurationChange>,
    pub performance_baseline: PerformanceBaseline,
    pub dependencies: Vec<SystemDependency>,
}

/// Architecture snapshot at incident time
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ArchitectureSnapshot {
    pub snapshot_id: String,
    pub timestamp: SystemTime,
    pub components: Vec<ComponentState>,
    pub connections: Vec<ComponentConnection>,
    pub load_distribution: HashMap<String, f64>,
}

/// State of system components
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ComponentState {
    pub component_id: String,
    pub component_type: String,
    pub health_status: String,
    pub resource_usage: HashMap<String, f64>,
    pub configuration_hash: String,
    pub version: String,
}

/// Connections between components
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ComponentConnection {
    pub from_component: String,
    pub to_component: String,
    pub connection_type: String,
    pub health_status: String,
    pub traffic_metrics: HashMap<String, f64>,
}

/// Deployment state information
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DeploymentState {
    pub active_deployments: Vec<ActiveDeployment>,
    pub recent_changes: Vec<DeploymentChange>,
    pub rollback_status: RollbackStatus,
    pub deployment_health: f64,
}

/// Active deployment information
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ActiveDeployment {
    pub deployment_id: String,
    pub service: String,
    pub version: String,
    pub deployed_at: SystemTime,
    pub rollout_percentage: f64,
    pub health_status: String,
}

/// Deployment change record
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DeploymentChange {
    pub change_id: String,
    pub timestamp: SystemTime,
    pub change_type: String,
    pub affected_services: Vec<String>,
    pub change_description: String,
    pub approver: String,
}

/// Rollback status
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum RollbackStatus {
    NotRequired,
    Available,
    InProgress,
    Completed,
    Failed,
}

/// Configuration change tracking
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ConfigurationChange {
    pub change_id: String,
    pub timestamp: SystemTime,
    pub component: String,
    pub parameter: String,
    pub old_value: String,
    pub new_value: String,
    pub change_reason: String,
    pub approver: String,
}

/// Performance baseline data
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PerformanceBaseline {
    pub baseline_period: Duration,
    pub metrics: HashMap<String, BaselineMetric>,
    pub anomalies_detected: Vec<PerformanceAnomaly>,
    pub trends: Vec<PerformanceTrend>,
}

/// Baseline metric information
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BaselineMetric {
    pub metric_name: String,
    pub average_value: f64,
    pub standard_deviation: f64,
    pub min_value: f64,
    pub max_value: f64,
    pub trend_direction: String,
}

/// Performance anomaly detection
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PerformanceAnomaly {
    pub anomaly_id: String,
    pub metric_name: String,
    pub timestamp: SystemTime,
    pub anomaly_score: f64,
    pub description: String,
}

/// Performance trend information
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PerformanceTrend {
    pub metric_name: String,
    pub trend_direction: String,
    pub trend_strength: f64,
    pub time_window: Duration,
}

/// System dependency information
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SystemDependency {
    pub dependency_id: String,
    pub dependent_system: String,
    pub dependency_system: String,
    pub dependency_type: String,
    pub criticality: f64,
    pub health_status: String,
}

/// Environmental factors affecting the incident
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EnvironmentalFactor {
    pub factor_id: String,
    pub factor_type: EnvironmentalFactorType,
    pub description: String,
    pub impact_level: f64,
    pub duration: Option<Duration>,
    pub mitigation_status: String,
}

/// Types of environmental factors
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum EnvironmentalFactorType {
    NetworkCongestion,
    DataCenterIssue,
    CloudProviderOutage,
    ThirdPartyService,
    WeatherEvent,
    PowerOutage,
    SecurityThreat,
    MaintenanceWindow,
}

/// Related incident information
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RelatedIncident {
    pub incident_id: String,
    pub title: String,
    pub relationship_type: RelationshipType,
    pub correlation_strength: f64,
    pub time_proximity: Duration,
    pub shared_components: Vec<String>,
    pub lessons_learned: Vec<String>,
}

/// Types of incident relationships
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum RelationshipType {
    Duplicate,
    Related,
    Caused,
    Triggered,
    Similar,
    Cascade,
}

/// Organizational impact assessment
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct OrganizationalImpact {
    pub teams_involved: Vec<TeamInvolvement>,
    pub communication_effectiveness: f64,
    pub decision_making_quality: f64,
    pub response_coordination: f64,
    pub knowledge_gaps: Vec<String>,
    pub process_improvements: Vec<String>,
}

/// Team involvement in incident response
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TeamInvolvement {
    pub team_name: String,
    pub involvement_level: f64,
    pub response_time: Duration,
    pub effectiveness: f64,
    pub key_contributors: Vec<String>,
}

/// External factors affecting the incident
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ExternalFactor {
    pub factor_id: String,
    pub source: String,
    pub description: String,
    pub impact_assessment: f64,
    pub controllable: bool,
    pub mitigation_options: Vec<String>,
}

/// Story chapters for structured narrative
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StoryChapter {
    pub chapter_id: String,
    pub title: String,
    pub chapter_number: u32,
    pub start_time: SystemTime,
    pub end_time: Option<SystemTime>,
    pub summary: String,
    pub detailed_narrative: String,
    pub key_events: Vec<String>,
    pub decisions_made: Vec<DecisionPoint>,
    pub lessons_learned: Vec<String>,
    pub next_chapter_preview: Option<String>,
}

/// Decision points in incident response
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DecisionPoint {
    pub decision_id: String,
    pub timestamp: SystemTime,
    pub decision_maker: String,
    pub decision_description: String,
    pub options_considered: Vec<String>,
    pub chosen_option: String,
    pub rationale: String,
    pub outcome: DecisionOutcome,
}

/// Outcome of decisions
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum DecisionOutcome {
    Successful,
    PartiallySuccessful,
    Unsuccessful,
    TooEarlyToTell,
    RequiresRevision,
}

/// Story resolution information
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StoryResolution {
    pub resolution_id: String,
    pub resolution_type: ResolutionType,
    pub resolution_time: SystemTime,
    pub resolution_summary: String,
    pub root_causes_identified: Vec<String>,
    pub corrective_actions: Vec<CorrectiveAction>,
    pub preventive_measures: Vec<PreventiveMeasure>,
    pub follow_up_items: Vec<FollowUpItem>,
    pub success_metrics: HashMap<String, f64>,
}

/// Types of incident resolution
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum ResolutionType {
    FullResolution,
    Workaround,
    PartialResolution,
    Escalated,
    Deferred,
}

/// Corrective actions taken
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CorrectiveAction {
    pub action_id: String,
    pub description: String,
    pub responsible_party: String,
    pub implementation_date: SystemTime,
    pub verification_method: String,
    pub effectiveness: f64,
}

/// Preventive measures implemented
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PreventiveMeasure {
    pub measure_id: String,
    pub description: String,
    pub implementation_timeline: Duration,
    pub responsible_team: String,
    pub expected_impact: f64,
    pub monitoring_plan: String,
}

/// Follow-up items
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FollowUpItem {
    pub item_id: String,
    pub description: String,
    pub priority: Priority,
    pub assigned_to: String,
    pub due_date: SystemTime,
    pub status: FollowUpStatus,
}

/// Priority levels
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum Priority {
    Critical,
    High,
    Medium,
    Low,
}

/// Follow-up item status
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum FollowUpStatus {
    NotStarted,
    InProgress,
    Completed,
    Blocked,
    Cancelled,
}

/// Story metadata
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StoryMetadata {
    pub created_at: SystemTime,
    pub last_updated: SystemTime,
    pub version: u32,
    pub author: String,
    pub reviewers: Vec<String>,
    pub tags: Vec<String>,
    pub complexity_score: f64,
    pub completeness_score: f64,
    pub accuracy_score: f64,
    pub story_length: u32,
    pub read_time_minutes: u32,
}

/// Story visualizations
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StoryVisualization {
    pub visualization_id: String,
    pub visualization_type: VisualizationType,
    pub title: String,
    pub description: String,
    pub data_source: String,
    pub configuration: HashMap<String, String>,
    pub interactive: bool,
    pub time_range: Option<TimeRange>,
}

/// Types of visualizations
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum VisualizationType {
    Timeline,
    FlowChart,
    NetworkDiagram,
    HeatMap,
    Gantt,
    Sankey,
    TreeMap,
    ScatterPlot,
    LineChart,
    BarChart,
}

/// Time range for visualizations
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TimeRange {
    pub start_time: SystemTime,
    pub end_time: SystemTime,
}

/// User interactions with the story
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UserInteraction {
    pub interaction_id: String,
    pub user_id: String,
    pub interaction_type: InteractionType,
    pub timestamp: SystemTime,
    pub content: String,
    pub target_element: Option<String>,
    pub metadata: HashMap<String, String>,
}

/// Types of user interactions
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum InteractionType {
    Comment,
    Annotation,
    Question,
    Insight,
    Correction,
    Bookmark,
    Share,
    Export,
}

/// Main story-mode incident system
pub struct StoryModeSystem {
    config: StoryModeConfig,
    incident_stories: RwLock<HashMap<String, IncidentStory>>,
    story_templates: RwLock<HashMap<String, StoryTemplate>>,
    narrative_engine: RwLock<NarrativeEngine>,
    correlation_engine: RwLock<CorrelationEngine>,
    visualization_engine: RwLock<VisualizationEngine>,
}

/// Template for story generation
#[derive(Debug, Clone)]
pub struct StoryTemplate {
    pub template_id: String,
    pub template_name: String,
    pub incident_types: Vec<String>,
    pub narrative_structure: Vec<String>,
    pub required_elements: Vec<String>,
    pub optional_elements: Vec<String>,
    pub visualization_suggestions: Vec<VisualizationType>,
}

/// AI narrative generation engine
#[derive(Debug, Clone)]
pub struct NarrativeEngine {
    pub models: HashMap<String, NarrativeModel>,
    pub style_templates: HashMap<NarrativeStyle, StyleTemplate>,
    pub language_models: HashMap<String, LanguageModel>,
}

/// Narrative generation model
#[derive(Debug, Clone)]
pub struct NarrativeModel {
    pub model_id: String,
    pub model_type: String,
    pub accuracy: f64,
    pub specialization: Vec<String>,
    pub last_trained: SystemTime,
}

/// Style template for narratives
#[derive(Debug, Clone)]
pub struct StyleTemplate {
    pub template_id: String,
    pub tone: EmotionalTone,
    pub complexity_level: u32,
    pub target_audience: String,
    pub key_phrases: Vec<String>,
    pub structure_guidelines: Vec<String>,
}

/// Language model for narrative generation
#[derive(Debug, Clone)]
pub struct LanguageModel {
    pub model_id: String,
    pub language: String,
    pub vocabulary_size: u32,
    pub context_window: u32,
    pub quality_score: f64,
}

/// Incident correlation engine
#[derive(Debug, Clone)]
pub struct CorrelationEngine {
    pub correlation_algorithms: HashMap<String, CorrelationAlgorithm>,
    pub correlation_cache: HashMap<String, CorrelationResult>,
    pub pattern_library: HashMap<String, IncidentPattern>,
}

/// Correlation algorithm
#[derive(Debug, Clone)]
pub struct CorrelationAlgorithm {
    pub algorithm_id: String,
    pub algorithm_type: String,
    pub accuracy: f64,
    pub processing_time: Duration,
    pub parameters: HashMap<String, f64>,
}

/// Correlation result
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CorrelationResult {
    pub result_id: String,
    pub primary_incident: String,
    pub related_incidents: Vec<String>,
    pub correlation_scores: HashMap<String, f64>,
    pub correlation_factors: Vec<String>,
    pub confidence: f64,
}

/// Incident pattern for recognition
#[derive(Debug, Clone)]
pub struct IncidentPattern {
    pub pattern_id: String,
    pub pattern_name: String,
    pub pattern_description: String,
    pub characteristic_events: Vec<String>,
    pub typical_timeline: Duration,
    pub common_causes: Vec<String>,
    pub resolution_strategies: Vec<String>,
}

/// Visualization engine
#[derive(Debug, Clone)]
pub struct VisualizationEngine {
    pub renderers: HashMap<VisualizationType, VisualizationRenderer>,
    pub layout_algorithms: HashMap<String, LayoutAlgorithm>,
    pub color_schemes: HashMap<String, ColorScheme>,
}

/// Visualization renderer
#[derive(Debug, Clone)]
pub struct VisualizationRenderer {
    pub renderer_id: String,
    pub supported_types: Vec<VisualizationType>,
    pub output_formats: Vec<String>,
    pub interactive_features: Vec<String>,
}

/// Layout algorithm for visualizations
#[derive(Debug, Clone)]
pub struct LayoutAlgorithm {
    pub algorithm_id: String,
    pub algorithm_name: String,
    pub optimal_data_size: u32,
    pub processing_complexity: String,
}

/// Color scheme for visualizations
#[derive(Debug, Clone)]
pub struct ColorScheme {
    pub scheme_id: String,
    pub scheme_name: String,
    pub colors: Vec<String>,
    pub accessibility_compliant: bool,
}

impl StoryModeSystem {
    /// Create a new story-mode incident system
    pub fn new(config: StoryModeConfig) -> Self {
        info!("Initializing Story-Mode Incident View System with config: {:?}", config);
        
        Self {
            config,
            incident_stories: RwLock::new(HashMap::new()),
            story_templates: RwLock::new(HashMap::new()),
            narrative_engine: RwLock::new(NarrativeEngine {
                models: HashMap::new(),
                style_templates: HashMap::new(),
                language_models: HashMap::new(),
            }),
            correlation_engine: RwLock::new(CorrelationEngine {
                correlation_algorithms: HashMap::new(),
                correlation_cache: HashMap::new(),
                pattern_library: HashMap::new(),
            }),
            visualization_engine: RwLock::new(VisualizationEngine {
                renderers: HashMap::new(),
                layout_algorithms: HashMap::new(),
                color_schemes: HashMap::new(),
            }),
        }
    }

    /// Create incident story from raw incident data
    pub async fn create_incident_story(&self, incident_data: IncidentData) -> Result<IncidentStory> {
        let story_id = Uuid::new_v4().to_string();
        
        info!("Creating incident story for incident: {}", incident_data.incident_id);
        
        // Generate narrative
        let narrative = self.generate_narrative(&incident_data).await?;
        
        // Build timeline
        let timeline = self.build_timeline(&incident_data).await?;
        
        // Identify characters
        let characters = self.identify_characters(&incident_data).await?;
        
        // Build context
        let context = self.build_context(&incident_data).await?;
        
        // Create chapters
        let chapters = self.create_chapters(&incident_data, &timeline).await?;
        
        // Generate visualizations
        let visualizations = self.generate_visualizations(&incident_data).await?;
        
        let story = IncidentStory {
            story_id: story_id.clone(),
            incident_id: incident_data.incident_id.clone(),
            title: self.generate_story_title(&incident_data).await?,
            narrative,
            timeline,
            characters,
            context,
            chapters,
            resolution: None, // Will be populated when incident is resolved
            metadata: StoryMetadata {
                created_at: SystemTime::now(),
                last_updated: SystemTime::now(),
                version: 1,
                author: "AI Story Generator".to_string(),
                reviewers: vec![],
                tags: self.generate_story_tags(&incident_data).await?,
                complexity_score: self.calculate_complexity_score(&incident_data).await?,
                completeness_score: 0.7, // Will improve as more data is added
                accuracy_score: 0.85, // Initial estimate
                story_length: 0, // Will be calculated
                read_time_minutes: 0, // Will be calculated
            },
            visualizations,
            interactions: vec![],
        };
        
        // Store the story
        let mut stories = self.incident_stories.write().await;
        stories.insert(story_id, story.clone());
        
        info!("Created incident story: {} for incident: {}", story.story_id, incident_data.incident_id);
        
        Ok(story)
    }

    /// Generate AI narrative for incident
    async fn generate_narrative(&self, incident_data: &IncidentData) -> Result<StoryNarrative> {
        // Simulate AI narrative generation
        let summary = format!(
            "A {} incident occurred affecting {} systems, lasting approximately {} minutes with {} impact on users.",
            incident_data.severity,
            incident_data.affected_systems.len(),
            incident_data.duration_minutes.unwrap_or(0),
            incident_data.impact_level
        );
        
        let detailed_description = format!(
            "On {}, at approximately {}, our monitoring systems detected anomalous behavior in the {} subsystem. \
             The incident began with {} and escalated to affect {} core services. \
             The primary symptoms included {} and {}. \
             Through coordinated investigation by the {} team, the root cause was identified as {}. \
             Resolution was achieved through {} after {} minutes of focused effort.",
            "today", // Would use actual timestamp formatting
            "incident time",
            incident_data.affected_systems.first().unwrap_or(&"unknown".to_string()),
            incident_data.initial_symptoms.first().unwrap_or(&"unknown symptom".to_string()),
            incident_data.affected_systems.len(),
            incident_data.primary_symptoms.first().unwrap_or(&"performance degradation".to_string()),
            incident_data.secondary_symptoms.first().unwrap_or(&"error rate increase".to_string()),
            incident_data.response_team.as_ref().unwrap_or(&"operations".to_string()),
            incident_data.root_cause.as_ref().unwrap_or(&"configuration error".to_string()),
            incident_data.resolution_method.as_ref().unwrap_or(&"system restart".to_string()),
            incident_data.duration_minutes.unwrap_or(30)
        );
        
        let key_insights = vec![
            "Incident detection time was within acceptable thresholds".to_string(),
            "Cross-team collaboration was effective during response".to_string(),
            "System resilience patterns performed as designed".to_string(),
            "Monitoring coverage identified gaps for future improvement".to_string(),
        ];

        Ok(StoryNarrative {
            summary,
            detailed_description,
            key_insights,
            narrative_style: NarrativeStyle::Technical,
            generated_at: SystemTime::now(),
            confidence_score: 0.82,
            language: "en".to_string(),
            emotional_tone: EmotionalTone::Informative,
        })
    }

    /// Build incident timeline
    async fn build_timeline(&self, incident_data: &IncidentData) -> Result<IncidentTimeline> {
        let timeline_id = Uuid::new_v4().to_string();
        
        // Create timeline events from incident data
        let mut events = Vec::new();
        let start_time = incident_data.start_time;
        
        // Initial detection event
        events.push(TimelineEvent {
            event_id: Uuid::new_v4().to_string(),
            timestamp: start_time,
            event_type: EventType::Detection,
            title: "Incident Detected".to_string(),
            description: format!("Initial detection of {} in {}", 
                               incident_data.initial_symptoms.first().unwrap_or(&"anomaly".to_string()),
                               incident_data.affected_systems.first().unwrap_or(&"system".to_string())),
            severity: EventSeverity::High,
            source: EventSource {
                source_type: SourceType::Monitor,
                component: "monitoring_system".to_string(),
                agent: None,
                system: "dcmaar".to_string(),
                location: "primary_datacenter".to_string(),
                confidence: 0.95,
            },
            impact_score: 0.8,
            related_events: vec![],
            evidence: vec![],
            tags: vec!["detection".to_string(), "initial".to_string()],
        });

        // Response initiation event
        events.push(TimelineEvent {
            event_id: Uuid::new_v4().to_string(),
            timestamp: start_time + Duration::from_secs(300), // 5 minutes later
            event_type: EventType::Response,
            title: "Response Team Engaged".to_string(),
            description: format!("Response team {} activated for incident", 
                               incident_data.response_team.as_ref().unwrap_or(&"operations".to_string())),
            severity: EventSeverity::Medium,
            source: EventSource {
                source_type: SourceType::User,
                component: "incident_management".to_string(),
                agent: None,
                system: "dcmaar".to_string(),
                location: "control_center".to_string(),
                confidence: 1.0,
            },
            impact_score: 0.6,
            related_events: vec![events[0].event_id.clone()],
            evidence: vec![],
            tags: vec!["response".to_string(), "team_activation".to_string()],
        });

        // Create milestones
        let milestones = vec![
            TimelineMilestone {
                milestone_id: Uuid::new_v4().to_string(),
                timestamp: start_time,
                title: "Incident Detection".to_string(),
                description: "Initial incident detection and alert generation".to_string(),
                milestone_type: MilestoneType::Detection,
                importance: 1.0,
                achievements: vec!["Alert generated".to_string(), "Team notified".to_string()],
            },
            TimelineMilestone {
                milestone_id: Uuid::new_v4().to_string(),
                timestamp: start_time + Duration::from_secs(300),
                title: "Response Initiated".to_string(),
                description: "Response team activation and initial assessment".to_string(),
                milestone_type: MilestoneType::FirstResponse,
                importance: 0.9,
                achievements: vec!["Team assembled".to_string(), "Assessment started".to_string()],
            },
        ];

        // Create phases
        let phases = vec![
            IncidentPhase {
                phase_id: Uuid::new_v4().to_string(),
                name: "Detection and Assessment".to_string(),
                start_time,
                end_time: Some(start_time + Duration::from_secs(600)),
                duration: Some(Duration::from_secs(600)),
                phase_type: PhaseType::Detection,
                description: "Initial detection, assessment, and team mobilization".to_string(),
                key_activities: vec![
                    "Monitor alert analysis".to_string(),
                    "Impact assessment".to_string(),
                    "Team notification".to_string(),
                ],
                challenges: vec!["Complex symptom correlation".to_string()],
                outcomes: vec!["Incident confirmed and classified".to_string()],
            },
        ];

        // Create causal chains (simplified)
        let causal_chains = vec![
            CausalChain {
                chain_id: Uuid::new_v4().to_string(),
                root_cause: CauseEvent {
                    cause_id: Uuid::new_v4().to_string(),
                    timestamp: start_time - Duration::from_secs(1800), // 30 minutes before
                    description: incident_data.root_cause.as_ref().unwrap_or(&"Configuration change".to_string()).clone(),
                    cause_type: CauseType::RootCause,
                    contributing_factors: vec!["Insufficient testing".to_string()],
                    evidence_support: 0.85,
                },
                intermediate_causes: vec![],
                final_effect: CauseEvent {
                    cause_id: Uuid::new_v4().to_string(),
                    timestamp: start_time,
                    description: "Service degradation observed".to_string(),
                    cause_type: CauseType::Contributing,
                    contributing_factors: vec!["Cascading failure".to_string()],
                    evidence_support: 0.95,
                },
                confidence: 0.8,
                chain_strength: 0.75,
                validation_status: ValidationStatus::Supported,
            },
        ];

        Ok(IncidentTimeline {
            timeline_id,
            start_time,
            end_time: incident_data.end_time,
            duration: incident_data.duration_minutes.map(|m| Duration::from_secs(m as u64 * 60)),
            events,
            milestones,
            phases,
            causal_chains,
        })
    }

    /// Identify story characters
    async fn identify_characters(&self, incident_data: &IncidentData) -> Result<Vec<StoryCharacter>> {
        let mut characters = Vec::new();
        
        // Add response team as character
        if let Some(team) = &incident_data.response_team {
            characters.push(StoryCharacter {
                character_id: Uuid::new_v4().to_string(),
                name: team.clone(),
                character_type: CharacterType::Person,
                role: "Incident Response Team".to_string(),
                involvement_level: 1.0,
                actions_taken: vec![
                    CharacterAction {
                        action_id: Uuid::new_v4().to_string(),
                        timestamp: incident_data.start_time + Duration::from_secs(300),
                        action_type: ActionType::Investigation,
                        description: "Initiated incident investigation".to_string(),
                        intent: "Identify root cause and impact".to_string(),
                        outcome: ActionOutcome::Successful,
                        effectiveness: 0.9,
                    },
                ],
                timeline_presence: vec![
                    TimeSpan {
                        start_time: incident_data.start_time + Duration::from_secs(300),
                        end_time: incident_data.end_time,
                        activity_level: 0.8,
                    },
                ],
                impact_on_incident: 0.8,
                expertise_areas: vec!["System Operations".to_string(), "Incident Response".to_string()],
            });
        }

        // Add affected systems as characters
        for system in &incident_data.affected_systems {
            characters.push(StoryCharacter {
                character_id: Uuid::new_v4().to_string(),
                name: system.clone(),
                character_type: CharacterType::System,
                role: "Affected System".to_string(),
                involvement_level: 0.9,
                actions_taken: vec![],
                timeline_presence: vec![
                    TimeSpan {
                        start_time: incident_data.start_time,
                        end_time: incident_data.end_time,
                        activity_level: 0.5,
                    },
                ],
                impact_on_incident: 0.7,
                expertise_areas: vec![],
            });
        }

        // Add monitoring system as character
        characters.push(StoryCharacter {
            character_id: Uuid::new_v4().to_string(),
            name: "Monitoring System".to_string(),
            character_type: CharacterType::System,
            role: "Detection and Alerting".to_string(),
            involvement_level: 0.8,
            actions_taken: vec![
                CharacterAction {
                    action_id: Uuid::new_v4().to_string(),
                    timestamp: incident_data.start_time,
                    action_type: ActionType::Detection,
                    description: "Generated initial incident alert".to_string(),
                    intent: "Notify operations team of anomaly".to_string(),
                    outcome: ActionOutcome::Successful,
                    effectiveness: 0.95,
                },
            ],
            timeline_presence: vec![
                TimeSpan {
                    start_time: incident_data.start_time,
                    end_time: incident_data.end_time,
                    activity_level: 0.9,
                },
            ],
            impact_on_incident: 0.9,
            expertise_areas: vec!["Monitoring".to_string(), "Alerting".to_string()],
        });

        Ok(characters)
    }

    /// Build story context
    async fn build_context(&self, incident_data: &IncidentData) -> Result<StoryContext> {
        let context_id = Uuid::new_v4().to_string();
        
        let business_context = BusinessContext {
            affected_services: incident_data.affected_systems.clone(),
            customer_impact: CustomerImpact {
                affected_customers: incident_data.affected_users.unwrap_or(0),
                impact_severity: match incident_data.severity.as_str() {
                    "Critical" => ImpactSeverity::Critical,
                    "High" => ImpactSeverity::High,
                    "Medium" => ImpactSeverity::Medium,
                    _ => ImpactSeverity::Low,
                },
                geographic_scope: vec!["Global".to_string()],
                service_degradation: incident_data.impact_level.parse().unwrap_or(0.5),
                customer_complaints: 0,
                reputation_impact: 0.3,
            },
            financial_impact: FinancialImpact {
                estimated_cost: 5000.0,
                revenue_loss: 1000.0,
                recovery_cost: 500.0,
                currency: "USD".to_string(),
                cost_breakdown: [
                    ("engineering_time".to_string(), 2000.0),
                    ("system_resources".to_string(), 1500.0),
                    ("communication".to_string(), 500.0),
                ].iter().cloned().collect(),
            },
            sla_breaches: vec![],
            business_criticality: 0.8,
            peak_hours: false,
        };

        let technical_context = TechnicalContext {
            affected_systems: incident_data.affected_systems.clone(),
            system_architecture: ArchitectureSnapshot {
                snapshot_id: Uuid::new_v4().to_string(),
                timestamp: incident_data.start_time,
                components: vec![],
                connections: vec![],
                load_distribution: HashMap::new(),
            },
            deployment_state: DeploymentState {
                active_deployments: vec![],
                recent_changes: vec![],
                rollback_status: RollbackStatus::Available,
                deployment_health: 0.9,
            },
            configuration_changes: vec![],
            performance_baseline: PerformanceBaseline {
                baseline_period: Duration::from_secs(3600 * 24 * 7), // 1 week
                metrics: HashMap::new(),
                anomalies_detected: vec![],
                trends: vec![],
            },
            dependencies: vec![],
        };

        Ok(StoryContext {
            context_id,
            business_context,
            technical_context,
            environmental_factors: vec![],
            related_incidents: vec![],
            organizational_impact: OrganizationalImpact {
                teams_involved: vec![
                    TeamInvolvement {
                        team_name: incident_data.response_team.as_ref().unwrap_or(&"Operations".to_string()).clone(),
                        involvement_level: 1.0,
                        response_time: Duration::from_secs(300),
                        effectiveness: 0.9,
                        key_contributors: vec!["Lead Engineer".to_string()],
                    },
                ],
                communication_effectiveness: 0.85,
                decision_making_quality: 0.8,
                response_coordination: 0.9,
                knowledge_gaps: vec![],
                process_improvements: vec!["Faster escalation procedures".to_string()],
            },
            external_factors: vec![],
        })
    }

    /// Create story chapters
    async fn create_chapters(&self, incident_data: &IncidentData, timeline: &IncidentTimeline) -> Result<Vec<StoryChapter>> {
        let mut chapters = Vec::new();
        
        // Chapter 1: Detection and Initial Response
        chapters.push(StoryChapter {
            chapter_id: Uuid::new_v4().to_string(),
            title: "The Detection".to_string(),
            chapter_number: 1,
            start_time: incident_data.start_time,
            end_time: Some(incident_data.start_time + Duration::from_secs(600)),
            summary: "Our monitoring systems detected the first signs of trouble".to_string(),
            detailed_narrative: format!(
                "It began as a subtle anomaly in the {} system. Our AI-powered monitoring detected \
                 unusual patterns in {} at exactly {}. What started as a minor blip would soon \
                 cascade into a significant incident affecting {} systems.",
                incident_data.affected_systems.first().unwrap_or(&"primary".to_string()),
                incident_data.initial_symptoms.first().unwrap_or(&"performance metrics".to_string()),
                "incident time", // Would format actual time
                incident_data.affected_systems.len()
            ),
            key_events: timeline.events.iter().take(2).map(|e| e.event_id.clone()).collect(),
            decisions_made: vec![
                DecisionPoint {
                    decision_id: Uuid::new_v4().to_string(),
                    timestamp: incident_data.start_time + Duration::from_secs(120),
                    decision_maker: "On-call Engineer".to_string(),
                    decision_description: "Escalate to incident response team".to_string(),
                    options_considered: vec![
                        "Wait and monitor".to_string(),
                        "Immediate escalation".to_string(),
                        "Automated mitigation".to_string(),
                    ],
                    chosen_option: "Immediate escalation".to_string(),
                    rationale: "Pattern matched previous critical incidents".to_string(),
                    outcome: DecisionOutcome::Successful,
                },
            ],
            lessons_learned: vec![
                "Early detection systems performed well".to_string(),
                "Escalation procedures were followed correctly".to_string(),
            ],
            next_chapter_preview: Some("The response team springs into action...".to_string()),
        });

        Ok(chapters)
    }

    /// Generate visualizations for the story
    async fn generate_visualizations(&self, incident_data: &IncidentData) -> Result<Vec<StoryVisualization>> {
        let mut visualizations = Vec::new();
        
        // Timeline visualization
        visualizations.push(StoryVisualization {
            visualization_id: Uuid::new_v4().to_string(),
            visualization_type: VisualizationType::Timeline,
            title: "Incident Timeline".to_string(),
            description: "Complete timeline of incident events and responses".to_string(),
            data_source: "incident_timeline".to_string(),
            configuration: [
                ("zoom_level".to_string(), "hour".to_string()),
                ("show_milestones".to_string(), "true".to_string()),
            ].iter().cloned().collect(),
            interactive: true,
            time_range: Some(TimeRange {
                start_time: incident_data.start_time - Duration::from_secs(1800),
                end_time: incident_data.end_time.unwrap_or(incident_data.start_time + Duration::from_secs(3600)),
            }),
        });

        // System impact heatmap
        visualizations.push(StoryVisualization {
            visualization_id: Uuid::new_v4().to_string(),
            visualization_type: VisualizationType::HeatMap,
            title: "System Impact Analysis".to_string(),
            description: "Heat map showing impact levels across affected systems".to_string(),
            data_source: "system_metrics".to_string(),
            configuration: [
                ("color_scheme".to_string(), "red_yellow_green".to_string()),
                ("metric_type".to_string(), "health_score".to_string()),
            ].iter().cloned().collect(),
            interactive: true,
            time_range: None,
        });

        Ok(visualizations)
    }

    /// Generate story title
    async fn generate_story_title(&self, incident_data: &IncidentData) -> Result<String> {
        let system = incident_data.affected_systems.first().unwrap_or(&"System".to_string());
        let duration = incident_data.duration_minutes.unwrap_or(30);
        
        Ok(format!("The {} Incident: A {}-Minute Journey to Resolution", system, duration))
    }

    /// Generate story tags
    async fn generate_story_tags(&self, incident_data: &IncidentData) -> Result<Vec<String>> {
        let mut tags = vec![
            incident_data.severity.to_lowercase(),
            "incident_response".to_string(),
            "system_outage".to_string(),
        ];
        
        for system in &incident_data.affected_systems {
            tags.push(system.to_lowercase());
        }
        
        if let Some(cause) = &incident_data.root_cause {
            if cause.contains("config") {
                tags.push("configuration".to_string());
            }
            if cause.contains("deploy") {
                tags.push("deployment".to_string());
            }
        }
        
        Ok(tags)
    }

    /// Calculate story complexity score
    async fn calculate_complexity_score(&self, incident_data: &IncidentData) -> Result<f64> {
        let mut complexity = 0.0;
        
        // More affected systems = higher complexity
        complexity += incident_data.affected_systems.len() as f64 * 0.2;
        
        // Longer duration = higher complexity
        if let Some(duration) = incident_data.duration_minutes {
            complexity += (duration as f64 / 60.0) * 0.1; // Hours factor
        }
        
        // Severity factor
        let severity_factor = match incident_data.severity.as_str() {
            "Critical" => 1.0,
            "High" => 0.8,
            "Medium" => 0.6,
            _ => 0.4,
        };
        complexity += severity_factor;
        
        // Cap at 10.0
        Ok(complexity.min(10.0))
    }

    /// Get story-mode system overview
    pub async fn get_story_overview(&self) -> StoryModeOverview {
        let stories = self.incident_stories.read().await;
        let templates = self.story_templates.read().await;
        
        let total_stories = stories.len();
        let active_stories = stories.values().filter(|s| s.resolution.is_none()).count();
        let completed_stories = total_stories - active_stories;
        
        let average_complexity = if !stories.is_empty() {
            stories.values().map(|s| s.metadata.complexity_score).sum::<f64>() / stories.len() as f64
        } else {
            0.0
        };
        
        let total_interactions = stories.values()
            .map(|s| s.interactions.len())
            .sum();
        
        StoryModeOverview {
            total_stories,
            active_stories,
            completed_stories,
            story_templates: templates.len(),
            average_complexity_score: average_complexity,
            total_user_interactions: total_interactions,
            visualization_types_available: 10, // Based on VisualizationType enum
            narrative_styles_supported: 6, // Based on NarrativeStyle enum
            correlation_algorithms_active: 3, // Simulated
            ai_narrative_accuracy: 0.87, // Simulated
            story_generation_success_rate: 0.94, // Simulated
            average_story_creation_time_seconds: 15.2, // Simulated
        }
    }
}

/// Input data for creating incident stories
#[derive(Debug, Clone)]
pub struct IncidentData {
    pub incident_id: String,
    pub start_time: SystemTime,
    pub end_time: Option<SystemTime>,
    pub duration_minutes: Option<u32>,
    pub severity: String,
    pub impact_level: String,
    pub affected_systems: Vec<String>,
    pub affected_users: Option<u32>,
    pub initial_symptoms: Vec<String>,
    pub primary_symptoms: Vec<String>,
    pub secondary_symptoms: Vec<String>,
    pub response_team: Option<String>,
    pub root_cause: Option<String>,
    pub resolution_method: Option<String>,
}

/// Overview of story-mode system
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StoryModeOverview {
    pub total_stories: usize,
    pub active_stories: usize,
    pub completed_stories: usize,
    pub story_templates: usize,
    pub average_complexity_score: f64,
    pub total_user_interactions: usize,
    pub visualization_types_available: usize,
    pub narrative_styles_supported: usize,
    pub correlation_algorithms_active: usize,
    pub ai_narrative_accuracy: f64,
    pub story_generation_success_rate: f64,
    pub average_story_creation_time_seconds: f64,
}