/**
 * Type definitions for compliance reporting and checks
 * 
 * Provides types for:
 * - Compliance standards (SOC2, HIPAA, GDPR, PCI-DSS)
 * - Compliance reports and assessments
 * - Compliance checks and validations
 * - Approval workflows
 * - Export formats
 */

/**
 * Supported compliance standards
 */
export enum ComplianceStandard {
    SOC2 = 'SOC2',
    HIPAA = 'HIPAA',
    GDPR = 'GDPR',
    PCI_DSS = 'PCI_DSS',
    ISO_27001 = 'ISO_27001',
    NIST = 'NIST',
}

/**
 * Compliance status
 */
export enum ComplianceStatus {
    COMPLIANT = 'COMPLIANT',
    NON_COMPLIANT = 'NON_COMPLIANT',
    PARTIALLY_COMPLIANT = 'PARTIALLY_COMPLIANT',
    NOT_ASSESSED = 'NOT_ASSESSED',
    IN_PROGRESS = 'IN_PROGRESS',
}

/**
 * Severity of compliance issues
 */
export enum ComplianceIssueSeverity {
    CRITICAL = 'CRITICAL',
    HIGH = 'HIGH',
    MEDIUM = 'MEDIUM',
    LOW = 'LOW',
    INFO = 'INFO',
}

/**
 * Approval status for permission changes
 */
export enum ApprovalStatus {
    PENDING = 'PENDING',
    APPROVED = 'APPROVED',
    REJECTED = 'REJECTED',
    CANCELLED = 'CANCELLED',
}

/**
 * Export format for compliance reports
 */
export enum ExportFormat {
    PDF = 'PDF',
    CSV = 'CSV',
    JSON = 'JSON',
    EXCEL = 'EXCEL',
}

/**
 * Compliance requirement
 */
export interface ComplianceRequirement {
    requirementId: string;
    standard: ComplianceStandard;
    title: string;
    description: string;
    category: string;
    controlId?: string;
    priority: ComplianceIssueSeverity;
    implementationGuidance?: string;
    testingProcedure?: string;
    references?: string[];
}

/**
 * Compliance check result
 */
export interface ComplianceCheckResult {
    checkId: string;
    requirementId: string;
    status: ComplianceStatus;
    timestamp: string;
    checkedBy?: string;
    evidence?: string[];
    issues?: ComplianceIssue[];
    score?: number; // 0-100
    notes?: string;
}

/**
 * Compliance issue
 */
export interface ComplianceIssue {
    issueId: string;
    requirementId: string;
    severity: ComplianceIssueSeverity;
    title: string;
    description: string;
    affectedResources: string[];
    detectedAt: string;
    resolvedAt?: string;
    resolution?: string;
    assignedTo?: string;
    dueDate?: string;
}

/**
 * Compliance assessment
 */
export interface ComplianceAssessment {
    assessmentId: string;
    standard: ComplianceStandard;
    title: string;
    description?: string;
    startDate: string;
    endDate?: string;
    status: ComplianceStatus;
    overallScore: number; // 0-100
    assessedBy: string;
    requirements: ComplianceCheckResult[];
    summary: {
        totalRequirements: number;
        compliant: number;
        nonCompliant: number;
        partiallyCompliant: number;
        notAssessed: number;
    };
    recommendations?: string[];
    nextAssessmentDate?: string;
}

/**
 * Compliance report
 */
export interface ComplianceReport {
    reportId: string;
    title: string;
    standard: ComplianceStandard;
    reportType: 'summary' | 'detailed' | 'executive' | 'technical';
    generatedAt: string;
    generatedBy: string;
    period: {
        startDate: string;
        endDate: string;
    };
    assessment: ComplianceAssessment;
    auditEvents: number;
    criticalIssues: number;
    openIssues: number;
    resolvedIssues: number;
    trends?: ComplianceTrend[];
    sections?: ComplianceReportSection[];
}

/**
 * Compliance report section
 */
export interface ComplianceReportSection {
    sectionId: string;
    title: string;
    content: string;
    subsections?: ComplianceReportSection[];
    charts?: ComplianceChart[];
    tables?: ComplianceTable[];
}

/**
 * Compliance trend data
 */
export interface ComplianceTrend {
    date: string;
    overallScore: number;
    compliantCount: number;
    nonCompliantCount: number;
    criticalIssues: number;
}

/**
 * Compliance chart data
 */
export interface ComplianceChart {
    chartId: string;
    type: 'bar' | 'line' | 'pie' | 'radar';
    title: string;
    data: any;
    options?: any;
}

/**
 * Compliance table data
 */
export interface ComplianceTable {
    tableId: string;
    title: string;
    headers: string[];
    rows: string[][];
}

/**
 * Approval request for permission changes
 */
export interface ApprovalRequest {
    requestId: string;
    requestType: 'permission_change' | 'role_change' | 'bulk_operation';
    requestedBy: string;
    requestedAt: string;
    status: ApprovalStatus;
    priority: ComplianceIssueSeverity;
    title: string;
    description: string;
    changes: {
        resourceType: string;
        resourceId: string;
        resourceName?: string;
        changeType: 'add' | 'remove' | 'modify';
        oldValue?: any;
        newValue?: any;
    }[];
    justification?: string;
    approvers: {
        userId: string;
        userName?: string;
        status: ApprovalStatus;
        decidedAt?: string;
        comment?: string;
    }[];
    expiresAt?: string;
    approvedAt?: string;
    rejectedAt?: string;
    cancelledAt?: string;
}

/**
 * Automated compliance check configuration
 */
export interface ComplianceCheckConfig {
    checkId: string;
    requirementId: string;
    name: string;
    description: string;
    enabled: boolean;
    schedule: 'manual' | 'daily' | 'weekly' | 'monthly';
    lastRun?: string;
    nextRun?: string;
    checkFunction: string; // Name of the check function
    parameters?: Record<string, any>;
    notifications?: {
        enabled: boolean;
        channels: ('email' | 'slack' | 'teams')[];
        recipients: string[];
        onFailureOnly: boolean;
    };
}

/**
 * Compliance dashboard data
 */
export interface ComplianceDashboard {
    overallStatus: ComplianceStatus;
    overallScore: number; // 0-100
    standards: {
        standard: ComplianceStandard;
        status: ComplianceStatus;
        score: number;
        lastAssessment: string;
    }[];
    criticalIssues: number;
    openIssues: number;
    recentAudits: number;
    upcomingDeadlines: {
        title: string;
        dueDate: string;
        severity: ComplianceIssueSeverity;
    }[];
    trends: ComplianceTrend[];
    recentActivity: {
        timestamp: string;
        type: string;
        description: string;
        severity: ComplianceIssueSeverity;
    }[];
}

/**
 * Export options
 */
export interface ExportOptions {
    format: ExportFormat;
    includeCharts: boolean;
    includeTables: boolean;
    includeAuditTrail: boolean;
    dateRange?: {
        startDate: string;
        endDate: string;
    };
    standards?: ComplianceStandard[];
    sections?: string[];
    template?: 'default' | 'executive' | 'technical' | 'custom';
}

/**
 * Compliance filter
 */
export interface ComplianceFilter {
    standards?: ComplianceStandard[];
    statuses?: ComplianceStatus[];
    severities?: ComplianceIssueSeverity[];
    startDate?: string;
    endDate?: string;
    assignedTo?: string[];
    limit?: number;
    offset?: number;
}

/**
 * Compliance query result
 */
export interface ComplianceQueryResult<T> {
    items: T[];
    total: number;
    hasMore: boolean;
}

/**
 * Compliance statistics
 */
export interface ComplianceStats {
    totalAssessments: number;
    totalRequirements: number;
    totalIssues: number;
    criticalIssues: number;
    openIssues: number;
    resolvedIssues: number;
    averageScore: number;
    complianceRate: number;
    byStandard: Record<ComplianceStandard, {
        assessments: number;
        score: number;
        issues: number;
    }>;
    bySeverity: Record<ComplianceIssueSeverity, number>;
    recentTrends: ComplianceTrend[];
}
