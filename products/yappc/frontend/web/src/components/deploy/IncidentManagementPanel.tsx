// @ts-nocheck
/**
 * Incident Management Panel
 *
 * Incident tracking, root cause analysis, and postmortem management for OBSERVE phase.
 * Incident data is persisted as lifecycle artifacts of kind `incident_report`.
 *
 * @doc.type component
 * @doc.purpose OBSERVE phase incident management
 * @doc.layer product
 * @doc.pattern Panel Component
 */

import React, { useCallback, useEffect, useState } from 'react';
import { Button } from '../ui/Button';
import {
    AlertCircle as ErrorIcon,
    CheckCircle,
    Clock as Schedule,
    User as Person,
    Plus as Add,
    X as Close,
} from 'lucide-react';
import { useLifecycleArtifacts } from '../../services/canvas/lifecycle/LifecycleArtifactService';
import type { ArtifactSummary, LifecycleArtifact } from '../../services/canvas/lifecycle/LifecycleArtifactService';
import { LifecycleArtifactKind } from '@/shared/types/lifecycle-artifacts';
import type { IncidentReportPayload } from '@/shared/types/lifecycle-artifacts';
import { useCurrentUser } from '../../providers/AuthProvider';

export interface IncidentManagementPanelProps {
    projectId: string;
}

/** Operational status of an incident — stored in the artifact payload. */
type IncidentStatus = 'open' | 'investigating' | 'mitigated' | 'resolved';

/** Severity of an incident — stored in the artifact payload. */
type IncidentSeverity = 'critical' | 'high' | 'medium' | 'low';

/**
 * Extended payload shape for incident_report artifacts.
 * Extends `IncidentReportPayload` with incident-specific operational fields.
 */
interface IncidentArtifactPayload extends IncidentReportPayload {
    incidentStatus: IncidentStatus;
    severity: IncidentSeverity;
    detectedAt: string;
    resolvedAt?: string;
    assignedTo?: string;
}

/** Form state for creating a new incident. */
interface NewIncidentForm {
    title: string;
    severity: IncidentSeverity;
    impact: string;
}

const NativeInput = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>((props, ref) =>
    React.createElement('input', { ...props, ref }),
);
NativeInput.displayName = 'NativeInput';

const NativeSelect = React.forwardRef<HTMLSelectElement, React.SelectHTMLAttributes<HTMLSelectElement>>((props, ref) =>
    React.createElement('select', { ...props, ref }),
);
NativeSelect.displayName = 'NativeSelect';

const NativeTextarea = React.forwardRef<HTMLTextAreaElement, React.TextareaHTMLAttributes<HTMLTextAreaElement>>((props, ref) =>
    React.createElement('textarea', { ...props, ref }),
);
NativeTextarea.displayName = 'NativeTextarea';

const INITIAL_FORM: NewIncidentForm = {
    title: '',
    severity: 'medium',
    impact: '',
};

function getSeverityColor(severity: IncidentSeverity): string {
    switch (severity) {
        case 'critical':
            return 'bg-error-color text-white';
        case 'high':
            return 'bg-warning-color text-white';
        case 'medium':
            return 'bg-info-bg text-white';
        case 'low':
            return 'bg-grey-400 text-white';
    }
}

function getStatusColor(status: IncidentStatus): string {
    switch (status) {
        case 'resolved':
            return 'text-success-color';
        case 'mitigated':
            return 'text-info-color';
        case 'investigating':
            return 'text-warning-color';
        case 'open':
            return 'text-error-color';
    }
}

function getStatusIcon(status: IncidentStatus): React.ReactNode {
    switch (status) {
        case 'resolved':
            return <CheckCircle className="w-5 h-5" />;
        case 'investigating':
            return <Schedule className="w-5 h-5" />;
        default:
            return <ErrorIcon className="w-5 h-5" />;
    }
}

function formatTimestamp(timestamp: string): string {
    return new Date(timestamp).toLocaleString();
}

function calculateDuration(start: string, end?: string): string {
    const startTime = new Date(start).getTime();
    const endTime = end ? new Date(end).getTime() : Date.now();
    const durationMs = endTime - startTime;
    const hours = Math.floor(durationMs / (1000 * 60 * 60));
    const minutes = Math.floor((durationMs % (1000 * 60 * 60)) / (1000 * 60));
    return hours > 0 ? `${hours}h ${minutes}m` : `${minutes}m`;
}

function getIncidentPayload(artifact: LifecycleArtifact): IncidentArtifactPayload {
    const p = artifact.payload as Partial<IncidentArtifactPayload>;
    return {
        incidentStatus: p.incidentStatus ?? 'open',
        severity: p.severity ?? 'medium',
        detectedAt: p.detectedAt ?? artifact.createdAt,
        resolvedAt: p.resolvedAt,
        assignedTo: p.assignedTo,
        timeline: Array.isArray(p.timeline) ? p.timeline : [],
        rootCause: p.rootCause ?? '',
        impact: p.impact ?? '',
        mitigations: Array.isArray(p.mitigations) ? p.mitigations : [],
        postMortemUrl: p.postMortemUrl ?? '',
    };
}

export const IncidentManagementPanel: React.FC<IncidentManagementPanelProps> = ({ projectId }) => {
    const { artifacts, createArtifact, updateArtifact, service } = useLifecycleArtifacts(projectId);
    const currentUser = useCurrentUser();

    const [selectedSummary, setSelectedSummary] = useState<ArtifactSummary | null>(null);
    const [selectedArtifact, setSelectedArtifact] = useState<LifecycleArtifact | null>(null);
    const [showNewIncidentDialog, setShowNewIncidentDialog] = useState(false);
    const [newIncidentForm, setNewIncidentForm] = useState<NewIncidentForm>(INITIAL_FORM);
    const [isCreating, setIsCreating] = useState(false);
    const [isLoadingDetail, setIsLoadingDetail] = useState(false);

    // Filter to incident_report artifacts only
    const incidentSummaries = artifacts.filter(
        (a) => a.kind === LifecycleArtifactKind.INCIDENT_REPORT,
    );

    // Load full artifact when a summary is selected
    const handleSelectIncident = useCallback(
        async (summary: ArtifactSummary) => {
            setSelectedSummary(summary);
            setIsLoadingDetail(true);
            try {
                const full = await service.getArtifact(summary.id);
                setSelectedArtifact(full);
            } finally {
                setIsLoadingDetail(false);
            }
        },
        [service],
    );

    // Create a new incident_report artifact from the form
    const handleCreateIncident = useCallback(async () => {
        if (!newIncidentForm.title.trim()) return;
        setIsCreating(true);
        try {
            const now = new Date().toISOString();
            const payload: IncidentArtifactPayload = {
                incidentStatus: 'open',
                severity: newIncidentForm.severity,
                detectedAt: now,
                timeline: [
                    {
                        timestamp: now,
                        event: 'Incident created',
                        user: currentUser.name,
                    },
                ],
                rootCause: '',
                impact: newIncidentForm.impact,
                mitigations: [],
                postMortemUrl: '',
            };
            await createArtifact(LifecycleArtifactKind.INCIDENT_REPORT, currentUser.id);
            // createArtifact returns the new artifact; update it with full payload and title
            const created = await service.getArtifactByKind(projectId, LifecycleArtifactKind.INCIDENT_REPORT);
            if (created) {
                await updateArtifact(created.id, { title: newIncidentForm.title, payload }, currentUser.id);
            }
            setNewIncidentForm(INITIAL_FORM);
            setShowNewIncidentDialog(false);
        } finally {
            setIsCreating(false);
        }
    }, [createArtifact, currentUser, newIncidentForm, projectId, service, updateArtifact]);

    // Reload detail when artifact list updates (e.g., after creation)
    useEffect(() => {
        if (selectedSummary) {
            void (async () => {
                try {
                    const full = await service.getArtifact(selectedSummary.id);
                    if (full) {
                        setSelectedArtifact(full);
                    }
                } catch (error: unknown) {
                    console.warn('Failed to reload incident artifact details', error);
                }
            })();
        }
    }, [artifacts, selectedSummary, service]);

    const selectedPayload = selectedArtifact ? getIncidentPayload(selectedArtifact) : null;

    return (
        <div className="flex flex-col h-full bg-bg-default">
            {/* Header */}
            <div className="flex items-center justify-between px-4 py-3 border-b border-divider bg-bg-paper">
                <div>
                    <h2 className="text-lg font-semibold text-text-primary">Incident Management</h2>
                    <p className="text-sm text-text-secondary">OBSERVE Phase – Issue Tracking</p>
                </div>
                <Button
                    onClick={() => setShowNewIncidentDialog(true)}
                    className="flex items-center gap-2 px-4 py-2 bg-primary-600 text-white rounded-md hover:bg-primary-700 transition-colors"
                >
                    <Add className="w-4 h-4" />
                    New Incident
                </Button>
            </div>

            {/* Content */}
            <div className="flex flex-1 overflow-hidden">
                {/* Incident List */}
                <div className="w-1/3 border-r border-divider overflow-auto">
                    <div className="p-4 space-y-3">
                        {incidentSummaries.length === 0 && (
                            <p className="text-sm text-text-secondary text-center py-8">
                                No incidents recorded yet
                            </p>
                        )}
                        {incidentSummaries.map((summary) => {
                            const isSelected = selectedSummary?.id === summary.id;
                            return (
                                <Button
                                    key={summary.id}
                                    type="button"
                                    onClick={() => void handleSelectIncident(summary)}
                                    className={`w-full text-left p-3 rounded-lg border cursor-pointer transition-colors ${isSelected
                                            ? 'border-primary-600 bg-primary-50'
                                            : 'border-divider bg-bg-paper hover:border-primary-200'
                                        }`}
                                >
                                    <div className="flex items-start justify-between mb-2">
                                        <div className="flex-1 min-w-0">
                                            <h4 className="text-sm font-medium text-text-primary truncate">
                                                {summary.title}
                                            </h4>
                                            <p className="text-xs text-text-secondary mt-1">
                                                {formatTimestamp(summary.updatedAt)}
                                            </p>
                                        </div>
                                    </div>
                                    <div className="text-xs text-text-secondary">{summary.status}</div>
                                </Button>
                            );
                        })}
                    </div>
                </div>

                {/* Incident Details */}
                <div className="flex-1 overflow-auto">
                    {isLoadingDetail && (
                        <div className="flex items-center justify-center h-full">
                            <p className="text-sm text-text-secondary">Loading&hellip;</p>
                        </div>
                    )}

                    {!isLoadingDetail && selectedArtifact && selectedPayload && (
                        <div className="p-6 space-y-6">
                            {/* Header */}
                            <div>
                                <div className="flex items-start justify-between mb-2">
                                    <h3 className="text-xl font-semibold text-text-primary">
                                        {selectedArtifact.title}
                                    </h3>
                                </div>
                                <div className="flex items-center gap-3 text-sm">
                                    <span
                                        className={`px-2 py-1 rounded-full font-medium text-xs ${getSeverityColor(selectedPayload.severity)}`}
                                    >
                                        {selectedPayload.severity}
                                    </span>
                                    <span
                                        className={`flex items-center gap-1 ${getStatusColor(selectedPayload.incidentStatus)}`}
                                    >
                                        {getStatusIcon(selectedPayload.incidentStatus)}
                                        {selectedPayload.incidentStatus}
                                    </span>
                                </div>
                            </div>

                            {/* Key Info */}
                            <div className="grid grid-cols-2 gap-4">
                                <div className="p-4 rounded-lg border border-divider bg-bg-paper">
                                    <div className="text-xs text-text-secondary mb-1">Detected At</div>
                                    <div className="text-sm font-medium text-text-primary">
                                        {formatTimestamp(selectedPayload.detectedAt)}
                                    </div>
                                </div>
                                {selectedPayload.resolvedAt && (
                                    <div className="p-4 rounded-lg border border-divider bg-bg-paper">
                                        <div className="text-xs text-text-secondary mb-1">Resolved At</div>
                                        <div className="text-sm font-medium text-text-primary">
                                            {formatTimestamp(selectedPayload.resolvedAt)}
                                        </div>
                                    </div>
                                )}
                                {selectedPayload.assignedTo && (
                                    <div className="p-4 rounded-lg border border-divider bg-bg-paper">
                                        <div className="text-xs text-text-secondary mb-1">Assigned To</div>
                                        <div className="flex items-center gap-2">
                                            <Person className="w-4 h-4 text-text-secondary" />
                                            <span className="text-sm font-medium text-text-primary">
                                                {selectedPayload.assignedTo}
                                            </span>
                                        </div>
                                    </div>
                                )}
                                <div className="p-4 rounded-lg border border-divider bg-bg-paper">
                                    <div className="text-xs text-text-secondary mb-1">Duration</div>
                                    <div className="text-sm font-medium text-text-primary">
                                        {calculateDuration(
                                            selectedPayload.detectedAt,
                                            selectedPayload.resolvedAt,
                                        )}
                                    </div>
                                </div>
                            </div>

                            {/* Impact */}
                            {selectedPayload.impact && (
                                <div className="p-4 rounded-lg border border-divider bg-bg-paper">
                                    <h4 className="text-sm font-medium text-text-primary mb-2">Impact</h4>
                                    <p className="text-sm text-text-secondary">{selectedPayload.impact}</p>
                                </div>
                            )}

                            {/* Root Cause */}
                            {selectedPayload.rootCause && (
                                <div className="p-4 rounded-lg border border-divider bg-bg-paper">
                                    <h4 className="text-sm font-medium text-text-primary mb-2">Root Cause</h4>
                                    <p className="text-sm text-text-secondary">{selectedPayload.rootCause}</p>
                                </div>
                            )}

                            {/* Timeline */}
                            {selectedPayload.timeline.length > 0 && (
                                <div>
                                    <h4 className="text-sm font-medium text-text-primary mb-3">Timeline</h4>
                                    <div className="space-y-3">
                                        {selectedPayload.timeline.map((event, idx) => (
                                            <div key={idx} className="flex gap-3">
                                                <div className="flex flex-col items-center">
                                                    <div className="w-3 h-3 rounded-full bg-primary-600" />
                                                    {idx < selectedPayload.timeline.length - 1 && (
                                                        <div className="w-0.5 flex-1 bg-grey-200 mt-1" />
                                                    )}
                                                </div>
                                                <div className="flex-1 pb-4">
                                                    <div className="text-xs text-text-secondary mb-1">
                                                        {formatTimestamp(event.timestamp)}
                                                        {event.user && ` • ${event.user}`}
                                                    </div>
                                                    <p className="text-sm text-text-primary">{event.event}</p>
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            )}
                        </div>
                    )}

                    {!isLoadingDetail && !selectedArtifact && (
                        <div className="flex items-center justify-center h-full">
                            <p className="text-sm text-text-secondary">Select an incident to view details</p>
                        </div>
                    )}
                </div>
            </div>

            {/* New Incident Dialog */}
            {showNewIncidentDialog && (
                <div
                    className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
                    role="dialog"
                    aria-modal="true"
                    aria-labelledby="new-incident-title"
                >
                    <div className="bg-bg-paper rounded-lg shadow-xl w-full max-w-md p-6 space-y-4">
                        <div className="flex items-center justify-between">
                            <h3
                                id="new-incident-title"
                                className="text-lg font-semibold text-text-primary"
                            >
                                New Incident
                            </h3>
                            <Button
                                type="button"
                                onClick={() => setShowNewIncidentDialog(false)}
                                className="text-text-secondary hover:text-text-primary"
                                aria-label="Close dialog"
                            >
                                <Close className="w-5 h-5" />
                            </Button>
                        </div>

                        <div className="space-y-3">
                            <div>
                                <label
                                    htmlFor="incident-title"
                                    className="block text-sm font-medium text-text-primary mb-1"
                                >
                                    Title <span aria-hidden="true">*</span>
                                </label>
                                <NativeInput
                                    id="incident-title"
                                    type="text"
                                    value={newIncidentForm.title}
                                    onChange={(e) =>
                                        setNewIncidentForm((f) => ({ ...f, title: e.target.value }))
                                    }
                                    placeholder="Brief incident description"
                                    className="w-full px-3 py-2 text-sm border border-divider rounded-md focus:outline-none focus:ring-2 focus:ring-primary-600"
                                    required
                                />
                            </div>

                            <div>
                                <label
                                    htmlFor="incident-severity"
                                    className="block text-sm font-medium text-text-primary mb-1"
                                >
                                    Severity
                                </label>
                                <NativeSelect
                                    id="incident-severity"
                                    value={newIncidentForm.severity}
                                    onChange={(e) =>
                                        setNewIncidentForm((f) => ({
                                            ...f,
                                            severity: e.target.value as IncidentSeverity,
                                        }))
                                    }
                                    className="w-full px-3 py-2 text-sm border border-divider rounded-md focus:outline-none focus:ring-2 focus:ring-primary-600"
                                >
                                    <option value="critical">Critical</option>
                                    <option value="high">High</option>
                                    <option value="medium">Medium</option>
                                    <option value="low">Low</option>
                                </NativeSelect>
                            </div>

                            <div>
                                <label
                                    htmlFor="incident-impact"
                                    className="block text-sm font-medium text-text-primary mb-1"
                                >
                                    Impact
                                </label>
                                <NativeTextarea
                                    id="incident-impact"
                                    value={newIncidentForm.impact}
                                    onChange={(e) =>
                                        setNewIncidentForm((f) => ({ ...f, impact: e.target.value }))
                                    }
                                    placeholder="Describe the business or user impact"
                                    rows={3}
                                    className="w-full px-3 py-2 text-sm border border-divider rounded-md focus:outline-none focus:ring-2 focus:ring-primary-600"
                                />
                            </div>
                        </div>

                        <div className="flex justify-end gap-3 pt-2">
                            <Button
                                type="button"
                                onClick={() => setShowNewIncidentDialog(false)}
                                className="px-4 py-2 text-sm text-text-secondary hover:text-text-primary border border-divider rounded-md transition-colors"
                            >
                                Cancel
                            </Button>
                            <Button
                                type="button"
                                onClick={() => void handleCreateIncident()}
                                disabled={isCreating || !newIncidentForm.title.trim()}
                                className="px-4 py-2 text-sm bg-primary-600 text-white rounded-md hover:bg-primary-700 disabled:opacity-50 transition-colors"
                            >
                                {isCreating ? 'Creating…' : 'Create Incident'}
                            </Button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};
