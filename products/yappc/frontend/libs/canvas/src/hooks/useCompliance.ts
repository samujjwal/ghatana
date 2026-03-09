/**
 * Compliance Hook
 * 
 * @doc.type hook
 * @doc.purpose Manage compliance controls, evidence, and audit reporting
 * @doc.layer product
 * @doc.pattern State Management Hook
 * 
 * Features:
 * - Multi-framework control management (SOC2, HIPAA, GDPR, PCI-DSS, ISO 27001)
 * - Evidence collection and tracking
 * - Gap analysis and remediation tracking
 * - Risk scoring and prioritization
 * - Compliance metrics and reporting
 * - Export to PDF, CSV, and JSON formats
 */

import { useState, useCallback } from 'react';

export type ComplianceFramework = 'soc2' | 'hipaa' | 'gdpr' | 'pci-dss' | 'iso-27001';

export type ControlStatus =
    | 'not-implemented'
    | 'in-progress'
    | 'implemented'
    | 'needs-review'
    | 'failed';

export type EvidenceType = 'document' | 'screenshot' | 'log' | 'attestation' | 'artifact';

export type Severity = 'low' | 'medium' | 'high' | 'critical';

export type RiskLevel = 'low' | 'medium' | 'high' | 'critical';

export interface ComplianceControl {
    id: string;
    framework: ComplianceFramework;
    controlId: string;
    title: string;
    description: string;
    category: string;
    status: ControlStatus;
    assignee?: string;
    dueDate?: string;
    lastReviewedAt?: string;
    implementedAt?: string;
    testingFrequency?: 'manual' | 'automated' | 'continuous';
}

export interface Evidence {
    id: string;
    controlId: string;
    type: EvidenceType;
    title: string;
    description: string;
    url?: string;
    uploadedAt: string;
    uploadedBy?: string;
    expiresAt?: string;
    verified?: boolean;
}

export interface GapAnalysisResult {
    gaps: Array<{
        controlId: string;
        title: string;
        description: string;
        severity: Severity;
        remediation: string;
        estimatedEffort: number; // days
    }>;
    recommendations: string[];
    estimatedDaysToClose: number;
}

export interface ComplianceOverview {
    totalControls: number;
    byStatus: Record<ControlStatus, number>;
    byFramework: Record<ComplianceFramework, number>;
    complianceScore: number; // 0-100
    totalEvidence: number;
}

export interface RiskScore {
    score: number; // 0-100
    overallRisk: RiskLevel;
    factors: Array<{
        factor: string;
        weight: number;
        value: number;
        impact: string;
    }>;
}

export interface UseComplianceReturn {
    // State
    programName: string;
    setProgramName: (name: string) => void;
    selectedFramework: ComplianceFramework;
    setSelectedFramework: (framework: ComplianceFramework) => void;
    controls: ComplianceControl[];
    evidence: Evidence[];

    // Control Management
    addControl: (control: Omit<ComplianceControl, 'id'>) => string;
    updateControl: (id: string, updates: Partial<ComplianceControl>) => void;
    deleteControl: (id: string) => void;
    getControl: (id: string) => ComplianceControl | undefined;
    getControlCount: () => number;
    getControlsByFramework: (framework: ComplianceFramework) => ComplianceControl[];
    getControlsByStatus: (status: ControlStatus) => ComplianceControl[];

    // Evidence Management
    addEvidence: (controlId: string, evidence: Omit<Evidence, 'id' | 'controlId'>) => string;
    updateEvidence: (id: string, updates: Partial<Evidence>) => void;
    deleteEvidence: (id: string) => void;
    getEvidenceForControl: (controlId: string) => Evidence[];

    // Analysis
    getComplianceOverview: () => ComplianceOverview;
    performGapAnalysis: () => GapAnalysisResult;
    calculateRiskScore: () => RiskScore;
    validateEvidence: (controlId: string) => boolean;

    // Export
    exportToAuditReport: () => string;
    exportToCSV: () => string;
    exportToJSON: () => string;
}

// Helper: Generate unique ID
const generateId = () => `comp_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

// Helper: Calculate compliance score
const calculateComplianceScore = (controls: ComplianceControl[]): number => {
    if (controls.length === 0) return 0;

    const weights: Record<ControlStatus, number> = {
        'not-implemented': 0,
        'in-progress': 0.5,
        implemented: 1,
        'needs-review': 0.7,
        failed: 0,
    };

    const totalWeight = controls.reduce((sum, control) => sum + weights[control.status], 0);
    return Math.round((totalWeight / controls.length) * 100);
};

// Helper: Analyze gaps
const analyzeGaps = (controls: ComplianceControl[], evidence: Evidence[]): GapAnalysisResult => {
    const gaps: GapAnalysisResult['gaps'] = [];
    const recommendations: string[] = [];

    controls.forEach((control) => {
        const controlEvidence = evidence.filter((e) => e.controlId === control.id);

        // Gap: Not implemented
        if (control.status === 'not-implemented') {
            gaps.push({
                controlId: control.controlId,
                title: control.title,
                description: `Control ${control.controlId} has not been implemented`,
                severity: 'high',
                remediation: `Implement control ${control.controlId} according to ${control.framework.toUpperCase()} requirements`,
                estimatedEffort: 5,
            });
        }

        // Gap: Failed audit
        if (control.status === 'failed') {
            gaps.push({
                controlId: control.controlId,
                title: control.title,
                description: `Control ${control.controlId} failed audit`,
                severity: 'critical',
                remediation: `Investigate failure and remediate control ${control.controlId}`,
                estimatedEffort: 3,
            });
        }

        // Gap: Missing evidence
        if (control.status === 'implemented' && controlEvidence.length === 0) {
            gaps.push({
                controlId: control.controlId,
                title: control.title,
                description: `No evidence provided for ${control.controlId}`,
                severity: 'medium',
                remediation: `Collect and upload evidence for control ${control.controlId}`,
                estimatedEffort: 1,
            });
        }

        // Gap: Needs review
        if (control.status === 'needs-review') {
            gaps.push({
                controlId: control.controlId,
                title: control.title,
                description: `Control ${control.controlId} requires review`,
                severity: 'low',
                remediation: `Schedule review for control ${control.controlId}`,
                estimatedEffort: 2,
            });
        }
    });

    // Generate recommendations
    const notImplementedCount = controls.filter((c) => c.status === 'not-implemented').length;
    const failedCount = controls.filter((c) => c.status === 'failed').length;
    const noEvidenceCount = controls.filter(
        (c) => c.status === 'implemented' && evidence.filter((e) => e.controlId === c.id).length === 0
    ).length;

    if (notImplementedCount > 0) {
        recommendations.push(
            `Prioritize implementing ${notImplementedCount} not-implemented control(s) to improve compliance score`
        );
    }

    if (failedCount > 0) {
        recommendations.push(
            `Immediately address ${failedCount} failed control(s) as they represent active compliance violations`
        );
    }

    if (noEvidenceCount > 0) {
        recommendations.push(
            `Collect evidence for ${noEvidenceCount} implemented control(s) to support audit readiness`
        );
    }

    if (controls.length < 10) {
        recommendations.push('Add more controls to ensure comprehensive coverage of compliance requirements');
    }

    const estimatedDaysToClose = gaps.reduce((sum, gap) => sum + gap.estimatedEffort, 0);

    return { gaps, recommendations, estimatedDaysToClose };
};

// Helper: Calculate risk score
const calculateRisk = (controls: ComplianceControl[], evidence: Evidence[]): RiskScore => {
    const factors: RiskScore['factors'] = [];
    let totalScore = 0;

    // Factor 1: Control Implementation (40% weight)
    const implementedCount = controls.filter((c) => c.status === 'implemented').length;
    const implementationRate = controls.length > 0 ? implementedCount / controls.length : 0;
    const implementationScore = implementationRate * 100;
    factors.push({
        factor: 'Control Implementation',
        weight: 0.4,
        value: implementationScore,
        impact: implementationRate > 0.8 ? 'Low risk' : implementationRate > 0.5 ? 'Medium risk' : 'High risk',
    });
    totalScore += implementationScore * 0.4;

    // Factor 2: Evidence Coverage (30% weight)
    const controlsWithEvidence = new Set(evidence.map((e) => e.controlId)).size;
    const evidenceCoverage = controls.length > 0 ? controlsWithEvidence / controls.length : 0;
    const evidenceScore = evidenceCoverage * 100;
    factors.push({
        factor: 'Evidence Coverage',
        weight: 0.3,
        value: evidenceScore,
        impact: evidenceCoverage > 0.8 ? 'Low risk' : evidenceCoverage > 0.5 ? 'Medium risk' : 'High risk',
    });
    totalScore += evidenceScore * 0.3;

    // Factor 3: Failed Controls (20% weight)
    const failedCount = controls.filter((c) => c.status === 'failed').length;
    const failedRate = controls.length > 0 ? failedCount / controls.length : 0;
    const failedScore = (1 - failedRate) * 100;
    factors.push({
        factor: 'Failed Controls',
        weight: 0.2,
        value: failedScore,
        impact: failedRate === 0 ? 'Low risk' : failedRate < 0.1 ? 'Medium risk' : 'High risk',
    });
    totalScore += failedScore * 0.2;

    // Factor 4: Review Status (10% weight)
    const needsReviewCount = controls.filter((c) => c.status === 'needs-review').length;
    const reviewRate = controls.length > 0 ? needsReviewCount / controls.length : 0;
    const reviewScore = (1 - reviewRate) * 100;
    factors.push({
        factor: 'Review Status',
        weight: 0.1,
        value: reviewScore,
        impact: reviewRate < 0.2 ? 'Low risk' : reviewRate < 0.4 ? 'Medium risk' : 'High risk',
    });
    totalScore += reviewScore * 0.1;

    // Determine overall risk level
    let overallRisk: RiskLevel;
    if (totalScore >= 80) {
        overallRisk = 'low';
    } else if (totalScore >= 60) {
        overallRisk = 'medium';
    } else if (totalScore >= 40) {
        overallRisk = 'high';
    } else {
        overallRisk = 'critical';
    }

    return {
        score: Math.round(totalScore),
        overallRisk,
        factors,
    };
};

export const useCompliance = (): UseComplianceReturn => {
    const [programName, setProgramName] = useState('Compliance Program');
    const [selectedFramework, setSelectedFramework] = useState<ComplianceFramework>('soc2');
    const [controls, setControls] = useState<ComplianceControl[]>([]);
    const [evidence, setEvidence] = useState<Evidence[]>([]);

    // Control Management
    const addControl = useCallback((control: Omit<ComplianceControl, 'id'>): string => {
        const id = generateId();
        const newControl: ComplianceControl = { ...control, id };
        setControls((prev) => [...prev, newControl]);
        return id;
    }, []);

    const updateControl = useCallback((id: string, updates: Partial<ComplianceControl>) => {
        setControls((prev) =>
            prev.map((control) =>
                control.id === id
                    ? {
                        ...control,
                        ...updates,
                        ...(updates.status === 'implemented' && !control.implementedAt
                            ? { implementedAt: new Date().toISOString() }
                            : {}),
                    }
                    : control
            )
        );
    }, []);

    const deleteControl = useCallback((id: string) => {
        setControls((prev) => prev.filter((control) => control.id !== id));
        // Also delete associated evidence
        setEvidence((prev) => prev.filter((ev) => ev.controlId !== id));
    }, []);

    const getControl = useCallback(
        (id: string): ComplianceControl | undefined => {
            return controls.find((control) => control.id === id);
        },
        [controls]
    );

    const getControlCount = useCallback((): number => {
        return controls.length;
    }, [controls]);

    const getControlsByFramework = useCallback(
        (framework: ComplianceFramework): ComplianceControl[] => {
            return controls.filter((control) => control.framework === framework);
        },
        [controls]
    );

    const getControlsByStatus = useCallback(
        (status: ControlStatus): ComplianceControl[] => {
            return controls.filter((control) => control.status === status);
        },
        [controls]
    );

    // Evidence Management
    const addEvidence = useCallback((controlId: string, ev: Omit<Evidence, 'id' | 'controlId'>): string => {
        const id = generateId();
        const newEvidence: Evidence = { ...ev, id, controlId };
        setEvidence((prev) => [...prev, newEvidence]);
        return id;
    }, []);

    const updateEvidence = useCallback((id: string, updates: Partial<Evidence>) => {
        setEvidence((prev) => prev.map((ev) => (ev.id === id ? { ...ev, ...updates } : ev)));
    }, []);

    const deleteEvidence = useCallback((id: string) => {
        setEvidence((prev) => prev.filter((ev) => ev.id !== id));
    }, []);

    const getEvidenceForControl = useCallback(
        (controlId: string): Evidence[] => {
            return evidence.filter((ev) => ev.controlId === controlId);
        },
        [evidence]
    );

    // Analysis
    const getComplianceOverview = useCallback((): ComplianceOverview => {
        const byStatus: Record<ControlStatus, number> = {
            'not-implemented': 0,
            'in-progress': 0,
            implemented: 0,
            'needs-review': 0,
            failed: 0,
        };

        const byFramework: Record<ComplianceFramework, number> = {
            soc2: 0,
            hipaa: 0,
            gdpr: 0,
            'pci-dss': 0,
            'iso-27001': 0,
        };

        controls.forEach((control) => {
            byStatus[control.status]++;
            byFramework[control.framework]++;
        });

        return {
            totalControls: controls.length,
            byStatus,
            byFramework,
            complianceScore: calculateComplianceScore(controls),
            totalEvidence: evidence.length,
        };
    }, [controls, evidence]);

    const performGapAnalysis = useCallback((): GapAnalysisResult => {
        return analyzeGaps(controls, evidence);
    }, [controls, evidence]);

    const calculateRiskScore = useCallback((): RiskScore => {
        return calculateRisk(controls, evidence);
    }, [controls, evidence]);

    const validateEvidence = useCallback(
        (controlId: string): boolean => {
            const controlEvidence = evidence.filter((ev) => ev.controlId === controlId);
            return controlEvidence.length > 0 && controlEvidence.every((ev) => ev.verified !== false);
        },
        [evidence]
    );

    // Export
    const exportToAuditReport = useCallback((): string => {
        const overview = getComplianceOverview();
        const gapAnalysis = performGapAnalysis();
        const riskScore = calculateRiskScore();

        let report = `COMPLIANCE AUDIT REPORT\n`;
        report += `Program: ${programName}\n`;
        report += `Generated: ${new Date().toISOString()}\n`;
        report += `\n`;
        report += `=== EXECUTIVE SUMMARY ===\n`;
        report += `Total Controls: ${overview.totalControls}\n`;
        report += `Compliance Score: ${overview.complianceScore}%\n`;
        report += `Risk Level: ${riskScore.overallRisk.toUpperCase()} (${riskScore.score}/100)\n`;
        report += `Total Evidence: ${overview.totalEvidence}\n`;
        report += `\n`;
        report += `=== CONTROL STATUS BREAKDOWN ===\n`;
        Object.entries(overview.byStatus).forEach(([status, count]) => {
            report += `${status.replace('-', ' ').toUpperCase()}: ${count}\n`;
        });
        report += `\n`;
        report += `=== FRAMEWORK COVERAGE ===\n`;
        Object.entries(overview.byFramework).forEach(([framework, count]) => {
            if (count > 0) {
                report += `${framework.toUpperCase()}: ${count} controls\n`;
            }
        });
        report += `\n`;
        report += `=== RISK FACTORS ===\n`;
        riskScore.factors.forEach((factor) => {
            report += `${factor.factor}: ${factor.value.toFixed(1)}% (Weight: ${(factor.weight * 100).toFixed(0)}%) - ${factor.impact}\n`;
        });
        report += `\n`;
        report += `=== GAP ANALYSIS ===\n`;
        report += `Total Gaps: ${gapAnalysis.gaps.length}\n`;
        report += `Estimated Days to Close: ${gapAnalysis.estimatedDaysToClose}\n`;
        report += `\n`;
        gapAnalysis.gaps.forEach((gap, idx) => {
            report += `${idx + 1}. [${gap.severity.toUpperCase()}] ${gap.controlId} - ${gap.title}\n`;
            report += `   Description: ${gap.description}\n`;
            report += `   Remediation: ${gap.remediation}\n`;
            report += `   Estimated Effort: ${gap.estimatedEffort} days\n`;
            report += `\n`;
        });
        report += `=== RECOMMENDATIONS ===\n`;
        gapAnalysis.recommendations.forEach((rec, idx) => {
            report += `${idx + 1}. ${rec}\n`;
        });
        report += `\n`;
        report += `=== CONTROL DETAILS ===\n`;
        controls.forEach((control) => {
            const controlEvidence = evidence.filter((ev) => ev.controlId === control.id);
            report += `\n`;
            report += `Control ID: ${control.controlId}\n`;
            report += `Framework: ${control.framework.toUpperCase()}\n`;
            report += `Title: ${control.title}\n`;
            report += `Status: ${control.status.toUpperCase()}\n`;
            report += `Category: ${control.category || 'N/A'}\n`;
            report += `Evidence Count: ${controlEvidence.length}\n`;
            if (controlEvidence.length > 0) {
                report += `Evidence:\n`;
                controlEvidence.forEach((ev, idx) => {
                    report += `  ${idx + 1}. [${ev.type.toUpperCase()}] ${ev.title}\n`;
                    if (ev.url) report += `     URL: ${ev.url}\n`;
                });
            }
        });

        return report;
    }, [programName, controls, evidence, getComplianceOverview, performGapAnalysis, calculateRiskScore]);

    const exportToCSV = useCallback((): string => {
        let csv = 'Framework,Control ID,Title,Status,Category,Evidence Count\n';
        controls.forEach((control) => {
            const evidenceCount = evidence.filter((ev) => ev.controlId === control.id).length;
            csv += `"${control.framework}","${control.controlId}","${control.title}","${control.status}","${control.category || ''}",${evidenceCount}\n`;
        });
        return csv;
    }, [controls, evidence]);

    const exportToJSON = useCallback((): string => {
        const data = {
            programName,
            generatedAt: new Date().toISOString(),
            overview: getComplianceOverview(),
            gapAnalysis: performGapAnalysis(),
            riskScore: calculateRiskScore(),
            controls: controls.map((control) => ({
                ...control,
                evidence: evidence.filter((ev) => ev.controlId === control.id),
            })),
        };
        return JSON.stringify(data, null, 2);
    }, [programName, controls, evidence, getComplianceOverview, performGapAnalysis, calculateRiskScore]);

    return {
        // State
        programName,
        setProgramName,
        selectedFramework,
        setSelectedFramework,
        controls,
        evidence,

        // Control Management
        addControl,
        updateControl,
        deleteControl,
        getControl,
        getControlCount,
        getControlsByFramework,
        getControlsByStatus,

        // Evidence Management
        addEvidence,
        updateEvidence,
        deleteEvidence,
        getEvidenceForControl,

        // Analysis
        getComplianceOverview,
        performGapAnalysis,
        calculateRiskScore,
        validateEvidence,

        // Export
        exportToAuditReport,
        exportToCSV,
        exportToJSON,
    };
};
