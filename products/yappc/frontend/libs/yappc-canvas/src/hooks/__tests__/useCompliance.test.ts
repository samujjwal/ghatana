/**
 * Compliance Hook Tests
 * 
 * @doc.type test
 * @doc.purpose Comprehensive tests for compliance management functionality
 * @doc.layer product
 * @doc.pattern Unit Tests
 * 
 * Test coverage:
 * - Control management (CRUD operations)
 * - Evidence collection and tracking
 * - Gap analysis and remediation
 * - Risk scoring and factors
 * - Compliance metrics and overview
 * - Export functionality (PDF, CSV, JSON)
 * - Multi-framework scenarios (SOC2, HIPAA, GDPR, PCI-DSS, ISO 27001)
 */

import { renderHook, act } from '@testing-library/react';
import { useCompliance, type ComplianceFramework, type ControlStatus } from '../useCompliance';

describe('useCompliance', () => {
    describe('Initialization', () => {
        it('should initialize with default values', () => {
            const { result } = renderHook(() => useCompliance());

            expect(result.current.programName).toBe('Compliance Program');
            expect(result.current.selectedFramework).toBe('soc2');
            expect(result.current.controls).toEqual([]);
            expect(result.current.evidence).toEqual([]);
            expect(result.current.getControlCount()).toBe(0);
        });

        it('should allow setting program name', () => {
            const { result } = renderHook(() => useCompliance());

            act(() => {
                result.current.setProgramName('Enterprise Compliance Program');
            });

            expect(result.current.programName).toBe('Enterprise Compliance Program');
        });

        it('should allow changing framework', () => {
            const { result } = renderHook(() => useCompliance());

            act(() => {
                result.current.setSelectedFramework('hipaa');
            });

            expect(result.current.selectedFramework).toBe('hipaa');
        });
    });

    describe('Control Management - SOC2', () => {
        it('should add a SOC2 control', () => {
            const { result } = renderHook(() => useCompliance());

            let controlId: string = '';
            act(() => {
                controlId = result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC1.1',
                    title: 'COSO Principle 1',
                    description: 'The entity demonstrates a commitment to integrity and ethical values',
                    category: 'Control Environment',
                    status: 'not-implemented',
                });
            });

            expect(controlId).toBeTruthy();
            expect(result.current.getControlCount()).toBe(1);
            expect(result.current.controls[0]).toMatchObject({
                framework: 'soc2',
                controlId: 'CC1.1',
                title: 'COSO Principle 1',
                status: 'not-implemented',
            });
        });

        it('should update control status', () => {
            const { result } = renderHook(() => useCompliance());

            let controlId: string = '';
            act(() => {
                controlId = result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC2.1',
                    title: 'Security Policies',
                    description: 'Security policies exist',
                    category: 'Communication',
                    status: 'not-implemented',
                });
            });

            act(() => {
                result.current.updateControl(controlId, { status: 'in-progress' });
            });

            const control = result.current.getControl(controlId);
            expect(control?.status).toBe('in-progress');
        });

        it('should mark control as implemented with timestamp', () => {
            const { result } = renderHook(() => useCompliance());

            let controlId: string = '';
            act(() => {
                controlId = result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC3.1',
                    title: 'Access Controls',
                    description: 'Access controls are in place',
                    category: 'Access Control',
                    status: 'in-progress',
                });
            });

            act(() => {
                result.current.updateControl(controlId, { status: 'implemented' });
            });

            const control = result.current.getControl(controlId);
            expect(control?.status).toBe('implemented');
            expect(control?.implementedAt).toBeDefined();
        });

        it('should delete a control', () => {
            const { result } = renderHook(() => useCompliance());

            let controlId: string = '';
            act(() => {
                controlId = result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC4.1',
                    title: 'Test Control',
                    description: 'Test',
                    category: 'Test',
                    status: 'not-implemented',
                });
            });

            expect(result.current.getControlCount()).toBe(1);

            act(() => {
                result.current.deleteControl(controlId);
            });

            expect(result.current.getControlCount()).toBe(0);
            expect(result.current.getControl(controlId)).toBeUndefined();
        });
    });

    describe('Control Management - HIPAA', () => {
        it('should add HIPAA controls', () => {
            const { result } = renderHook(() => useCompliance());

            act(() => {
                result.current.addControl({
                    framework: 'hipaa',
                    controlId: '164.308(a)(1)(i)',
                    title: 'Security Management Process',
                    description: 'Implement policies and procedures',
                    category: 'Administrative Safeguards',
                    status: 'implemented',
                });
                result.current.addControl({
                    framework: 'hipaa',
                    controlId: '164.312(a)(1)',
                    title: 'Access Control',
                    description: 'Implement technical policies',
                    category: 'Technical Safeguards',
                    status: 'in-progress',
                });
            });

            const hipaaControls = result.current.getControlsByFramework('hipaa');
            expect(hipaaControls).toHaveLength(2);
            expect(hipaaControls.every((c) => c.framework === 'hipaa')).toBe(true);
        });
    });

    describe('Control Management - GDPR', () => {
        it('should add GDPR controls', () => {
            const { result } = renderHook(() => useCompliance());

            act(() => {
                result.current.addControl({
                    framework: 'gdpr',
                    controlId: 'Art. 32',
                    title: 'Security of Processing',
                    description: 'Implement appropriate technical measures',
                    category: 'Data Security',
                    status: 'implemented',
                });
            });

            const gdprControls = result.current.getControlsByFramework('gdpr');
            expect(gdprControls).toHaveLength(1);
            expect(gdprControls[0].framework).toBe('gdpr');
        });
    });

    describe('Control Management - PCI-DSS', () => {
        it('should add PCI-DSS controls', () => {
            const { result } = renderHook(() => useCompliance());

            act(() => {
                result.current.addControl({
                    framework: 'pci-dss',
                    controlId: '1.1',
                    title: 'Firewall Configuration',
                    description: 'Establish firewall standards',
                    category: 'Network Security',
                    status: 'implemented',
                });
            });

            const pciControls = result.current.getControlsByFramework('pci-dss');
            expect(pciControls).toHaveLength(1);
        });
    });

    describe('Control Management - ISO 27001', () => {
        it('should add ISO 27001 controls', () => {
            const { result } = renderHook(() => useCompliance());

            act(() => {
                result.current.addControl({
                    framework: 'iso-27001',
                    controlId: 'A.9.1.1',
                    title: 'Access Control Policy',
                    description: 'Define access control policy',
                    category: 'Access Control',
                    status: 'implemented',
                });
            });

            const isoControls = result.current.getControlsByFramework('iso-27001');
            expect(isoControls).toHaveLength(1);
        });
    });

    describe('Control Filtering', () => {
        it('should filter controls by status', () => {
            const { result } = renderHook(() => useCompliance());

            act(() => {
                result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC1.1',
                    title: 'Control 1',
                    description: '',
                    category: '',
                    status: 'not-implemented',
                });
                result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC1.2',
                    title: 'Control 2',
                    description: '',
                    category: '',
                    status: 'implemented',
                });
                result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC1.3',
                    title: 'Control 3',
                    description: '',
                    category: '',
                    status: 'implemented',
                });
            });

            const implementedControls = result.current.getControlsByStatus('implemented');
            expect(implementedControls).toHaveLength(2);

            const notImplementedControls = result.current.getControlsByStatus('not-implemented');
            expect(notImplementedControls).toHaveLength(1);
        });
    });

    describe('Evidence Management', () => {
        it('should add evidence to a control', () => {
            const { result } = renderHook(() => useCompliance());

            let controlId: string = '';
            act(() => {
                controlId = result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC1.1',
                    title: 'Test Control',
                    description: '',
                    category: '',
                    status: 'implemented',
                });
            });

            act(() => {
                result.current.addEvidence(controlId, {
                    type: 'document',
                    title: 'Security Policy',
                    description: 'Company security policy document',
                    url: 'https://example.com/security-policy.pdf',
                    uploadedAt: new Date().toISOString(),
                });
            });

            const evidence = result.current.getEvidenceForControl(controlId);
            expect(evidence).toHaveLength(1);
            expect(evidence[0]).toMatchObject({
                type: 'document',
                title: 'Security Policy',
                controlId,
            });
        });

        it('should add multiple evidence items', () => {
            const { result } = renderHook(() => useCompliance());

            let controlId: string = '';
            act(() => {
                controlId = result.current.addControl({
                    framework: 'hipaa',
                    controlId: '164.308(a)(1)(i)',
                    title: 'Security Management',
                    description: '',
                    category: '',
                    status: 'implemented',
                });
            });

            act(() => {
                result.current.addEvidence(controlId, {
                    type: 'document',
                    title: 'Risk Assessment',
                    description: 'Annual risk assessment',
                    uploadedAt: new Date().toISOString(),
                });
                result.current.addEvidence(controlId, {
                    type: 'screenshot',
                    title: 'System Configuration',
                    description: 'Security settings screenshot',
                    uploadedAt: new Date().toISOString(),
                });
                result.current.addEvidence(controlId, {
                    type: 'log',
                    title: 'Audit Logs',
                    description: 'System audit logs',
                    uploadedAt: new Date().toISOString(),
                });
            });

            const evidence = result.current.getEvidenceForControl(controlId);
            expect(evidence).toHaveLength(3);
        });

        it('should update evidence', () => {
            const { result } = renderHook(() => useCompliance());

            let controlId: string = '';
            let evidenceId: string = '';
            act(() => {
                controlId = result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC2.1',
                    title: 'Test',
                    description: '',
                    category: '',
                    status: 'implemented',
                });
                evidenceId = result.current.addEvidence(controlId, {
                    type: 'document',
                    title: 'Old Title',
                    description: 'Old description',
                    uploadedAt: new Date().toISOString(),
                });
            });

            act(() => {
                result.current.updateEvidence(evidenceId, {
                    title: 'New Title',
                    verified: true,
                });
            });

            const evidence = result.current.getEvidenceForControl(controlId);
            expect(evidence[0].title).toBe('New Title');
            expect(evidence[0].verified).toBe(true);
        });

        it('should delete evidence', () => {
            const { result } = renderHook(() => useCompliance());

            let controlId: string = '';
            let evidenceId: string = '';
            act(() => {
                controlId = result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC3.1',
                    title: 'Test',
                    description: '',
                    category: '',
                    status: 'implemented',
                });
                evidenceId = result.current.addEvidence(controlId, {
                    type: 'document',
                    title: 'Evidence',
                    description: '',
                    uploadedAt: new Date().toISOString(),
                });
            });

            expect(result.current.getEvidenceForControl(controlId)).toHaveLength(1);

            act(() => {
                result.current.deleteEvidence(evidenceId);
            });

            expect(result.current.getEvidenceForControl(controlId)).toHaveLength(0);
        });

        it('should delete evidence when control is deleted', () => {
            const { result } = renderHook(() => useCompliance());

            let controlId: string = '';
            act(() => {
                controlId = result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC4.1',
                    title: 'Test',
                    description: '',
                    category: '',
                    status: 'implemented',
                });
                result.current.addEvidence(controlId, {
                    type: 'document',
                    title: 'Evidence 1',
                    description: '',
                    uploadedAt: new Date().toISOString(),
                });
                result.current.addEvidence(controlId, {
                    type: 'document',
                    title: 'Evidence 2',
                    description: '',
                    uploadedAt: new Date().toISOString(),
                });
            });

            expect(result.current.evidence).toHaveLength(2);

            act(() => {
                result.current.deleteControl(controlId);
            });

            expect(result.current.evidence).toHaveLength(0);
        });
    });

    describe('Compliance Overview', () => {
        it('should calculate compliance overview', () => {
            const { result } = renderHook(() => useCompliance());

            act(() => {
                result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC1.1',
                    title: 'Control 1',
                    description: '',
                    category: '',
                    status: 'implemented',
                });
                result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC1.2',
                    title: 'Control 2',
                    description: '',
                    category: '',
                    status: 'in-progress',
                });
                result.current.addControl({
                    framework: 'hipaa',
                    controlId: '164.308',
                    title: 'Control 3',
                    description: '',
                    category: '',
                    status: 'not-implemented',
                });
            });

            const overview = result.current.getComplianceOverview();

            expect(overview.totalControls).toBe(3);
            expect(overview.byStatus.implemented).toBe(1);
            expect(overview.byStatus['in-progress']).toBe(1);
            expect(overview.byStatus['not-implemented']).toBe(1);
            expect(overview.byFramework.soc2).toBe(2);
            expect(overview.byFramework.hipaa).toBe(1);
        });

        it('should calculate compliance score', () => {
            const { result } = renderHook(() => useCompliance());

            act(() => {
                result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC1.1',
                    title: 'Control 1',
                    description: '',
                    category: '',
                    status: 'implemented',
                });
                result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC1.2',
                    title: 'Control 2',
                    description: '',
                    category: '',
                    status: 'implemented',
                });
            });

            const overview = result.current.getComplianceOverview();
            expect(overview.complianceScore).toBe(100);
        });

        it('should calculate partial compliance score', () => {
            const { result } = renderHook(() => useCompliance());

            act(() => {
                result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC1.1',
                    title: 'Control 1',
                    description: '',
                    category: '',
                    status: 'implemented',
                });
                result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC1.2',
                    title: 'Control 2',
                    description: '',
                    category: '',
                    status: 'not-implemented',
                });
            });

            const overview = result.current.getComplianceOverview();
            expect(overview.complianceScore).toBe(50);
        });
    });

    describe('Gap Analysis', () => {
        it('should identify not-implemented gaps', () => {
            const { result } = renderHook(() => useCompliance());

            act(() => {
                result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC1.1',
                    title: 'Control 1',
                    description: '',
                    category: '',
                    status: 'not-implemented',
                });
            });

            const gapAnalysis = result.current.performGapAnalysis();

            expect(gapAnalysis.gaps).toHaveLength(1);
            expect(gapAnalysis.gaps[0]).toMatchObject({
                controlId: 'CC1.1',
                severity: 'high',
            });
        });

        it('should identify failed audit gaps', () => {
            const { result } = renderHook(() => useCompliance());

            act(() => {
                result.current.addControl({
                    framework: 'hipaa',
                    controlId: '164.308',
                    title: 'Security Management',
                    description: '',
                    category: '',
                    status: 'failed',
                });
            });

            const gapAnalysis = result.current.performGapAnalysis();

            expect(gapAnalysis.gaps).toHaveLength(1);
            expect(gapAnalysis.gaps[0].severity).toBe('critical');
        });

        it('should identify missing evidence gaps', () => {
            const { result } = renderHook(() => useCompliance());

            act(() => {
                result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC2.1',
                    title: 'Control with no evidence',
                    description: '',
                    category: '',
                    status: 'implemented',
                });
            });

            const gapAnalysis = result.current.performGapAnalysis();

            expect(gapAnalysis.gaps).toHaveLength(1);
            expect(gapAnalysis.gaps[0]).toMatchObject({
                controlId: 'CC2.1',
                severity: 'medium',
            });
        });

        it('should not flag controls with evidence', () => {
            const { result } = renderHook(() => useCompliance());

            let controlId: string = '';
            act(() => {
                controlId = result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC3.1',
                    title: 'Control with evidence',
                    description: '',
                    category: '',
                    status: 'implemented',
                });
                result.current.addEvidence(controlId, {
                    type: 'document',
                    title: 'Evidence',
                    description: '',
                    uploadedAt: new Date().toISOString(),
                });
            });

            const gapAnalysis = result.current.performGapAnalysis();

            const missingEvidenceGaps = gapAnalysis.gaps.filter((g) => g.controlId === 'CC3.1');
            expect(missingEvidenceGaps).toHaveLength(0);
        });

        it('should calculate estimated days to close', () => {
            const { result } = renderHook(() => useCompliance());

            act(() => {
                result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC1.1',
                    title: 'Control 1',
                    description: '',
                    category: '',
                    status: 'not-implemented',
                });
                result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC1.2',
                    title: 'Control 2',
                    description: '',
                    category: '',
                    status: 'failed',
                });
            });

            const gapAnalysis = result.current.performGapAnalysis();

            expect(gapAnalysis.estimatedDaysToClose).toBeGreaterThan(0);
        });

        it('should generate recommendations', () => {
            const { result } = renderHook(() => useCompliance());

            act(() => {
                result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC1.1',
                    title: 'Control 1',
                    description: '',
                    category: '',
                    status: 'not-implemented',
                });
                result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC1.2',
                    title: 'Control 2',
                    description: '',
                    category: '',
                    status: 'failed',
                });
            });

            const gapAnalysis = result.current.performGapAnalysis();

            expect(gapAnalysis.recommendations.length).toBeGreaterThan(0);
            expect(gapAnalysis.recommendations.some((r) => r.includes('not-implemented'))).toBe(true);
            expect(gapAnalysis.recommendations.some((r) => r.includes('failed'))).toBe(true);
        });
    });

    describe('Risk Scoring', () => {
        it('should calculate low risk for fully compliant program', () => {
            const { result } = renderHook(() => useCompliance());

            act(() => {
                for (let i = 0; i < 10; i++) {
                    const controlId = result.current.addControl({
                        framework: 'soc2',
                        controlId: `CC${i + 1}.1`,
                        title: `Control ${i + 1}`,
                        description: '',
                        category: '',
                        status: 'implemented',
                    });
                    result.current.addEvidence(controlId, {
                        type: 'document',
                        title: `Evidence ${i + 1}`,
                        description: '',
                        uploadedAt: new Date().toISOString(),
                    });
                }
            });

            const riskScore = result.current.calculateRiskScore();

            expect(riskScore.score).toBeGreaterThanOrEqual(80);
            expect(riskScore.overallRisk).toBe('low');
        });

        it('should calculate high risk for non-compliant program', () => {
            const { result } = renderHook(() => useCompliance());

            act(() => {
                result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC1.1',
                    title: 'Control 1',
                    description: '',
                    category: '',
                    status: 'not-implemented',
                });
                result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC1.2',
                    title: 'Control 2',
                    description: '',
                    category: '',
                    status: 'failed',
                });
            });

            const riskScore = result.current.calculateRiskScore();

            expect(riskScore.score).toBeLessThan(60);
            expect(['high', 'critical']).toContain(riskScore.overallRisk);
        });

        it('should include risk factors', () => {
            const { result } = renderHook(() => useCompliance());

            act(() => {
                result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC1.1',
                    title: 'Control 1',
                    description: '',
                    category: '',
                    status: 'implemented',
                });
            });

            const riskScore = result.current.calculateRiskScore();

            expect(riskScore.factors).toHaveLength(4);
            expect(riskScore.factors[0].factor).toBe('Control Implementation');
            expect(riskScore.factors[1].factor).toBe('Evidence Coverage');
            expect(riskScore.factors[2].factor).toBe('Failed Controls');
            expect(riskScore.factors[3].factor).toBe('Review Status');
        });
    });

    describe('Evidence Validation', () => {
        it('should validate evidence exists for control', () => {
            const { result } = renderHook(() => useCompliance());

            let controlId: string = '';
            act(() => {
                controlId = result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC1.1',
                    title: 'Control 1',
                    description: '',
                    category: '',
                    status: 'implemented',
                });
                result.current.addEvidence(controlId, {
                    type: 'document',
                    title: 'Evidence',
                    description: '',
                    uploadedAt: new Date().toISOString(),
                    verified: true,
                });
            });

            const isValid = result.current.validateEvidence(controlId);
            expect(isValid).toBe(true);
        });

        it('should fail validation when no evidence exists', () => {
            const { result } = renderHook(() => useCompliance());

            let controlId: string = '';
            act(() => {
                controlId = result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC1.1',
                    title: 'Control 1',
                    description: '',
                    category: '',
                    status: 'implemented',
                });
            });

            const isValid = result.current.validateEvidence(controlId);
            expect(isValid).toBe(false);
        });
    });

    describe('Export to Audit Report', () => {
        it('should export audit report', () => {
            const { result } = renderHook(() => useCompliance());

            act(() => {
                result.current.setProgramName('Test Program');
                const controlId = result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC1.1',
                    title: 'Test Control',
                    description: 'Test description',
                    category: 'Test Category',
                    status: 'implemented',
                });
                result.current.addEvidence(controlId, {
                    type: 'document',
                    title: 'Test Evidence',
                    description: 'Test evidence description',
                    uploadedAt: new Date().toISOString(),
                });
            });

            const report = result.current.exportToAuditReport();

            expect(report).toContain('COMPLIANCE AUDIT REPORT');
            expect(report).toContain('Test Program');
            expect(report).toContain('CC1.1');
            expect(report).toContain('Test Control');
            expect(report).toContain('Test Evidence');
        });
    });

    describe('Export to CSV', () => {
        it('should export to CSV format', () => {
            const { result } = renderHook(() => useCompliance());

            act(() => {
                result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC1.1',
                    title: 'Control 1',
                    description: '',
                    category: 'Category 1',
                    status: 'implemented',
                });
            });

            const csv = result.current.exportToCSV();

            expect(csv).toContain('Framework,Control ID,Title,Status,Category,Evidence Count');
            expect(csv).toContain('soc2');
            expect(csv).toContain('CC1.1');
            expect(csv).toContain('Control 1');
            expect(csv).toContain('implemented');
        });
    });

    describe('Export to JSON', () => {
        it('should export to JSON format', () => {
            const { result } = renderHook(() => useCompliance());

            act(() => {
                result.current.setProgramName('JSON Test Program');
                result.current.addControl({
                    framework: 'hipaa',
                    controlId: '164.308',
                    title: 'Security Management',
                    description: '',
                    category: '',
                    status: 'implemented',
                });
            });

            const json = result.current.exportToJSON();
            const data = JSON.parse(json);

            expect(data.programName).toBe('JSON Test Program');
            expect(data.controls).toHaveLength(1);
            expect(data.controls[0].controlId).toBe('164.308');
            expect(data.overview).toBeDefined();
            expect(data.gapAnalysis).toBeDefined();
            expect(data.riskScore).toBeDefined();
        });
    });

    describe('Complex Scenario: Multi-Framework Compliance Program', () => {
        it('should handle comprehensive compliance program', () => {
            const { result } = renderHook(() => useCompliance());

            act(() => {
                result.current.setProgramName('Enterprise Compliance Program');

                // SOC2 Controls
                const soc2Control1 = result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC1.1',
                    title: 'Control Environment - Integrity and Ethics',
                    description: 'The entity demonstrates a commitment to integrity and ethical values',
                    category: 'Control Environment',
                    status: 'implemented',
                });
                result.current.addEvidence(soc2Control1, {
                    type: 'document',
                    title: 'Code of Conduct',
                    description: 'Company code of conduct document',
                    url: 'https://example.com/code-of-conduct.pdf',
                    uploadedAt: new Date().toISOString(),
                    verified: true,
                });

                result.current.addControl({
                    framework: 'soc2',
                    controlId: 'CC2.1',
                    title: 'Communication and Information',
                    description: 'The entity obtains or generates relevant information',
                    category: 'Communication',
                    status: 'in-progress',
                });

                // HIPAA Controls
                const hipaaControl1 = result.current.addControl({
                    framework: 'hipaa',
                    controlId: '164.308(a)(1)(i)',
                    title: 'Security Management Process',
                    description: 'Implement policies and procedures to prevent unauthorized access',
                    category: 'Administrative Safeguards',
                    status: 'implemented',
                });
                result.current.addEvidence(hipaaControl1, {
                    type: 'document',
                    title: 'Risk Assessment Report',
                    description: 'Annual risk assessment',
                    uploadedAt: new Date().toISOString(),
                    verified: true,
                });
                result.current.addEvidence(hipaaControl1, {
                    type: 'attestation',
                    title: 'Management Attestation',
                    description: 'Management sign-off on security policies',
                    uploadedAt: new Date().toISOString(),
                    verified: true,
                });

                result.current.addControl({
                    framework: 'hipaa',
                    controlId: '164.312(a)(1)',
                    title: 'Access Control',
                    description: 'Implement technical policies to allow access only to authorized persons',
                    category: 'Technical Safeguards',
                    status: 'not-implemented',
                });

                // GDPR Controls
                const gdprControl1 = result.current.addControl({
                    framework: 'gdpr',
                    controlId: 'Art. 32',
                    title: 'Security of Processing',
                    description: 'Implement appropriate technical and organizational measures',
                    category: 'Data Security',
                    status: 'implemented',
                });
                result.current.addEvidence(gdprControl1, {
                    type: 'document',
                    title: 'Data Protection Impact Assessment',
                    description: 'DPIA for data processing activities',
                    uploadedAt: new Date().toISOString(),
                    verified: true,
                });

                result.current.addControl({
                    framework: 'gdpr',
                    controlId: 'Art. 33',
                    title: 'Breach Notification',
                    description: 'Notify supervisory authority of personal data breach',
                    category: 'Incident Response',
                    status: 'implemented',
                });

                // PCI-DSS Controls
                result.current.addControl({
                    framework: 'pci-dss',
                    controlId: '1.1',
                    title: 'Firewall Configuration Standards',
                    description: 'Establish and implement firewall configuration standards',
                    category: 'Network Security',
                    status: 'implemented',
                });

                result.current.addControl({
                    framework: 'pci-dss',
                    controlId: '2.1',
                    title: 'Vendor Defaults',
                    description: 'Change vendor-supplied defaults',
                    category: 'Configuration',
                    status: 'failed',
                });

                // ISO 27001 Controls
                result.current.addControl({
                    framework: 'iso-27001',
                    controlId: 'A.9.1.1',
                    title: 'Access Control Policy',
                    description: 'Establish, document and review access control policy',
                    category: 'Access Control',
                    status: 'implemented',
                });

                result.current.addControl({
                    framework: 'iso-27001',
                    controlId: 'A.12.1.1',
                    title: 'Documented Operating Procedures',
                    description: 'Document and maintain operating procedures',
                    category: 'Operations Security',
                    status: 'needs-review',
                });
            });

            // Verify counts
            expect(result.current.getControlCount()).toBe(10);
            expect(result.current.getControlsByFramework('soc2')).toHaveLength(2);
            expect(result.current.getControlsByFramework('hipaa')).toHaveLength(2);
            expect(result.current.getControlsByFramework('gdpr')).toHaveLength(2);
            expect(result.current.getControlsByFramework('pci-dss')).toHaveLength(2);
            expect(result.current.getControlsByFramework('iso-27001')).toHaveLength(2);

            // Verify overview
            const overview = result.current.getComplianceOverview();
            expect(overview.totalControls).toBe(10);
            expect(overview.byStatus.implemented).toBe(6);
            expect(overview.byStatus['in-progress']).toBe(1);
            expect(overview.byStatus['not-implemented']).toBe(1);
            expect(overview.byStatus['needs-review']).toBe(1);
            expect(overview.byStatus.failed).toBe(1);
            expect(overview.complianceScore).toBeGreaterThanOrEqual(50);
            expect(overview.totalEvidence).toBe(5);

            // Verify gap analysis
            const gapAnalysis = result.current.performGapAnalysis();
            expect(gapAnalysis.gaps.length).toBeGreaterThan(0);
            expect(gapAnalysis.gaps.some((g) => g.severity === 'critical')).toBe(true); // Failed control
            expect(gapAnalysis.gaps.some((g) => g.severity === 'high')).toBe(true); // Not implemented
            expect(gapAnalysis.recommendations.length).toBeGreaterThan(0);

            // Verify risk score
            const riskScore = result.current.calculateRiskScore();
            expect(riskScore.score).toBeGreaterThan(0);
            expect(riskScore.score).toBeLessThan(100);
            expect(['low', 'medium', 'high', 'critical']).toContain(riskScore.overallRisk);
            expect(riskScore.factors).toHaveLength(4);

            // Verify exports
            const auditReport = result.current.exportToAuditReport();
            expect(auditReport).toContain('Enterprise Compliance Program');
            expect(auditReport).toContain('SOC2');
            expect(auditReport).toContain('HIPAA');
            expect(auditReport).toContain('GDPR');
            expect(auditReport).toContain('PCI-DSS');
            expect(auditReport).toContain('ISO-27001');

            const csv = result.current.exportToCSV();
            expect(csv).toContain('soc2');
            expect(csv).toContain('hipaa');
            expect(csv).toContain('gdpr');

            const json = result.current.exportToJSON();
            const data = JSON.parse(json);
            expect(data.controls).toHaveLength(10);
            expect(data.overview.totalControls).toBe(10);
        });
    });
});
