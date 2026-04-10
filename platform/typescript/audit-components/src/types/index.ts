/**
 * @fileoverview Core TypeScript types and interfaces for accessibility audit system
 * @module @ghatana/accessibility-audit/types
 * 
 * Based on WCAG 2.1, Lighthouse, and axe-core industry standards
 * See: SCORING_GUIDE.md and ENHANCEMENT_PLAN.md for detailed specifications
 */

// ============================================================================
// SCORING SYSTEM TYPES
// ============================================================================

/**
 * Letter grade for overall accessibility score
 * Based on 0-100 scale with industry-standard grade boundaries
 */
export type AccessibilityGrade = 
  | 'A+' // 97-100: Exceptional - Industry leading
  | 'A'  // 93-96: Excellent - Exceeds AAA
  | 'A-' // 90-92: Very Good - Full AA with AAA features
  | 'B+' // 87-89: Good - AA compliant
  | 'B'  // 83-86: Above Average - Mostly AA
  | 'B-' // 80-82: Average - A compliant
  | 'C+' // 77-79: Below Average
  | 'C'  // 73-76: Poor
  | 'C-' // 70-72: Very Poor
  | 'D'  // 60-69: Critical
  | 'F'; // 0-59: Failing

/**
 * WCAG compliance level achieved
 */
export type ComplianceLevel = 
  | 'WCAG AAA'        // Highest standard
  | 'WCAG AA'         // Industry standard
  | 'WCAG A'          // Minimum standard
  | 'Partial A'       // Some A criteria met
  | 'Non-compliant';  // Major failures

/**
 * Individual dimension score with detailed breakdown
 */
export interface DimensionScore {
  /** Score for this dimension (0-100) */
  score: number;
  
  /** Letter grade for this dimension */
  grade: AccessibilityGrade;
  
  /** Weight of this dimension in overall score */
  weight: number;
  
  /** Contribution to overall score (score * weight) */
  contribution: number;
  
  /** Number of issues by severity */
  issues: {
    critical: number;
    serious: number;
    moderate: number;
    minor: number;
  };
  
  /** Specific findings for this dimension */
  findings: string[];
  
  /** Recommendations for improvement */
  recommendations: string[];
}

/**
 * Complete accessibility score with 8-dimension breakdown
 * See SCORING_GUIDE.md for calculation methodology
 */
export interface AccessibilityScore {
  /** Overall score (0-100) */
  overall: number;
  
  /** Letter grade (A+ to F) */
  grade: AccessibilityGrade;
  
  /** WCAG compliance level achieved */
  complianceLevel: ComplianceLevel;
  
  /** Individual dimension scores with weights */
  dimensions: {
    /** WCAG Compliance (25% weight) */
    wcagCompliance: DimensionScore;
    
    /** Semantic Structure (15% weight) */
    semanticStructure: DimensionScore;
    
    /** Keyboard Accessibility (20% weight) */
    keyboardAccessibility: DimensionScore;
    
    /** Visual Accessibility (15% weight) */
    visualAccessibility: DimensionScore;
    
    /** Form Accessibility (10% weight) */
    formAccessibility: DimensionScore;
    
    /** Media Accessibility (5% weight) */
    mediaAccessibility: DimensionScore;
    
    /** ARIA Implementation (5% weight) */
    ariaImplementation: DimensionScore;
    
    /** Focus Management (5% weight) */
    focusManagement: DimensionScore;
  };
  
  /** Industry benchmark comparison */
  benchmark?: {
    industry: string;
    averageScore: number;
    top10PercentScore: number;
    percentile: number;
  };
  
  /** Historical trend data */
  trend?: {
    direction: 'improving' | 'stable' | 'degrading';
    changePercentage: number;
    previousScore: number;
    projectedScore: number;
  };
  
  /** Timestamp of score calculation */
  calculatedAt: string;
}

// ============================================================================
// VIOLATION AND FINDING TYPES
// ============================================================================

/**
 * Severity levels aligned with axe-core and WCAG impact
 */
export type ViolationSeverity = 'critical' | 'serious' | 'moderate' | 'minor';

/**
 * Type of finding
 */
export type FindingType = 'violation' | 'warning' | 'suggestion' | 'incomplete';

/**
 * WCAG 2.1 level
 */
export type WCAGLevel = 'A' | 'AA' | 'AAA';

/**
 * Location information for a finding
 */
export interface FindingLocation {
  /** CSS selector */
  selector: string;
  
  /** XPath expression */
  xpath: string;
  
  /** HTML snippet */
  snippet: string;
  
  /** Element bounding box (if available) */
  boundingBox?: {
    x: number;
    y: number;
    width: number;
    height: number;
  };
  
  /** Source file information */
  file?: string;
  line?: number;
  column?: number;
  
  /** Component name (if React component) */
  component?: string;
  
  /** URL where found */
  url: string;
}

/**
 * WCAG classification information
 */
export interface WCAGClassification {
  /** WCAG level (A, AA, AAA) */
  level: WCAGLevel;
  
  /** WCAG success criterion (e.g., "1.4.3") */
  criterion: string;
  
  /** WCAG technique reference (e.g., "G18") */
  technique?: string;
  
  /** WCAG principle */
  principle: 'perceivable' | 'operable' | 'understandable' | 'robust';
  
  /** Guideline number */
  guideline: string;
}

/**
 * Remediation guidance for a finding
 */
export interface RemediationGuidance {
  /** Plain English description of the fix */
  description: string;
  
  /** Step-by-step instructions */
  steps: string[];
  
  /** Code example showing the fix */
  codeExample: string;
  
  /** Can this be automatically fixed? */
  automatable: boolean;
  
  /** Estimated effort to fix */
  estimatedEffort: {
    hours: number;
    complexity: 'low' | 'medium' | 'high';
  };
  
  /** Priority (1 = highest) */
  priority: number;
  
  /** Links to resources */
  resources: string[];
}

/**
 * Complete finding/violation structure
 */
export interface Finding {
  /** Unique identifier */
  id: string;
  
  /** Type of finding */
  type: FindingType;
  
  /** Severity level */
  severity: ViolationSeverity;
  
  /** Impact level (null for suggestions) */
  impact: ViolationSeverity | null;
  
  /** Short description */
  description: string;
  
  /** Detailed help text */
  help: string;
  
  /** Link to help documentation */
  helpUrl: string;
  
  /** Location information */
  location: FindingLocation;
  
  /** WCAG classification */
  wcag: WCAGClassification;
  
  /** ARIA-related information */
  aria?: {
    role?: string;
    attributes: Record<string, string>;
    invalidAttributes?: string[];
  };
  
  /** Remediation guidance */
  remediation: RemediationGuidance;
  
  /** Related findings (by ID) */
  relatedFindings: string[];
  
  /** User groups affected */
  affectedUsers: Array<
    | 'blind'
    | 'low-vision'
    | 'color-blind'
    | 'deaf'
    | 'hard-of-hearing'
    | 'motor-impaired'
    | 'cognitive'
  >;
  
  /** Tags for categorization */
  tags: string[];
  
  /** Test result that found this issue */
  testResult?: {
    ruleId: string;
    toolName: string;
    toolVersion: string;
  };
}

// ============================================================================
// AUDIT CONFIGURATION
// ============================================================================

/**
 * Viewport configuration for responsive testing
 */
export interface ViewportConfig {
  width: number;
  height: number;
  name?: string;
  deviceScaleFactor?: number;
}

/**
 * Authentication configuration
 */
export interface AuthConfig {
  type: 'none' | 'basic' | 'bearer' | 'cookie' | 'custom';
  credentials?: {
    username?: string;
    password?: string;
    token?: string;
    cookies?: Array<{
      name: string;
      value: string;
      domain?: string;
      path?: string;
    }>;
  };
}

/**
 * Analysis mode configuration
 */
export interface AnalysisConfig {
  /** Static HTML/DOM analysis */
  static: boolean;
  
  /** Dynamic interaction analysis */
  dynamic: boolean;
  
  /** Runtime state monitoring */
  runtime: boolean;
  
  /** Performance profiling */
  performance: boolean;
  
  /** Design system specific checks */
  designSystem: boolean;
  
  /** React-specific analysis */
  reactSpecific: boolean;
}

/**
 * Output format configuration
 */
export interface OutputConfig {
  /** Output formats to generate */
  formats: Array<'json' | 'sarif' | 'xml' | 'csv' | 'html' | 'markdown'>;
  
  /** Output directory */
  directory: string;
  
  /** Include screenshots */
  screenshots: boolean;
  
  /** Include source code snippets */
  codeSnippets: boolean;
  
  /** Verbosity level */
  verbosity: 'minimal' | 'normal' | 'detailed' | 'debug';
}

/**
 * Threshold configuration for CI/CD gates
 */
export interface ThresholdConfig {
  /** Minimum overall score (0-100) */
  minScore: number;
  
  /** Minimum scores by dimension */
  minDimensionScores?: Partial<Record<keyof AccessibilityScore['dimensions'], number>>;
  
  /** Maximum allowed violations by severity */
  maxViolations: {
    critical: number;
    serious: number;
    moderate: number;
    minor: number;
  };
  
  /** Allow score decrease from baseline */
  allowScoreDecrease: number;
  
  /** Fail on new violations */
  failOnNewViolations: {
    critical: boolean;
    serious: boolean;
    moderate: boolean;
    minor: boolean;
  };
}

/**
 * Complete audit configuration
 */
export interface AuditConfig {
  /** URLs or file paths to audit */
  targets: string[];
  
  /** Viewports for responsive testing */
  viewports: ViewportConfig[];
  
  /** Authentication configuration */
  auth: AuthConfig;
  
  /** Analysis mode */
  analysis: AnalysisConfig;
  
  /** Output configuration */
  output: OutputConfig;
  
  /** Thresholds for CI/CD */
  thresholds?: ThresholdConfig;
  
  /** Include specific components */
  includeComponents?: string[];
  
  /** Exclude specific components */
  excludeComponents?: string[];
  
  /** Include specific rules */
  includeRules?: string[];
  
  /** Exclude specific rules */
  excludeRules?: string[];
  
  /** Storybook configuration */
  storybook?: {
    enabled: boolean;
    url: string;
    stories?: string[];
  };
  
  /** Timeout settings (ms) */
  timeout: {
    page: number;
    analysis: number;
    total: number;
  };
  
  /** Concurrency settings */
  concurrency: {
    maxPages: number;
    maxAnalyzers: number;
  };
  
  /** Baseline comparison */
  baseline?: {
    enabled: boolean;
    file: string;
  };
}

// ============================================================================
// REPORT TYPES
// ============================================================================

/**
 * Summary statistics
 */
export interface AuditSummary {
  /** Total findings */
  totalFindings: number;
  
  /** Findings by type */
  byType: {
    violations: number;
    warnings: number;
    suggestions: number;
    incomplete: number;
  };
  
  /** Findings by severity */
  bySeverity: {
    critical: number;
    serious: number;
    moderate: number;
    minor: number;
  };
  
  /** Elements analyzed */
  elementsAnalyzed: number;
  
  /** Elements with issues */
  elementsWithIssues: number;
  
  /** Coverage percentage */
  coverage: number;
  
  /** Duration (ms) */
  duration: number;
}

/**
 * Metadata about the audit
 */
export interface AuditMetadata {
  /** Audit ID */
  id: string;
  
  /** Audit timestamp */
  timestamp: string;
  
  /** Tool version */
  toolVersion: string;
  
  /** Configuration used */
  config: AuditConfig;
  
  /** Environment information */
  environment: {
    os: string;
    browser: string;
    browserVersion: string;
    nodeVersion: string;
  };
  
  /** User who ran the audit */
  user?: string;
  
  /** CI/CD context */
  ci?: {
    provider: string;
    branch: string;
    commit: string;
    buildNumber: string;
  };
}

/**
 * Complete accessibility audit report
 */
export interface AccessibilityReport {
  /** Metadata */
  metadata: AuditMetadata;
  
  /** Accessibility score */
  score: AccessibilityScore;
  
  /** Summary statistics */
  summary: AuditSummary;
  
  /** All findings */
  findings: Finding[];
  
  /** Page/component analyzed */
  target: {
    url: string;
    title: string;
    description?: string;
  };
  
  /** Historical comparison */
  comparison?: {
    baseline: AccessibilityScore;
    current: AccessibilityScore;
    changes: {
      scoreChange: number;
      newFindings: Finding[];
      resolvedFindings: Finding[];
      regressions: Finding[];
    };
  };
  
  /** Recommendations */
  recommendations: {
    immediate: string[];
    shortTerm: string[];
    longTerm: string[];
  };
}

// ============================================================================
// STATE ANALYSIS TYPES
// ============================================================================

/**
 * ARIA attribute mutation tracking
 */
export interface ARIAMutation {
  /** Element identifier */
  element: string;
  
  /** Attribute that changed */
  attribute: string;
  
  /** Previous value */
  oldValue: string | null;
  
  /** New value */
  newValue: string | null;
  
  /** Timestamp */
  timestamp: number;
  
  /** React component (if applicable) */
  component?: string;
  
  /** Was this mutation expected/valid? */
  valid: boolean;
  
  /** Performance impact */
  performanceImpact: 'low' | 'medium' | 'high';
}

/**
 * Focus state tracking
 */
export interface FocusStateChange {
  /** Element that received focus */
  element: string;
  
  /** Previous focused element */
  previousElement: string | null;
  
  /** Timestamp */
  timestamp: number;
  
  /** Focus method (tab, click, programmatic) */
  method: 'keyboard' | 'mouse' | 'programmatic';
  
  /** Was focus visible? */
  visible: boolean;
  
  /** Was this expected in the focus order? */
  expectedOrder: boolean;
}

/**
 * State management analysis
 */
export interface StateAnalysis {
  /** ARIA mutations detected */
  ariaMutations: ARIAMutation[];
  
  /** Focus state changes */
  focusChanges: FocusStateChange[];
  
  /** Infinite loop detection */
  infiniteLoops: Array<{
    component: string;
    updateCount: number;
    stackTrace: string;
  }>;
  
  /** Re-render analysis */
  rerenders: Array<{
    component: string;
    count: number;
    accessibilityRelated: boolean;
    performanceImpact: 'low' | 'medium' | 'high' | 'critical';
  }>;
  
  /** Overall assessment */
  assessment: {
    hasIssues: boolean;
    severity: ViolationSeverity;
    recommendations: string[];
  };
}

// ============================================================================
// MACHINE-READABLE OUTPUT TYPES
// ============================================================================

/**
 * SARIF (Static Analysis Results Interchange Format) output
 * See: https://sarifweb.azurewebsites.net/
 */
export interface SARIFOutput {
  version: '2.1.0';
  $schema: string;
  runs: Array<{
    tool: {
      driver: {
        name: string;
        version: string;
        informationUri: string;
        rules: Array<{
          id: string;
          shortDescription: { text: string };
          fullDescription: { text: string };
          helpUri: string;
        }>;
      };
    };
    results: Array<{
      ruleId: string;
      level: 'error' | 'warning' | 'note';
      message: { text: string };
      locations: Array<{
        physicalLocation: {
          artifactLocation: { uri: string };
          region?: {
            startLine: number;
            startColumn: number;
          };
        };
      }>;
    }>;
  }>;
}

/**
 * Export all types
 */
export type {
  // Re-export for convenience
  Finding as AccessibilityViolation,
  AccessibilityReport as AuditReport,
};

// ============================================================================
// ADDITIONAL UTILITY TYPES
// ============================================================================

/**
 * Output format types
 */
export type OutputFormat = 'json' | 'html' | 'csv' | 'sarif' | 'xml' | 'markdown';

/**
 * Affected user groups
 */
export type AffectedUserGroup = 
  | 'blind'
  | 'low-vision'
  | 'color-blind'
  | 'deaf'
  | 'hard-of-hearing'
  | 'motor-impaired'
  | 'cognitive';

/**
 * Recommendation item
 */
export interface Recommendation {
  /** Recommendation text */
  text: string;
  
  /** Priority level */
  priority: 'immediate' | 'short-term' | 'long-term';
  
  /** Related findings */
  relatedFindings?: string[];
}

/**
 * Comparison result between two reports
 */
export interface ComparisonResult {
  /** Current report score */
  current: AccessibilityScore;
  
  /** Previous report score */
  previous: AccessibilityScore;
  
  /** Trend analysis */
  trend: {
    direction: 'improving' | 'stable' | 'degrading';
    change: number;
    scoreChange: number;
    previousScore: number;
  };
  
  /** Number of new issues */
  newIssues: number;
  
  /** Number of resolved issues */
  resolvedIssues: number;
  
  /** Detailed findings comparison */
  details?: {
    newFindings: Finding[];
    resolvedFindings: Finding[];
  };
}
