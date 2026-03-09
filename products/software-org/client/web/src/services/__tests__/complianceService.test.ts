import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComplianceService } from '../complianceService';
import {
    ComplianceStandard,
    ComplianceStatus,
    ComplianceIssueSeverity,
    ApprovalStatus,
    ExportFormat,
    type ComplianceReport,
    type ComplianceAssessment,
    type ApprovalRequest,
} from '../../types/compliance';

describe('ComplianceService', () => {
    beforeEach(() => {
        // Tests use isolated instances - no need to clear global store
        vi.clearAllMocks();
    });

    describe('Report Generation', () => {
        it('should generate SOC2 compliance report', async () => {
            const report = await ComplianceService.generateReport(ComplianceStandard.SOC2);

            expect(report).toBeDefined();
            expect(report.reportId).toBeDefined();
            expect(report.standard).toBe(ComplianceStandard.SOC2);
            expect(report.generatedAt).toBeDefined();
            expect(report.assessment).toBeDefined();
            expect(report.sections.length).toBeGreaterThan(0);
        });

        it('should generate HIPAA compliance report', async () => {
            const report = await ComplianceService.generateReport(ComplianceStandard.HIPAA);

            expect(report.standard).toBe(ComplianceStandard.HIPAA);
            expect(report.sections).toBeDefined();
            expect(report.assessment.overallStatus).toMatch(/compliant|non_compliant|partially_compliant/);
        });

        it('should generate GDPR compliance report', async () => {
            const report = await ComplianceService.generateReport(ComplianceStandard.GDPR);

            expect(report.standard).toBe(ComplianceStandard.GDPR);
            expect(report.sections.length).toBeGreaterThan(0);
        });

        it('should generate PCI-DSS compliance report', async () => {
            const report = await ComplianceService.generateReport(ComplianceStandard.PCI_DSS);

            expect(report.standard).toBe(ComplianceStandard.PCI_DSS);
            expect(report.sections.length).toBeGreaterThan(0);
        });

        it('should calculate compliance scores correctly', async () => {
            const report = await ComplianceService.generateReport(ComplianceStandard.SOC2);

            expect(report.assessment.overallScore).toBeGreaterThanOrEqual(0);
            expect(report.assessment.overallScore).toBeLessThanOrEqual(100);
            expect(typeof report.assessment.overallScore).toBe('number');
        });

        it('should include issues in report', async () => {
            const report = await ComplianceService.generateReport(ComplianceStandard.SOC2);

            expect(typeof report.criticalIssues).toBe('number');
            expect(typeof report.openIssues).toBe('number');
            expect(typeof report.resolvedIssues).toBe('number');
        });

        it('should include trends in report', async () => {
            const report = await ComplianceService.generateReport(ComplianceStandard.SOC2);

            expect(Array.isArray(report.trends)).toBe(true);
            report.trends.forEach(trend => {
                expect(trend.date).toBeDefined();
                expect(typeof trend.score).toBe('number');
                expect(trend.status).toBeDefined();
            });
        });

        it('should track audit events', async () => {
            const report = await ComplianceService.generateReport(ComplianceStandard.SOC2);

            expect(typeof report.auditEvents).toBe('number');
            expect(report.auditEvents).toBeGreaterThanOrEqual(0);
        });
    });

    describe('Report Retrieval', () => {
        it('should retrieve all reports', async () => {
            await ComplianceService.generateReport(ComplianceFramework.SOC2);
            await ComplianceService.generateReport(ComplianceFramework.HIPAA);
            await ComplianceService.generateReport(ComplianceFramework.GDPR);

            const reports = await ComplianceService.getReports();

            expect(reports.length).toBeGreaterThanOrEqual(3);
        });

        it('should filter reports by framework', async () => {
            await ComplianceService.generateReport(ComplianceFramework.SOC2);
            await ComplianceService.generateReport(ComplianceFramework.HIPAA);
            await ComplianceService.generateReport(ComplianceFramework.SOC2);

            const soc2Reports = await ComplianceService.getReports({
                framework: ComplianceFramework.SOC2,
            });

            expect(soc2Reports.length).toBe(2);
            soc2Reports.forEach(report => {
                expect(report.framework).toBe(ComplianceFramework.SOC2);
            });
        });

        it('should filter reports by status', async () => {
            await ComplianceService.generateReport(ComplianceFramework.SOC2);
            await ComplianceService.generateReport(ComplianceFramework.HIPAA);

            const reports = await ComplianceService.getReports({
                status: ComplianceStatus.COMPLIANT,
            });

            reports.forEach(report => {
                expect(report.overallStatus).toBe(ComplianceStatus.COMPLIANT);
            });
        });

        it('should filter reports by date range', async () => {
            const startDate = new Date('2024-01-01').toISOString();
            const endDate = new Date('2024-12-31').toISOString();

            await ComplianceService.generateReport(ComplianceFramework.SOC2);

            const reports = await ComplianceService.getReports({
                startDate,
                endDate,
            });

            reports.forEach(report => {
                const reportDate = new Date(report.generatedAt);
                expect(reportDate.getTime()).toBeGreaterThanOrEqual(new Date(startDate).getTime());
                expect(reportDate.getTime()).toBeLessThanOrEqual(new Date(endDate).getTime());
            });
        });

        it('should return null for non-existent report', async () => {
            const report = await ComplianceService.getReport('non-existent-id');
            expect(report).toBeNull();
        });
    });

    describe('Compliance Assessment', () => {
        it('should create compliance assessment', async () => {
            const assessment = await ComplianceService.createAssessment({
                framework: ComplianceFramework.SOC2,
                scope: ['access-control', 'data-protection'],
                assessors: ['assessor1', 'assessor2'],
                targetDate: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString(),
            });

            expect(assessment).toBeDefined();
            expect(assessment.assessmentId).toBeDefined();
            expect(assessment.framework).toBe(ComplianceFramework.SOC2);
            expect(assessment.status).toBe(AssessmentStatus.PLANNING);
            expect(assessment.progress).toBe(0);
        });

        it('should retrieve assessment by id', async () => {
            const created = await ComplianceService.createAssessment({
                framework: ComplianceFramework.HIPAA,
                scope: ['privacy'],
                assessors: ['assessor1'],
                targetDate: new Date().toISOString(),
            });

            const retrieved = await ComplianceService.getAssessment(created.assessmentId);

            expect(retrieved).toEqual(created);
        });

        it('should update assessment status', async () => {
            const assessment = await ComplianceService.createAssessment({
                framework: ComplianceFramework.GDPR,
                scope: ['data-rights'],
                assessors: ['assessor1'],
                targetDate: new Date().toISOString(),
            });

            const updated = await ComplianceService.updateAssessment(assessment.assessmentId, {
                status: AssessmentStatus.IN_PROGRESS,
                progress: 50,
            });

            expect(updated.status).toBe(AssessmentStatus.IN_PROGRESS);
            expect(updated.progress).toBe(50);
        });

        it('should track assessment progress', async () => {
            const assessment = await ComplianceService.createAssessment({
                framework: ComplianceFramework.SOC2,
                scope: ['controls'],
                assessors: ['assessor1'],
                targetDate: new Date().toISOString(),
            });

            const updated = await ComplianceService.updateAssessment(assessment.assessmentId, {
                progress: 75,
            });

            expect(updated.progress).toBe(75);
            expect(updated.progress).toBeGreaterThanOrEqual(0);
            expect(updated.progress).toBeLessThanOrEqual(100);
        });

        it('should complete assessment', async () => {
            const assessment = await ComplianceService.createAssessment({
                framework: ComplianceFramework.SOC2,
                scope: ['controls'],
                assessors: ['assessor1'],
                targetDate: new Date().toISOString(),
            });

            const completed = await ComplianceService.updateAssessment(assessment.assessmentId, {
                status: AssessmentStatus.COMPLETED,
                progress: 100,
                completedAt: new Date().toISOString(),
            });

            expect(completed.status).toBe(AssessmentStatus.COMPLETED);
            expect(completed.progress).toBe(100);
            expect(completed.completedAt).toBeDefined();
        });

        it('should list all assessments', async () => {
            await ComplianceService.createAssessment({
                framework: ComplianceFramework.SOC2,
                scope: ['controls'],
                assessors: ['assessor1'],
                targetDate: new Date().toISOString(),
            });

            await ComplianceService.createAssessment({
                framework: ComplianceFramework.HIPAA,
                scope: ['privacy'],
                assessors: ['assessor2'],
                targetDate: new Date().toISOString(),
            });

            const assessments = await ComplianceService.getAssessments();

            expect(assessments.length).toBeGreaterThanOrEqual(2);
        });

        it('should filter assessments by framework', async () => {
            await ComplianceService.createAssessment({
                framework: ComplianceFramework.SOC2,
                scope: ['controls'],
                assessors: ['assessor1'],
                targetDate: new Date().toISOString(),
            });

            const assessments = await ComplianceService.getAssessments({
                framework: ComplianceFramework.SOC2,
            });

            assessments.forEach(assessment => {
                expect(assessment.framework).toBe(ComplianceFramework.SOC2);
            });
        });

        it('should filter assessments by status', async () => {
            await ComplianceService.createAssessment({
                framework: ComplianceFramework.SOC2,
                scope: ['controls'],
                assessors: ['assessor1'],
                targetDate: new Date().toISOString(),
            });

            const assessments = await ComplianceService.getAssessments({
                status: AssessmentStatus.PLANNING,
            });

            assessments.forEach(assessment => {
                expect(assessment.status).toBe(AssessmentStatus.PLANNING);
            });
        });
    });

    describe('Approval Workflow', () => {
        it('should create approval workflow', async () => {
            const workflow = await ComplianceService.createApprovalWorkflow({
                type: 'permission_change',
                requestedBy: 'user1',
                title: 'Add admin permission',
                description: 'Request to add admin permission',
                requiredApprovers: ['manager1', 'security1'],
                metadata: { permissionId: 'perm1', action: 'grant' },
            });

            expect(workflow).toBeDefined();
            expect(workflow.workflowId).toBeDefined();
            expect(workflow.status).toBe(ApprovalStatus.PENDING);
            expect(workflow.currentStep).toBe(0);
            expect(workflow.steps.length).toBeGreaterThan(0);
        });

        it('should approve workflow step', async () => {
            const workflow = await ComplianceService.createApprovalWorkflow({
                type: 'role_assignment',
                requestedBy: 'user1',
                title: 'Assign manager role',
                description: 'Request to assign manager role',
                requiredApprovers: ['manager1'],
                metadata: { roleId: 'role1' },
            });

            const approved = await ComplianceService.approveWorkflowStep(
                workflow.workflowId,
                'manager1',
                'Approved - looks good'
            );

            expect(approved.steps[0].status).toBe(ApprovalStatus.APPROVED);
            expect(approved.steps[0].approvedBy).toBe('manager1');
            expect(approved.steps[0].comments).toBe('Approved - looks good');
        });

        it('should reject workflow step', async () => {
            const workflow = await ComplianceService.createApprovalWorkflow({
                type: 'permission_change',
                requestedBy: 'user1',
                title: 'Remove permission',
                description: 'Request to remove permission',
                requiredApprovers: ['manager1'],
                metadata: { permissionId: 'perm1' },
            });

            const rejected = await ComplianceService.rejectWorkflowStep(
                workflow.workflowId,
                'manager1',
                'Insufficient justification'
            );

            expect(rejected.status).toBe(ApprovalStatus.REJECTED);
            expect(rejected.steps[0].status).toBe(ApprovalStatus.REJECTED);
            expect(rejected.steps[0].rejectedBy).toBe('manager1');
            expect(rejected.steps[0].comments).toBe('Insufficient justification');
        });

        it('should complete workflow after all approvals', async () => {
            const workflow = await ComplianceService.createApprovalWorkflow({
                type: 'role_assignment',
                requestedBy: 'user1',
                title: 'Assign role',
                description: 'Request to assign role',
                requiredApprovers: ['manager1', 'security1'],
                metadata: { roleId: 'role1' },
            });

            const step1 = await ComplianceService.approveWorkflowStep(
                workflow.workflowId,
                'manager1',
                'Approved'
            );

            const completed = await ComplianceService.approveWorkflowStep(
                step1.workflowId,
                'security1',
                'Approved'
            );

            expect(completed.status).toBe(ApprovalStatus.APPROVED);
            expect(completed.completedAt).toBeDefined();
        });

        it('should retrieve workflow by id', async () => {
            const created = await ComplianceService.createApprovalWorkflow({
                type: 'permission_change',
                requestedBy: 'user1',
                title: 'Change permission',
                description: 'Request to change permission',
                requiredApprovers: ['manager1'],
                metadata: {},
            });

            const retrieved = await ComplianceService.getWorkflow(created.workflowId);

            expect(retrieved).toEqual(created);
        });

        it('should list all workflows', async () => {
            await ComplianceService.createApprovalWorkflow({
                type: 'permission_change',
                requestedBy: 'user1',
                title: 'Change 1',
                description: 'Request 1',
                requiredApprovers: ['manager1'],
                metadata: {},
            });

            await ComplianceService.createApprovalWorkflow({
                type: 'role_assignment',
                requestedBy: 'user2',
                title: 'Change 2',
                description: 'Request 2',
                requiredApprovers: ['manager2'],
                metadata: {},
            });

            const workflows = await ComplianceService.getWorkflows();

            expect(workflows.length).toBeGreaterThanOrEqual(2);
        });

        it('should filter workflows by status', async () => {
            await ComplianceService.createApprovalWorkflow({
                type: 'permission_change',
                requestedBy: 'user1',
                title: 'Change',
                description: 'Request',
                requiredApprovers: ['manager1'],
                metadata: {},
            });

            const workflows = await ComplianceService.getWorkflows({
                status: ApprovalStatus.PENDING,
            });

            workflows.forEach(workflow => {
                expect(workflow.status).toBe(ApprovalStatus.PENDING);
            });
        });

        it('should filter workflows by type', async () => {
            await ComplianceService.createApprovalWorkflow({
                type: 'role_assignment',
                requestedBy: 'user1',
                title: 'Assign role',
                description: 'Request',
                requiredApprovers: ['manager1'],
                metadata: {},
            });

            const workflows = await ComplianceService.getWorkflows({
                type: 'role_assignment',
            });

            workflows.forEach(workflow => {
                expect(workflow.type).toBe('role_assignment');
            });
        });
    });

    describe('Report Export', () => {
        let testReport: ComplianceReport;

        beforeEach(async () => {
            testReport = await ComplianceService.generateReport(ComplianceFramework.SOC2);
        });

        it('should export report to CSV', async () => {
            const exported = await ComplianceService.exportReport(
                testReport.reportId,
                ExportFormat.CSV
            );

            expect(exported).toBeDefined();
            expect(exported.format).toBe(ExportFormat.CSV);
            expect(exported.data).toBeDefined();
            expect(typeof exported.data).toBe('string');
            expect(exported.data).toContain('Control ID');
        });

        it('should export report to JSON', async () => {
            const exported = await ComplianceService.exportReport(
                testReport.reportId,
                ExportFormat.JSON
            );

            expect(exported.format).toBe(ExportFormat.JSON);
            expect(typeof exported.data).toBe('string');

            const parsed = JSON.parse(exported.data);
            expect(parsed.reportId).toBe(testReport.reportId);
            expect(parsed.framework).toBe(testReport.framework);
        });

        it('should export report to PDF', async () => {
            const exported = await ComplianceService.exportReport(
                testReport.reportId,
                ExportFormat.PDF
            );

            expect(exported.format).toBe(ExportFormat.PDF);
            expect(exported.data).toBeDefined();
            expect(exported.filename).toContain('.pdf');
        });

        it('should export report to Excel', async () => {
            const exported = await ComplianceService.exportReport(
                testReport.reportId,
                ExportFormat.EXCEL
            );

            expect(exported.format).toBe(ExportFormat.EXCEL);
            expect(exported.data).toBeDefined();
            expect(exported.filename).toContain('.xlsx');
        });

        it('should generate unique filename', async () => {
            const export1 = await ComplianceService.exportReport(
                testReport.reportId,
                ExportFormat.CSV
            );

            const export2 = await ComplianceService.exportReport(
                testReport.reportId,
                ExportFormat.CSV
            );

            expect(export1.filename).not.toBe(export2.filename);
        });

        it('should include framework in filename', async () => {
            const exported = await ComplianceService.exportReport(
                testReport.reportId,
                ExportFormat.CSV
            );

            expect(exported.filename).toContain('soc2');
        });

        it('should throw error for non-existent report', async () => {
            await expect(
                ComplianceService.exportReport('non-existent', ExportFormat.CSV)
            ).rejects.toThrow('Report not found');
        });
    });

    describe('Compliance Trends', () => {
        it('should generate compliance trends', async () => {
            await ComplianceService.generateReport(ComplianceFramework.SOC2);
            await ComplianceService.generateReport(ComplianceFramework.SOC2);
            await ComplianceService.generateReport(ComplianceFramework.SOC2);

            const trends = await ComplianceService.getComplianceTrends(
                ComplianceFramework.SOC2,
                new Date(Date.now() - 90 * 24 * 60 * 60 * 1000).toISOString(),
                new Date().toISOString()
            );

            expect(trends).toBeDefined();
            expect(trends.framework).toBe(ComplianceFramework.SOC2);
            expect(trends.period).toBeDefined();
            expect(trends.scoreHistory.length).toBeGreaterThan(0);
        });

        it('should calculate average score', async () => {
            await ComplianceService.generateReport(ComplianceFramework.HIPAA);

            const trends = await ComplianceService.getComplianceTrends(
                ComplianceFramework.HIPAA,
                new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString(),
                new Date().toISOString()
            );

            expect(trends.averageScore).toBeGreaterThanOrEqual(0);
            expect(trends.averageScore).toBeLessThanOrEqual(100);
        });

        it('should identify trend direction', async () => {
            await ComplianceService.generateReport(ComplianceFramework.GDPR);

            const trends = await ComplianceService.getComplianceTrends(
                ComplianceFramework.GDPR,
                new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString(),
                new Date().toISOString()
            );

            expect(trends.trend).toMatch(/improving|declining|stable/);
        });

        it('should track control compliance over time', async () => {
            await ComplianceService.generateReport(ComplianceFramework.SOC2);

            const trends = await ComplianceService.getComplianceTrends(
                ComplianceFramework.SOC2,
                new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString(),
                new Date().toISOString()
            );

            expect(Array.isArray(trends.controlTrends)).toBe(true);
            trends.controlTrends.forEach(controlTrend => {
                expect(controlTrend.controlId).toBeDefined();
                expect(Array.isArray(controlTrend.complianceHistory)).toBe(true);
            });
        });
    });

    describe('Performance', () => {
        it('should generate report within 500ms', async () => {
            const start = Date.now();
            await ComplianceService.generateReport(ComplianceFramework.SOC2);
            const duration = Date.now() - start;

            expect(duration).toBeLessThan(500);
        });

        it('should retrieve reports within 100ms', async () => {
            await ComplianceService.generateReport(ComplianceFramework.SOC2);

            const start = Date.now();
            await ComplianceService.getReports();
            const duration = Date.now() - start;

            expect(duration).toBeLessThan(100);
        });

        it('should export report within 300ms', async () => {
            const report = await ComplianceService.generateReport(ComplianceFramework.SOC2);

            const start = Date.now();
            await ComplianceService.exportReport(report.reportId, ExportFormat.CSV);
            const duration = Date.now() - start;

            expect(duration).toBeLessThan(300);
        });
    });

    describe('Error Handling', () => {
        it('should handle invalid report id', async () => {
            const report = await ComplianceService.getReport('invalid-id');
            expect(report).toBeNull();
        });

        it('should handle invalid assessment id', async () => {
            const assessment = await ComplianceService.getAssessment('invalid-id');
            expect(assessment).toBeNull();
        });

        it('should handle invalid workflow id', async () => {
            const workflow = await ComplianceService.getWorkflow('invalid-id');
            expect(workflow).toBeNull();
        });

        it('should throw error for export with invalid report id', async () => {
            await expect(
                ComplianceService.exportReport('invalid-id', ExportFormat.CSV)
            ).rejects.toThrow('Report not found');
        });
    });
});
