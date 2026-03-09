/**
 * Release Packet Panel Component
 *
 * Displays release package information, evidence, and approval status.
 * Used in Deploy surface Deployments segment.
 *
 * @doc.type component
 * @doc.purpose RUN phase release packet and evidence display
 * @doc.layer product
 * @doc.pattern Panel Component
 */

import React, { useState, useCallback } from 'react';
import { Package as Inventory, CheckCircle, XCircle as Cancel, Hourglass as HourglassEmpty, Download, BadgeCheck as Verified, User as Person, Clock as Schedule, FileText as Description, Shield as Security } from 'lucide-react';
import type { ReleasePacketPayload, EvidencePackPayload } from '@/shared/types/lifecycle-artifacts';

export interface ApprovalGate {
    id: string;
    name: string;
    status: 'pending' | 'approved' | 'rejected';
    approver?: string;
    approvedAt?: string;
    comments?: string;
}

export interface ReleasePacketPanelProps {
    releasePacket: ReleasePacketPayload;
    evidencePack?: EvidencePackPayload;
    approvalGates: ApprovalGate[];
    onApprove?: (gateId: string, comments?: string) => Promise<void>;
    onReject?: (gateId: string, reason: string) => Promise<void>;
    onDownloadArtifact?: (artifact: { name: string; url: string }) => Promise<void>;
    onDownloadEvidence?: (format: 'pdf' | 'zip') => Promise<void>;
    currentUserId?: string;
    isLoading?: boolean;
}

const APPROVAL_STATUS_CONFIG: Record<string, { icon: React.ReactNode; color: string; bgColor: string }> = {
    pending: {
        icon: <HourglassEmpty className="w-4 h-4" />,
        color: 'text-yellow-600',
        bgColor: 'bg-yellow-100 dark:bg-yellow-900/30',
    },
    approved: {
        icon: <CheckCircle className="w-4 h-4" />,
        color: 'text-green-600',
        bgColor: 'bg-green-100 dark:bg-green-900/30',
    },
    rejected: {
        icon: <Cancel className="w-4 h-4" />,
        color: 'text-red-600',
        bgColor: 'bg-red-100 dark:bg-red-900/30',
    },
};

/**
 * Release Packet Panel for RUN phase.
 */
export const ReleasePacketPanel: React.FC<ReleasePacketPanelProps> = ({
    releasePacket,
    evidencePack,
    approvalGates,
    onApprove,
    onReject,
    onDownloadArtifact,
    onDownloadEvidence,
    currentUserId,
    isLoading = false,
}) => {
    const [activeTab, setActiveTab] = useState<'overview' | 'artifacts' | 'evidence' | 'approvals'>('overview');
    const [approvalComments, setApprovalComments] = useState<Record<string, string>>({});
    const [processingGate, setProcessingGate] = useState<string | null>(null);

    const allApproved = approvalGates.every((g) => g.status === 'approved');
    const anyRejected = approvalGates.some((g) => g.status === 'rejected');
    const pendingApprovals = approvalGates.filter((g) => g.status === 'pending').length;

    const handleApprove = useCallback(
        async (gateId: string) => {
            if (!onApprove) return;
            setProcessingGate(gateId);
            try {
                await onApprove(gateId, approvalComments[gateId]);
            } finally {
                setProcessingGate(null);
            }
        },
        [onApprove, approvalComments],
    );

    const handleReject = useCallback(
        async (gateId: string) => {
            if (!onReject) return;
            const reason = approvalComments[gateId] || 'No reason provided';
            setProcessingGate(gateId);
            try {
                await onReject(gateId, reason);
            } finally {
                setProcessingGate(null);
            }
        },
        [onReject, approvalComments],
    );

    return (
        <div className="flex flex-col h-full">
            {/* Header */}
            <div className="flex items-center justify-between p-4 border-b border-divider">
                <div className="flex items-center gap-3">
                    <div
                        className={`p-2 rounded-lg ${allApproved
                                ? 'bg-green-100 dark:bg-green-900/30'
                                : anyRejected
                                    ? 'bg-red-100 dark:bg-red-900/30'
                                    : 'bg-yellow-100 dark:bg-yellow-900/30'
                            }`}
                    >
                        <Inventory
                            className={`w-5 h-5 ${allApproved
                                    ? 'text-green-600'
                                    : anyRejected
                                        ? 'text-red-600'
                                        : 'text-yellow-600'
                                }`}
                        />
                    </div>
                    <div>
                        <h3 className="font-semibold text-text-primary">
                            Release {releasePacket.version}
                        </h3>
                        <p className="text-xs text-text-secondary">
                            {allApproved
                                ? 'Ready to deploy'
                                : anyRejected
                                    ? 'Rejected'
                                    : `${pendingApprovals} approval(s) pending`}
                        </p>
                    </div>
                </div>
                {onDownloadEvidence && (
                    <div className="flex gap-2">
                        <button
                            onClick={() => onDownloadEvidence('pdf')}
                            className="flex items-center gap-1 px-3 py-1.5 text-sm text-text-secondary hover:text-text-primary hover:bg-grey-100 dark:hover:bg-grey-800 rounded-lg transition-colors"
                        >
                            <Download className="w-4 h-4" /> PDF
                        </button>
                        <button
                            onClick={() => onDownloadEvidence('zip')}
                            className="flex items-center gap-1 px-3 py-1.5 text-sm text-text-secondary hover:text-text-primary hover:bg-grey-100 dark:hover:bg-grey-800 rounded-lg transition-colors"
                        >
                            <Download className="w-4 h-4" /> ZIP
                        </button>
                    </div>
                )}
            </div>

            {/* Tabs */}
            <div className="flex border-b border-divider">
                <button
                    onClick={() => setActiveTab('overview')}
                    className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${activeTab === 'overview'
                            ? 'border-primary-600 text-primary-600'
                            : 'border-transparent text-text-secondary hover:text-text-primary'
                        }`}
                >
                    Overview
                </button>
                <button
                    onClick={() => setActiveTab('artifacts')}
                    className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${activeTab === 'artifacts'
                            ? 'border-primary-600 text-primary-600'
                            : 'border-transparent text-text-secondary hover:text-text-primary'
                        }`}
                >
                    Artifacts ({releasePacket.artifacts?.length || 0})
                </button>
                <button
                    onClick={() => setActiveTab('evidence')}
                    className={`flex items-center gap-1 px-4 py-2 text-sm font-medium border-b-2 transition-colors ${activeTab === 'evidence'
                            ? 'border-primary-600 text-primary-600'
                            : 'border-transparent text-text-secondary hover:text-text-primary'
                        }`}
                >
                    <Verified className="w-4 h-4" /> Evidence
                </button>
                <button
                    onClick={() => setActiveTab('approvals')}
                    className={`flex items-center gap-1 px-4 py-2 text-sm font-medium border-b-2 transition-colors ${activeTab === 'approvals'
                            ? 'border-primary-600 text-primary-600'
                            : 'border-transparent text-text-secondary hover:text-text-primary'
                        }`}
                >
                    <Security className="w-4 h-4" /> Approvals ({approvalGates.length})
                </button>
            </div>

            {/* Content */}
            <div className="flex-1 overflow-auto p-4">
                {activeTab === 'overview' && (
                    <div className="space-y-4">
                        {/* Release Info */}
                        <div className="grid grid-cols-2 gap-4">
                            <div className="p-3 bg-grey-50 dark:bg-grey-800/50 rounded-lg">
                                <div className="text-xs text-text-secondary mb-1">Version</div>
                                <div className="font-mono font-medium text-text-primary">
                                    {releasePacket.version}
                                </div>
                            </div>
                            <div className="p-3 bg-grey-50 dark:bg-grey-800/50 rounded-lg">
                                <div className="text-xs text-text-secondary mb-1">Build ID</div>
                                <div className="font-mono font-medium text-text-primary">
                                    {releasePacket.buildId}
                                </div>
                            </div>
                            <div className="p-3 bg-grey-50 dark:bg-grey-800/50 rounded-lg">
                                <div className="text-xs text-text-secondary mb-1">Branch</div>
                                <div className="font-mono text-text-primary">{releasePacket.branch}</div>
                            </div>
                            <div className="p-3 bg-grey-50 dark:bg-grey-800/50 rounded-lg">
                                <div className="text-xs text-text-secondary mb-1">Commit</div>
                                <div className="font-mono text-text-primary">
                                    {releasePacket.commit?.substring(0, 12)}
                                </div>
                            </div>
                        </div>

                        {/* Release Notes */}
                        {releasePacket.releaseNotes && (
                            <div>
                                <h4 className="text-sm font-medium text-text-primary mb-2 flex items-center gap-2">
                                    <Description className="w-4 h-4" /> Release Notes
                                </h4>
                                <div className="p-3 bg-grey-50 dark:bg-grey-800/50 rounded-lg prose prose-sm max-w-none text-text-primary">
                                    {releasePacket.releaseNotes}
                                </div>
                            </div>
                        )}

                        {/* Changes */}
                        {releasePacket.changes && releasePacket.changes.length > 0 && (
                            <div>
                                <h4 className="text-sm font-medium text-text-primary mb-2">Changes</h4>
                                <ul className="space-y-1">
                                    {releasePacket.changes.map((change, idx) => (
                                        <li
                                            key={idx}
                                            className="flex items-start gap-2 text-sm text-text-primary"
                                        >
                                            <span className="text-primary-500 mt-1">•</span>
                                            {change}
                                        </li>
                                    ))}
                                </ul>
                            </div>
                        )}
                    </div>
                )}

                {activeTab === 'artifacts' && (
                    <div className="space-y-2">
                        {!releasePacket.artifacts || releasePacket.artifacts.length === 0 ? (
                            <div className="text-center py-8 text-text-secondary">
                                <Inventory className="w-12 h-12 mx-auto mb-2 opacity-50" />
                                <p className="text-sm">No artifacts in this release</p>
                            </div>
                        ) : (
                            releasePacket.artifacts.map((artifact, idx) => (
                                <button
                                    key={idx}
                                    onClick={() => onDownloadArtifact?.(artifact)}
                                    className="w-full flex items-center gap-3 p-3 text-left border border-divider rounded-lg bg-bg-paper hover:bg-grey-50 dark:hover:bg-grey-800/50 transition-colors"
                                >
                                    <Download className="w-5 h-5 text-primary-600" />
                                    <div className="flex-1 min-w-0">
                                        <div className="font-medium text-sm text-text-primary truncate">
                                            {artifact.name}
                                        </div>
                                        {artifact.size && (
                                            <div className="text-xs text-text-secondary">
                                                {artifact.size}
                                            </div>
                                        )}
                                    </div>
                                    <span className="text-xs text-primary-600">Download</span>
                                </button>
                            ))
                        )}
                    </div>
                )}

                {activeTab === 'evidence' && (
                    <div className="space-y-4">
                        {!evidencePack ? (
                            <div className="text-center py-8 text-text-secondary">
                                <Verified className="w-12 h-12 mx-auto mb-2 opacity-50" />
                                <p className="text-sm">No evidence pack generated</p>
                            </div>
                        ) : (
                            <>
                                {/* Test Results */}
                                {evidencePack.testResults && (
                                    <div className="p-4 border border-divider rounded-lg bg-bg-paper">
                                        <h4 className="text-sm font-medium text-text-primary mb-3">
                                            Test Results
                                        </h4>
                                        <div className="grid grid-cols-4 gap-3">
                                            <div className="text-center">
                                                <div className="text-xl font-bold text-text-primary">
                                                    {evidencePack.testResults.total}
                                                </div>
                                                <div className="text-xs text-text-secondary">Total</div>
                                            </div>
                                            <div className="text-center">
                                                <div className="text-xl font-bold text-green-500">
                                                    {evidencePack.testResults.passed}
                                                </div>
                                                <div className="text-xs text-text-secondary">Passed</div>
                                            </div>
                                            <div className="text-center">
                                                <div className="text-xl font-bold text-red-500">
                                                    {evidencePack.testResults.failed}
                                                </div>
                                                <div className="text-xs text-text-secondary">Failed</div>
                                            </div>
                                            <div className="text-center">
                                                <div className="text-xl font-bold text-yellow-500">
                                                    {evidencePack.testResults.skipped}
                                                </div>
                                                <div className="text-xs text-text-secondary">Skipped</div>
                                            </div>
                                        </div>
                                        {evidencePack.testResults.coverage !== undefined && (
                                            <div className="mt-3 pt-3 border-t border-divider">
                                                <div className="flex items-center justify-between text-sm">
                                                    <span className="text-text-secondary">Coverage</span>
                                                    <span className="font-medium text-text-primary">
                                                        {evidencePack.testResults.coverage}%
                                                    </span>
                                                </div>
                                                <div className="mt-1 h-2 bg-grey-200 dark:bg-grey-700 rounded-full overflow-hidden">
                                                    <div
                                                        className={`h-full ${evidencePack.testResults.coverage >= 80
                                                                ? 'bg-green-500'
                                                                : evidencePack.testResults.coverage >= 60
                                                                    ? 'bg-yellow-500'
                                                                    : 'bg-red-500'
                                                            }`}
                                                        style={{ width: `${evidencePack.testResults.coverage}%` }}
                                                    />
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                )}

                                {/* Security Scan */}
                                {evidencePack.securityScan && (
                                    <div className="p-4 border border-divider rounded-lg bg-bg-paper">
                                        <h4 className="text-sm font-medium text-text-primary mb-3 flex items-center gap-2">
                                            <Security className="w-4 h-4" /> Security Scan
                                        </h4>
                                        <div className="grid grid-cols-4 gap-3">
                                            <div className="text-center">
                                                <div className="text-xl font-bold text-red-500">
                                                    {evidencePack.securityScan.critical}
                                                </div>
                                                <div className="text-xs text-text-secondary">Critical</div>
                                            </div>
                                            <div className="text-center">
                                                <div className="text-xl font-bold text-orange-500">
                                                    {evidencePack.securityScan.high}
                                                </div>
                                                <div className="text-xs text-text-secondary">High</div>
                                            </div>
                                            <div className="text-center">
                                                <div className="text-xl font-bold text-yellow-500">
                                                    {evidencePack.securityScan.medium}
                                                </div>
                                                <div className="text-xs text-text-secondary">Medium</div>
                                            </div>
                                            <div className="text-center">
                                                <div className="text-xl font-bold text-grey-500">
                                                    {evidencePack.securityScan.low}
                                                </div>
                                                <div className="text-xs text-text-secondary">Low</div>
                                            </div>
                                        </div>
                                    </div>
                                )}

                                {/* Compliance */}
                                {evidencePack.compliance && evidencePack.compliance.length > 0 && (
                                    <div className="p-4 border border-divider rounded-lg bg-bg-paper">
                                        <h4 className="text-sm font-medium text-text-primary mb-3">
                                            Compliance
                                        </h4>
                                        <div className="space-y-2">
                                            {evidencePack.compliance.map((item, idx) => (
                                                <div
                                                    key={idx}
                                                    className="flex items-center justify-between text-sm"
                                                >
                                                    <span className="text-text-primary">{item.name}</span>
                                                    <span
                                                        className={`px-2 py-0.5 rounded text-xs ${item.status === 'passed'
                                                                ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300'
                                                                : 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300'
                                                            }`}
                                                    >
                                                        {item.status}
                                                    </span>
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                )}
                            </>
                        )}
                    </div>
                )}

                {activeTab === 'approvals' && (
                    <div className="space-y-3">
                        {approvalGates.map((gate) => (
                            <div
                                key={gate.id}
                                className={`p-4 border rounded-lg ${gate.status === 'approved'
                                        ? 'border-green-200 dark:border-green-800 bg-green-50 dark:bg-green-900/20'
                                        : gate.status === 'rejected'
                                            ? 'border-red-200 dark:border-red-800 bg-red-50 dark:bg-red-900/20'
                                            : 'border-divider bg-bg-paper'
                                    }`}
                            >
                                <div className="flex items-start justify-between mb-3">
                                    <div className="flex items-center gap-2">
                                        <div className={APPROVAL_STATUS_CONFIG[gate.status].color}>
                                            {APPROVAL_STATUS_CONFIG[gate.status].icon}
                                        </div>
                                        <div>
                                            <div className="font-medium text-text-primary">{gate.name}</div>
                                            <div className="text-xs text-text-secondary capitalize">
                                                {gate.status}
                                            </div>
                                        </div>
                                    </div>
                                    {gate.approver && (
                                        <div className="flex items-center gap-1 text-xs text-text-secondary">
                                            <Person className="w-3 h-3" />
                                            {gate.approver}
                                            {gate.approvedAt && (
                                                <>
                                                    <Schedule className="w-3 h-3 ml-2" />
                                                    {new Date(gate.approvedAt).toLocaleString()}
                                                </>
                                            )}
                                        </div>
                                    )}
                                </div>

                                {gate.comments && (
                                    <div className="text-sm text-text-secondary mb-3 p-2 bg-bg-default rounded">
                                        {gate.comments}
                                    </div>
                                )}

                                {gate.status === 'pending' && onApprove && onReject && (
                                    <div className="space-y-2">
                                        <textarea
                                            value={approvalComments[gate.id] || ''}
                                            onChange={(e) =>
                                                setApprovalComments((prev) => ({
                                                    ...prev,
                                                    [gate.id]: e.target.value,
                                                }))
                                            }
                                            placeholder="Add comments (optional for approval, required for rejection)"
                                            rows={2}
                                            className="w-full px-3 py-2 text-sm border border-divider rounded bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500 resize-none"
                                        />
                                        <div className="flex gap-2 justify-end">
                                            <button
                                                onClick={() => handleReject(gate.id)}
                                                disabled={processingGate === gate.id || !approvalComments[gate.id]?.trim()}
                                                className="px-4 py-2 text-sm text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg transition-colors disabled:opacity-50"
                                            >
                                                Reject
                                            </button>
                                            <button
                                                onClick={() => handleApprove(gate.id)}
                                                disabled={processingGate === gate.id}
                                                className="px-4 py-2 text-sm bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors disabled:opacity-50"
                                            >
                                                {processingGate === gate.id ? 'Processing...' : 'Approve'}
                                            </button>
                                        </div>
                                    </div>
                                )}
                            </div>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
};

export default ReleasePacketPanel;
