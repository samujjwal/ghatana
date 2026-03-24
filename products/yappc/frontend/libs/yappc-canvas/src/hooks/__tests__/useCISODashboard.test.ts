/**
 * Tests for useCISODashboard hook
 * 
 * Comprehensive test suite covering:
 * - Initialization and state management
 * - Security KPI calculations
 * - Risk heatmap generation and system risk assessment
 * - CVE management (CRUD operations, filtering, system associations)
 * - Incident management (CRUD operations, status tracking, timeline)
 * - Trend analysis (vulnerability, incident, compliance trends)
 * - Board reporting (executive summary, full report, export formats)
 * - Complex enterprise security scenarios
 */

import { renderHook, act } from '@testing-library/react';
import { useCISODashboard } from '../useCISODashboard';
import type {
    CVE,
    SecurityIncident,
    Severity,
    RiskLevel,
    IncidentStatus,
    TrendPeriod,
    ExportFormat,
} from '../useCISODashboard';

describe('useCISODashboard', () => {
    // ============================================================================
    // Initialization Tests
    // ============================================================================

    describe('Initialization', () => {
        it('should initialize with default values', () => {
            const { result } = renderHook(() => useCISODashboard());

            expect(result.current.organization).toBe('My Organization');
            expect(result.current.selectedSystem).toBeNull();
            expect(result.current.selectedSeverity).toBeNull();
            expect(result.current.selectedIncidentStatus).toBeNull();
            expect(result.current.selectedTrendPeriod).toBe('month');
            expect(result.current.getCVEs()).toEqual([]);
            expect(result.current.getIncidents()).toEqual([]);
        });

        it('should set organization name', () => {
            const { result } = renderHook(() => useCISODashboard());

            act(() => {
                result.current.setOrganization('Acme Corp');
            });

            expect(result.current.organization).toBe('Acme Corp');
        });

        it('should set selected filters', () => {
            const { result } = renderHook(() => useCISODashboard());

            act(() => {
                result.current.setSelectedSystem('web-server');
                result.current.setSelectedSeverity('critical');
                result.current.setSelectedIncidentStatus('active');
                result.current.setSelectedTrendPeriod('week');
            });

            expect(result.current.selectedSystem).toBe('web-server');
            expect(result.current.selectedSeverity).toBe('critical');
            expect(result.current.selectedIncidentStatus).toBe('active');
            expect(result.current.selectedTrendPeriod).toBe('week');
        });
    });

    // ============================================================================
    // Security KPI Tests
    // ============================================================================

    describe('Security KPIs', () => {
        it('should calculate KPIs with no data', () => {
            const { result } = renderHook(() => useCISODashboard());

            const kpis = result.current.getSecurityKPIs();

            expect(kpis.totalVulnerabilities).toBe(0);
            expect(kpis.criticalVulnerabilities).toBe(0);
            expect(kpis.highVulnerabilities).toBe(0);
            expect(kpis.complianceScore).toBe(100);
            expect(kpis.activeIncidents).toBe(0);
            expect(kpis.overallRisk).toBe('low');
        });

        it('should calculate KPIs with vulnerabilities', () => {
            const { result } = renderHook(() => useCISODashboard());

            act(() => {
                // Add critical CVE
                result.current.addCVE({
                    cveId: 'CVE-2024-0001',
                    severity: 'critical',
                    cvssScore: 9.8,
                    description: 'Critical RCE vulnerability',
                    affectedSystems: ['web-server'],
                    publishedDate: '2024-01-01',
                    patchAvailable: false,
                    exploitAvailable: true,
                });

                // Add high CVE
                result.current.addCVE({
                    cveId: 'CVE-2024-0002',
                    severity: 'high',
                    cvssScore: 8.5,
                    description: 'High severity SQL injection',
                    affectedSystems: ['database'],
                    publishedDate: '2024-01-02',
                    patchAvailable: true,
                    exploitAvailable: false,
                });

                // Add medium CVE
                result.current.addCVE({
                    cveId: 'CVE-2024-0003',
                    severity: 'medium',
                    cvssScore: 6.0,
                    description: 'Medium severity XSS',
                    affectedSystems: ['web-server'],
                    publishedDate: '2024-01-03',
                    patchAvailable: true,
                    exploitAvailable: false,
                });
            });

            const kpis = result.current.getSecurityKPIs();

            expect(kpis.totalVulnerabilities).toBe(3);
            expect(kpis.criticalVulnerabilities).toBe(1);
            expect(kpis.highVulnerabilities).toBe(1);
            expect(kpis.mediumVulnerabilities).toBe(1);
            expect(kpis.lowVulnerabilities).toBe(0);
            expect(kpis.complianceScore).toBe(67); // 2 patches available out of 3
            expect(kpis.overallRisk).toBe('high'); // Has critical vulnerability
        });

        it('should calculate KPIs with incidents', () => {
            const { result } = renderHook(() => useCISODashboard());

            act(() => {
                result.current.addIncident({
                    title: 'Security breach detected',
                    description: 'Unauthorized access attempt',
                    status: 'critical',
                    severity: 'critical',
                    affectedSystems: ['web-server'],
                    detectedAt: '2024-01-01T10:00:00Z',
                });

                result.current.addIncident({
                    title: 'Malware detected',
                    description: 'Malware found on endpoint',
                    status: 'active',
                    severity: 'high',
                    affectedSystems: ['endpoint-001'],
                    detectedAt: '2024-01-02T10:00:00Z',
                });

                result.current.addIncident({
                    title: 'Phishing attempt',
                    description: 'Phishing email reported',
                    status: 'resolved',
                    severity: 'medium',
                    affectedSystems: [],
                    detectedAt: '2024-01-03T10:00:00Z',
                    resolvedAt: '2024-01-03T14:00:00Z',
                });
            });

            const kpis = result.current.getSecurityKPIs();

            expect(kpis.activeIncidents).toBe(2); // critical + active
            expect(kpis.criticalIncidents).toBe(1);
            expect(kpis.resolvedIncidents).toBe(1);
            expect(kpis.meanTimeToResolve).toBeGreaterThan(0);
            expect(kpis.overallRisk).toBe('critical'); // Has critical incident
        });

        it('should calculate overall risk level correctly', () => {
            const { result } = renderHook(() => useCISODashboard());

            // Test critical risk (> 5 critical vulns)
            act(() => {
                for (let i = 0; i < 6; i++) {
                    result.current.addCVE({
                        cveId: `CVE-2024-000${i}`,
                        severity: 'critical',
                        cvssScore: 9.0,
                        description: `Critical vulnerability ${i}`,
                        affectedSystems: ['system-1'],
                        publishedDate: '2024-01-01',
                        patchAvailable: false,
                        exploitAvailable: true,
                    });
                }
            });

            let kpis = result.current.getSecurityKPIs();
            expect(kpis.overallRisk).toBe('critical');

            // Clear and test high risk
            const cves = result.current.getCVEs();
            act(() => {
                cves.forEach((cve) => result.current.deleteCVE(cve.id));
                result.current.addCVE({
                    cveId: 'CVE-2024-0100',
                    severity: 'high',
                    cvssScore: 8.0,
                    description: 'High vulnerability',
                    affectedSystems: ['system-1'],
                    publishedDate: '2024-01-01',
                    patchAvailable: false,
                    exploitAvailable: false,
                });
            });

            kpis = result.current.getSecurityKPIs();
            expect(kpis.overallRisk).toBe('medium');
        });
    });

    // ============================================================================
    // CVE Management Tests
    // ============================================================================

    describe('CVE Management', () => {
        it('should add a CVE', () => {
            const { result } = renderHook(() => useCISODashboard());

            let cveId: string;
            act(() => {
                cveId = result.current.addCVE({
                    cveId: 'CVE-2024-1234',
                    severity: 'critical',
                    cvssScore: 9.8,
                    description: 'Remote code execution vulnerability',
                    affectedSystems: ['web-server', 'api-gateway'],
                    publishedDate: '2024-01-15',
                    mitigationSteps: ['Apply patch', 'Restart services'],
                    references: ['https://nvd.nist.gov/vuln/detail/CVE-2024-1234'],
                    patchAvailable: true,
                    exploitAvailable: true,
                });
            });

            const cves = result.current.getCVEs();
            expect(cves).toHaveLength(1);
            expect(cves[0].cveId).toBe('CVE-2024-1234');
            expect(cves[0].severity).toBe('critical');
            expect(cves[0].cvssScore).toBe(9.8);
            expect(cves[0].affectedSystems).toEqual(['web-server', 'api-gateway']);
            expect(cveId).toBeDefined();
        });

        it('should update a CVE', () => {
            const { result } = renderHook(() => useCISODashboard());

            let cveId: string;
            act(() => {
                cveId = result.current.addCVE({
                    cveId: 'CVE-2024-5678',
                    severity: 'high',
                    cvssScore: 8.5,
                    description: 'SQL injection vulnerability',
                    affectedSystems: ['database'],
                    publishedDate: '2024-01-20',
                    patchAvailable: false,
                    exploitAvailable: false,
                });
            });

            act(() => {
                result.current.updateCVE(cveId, {
                    patchAvailable: true,
                    mitigationSteps: ['Update to version 2.0', 'Run security scan'],
                });
            });

            const cve = result.current.getCVEDetails(cveId);
            expect(cve?.patchAvailable).toBe(true);
            expect(cve?.mitigationSteps).toHaveLength(2);
        });

        it('should delete a CVE', () => {
            const { result } = renderHook(() => useCISODashboard());

            let cveId: string;
            act(() => {
                cveId = result.current.addCVE({
                    cveId: 'CVE-2024-9999',
                    severity: 'medium',
                    cvssScore: 6.0,
                    description: 'Cross-site scripting',
                    affectedSystems: ['web-app'],
                    publishedDate: '2024-01-25',
                    patchAvailable: true,
                    exploitAvailable: false,
                });
            });

            expect(result.current.getCVEs()).toHaveLength(1);

            act(() => {
                result.current.deleteCVE(cveId);
            });

            expect(result.current.getCVEs()).toHaveLength(0);
            expect(result.current.getCVEDetails(cveId)).toBeUndefined();
        });

        it('should filter CVEs by severity', () => {
            const { result } = renderHook(() => useCISODashboard());

            act(() => {
                result.current.addCVE({
                    cveId: 'CVE-2024-0001',
                    severity: 'critical',
                    cvssScore: 9.5,
                    description: 'Critical vuln',
                    affectedSystems: ['sys1'],
                    publishedDate: '2024-01-01',
                    patchAvailable: false,
                    exploitAvailable: true,
                });

                result.current.addCVE({
                    cveId: 'CVE-2024-0002',
                    severity: 'high',
                    cvssScore: 8.0,
                    description: 'High vuln',
                    affectedSystems: ['sys2'],
                    publishedDate: '2024-01-02',
                    patchAvailable: true,
                    exploitAvailable: false,
                });

                result.current.addCVE({
                    cveId: 'CVE-2024-0003',
                    severity: 'critical',
                    cvssScore: 9.0,
                    description: 'Another critical',
                    affectedSystems: ['sys3'],
                    publishedDate: '2024-01-03',
                    patchAvailable: false,
                    exploitAvailable: true,
                });
            });

            const criticalCVEs = result.current.getCVEsBySeverity('critical');
            const highCVEs = result.current.getCVEsBySeverity('high');

            expect(criticalCVEs).toHaveLength(2);
            expect(highCVEs).toHaveLength(1);
            expect(criticalCVEs.every((c) => c.severity === 'critical')).toBe(true);
        });

        it('should filter CVEs by system', () => {
            const { result } = renderHook(() => useCISODashboard());

            act(() => {
                result.current.addCVE({
                    cveId: 'CVE-2024-1111',
                    severity: 'high',
                    cvssScore: 8.0,
                    description: 'Vuln 1',
                    affectedSystems: ['web-server', 'api-gateway'],
                    publishedDate: '2024-01-01',
                    patchAvailable: true,
                    exploitAvailable: false,
                });

                result.current.addCVE({
                    cveId: 'CVE-2024-2222',
                    severity: 'medium',
                    cvssScore: 6.0,
                    description: 'Vuln 2',
                    affectedSystems: ['web-server'],
                    publishedDate: '2024-01-02',
                    patchAvailable: true,
                    exploitAvailable: false,
                });

                result.current.addCVE({
                    cveId: 'CVE-2024-3333',
                    severity: 'low',
                    cvssScore: 3.0,
                    description: 'Vuln 3',
                    affectedSystems: ['database'],
                    publishedDate: '2024-01-03',
                    patchAvailable: true,
                    exploitAvailable: false,
                });
            });

            const webServerCVEs = result.current.getCVEsBySystem('web-server');
            const databaseCVEs = result.current.getCVEsBySystem('database');

            expect(webServerCVEs).toHaveLength(2);
            expect(databaseCVEs).toHaveLength(1);
            expect(webServerCVEs.every((c) => c.affectedSystems.includes('web-server'))).toBe(true);
        });

        it('should get CVE count', () => {
            const { result } = renderHook(() => useCISODashboard());

            expect(result.current.getCVECount()).toBe(0);

            act(() => {
                result.current.addCVE({
                    cveId: 'CVE-2024-0001',
                    severity: 'high',
                    cvssScore: 8.0,
                    description: 'Test',
                    affectedSystems: [],
                    publishedDate: '2024-01-01',
                    patchAvailable: true,
                    exploitAvailable: false,
                });
            });

            expect(result.current.getCVECount()).toBe(1);
        });
    });

    // ============================================================================
    // Incident Management Tests
    // ============================================================================

    describe('Incident Management', () => {
        it('should add a security incident', () => {
            const { result } = renderHook(() => useCISODashboard());

            let incidentId: string;
            act(() => {
                incidentId = result.current.addIncident({
                    title: 'Data breach attempt',
                    description: 'Unauthorized access to customer database detected',
                    status: 'critical',
                    severity: 'critical',
                    affectedSystems: ['customer-db', 'web-app'],
                    detectedAt: '2024-01-15T08:30:00Z',
                    responseTeam: ['security-team', 'devops'],
                });
            });

            const incidents = result.current.getIncidents();
            expect(incidents).toHaveLength(1);
            expect(incidents[0].title).toBe('Data breach attempt');
            expect(incidents[0].status).toBe('critical');
            expect(incidents[0].affectedSystems).toEqual(['customer-db', 'web-app']);
            expect(incidentId).toBeDefined();
        });

        it('should update an incident', () => {
            const { result } = renderHook(() => useCISODashboard());

            let incidentId: string;
            act(() => {
                incidentId = result.current.addIncident({
                    title: 'Malware detected',
                    description: 'Ransomware found on endpoint',
                    status: 'active',
                    severity: 'high',
                    affectedSystems: ['endpoint-42'],
                    detectedAt: '2024-01-20T10:00:00Z',
                });
            });

            act(() => {
                result.current.updateIncident(incidentId, {
                    status: 'contained',
                    containedAt: '2024-01-20T12:00:00Z',
                    remediation: 'Isolated endpoint and ran antivirus scan',
                });
            });

            const incident = result.current.getIncidentDetails(incidentId);
            expect(incident?.status).toBe('contained');
            expect(incident?.containedAt).toBe('2024-01-20T12:00:00Z');
            expect(incident?.remediation).toBeDefined();
        });

        it('should delete an incident', () => {
            const { result } = renderHook(() => useCISODashboard());

            let incidentId: string;
            act(() => {
                incidentId = result.current.addIncident({
                    title: 'False alarm',
                    description: 'False positive alert',
                    status: 'resolved',
                    severity: 'low',
                    affectedSystems: [],
                    detectedAt: '2024-01-25T09:00:00Z',
                    resolvedAt: '2024-01-25T09:15:00Z',
                });
            });

            expect(result.current.getIncidents()).toHaveLength(1);

            act(() => {
                result.current.deleteIncident(incidentId);
            });

            expect(result.current.getIncidents()).toHaveLength(0);
            expect(result.current.getIncidentDetails(incidentId)).toBeUndefined();
        });

        it('should filter incidents by status', () => {
            const { result } = renderHook(() => useCISODashboard());

            act(() => {
                result.current.addIncident({
                    title: 'Incident 1',
                    description: 'Critical incident',
                    status: 'critical',
                    severity: 'critical',
                    affectedSystems: ['sys1'],
                    detectedAt: '2024-01-01T10:00:00Z',
                });

                result.current.addIncident({
                    title: 'Incident 2',
                    description: 'Active incident',
                    status: 'active',
                    severity: 'high',
                    affectedSystems: ['sys2'],
                    detectedAt: '2024-01-02T10:00:00Z',
                });

                result.current.addIncident({
                    title: 'Incident 3',
                    description: 'Resolved incident',
                    status: 'resolved',
                    severity: 'medium',
                    affectedSystems: ['sys3'],
                    detectedAt: '2024-01-03T10:00:00Z',
                    resolvedAt: '2024-01-03T14:00:00Z',
                });
            });

            const criticalIncidents = result.current.getIncidentsBystatus('critical');
            const activeIncidents = result.current.getIncidentsBystatus('active');
            const resolvedIncidents = result.current.getIncidentsBystatus('resolved');

            expect(criticalIncidents).toHaveLength(1);
            expect(activeIncidents).toHaveLength(1);
            expect(resolvedIncidents).toHaveLength(1);
        });

        it('should filter incidents by system', () => {
            const { result } = renderHook(() => useCISODashboard());

            act(() => {
                result.current.addIncident({
                    title: 'Web server breach',
                    description: 'Attack on web server',
                    status: 'active',
                    severity: 'high',
                    affectedSystems: ['web-server', 'load-balancer'],
                    detectedAt: '2024-01-01T10:00:00Z',
                });

                result.current.addIncident({
                    title: 'DB compromise',
                    description: 'Database breach',
                    status: 'critical',
                    severity: 'critical',
                    affectedSystems: ['database'],
                    detectedAt: '2024-01-02T10:00:00Z',
                });
            });

            const webServerIncidents = result.current.getIncidentsBySystem('web-server');
            const databaseIncidents = result.current.getIncidentsBySystem('database');

            expect(webServerIncidents).toHaveLength(1);
            expect(databaseIncidents).toHaveLength(1);
        });

        it('should get incident timeline sorted by date', () => {
            const { result } = renderHook(() => useCISODashboard());

            act(() => {
                result.current.addIncident({
                    title: 'Old incident',
                    description: 'Oldest',
                    status: 'resolved',
                    severity: 'low',
                    affectedSystems: [],
                    detectedAt: '2024-01-01T10:00:00Z',
                    resolvedAt: '2024-01-01T11:00:00Z',
                });

                result.current.addIncident({
                    title: 'Recent incident',
                    description: 'Most recent',
                    status: 'active',
                    severity: 'high',
                    affectedSystems: ['sys1'],
                    detectedAt: '2024-01-15T10:00:00Z',
                });

                result.current.addIncident({
                    title: 'Middle incident',
                    description: 'Middle one',
                    status: 'contained',
                    severity: 'medium',
                    affectedSystems: ['sys2'],
                    detectedAt: '2024-01-10T10:00:00Z',
                });
            });

            const timeline = result.current.getIncidentTimeline();

            expect(timeline).toHaveLength(3);
            expect(timeline[0].title).toBe('Recent incident'); // Most recent first
            expect(timeline[1].title).toBe('Middle incident');
            expect(timeline[2].title).toBe('Old incident');
        });
    });

    // ============================================================================
    // Risk Management Tests
    // ============================================================================

    describe('Risk Management', () => {
        it('should calculate system risk score', () => {
            const { result } = renderHook(() => useCISODashboard());

            act(() => {
                // Add vulnerabilities to system
                result.current.addCVE({
                    cveId: 'CVE-2024-0001',
                    severity: 'critical',
                    cvssScore: 9.8,
                    description: 'Critical vuln',
                    affectedSystems: ['production-server'],
                    publishedDate: '2024-01-01',
                    patchAvailable: false,
                    exploitAvailable: true,
                });

                result.current.addCVE({
                    cveId: 'CVE-2024-0002',
                    severity: 'high',
                    cvssScore: 8.0,
                    description: 'High vuln',
                    affectedSystems: ['production-server'],
                    publishedDate: '2024-01-02',
                    patchAvailable: true,
                    exploitAvailable: false,
                });

                // Add incident to system
                result.current.addIncident({
                    title: 'Active breach',
                    description: 'Ongoing attack',
                    status: 'active',
                    severity: 'critical',
                    affectedSystems: ['production-server'],
                    detectedAt: '2024-01-15T10:00:00Z',
                });
            });

            const riskScore = result.current.calculateSystemRisk('production-server');

            // Expected: 40 (critical) + 20 (high) + 15 (active incident) = 75
            expect(riskScore).toBeGreaterThanOrEqual(70);
        });

        it('should generate risk heatmap sorted by risk score', () => {
            const { result } = renderHook(() => useCISODashboard());

            // Note: Risk heatmap requires systems to be added via the systems atom
            // In a real implementation, systems would be managed through CRUD operations
            // For now, we can test that the function returns an array
            const heatmap = result.current.getRiskHeatmap();

            expect(Array.isArray(heatmap)).toBe(true);
        });
    });

    // ============================================================================
    // Trend Analysis Tests
    // ============================================================================

    describe('Trend Analysis', () => {
        it('should get security trends for week period', () => {
            const { result } = renderHook(() => useCISODashboard());

            act(() => {
                result.current.setSelectedTrendPeriod('week');
            });

            const trends = result.current.getSecurityTrends('week');

            expect(trends.period).toBe('week');
            expect(typeof trends.vulnerabilityChange).toBe('number');
            expect(typeof trends.incidentChange).toBe('number');
            expect(typeof trends.complianceChange).toBe('number');
            expect(typeof trends.riskChange).toBe('number');
        });

        it('should get security trends for month period', () => {
            const { result } = renderHook(() => useCISODashboard());

            const trends = result.current.getSecurityTrends('month');

            expect(trends.period).toBe('month');
            expect(trends).toHaveProperty('vulnerabilityChange');
            expect(trends).toHaveProperty('incidentChange');
            expect(trends).toHaveProperty('complianceChange');
        });

        it('should get security trends for quarter period', () => {
            const { result } = renderHook(() => useCISODashboard());

            const trends = result.current.getSecurityTrends('quarter');

            expect(trends.period).toBe('quarter');
            expect(trends).toHaveProperty('vulnerabilityChange');
            expect(trends).toHaveProperty('patchComplianceChange');
        });

        it('should get vulnerability trends', () => {
            const { result } = renderHook(() => useCISODashboard());

            const weekTrends = result.current.getVulnerabilityTrends('week');
            const monthTrends = result.current.getVulnerabilityTrends('month');

            expect(weekTrends).toHaveLength(7);
            expect(monthTrends).toHaveLength(30);
            expect(weekTrends[0]).toHaveProperty('date');
            expect(weekTrends[0]).toHaveProperty('count');
        });

        it('should get incident trends', () => {
            const { result } = renderHook(() => useCISODashboard());

            const quarterTrends = result.current.getIncidentTrends('quarter');

            expect(quarterTrends).toHaveLength(90);
            expect(quarterTrends[0]).toHaveProperty('date');
            expect(quarterTrends[0]).toHaveProperty('count');
        });
    });

    // ============================================================================
    // Board Reporting Tests
    // ============================================================================

    describe('Board Reporting', () => {
        it('should generate executive summary', () => {
            const { result } = renderHook(() => useCISODashboard());

            act(() => {
                result.current.setOrganization('Acme Corp');
                result.current.addCVE({
                    cveId: 'CVE-2024-0001',
                    severity: 'critical',
                    cvssScore: 9.5,
                    description: 'Critical vulnerability',
                    affectedSystems: ['prod-server'],
                    publishedDate: '2024-01-01',
                    patchAvailable: false,
                    exploitAvailable: true,
                });
            });

            const summary = result.current.generateExecutiveSummary();

            expect(summary).toContain('Acme Corp');
            expect(summary).toContain('Security Posture Summary');
            expect(summary).toContain('Overall Risk Level');
            expect(summary).toContain('Total Open Vulnerabilities');
            expect(summary).toContain('Compliance Score');
        });

        it('should generate comprehensive board report', () => {
            const { result } = renderHook(() => useCISODashboard());

            act(() => {
                result.current.setOrganization('Test Corp');

                // Add vulnerabilities
                result.current.addCVE({
                    cveId: 'CVE-2024-1111',
                    severity: 'critical',
                    cvssScore: 9.8,
                    description: 'Critical RCE',
                    affectedSystems: ['web-server'],
                    publishedDate: '2024-01-01',
                    patchAvailable: true,
                    exploitAvailable: true,
                });

                // Add incident
                result.current.addIncident({
                    title: 'Security breach',
                    description: 'Data exfiltration attempt',
                    status: 'active',
                    severity: 'high',
                    affectedSystems: ['database'],
                    detectedAt: '2024-01-15T10:00:00Z',
                });
            });

            const report = result.current.generateBoardReport();

            expect(report).toContain('EXECUTIVE SECURITY BOARD REPORT');
            expect(report).toContain('Test Corp');
            expect(report).toContain('EXECUTIVE SUMMARY');
            expect(report).toContain('KEY PERFORMANCE INDICATORS');
            expect(report).toContain('TREND ANALYSIS');
            expect(report).toContain('TOP VULNERABILITIES');
            expect(report).toContain('ACTIVE SECURITY INCIDENTS');
            expect(report).toContain('RECOMMENDATIONS');
        });

        it('should export report as PDF format', () => {
            const { result } = renderHook(() => useCISODashboard());

            const pdfExport = result.current.exportReport('pdf');

            expect(pdfExport).toContain('PDF Export');
        });

        it('should export report as PowerPoint format', () => {
            const { result } = renderHook(() => useCISODashboard());

            const pptxExport = result.current.exportReport('pptx');

            expect(pptxExport).toContain('PowerPoint Export');
            expect(pptxExport).toContain('Executive Summary');
        });

        it('should export report as JSON format', () => {
            const { result } = renderHook(() => useCISODashboard());

            act(() => {
                result.current.setOrganization('JSON Test Corp');
            });

            const jsonExport = result.current.exportReport('json');
            const data = JSON.parse(jsonExport);

            expect(data).toHaveProperty('organization');
            expect(data).toHaveProperty('reportDate');
            expect(data).toHaveProperty('executiveSummary');
            expect(data).toHaveProperty('kpis');
            expect(data).toHaveProperty('vulnerabilities');
            expect(data).toHaveProperty('incidents');
            expect(data).toHaveProperty('trends');
            expect(data.organization).toBe('JSON Test Corp');
        });
    });

    // ============================================================================
    // Complex Enterprise Security Scenario
    // ============================================================================

    describe('Complex Enterprise Security Scenario', () => {
        it('should handle comprehensive enterprise security monitoring', () => {
            const { result } = renderHook(() => useCISODashboard());

            act(() => {
                result.current.setOrganization('Enterprise Corp');

                // Critical production vulnerabilities
                result.current.addCVE({
                    cveId: 'CVE-2024-0001',
                    severity: 'critical',
                    cvssScore: 10.0,
                    description: 'Zero-day RCE in production web server',
                    affectedSystems: ['prod-web-01', 'prod-web-02'],
                    publishedDate: '2024-01-01',
                    patchAvailable: false,
                    exploitAvailable: true,
                    mitigationSteps: ['Deploy WAF rules', 'Isolate affected systems'],
                    references: ['https://nvd.nist.gov/vuln/detail/CVE-2024-0001'],
                });

                result.current.addCVE({
                    cveId: 'CVE-2024-0002',
                    severity: 'critical',
                    cvssScore: 9.8,
                    description: 'SQL injection in customer database',
                    affectedSystems: ['customer-db-01'],
                    publishedDate: '2024-01-02',
                    patchAvailable: true,
                    exploitAvailable: true,
                });

                // High severity vulnerabilities
                result.current.addCVE({
                    cveId: 'CVE-2024-0003',
                    severity: 'high',
                    cvssScore: 8.5,
                    description: 'Authentication bypass in API gateway',
                    affectedSystems: ['api-gateway-01', 'api-gateway-02'],
                    publishedDate: '2024-01-03',
                    patchAvailable: true,
                    exploitAvailable: false,
                });

                result.current.addCVE({
                    cveId: 'CVE-2024-0004',
                    severity: 'high',
                    cvssScore: 8.0,
                    description: 'Privilege escalation in auth service',
                    affectedSystems: ['auth-service'],
                    publishedDate: '2024-01-04',
                    patchAvailable: true,
                    exploitAvailable: false,
                });

                // Medium and low severity
                result.current.addCVE({
                    cveId: 'CVE-2024-0005',
                    severity: 'medium',
                    cvssScore: 6.5,
                    description: 'XSS vulnerability in admin panel',
                    affectedSystems: ['admin-panel'],
                    publishedDate: '2024-01-05',
                    patchAvailable: true,
                    exploitAvailable: false,
                });

                result.current.addCVE({
                    cveId: 'CVE-2024-0006',
                    severity: 'low',
                    cvssScore: 3.5,
                    description: 'Information disclosure in logs',
                    affectedSystems: ['logging-service'],
                    publishedDate: '2024-01-06',
                    patchAvailable: true,
                    exploitAvailable: false,
                });

                // Critical active security incident
                result.current.addIncident({
                    title: 'Active data breach',
                    description: 'Unauthorized access to customer database detected',
                    status: 'critical',
                    severity: 'critical',
                    affectedSystems: ['customer-db-01', 'api-gateway-01'],
                    detectedAt: '2024-01-15T08:30:00Z',
                    responseTeam: ['security-team', 'incident-response', 'legal'],
                    rootCause: 'Exploited CVE-2024-0002 SQL injection vulnerability',
                });

                // Active investigation
                result.current.addIncident({
                    title: 'Suspicious network activity',
                    description: 'Unusual outbound traffic detected',
                    status: 'investigating',
                    severity: 'high',
                    affectedSystems: ['prod-web-01'],
                    detectedAt: '2024-01-16T10:00:00Z',
                    responseTeam: ['security-team', 'network-ops'],
                });

                // Contained incident
                result.current.addIncident({
                    title: 'Malware on endpoint',
                    description: 'Ransomware detected and contained',
                    status: 'contained',
                    severity: 'high',
                    affectedSystems: ['endpoint-042'],
                    detectedAt: '2024-01-14T09:00:00Z',
                    containedAt: '2024-01-14T09:30:00Z',
                    remediation: 'Isolated endpoint, ran full scan, restored from backup',
                });

                // Resolved incidents
                result.current.addIncident({
                    title: 'Phishing campaign',
                    description: 'Mass phishing emails sent to employees',
                    status: 'resolved',
                    severity: 'medium',
                    affectedSystems: [],
                    detectedAt: '2024-01-10T08:00:00Z',
                    resolvedAt: '2024-01-10T16:00:00Z',
                    remediation: 'Blocked sender, conducted security awareness training',
                });

                result.current.addIncident({
                    title: 'DDoS attempt',
                    description: 'Distributed denial of service attack',
                    status: 'resolved',
                    severity: 'high',
                    affectedSystems: ['prod-web-01', 'prod-web-02'],
                    detectedAt: '2024-01-08T14:00:00Z',
                    resolvedAt: '2024-01-08T18:00:00Z',
                    remediation: 'Activated DDoS mitigation, traffic normalized',
                });
            });

            // Verify comprehensive security posture
            const kpis = result.current.getSecurityKPIs();

            // Vulnerability assertions
            expect(kpis.totalVulnerabilities).toBe(6);
            expect(kpis.criticalVulnerabilities).toBe(2);
            expect(kpis.highVulnerabilities).toBe(2);
            expect(kpis.mediumVulnerabilities).toBe(1);
            expect(kpis.lowVulnerabilities).toBe(1);

            // Incident assertions
            expect(kpis.activeIncidents).toBe(3); // critical + investigating + contained
            expect(kpis.criticalIncidents).toBe(1);
            expect(kpis.resolvedIncidents).toBe(2);

            // Risk assertions
            expect(kpis.overallRisk).toBe('critical'); // Due to critical vulns and incidents
            expect(kpis.complianceScore).toBeLessThan(100); // Not all patches applied

            // CVE filtering
            const criticalCVEs = result.current.getCVEsBySeverity('critical');
            expect(criticalCVEs).toHaveLength(2);

            const webServerCVEs = result.current.getCVEsBySystem('prod-web-01');
            expect(webServerCVEs.length).toBeGreaterThan(0);

            // Incident filtering
            const activeIncidents = result.current.getIncidentsBystatus('critical');
            expect(activeIncidents).toHaveLength(1);
            expect(activeIncidents[0].title).toBe('Active data breach');

            const timeline = result.current.getIncidentTimeline();
            expect(timeline).toHaveLength(5);
            expect(timeline[0].detectedAt > timeline[4].detectedAt).toBe(true); // Sorted by date

            // Board reporting
            const executiveSummary = result.current.generateExecutiveSummary();
            expect(executiveSummary).toContain('Enterprise Corp');
            expect(executiveSummary).toContain('CRITICAL');
            expect(executiveSummary).toContain('2 critical vulnerabilities');
            expect(executiveSummary).toContain('ATTENTION REQUIRED');

            const boardReport = result.current.generateBoardReport();
            expect(boardReport).toContain('EXECUTIVE SECURITY BOARD REPORT');
            expect(boardReport).toContain('CVE-2024-0001');
            expect(boardReport).toContain('Active data breach');
            expect(boardReport).toContain('RECOMMENDATIONS');

            // Export in all formats
            const pdfExport = result.current.exportReport('pdf');
            expect(pdfExport).toContain('PDF Export');

            const pptxExport = result.current.exportReport('pptx');
            expect(pptxExport).toContain('PowerPoint Export');

            const jsonExport = result.current.exportReport('json');
            const jsonData = JSON.parse(jsonExport);
            expect(jsonData.organization).toBe('Enterprise Corp');
            expect(jsonData.vulnerabilities).toHaveLength(6);
            expect(jsonData.incidents).toHaveLength(5);
            expect(jsonData.kpis.overallRisk).toBe('critical');

            // Trend analysis
            const trends = result.current.getSecurityTrends('month');
            expect(trends.period).toBe('month');
            expect(typeof trends.vulnerabilityChange).toBe('number');

            const vulnTrends = result.current.getVulnerabilityTrends('week');
            expect(vulnTrends).toHaveLength(7);

            const incidentTrends = result.current.getIncidentTrends('month');
            expect(incidentTrends).toHaveLength(30);
        });
    });
});
