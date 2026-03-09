/**
 * Compliance Canvas Component
 * 
 * @doc.type component
 * @doc.purpose Compliance Officer tool for audit trails and compliance controls
 * @doc.layer product
 * @doc.pattern Specialized Canvas
 * 
 * Features:
 * - Multi-framework support (SOC2, HIPAA, GDPR, PCI-DSS, ISO 27001)
 * - Control mapping and gap analysis
 * - Evidence collection and documentation
 * - Audit report generation
 * - Compliance timeline and progress tracking
 * - Remediation tracking and assignment
 * - Risk scoring and prioritization
 */

import React, { useState } from 'react';
import { useCompliance, type ComplianceFramework, type ControlStatus } from '../hooks/useCompliance';
import { Button } from '@ghatana/ui';
import { Card, CardContent, CardHeader } from '@ghatana/ui';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
} from '@ghatana/ui';
import { TextField } from '@ghatana/ui';
import { Typography } from '@ghatana/ui';

export const ComplianceCanvas: React.FC = () => {
    const compliance = useCompliance();

    // Dialog states
    const [addControlDialogOpen, setAddControlDialogOpen] = useState(false);
    const [addEvidenceDialogOpen, setAddEvidenceDialogOpen] = useState(false);
    const [gapAnalysisDialogOpen, setGapAnalysisDialogOpen] = useState(false);
    const [exportDialogOpen, setExportDialogOpen] = useState(false);

    // Form states
    const [newControlForm, setNewControlForm] = useState({
        framework: 'soc2' as ComplianceFramework,
        controlId: '',
        title: '',
        description: '',
        category: '',
    });

    const [newEvidenceForm, setNewEvidenceForm] = useState({
        controlId: '',
        type: 'document' as 'document' | 'screenshot' | 'log' | 'attestation' | 'artifact',
        title: '',
        description: '',
        url: '',
    });

    const [exportFormat, setExportFormat] = useState<'pdf' | 'csv' | 'json'>('pdf');

    // Handlers
    const handleAddControl = () => {
        if (!newControlForm.controlId || !newControlForm.title) return;

        compliance.addControl({
            framework: newControlForm.framework,
            controlId: newControlForm.controlId,
            title: newControlForm.title,
            description: newControlForm.description,
            category: newControlForm.category,
            status: 'not-implemented',
        });

        setNewControlForm({
            framework: 'soc2',
            controlId: '',
            title: '',
            description: '',
            category: '',
        });
        setAddControlDialogOpen(false);
    };

    const handleAddEvidence = () => {
        if (!newEvidenceForm.controlId || !newEvidenceForm.title) return;

        compliance.addEvidence(newEvidenceForm.controlId, {
            type: newEvidenceForm.type,
            title: newEvidenceForm.title,
            description: newEvidenceForm.description,
            url: newEvidenceForm.url,
            uploadedAt: new Date().toISOString(),
        });

        setNewEvidenceForm({
            controlId: '',
            type: 'document',
            title: '',
            description: '',
            url: '',
        });
        setAddEvidenceDialogOpen(false);
    };

    const handleExport = () => {
        let result = '';
        switch (exportFormat) {
            case 'pdf':
                result = compliance.exportToAuditReport();
                break;
            case 'csv':
                result = compliance.exportToCSV();
                break;
            case 'json':
                result = compliance.exportToJSON();
                break;
        }

        // Download the result
        const blob = new Blob([result], { type: exportFormat === 'json' ? 'application/json' : 'text/plain' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `compliance-report-${Date.now()}.${exportFormat}`;
        a.click();
        URL.revokeObjectURL(url);
        setExportDialogOpen(false);
    };

    // Calculate metrics
    const overview = compliance.getComplianceOverview();
    const gapAnalysis = compliance.performGapAnalysis();
    const riskScore = compliance.calculateRiskScore();

    const statusColors: Record<ControlStatus, string> = {
        'not-implemented': 'bg-gray-200 text-gray-800',
        'in-progress': 'bg-blue-200 text-blue-800',
        implemented: 'bg-green-200 text-green-800',
        'needs-review': 'bg-yellow-200 text-yellow-800',
        failed: 'bg-red-200 text-red-800',
    };

    const frameworkColors: Record<ComplianceFramework, string> = {
        soc2: 'bg-blue-100 border-blue-400',
        hipaa: 'bg-purple-100 border-purple-400',
        gdpr: 'bg-green-100 border-green-400',
        'pci-dss': 'bg-orange-100 border-orange-400',
        'iso-27001': 'bg-indigo-100 border-indigo-400',
    };

    return (
        <div className="w-full h-full p-6 bg-gray-50 overflow-auto">
            {/* Header */}
            <div className="mb-6">
                <TextField
                    value={compliance.programName}
                    onChange={(e) => compliance.setProgramName(e.target.value)}
                    placeholder="Compliance Program Name"
                    className="text-2xl font-bold mb-2"
                />
                <Typography variant="body2" className="text-gray-600">
                    Track compliance controls, collect evidence, and generate audit reports
                </Typography>
            </div>

            {/* Framework Selector */}
            <Card className="mb-6">
                <CardHeader>
                    <Typography variant="h6">Compliance Frameworks</Typography>
                </CardHeader>
                <CardContent>
                    <div className="flex gap-2 flex-wrap">
                        {(['soc2', 'hipaa', 'gdpr', 'pci-dss', 'iso-27001'] as ComplianceFramework[]).map((framework) => (
                            <Button
                                key={framework}
                                variant={compliance.selectedFramework === framework ? 'contained' : 'outlined'}
                                onClick={() => compliance.setSelectedFramework(framework)}
                                className={`${compliance.selectedFramework === framework ? 'bg-blue-600 text-white' : 'border-gray-300'}`}
                            >
                                {framework.toUpperCase()}
                            </Button>
                        ))}
                    </div>
                </CardContent>
            </Card>

            {/* Metrics Dashboard */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
                <Card>
                    <CardContent className="p-4">
                        <Typography variant="body2" className="text-gray-600 mb-1">
                            Total Controls
                        </Typography>
                        <Typography variant="h4" className="font-bold">
                            {compliance.getControlCount()}
                        </Typography>
                    </CardContent>
                </Card>

                <Card>
                    <CardContent className="p-4">
                        <Typography variant="body2" className="text-gray-600 mb-1">
                            Compliance Score
                        </Typography>
                        <Typography variant="h4" className="font-bold text-green-600">
                            {overview.complianceScore}%
                        </Typography>
                    </CardContent>
                </Card>

                <Card>
                    <CardContent className="p-4">
                        <Typography variant="body2" className="text-gray-600 mb-1">
                            Risk Score
                        </Typography>
                        <Typography
                            variant="h4"
                            className={`font-bold ${riskScore.overallRisk === 'low'
                                    ? 'text-green-600'
                                    : riskScore.overallRisk === 'medium'
                                        ? 'text-yellow-600'
                                        : riskScore.overallRisk === 'high'
                                            ? 'text-orange-600'
                                            : 'text-red-600'
                                }`}
                        >
                            {riskScore.overallRisk.toUpperCase()}
                        </Typography>
                        <Typography variant="caption" className="text-gray-500">
                            Score: {riskScore.score.toFixed(1)}/100
                        </Typography>
                    </CardContent>
                </Card>

                <Card>
                    <CardContent className="p-4">
                        <Typography variant="body2" className="text-gray-600 mb-1">
                            Evidence Items
                        </Typography>
                        <Typography variant="h4" className="font-bold">
                            {overview.totalEvidence}
                        </Typography>
                    </CardContent>
                </Card>
            </div>

            {/* Status Breakdown */}
            <Card className="mb-6">
                <CardHeader>
                    <Typography variant="h6">Control Status Breakdown</Typography>
                </CardHeader>
                <CardContent>
                    <div className="space-y-3">
                        {Object.entries(overview.byStatus).map(([status, count]) => {
                            const percentage = compliance.getControlCount() > 0 ? (count / compliance.getControlCount()) * 100 : 0;
                            return (
                                <div key={status}>
                                    <div className="flex justify-between mb-1">
                                        <Typography variant="body2" className="capitalize">
                                            {status.replace('-', ' ')}
                                        </Typography>
                                        <Typography variant="body2" className="font-semibold">
                                            {count} ({percentage.toFixed(0)}%)
                                        </Typography>
                                    </div>
                                    <div className="w-full bg-gray-200 rounded-full h-2">
                                        <div
                                            className={`h-2 rounded-full ${status === 'implemented'
                                                    ? 'bg-green-500'
                                                    : status === 'in-progress'
                                                        ? 'bg-blue-500'
                                                        : status === 'needs-review'
                                                            ? 'bg-yellow-500'
                                                            : status === 'failed'
                                                                ? 'bg-red-500'
                                                                : 'bg-gray-400'
                                                }`}
                                            style={{ width: `${percentage}%` }}
                                        />
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                </CardContent>
            </Card>

            {/* Controls List */}
            <Card className="mb-6">
                <CardHeader>
                    <div className="flex justify-between items-center">
                        <Typography variant="h6">Controls</Typography>
                        <Button variant="contained" onClick={() => setAddControlDialogOpen(true)} className="bg-blue-600 text-white">
                            Add Control
                        </Button>
                    </div>
                </CardHeader>
                <CardContent>
                    {compliance.controls.length === 0 ? (
                        <div className="text-center py-8 text-gray-500">
                            <Typography variant="body1">No controls added yet. Click "Add Control" to get started.</Typography>
                        </div>
                    ) : (
                        <div className="space-y-3">
                            {compliance
                                .getControlsByFramework(compliance.selectedFramework)
                                .sort((a, b) => a.controlId.localeCompare(b.controlId))
                                .map((control) => {
                                    const evidence = compliance.getEvidenceForControl(control.id);
                                    return (
                                        <Card key={control.id} className={`border-l-4 ${frameworkColors[control.framework]}`}>
                                            <CardContent className="p-4">
                                                <div className="flex justify-between items-start mb-2">
                                                    <div className="flex-1">
                                                        <div className="flex items-center gap-2 mb-1">
                                                            <Typography variant="body1" className="font-semibold">
                                                                {control.controlId}
                                                            </Typography>
                                                            <span className={`px-2 py-1 rounded text-xs font-medium ${statusColors[control.status]}`}>
                                                                {control.status.replace('-', ' ')}
                                                            </span>
                                                        </div>
                                                        <Typography variant="h6" className="mb-1">
                                                            {control.title}
                                                        </Typography>
                                                        {control.description && (
                                                            <Typography variant="body2" className="text-gray-600 mb-2">
                                                                {control.description}
                                                            </Typography>
                                                        )}
                                                        {control.category && (
                                                            <Typography variant="caption" className="text-gray-500">
                                                                Category: {control.category}
                                                            </Typography>
                                                        )}
                                                    </div>
                                                    <div className="flex gap-2">
                                                        <Button
                                                            size="small"
                                                            variant="outlined"
                                                            onClick={() => {
                                                                setNewEvidenceForm({ ...newEvidenceForm, controlId: control.id });
                                                                setAddEvidenceDialogOpen(true);
                                                            }}
                                                            className="border-gray-300"
                                                        >
                                                            Add Evidence
                                                        </Button>
                                                        <select
                                                            value={control.status}
                                                            onChange={(e) =>
                                                                compliance.updateControl(control.id, { status: e.target.value as ControlStatus })
                                                            }
                                                            className="px-2 py-1 border border-gray-300 rounded text-sm"
                                                        >
                                                            <option value="not-implemented">Not Implemented</option>
                                                            <option value="in-progress">In Progress</option>
                                                            <option value="implemented">Implemented</option>
                                                            <option value="needs-review">Needs Review</option>
                                                            <option value="failed">Failed</option>
                                                        </select>
                                                    </div>
                                                </div>

                                                {/* Evidence List */}
                                                {evidence.length > 0 && (
                                                    <div className="mt-3 pt-3 border-t border-gray-200">
                                                        <Typography variant="body2" className="font-semibold mb-2">
                                                            Evidence ({evidence.length})
                                                        </Typography>
                                                        <div className="space-y-2">
                                                            {evidence.map((ev) => (
                                                                <div key={ev.id} className="flex items-start gap-2 text-sm">
                                                                    <span className="px-2 py-0.5 bg-gray-100 rounded text-xs capitalize">{ev.type}</span>
                                                                    <div className="flex-1">
                                                                        <Typography variant="body2" className="font-medium">
                                                                            {ev.title}
                                                                        </Typography>
                                                                        {ev.description && (
                                                                            <Typography variant="caption" className="text-gray-600">
                                                                                {ev.description}
                                                                            </Typography>
                                                                        )}
                                                                        {ev.url && (
                                                                            <a
                                                                                href={ev.url}
                                                                                target="_blank"
                                                                                rel="noopener noreferrer"
                                                                                className="text-blue-600 hover:underline text-xs"
                                                                            >
                                                                                View Evidence
                                                                            </a>
                                                                        )}
                                                                    </div>
                                                                    <Button
                                                                        size="small"
                                                                        variant="text"
                                                                        onClick={() => compliance.deleteEvidence(ev.id)}
                                                                        className="text-red-600 hover:bg-red-50"
                                                                    >
                                                                        Delete
                                                                    </Button>
                                                                </div>
                                                            ))}
                                                        </div>
                                                    </div>
                                                )}
                                            </CardContent>
                                        </Card>
                                    );
                                })}
                        </div>
                    )}
                </CardContent>
            </Card>

            {/* Gap Analysis */}
            {gapAnalysis.gaps.length > 0 && (
                <Card className="mb-6">
                    <CardHeader>
                        <div className="flex justify-between items-center">
                            <Typography variant="h6">Gap Analysis</Typography>
                            <Button variant="outlined" onClick={() => setGapAnalysisDialogOpen(true)} className="border-gray-300">
                                View Details
                            </Button>
                        </div>
                    </CardHeader>
                    <CardContent>
                        <Typography variant="body2" className="mb-3 text-gray-600">
                            {gapAnalysis.gaps.length} gaps identified requiring attention
                        </Typography>
                        <div className="space-y-2">
                            {gapAnalysis.gaps.slice(0, 5).map((gap) => (
                                <div
                                    key={gap.controlId}
                                    className={`p-3 rounded border-l-4 ${gap.severity === 'critical'
                                            ? 'bg-red-50 border-red-500'
                                            : gap.severity === 'high'
                                                ? 'bg-orange-50 border-orange-500'
                                                : gap.severity === 'medium'
                                                    ? 'bg-yellow-50 border-yellow-500'
                                                    : 'bg-blue-50 border-blue-500'
                                        }`}
                                >
                                    <div className="flex justify-between items-start">
                                        <div className="flex-1">
                                            <Typography variant="body2" className="font-semibold mb-1">
                                                {gap.controlId} - {gap.title}
                                            </Typography>
                                            <Typography variant="caption" className="text-gray-600">
                                                {gap.description}
                                            </Typography>
                                        </div>
                                        <span
                                            className={`px-2 py-1 rounded text-xs font-medium ${gap.severity === 'critical'
                                                    ? 'bg-red-200 text-red-800'
                                                    : gap.severity === 'high'
                                                        ? 'bg-orange-200 text-orange-800'
                                                        : gap.severity === 'medium'
                                                            ? 'bg-yellow-200 text-yellow-800'
                                                            : 'bg-blue-200 text-blue-800'
                                                }`}
                                        >
                                            {gap.severity}
                                        </span>
                                    </div>
                                </div>
                            ))}
                            {gapAnalysis.gaps.length > 5 && (
                                <Button variant="text" onClick={() => setGapAnalysisDialogOpen(true)} className="text-blue-600">
                                    View all {gapAnalysis.gaps.length} gaps
                                </Button>
                            )}
                        </div>
                    </CardContent>
                </Card>
            )}

            {/* Recommendations */}
            {gapAnalysis.recommendations.length > 0 && (
                <Card className="mb-6">
                    <CardHeader>
                        <Typography variant="h6">Recommendations</Typography>
                    </CardHeader>
                    <CardContent>
                        <div className="space-y-2">
                            {gapAnalysis.recommendations.map((rec, idx) => (
                                <div key={idx} className="flex items-start gap-2">
                                    <span className="text-blue-600 mt-1">•</span>
                                    <Typography variant="body2" className="flex-1">
                                        {rec}
                                    </Typography>
                                </div>
                            ))}
                        </div>
                    </CardContent>
                </Card>
            )}

            {/* Action Buttons */}
            <div className="flex gap-3 justify-end">
                <Button variant="outlined" onClick={() => setGapAnalysisDialogOpen(true)} className="border-gray-300">
                    Gap Analysis
                </Button>
                <Button variant="outlined" onClick={() => setExportDialogOpen(true)} className="border-gray-300">
                    Export Report
                </Button>
            </div>

            {/* Add Control Dialog */}
            <Dialog open={addControlDialogOpen} onClose={() => setAddControlDialogOpen(false)}>
                <DialogTitle>Add Compliance Control</DialogTitle>
                <DialogContent>
                    <div className="space-y-4 w-96">
                        <div>
                            <label className="block text-sm font-medium mb-1">Framework</label>
                            <select
                                value={newControlForm.framework}
                                onChange={(e) => setNewControlForm({ ...newControlForm, framework: e.target.value as ComplianceFramework })}
                                className="w-full px-3 py-2 border border-gray-300 rounded"
                            >
                                <option value="soc2">SOC 2</option>
                                <option value="hipaa">HIPAA</option>
                                <option value="gdpr">GDPR</option>
                                <option value="pci-dss">PCI-DSS</option>
                                <option value="iso-27001">ISO 27001</option>
                            </select>
                        </div>

                        <TextField
                            label="Control ID"
                            value={newControlForm.controlId}
                            onChange={(e) => setNewControlForm({ ...newControlForm, controlId: e.target.value })}
                            placeholder="e.g., CC1.1, 164.308(a)(1)(i)"
                            required
                            fullWidth
                        />

                        <TextField
                            label="Title"
                            value={newControlForm.title}
                            onChange={(e) => setNewControlForm({ ...newControlForm, title: e.target.value })}
                            placeholder="Control title"
                            required
                            fullWidth
                        />

                        <TextField
                            label="Description"
                            value={newControlForm.description}
                            onChange={(e) => setNewControlForm({ ...newControlForm, description: e.target.value })}
                            placeholder="Control description"
                            multiline
                            rows={3}
                            fullWidth
                        />

                        <TextField
                            label="Category"
                            value={newControlForm.category}
                            onChange={(e) => setNewControlForm({ ...newControlForm, category: e.target.value })}
                            placeholder="e.g., Access Control, Data Protection"
                            fullWidth
                        />
                    </div>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setAddControlDialogOpen(false)} variant="text">
                        Cancel
                    </Button>
                    <Button
                        onClick={handleAddControl}
                        variant="contained"
                        disabled={!newControlForm.controlId || !newControlForm.title}
                        className="bg-blue-600 text-white"
                    >
                        Add Control
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Add Evidence Dialog */}
            <Dialog open={addEvidenceDialogOpen} onClose={() => setAddEvidenceDialogOpen(false)}>
                <DialogTitle>Add Evidence</DialogTitle>
                <DialogContent>
                    <div className="space-y-4 w-96">
                        <div>
                            <label className="block text-sm font-medium mb-1">Evidence Type</label>
                            <select
                                value={newEvidenceForm.type}
                                onChange={(e) =>
                                    setNewEvidenceForm({
                                        ...newEvidenceForm,
                                        type: e.target.value as 'document' | 'screenshot' | 'log' | 'attestation' | 'artifact',
                                    })
                                }
                                className="w-full px-3 py-2 border border-gray-300 rounded"
                            >
                                <option value="document">Document</option>
                                <option value="screenshot">Screenshot</option>
                                <option value="log">Log</option>
                                <option value="attestation">Attestation</option>
                                <option value="artifact">Artifact</option>
                            </select>
                        </div>

                        <TextField
                            label="Title"
                            value={newEvidenceForm.title}
                            onChange={(e) => setNewEvidenceForm({ ...newEvidenceForm, title: e.target.value })}
                            placeholder="Evidence title"
                            required
                            fullWidth
                        />

                        <TextField
                            label="Description"
                            value={newEvidenceForm.description}
                            onChange={(e) => setNewEvidenceForm({ ...newEvidenceForm, description: e.target.value })}
                            placeholder="Evidence description"
                            multiline
                            rows={2}
                            fullWidth
                        />

                        <TextField
                            label="URL/File Path"
                            value={newEvidenceForm.url}
                            onChange={(e) => setNewEvidenceForm({ ...newEvidenceForm, url: e.target.value })}
                            placeholder="https://... or /path/to/file"
                            fullWidth
                        />
                    </div>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setAddEvidenceDialogOpen(false)} variant="text">
                        Cancel
                    </Button>
                    <Button
                        onClick={handleAddEvidence}
                        variant="contained"
                        disabled={!newEvidenceForm.controlId || !newEvidenceForm.title}
                        className="bg-blue-600 text-white"
                    >
                        Add Evidence
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Gap Analysis Dialog */}
            <Dialog open={gapAnalysisDialogOpen} onClose={() => setGapAnalysisDialogOpen(false)} maxWidth="md">
                <DialogTitle>Gap Analysis Report</DialogTitle>
                <DialogContent>
                    <div className="space-y-4 w-full max-w-2xl">
                        <div className="grid grid-cols-2 gap-4 mb-4">
                            <div className="p-4 bg-gray-50 rounded">
                                <Typography variant="body2" className="text-gray-600 mb-1">
                                    Total Gaps
                                </Typography>
                                <Typography variant="h4" className="font-bold">
                                    {gapAnalysis.gaps.length}
                                </Typography>
                            </div>
                            <div className="p-4 bg-gray-50 rounded">
                                <Typography variant="body2" className="text-gray-600 mb-1">
                                    Estimated Days to Close
                                </Typography>
                                <Typography variant="h4" className="font-bold">
                                    {gapAnalysis.estimatedDaysToClose}
                                </Typography>
                            </div>
                        </div>

                        <div className="space-y-3">
                            {gapAnalysis.gaps.map((gap) => (
                                <Card
                                    key={gap.controlId}
                                    className={`border-l-4 ${gap.severity === 'critical'
                                            ? 'border-red-500'
                                            : gap.severity === 'high'
                                                ? 'border-orange-500'
                                                : gap.severity === 'medium'
                                                    ? 'border-yellow-500'
                                                    : 'border-blue-500'
                                        }`}
                                >
                                    <CardContent className="p-4">
                                        <div className="flex justify-between items-start mb-2">
                                            <Typography variant="body1" className="font-semibold">
                                                {gap.controlId} - {gap.title}
                                            </Typography>
                                            <span
                                                className={`px-2 py-1 rounded text-xs font-medium ${gap.severity === 'critical'
                                                        ? 'bg-red-200 text-red-800'
                                                        : gap.severity === 'high'
                                                            ? 'bg-orange-200 text-orange-800'
                                                            : gap.severity === 'medium'
                                                                ? 'bg-yellow-200 text-yellow-800'
                                                                : 'bg-blue-200 text-blue-800'
                                                    }`}
                                            >
                                                {gap.severity}
                                            </span>
                                        </div>
                                        <Typography variant="body2" className="text-gray-600 mb-2">
                                            {gap.description}
                                        </Typography>
                                        <Typography variant="caption" className="text-gray-500">
                                            Remediation: {gap.remediation}
                                        </Typography>
                                    </CardContent>
                                </Card>
                            ))}
                        </div>

                        {gapAnalysis.recommendations.length > 0 && (
                            <div className="mt-4">
                                <Typography variant="h6" className="mb-2">
                                    Recommendations
                                </Typography>
                                <div className="space-y-2">
                                    {gapAnalysis.recommendations.map((rec, idx) => (
                                        <div key={idx} className="flex items-start gap-2 p-3 bg-blue-50 rounded">
                                            <span className="text-blue-600 mt-1">💡</span>
                                            <Typography variant="body2">{rec}</Typography>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}
                    </div>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setGapAnalysisDialogOpen(false)} variant="text">
                        Close
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Export Dialog */}
            <Dialog open={exportDialogOpen} onClose={() => setExportDialogOpen(false)}>
                <DialogTitle>Export Compliance Report</DialogTitle>
                <DialogContent>
                    <div className="space-y-4 w-96">
                        <Typography variant="body2" className="text-gray-600">
                            Choose the format for your compliance report
                        </Typography>

                        <div className="space-y-2">
                            <label className="flex items-center gap-2 p-3 border rounded cursor-pointer hover:bg-gray-50">
                                <input
                                    type="radio"
                                    value="pdf"
                                    checked={exportFormat === 'pdf'}
                                    onChange={(e) => setExportFormat(e.target.value as 'pdf')}
                                    className="w-4 h-4"
                                />
                                <div>
                                    <Typography variant="body2" className="font-medium">
                                        PDF Report
                                    </Typography>
                                    <Typography variant="caption" className="text-gray-600">
                                        Comprehensive audit report with controls, evidence, and analysis
                                    </Typography>
                                </div>
                            </label>

                            <label className="flex items-center gap-2 p-3 border rounded cursor-pointer hover:bg-gray-50">
                                <input
                                    type="radio"
                                    value="csv"
                                    checked={exportFormat === 'csv'}
                                    onChange={(e) => setExportFormat(e.target.value as 'csv')}
                                    className="w-4 h-4"
                                />
                                <div>
                                    <Typography variant="body2" className="font-medium">
                                        CSV Export
                                    </Typography>
                                    <Typography variant="caption" className="text-gray-600">
                                        Spreadsheet format for data analysis and tracking
                                    </Typography>
                                </div>
                            </label>

                            <label className="flex items-center gap-2 p-3 border rounded cursor-pointer hover:bg-gray-50">
                                <input
                                    type="radio"
                                    value="json"
                                    checked={exportFormat === 'json'}
                                    onChange={(e) => setExportFormat(e.target.value as 'json')}
                                    className="w-4 h-4"
                                />
                                <div>
                                    <Typography variant="body2" className="font-medium">
                                        JSON Data
                                    </Typography>
                                    <Typography variant="caption" className="text-gray-600">
                                        Machine-readable format for integrations and APIs
                                    </Typography>
                                </div>
                            </label>
                        </div>
                    </div>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setExportDialogOpen(false)} variant="text">
                        Cancel
                    </Button>
                    <Button onClick={handleExport} variant="contained" className="bg-blue-600 text-white">
                        Export
                    </Button>
                </DialogActions>
            </Dialog>
        </div>
    );
};
