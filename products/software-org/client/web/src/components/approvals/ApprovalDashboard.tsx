/**
 * Approval Dashboard Component
 *
 * @doc.type component
 * @doc.purpose Main dashboard for viewing and managing approval requests
 * @doc.layer presentation
 * @doc.pattern Container Component
 *
 * Features:
 * - List pending, approved, and rejected approvals
 * - Filter by status, type, and requester
 * - Real-time updates using TanStack Query
 * - Quick approve/reject actions
 */

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAtom } from 'jotai';
import { currentUserAtom } from '../../atoms/user';
import { useDelegateApproval } from '../../hooks';
import { DepartmentHierarchyViz } from '../org/DepartmentHierarchyViz';

interface Approval {
    id: string;
    type: string;
    requesterId: string;
    status: 'PENDING' | 'IN_PROGRESS' | 'APPROVED' | 'REJECTED';
    data: Record<string, unknown>;
    metadata: Record<string, unknown>;
    currentStepIndex: number;
    createdAt: string;
    steps: ApprovalStep[];
}

interface ApprovalStep {
    id: string;
    level: number;
    approverId: string;
    role: string;
    status: 'PENDING' | 'NOTIFIED' | 'COMPLETED';
    decision?: 'APPROVE' | 'REJECT';
    comment?: string;
    decidedAt?: string;
}

type ApprovalStatus = 'PENDING' | 'IN_PROGRESS' | 'APPROVED' | 'REJECTED';

const STATUS_COLORS: Record<ApprovalStatus, string> = {
    PENDING: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200',
    IN_PROGRESS: 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200',
    APPROVED: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200',
    REJECTED: 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200',
};

export function ApprovalDashboard() {
    const [currentUser] = useAtom(currentUserAtom);
    const [selectedStatus, setSelectedStatus] = useState<ApprovalStatus | 'ALL'>('ALL');
    const [selectedType, setSelectedType] = useState<string>('ALL');
    const queryClient = useQueryClient();

    // Fetch approvals
    const { data, isLoading, error } = useQuery({
        queryKey: ['approvals', selectedStatus, selectedType],
        queryFn: async () => {
            const params = new URLSearchParams();
            if (selectedStatus !== 'ALL') params.append('status', selectedStatus);
            if (selectedType !== 'ALL') params.append('type', selectedType);
            params.append('limit', '50');

            const response = await fetch(`/api/v1/approvals?${params}`);
            if (!response.ok) throw new Error('Failed to fetch approvals');
            return response.json();
        },
    });

    // Fetch pending approvals for current user
    const { data: pendingData } = useQuery({
        queryKey: ['approvals', 'pending', currentUser?.id],
        queryFn: async () => {
            if (!currentUser?.id) return { data: [] };
            const response = await fetch(`/api/v1/approvals/pending?userId=${currentUser.id}`);
            if (!response.ok) throw new Error('Failed to fetch pending approvals');
            return response.json();
        },
        enabled: !!currentUser?.id,
    });

    // Record decision mutation
    const recordDecision = useMutation({
        mutationFn: async ({
            approvalId,
            decision,
            comment,
        }: {
            approvalId: string;
            decision: 'APPROVE' | 'REJECT';
            comment?: string;
        }) => {
            const response = await fetch(`/api/v1/approvals/${approvalId}/decision`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    approverId: currentUser?.id,
                    decision,
                    comment,
                }),
            });
            if (!response.ok) throw new Error('Failed to record decision');
            return response.json();
        },
        onSuccess: () => {
            // Invalidate and refetch
            queryClient.invalidateQueries({ queryKey: ['approvals'] });
        },
    });

    if (isLoading) {
        return (
            <div className="flex items-center justify-center h-64">
                <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600" />
            </div>
        );
    }

    if (error) {
        return (
            <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-4">
                <h3 className="text-red-800 dark:text-red-200 font-semibold">Error loading approvals</h3>
                <p className="text-red-600 dark:text-red-300 text-sm mt-1">
                    {error instanceof Error ? error.message : 'Unknown error'}
                </p>
            </div>
        );
    }

    const approvals = data?.data || [];
    const pendingApprovals = pendingData?.data || [];

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-3xl font-bold text-gray-900 dark:text-white">Approvals</h1>
                    <p className="text-gray-600 dark:text-gray-400 mt-1">
                        Manage approval requests and decisions
                    </p>
                </div>
                {pendingApprovals.length > 0 && (
                    <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg px-4 py-2">
                        <span className="text-yellow-800 dark:text-yellow-200 font-medium">
                            {pendingApprovals.length} pending your action
                        </span>
                    </div>
                )}
            </div>

            {/* Filters */}
            <div className="bg-white dark:bg-gray-800 rounded-lg shadow p-4">
                <div className="flex gap-4">
                    <div className="flex-1">
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                            Status
                        </label>
                        <select
                            value={selectedStatus}
                            onChange={(e) => setSelectedStatus(e.target.value as ApprovalStatus | 'ALL')}
                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
                        >
                            <option value="ALL">All Statuses</option>
                            <option value="PENDING">Pending</option>
                            <option value="IN_PROGRESS">In Progress</option>
                            <option value="APPROVED">Approved</option>
                            <option value="REJECTED">Rejected</option>
                        </select>
                    </div>
                    <div className="flex-1">
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                            Type
                        </label>
                        <select
                            value={selectedType}
                            onChange={(e) => setSelectedType(e.target.value)}
                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
                        >
                            <option value="ALL">All Types</option>
                            <option value="TIME_OFF">Time Off</option>
                            <option value="EXPENSE">Expense</option>
                            <option value="HIRE">Hire</option>
                            <option value="PROMOTION">Promotion</option>
                        </select>
                    </div>
                </div>
            </div>

            {/* Pending Actions (if any) */}
            {pendingApprovals.length > 0 && (
                <div className="bg-yellow-50 dark:bg-yellow-900/10 rounded-lg p-6">
                    <h2 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
                        Pending Your Action
                    </h2>
                    <div className="space-y-3">
                        {pendingApprovals.map((approval: Approval) => (
                            <ApprovalCard
                                key={approval.id}
                                approval={approval}
                                onDecision={(decision, comment) =>
                                    recordDecision.mutate({
                                        approvalId: approval.id,
                                        decision,
                                        comment,
                                    })
                                }
                                isPending
                                currentUserId={currentUser?.id}
                            />
                        ))}
                    </div>
                </div>
            )}

            {/* All Approvals */}
            <div className="bg-white dark:bg-gray-800 rounded-lg shadow">
                <div className="p-6">
                    <h2 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
                        All Approvals
                    </h2>
                    {approvals.length === 0 ? (
                        <p className="text-gray-500 dark:text-gray-400 text-center py-8">
                            No approvals found
                        </p>
                    ) : (
                        <div className="space-y-3">
                            {approvals.map((approval: Approval) => (
                                <ApprovalCard
                                    key={approval.id}
                                    approval={approval}
                                    onDecision={(decision, comment) =>
                                        recordDecision.mutate({
                                            approvalId: approval.id,
                                            decision,
                                            comment,
                                        })
                                    }
                                    currentUserId={currentUser?.id}
                                />
                            ))}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}

interface ApprovalCardProps {
    approval: Approval;
    onDecision: (decision: 'APPROVE' | 'REJECT', comment?: string) => void;
    isPending?: boolean;
    currentUserId?: string;
}

function ApprovalCard({ approval, onDecision, isPending, currentUserId }: ApprovalCardProps) {
    const [showCommentDialog, setShowCommentDialog] = useState(false);
    const [comment, setComment] = useState('');
    const [pendingDecision, setPendingDecision] = useState<'APPROVE' | 'REJECT' | null>(null);
    const [expanded, setExpanded] = useState(false);
    const [showDelegateDialog, setShowDelegateDialog] = useState(false);
    const [delegateUserId, setDelegateUserId] = useState('');
    const [delegateReason, setDelegateReason] = useState('');
    const queryClient = useQueryClient();

    const delegateApproval = useDelegateApproval();

    const currentStep = approval.steps[approval.currentStepIndex];
    const statusColor = STATUS_COLORS[approval.status];

    const dataObj =
        approval.data && typeof approval.data === 'object' ? (approval.data as Record<string, unknown>) : null;

    const isRestructure = approval.type === 'restructure';
    const restructureMetadata =
        isRestructure && approval.metadata && typeof approval.metadata === 'object'
            ? (approval.metadata as {
                impact?: { departments: number; employees: number; budget: number };
                changes?: Array<{
                    type: 'merge' | 'split' | 'reorganize' | 'rename';
                    departmentId: string;
                    targetDepartmentId?: string;
                    newName?: string;
                    newParentId?: string;
                }>;
            })
            : null;

    const handleDecisionClick = (decision: 'APPROVE' | 'REJECT') => {
        setPendingDecision(decision);
        setShowCommentDialog(true);
    };

    const handleSubmitDecision = () => {
        if (!pendingDecision) return;
        onDecision(pendingDecision, comment.trim() || undefined);
        setShowCommentDialog(false);
        setComment('');
        setPendingDecision(null);
    };

    const handleDelegateClick = () => {
        setShowDelegateDialog(true);
    };

    const handleSubmitDelegate = async () => {
        if (!delegateUserId.trim() || !currentUserId) return;

        try {
            await delegateApproval.mutateAsync({
                approvalId: approval.id,
                fromUserId: currentUserId,
                toUserId: delegateUserId.trim(),
                reason: delegateReason.trim() || undefined,
            });

            // Invalidate queries to refresh the list
            queryClient.invalidateQueries({ queryKey: ['approvals'] });

            setShowDelegateDialog(false);
            setDelegateUserId('');
            setDelegateReason('');
        } catch (error) {
            console.error('Failed to delegate approval:', error);
        }
    };

    return (
        <div
            className={`border ${isPending
                ? 'border-yellow-300 dark:border-yellow-700 bg-yellow-50 dark:bg-yellow-900/10'
                : 'border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-750'
                } rounded-lg p-4`}
        >
            <div
                className="flex items-start justify-between cursor-pointer"
                onClick={() => setExpanded((v) => !v)}
            >
                <div className="flex-1">
                    <div className="flex items-center gap-3">
                        <h3 className="text-lg font-semibold text-gray-900 dark:text-white">
                            {approval.type.replace('_', ' ')}
                        </h3>
                        <span className={`px-2 py-1 text-xs font-medium rounded-full ${statusColor}`}>
                            {approval.status}
                        </span>
                        {isRestructure && (
                            <span className="px-2 py-1 text-xs font-medium rounded-full bg-purple-100 text-purple-800 dark:bg-purple-900 dark:text-purple-200">
                                🏢 Org Change
                            </span>
                        )}
                    </div>
                    <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">
                        Requested by: {approval.requesterId}
                    </p>
                    <p className="text-sm text-gray-600 dark:text-gray-400">
                        Created: {new Date(approval.createdAt).toLocaleDateString()}
                    </p>

                    {isRestructure && restructureMetadata?.impact && (
                        <div className="flex items-center gap-6 mt-2 text-sm text-gray-600 dark:text-gray-400">
                            <span>🏢 {restructureMetadata.impact.departments} depts</span>
                            <span>👥 {restructureMetadata.impact.employees} people</span>
                            <span>💵 ${(restructureMetadata.impact.budget / 1000000).toFixed(1)}M</span>
                        </div>
                    )}
                </div>
                <div className="text-gray-400 dark:text-gray-500 ml-2">{expanded ? '▲' : '▼'}</div>
            </div>

            {/* Approval Steps */}
            <div className="mt-3">
                <div className="flex items-center gap-2">
                    {approval.steps.map((step, index) => (
                        <div key={step.id} className="flex items-center">
                            <div
                                className={`flex items-center justify-center w-8 h-8 rounded-full text-sm font-medium ${step.status === 'COMPLETED'
                                    ? 'bg-green-500 text-white'
                                    : step.status === 'NOTIFIED'
                                        ? 'bg-blue-500 text-white'
                                        : 'bg-gray-300 text-gray-600'
                                    }`}
                                title={`${step.role} (level ${step.level})`}
                            >
                                {index + 1}
                            </div>
                            {index < approval.steps.length - 1 && (
                                <div className="w-6 h-0.5 bg-gray-300 dark:bg-gray-600" />
                            )}
                        </div>
                    ))}
                </div>
            </div>

            {/* Actions */}
            {isPending && currentStep && (
                <div className="mt-4 flex gap-2" onClick={(e) => e.stopPropagation()}>
                    <button
                        onClick={() => handleDecisionClick('APPROVE')}
                        className="px-4 py-2 bg-green-600 hover:bg-green-700 text-white rounded-md text-sm font-medium transition-colors"
                    >
                        Approve
                    </button>
                    <button
                        onClick={() => handleDecisionClick('REJECT')}
                        className="px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-md text-sm font-medium transition-colors"
                    >
                        Reject
                    </button>
                    <button
                        onClick={handleDelegateClick}
                        className="px-4 py-2 bg-gray-600 hover:bg-gray-700 text-white rounded-md text-sm font-medium transition-colors"
                    >
                        Delegate
                    </button>
                </div>
            )}

            {/* Expanded Details */}
            {expanded && (
                <div className="mt-4 pt-4 border-t border-gray-200 dark:border-gray-700">
                    {typeof dataObj?.description === 'string' && dataObj.description.trim().length > 0 && (
                        <div className="mb-4">
                            <h4 className="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-2">
                                Description
                            </h4>
                            <p className="text-sm text-gray-600 dark:text-gray-400">{dataObj.description}</p>
                        </div>
                    )}

                    {isRestructure && restructureMetadata?.changes && restructureMetadata.changes.length > 0 && (
                        <div>
                            <h4 className="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-2">
                                Proposed Changes ({restructureMetadata.changes.length})
                            </h4>
                            <div className="space-y-2">
                                {restructureMetadata.changes.map((change, idx) => (
                                    <div
                                        key={`${change.departmentId}-${idx}`}
                                        className="p-3 bg-gray-50 dark:bg-gray-700 rounded-md text-sm"
                                    >
                                        <span className="font-medium text-gray-900 dark:text-gray-100">
                                            {change.type.toUpperCase()}
                                        </span>
                                        <p className="text-gray-600 dark:text-gray-400 mt-1">
                                            Department ID: {change.departmentId}
                                            {change.newName && ` → ${change.newName}`}
                                            {change.newParentId && ` → Parent: ${change.newParentId}`}
                                        </p>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}
                </div>
            )}

            {/* Comment Dialog */}
            {showCommentDialog && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                    <div className="bg-white dark:bg-gray-800 rounded-lg p-6 max-w-md w-full mx-4">
                        <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
                            {pendingDecision === 'APPROVE' ? 'Approve' : 'Reject'} Request
                        </h3>
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                            Comment (optional)
                        </label>
                        <textarea
                            value={comment}
                            onChange={(e) => setComment(e.target.value)}
                            rows={3}
                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
                            placeholder="Add a comment..."
                        />
                        <div className="flex gap-2 mt-4">
                            <button
                                onClick={handleSubmitDecision}
                                className={`flex-1 px-4 py-2 text-white rounded-md text-sm font-medium transition-colors ${pendingDecision === 'APPROVE'
                                    ? 'bg-green-600 hover:bg-green-700'
                                    : 'bg-red-600 hover:bg-red-700'
                                    }`}
                            >
                                Confirm
                            </button>
                            <button
                                onClick={() => {
                                    setShowCommentDialog(false);
                                    setComment('');
                                    setPendingDecision(null);
                                }}
                                className="flex-1 px-4 py-2 bg-gray-200 dark:bg-gray-700 text-gray-900 dark:text-white rounded-md text-sm font-medium hover:bg-gray-300 dark:hover:bg-gray-600 transition-colors"
                            >
                                Cancel
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Delegate Dialog */}
            {showDelegateDialog && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                    <div className="bg-white dark:bg-gray-800 rounded-lg p-6 max-w-md w-full mx-4">
                        <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
                            Delegate Approval
                        </h3>
                        <div className="space-y-4">
                            <div>
                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                    Delegate to User ID <span className="text-red-500">*</span>
                                </label>
                                <input
                                    type="text"
                                    value={delegateUserId}
                                    onChange={(e) => setDelegateUserId(e.target.value)}
                                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
                                    placeholder="Enter user ID..."
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                    Reason (optional)
                                </label>
                                <textarea
                                    value={delegateReason}
                                    onChange={(e) => setDelegateReason(e.target.value)}
                                    rows={3}
                                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
                                    placeholder="Why are you delegating this approval?"
                                />
                            </div>
                        </div>
                        <div className="flex gap-2 mt-4">
                            <button
                                onClick={handleSubmitDelegate}
                                disabled={!delegateUserId.trim() || delegateApproval.isPending}
                                className="flex-1 px-4 py-2 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed text-white rounded-md text-sm font-medium transition-colors"
                            >
                                {delegateApproval.isPending ? 'Delegating...' : 'Delegate'}
                            </button>
                            <button
                                onClick={() => {
                                    setShowDelegateDialog(false);
                                    setDelegateUserId('');
                                    setDelegateReason('');
                                }}
                                className="flex-1 px-4 py-2 bg-gray-200 dark:bg-gray-700 text-gray-900 dark:text-white rounded-md text-sm font-medium hover:bg-gray-300 dark:hover:bg-gray-600 transition-colors"
                            >
                                Cancel
                            </button>
                        </div>
                        {delegateApproval.isError && (
                            <p className="mt-2 text-sm text-red-600 dark:text-red-400">
                                Failed to delegate: {delegateApproval.error instanceof Error ? delegateApproval.error.message : 'Unknown error'}
                            </p>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}
