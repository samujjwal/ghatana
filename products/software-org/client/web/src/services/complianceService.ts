/**
 * Compliance Service
 * 
 * Provides compliance reporting, assessment, and checking functionality.
 * 
 * Features:
 * - Generate compliance reports for SOC2, HIPAA, GDPR, PCI-DSS, ISO 27001, NIST
 * - Run automated compliance checks
 * - Track compliance issues and resolutions
 * - Manage approval workflows for permission changes
 * - Export reports in PDF, CSV, JSON, Excel formats
 * - Provide dashboard data and analytics
 * 
 * Performance targets:
 * - Report generation: <2 seconds
 * - Compliance check: <500ms
 * - Dashboard load: <200ms
 * - Export generation: <3 seconds
 */

import {
    ComplianceStandard,
    ComplianceStatus,
    ComplianceIssueSeverity,
    ApprovalStatus,
    ExportFormat,
    ComplianceRequirement,
    ComplianceCheckResult,
    ComplianceIssue,
    ComplianceAssessment,
    ComplianceReport,
    ComplianceDashboard,
    ApprovalRequest,
    ComplianceCheckConfig,
    ExportOptions,
    ComplianceFilter,
    ComplianceQueryResult,
    ComplianceStats,
    ComplianceTrend,
} from '../types/compliance';
import { AuditService } from './auditService';

/**
 * In-memory compliance store
 */
class ComplianceStore {
    private assessments: Map<string, ComplianceAssessment> = new Map();
    private reports: Map<string, ComplianceReport> = new Map();
    private issues: Map<string, ComplianceIssue> = new Map();
    private approvalRequests: Map<string, ApprovalRequest> = new Map();
    private checkConfigs: Map<string, ComplianceCheckConfig> = new Map();
    private requirements: Map<string, ComplianceRequirement> = new Map();

    constructor() {
        this.initializeDefaultRequirements();
    }

    /**
     * Initialize default compliance requirements
     */
    private initializeDefaultRequirements(): void {
        // SOC2 requirements
        this.addRequirement({
            requirementId: 'soc2-cc6.1',
            standard: ComplianceStandard.SOC2,
            title: 'Logical and Physical Access Controls',
            description: 'The entity implements logical access security software, infrastructure, and architectures over protected information assets to protect them from security events to meet the entity\'s objectives.',
            category: 'Common Criteria',
            controlId: 'CC6.1',
            priority: ComplianceIssueSeverity.HIGH,
            implementationGuidance: 'Implement role-based access control (RBAC) with regular reviews',
            testingProcedure: 'Review access control lists and audit logs',
            references: ['SOC2 Trust Services Criteria'],
        });

        this.addRequirement({
            requirementId: 'soc2-cc6.2',
            standard: ComplianceStandard.SOC2,
            title: 'Prior to Issuing System Credentials and Granting System Access',
            description: 'Prior to issuing system credentials and granting system access, the entity registers and authorizes new internal and external users whose access is administered by the entity.',
            category: 'Common Criteria',
            controlId: 'CC6.2',
            priority: ComplianceIssueSeverity.HIGH,
            implementationGuidance: 'Implement approval workflow for access requests',
            testingProcedure: 'Review approval records and access grant logs',
            references: ['SOC2 Trust Services Criteria'],
        });

        // HIPAA requirements
        this.addRequirement({
            requirementId: 'hipaa-164.308-a-3',
            standard: ComplianceStandard.HIPAA,
            title: 'Workforce Security',
            description: 'Implement policies and procedures to ensure that all members of its workforce have appropriate access to electronic protected health information.',
            category: 'Administrative Safeguards',
            controlId: '164.308(a)(3)',
            priority: ComplianceIssueSeverity.CRITICAL,
            implementationGuidance: 'Implement workforce clearance procedures, authorization procedures, and access establishment',
            testingProcedure: 'Review workforce access policies and authorization records',
            references: ['HIPAA Security Rule'],
        });

        this.addRequirement({
            requirementId: 'hipaa-164.308-a-5',
            standard: ComplianceStandard.HIPAA,
            title: 'Security Awareness and Training',
            description: 'Implement a security awareness and training program for all members of its workforce.',
            category: 'Administrative Safeguards',
            controlId: '164.308(a)(5)',
            priority: ComplianceIssueSeverity.MEDIUM,
            implementationGuidance: 'Conduct regular security training and maintain training records',
            testingProcedure: 'Review training records and curriculum',
            references: ['HIPAA Security Rule'],
        });

        // GDPR requirements
        this.addRequirement({
            requirementId: 'gdpr-art-32',
            standard: ComplianceStandard.GDPR,
            title: 'Security of Processing',
            description: 'Implement appropriate technical and organizational measures to ensure a level of security appropriate to the risk.',
            category: 'Security',
            controlId: 'Article 32',
            priority: ComplianceIssueSeverity.HIGH,
            implementationGuidance: 'Implement encryption, access controls, and regular security testing',
            testingProcedure: 'Review security measures and test results',
            references: ['GDPR Article 32'],
        });

        this.addRequirement({
            requirementId: 'gdpr-art-33',
            standard: ComplianceStandard.GDPR,
            title: 'Notification of a Personal Data Breach',
            description: 'In the case of a personal data breach, notify the supervisory authority without undue delay and, where feasible, not later than 72 hours.',
            category: 'Breach Notification',
            controlId: 'Article 33',
            priority: ComplianceIssueSeverity.CRITICAL,
            implementationGuidance: 'Establish breach notification procedures and contact lists',
            testingProcedure: 'Review breach response procedures and notification templates',
            references: ['GDPR Article 33'],
        });

        // Add more requirements as needed
    }

    addRequirement(requirement: ComplianceRequirement): void {
        this.requirements.set(requirement.requirementId, requirement);
    }

    getRequirement(requirementId: string): ComplianceRequirement | undefined {
        return this.requirements.get(requirementId);
    }

    getRequirementsByStandard(standard: ComplianceStandard): ComplianceRequirement[] {
        return Array.from(this.requirements.values()).filter(
            (req) => req.standard === standard
        );
    }

    addAssessment(assessment: ComplianceAssessment): void {
        this.assessments.set(assessment.assessmentId, assessment);
    }

    getAssessment(assessmentId: string): ComplianceAssessment | undefined {
        return this.assessments.get(assessmentId);
    }

    getAssessments(filter?: ComplianceFilter): ComplianceQueryResult<ComplianceAssessment> {
        let items = Array.from(this.assessments.values());

        if (filter?.standards) {
            items = items.filter((a) => filter.standards!.includes(a.standard));
        }

        if (filter?.statuses) {
            items = items.filter((a) => filter.statuses!.includes(a.status));
        }

        if (filter?.startDate) {
            items = items.filter((a) => a.startDate >= filter.startDate!);
        }

        if (filter?.endDate) {
            items = items.filter((a) => a.endDate && a.endDate <= filter.endDate!);
        }

        const total = items.length;
        const offset = filter?.offset || 0;
        const limit = filter?.limit || 50;
        const paginated = items.slice(offset, offset + limit);

        return {
            items: paginated,
            total,
            hasMore: offset + limit < total,
        };
    }

    addReport(report: ComplianceReport): void {
        this.reports.set(report.reportId, report);
    }

    getReport(reportId: string): ComplianceReport | undefined {
        return this.reports.get(reportId);
    }

    addIssue(issue: ComplianceIssue): void {
        this.issues.set(issue.issueId, issue);
    }

    getIssue(issueId: string): ComplianceIssue | undefined {
        return this.issues.get(issueId);
    }

    getIssues(filter?: ComplianceFilter): ComplianceQueryResult<ComplianceIssue> {
        let items = Array.from(this.issues.values());

        if (filter?.severities) {
            items = items.filter((i) => filter.severities!.includes(i.severity));
        }

        if (filter?.startDate) {
            items = items.filter((i) => i.detectedAt >= filter.startDate!);
        }

        if (filter?.endDate) {
            items = items.filter((i) => i.detectedAt <= filter.endDate!);
        }

        if (filter?.assignedTo) {
            items = items.filter((i) => i.assignedTo && filter.assignedTo!.includes(i.assignedTo));
        }

        const total = items.length;
        const offset = filter?.offset || 0;
        const limit = filter?.limit || 50;
        const paginated = items.slice(offset, offset + limit);

        return {
            items: paginated,
            total,
            hasMore: offset + limit < total,
        };
    }

    addApprovalRequest(request: ApprovalRequest): void {
        this.approvalRequests.set(request.requestId, request);
    }

    getApprovalRequest(requestId: string): ApprovalRequest | undefined {
        return this.approvalRequests.get(requestId);
    }

    getApprovalRequests(status?: ApprovalStatus): ApprovalRequest[] {
        const requests = Array.from(this.approvalRequests.values());
        return status ? requests.filter((r) => r.status === status) : requests;
    }

    addCheckConfig(config: ComplianceCheckConfig): void {
        this.checkConfigs.set(config.checkId, config);
    }

    getCheckConfig(checkId: string): ComplianceCheckConfig | undefined {
        return this.checkConfigs.get(checkId);
    }

    getCheckConfigs(): ComplianceCheckConfig[] {
        return Array.from(this.checkConfigs.values());
    }

    clear(): void {
        this.assessments.clear();
        this.reports.clear();
        this.issues.clear();
        this.approvalRequests.clear();
        this.checkConfigs.clear();
        // Don't clear requirements as they are defaults
    }
}

const store = new ComplianceStore();

/**
 * Compliance Service
 */
export class ComplianceService {
    /**
     * Generate compliance report for a standard
     */
    static async generateReport(
        standard: ComplianceStandard,
        options?: {
            reportType?: 'summary' | 'detailed' | 'executive' | 'technical';
            startDate?: string;
            endDate?: string;
        }
    ): Promise<ComplianceReport> {
        const startTime = Date.now();

        // Get or create assessment
        const assessment = await this.runAssessment(standard);

        // Get audit events count for period
        const auditEvents = await this.countAuditEvents(
            options?.startDate || assessment.startDate,
            options?.endDate || new Date().toISOString()
        );

        // Get issues
        const allIssues = store.getIssues({
            startDate: options?.startDate,
            endDate: options?.endDate,
        });

        const criticalIssues = allIssues.items.filter(
            (i) => i.severity === ComplianceIssueSeverity.CRITICAL && !i.resolvedAt
        ).length;

        const openIssues = allIssues.items.filter((i) => !i.resolvedAt).length;
        const resolvedIssues = allIssues.items.filter((i) => i.resolvedAt).length;

        // Generate trends (last 30 days)
        const trends = await this.generateTrends(standard, 30);

        const report: ComplianceReport = {
            reportId: `report-${Date.now()}`,
            title: `${standard} Compliance Report`,
            standard,
            reportType: options?.reportType || 'detailed',
            generatedAt: new Date().toISOString(),
            generatedBy: 'System',
            period: {
                startDate: options?.startDate || assessment.startDate,
                endDate: options?.endDate || new Date().toISOString(),
            },
            assessment,
            auditEvents,
            criticalIssues,
            openIssues,
            resolvedIssues,
            trends,
            sections: this.generateReportSections(standard, assessment, options?.reportType || 'detailed'),
        };

        store.addReport(report);

        // Log audit event
        await AuditService.logEvent({
            userId: 'system',
            action: 'compliance.report.generate',
            resource: 'compliance_report',
            resourceId: report.reportId,
            metadata: {
                standard,
                reportType: options?.reportType || 'detailed',
                overallScore: assessment.overallScore,
                duration: Date.now() - startTime,
            },
        });

        return report;
    }

    /**
     * Run compliance assessment
     */
    static async runAssessment(standard: ComplianceStandard): Promise<ComplianceAssessment> {
        const requirements = store.getRequirementsByStandard(standard);
        const results: ComplianceCheckResult[] = [];

        // Run checks for each requirement
        for (const req of requirements) {
            const result = await this.runComplianceCheck(req);
            results.push(result);
        }

        // Calculate overall score
        const scores = results.map((r) => r.score || 0);
        const overallScore = scores.reduce((sum, score) => sum + score, 0) / scores.length;

        // Count statuses
        const summary = {
            totalRequirements: results.length,
            compliant: results.filter((r) => r.status === ComplianceStatus.COMPLIANT).length,
            nonCompliant: results.filter((r) => r.status === ComplianceStatus.NON_COMPLIANT).length,
            partiallyCompliant: results.filter((r) => r.status === ComplianceStatus.PARTIALLY_COMPLIANT).length,
            notAssessed: results.filter((r) => r.status === ComplianceStatus.NOT_ASSESSED).length,
        };

        // Determine overall status
        let status: ComplianceStatus;
        if (summary.nonCompliant === 0 && summary.notAssessed === 0) {
            status = ComplianceStatus.COMPLIANT;
        } else if (summary.compliant > summary.nonCompliant) {
            status = ComplianceStatus.PARTIALLY_COMPLIANT;
        } else {
            status = ComplianceStatus.NON_COMPLIANT;
        }

        const assessment: ComplianceAssessment = {
            assessmentId: `assessment-${Date.now()}`,
            standard,
            title: `${standard} Assessment`,
            startDate: new Date().toISOString(),
            endDate: new Date().toISOString(),
            status,
            overallScore,
            assessedBy: 'Automated System',
            requirements: results,
            summary,
            recommendations: this.generateRecommendations(results),
            nextAssessmentDate: new Date(Date.now() + 90 * 24 * 60 * 60 * 1000).toISOString(), // 90 days
        };

        store.addAssessment(assessment);

        // Log audit event
        await AuditService.logEvent({
            userId: 'system',
            action: 'compliance.assessment.run',
            resource: 'compliance_assessment',
            resourceId: assessment.assessmentId,
            metadata: {
                standard,
                overallScore,
                status,
                totalRequirements: summary.totalRequirements,
                compliant: summary.compliant,
                nonCompliant: summary.nonCompliant,
            },
        });

        return assessment;
    }

    /**
     * Run compliance check for a requirement
     */
    static async runComplianceCheck(requirement: ComplianceRequirement): Promise<ComplianceCheckResult> {
        // In a real implementation, this would run actual checks
        // For now, we'll simulate with random results

        const status = this.determineComplianceStatus(requirement);
        const score = this.calculateComplianceScore(status);
        const issues = status !== ComplianceStatus.COMPLIANT ? this.generateIssues(requirement) : [];

        const result: ComplianceCheckResult = {
            checkId: `check-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
            requirementId: requirement.requirementId,
            status,
            timestamp: new Date().toISOString(),
            checkedBy: 'Automated System',
            evidence: [`Audit log review for ${requirement.title}`],
            issues,
            score,
            notes: `Automated check completed for ${requirement.controlId || requirement.requirementId}`,
        };

        return result;
    }

    /**
     * Create approval request
     */
    static async createApprovalRequest(params: {
        requestType: 'permission_change' | 'role_change' | 'bulk_operation';
        requestedBy: string;
        title: string;
        description: string;
        changes: ApprovalRequest['changes'];
        justification?: string;
        approvers: string[];
        priority?: ComplianceIssueSeverity;
        expiresAt?: string;
    }): Promise<ApprovalRequest> {
        const request: ApprovalRequest = {
            requestId: `approval-${Date.now()}`,
            requestType: params.requestType,
            requestedBy: params.requestedBy,
            requestedAt: new Date().toISOString(),
            status: ApprovalStatus.PENDING,
            priority: params.priority || ComplianceIssueSeverity.MEDIUM,
            title: params.title,
            description: params.description,
            changes: params.changes,
            justification: params.justification,
            approvers: params.approvers.map((userId) => ({
                userId,
                status: ApprovalStatus.PENDING,
            })),
            expiresAt: params.expiresAt,
        };

        store.addApprovalRequest(request);

        // Log audit event
        await AuditService.logEvent({
            userId: params.requestedBy,
            action: 'approval.request.create',
            resource: 'approval_request',
            resourceId: request.requestId,
            metadata: {
                requestType: params.requestType,
                changesCount: params.changes.length,
                approversCount: params.approvers.length,
            },
        });

        return request;
    }

    /**
     * Approve or reject approval request
     */
    static async respondToApprovalRequest(
        requestId: string,
        userId: string,
        decision: 'approve' | 'reject',
        comment?: string
    ): Promise<ApprovalRequest> {
        const request = store.getApprovalRequest(requestId);
        if (!request) {
            throw new Error('Approval request not found');
        }

        if (request.status !== ApprovalStatus.PENDING) {
            throw new Error('Approval request is not pending');
        }

        // Update approver status
        const approver = request.approvers.find((a) => a.userId === userId);
        if (!approver) {
            throw new Error('User is not an approver for this request');
        }

        approver.status = decision === 'approve' ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED;
        approver.decidedAt = new Date().toISOString();
        approver.comment = comment;

        // Check if all approvers have responded
        const allResponded = request.approvers.every((a) => a.status !== ApprovalStatus.PENDING);
        const anyRejected = request.approvers.some((a) => a.status === ApprovalStatus.REJECTED);

        if (allResponded) {
            if (anyRejected) {
                request.status = ApprovalStatus.REJECTED;
                request.rejectedAt = new Date().toISOString();
            } else {
                request.status = ApprovalStatus.APPROVED;
                request.approvedAt = new Date().toISOString();
            }
        }

        store.addApprovalRequest(request);

        // Log audit event
        await AuditService.logEvent({
            userId,
            action: `approval.request.${decision}`,
            resource: 'approval_request',
            resourceId: request.requestId,
            metadata: {
                decision,
                comment,
                finalStatus: request.status,
            },
        });

        return request;
    }

    /**
     * Get compliance dashboard data
     */
    static async getDashboard(): Promise<ComplianceDashboard> {
        const standards = [
            ComplianceStandard.SOC2,
            ComplianceStandard.HIPAA,
            ComplianceStandard.GDPR,
        ];

        const standardsData = await Promise.all(
            standards.map(async (standard) => {
                const assessments = store.getAssessments({ standards: [standard] });
                const latest = assessments.items[assessments.items.length - 1];

                return {
                    standard,
                    status: latest?.status || ComplianceStatus.NOT_ASSESSED,
                    score: latest?.overallScore || 0,
                    lastAssessment: latest?.endDate || 'Never',
                };
            })
        );

        // Calculate overall status and score
        const avgScore = standardsData.reduce((sum, s) => sum + s.score, 0) / standardsData.length;
        const overallStatus =
            avgScore >= 90
                ? ComplianceStatus.COMPLIANT
                : avgScore >= 70
                    ? ComplianceStatus.PARTIALLY_COMPLIANT
                    : ComplianceStatus.NON_COMPLIANT;

        // Get critical and open issues
        const allIssues = store.getIssues({});
        const criticalIssues = allIssues.items.filter(
            (i) => i.severity === ComplianceIssueSeverity.CRITICAL && !i.resolvedAt
        ).length;
        const openIssues = allIssues.items.filter((i) => !i.resolvedAt).length;

        // Get recent audit count
        const recentAudits = await this.countAuditEvents(
            new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
            new Date().toISOString()
        );

        // Get upcoming deadlines
        const upcomingDeadlines = allIssues.items
            .filter((i) => i.dueDate && !i.resolvedAt)
            .sort((a, b) => (a.dueDate! < b.dueDate! ? -1 : 1))
            .slice(0, 5)
            .map((i) => ({
                title: i.title,
                dueDate: i.dueDate!,
                severity: i.severity,
            }));

        // Generate trends
        const trends = await this.generateTrends(ComplianceStandard.SOC2, 30);

        // Get recent activity
        const recentEvents = await AuditService.queryAuditTrail({
            actions: ['compliance.assessment.run', 'compliance.report.generate', 'approval.request.create'],
            limit: 10,
        });

        const recentActivity = recentEvents.events.map((e) => ({
            timestamp: e.timestamp,
            type: e.action,
            description: `${e.action} by ${e.userId}`,
            severity: ComplianceIssueSeverity.INFO,
        }));

        return {
            overallStatus,
            overallScore: avgScore,
            standards: standardsData,
            criticalIssues,
            openIssues,
            recentAudits,
            upcomingDeadlines,
            trends,
            recentActivity,
        };
    }

    /**
     * Export compliance report
     */
    static async exportReport(reportId: string, options: ExportOptions): Promise<Blob> {
        const report = store.getReport(reportId);
        if (!report) {
            throw new Error('Report not found');
        }

        const startTime = Date.now();

        let blob: Blob;
        switch (options.format) {
            case ExportFormat.PDF:
                blob = await this.exportToPDF(report, options);
                break;
            case ExportFormat.CSV:
                blob = await this.exportToCSV(report, options);
                break;
            case ExportFormat.JSON:
                blob = await this.exportToJSON(report, options);
                break;
            case ExportFormat.EXCEL:
                blob = await this.exportToExcel(report, options);
                break;
            default:
                throw new Error('Unsupported export format');
        }

        // Log audit event
        await AuditService.logEvent({
            userId: 'system',
            action: 'compliance.report.export',
            resource: 'compliance_report',
            resourceId: report.reportId,
            metadata: {
                format: options.format,
                size: blob.size,
                duration: Date.now() - startTime,
            },
        });

        return blob;
    }

    /**
     * Get compliance statistics
     */
    static async getStats(): Promise<ComplianceStats> {
        const assessments = store.getAssessments({});
        const issues = store.getIssues({});

        const criticalIssues = issues.items.filter(
            (i) => i.severity === ComplianceIssueSeverity.CRITICAL
        ).length;
        const openIssues = issues.items.filter((i) => !i.resolvedAt).length;
        const resolvedIssues = issues.items.filter((i) => i.resolvedAt).length;

        const avgScore =
            assessments.items.reduce((sum, a) => sum + a.overallScore, 0) / (assessments.items.length || 1);

        const complianceRate =
            assessments.items.filter((a) => a.status === ComplianceStatus.COMPLIANT).length /
            (assessments.items.length || 1);

        // By standard
        const byStandard: Record<ComplianceStandard, any> = {} as any;
        Object.values(ComplianceStandard).forEach((standard) => {
            const standardAssessments = assessments.items.filter((a) => a.standard === standard);
            const standardIssues = issues.items.filter((i) => {
                const req = store.getRequirement(i.requirementId);
                return req?.standard === standard;
            });

            byStandard[standard] = {
                assessments: standardAssessments.length,
                score:
                    standardAssessments.reduce((sum, a) => sum + a.overallScore, 0) /
                    (standardAssessments.length || 1),
                issues: standardIssues.length,
            };
        });

        // By severity
        const bySeverity: Record<ComplianceIssueSeverity, number> = {} as any;
        Object.values(ComplianceIssueSeverity).forEach((severity) => {
            bySeverity[severity] = issues.items.filter((i) => i.severity === severity).length;
        });

        // Recent trends
        const recentTrends = await this.generateTrends(ComplianceStandard.SOC2, 30);

        return {
            totalAssessments: assessments.total,
            totalRequirements: store.getRequirementsByStandard(ComplianceStandard.SOC2).length,
            totalIssues: issues.total,
            criticalIssues,
            openIssues,
            resolvedIssues,
            averageScore: avgScore,
            complianceRate,
            byStandard,
            bySeverity,
            recentTrends,
        };
    }

    /**
     * Helper: Count audit events in date range
     */
    private static async countAuditEvents(startDate: string, endDate: string): Promise<number> {
        const result = await AuditService.queryAuditTrail({
            // Empty filter gets all events
        });
        // Filter by date range (simple implementation)
        return result.events.filter((e) => e.timestamp >= startDate && e.timestamp <= endDate).length;
    }

    /**
     * Helper: Generate compliance trends
     */
    private static async generateTrends(
        standard: ComplianceStandard,
        days: number
    ): Promise<ComplianceTrend[]> {
        const trends: ComplianceTrend[] = [];
        const now = Date.now();

        for (let i = days - 1; i >= 0; i--) {
            const date = new Date(now - i * 24 * 60 * 60 * 1000).toISOString().split('T')[0];

            // Simulate trend data
            trends.push({
                date,
                overallScore: 75 + Math.random() * 20,
                compliantCount: Math.floor(Math.random() * 10) + 5,
                nonCompliantCount: Math.floor(Math.random() * 5),
                criticalIssues: Math.floor(Math.random() * 3),
            });
        }

        return trends;
    }

    /**
     * Helper: Generate report sections
     */
    private static generateReportSections(
        standard: ComplianceStandard,
        assessment: ComplianceAssessment,
        reportType: string
    ): any[] {
        // Simplified section generation
        return [
            {
                sectionId: 'executive-summary',
                title: 'Executive Summary',
                content: `Overall compliance score: ${assessment.overallScore.toFixed(1)}%`,
            },
            {
                sectionId: 'detailed-findings',
                title: 'Detailed Findings',
                content: `${assessment.requirements.length} requirements assessed`,
            },
        ];
    }

    /**
     * Helper: Generate recommendations
     */
    private static generateRecommendations(results: ComplianceCheckResult[]): string[] {
        const recommendations: string[] = [];

        const nonCompliant = results.filter((r) => r.status === ComplianceStatus.NON_COMPLIANT);
        if (nonCompliant.length > 0) {
            recommendations.push(`Address ${nonCompliant.length} non-compliant requirements`);
        }

        const partial = results.filter((r) => r.status === ComplianceStatus.PARTIALLY_COMPLIANT);
        if (partial.length > 0) {
            recommendations.push(`Improve ${partial.length} partially compliant requirements`);
        }

        return recommendations;
    }

    /**
     * Helper: Determine compliance status (simulated)
     */
    private static determineComplianceStatus(requirement: ComplianceRequirement): ComplianceStatus {
        // Simulate status based on priority
        const rand = Math.random();
        if (requirement.priority === ComplianceIssueSeverity.CRITICAL) {
            return rand > 0.2 ? ComplianceStatus.COMPLIANT : ComplianceStatus.NON_COMPLIANT;
        } else if (requirement.priority === ComplianceIssueSeverity.HIGH) {
            return rand > 0.3 ? ComplianceStatus.COMPLIANT : ComplianceStatus.PARTIALLY_COMPLIANT;
        } else {
            return rand > 0.1 ? ComplianceStatus.COMPLIANT : ComplianceStatus.PARTIALLY_COMPLIANT;
        }
    }

    /**
     * Helper: Calculate compliance score
     */
    private static calculateComplianceScore(status: ComplianceStatus): number {
        switch (status) {
            case ComplianceStatus.COMPLIANT:
                return 100;
            case ComplianceStatus.PARTIALLY_COMPLIANT:
                return 70;
            case ComplianceStatus.NON_COMPLIANT:
                return 30;
            default:
                return 0;
        }
    }

    /**
     * Helper: Generate issues (simulated)
     */
    private static generateIssues(requirement: ComplianceRequirement): ComplianceIssue[] {
        if (Math.random() > 0.5) {
            return [];
        }

        return [
            {
                issueId: `issue-${Date.now()}`,
                requirementId: requirement.requirementId,
                severity: requirement.priority,
                title: `Issue with ${requirement.title}`,
                description: 'Automated check detected potential compliance issue',
                affectedResources: ['resource-1', 'resource-2'],
                detectedAt: new Date().toISOString(),
            },
        ];
    }

    /**
     * Helper: Export to PDF (simulated)
     */
    private static async exportToPDF(report: ComplianceReport, options: ExportOptions): Promise<Blob> {
        const content = JSON.stringify(report, null, 2);
        return new Blob([content], { type: 'application/pdf' });
    }

    /**
     * Helper: Export to CSV (simulated)
     */
    private static async exportToCSV(report: ComplianceReport, options: ExportOptions): Promise<Blob> {
        let csv = 'Requirement,Status,Score\n';
        report.assessment.requirements.forEach((req) => {
            csv += `${req.requirementId},${req.status},${req.score}\n`;
        });
        return new Blob([csv], { type: 'text/csv' });
    }

    /**
     * Helper: Export to JSON
     */
    private static async exportToJSON(report: ComplianceReport, options: ExportOptions): Promise<Blob> {
        const content = JSON.stringify(report, null, 2);
        return new Blob([content], { type: 'application/json' });
    }

    /**
     * Helper: Export to Excel (simulated)
     */
    private static async exportToExcel(report: ComplianceReport, options: ExportOptions): Promise<Blob> {
        const content = JSON.stringify(report, null, 2);
        return new Blob([content], {
            type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        });
    }

    /**
     * Clear all compliance data (for testing)
     */
    static clearAll(): void {
        store.clear();
    }
}
