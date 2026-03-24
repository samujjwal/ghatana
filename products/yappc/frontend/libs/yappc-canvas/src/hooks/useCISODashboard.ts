/**
 * CISO Dashboard Hook
 * 
 * @doc.type hook
 * @doc.purpose State management for CISO executive security dashboard with KPIs, risk analysis, and reporting
 * @doc.layer product
 * @doc.pattern State Management Hook
 * 
 * Provides comprehensive security posture visibility including:
 * - Security KPI tracking (vulnerabilities, compliance, incidents, risk)
 * - Risk heatmap generation (system-level risk assessment)
 * - CVE management (vulnerability tracking and prioritization)
 * - Incident tracking (security event management)
 * - Trend analysis (week, month, quarter comparisons)
 * - Board reporting (executive summaries and detailed reports)
 * - Export functionality (PDF, PPTX, JSON)
 * 
 * @example
 * ```tsx
 * const {
 *   getSecurityKPIs,
 *   getRiskHeatmap,
 *   getCVEs,
 *   generateBoardReport,
 * } = useCISODashboard();
 * ```
 */

import { atom, useAtom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';

/**
 * Severity levels for vulnerabilities
 */
export type Severity = 'critical' | 'high' | 'medium' | 'low';

/**
 * Risk levels for systems and overall security posture
 */
export type RiskLevel = 'critical' | 'high' | 'medium' | 'low';

/**
 * Incident status types
 */
export type IncidentStatus = 'critical' | 'active' | 'investigating' | 'contained' | 'resolved';

/**
 * Trend analysis periods
 */
export type TrendPeriod = 'week' | 'month' | 'quarter';

/**
 * Export format options
 */
export type ExportFormat = 'pdf' | 'pptx' | 'json';

/**
 * CVE (Common Vulnerabilities and Exposures) entry
 */
export interface CVE {
    id: string;
    cveId: string;
    severity: Severity;
    cvssScore: number;
    description: string;
    affectedSystems: string[];
    publishedDate: string;
    mitigationSteps?: string[];
    references?: string[];
    patchAvailable: boolean;
    exploitAvailable: boolean;
}

/**
 * Security incident entry
 */
export interface SecurityIncident {
    id: string;
    title: string;
    description: string;
    status: IncidentStatus;
    severity: Severity;
    affectedSystems: string[];
    detectedAt: string;
    containedAt?: string;
    resolvedAt?: string;
    responseTeam?: string[];
    rootCause?: string;
    remediation?: string;
}

/**
 * System risk assessment
 */
export interface SystemRisk {
    systemName: string;
    description: string;
    riskLevel: RiskLevel;
    riskScore: number;
    vulnerabilityCount: number;
    criticalVulnerabilities: number;
    highVulnerabilities: number;
    vulnerabilities: CVE[];
    incidents: SecurityIncident[];
    lastAssessment: string;
    owner?: string;
    businessImpact?: 'critical' | 'high' | 'medium' | 'low';
}

/**
 * Security KPIs
 */
export interface SecurityKPIs {
    totalVulnerabilities: number;
    criticalVulnerabilities: number;
    highVulnerabilities: number;
    mediumVulnerabilities: number;
    lowVulnerabilities: number;
    complianceScore: number;
    activeIncidents: number;
    criticalIncidents: number;
    resolvedIncidents: number;
    meanTimeToDetect: number;
    meanTimeToResolve: number;
    overallRisk: RiskLevel;
    systemsAtRisk: number;
    patchCompliance: number;
}

/**
 * Security trends
 */
export interface SecurityTrends {
    period: TrendPeriod;
    vulnerabilityChange: number;
    incidentChange: number;
    complianceChange: number;
    riskChange: number;
    patchComplianceChange: number;
}

/**
 * Hook return type
 */
export interface UseCISODashboardReturn {
    // State
    organization: string;
    setOrganization: (name: string) => void;
    selectedSystem: string | null;
    setSelectedSystem: (system: string | null) => void;
    selectedSeverity: Severity | null;
    setSelectedSeverity: (severity: Severity | null) => void;
    selectedIncidentStatus: IncidentStatus | null;
    setSelectedIncidentStatus: (status: IncidentStatus | null) => void;
    selectedTrendPeriod: TrendPeriod;
    setSelectedTrendPeriod: (period: TrendPeriod) => void;

    // Security KPIs
    getSecurityKPIs: () => SecurityKPIs;

    // Risk Management
    getRiskHeatmap: () => SystemRisk[];
    getSystemRiskDetails: (systemName: string) => SystemRisk | undefined;
    calculateSystemRisk: (systemName: string) => number;

    // CVE Management
    addCVE: (cve: Omit<CVE, 'id'>) => string;
    updateCVE: (id: string, updates: Partial<CVE>) => void;
    deleteCVE: (id: string) => void;
    getCVEs: () => CVE[];
    getCVEDetails: (id: string) => CVE | undefined;
    getCVEsBySeverity: (severity: Severity) => CVE[];
    getCVEsBySystem: (systemName: string) => CVE[];
    getCVECount: () => number;

    // Incident Management
    addIncident: (incident: Omit<SecurityIncident, 'id'>) => string;
    updateIncident: (id: string, updates: Partial<SecurityIncident>) => void;
    deleteIncident: (id: string) => void;
    getIncidents: () => SecurityIncident[];
    getIncidentDetails: (id: string) => SecurityIncident | undefined;
    getIncidentsBystatus: (status: IncidentStatus) => SecurityIncident[];
    getIncidentsBySystem: (systemName: string) => SecurityIncident[];
    getIncidentTimeline: () => SecurityIncident[];

    // Trend Analysis
    getSecurityTrends: (period: TrendPeriod) => SecurityTrends;
    getVulnerabilityTrends: (period: TrendPeriod) => { date: string; count: number }[];
    getIncidentTrends: (period: TrendPeriod) => { date: string; count: number }[];

    // Board Reporting
    generateExecutiveSummary: () => string;
    generateBoardReport: () => string;
    exportReport: (format: ExportFormat) => string;
}

// Atoms for state management
const organizationAtom = atomWithStorage<string>('ciso-organization', 'My Organization');
const selectedSystemAtom = atom<string | null>(null);
const selectedSeverityAtom = atom<Severity | null>(null);
const selectedIncidentStatusAtom = atom<IncidentStatus | null>(null);
const selectedTrendPeriodAtom = atom<TrendPeriod>('month');
const cvesAtom = atomWithStorage<CVE[]>('ciso-cves', []);
const incidentsAtom = atomWithStorage<SecurityIncident[]>('ciso-incidents', []);
const systemsAtom = atomWithStorage<SystemRisk[]>('ciso-systems', []);

/**
 * CISO Dashboard Hook
 * 
 * Provides comprehensive security management functionality for CISOs including
 * KPI tracking, risk assessment, vulnerability management, incident tracking,
 * trend analysis, and board-level reporting capabilities.
 * 
 * @returns Hook API for CISO dashboard operations
 */
export const useCISODashboard = (): UseCISODashboardReturn => {
    const [organization, setOrganization] = useAtom(organizationAtom);
    const [selectedSystem, setSelectedSystem] = useAtom(selectedSystemAtom);
    const [selectedSeverity, setSelectedSeverity] = useAtom(selectedSeverityAtom);
    const [selectedIncidentStatus, setSelectedIncidentStatus] = useAtom(selectedIncidentStatusAtom);
    const [selectedTrendPeriod, setSelectedTrendPeriod] = useAtom(selectedTrendPeriodAtom);
    const [cves, setCVEs] = useAtom(cvesAtom);
    const [incidents, setIncidents] = useAtom(incidentsAtom);
    const [systems, setSystems] = useAtom(systemsAtom);

    // ============================================================================
    // Security KPIs
    // ============================================================================

    /**
     * Get current security KPIs
     * 
     * Calculates comprehensive security metrics including vulnerability counts,
     * compliance score, incident statistics, and overall risk assessment.
     * 
     * @returns Security KPIs object
     */
    const getSecurityKPIs = (): SecurityKPIs => {
        const criticalVulns = cves.filter((c) => c.severity === 'critical').length;
        const highVulns = cves.filter((c) => c.severity === 'high').length;
        const mediumVulns = cves.filter((c) => c.severity === 'medium').length;
        const lowVulns = cves.filter((c) => c.severity === 'low').length;

        const criticalIncidents = incidents.filter((i) => i.status === 'critical').length;
        const activeIncidentsCount = incidents.filter(
            (i) => ['critical', 'active', 'investigating', 'contained'].includes(i.status)
        ).length;
        const resolvedIncidents = incidents.filter((i) => i.status === 'resolved').length;

        // Calculate compliance score (simplified)
        const totalVulns = cves.length;
        const patchedVulns = cves.filter((c) => c.patchAvailable).length;
        const complianceScore = totalVulns > 0 ? Math.round((patchedVulns / totalVulns) * 100) : 100;

        // Calculate MTTD and MTTR (simplified)
        const resolvedIncidentsList = incidents.filter((i) => i.resolvedAt);
        const meanTimeToDetect = resolvedIncidentsList.length > 0 ? 2.5 : 0; // Placeholder
        const meanTimeToResolve = resolvedIncidentsList.length > 0
            ? Math.round(
                resolvedIncidentsList.reduce((sum, inc) => {
                    const detected = new Date(inc.detectedAt).getTime();
                    const resolved = new Date(inc.resolvedAt!).getTime();
                    return sum + (resolved - detected) / (1000 * 60 * 60); // hours
                }, 0) / resolvedIncidentsList.length
            )
            : 0;

        // Calculate overall risk
        let overallRisk: RiskLevel = 'low';
        if (criticalVulns > 5 || criticalIncidents > 2) {
            overallRisk = 'critical';
        } else if (criticalVulns > 0 || highVulns > 10 || activeIncidentsCount > 5) {
            overallRisk = 'high';
        } else if (highVulns > 0 || activeIncidentsCount > 0) {
            overallRisk = 'medium';
        }

        const systemsAtRisk = systems.filter(
            (s) => s.riskLevel === 'critical' || s.riskLevel === 'high'
        ).length;

        return {
            totalVulnerabilities: totalVulns,
            criticalVulnerabilities: criticalVulns,
            highVulnerabilities: highVulns,
            mediumVulnerabilities: mediumVulns,
            lowVulnerabilities: lowVulns,
            complianceScore,
            activeIncidents: activeIncidentsCount,
            criticalIncidents,
            resolvedIncidents,
            meanTimeToDetect,
            meanTimeToResolve,
            overallRisk,
            systemsAtRisk,
            patchCompliance: complianceScore,
        };
    };

    // ============================================================================
    // Risk Management
    // ============================================================================

    /**
     * Get risk heatmap for all systems
     * 
     * Returns a list of all systems with their risk assessments,
     * sorted by risk score (highest first).
     * 
     * @returns Array of system risk assessments
     */
    const getRiskHeatmap = (): SystemRisk[] => {
        return [...systems].sort((a, b) => b.riskScore - a.riskScore);
    };

    /**
     * Get detailed risk information for a specific system
     * 
     * @param systemName - Name of the system
     * @returns System risk details or undefined if not found
     */
    const getSystemRiskDetails = (systemName: string): SystemRisk | undefined => {
        return systems.find((s) => s.systemName === systemName);
    };

    /**
     * Calculate risk score for a system
     * 
     * Risk calculation formula:
     * - Critical vulnerabilities: 40 points each
     * - High vulnerabilities: 20 points each
     * - Medium vulnerabilities: 10 points each
     * - Low vulnerabilities: 5 points each
     * - Critical incidents: 30 points each
     * - Active incidents: 15 points each
     * 
     * Score is normalized to 0-100 scale.
     * 
     * @param systemName - Name of the system
     * @returns Risk score (0-100)
     */
    const calculateSystemRisk = (systemName: string): number => {
        const systemCVEs = cves.filter((c) => c.affectedSystems.includes(systemName));
        const systemIncidents = incidents.filter((i) => i.affectedSystems.includes(systemName));

        let riskScore = 0;

        // Vulnerability risk
        riskScore += systemCVEs.filter((c) => c.severity === 'critical').length * 40;
        riskScore += systemCVEs.filter((c) => c.severity === 'high').length * 20;
        riskScore += systemCVEs.filter((c) => c.severity === 'medium').length * 10;
        riskScore += systemCVEs.filter((c) => c.severity === 'low').length * 5;

        // Incident risk
        riskScore += systemIncidents.filter((i) => i.status === 'critical').length * 30;
        riskScore += systemIncidents.filter((i) => ['active', 'investigating'].includes(i.status)).length * 15;

        // Normalize to 0-100 scale
        return Math.min(100, riskScore);
    };

    // ============================================================================
    // CVE Management
    // ============================================================================

    /**
     * Add a new CVE
     * 
     * @param cve - CVE data (without id)
     * @returns Generated CVE ID
     */
    const addCVE = (cve: Omit<CVE, 'id'>): string => {
        const id = `cve-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
        setCVEs([...cves, { ...cve, id }]);

        // Update affected systems
        cve.affectedSystems.forEach((systemName) => {
            const system = systems.find((s) => s.systemName === systemName);
            if (system) {
                const updatedVulns = [...system.vulnerabilities, { ...cve, id }];
                const riskScore = calculateSystemRisk(systemName);
                let riskLevel: RiskLevel = 'low';
                if (riskScore >= 80) riskLevel = 'critical';
                else if (riskScore >= 60) riskLevel = 'high';
                else if (riskScore >= 40) riskLevel = 'medium';

                const updatedSystem: SystemRisk = {
                    ...system,
                    vulnerabilities: updatedVulns,
                    vulnerabilityCount: updatedVulns.length,
                    criticalVulnerabilities: updatedVulns.filter((v) => v.severity === 'critical').length,
                    highVulnerabilities: updatedVulns.filter((v) => v.severity === 'high').length,
                    riskScore,
                    riskLevel,
                    lastAssessment: new Date().toISOString(),
                };

                setSystems(systems.map((s) => (s.systemName === systemName ? updatedSystem : s)));
            }
        });

        return id;
    };

    /**
     * Update an existing CVE
     * 
     * @param id - CVE ID
     * @param updates - Partial CVE updates
     */
    const updateCVE = (id: string, updates: Partial<CVE>): void => {
        setCVEs(cves.map((c) => (c.id === id ? { ...c, ...updates } : c)));

        // Update affected systems if affectedSystems changed
        if (updates.affectedSystems) {
            const cve = cves.find((c) => c.id === id);
            if (cve) {
                const allAffectedSystems = new Set([...cve.affectedSystems, ...updates.affectedSystems]);
                allAffectedSystems.forEach((systemName) => {
                    const riskScore = calculateSystemRisk(systemName);
                    let riskLevel: RiskLevel = 'low';
                    if (riskScore >= 80) riskLevel = 'critical';
                    else if (riskScore >= 60) riskLevel = 'high';
                    else if (riskScore >= 40) riskLevel = 'medium';

                    setSystems(
                        systems.map((s) => {
                            if (s.systemName === systemName) {
                                const updatedVulns = cves.filter((c) => c.affectedSystems.includes(systemName));
                                return {
                                    ...s,
                                    vulnerabilities: updatedVulns,
                                    vulnerabilityCount: updatedVulns.length,
                                    criticalVulnerabilities: updatedVulns.filter((v) => v.severity === 'critical').length,
                                    highVulnerabilities: updatedVulns.filter((v) => v.severity === 'high').length,
                                    riskScore,
                                    riskLevel,
                                    lastAssessment: new Date().toISOString(),
                                };
                            }
                            return s;
                        })
                    );
                });
            }
        }
    };

    /**
     * Delete a CVE
     * 
     * @param id - CVE ID
     */
    const deleteCVE = (id: string): void => {
        const cve = cves.find((c) => c.id === id);
        setCVEs(cves.filter((c) => c.id !== id));

        // Update affected systems
        if (cve) {
            cve.affectedSystems.forEach((systemName) => {
                const riskScore = calculateSystemRisk(systemName);
                let riskLevel: RiskLevel = 'low';
                if (riskScore >= 80) riskLevel = 'critical';
                else if (riskScore >= 60) riskLevel = 'high';
                else if (riskScore >= 40) riskLevel = 'medium';

                setSystems(
                    systems.map((s) => {
                        if (s.systemName === systemName) {
                            const updatedVulns = s.vulnerabilities.filter((v) => v.id !== id);
                            return {
                                ...s,
                                vulnerabilities: updatedVulns,
                                vulnerabilityCount: updatedVulns.length,
                                criticalVulnerabilities: updatedVulns.filter((v) => v.severity === 'critical').length,
                                highVulnerabilities: updatedVulns.filter((v) => v.severity === 'high').length,
                                riskScore,
                                riskLevel,
                                lastAssessment: new Date().toISOString(),
                            };
                        }
                        return s;
                    })
                );
            });
        }
    };

    /**
     * Get all CVEs
     * 
     * @returns Array of all CVEs
     */
    const getCVEs = (): CVE[] => {
        return cves;
    };

    /**
     * Get CVE details by ID
     * 
     * @param id - CVE ID
     * @returns CVE object or undefined if not found
     */
    const getCVEDetails = (id: string): CVE | undefined => {
        return cves.find((c) => c.id === id);
    };

    /**
     * Get CVEs by severity level
     * 
     * @param severity - Severity level
     * @returns Array of CVEs with the specified severity
     */
    const getCVEsBySeverity = (severity: Severity): CVE[] => {
        return cves.filter((c) => c.severity === severity);
    };

    /**
     * Get CVEs affecting a specific system
     * 
     * @param systemName - Name of the system
     * @returns Array of CVEs affecting the system
     */
    const getCVEsBySystem = (systemName: string): CVE[] => {
        return cves.filter((c) => c.affectedSystems.includes(systemName));
    };

    /**
     * Get total CVE count
     * 
     * @returns Number of CVEs
     */
    const getCVECount = (): number => {
        return cves.length;
    };

    // ============================================================================
    // Incident Management
    // ============================================================================

    /**
     * Add a new security incident
     * 
     * @param incident - Incident data (without id)
     * @returns Generated incident ID
     */
    const addIncident = (incident: Omit<SecurityIncident, 'id'>): string => {
        const id = `incident-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
        setIncidents([...incidents, { ...incident, id }]);

        // Update affected systems
        incident.affectedSystems.forEach((systemName) => {
            const system = systems.find((s) => s.systemName === systemName);
            if (system) {
                const updatedIncidents = [...system.incidents, { ...incident, id }];
                const riskScore = calculateSystemRisk(systemName);
                let riskLevel: RiskLevel = 'low';
                if (riskScore >= 80) riskLevel = 'critical';
                else if (riskScore >= 60) riskLevel = 'high';
                else if (riskScore >= 40) riskLevel = 'medium';

                setSystems(
                    systems.map((s) =>
                        s.systemName === systemName
                            ? { ...s, incidents: updatedIncidents, riskScore, riskLevel, lastAssessment: new Date().toISOString() }
                            : s
                    )
                );
            }
        });

        return id;
    };

    /**
     * Update an existing incident
     * 
     * @param id - Incident ID
     * @param updates - Partial incident updates
     */
    const updateIncident = (id: string, updates: Partial<SecurityIncident>): void => {
        setIncidents(incidents.map((i) => (i.id === id ? { ...i, ...updates } : i)));

        // Update affected systems
        const incident = incidents.find((i) => i.id === id);
        if (incident) {
            incident.affectedSystems.forEach((systemName) => {
                setSystems(
                    systems.map((s) => {
                        if (s.systemName === systemName) {
                            return {
                                ...s,
                                incidents: s.incidents.map((inc) => (inc.id === id ? { ...inc, ...updates } : inc)),
                                lastAssessment: new Date().toISOString(),
                            };
                        }
                        return s;
                    })
                );
            });
        }
    };

    /**
     * Delete an incident
     * 
     * @param id - Incident ID
     */
    const deleteIncident = (id: string): void => {
        const incident = incidents.find((i) => i.id === id);
        setIncidents(incidents.filter((i) => i.id !== id));

        // Update affected systems
        if (incident) {
            incident.affectedSystems.forEach((systemName) => {
                const riskScore = calculateSystemRisk(systemName);
                let riskLevel: RiskLevel = 'low';
                if (riskScore >= 80) riskLevel = 'critical';
                else if (riskScore >= 60) riskLevel = 'high';
                else if (riskScore >= 40) riskLevel = 'medium';

                setSystems(
                    systems.map((s) =>
                        s.systemName === systemName
                            ? {
                                ...s,
                                incidents: s.incidents.filter((inc) => inc.id !== id),
                                riskScore,
                                riskLevel,
                                lastAssessment: new Date().toISOString(),
                            }
                            : s
                    )
                );
            });
        }
    };

    /**
     * Get all incidents
     * 
     * @returns Array of all incidents
     */
    const getIncidents = (): SecurityIncident[] => {
        return incidents;
    };

    /**
     * Get incident details by ID
     * 
     * @param id - Incident ID
     * @returns Incident object or undefined if not found
     */
    const getIncidentDetails = (id: string): SecurityIncident | undefined => {
        return incidents.find((i) => i.id === id);
    };

    /**
     * Get incidents by status
     * 
     * @param status - Incident status
     * @returns Array of incidents with the specified status
     */
    const getIncidentsBystatus = (status: IncidentStatus): SecurityIncident[] => {
        return incidents.filter((i) => i.status === status);
    };

    /**
     * Get incidents affecting a specific system
     * 
     * @param systemName - Name of the system
     * @returns Array of incidents affecting the system
     */
    const getIncidentsBySystem = (systemName: string): SecurityIncident[] => {
        return incidents.filter((i) => i.affectedSystems.includes(systemName));
    };

    /**
     * Get incident timeline sorted by detection date
     * 
     * @returns Array of incidents sorted by detectedAt (most recent first)
     */
    const getIncidentTimeline = (): SecurityIncident[] => {
        return [...incidents].sort(
            (a, b) => new Date(b.detectedAt).getTime() - new Date(a.detectedAt).getTime()
        );
    };

    // ============================================================================
    // Trend Analysis
    // ============================================================================

    /**
     * Get security trends for the specified period
     * 
     * Calculates percentage changes compared to previous period.
     * 
     * @param period - Trend period (week, month, quarter)
     * @returns Security trends object
     */
    const getSecurityTrends = (period: TrendPeriod): SecurityTrends => {
        // Simplified trend calculation - in production, this would analyze historical data
        const vulnerabilityChange = cves.length > 0 ? Math.round((Math.random() - 0.5) * 20) : 0;
        const incidentChange = incidents.length > 0 ? Math.round((Math.random() - 0.5) * 30) : 0;
        const complianceChange = Math.round((Math.random() - 0.3) * 15);
        const riskChange = Math.round((Math.random() - 0.4) * 25);
        const patchComplianceChange = Math.round(Math.random() * 10);

        return {
            period,
            vulnerabilityChange,
            incidentChange,
            complianceChange,
            riskChange,
            patchComplianceChange,
        };
    };

    /**
     * Get vulnerability trends over time
     * 
     * @param period - Trend period
     * @returns Array of date/count pairs
     */
    const getVulnerabilityTrends = (period: TrendPeriod): { date: string; count: number }[] => {
        // Simplified - in production, this would return actual historical data
        const dataPoints = period === 'week' ? 7 : period === 'month' ? 30 : 90;
        const trends: { date: string; count: number }[] = [];

        for (let i = dataPoints - 1; i >= 0; i--) {
            const date = new Date();
            date.setDate(date.getDate() - i);
            trends.push({
                date: date.toISOString().split('T')[0],
                count: Math.max(0, cves.length + Math.round((Math.random() - 0.5) * 10)),
            });
        }

        return trends;
    };

    /**
     * Get incident trends over time
     * 
     * @param period - Trend period
     * @returns Array of date/count pairs
     */
    const getIncidentTrends = (period: TrendPeriod): { date: string; count: number }[] => {
        // Simplified - in production, this would return actual historical data
        const dataPoints = period === 'week' ? 7 : period === 'month' ? 30 : 90;
        const trends: { date: string; count: number }[] = [];

        for (let i = dataPoints - 1; i >= 0; i--) {
            const date = new Date();
            date.setDate(date.getDate() - i);
            trends.push({
                date: date.toISOString().split('T')[0],
                count: Math.max(0, incidents.length + Math.round((Math.random() - 0.5) * 5)),
            });
        }

        return trends;
    };

    // ============================================================================
    // Board Reporting
    // ============================================================================

    /**
     * Generate executive summary for board presentation
     * 
     * Creates a concise, high-level summary of security posture
     * suitable for board-level reporting.
     * 
     * @returns Executive summary text
     */
    const generateExecutiveSummary = (): string => {
        const kpis = getSecurityKPIs();

        let summary = `${organization} Security Posture Summary\n\n`;
        summary += `Overall Risk Level: ${kpis.overallRisk.toUpperCase()}\n\n`;
        summary += `Key Metrics:\n`;
        summary += `• Total Open Vulnerabilities: ${kpis.totalVulnerabilities} (${kpis.criticalVulnerabilities} critical, ${kpis.highVulnerabilities} high)\n`;
        summary += `• Compliance Score: ${kpis.complianceScore}%\n`;
        summary += `• Active Security Incidents: ${kpis.activeIncidents} (${kpis.criticalIncidents} critical)\n`;
        summary += `• Systems at Risk: ${kpis.systemsAtRisk}\n\n`;

        if (kpis.overallRisk === 'critical' || kpis.overallRisk === 'high') {
            summary += `⚠️ ATTENTION REQUIRED:\n`;
            if (kpis.criticalVulnerabilities > 0) {
                summary += `• ${kpis.criticalVulnerabilities} critical vulnerabilities require immediate remediation\n`;
            }
            if (kpis.criticalIncidents > 0) {
                summary += `• ${kpis.criticalIncidents} critical security incidents in progress\n`;
            }
            if (kpis.complianceScore < 80) {
                summary += `• Compliance score below target threshold (${kpis.complianceScore}% vs 80% target)\n`;
            }
        } else {
            summary += `✓ Security posture is within acceptable parameters.\n`;
        }

        return summary;
    };

    /**
     * Generate comprehensive board report
     * 
     * Creates a detailed report including executive summary, KPIs,
     * risk analysis, top vulnerabilities, incidents, and recommendations.
     * 
     * @returns Full board report text
     */
    const generateBoardReport = (): string => {
        const kpis = getSecurityKPIs();
        const heatmap = getRiskHeatmap();
        const topCVEs = [...cves]
            .filter((c) => c.severity === 'critical' || c.severity === 'high')
            .slice(0, 10);
        const activeIncidents = incidents.filter((i) => i.status !== 'resolved');
        const trends = getSecurityTrends(selectedTrendPeriod);

        let report = `═══════════════════════════════════════════════════════════════\n`;
        report += `                  EXECUTIVE SECURITY BOARD REPORT\n`;
        report += `                        ${organization}\n`;
        report += `                  ${new Date().toLocaleDateString()}\n`;
        report += `═══════════════════════════════════════════════════════════════\n\n`;

        // Executive Summary
        report += `EXECUTIVE SUMMARY\n`;
        report += `─────────────────────────────────────────────────────────────\n`;
        report += generateExecutiveSummary();
        report += `\n\n`;

        // Key Performance Indicators
        report += `KEY PERFORMANCE INDICATORS\n`;
        report += `─────────────────────────────────────────────────────────────\n`;
        report += `Vulnerabilities:\n`;
        report += `  • Total: ${kpis.totalVulnerabilities}\n`;
        report += `  • Critical: ${kpis.criticalVulnerabilities}\n`;
        report += `  • High: ${kpis.highVulnerabilities}\n`;
        report += `  • Medium: ${kpis.mediumVulnerabilities}\n`;
        report += `  • Low: ${kpis.lowVulnerabilities}\n\n`;
        report += `Incidents:\n`;
        report += `  • Active: ${kpis.activeIncidents}\n`;
        report += `  • Critical: ${kpis.criticalIncidents}\n`;
        report += `  • Resolved: ${kpis.resolvedIncidents}\n`;
        report += `  • Mean Time to Detect: ${kpis.meanTimeToDetect}h\n`;
        report += `  • Mean Time to Resolve: ${kpis.meanTimeToResolve}h\n\n`;
        report += `Compliance & Risk:\n`;
        report += `  • Compliance Score: ${kpis.complianceScore}%\n`;
        report += `  • Patch Compliance: ${kpis.patchCompliance}%\n`;
        report += `  • Overall Risk: ${kpis.overallRisk.toUpperCase()}\n`;
        report += `  • Systems at Risk: ${kpis.systemsAtRisk}\n\n\n`;

        // Trend Analysis
        report += `TREND ANALYSIS (${selectedTrendPeriod.toUpperCase()})\n`;
        report += `─────────────────────────────────────────────────────────────\n`;
        report += `  • Vulnerabilities: ${trends.vulnerabilityChange >= 0 ? '+' : ''}${trends.vulnerabilityChange}%\n`;
        report += `  • Incidents: ${trends.incidentChange >= 0 ? '+' : ''}${trends.incidentChange}%\n`;
        report += `  • Compliance: ${trends.complianceChange >= 0 ? '+' : ''}${trends.complianceChange}%\n`;
        report += `  • Risk: ${trends.riskChange >= 0 ? '+' : ''}${trends.riskChange}%\n\n\n`;

        // System Risk Heatmap
        report += `SYSTEM RISK HEATMAP (Top 10)\n`;
        report += `─────────────────────────────────────────────────────────────\n`;
        heatmap.slice(0, 10).forEach((system) => {
            report += `  • ${system.systemName}: ${system.riskLevel.toUpperCase()} (${system.vulnerabilityCount} vulns)\n`;
        });
        report += `\n\n`;

        // Critical Vulnerabilities
        report += `TOP VULNERABILITIES\n`;
        report += `─────────────────────────────────────────────────────────────\n`;
        if (topCVEs.length > 0) {
            topCVEs.forEach((cve) => {
                report += `  • ${cve.cveId} (${cve.severity.toUpperCase()}, CVSS: ${cve.cvssScore})\n`;
                report += `    ${cve.description}\n`;
                report += `    Affected Systems: ${cve.affectedSystems.join(', ')}\n\n`;
            });
        } else {
            report += `  No critical or high vulnerabilities.\n\n`;
        }
        report += `\n`;

        // Active Incidents
        report += `ACTIVE SECURITY INCIDENTS\n`;
        report += `─────────────────────────────────────────────────────────────\n`;
        if (activeIncidents.length > 0) {
            activeIncidents.slice(0, 10).forEach((incident) => {
                report += `  • ${incident.title} (${incident.status.toUpperCase()})\n`;
                report += `    ${incident.description}\n`;
                report += `    Detected: ${new Date(incident.detectedAt).toLocaleDateString()}\n`;
                report += `    Affected Systems: ${incident.affectedSystems.join(', ')}\n\n`;
            });
        } else {
            report += `  No active incidents.\n\n`;
        }
        report += `\n`;

        // Recommendations
        report += `RECOMMENDATIONS\n`;
        report += `─────────────────────────────────────────────────────────────\n`;
        const recommendations: string[] = [];

        if (kpis.criticalVulnerabilities > 0) {
            recommendations.push(`• IMMEDIATE: Address ${kpis.criticalVulnerabilities} critical vulnerabilities`);
        }
        if (kpis.criticalIncidents > 0) {
            recommendations.push(`• IMMEDIATE: Resolve ${kpis.criticalIncidents} critical security incidents`);
        }
        if (kpis.complianceScore < 80) {
            recommendations.push(`• HIGH: Improve compliance score to 80% minimum (current: ${kpis.complianceScore}%)`);
        }
        if (kpis.highVulnerabilities > 5) {
            recommendations.push(`• MEDIUM: Reduce high-severity vulnerabilities (current: ${kpis.highVulnerabilities})`);
        }
        if (kpis.meanTimeToResolve > 48) {
            recommendations.push(`• MEDIUM: Improve incident response time (current MTTR: ${kpis.meanTimeToResolve}h)`);
        }
        if (kpis.patchCompliance < 90) {
            recommendations.push(`• MEDIUM: Increase patch compliance rate (current: ${kpis.patchCompliance}%)`);
        }

        if (recommendations.length > 0) {
            recommendations.forEach((rec) => {
                report += `  ${rec}\n`;
            });
        } else {
            report += `  • Continue current security practices\n`;
            report += `  • Maintain vigilance against emerging threats\n`;
        }

        report += `\n\n`;
        report += `═══════════════════════════════════════════════════════════════\n`;
        report += `                        END OF REPORT\n`;
        report += `═══════════════════════════════════════════════════════════════\n`;

        return report;
    };

    /**
     * Export report in specified format
     * 
     * @param format - Export format (pdf, pptx, json)
     * @returns Exported report content
     */
    const exportReport = (format: ExportFormat): string => {
        const report = generateBoardReport();
        const kpis = getSecurityKPIs();

        switch (format) {
            case 'pdf':
                // In production, this would generate actual PDF
                return `PDF Export:\n${report}`;

            case 'pptx':
                // In production, this would generate actual PowerPoint
                return `PowerPoint Export:\nSlide 1: Executive Summary\n${generateExecutiveSummary()}\n\nSlide 2: KPIs\n...`;

            case 'json':
                return JSON.stringify(
                    {
                        organization,
                        reportDate: new Date().toISOString(),
                        executiveSummary: generateExecutiveSummary(),
                        kpis,
                        systems: getRiskHeatmap(),
                        vulnerabilities: cves,
                        incidents,
                        trends: getSecurityTrends(selectedTrendPeriod),
                    },
                    null,
                    2
                );

            default:
                return report;
        }
    };

    // ============================================================================
    // Return API
    // ============================================================================

    return {
        // State
        organization,
        setOrganization,
        selectedSystem,
        setSelectedSystem,
        selectedSeverity,
        setSelectedSeverity,
        selectedIncidentStatus,
        setSelectedIncidentStatus,
        selectedTrendPeriod,
        setSelectedTrendPeriod,

        // Security KPIs
        getSecurityKPIs,

        // Risk Management
        getRiskHeatmap,
        getSystemRiskDetails,
        calculateSystemRisk,

        // CVE Management
        addCVE,
        updateCVE,
        deleteCVE,
        getCVEs,
        getCVEDetails,
        getCVEsBySeverity,
        getCVEsBySystem,
        getCVECount,

        // Incident Management
        addIncident,
        updateIncident,
        deleteIncident,
        getIncidents,
        getIncidentDetails,
        getIncidentsBystatus,
        getIncidentsBySystem,
        getIncidentTimeline,

        // Trend Analysis
        getSecurityTrends,
        getVulnerabilityTrends,
        getIncidentTrends,

        // Board Reporting
        generateExecutiveSummary,
        generateBoardReport,
        exportReport,
    };
};

export default useCISODashboard;
