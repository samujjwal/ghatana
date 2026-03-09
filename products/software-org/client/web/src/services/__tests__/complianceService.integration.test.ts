import { describe, it, expect, beforeEach } from 'vitest';
import { ComplianceService } from '../complianceService';
import { ComplianceStandard, ExportFormat } from '../../types/compliance';

describe('ComplianceService - Integration Tests', () => {
    describe('Report Generation', () => {
        it('should generate SOC2 compliance report', async () => {
            const report = await ComplianceService.generateReport(ComplianceStandard.SOC2);

            expect(report).toBeDefined();
            expect(report.reportId).toBeDefined();
            expect(report.standard).toBe(ComplianceStandard.SOC2);
            expect(report.generatedAt).toBeDefined();
            expect(report.title).toContain('SOC2');
        });

        it('should generate HIPAA compliance report', async () => {
            const report = await ComplianceService.generateReport(ComplianceStandard.HIPAA);

            expect(report.standard).toBe(ComplianceStandard.HIPAA);
            expect(report.title).toContain('HIPAA');
        });

        it('should generate GDPR compliance report', async () => {
            const report = await ComplianceService.generateReport(ComplianceStandard.GDPR);

            expect(report.standard).toBe(ComplianceStandard.GDPR);
            expect(report.title).toContain('GDPR');
        });

        it('should generate PCI-DSS compliance report', async () => {
            const report = await ComplianceService.generateReport(ComplianceStandard.PCI_DSS);

            expect(report.standard).toBe(ComplianceStandard.PCI_DSS);
            expect(report.title).toContain('PCI-DSS');
        });

        it('should include assessment in report', async () => {
            const report = await ComplianceService.generateReport(ComplianceStandard.SOC2);

            expect(report.assessment).toBeDefined();
            expect(report.assessment.assessmentId).toBeDefined();
            expect(typeof report.assessment.overallScore).toBe('number');
            expect(report.assessment.overallScore).toBeGreaterThanOrEqual(0);
            expect(report.assessment.overallScore).toBeLessThanOrEqual(100);
        });

        it('should track issues in report', async () => {
            const report = await ComplianceService.generateReport(ComplianceStandard.SOC2);

            expect(typeof report.criticalIssues).toBe('number');
            expect(typeof report.openIssues).toBe('number');
            expect(typeof report.resolvedIssues).toBe('number');
            expect(report.criticalIssues).toBeGreaterThanOrEqual(0);
            expect(report.openIssues).toBeGreaterThanOrEqual(0);
            expect(report.resolvedIssues).toBeGreaterThanOrEqual(0);
        });

        it('should track audit events', async () => {
            const report = await ComplianceService.generateReport(ComplianceStandard.SOC2);

            expect(typeof report.auditEvents).toBe('number');
            expect(report.auditEvents).toBeGreaterThanOrEqual(0);
        });
    });

    describe('Compliance Assessment', () => {
        it('should run compliance assessment', async () => {
            const assessment = await ComplianceService.runAssessment(ComplianceStandard.SOC2);

            expect(assessment).toBeDefined();
            expect(assessment.assessmentId).toBeDefined();
            expect(assessment.standard).toBe(ComplianceStandard.SOC2);
            expect(assessment.startDate).toBeDefined();
        });

        it('should calculate assessment scores', async () => {
            const assessment = await ComplianceService.runAssessment(ComplianceStandard.HIPAA);

            expect(typeof assessment.overallScore).toBe('number');
            expect(assessment.overallScore).toBeGreaterThanOrEqual(0);
            expect(assessment.overallScore).toBeLessThanOrEqual(100);
        });

        it('should include check results', async () => {
            const assessment = await ComplianceService.runAssessment(ComplianceStandard.GDPR);

            expect(Array.isArray(assessment.checkResults)).toBe(true);
            expect(assessment.checkResults.length).toBeGreaterThan(0);
        });
    });

    describe('Compliance Checks', () => {
        it('should run compliance checks', async () => {
            const results = await ComplianceService.runComplianceChecks(ComplianceStandard.SOC2);

            expect(Array.isArray(results)).toBe(true);
            results.forEach(result => {
                expect(result.checkId).toBeDefined();
                expect(result.requirementId).toBeDefined();
                expect(result.passed).toBeDefined();
            });
        });

        it('should check access controls', async () => {
            const results = await ComplianceService.checkAccessControls(ComplianceStandard.SOC2);

            expect(Array.isArray(results)).toBe(true);
            results.forEach(result => {
                expect(typeof result.passed).toBe('boolean');
            });
        });

        it('should check data protection', async () => {
            const results = await ComplianceService.checkDataProtection(ComplianceStandard.HIPAA);

            expect(Array.isArray(results)).toBe(true);
            results.forEach(result => {
                expect(typeof result.passed).toBe('boolean');
            });
        });
    });

    describe('Report Export', () => {
        it('should export report to PDF', async () => {
            const report = await ComplianceService.generateReport(ComplianceStandard.SOC2);
            const blob = await ComplianceService.exportReport(report.reportId, {
                format: ExportFormat.PDF,
            });

            expect(blob).toBeDefined();
            expect(blob instanceof Blob).toBe(true);
            expect(blob.size).toBeGreaterThan(0);
        });

        it('should export report to CSV', async () => {
            const report = await ComplianceService.generateReport(ComplianceStandard.SOC2);
            const blob = await ComplianceService.exportReport(report.reportId, {
                format: ExportFormat.CSV,
            });

            expect(blob).toBeDefined();
            expect(blob instanceof Blob).toBe(true);
            expect(blob.size).toBeGreaterThan(0);
        });

        it('should export report to JSON', async () => {
            const report = await ComplianceService.generateReport(ComplianceStandard.SOC2);
            const blob = await ComplianceService.exportReport(report.reportId, {
                format: ExportFormat.JSON,
            });

            expect(blob).toBeDefined();
            expect(blob instanceof Blob).toBe(true);
            expect(blob.size).toBeGreaterThan(0);
        });

        it('should export report to Excel', async () => {
            const report = await ComplianceService.generateReport(ComplianceStandard.SOC2);
            const blob = await ComplianceService.exportReport(report.reportId, {
                format: ExportFormat.EXCEL,
            });

            expect(blob).toBeDefined();
            expect(blob instanceof Blob).toBe(true);
            expect(blob.size).toBeGreaterThan(0);
        });
    });

    describe('Dashboard', () => {
        it('should get compliance dashboard', async () => {
            const dashboard = await ComplianceService.getDashboard();

            expect(dashboard).toBeDefined();
            expect(Array.isArray(dashboard.recentReports)).toBe(true);
            expect(typeof dashboard.totalReports).toBe('number');
            expect(typeof dashboard.openIssues).toBe('number');
            expect(typeof dashboard.criticalIssues).toBe('number');
        });

        it('should include statistics', async () => {
            const dashboard = await ComplianceService.getDashboard();

            expect(dashboard.stats).toBeDefined();
            expect(typeof dashboard.stats.averageScore).toBe('number');
            expect(dashboard.stats.averageScore).toBeGreaterThanOrEqual(0);
            expect(dashboard.stats.averageScore).toBeLessThanOrEqual(100);
        });
    });

    describe('Performance', () => {
        it('should generate report within 2 seconds', async () => {
            const start = Date.now();
            await ComplianceService.generateReport(ComplianceStandard.SOC2);
            const duration = Date.now() - start;

            expect(duration).toBeLessThan(2000);
        });

        it('should run compliance checks within 500ms', async () => {
            const start = Date.now();
            await ComplianceService.runComplianceChecks(ComplianceStandard.SOC2);
            const duration = Date.now() - start;

            expect(duration).toBeLessThan(500);
        });

        it('should get dashboard within 200ms', async () => {
            const start = Date.now();
            await ComplianceService.getDashboard();
            const duration = Date.now() - start;

            expect(duration).toBeLessThan(200);
        });

        it('should export report within 3 seconds', async () => {
            const report = await ComplianceService.generateReport(ComplianceStandard.SOC2);

            const start = Date.now();
            await ComplianceService.exportReport(report.reportId, { format: ExportFormat.PDF });
            const duration = Date.now() - start;

            expect(duration).toBeLessThan(3000);
        });
    });
});
