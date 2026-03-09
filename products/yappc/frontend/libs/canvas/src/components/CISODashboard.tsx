/**
 * CISO Dashboard Component
 * 
 * @doc.type component
 * @doc.purpose Executive security dashboard for CISOs with KPIs, risk heatmap, CVE drill-down, incident timeline, and board reporting
 * @doc.layer product
 * @doc.pattern Presentation Component
 * 
 * Features:
 * - Security KPI cards (vulnerabilities, compliance score, incidents, risk level)
 * - Risk heatmap with system-level visibility (color-coded by risk)
 * - CVE drill-down with severity filtering (critical, high, medium, low)
 * - Incident timeline with status tracking
 * - Board report generation (executive summary, PDF/PPT export)
 * - Real-time security posture monitoring
 * - Trend analysis (week, month, quarter)
 * - Multi-level drill-down capabilities
 * 
 * @example
 * ```tsx
 * <CISODashboard organizationName="Acme Corp" />
 * ```
 */

import React, { useState } from 'react';
import {
  Button,
  Card,
  CardContent,
  CardHeader,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Typography,
} from '@ghatana/ui';
import { TextField } from '@ghatana/ui';
import { useCISODashboard } from '../hooks/useCISODashboard';
import type {
    Severity,
    RiskLevel,
    IncidentStatus,
    TrendPeriod,
    ExportFormat,
} from '../hooks/useCISODashboard';

interface CISODashboardProps {
    /**
     * Organization name displayed in the dashboard
     */
    organizationName?: string;
}

/**
 * CISO Dashboard Component
 * 
 * Executive security dashboard providing comprehensive security posture visibility,
 * risk management, vulnerability tracking, incident response, and board-level reporting.
 * 
 * @param props - Component props
 * @returns CISO Dashboard component
 */
export const CISODashboard: React.FC<CISODashboardProps> = ({
    organizationName = 'My Organization',
}) => {
    const {
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

        // CVE Management
        getCVEs,
        getCVEsBySeverity,
        getCVEsBySystem,
        getCVEDetails,

        // Incident Management
        getIncidents,
        getIncidentsBystatus,
        getIncidentTimeline,

        // Trend Analysis
        getSecurityTrends,
        getVulnerabilityTrends,
        getIncidentTrends,

        // Board Reporting
        generateExecutiveSummary,
        generateBoardReport,
        exportReport,
    } = useCISODashboard();

    // Local UI state
    const [showSystemDetails, setShowSystemDetails] = useState(false);
    const [showCVEDetails, setShowCVEDetails] = useState(false);
    const [showIncidentDetails, setShowIncidentDetails] = useState(false);
    const [showBoardReport, setShowBoardReport] = useState(false);
    const [showExportDialog, setShowExportDialog] = useState(false);
    const [selectedCVEId, setSelectedCVEId] = useState<string>('');
    const [selectedIncidentId, setSelectedIncidentId] = useState<string>('');
    const [exportFormat, setExportFormat] = useState<ExportFormat>('pdf');

    // Get data
    const kpis = getSecurityKPIs();
    const heatmap = getRiskHeatmap();
    const cves = selectedSeverity ? getCVEsBySeverity(selectedSeverity) : getCVEs();
    const incidents = selectedIncidentStatus ? getIncidentsBystatus(selectedIncidentStatus) : getIncidents();
    const trends = getSecurityTrends(selectedTrendPeriod);
    const cveDetails = selectedCVEId ? getCVEDetails(selectedCVEId) : null;
    const systemDetails = selectedSystem ? getSystemRiskDetails(selectedSystem) : null;

    // Risk level color mapping
    const getRiskColor = (risk: RiskLevel): string => {
        switch (risk) {
            case 'critical':
                return 'border-red-500 bg-red-50';
            case 'high':
                return 'border-orange-500 bg-orange-50';
            case 'medium':
                return 'border-yellow-500 bg-yellow-50';
            case 'low':
                return 'border-green-500 bg-green-50';
            default:
                return 'border-gray-500 bg-gray-50';
        }
    };

    const getRiskTextColor = (risk: RiskLevel): string => {
        switch (risk) {
            case 'critical':
                return 'text-red-700';
            case 'high':
                return 'text-orange-700';
            case 'medium':
                return 'text-yellow-700';
            case 'low':
                return 'text-green-700';
            default:
                return 'text-gray-700';
        }
    };

    // Severity color mapping
    const getSeverityColor = (severity: Severity): string => {
        switch (severity) {
            case 'critical':
                return 'bg-red-600 text-white';
            case 'high':
                return 'bg-orange-600 text-white';
            case 'medium':
                return 'bg-yellow-600 text-white';
            case 'low':
                return 'bg-blue-600 text-white';
            default:
                return 'bg-gray-600 text-white';
        }
    };

    // Incident status color mapping
    const getIncidentStatusColor = (status: IncidentStatus): string => {
        switch (status) {
            case 'critical':
                return 'bg-red-100 text-red-800 border-red-300';
            case 'active':
                return 'bg-orange-100 text-orange-800 border-orange-300';
            case 'investigating':
                return 'bg-yellow-100 text-yellow-800 border-yellow-300';
            case 'contained':
                return 'bg-blue-100 text-blue-800 border-blue-300';
            case 'resolved':
                return 'bg-green-100 text-green-800 border-green-300';
            default:
                return 'bg-gray-100 text-gray-800 border-gray-300';
        }
    };

    // Handle system click in heatmap
    const handleSystemClick = (systemName: string) => {
        setSelectedSystem(systemName);
        setShowSystemDetails(true);
    };

    // Handle CVE click
    const handleCVEClick = (cveId: string) => {
        setSelectedCVEId(cveId);
        setShowCVEDetails(true);
    };

    // Handle incident click
    const handleIncidentClick = (incidentId: string) => {
        setSelectedIncidentId(incidentId);
        setShowIncidentDetails(true);
    };

    // Handle export
    const handleExport = () => {
        const report = exportReport(exportFormat);
        // In a real implementation, this would trigger a file download
        console.log(`Exporting report in ${exportFormat} format:`, report);
        setShowExportDialog(false);
    };

    return (
        <div className="w-full h-full p-6 space-y-6 bg-gray-50 overflow-auto">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div>
                    <Typography variant="h4" className="font-bold text-gray-900">
                        CISO Security Dashboard
                    </Typography>
                    <TextField
                        value={organization}
                        onChange={(e) => setOrganization(e.target.value)}
                        placeholder="Organization name"
                        className="mt-2 text-lg"
                    />
                </div>
                <div className="flex gap-2">
                    <Button
                        onClick={() => setShowBoardReport(true)}
                        className="bg-blue-600 text-white hover:bg-blue-700"
                    >
                        Board Report
                    </Button>
                    <Button
                        onClick={() => setShowExportDialog(true)}
                        className="bg-green-600 text-white hover:bg-green-700"
                    >
                        Export
                    </Button>
                </div>
            </div>

            {/* Security KPIs */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                {/* Open Vulnerabilities */}
                <Card className={`border-l-4 ${getRiskColor(kpis.overallRisk)}`}>
                    <CardContent className="p-6">
                        <Typography variant="caption" className="text-gray-600 uppercase tracking-wide">
                            Open Vulnerabilities
                        </Typography>
                        <Typography variant="h3" className={`font-bold mt-2 ${getRiskTextColor(kpis.overallRisk)}`}>
                            {kpis.totalVulnerabilities}
                        </Typography>
                        <div className="mt-2 space-y-1">
                            <div className="flex justify-between text-sm">
                                <span className="text-red-600">Critical:</span>
                                <span className="font-semibold">{kpis.criticalVulnerabilities}</span>
                            </div>
                            <div className="flex justify-between text-sm">
                                <span className="text-orange-600">High:</span>
                                <span className="font-semibold">{kpis.highVulnerabilities}</span>
                            </div>
                        </div>
                    </CardContent>
                </Card>

                {/* Compliance Score */}
                <Card className="border-l-4 border-blue-500 bg-blue-50">
                    <CardContent className="p-6">
                        <Typography variant="caption" className="text-gray-600 uppercase tracking-wide">
                            Compliance Score
                        </Typography>
                        <Typography variant="h3" className="font-bold mt-2 text-blue-700">
                            {kpis.complianceScore}%
                        </Typography>
                        <div className="mt-2">
                            <div className="w-full bg-gray-200 rounded-full h-2">
                                <div
                                    className="bg-blue-600 h-2 rounded-full"
                                    style={{ width: `${kpis.complianceScore}%` }}
                                />
                            </div>
                        </div>
                    </CardContent>
                </Card>

                {/* Active Incidents */}
                <Card className="border-l-4 border-orange-500 bg-orange-50">
                    <CardContent className="p-6">
                        <Typography variant="caption" className="text-gray-600 uppercase tracking-wide">
                            Active Incidents
                        </Typography>
                        <Typography variant="h3" className="font-bold mt-2 text-orange-700">
                            {kpis.activeIncidents}
                        </Typography>
                        <div className="mt-2 space-y-1">
                            <div className="flex justify-between text-sm">
                                <span className="text-red-600">Critical:</span>
                                <span className="font-semibold">{kpis.criticalIncidents}</span>
                            </div>
                            <div className="flex justify-between text-sm">
                                <span className="text-gray-600">MTTR:</span>
                                <span className="font-semibold">{kpis.meanTimeToResolve}h</span>
                            </div>
                        </div>
                    </CardContent>
                </Card>

                {/* Risk Level */}
                <Card className={`border-l-4 ${getRiskColor(kpis.overallRisk)}`}>
                    <CardContent className="p-6">
                        <Typography variant="caption" className="text-gray-600 uppercase tracking-wide">
                            Overall Risk Level
                        </Typography>
                        <Typography variant="h3" className={`font-bold mt-2 uppercase ${getRiskTextColor(kpis.overallRisk)}`}>
                            {kpis.overallRisk}
                        </Typography>
                        <div className="mt-2">
                            <Typography variant="caption" className="text-gray-600">
                                Systems at Risk: {kpis.systemsAtRisk}
                            </Typography>
                        </div>
                    </CardContent>
                </Card>
            </div>

            {/* Trend Analysis */}
            <Card>
                <CardHeader>
                    <div className="flex items-center justify-between">
                        <Typography variant="h6" className="font-semibold">
                            Security Trends
                        </Typography>
                        <div className="flex gap-2">
                            {(['week', 'month', 'quarter'] as TrendPeriod[]).map((period) => (
                                <Button
                                    key={period}
                                    onClick={() => setSelectedTrendPeriod(period)}
                                    className={
                                        selectedTrendPeriod === period
                                            ? 'bg-blue-600 text-white'
                                            : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
                                    }
                                >
                                    {period.charAt(0).toUpperCase() + period.slice(1)}
                                </Button>
                            ))}
                        </div>
                    </div>
                </CardHeader>
                <CardContent>
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                        <div className="p-4 bg-gray-50 rounded">
                            <Typography variant="caption" className="text-gray-600 uppercase">
                                Vulnerability Trend
                            </Typography>
                            <Typography variant="h4" className={`font-bold mt-2 ${trends.vulnerabilityChange >= 0 ? 'text-red-600' : 'text-green-600'}`}>
                                {trends.vulnerabilityChange >= 0 ? '+' : ''}{trends.vulnerabilityChange}%
                            </Typography>
                            <Typography variant="caption" className="text-gray-500">
                                vs previous {selectedTrendPeriod}
                            </Typography>
                        </div>
                        <div className="p-4 bg-gray-50 rounded">
                            <Typography variant="caption" className="text-gray-600 uppercase">
                                Incident Trend
                            </Typography>
                            <Typography variant="h4" className={`font-bold mt-2 ${trends.incidentChange >= 0 ? 'text-red-600' : 'text-green-600'}`}>
                                {trends.incidentChange >= 0 ? '+' : ''}{trends.incidentChange}%
                            </Typography>
                            <Typography variant="caption" className="text-gray-500">
                                vs previous {selectedTrendPeriod}
                            </Typography>
                        </div>
                        <div className="p-4 bg-gray-50 rounded">
                            <Typography variant="caption" className="text-gray-600 uppercase">
                                Compliance Trend
                            </Typography>
                            <Typography variant="h4" className={`font-bold mt-2 ${trends.complianceChange >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                                {trends.complianceChange >= 0 ? '+' : ''}{trends.complianceChange}%
                            </Typography>
                            <Typography variant="caption" className="text-gray-500">
                                vs previous {selectedTrendPeriod}
                            </Typography>
                        </div>
                    </div>
                </CardContent>
            </Card>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                {/* Risk Heatmap */}
                <Card>
                    <CardHeader>
                        <Typography variant="h6" className="font-semibold">
                            System Risk Heatmap
                        </Typography>
                    </CardHeader>
                    <CardContent>
                        <div className="space-y-2">
                            {heatmap.map((system) => (
                                <div
                                    key={system.systemName}
                                    onClick={() => handleSystemClick(system.systemName)}
                                    className={`p-4 rounded border-l-4 cursor-pointer hover:shadow-md transition-shadow ${getRiskColor(system.riskLevel)}`}
                                >
                                    <div className="flex items-center justify-between">
                                        <div>
                                            <Typography variant="body1" className="font-semibold">
                                                {system.systemName}
                                            </Typography>
                                            <Typography variant="caption" className="text-gray-600">
                                                {system.vulnerabilityCount} vulnerabilities
                                            </Typography>
                                        </div>
                                        <div className="text-right">
                                            <Typography variant="caption" className={`uppercase font-bold ${getRiskTextColor(system.riskLevel)}`}>
                                                {system.riskLevel}
                                            </Typography>
                                            <Typography variant="caption" className="block text-gray-600">
                                                Score: {system.riskScore}
                                            </Typography>
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </CardContent>
                </Card>

                {/* CVE List */}
                <Card>
                    <CardHeader>
                        <div className="flex items-center justify-between">
                            <Typography variant="h6" className="font-semibold">
                                Critical Vulnerabilities (CVEs)
                            </Typography>
                            <div className="flex gap-2">
                                {(['critical', 'high', 'medium', 'low'] as Severity[]).map((severity) => (
                                    <Button
                                        key={severity}
                                        onClick={() => setSelectedSeverity(selectedSeverity === severity ? null : severity)}
                                        className={
                                            selectedSeverity === severity
                                                ? getSeverityColor(severity)
                                                : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
                                        }
                                    >
                                        {severity.charAt(0).toUpperCase() + severity.slice(1)}
                                    </Button>
                                ))}
                            </div>
                        </div>
                    </CardHeader>
                    <CardContent>
                        <div className="space-y-2 max-h-96 overflow-y-auto">
                            {cves.slice(0, 10).map((cve) => (
                                <div
                                    key={cve.id}
                                    onClick={() => handleCVEClick(cve.id)}
                                    className="p-3 bg-gray-50 rounded hover:bg-gray-100 cursor-pointer border border-gray-200"
                                >
                                    <div className="flex items-start justify-between">
                                        <div className="flex-1">
                                            <div className="flex items-center gap-2">
                                                <Typography variant="body2" className="font-semibold">
                                                    {cve.cveId}
                                                </Typography>
                                                <span className={`px-2 py-0.5 rounded text-xs font-semibold ${getSeverityColor(cve.severity)}`}>
                                                    {cve.severity}
                                                </span>
                                            </div>
                                            <Typography variant="caption" className="text-gray-600 line-clamp-2">
                                                {cve.description}
                                            </Typography>
                                            <Typography variant="caption" className="text-gray-500">
                                                CVSS: {cve.cvssScore} | Affected: {cve.affectedSystems.length} systems
                                            </Typography>
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </CardContent>
                </Card>
            </div>

            {/* Incident Timeline */}
            <Card>
                <CardHeader>
                    <div className="flex items-center justify-between">
                        <Typography variant="h6" className="font-semibold">
                            Security Incidents
                        </Typography>
                        <div className="flex gap-2">
                            {(['critical', 'active', 'investigating', 'contained', 'resolved'] as IncidentStatus[]).map((status) => (
                                <Button
                                    key={status}
                                    onClick={() => setSelectedIncidentStatus(selectedIncidentStatus === status ? null : status)}
                                    className={
                                        selectedIncidentStatus === status
                                            ? 'bg-blue-600 text-white'
                                            : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
                                    }
                                >
                                    {status.charAt(0).toUpperCase() + status.slice(1)}
                                </Button>
                            ))}
                        </div>
                    </div>
                </CardHeader>
                <CardContent>
                    <div className="space-y-3">
                        {incidents.slice(0, 8).map((incident) => (
                            <div
                                key={incident.id}
                                onClick={() => handleIncidentClick(incident.id)}
                                className="p-4 bg-gray-50 rounded hover:bg-gray-100 cursor-pointer border border-gray-200"
                            >
                                <div className="flex items-start justify-between">
                                    <div className="flex-1">
                                        <div className="flex items-center gap-2">
                                            <Typography variant="body1" className="font-semibold">
                                                {incident.title}
                                            </Typography>
                                            <span className={`px-2 py-1 rounded text-xs font-semibold border ${getIncidentStatusColor(incident.status)}`}>
                                                {incident.status}
                                            </span>
                                        </div>
                                        <Typography variant="caption" className="text-gray-600">
                                            {incident.description}
                                        </Typography>
                                        <div className="mt-2 flex items-center gap-4 text-xs text-gray-500">
                                            <span>Detected: {new Date(incident.detectedAt).toLocaleDateString()}</span>
                                            {incident.resolvedAt && (
                                                <span>Resolved: {new Date(incident.resolvedAt).toLocaleDateString()}</span>
                                            )}
                                            <span>Affected: {incident.affectedSystems.length} systems</span>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                </CardContent>
            </Card>

            {/* System Details Dialog */}
            <Dialog open={showSystemDetails} onClose={() => setShowSystemDetails(false)}>
                <DialogTitle>System Risk Details</DialogTitle>
                <DialogContent>
                    {systemDetails && (
                        <div className="space-y-4">
                            <div>
                                <Typography variant="h6" className="font-semibold">
                                    {systemDetails.systemName}
                                </Typography>
                                <Typography variant="caption" className="text-gray-600">
                                    {systemDetails.description}
                                </Typography>
                            </div>

                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <Typography variant="caption" className="text-gray-600 uppercase">
                                        Risk Level
                                    </Typography>
                                    <Typography variant="body1" className={`font-bold ${getRiskTextColor(systemDetails.riskLevel)}`}>
                                        {systemDetails.riskLevel.toUpperCase()}
                                    </Typography>
                                </div>
                                <div>
                                    <Typography variant="caption" className="text-gray-600 uppercase">
                                        Risk Score
                                    </Typography>
                                    <Typography variant="body1" className="font-bold">
                                        {systemDetails.riskScore}/100
                                    </Typography>
                                </div>
                            </div>

                            <div>
                                <Typography variant="body2" className="font-semibold mb-2">
                                    Vulnerabilities ({systemDetails.vulnerabilities.length})
                                </Typography>
                                <div className="space-y-2 max-h-64 overflow-y-auto">
                                    {systemDetails.vulnerabilities.map((vuln) => (
                                        <div key={vuln.cveId} className="p-2 bg-gray-50 rounded">
                                            <div className="flex items-center justify-between">
                                                <Typography variant="caption" className="font-semibold">
                                                    {vuln.cveId}
                                                </Typography>
                                                <span className={`px-2 py-0.5 rounded text-xs font-semibold ${getSeverityColor(vuln.severity)}`}>
                                                    {vuln.severity}
                                                </span>
                                            </div>
                                            <Typography variant="caption" className="text-gray-600">
                                                CVSS: {vuln.cvssScore}
                                            </Typography>
                                        </div>
                                    ))}
                                </div>
                            </div>

                            <div>
                                <Typography variant="body2" className="font-semibold mb-2">
                                    Recent Incidents ({systemDetails.incidents.length})
                                </Typography>
                                <div className="space-y-2">
                                    {systemDetails.incidents.map((inc) => (
                                        <div key={inc.id} className="p-2 bg-gray-50 rounded">
                                            <Typography variant="caption" className="font-semibold">
                                                {inc.title}
                                            </Typography>
                                            <Typography variant="caption" className="block text-gray-600">
                                                {inc.status} - {new Date(inc.detectedAt).toLocaleDateString()}
                                            </Typography>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        </div>
                    )}
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setShowSystemDetails(false)}>Close</Button>
                </DialogActions>
            </Dialog>

            {/* CVE Details Dialog */}
            <Dialog open={showCVEDetails} onClose={() => setShowCVEDetails(false)}>
                <DialogTitle>CVE Details</DialogTitle>
                <DialogContent>
                    {cveDetails && (
                        <div className="space-y-4">
                            <div>
                                <div className="flex items-center gap-2 mb-2">
                                    <Typography variant="h6" className="font-semibold">
                                        {cveDetails.cveId}
                                    </Typography>
                                    <span className={`px-2 py-1 rounded text-xs font-semibold ${getSeverityColor(cveDetails.severity)}`}>
                                        {cveDetails.severity}
                                    </span>
                                </div>
                                <Typography variant="body2" className="text-gray-700">
                                    {cveDetails.description}
                                </Typography>
                            </div>

                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <Typography variant="caption" className="text-gray-600 uppercase">
                                        CVSS Score
                                    </Typography>
                                    <Typography variant="h5" className="font-bold">
                                        {cveDetails.cvssScore}
                                    </Typography>
                                </div>
                                <div>
                                    <Typography variant="caption" className="text-gray-600 uppercase">
                                        Published
                                    </Typography>
                                    <Typography variant="body2">
                                        {new Date(cveDetails.publishedDate).toLocaleDateString()}
                                    </Typography>
                                </div>
                            </div>

                            <div>
                                <Typography variant="body2" className="font-semibold mb-2">
                                    Affected Systems ({cveDetails.affectedSystems.length})
                                </Typography>
                                <div className="space-y-1">
                                    {cveDetails.affectedSystems.map((system) => (
                                        <div key={system} className="px-3 py-2 bg-gray-50 rounded">
                                            <Typography variant="caption">{system}</Typography>
                                        </div>
                                    ))}
                                </div>
                            </div>

                            {cveDetails.mitigationSteps && cveDetails.mitigationSteps.length > 0 && (
                                <div>
                                    <Typography variant="body2" className="font-semibold mb-2">
                                        Mitigation Steps
                                    </Typography>
                                    <ol className="list-decimal list-inside space-y-1">
                                        {cveDetails.mitigationSteps.map((step, idx) => (
                                            <li key={idx} className="text-sm text-gray-700">
                                                {step}
                                            </li>
                                        ))}
                                    </ol>
                                </div>
                            )}

                            {cveDetails.references && cveDetails.references.length > 0 && (
                                <div>
                                    <Typography variant="body2" className="font-semibold mb-2">
                                        References
                                    </Typography>
                                    <div className="space-y-1">
                                        {cveDetails.references.map((ref, idx) => (
                                            <a
                                                key={idx}
                                                href={ref}
                                                target="_blank"
                                                rel="noopener noreferrer"
                                                className="block text-sm text-blue-600 hover:underline"
                                            >
                                                {ref}
                                            </a>
                                        ))}
                                    </div>
                                </div>
                            )}
                        </div>
                    )}
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setShowCVEDetails(false)}>Close</Button>
                </DialogActions>
            </Dialog>

            {/* Board Report Dialog */}
            <Dialog open={showBoardReport} onClose={() => setShowBoardReport(false)} maxWidth="lg">
                <DialogTitle>Executive Board Report</DialogTitle>
                <DialogContent>
                    <div className="space-y-6">
                        <div>
                            <Typography variant="h6" className="font-semibold mb-2">
                                Executive Summary
                            </Typography>
                            <Typography variant="body2" className="text-gray-700 whitespace-pre-line">
                                {generateExecutiveSummary()}
                            </Typography>
                        </div>

                        <div>
                            <Typography variant="h6" className="font-semibold mb-2">
                                Full Board Report
                            </Typography>
                            <div className="bg-gray-50 p-4 rounded max-h-96 overflow-y-auto">
                                <pre className="text-sm text-gray-700 whitespace-pre-wrap font-mono">
                                    {generateBoardReport()}
                                </pre>
                            </div>
                        </div>
                    </div>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setShowBoardReport(false)}>Close</Button>
                    <Button
                        onClick={() => setShowExportDialog(true)}
                        className="bg-blue-600 text-white hover:bg-blue-700"
                    >
                        Export Report
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Export Dialog */}
            <Dialog open={showExportDialog} onClose={() => setShowExportDialog(false)}>
                <DialogTitle>Export Report</DialogTitle>
                <DialogContent>
                    <div className="space-y-4">
                        <Typography variant="body2">
                            Select export format for the board report:
                        </Typography>
                        <div className="flex gap-2">
                            {(['pdf', 'pptx', 'json'] as ExportFormat[]).map((format) => (
                                <Button
                                    key={format}
                                    onClick={() => setExportFormat(format)}
                                    className={
                                        exportFormat === format
                                            ? 'bg-blue-600 text-white'
                                            : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
                                    }
                                >
                                    {format.toUpperCase()}
                                </Button>
                            ))}
                        </div>
                    </div>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setShowExportDialog(false)}>Cancel</Button>
                    <Button
                        onClick={handleExport}
                        className="bg-green-600 text-white hover:bg-green-700"
                    >
                        Export as {exportFormat.toUpperCase()}
                    </Button>
                </DialogActions>
            </Dialog>
        </div>
    );
};

export default CISODashboard;
