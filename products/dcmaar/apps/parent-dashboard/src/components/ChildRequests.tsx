/**
 * ChildRequests - Handle child requests (extend time, unblock)
 * 
 * REUSES: 
 * - hooks/useGuardian.ts (useChildRequests, useChildren, useDevices)
 * - services/api.service.ts (type definitions)
 * NO DUPLICATION: Uses centralized state and API layer
 */

import { useState, useCallback, memo } from 'react';
import { useChildRequests, useChildren, useDevices } from '../hooks/useGuardian';
import type { ChildRequest, RequestDecisionInput } from '../services/api.service';

// ============================================================================
// Request Card Component
// ============================================================================

interface RequestCardProps {
    request: ChildRequest;
    childName?: string;
    onApprove: (request: ChildRequest) => void;
    onDeny: (request: ChildRequest) => void;
    loading?: boolean;
}

const RequestCard = memo(function RequestCard({
    request,
    childName,
    onApprove,
    onDeny,
    loading
}: RequestCardProps) {
    const getRequestIcon = (type: string) => {
        switch (type) {
            case 'extend_session': return '⏰';
            case 'unblock': return '🔓';
            default: return '❓';
        }
    };

    const getRequestTitle = (type: string) => {
        switch (type) {
            case 'extend_session': return 'Extra Time Request';
            case 'unblock': return 'Unblock Request';
            default: return 'Request';
        }
    };

    const formatTime = (dateStr: string) => {
        const date = new Date(dateStr);
        const now = new Date();
        const diffMs = now.getTime() - date.getTime();
        const diffMins = Math.floor(diffMs / 60000);

        if (diffMins < 1) return 'Just now';
        if (diffMins < 60) return `${diffMins}m ago`;
        const diffHours = Math.floor(diffMins / 60);
        if (diffHours < 24) return `${diffHours}h ago`;
        return date.toLocaleDateString();
    };

    const isPending = request.status === 'pending';

    return (
        <div className={`bg-white rounded-lg shadow-sm border p-4 ${isPending ? 'border-yellow-200 bg-yellow-50/30' : 'border-gray-200'
            }`}>
            <div className="flex items-start justify-between">
                <div className="flex items-center gap-3">
                    <span className="text-2xl">{getRequestIcon(request.type)}</span>
                    <div>
                        <h3 className="font-medium text-gray-900">{getRequestTitle(request.type)}</h3>
                        {childName && (
                            <p className="text-sm text-gray-500">From {childName}</p>
                        )}
                    </div>
                </div>
                <span className={`inline-flex items-center px-2 py-1 rounded-full text-xs font-medium ${request.status === 'pending' ? 'bg-yellow-100 text-yellow-800' :
                    request.status === 'approved' ? 'bg-green-100 text-green-800' :
                        'bg-red-100 text-red-800'
                    }`}>
                    {request.status.charAt(0).toUpperCase() + request.status.slice(1)}
                </span>
            </div>

            <div className="mt-3 space-y-1 text-sm text-gray-600">
                {request.type === 'extend_session' && request.minutes_requested && (
                    <p>Requested: <span className="font-medium">{request.minutes_requested} minutes</span></p>
                )}
                {request.type === 'unblock' && request.resource && (
                    <p>Resource: <span className="font-medium">{request.resource}</span></p>
                )}
                {request.reason && (
                    <p>Reason: <span className="italic">"{request.reason}"</span></p>
                )}
                <p className="text-gray-400">{formatTime(request.created_at)}</p>
            </div>

            {isPending && (
                <div className="mt-4 flex gap-2">
                    <button
                        onClick={() => onApprove(request)}
                        disabled={loading}
                        className="flex-1 px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                    >
                        ✓ Approve
                    </button>
                    <button
                        onClick={() => onDeny(request)}
                        disabled={loading}
                        className="flex-1 px-4 py-2 bg-gray-200 text-gray-700 rounded-lg hover:bg-gray-300 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                    >
                        ✗ Deny
                    </button>
                </div>
            )}
        </div>
    );
});

// ============================================================================
// Approval Modal Component
// ============================================================================

interface ApprovalModalProps {
    request: ChildRequest;
    onConfirm: (decision: RequestDecisionInput) => void;
    onCancel: () => void;
    devices: Array<{ id: string; device_name: string }>;
}

const ApprovalModal = memo(function ApprovalModal({
    request,
    onConfirm,
    onCancel,
    devices
}: ApprovalModalProps) {
    const [minutesGranted, setMinutesGranted] = useState(request.minutes_requested || 15);
    const [selectedDeviceId, setSelectedDeviceId] = useState(devices[0]?.id || '');

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();

        const decision: RequestDecisionInput = {
            type: request.type,
            decision: 'approved',
            device_id: selectedDeviceId || undefined,
        };

        if (request.type === 'extend_session') {
            decision.minutes_granted = minutesGranted;
        }

        onConfirm(decision);
    };

    return (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
            <div className="bg-white rounded-xl shadow-xl max-w-md w-full mx-4 p-6">
                <h2 className="text-xl font-semibold text-gray-900 mb-4">
                    Approve {request.type === 'extend_session' ? 'Extra Time' : 'Unblock'} Request
                </h2>

                <form onSubmit={handleSubmit} className="space-y-4">
                    {request.type === 'extend_session' && (
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                Minutes to grant
                            </label>
                            <div className="flex items-center gap-2">
                                {[15, 30, 45, 60].map(mins => (
                                    <button
                                        key={mins}
                                        type="button"
                                        onClick={() => setMinutesGranted(mins)}
                                        className={`px-3 py-2 rounded-lg text-sm font-medium transition-colors ${minutesGranted === mins
                                            ? 'bg-blue-600 text-white'
                                            : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                                            }`}
                                    >
                                        {mins}m
                                    </button>
                                ))}
                                <input
                                    type="number"
                                    min="5"
                                    max="120"
                                    value={minutesGranted}
                                    onChange={(e) => setMinutesGranted(Number(e.target.value))}
                                    className="w-20 px-3 py-2 border border-gray-300 rounded-lg text-sm"
                                />
                            </div>
                        </div>
                    )}

                    {devices.length > 0 && (
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                Apply to device
                            </label>
                            <select
                                value={selectedDeviceId}
                                onChange={(e) => setSelectedDeviceId(e.target.value)}
                                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                            >
                                <option value="">All devices</option>
                                {devices.map(device => (
                                    <option key={device.id} value={device.id}>
                                        {device.device_name}
                                    </option>
                                ))}
                            </select>
                        </div>
                    )}

                    <div className="flex gap-3 pt-4">
                        <button
                            type="submit"
                            className="flex-1 px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors"
                        >
                            Confirm Approval
                        </button>
                        <button
                            type="button"
                            onClick={onCancel}
                            className="flex-1 px-4 py-2 bg-gray-200 text-gray-700 rounded-lg hover:bg-gray-300 transition-colors"
                        >
                            Cancel
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
});

// Main Component
// ============================================================================

export interface ChildRequestsProps {
    childId?: string;
    showHistory?: boolean;
}

function ChildRequestsComponent({ childId, showHistory = false }: ChildRequestsProps) {
    const { children } = useChildren();
    const { devices } = useDevices();
    // If no explicit childId provided, default to first available child
    const effectiveChildId = childId ?? (children[0]?.id ?? undefined);

    const {
        requests,
        pendingRequests,
        loading,
        pendingCount,
        decideRequest
    } = useChildRequests(effectiveChildId);

    const [processingId, setProcessingId] = useState<string | null>(null);
    const [approvalRequest, setApprovalRequest] = useState<ChildRequest | null>(null);

    const getChildName = useCallback((id: string) => {
        return children.find(c => c.id === id)?.name || 'Unknown';
    }, [children]);

    const getChildDevices = useCallback((id: string) => {
        return devices.filter(d => d.child_id === id);
    }, [devices]);

    const handleApprove = useCallback((request: ChildRequest) => {
        setApprovalRequest(request);
    }, []);

    const handleDeny = useCallback(async (request: ChildRequest) => {
        setProcessingId(request.id);
        try {
            await decideRequest(request.id, {
                type: request.type,
                decision: 'denied',
            });
        } catch (err) {
            console.error('Failed to deny request:', err);
        } finally {
            setProcessingId(null);
        }
    }, [decideRequest]);

    const handleConfirmApproval = useCallback(async (decision: RequestDecisionInput) => {
        if (!approvalRequest) return;

        setProcessingId(approvalRequest.id);
        try {
            await decideRequest(approvalRequest.id, decision);
            setApprovalRequest(null);
        } catch (err) {
            console.error('Failed to approve request:', err);
        } finally {
            setProcessingId(null);
        }
    }, [approvalRequest, decideRequest]);

    const displayRequests = showHistory ? requests : pendingRequests;

    if (loading) {
        return (
            <div className="flex items-center justify-center h-32">
                <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-blue-600" />
            </div>
        );
    }

    return (
        <div className="space-y-4">
            {/* Header */}
            <div className="flex items-center justify-between">
                <h2 className="text-lg font-semibold text-gray-900">
                    {showHistory ? 'Request History' : 'Pending Requests'}
                    {pendingCount > 0 && !showHistory && (
                        <span className="ml-2 inline-flex items-center justify-center px-2 py-1 text-xs font-bold leading-none text-white bg-red-500 rounded-full">
                            {pendingCount}
                        </span>
                    )}
                </h2>
            </div>

            {/* Request List */}
            {displayRequests.length === 0 ? (
                <div className="text-center py-8 bg-gray-50 rounded-lg">
                    <p className="text-gray-500">
                        {showHistory ? 'No request history' : 'No pending requests'}
                    </p>
                    <p className="text-sm text-gray-400 mt-1">
                        {showHistory
                            ? 'Requests will appear here after being processed'
                            : 'Child requests for extra time or unblocking will appear here'
                        }
                    </p>
                </div>
            ) : (
                <div className="grid gap-4">
                    {displayRequests.map(request => (
                        <RequestCard
                            key={request.id}
                            request={request}
                            childName={!childId ? getChildName(request.child_id) : undefined}
                            onApprove={handleApprove}
                            onDeny={handleDeny}
                            loading={processingId === request.id}
                        />
                    ))}
                </div>
            )}

            {/* Approval Modal */}
            {approvalRequest && (
                <ApprovalModal
                    request={approvalRequest}
                    devices={getChildDevices(approvalRequest.child_id)}
                    onConfirm={handleConfirmApproval}
                    onCancel={() => setApprovalRequest(null)}
                />
            )}
        </div>
    );
}

export const ChildRequests = memo(ChildRequestsComponent);
export default ChildRequests;
