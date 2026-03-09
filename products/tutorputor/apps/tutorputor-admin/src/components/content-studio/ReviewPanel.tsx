/**
 * Review Panel Component
 * 
 * HITL (Human-in-the-Loop) review interface for content reviewers.
 * Allows SMEs, pedagogy experts, and safety reviewers to approve/reject content.
 */

import { useState } from 'react';
import { clsx } from 'clsx';
import { RiskBadge, type RiskLevel } from './RiskBadge';

type ReviewerRole = 'SME' | 'PEDAGOGY' | 'SAFETY' | 'ADMIN';
type ReviewDecision = 'APPROVE' | 'REJECT' | 'REQUEST_CHANGES' | 'ESCALATE';

interface ReviewQueueItem {
    id: string;
    experienceId: string;
    experienceTitle: string;
    queuedAt: Date;
    priority: number;
    riskLevel: RiskLevel;
    triggerReason: string;
    assignedTo?: string;
}

interface ReviewDecisionRecord {
    id: string;
    reviewerId: string;
    reviewerRole: ReviewerRole;
    decision: ReviewDecision;
    rationale: string;
    createdAt: Date;
}

interface ReviewPanelProps {
    experienceId: string;
    experienceTitle: string;
    riskLevel: RiskLevel;
    triggerReason?: string;
    previousDecisions?: ReviewDecisionRecord[];
    currentUserRole: ReviewerRole;
    onDecisionSubmitted: (decision: ReviewDecision, rationale: string) => void;
    className?: string;
}

export function ReviewPanel({
    experienceId,
    experienceTitle,
    riskLevel,
    triggerReason,
    previousDecisions = [],
    currentUserRole,
    onDecisionSubmitted,
    className,
}: ReviewPanelProps) {
    const [decision, setDecision] = useState<ReviewDecision | null>(null);
    const [rationale, setRationale] = useState('');
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const handleSubmit = async () => {
        if (!decision) {
            setError('Please select a decision');
            return;
        }
        if (rationale.trim().length < 10) {
            setError('Please provide a rationale of at least 10 characters');
            return;
        }

        setIsSubmitting(true);
        setError(null);

        try {
            const response = await fetch(`/api/content-studio/review-queue/${experienceId}/decision`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'x-user-id': 'current-user',
                },
                body: JSON.stringify({
                    decision,
                    rationale,
                    reviewerRole: currentUserRole,
                }),
            });

            if (!response.ok) {
                throw new Error('Failed to submit decision');
            }

            onDecisionSubmitted(decision, rationale);
            setDecision(null);
            setRationale('');
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to submit decision');
        } finally {
            setIsSubmitting(false);
        }
    };

    const decisionOptions: { value: ReviewDecision; label: string; description: string; color: string }[] = [
        { value: 'APPROVE', label: 'Approve', description: 'Content meets all requirements', color: 'green' },
        { value: 'REQUEST_CHANGES', label: 'Request Changes', description: 'Minor changes needed', color: 'yellow' },
        { value: 'REJECT', label: 'Reject', description: 'Content does not meet requirements', color: 'red' },
        { value: 'ESCALATE', label: 'Escalate', description: 'Needs higher-level review', color: 'purple' },
    ];

    return (
        <div className={clsx('rounded-lg border border-gray-200 bg-white', className)}>
            {/* Header */}
            <div className="border-b border-gray-200 px-6 py-4">
                <div className="flex items-center justify-between">
                    <div>
                        <h3 className="text-lg font-semibold text-gray-900">Content Review</h3>
                        <p className="text-sm text-gray-500">{experienceTitle}</p>
                    </div>
                    <div className="flex items-center gap-2">
                        <RiskBadge riskLevel={riskLevel} />
                        <span className="rounded-full bg-blue-100 px-2 py-0.5 text-xs font-medium text-blue-800">
                            {currentUserRole}
                        </span>
                    </div>
                </div>
                {triggerReason && (
                    <div className="mt-2 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-700">
                        <strong>Review Trigger:</strong> {triggerReason.replace(/_/g, ' ')}
                    </div>
                )}
            </div>

            {/* Previous Decisions */}
            {previousDecisions.length > 0 && (
                <div className="border-b border-gray-200 px-6 py-4">
                    <h4 className="mb-3 text-sm font-medium text-gray-700">Previous Decisions</h4>
                    <div className="space-y-2">
                        {previousDecisions.map((d) => (
                            <div key={d.id} className="rounded-md bg-gray-50 px-3 py-2 text-sm">
                                <div className="flex items-center justify-between">
                                    <span className="font-medium text-gray-700">{d.reviewerRole}</span>
                                    <span className={clsx(
                                        'rounded-full px-2 py-0.5 text-xs font-medium',
                                        d.decision === 'APPROVE' && 'bg-green-100 text-green-800',
                                        d.decision === 'REJECT' && 'bg-red-100 text-red-800',
                                        d.decision === 'REQUEST_CHANGES' && 'bg-yellow-100 text-yellow-800',
                                        d.decision === 'ESCALATE' && 'bg-purple-100 text-purple-800'
                                    )}>
                                        {d.decision}
                                    </span>
                                </div>
                                <p className="mt-1 text-gray-600">{d.rationale}</p>
                            </div>
                        ))}
                    </div>
                </div>
            )}

            {/* Decision Form */}
            <div className="px-6 py-4">
                <h4 className="mb-3 text-sm font-medium text-gray-700">Your Decision</h4>

                {/* Decision Options */}
                <div className="mb-4 grid grid-cols-2 gap-2">
                    {decisionOptions.map((option) => (
                        <button
                            key={option.value}
                            onClick={() => setDecision(option.value)}
                            className={clsx(
                                'rounded-md border-2 px-3 py-2 text-left transition-colors',
                                decision === option.value
                                    ? option.color === 'green' ? 'border-green-500 bg-green-50'
                                        : option.color === 'yellow' ? 'border-yellow-500 bg-yellow-50'
                                            : option.color === 'red' ? 'border-red-500 bg-red-50'
                                                : 'border-purple-500 bg-purple-50'
                                    : 'border-gray-200 hover:border-gray-300'
                            )}
                        >
                            <div className="text-sm font-medium text-gray-900">{option.label}</div>
                            <div className="text-xs text-gray-500">{option.description}</div>
                        </button>
                    ))}
                </div>

                {/* Rationale */}
                <div className="mb-4">
                    <label className="mb-1 block text-sm font-medium text-gray-700">
                        Rationale <span className="text-red-500">*</span>
                    </label>
                    <textarea
                        value={rationale}
                        onChange={(e) => setRationale(e.target.value)}
                        placeholder="Explain your decision (minimum 10 characters)..."
                        className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                        rows={3}
                    />
                    <p className="mt-1 text-xs text-gray-500">
                        {rationale.length}/10 characters minimum
                    </p>
                </div>

                {/* Error */}
                {error && (
                    <div className="mb-4 rounded-md bg-red-50 p-3 text-sm text-red-700">
                        {error}
                    </div>
                )}

                {/* Submit */}
                <button
                    onClick={handleSubmit}
                    disabled={isSubmitting || !decision || rationale.length < 10}
                    className={clsx(
                        'w-full rounded-md px-4 py-2 text-sm font-medium transition-colors',
                        isSubmitting || !decision || rationale.length < 10
                            ? 'cursor-not-allowed bg-gray-100 text-gray-400'
                            : 'bg-blue-600 text-white hover:bg-blue-700'
                    )}
                >
                    {isSubmitting ? 'Submitting...' : 'Submit Decision'}
                </button>
            </div>
        </div>
    );
}

interface ReviewQueueListProps {
    items: ReviewQueueItem[];
    onItemSelect: (item: ReviewQueueItem) => void;
    className?: string;
}

export function ReviewQueueList({ items, onItemSelect, className }: ReviewQueueListProps) {
    if (items.length === 0) {
        return (
            <div className={clsx('rounded-lg border border-gray-200 bg-white p-8 text-center', className)}>
                <svg className="mx-auto h-12 w-12 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <h3 className="mt-2 text-sm font-medium text-gray-900">No items in review queue</h3>
                <p className="mt-1 text-sm text-gray-500">All content has been reviewed.</p>
            </div>
        );
    }

    return (
        <div className={clsx('rounded-lg border border-gray-200 bg-white', className)}>
            <div className="border-b border-gray-200 px-6 py-4">
                <h3 className="text-lg font-semibold text-gray-900">Review Queue</h3>
                <p className="text-sm text-gray-500">{items.length} items pending review</p>
            </div>
            <ul className="divide-y divide-gray-200">
                {items.map((item) => (
                    <li key={item.id}>
                        <button
                            onClick={() => onItemSelect(item)}
                            className="flex w-full items-center justify-between px-6 py-4 text-left hover:bg-gray-50"
                        >
                            <div>
                                <div className="font-medium text-gray-900">{item.experienceTitle}</div>
                                <div className="text-sm text-gray-500">
                                    Queued {new Date(item.queuedAt).toLocaleDateString()}
                                </div>
                            </div>
                            <div className="flex items-center gap-2">
                                <RiskBadge riskLevel={item.riskLevel} size="sm" />
                                <svg className="h-5 w-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                                </svg>
                            </div>
                        </button>
                    </li>
                ))}
            </ul>
        </div>
    );
}
