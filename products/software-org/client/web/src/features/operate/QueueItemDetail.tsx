import { useState } from 'react';
import { useParams, useNavigate } from 'react-router';
import { useAtomValue } from 'jotai';
import { selectedTenantAtom } from '@/state/jotai/session.store';
import { useQueueItem, useApproveQueueItem, useRejectQueueItem } from '@/hooks/useOperateApi';
import {
    ArrowLeft,
    Package,
    Database,
    Zap,
    User,
    Clock,
    CheckCircle2,
    XCircle,
    AlertCircle,
} from 'lucide-react';
import { Badge } from "@/components/ui";

interface QueueItemContext {
    [key: string]: unknown;
    serviceId?: string;
    workflowId?: string;
    estimatedCost?: number;
    estimatedDowntime?: string;
    affectedUsers?: number;
}

/**
 * Queue Item Detail
 *
 * <p><b>Purpose</b><br>
 * Detailed view of a work queue item with context and approval actions.
 * Enables human-in-the-loop decision making for critical workflows.
 *
 * <p><b>Features</b><br>
 * - Item context and metadata
 * - Approval/rejection with comments
 * - Impact assessment
 * - Related resource links
 *
 * @doc.type component
 * @doc.purpose Queue item detail and approval
 * @doc.layer product
 * @doc.pattern Detail
 */
export function QueueItemDetail() {
    const { itemId } = useParams();
    const navigate = useNavigate();
    const selectedTenant = useAtomValue(selectedTenantAtom);
    const tenantId = selectedTenant || 'acme-payments-id';

    const [showApproveModal, setShowApproveModal] = useState(false);
    const [showRejectModal, setShowRejectModal] = useState(false);
    const [comment, setComment] = useState('');

    const { data: item, isLoading, error } = useQueueItem(itemId || '', tenantId);
    const approveMutation = useApproveQueueItem();
    const rejectMutation = useRejectQueueItem();

    if (isLoading) {
        return (
            <div className="flex items-center justify-center h-64">
                <div className="text-slate-600 dark:text-neutral-400">Loading queue item...</div>
            </div>
        );
    }

    if (error || !item) {
        return (
            <div className="p-6">
                <div className="text-red-600 dark:text-red-400">
                    Failed to load queue item: {error?.message || 'Item not found'}
                </div>
            </div>
        );
    }

    const typeConfig = {
        'deployment-approval': { variant: 'primary' as const, color: 'text-blue-600', bg: 'bg-blue-50 dark:bg-blue-900/20', icon: Package, label: 'Deployment Approval' },
        'config-change': { variant: 'warning' as const, color: 'text-amber-600', bg: 'bg-amber-50 dark:bg-amber-900/20', icon: Database, label: 'Config Change' },
        'workflow-execution': { variant: 'neutral' as const, color: 'text-purple-600', bg: 'bg-purple-50 dark:bg-purple-900/20', icon: Zap, label: 'Workflow Execution' },
    };

    const priorityConfig = {
        'high': { variant: 'danger' as const, label: 'High' },
        'medium': { variant: 'warning' as const, label: 'Medium' },
        'low': { variant: 'neutral' as const, label: 'Low' },
    };

    const TypeIcon = typeConfig[item.type as keyof typeof typeConfig].icon;
    const context = item.context as QueueItemContext;

    const handleApprove = async () => {
        try {
            await approveMutation.mutateAsync({
                itemId: item.id,
                tenantId,
                userId: 'current-user',
                comment,
            });
            navigate('/operate/queue');
        } catch (error) {
            console.error('Failed to approve item:', error);
        }
    };

    const handleReject = async () => {
        try {
            await rejectMutation.mutateAsync({
                itemId: item.id,
                tenantId,
                userId: 'current-user',
                reason: comment,
            });
            navigate('/operate/queue');
        } catch (error) {
            console.error('Failed to reject item:', error);
        }
    };

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div className="flex items-center gap-4">
                    <button
                        onClick={() => navigate('/operate/queue')}
                        className="p-2 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg transition-colors"
                    >
                        <ArrowLeft className="h-5 w-5 text-slate-600 dark:text-neutral-400" />
                    </button>
                    <div className="flex items-center gap-3">
                        <div className={`p-3 rounded-lg ${typeConfig[item.type as keyof typeof typeConfig].bg}`}>
                            <TypeIcon className={`h-6 w-6 ${typeConfig[item.type as keyof typeof typeConfig].color}`} />
                        </div>
                        <div>
                            <div className="flex items-center gap-3 mb-1">
                                <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">{item.title}</h1>
                                <Badge variant={typeConfig[item.type as keyof typeof typeConfig].variant}>
                                    {typeConfig[item.type as keyof typeof typeConfig].label}
                                </Badge>
                                <Badge variant={priorityConfig[item.priority as keyof typeof priorityConfig].variant}>
                                    {priorityConfig[item.priority as keyof typeof priorityConfig].label}
                                </Badge>
                            </div>
                            <p className="text-slate-600 dark:text-neutral-400">{item.id}</p>
                        </div>
                    </div>
                </div>
                <div className="flex items-center gap-3">
                    <button
                        onClick={() => setShowRejectModal(true)}
                        disabled={rejectMutation.isPending}
                        className="inline-flex items-center gap-2 px-4 py-2 border border-red-300 dark:border-red-600 text-red-700 dark:text-red-400 rounded-lg hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors disabled:opacity-50"
                    >
                        <XCircle className="h-4 w-4" />
                        Reject
                    </button>
                    <button
                        onClick={() => setShowApproveModal(true)}
                        disabled={approveMutation.isPending}
                        className="inline-flex items-center gap-2 px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors disabled:opacity-50"
                    >
                        <CheckCircle2 className="h-4 w-4" />
                        Approve
                    </button>
                </div>
            </div>

            {/* Description */}
            <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-3">Description</h2>
                <p className="text-slate-600 dark:text-neutral-400">{item.description}</p>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* Main Content */}
                <div className="lg:col-span-2 space-y-6">
                    {/* Context Details */}
                    <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                        <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">Context</h2>
                        <div className="space-y-4">
                            {Object.entries(context).map(([key, value]) => (
                                <div key={key} className="flex items-start gap-4 p-4 bg-slate-50 dark:bg-slate-800 rounded-lg">
                                    <div className="flex-1">
                                        <div className="text-sm font-medium text-slate-900 dark:text-neutral-100 mb-1">
                                            {key.split(/(?=[A-Z])/).join(' ').charAt(0).toUpperCase() + key.split(/(?=[A-Z])/).join(' ').slice(1)}
                                        </div>
                                        <div className="text-sm text-slate-600 dark:text-neutral-400">
                                            {typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value ?? '')}
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* Impact Assessment */}
                    {(context.estimatedCost || context.estimatedDowntime || context.affectedUsers) && (
                        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                            <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4 flex items-center gap-2">
                                <AlertCircle className="h-5 w-5 text-amber-600" />
                                Impact Assessment
                            </h2>
                            <div className="grid grid-cols-3 gap-4">
                                {context.estimatedCost && (
                                    <div className="p-4 bg-blue-50 dark:bg-blue-900/20 rounded-lg">
                                        <div className="text-sm text-slate-600 dark:text-neutral-400 mb-1">Estimated Cost</div>
                                        <div className="text-xl font-bold text-blue-600 dark:text-blue-400">
                                            ${String(context.estimatedCost)}
                                        </div>
                                    </div>
                                )}
                                {context.estimatedDowntime && (
                                    <div className="p-4 bg-amber-50 dark:bg-amber-900/20 rounded-lg">
                                        <div className="text-sm text-slate-600 dark:text-neutral-400 mb-1">Estimated Downtime</div>
                                        <div className="text-xl font-bold text-amber-600 dark:text-amber-400">
                                            {String(context.estimatedDowntime)}
                                        </div>
                                    </div>
                                )}
                                {context.affectedUsers && (
                                    <div className="p-4 bg-purple-50 dark:bg-purple-900/20 rounded-lg">
                                        <div className="text-sm text-slate-600 dark:text-neutral-400 mb-1">Affected Users</div>
                                        <div className="text-xl font-bold text-purple-600 dark:text-purple-400">
                                            {String(context.affectedUsers)}
                                        </div>
                                    </div>
                                )}
                            </div>
                        </div>
                    )}
                </div>

                {/* Sidebar */}
                <div className="space-y-6">
                    {/* Metadata */}
                    <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                        <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">Details</h2>
                        <div className="space-y-4">
                            <div>
                                <div className="text-sm text-slate-600 dark:text-neutral-400 mb-1">Requested By</div>
                                <div className="flex items-center gap-2">
                                    <User className="h-4 w-4 text-slate-400" />
                                    <span className="font-medium text-slate-900 dark:text-neutral-100">
                                        {item.requestedBy}
                                    </span>
                                </div>
                            </div>
                            <div>
                                <div className="text-sm text-slate-600 dark:text-neutral-400 mb-1">Due In</div>
                                <div className="flex items-center gap-2">
                                    <Clock className="h-4 w-4 text-slate-400" />
                                    <span className="text-sm text-slate-900 dark:text-neutral-100">
                                        {item.dueIn}
                                    </span>
                                </div>
                            </div>
                            <div>
                                <div className="text-sm text-slate-600 dark:text-neutral-400 mb-1">Priority</div>
                                <Badge variant={priorityConfig[item.priority as keyof typeof priorityConfig].variant}>
                                    {priorityConfig[item.priority as keyof typeof priorityConfig].label}
                                </Badge>
                            </div>
                        </div>
                    </div>

                    {/* Quick Actions */}
                    <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                        <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">Quick Actions</h2>
                        <div className="space-y-2">
                            {context.serviceId && (
                                <button
                                    onClick={() => navigate(`/admin/services/${context.serviceId}`)}
                                    className="w-full px-4 py-2 border border-slate-300 dark:border-slate-600 text-slate-700 dark:text-neutral-300 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors text-left"
                                >
                                    View Service
                                </button>
                            )}
                            {context.workflowId && (
                                <button
                                    onClick={() => navigate(`/build/workflows/${context.workflowId}`)}
                                    className="w-full px-4 py-2 border border-slate-300 dark:border-slate-600 text-slate-700 dark:text-neutral-300 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors text-left"
                                >
                                    View Workflow
                                </button>
                            )}
                            <button
                                onClick={() => navigate('/observe/reports')}
                                className="w-full px-4 py-2 border border-slate-300 dark:border-slate-600 text-slate-700 dark:text-neutral-300 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors text-left"
                            >
                                View Related Reports
                            </button>
                        </div>
                    </div>
                </div>
            </div>

            {/* Approve Modal */}
            {showApproveModal && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
                    <div className="bg-white dark:bg-slate-900 rounded-lg p-6 max-w-md w-full mx-4">
                        <h3 className="text-xl font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                            Approve Request
                        </h3>
                        <p className="text-slate-600 dark:text-neutral-400 mb-4">
                            Are you sure you want to approve this request? This action will proceed with the workflow.
                        </p>
                        <textarea
                            value={comment}
                            onChange={(e) => setComment(e.target.value)}
                            placeholder="Add a comment (optional)"
                            className="w-full px-3 py-2 border border-slate-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100 placeholder-slate-400 dark:placeholder-neutral-500 mb-4"
                            rows={3}
                        />
                        <div className="flex gap-3">
                            <button
                                onClick={() => {
                                    setShowApproveModal(false);
                                    setComment('');
                                }}
                                className="flex-1 px-4 py-2 border border-slate-300 dark:border-slate-600 text-slate-700 dark:text-neutral-300 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={handleApprove}
                                disabled={approveMutation.isPending}
                                className="flex-1 px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors disabled:opacity-50"
                            >
                                {approveMutation.isPending ? 'Approving...' : 'Approve'}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Reject Modal */}
            {showRejectModal && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
                    <div className="bg-white dark:bg-slate-900 rounded-lg p-6 max-w-md w-full mx-4">
                        <h3 className="text-xl font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                            Reject Request
                        </h3>
                        <p className="text-slate-600 dark:text-neutral-400 mb-4">
                            Are you sure you want to reject this request? Please provide a reason.
                        </p>
                        <textarea
                            value={comment}
                            onChange={(e) => setComment(e.target.value)}
                            placeholder="Reason for rejection (required)"
                            className="w-full px-3 py-2 border border-slate-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100 placeholder-slate-400 dark:placeholder-neutral-500 mb-4"
                            rows={3}
                        />
                        <div className="flex gap-3">
                            <button
                                onClick={() => {
                                    setShowRejectModal(false);
                                    setComment('');
                                }}
                                className="flex-1 px-4 py-2 border border-slate-300 dark:border-slate-600 text-slate-700 dark:text-neutral-300 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={handleReject}
                                disabled={rejectMutation.isPending || !comment.trim()}
                                className="flex-1 px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 transition-colors disabled:opacity-50"
                            >
                                {rejectMutation.isPending ? 'Rejecting...' : 'Reject'}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
