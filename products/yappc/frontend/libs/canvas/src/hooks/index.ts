/**
 * Hooks barrel export
 */

// ============================================================================
// CONSOLIDATED HOOKS (10 logical groups replacing 37+ individual hooks)
// ============================================================================

export {
    useCanvasCore,
    type UseCanvasCoreOptions,
    type UseCanvasCoreReturn,
} from './useCanvasCore';

export {
    useCanvasCollaboration,
    type UseCanvasCollaborationOptions,
    type UseCanvasCollaborationReturn,
} from './useCanvasCollaboration';

export {
    useCanvasAI,
    type UseCanvasAIOptions,
    type UseCanvasAIReturn,
} from './useCanvasAI';

export {
    useCanvasInfrastructure,
    type UseCanvasInfrastructureOptions,
    type UseCanvasInfrastructureReturn,
} from './useCanvasInfrastructure';

export {
    useCanvasSecurity,
    type UseCanvasSecurityOptions,
    type UseCanvasSecurityReturn,
} from './useCanvasSecurity';

export {
    useCanvasTemplates,
    type UseCanvasTemplatesOptions,
    type UseCanvasTemplatesReturn,
} from './useCanvasTemplates';

export {
    useCanvasMobile,
    type UseCanvasMobileOptions,
    type UseCanvasMobileReturn,
} from './useCanvasMobile';

export {
    useCanvasUserJourney,
    type UseCanvasUserJourneyOptions,
    type UseCanvasUserJourneyReturn,
} from './useCanvasUserJourney';

export {
    useCanvasFullStack,
    type UseCanvasFullStackOptions,
    type UseCanvasFullStackReturn,
} from './useCanvasFullStack';

export {
    useCanvasAnalytics,
    type UseCanvasAnalyticsOptions,
    type UseCanvasAnalyticsReturn,
} from './useCanvasAnalytics';

// ============================================================================
// LEGACY INDIVIDUAL HOOKS (kept for backward compatibility)
// ============================================================================

export * from './useTemplateActions';

// Node Grouping Hook (Journey 1.1: PM Handoff Workflow)
export {
    useNodeGrouping,
    type UseNodeGroupingResult,
    type UseNodeGroupingOptions,
} from './useNodeGrouping';

// Test Generation Hook (Journey 4.1: QA - Test Generation)
export {
    useTestGeneration,
    type UseTestGenerationResult,
    type UseTestGenerationOptions,
} from './useTestGeneration';

// AI Brainstorming Hook (Journey 1.1: PM AI Brainstorming)
export {
    useAIBrainstorming,
    type UseAIBrainstormingResult,
    type UseAIBrainstormingOptions,
} from './useAIBrainstorming';

// Code Scaffold Hook (Journey 3.1: Dev - Code Generation)
export {
    useCodeScaffold,
    type UseCodeScaffoldResult,
    type UseCodeScaffoldOptions,
} from './useCodeScaffold';

// Security Monitoring Hook (Cross-cutting: Security Alerts)
export {
    useSecurityMonitoring,
    type UseSecurityMonitoringResult,
    type UseSecurityMonitoringOptions,
    type Vulnerability,
    type VulnerabilitySeverity,
    type NodeVulnerabilityStatus,
    type FixSuggestion,
    type PRGenerationResult,
} from './useSecurityMonitoring';

// OpenAPI Generator Hook (Journey 6.1: Backend Engineer - OpenAPI Generation)
export {
    useOpenAPIGenerator,
    type UseOpenAPIGeneratorResult,
    type UseOpenAPIGeneratorOptions,
} from './useOpenAPIGenerator';

// Full-Stack Mode Hook (Journey 8.1: Full-Stack Developer - Split-Screen Mode)
export {
    useFullStackMode,
    type UseFullStackModeResult,
    type UseFullStackModeOptions,
    type CanvasSide,
    type LayoutMode,
    type DataFlowEdge,
    type CanvasPartition,
    type CrossCanvasValidation,
} from './useFullStackMode';

// Service Health Hook (Journey 13.1: SRE - Real-Time Incident Response)
export {
    useServiceHealth,
    type UseServiceHealthResult,
    type UseServiceHealthOptions,
    type HealthStatus,
    type MetricType,
    type Metric,
    type ServiceHealthData,
    type Alert,
    type SLO,
    type CircuitBreaker,
    type PrometheusConfig,
} from './useServiceHealth';

// Component Generation Hook (Journey 7.1: Frontend Engineer - Component Development)
export {
    useComponentGeneration,
    type UseComponentGenerationResult,
    type UseComponentGenerationOptions,
    type UIFramework,
    type StylingApproach,
    type ComponentGenerationOptions,
    type GeneratedComponent,
} from './useComponentGeneration';

// Mobile Canvas Hook (Journey 9.1: Mobile Engineer - Mobile Screen Design)
export {
    useMobileCanvas,
    type UseMobileCanvasResult,
    type UseMobileCanvasOptions,
    type MobilePlatform,
    type MobileComponentType,
    type MobileComponent,
    type DeviceFrame,
} from './useMobileCanvas';

// Data Pipeline Hook (Journey 10.1: Data Engineer - ETL Pipeline Design)
export {
    useDataPipeline,
    type UseDataPipelineResult,
    type UseDataPipelineOptions,
    type PipelineNodeType,
    type DataSourceType,
    type TransformationType,
    type SinkType,
    type ColumnSchema,
    type SchemaMapping,
    type PipelineNode,
    type PipelineConnection,
    type PipelineNodeConfig,
    type ValidationError,
} from './useDataPipeline';

// User Journey Hook (Journey 19.1: UX Researcher - User Journey Mapping)
export {
    useUserJourney,
    type UseUserJourneyResult,
    type UseUserJourneyOptions,
    type EmotionType,
    type TouchpointType,
    type JourneyTouchpoint,
    type JourneyPainPoint,
    type JourneyEmotion,
    type UserQuote,
    type JourneyStage,
} from './useUserJourney';

// Service Blueprint Hook (Journey 20.1: Service Designer - Service Blueprint)
export {
    useServiceBlueprint,
    type UseServiceBlueprintResult,
    type UseServiceBlueprintOptions,
    type LaneType,
    type Touchpoint,
    type ProcessNode,
    type NodeConnection,
    type BlueprintLane,
} from './useServiceBlueprint';

// Threat Modeling Hook (Journey 11.1: Security Engineer - Threat Modeling)
export {
    useThreatModeling,
    type UseThreatModelingResult,
    type UseThreatModelingOptions,
    type ThreatCategory,
    type RiskLevel,
    type MitigationStatus,
    type AssetType,
    type Mitigation,
    type Threat,
    type Asset,
} from './useThreatModeling';

// CI/CD Pipeline Hook (Journey 12.1: DevOps Engineer - CI/CD Pipeline)
export {
    useCICDPipeline,
    type UseCICDPipelineResult,
    type UseCICDPipelineOptions,
    type StageType,
    type StageStatus,
    type PipelineStep,
    type PipelineStage,
} from './useCICDPipeline';

// Microservices Extractor Hook (Journey 14.1: Solution Architect - Microservices Extraction)
export {
    useMicroservicesExtractor,
    type UseMicroservicesExtractorResult,
    type UseMicroservicesExtractorOptions,
    type EntityType,
    type ExtractionStrategy,
    type CouplingLevel,
    type MonolithEntity,
    type BoundedContext,
    type ServiceBoundary,
    type CouplingAnalysis,
    type CohesionAnalysis,
    type ComplexityResult,
    type StrategyRecommendation,
} from './useMicroservicesExtractor';

// Zero-Trust Architecture Hook (Journey 15.1: Security Architect - Zero-Trust Design)
export {
    useZeroTrustArchitecture,
    type UseZeroTrustArchitectureReturn,
    type SecurityZone,
    type IdentityProvider,
    type PolicyRule,
    type TrustLevel,
    type SecurityZoneType,
    type TrustScore,
    type CoverageAnalysis,
} from './useZeroTrustArchitecture';

// Cloud Infrastructure Hook (Journey 16.1: Technical Architect - Cloud Infrastructure)
export {
    useCloudInfrastructure,
    type UseCloudInfrastructureReturn,
    type CloudProvider,
    type CloudResource,
    type ResourceCategory,
    type ResourceType,
    type Region,
    type CostEstimate,
    type ComplianceCheck,
    type ComplianceReport,
    type HighAvailabilityAnalysis,
} from './useCloudInfrastructure';

// Compliance Hook (Journey 26.1: Compliance Officer - Compliance Canvas)
export {
    useCompliance,
    type UseComplianceReturn,
    type ComplianceFramework,
    type ComplianceControl,
    type ControlStatus,
    type Evidence,
    type EvidenceType,
    type Severity,
    type RiskLevel as ComplianceRiskLevel,
    type GapAnalysisResult,
    type ComplianceOverview,
    type RiskScore,
} from './useCompliance';

// CISO Dashboard Hook (Journey 27.1: CISO - Executive Security Dashboard)
export {
    useCISODashboard,
    type UseCISODashboardReturn,
    type CVE,
    type SecurityIncident,
    type SystemRisk,
    type SecurityKPIs,
    type SecurityTrends,
    type IncidentStatus,
    type TrendPeriod,
    type ExportFormat,
} from './useCISODashboard';

// Release Train Hook (Journey 29.1: Release Train - Multi-Team Orchestration)
export {
    useReleaseTrain,
    type UseReleaseTrainReturn,
    type Team,
    type Feature,
    type Blocker,
    type Dependency,
    type ReadinessCheck,
    type FeatureStatus,
    type BlockerType,
    type ReadinessCheckType,
    type RiskLevel as ReleaseTrainRiskLevel,
} from './useReleaseTrain';

// Performance Analysis Hook (Journey 31.1: Performance Analysis - Profiling & Optimization)
export {
    usePerformanceAnalysis,
    type UsePerformanceAnalysisReturn,
    type MetricType as PerformanceMetricType,
    type TimeRange,
    type OptimizationPriority,
    type Service,
    type PerformanceMetric,
    type SLO as PerformanceSLO,
    type Bottleneck,
    type QueryPerformance,
    type OptimizationRecommendation,
    type PerformanceSnapshot,
} from './usePerformanceAnalysis';
