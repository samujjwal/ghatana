import { Card, Button } from "@/components/ui";
import { useState } from "react";

/**
 * AI Insights Card Component - Displays AI-driven recommendations
 *
 * <p><b>Purpose</b><br>
 * Shows AI-generated insights with confidence score, reasoning, and HITL status.
 * Allows users to approve, defer, or reject recommendations.
 *
 * <p><b>Features</b><br>
 * - Confidence score display (0-1)
 * - Expandable reasoning text
 * - HITL status (pending/approved/rejected)
 * - Action buttons (Approve/Defer/Reject)
 * - Real-time status updates
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <InsightCard
 *   title="QA Pipeline Analysis"
 *   insight="15% flakiness detected in auth tests"
 *   confidence={0.92}
 *   reasoning="Based on 1000 test runs in last 7 days"
 *   onApprove={() => console.log('approved')}
 * />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose AI insight recommendation display
 * @doc.layer product
 * @doc.pattern Molecule
 */

interface InsightCardProps {
    title: string;
    insight: string;
    confidence: number;
    reasoning?: string;
    status?: "pending" | "approved" | "rejected";
    onApprove?: () => void;
    onDefer?: () => void;
    onReject?: () => void;
}

export function InsightCard({
    title,
    insight,
    confidence,
    reasoning,
    status = "pending",
    onApprove,
    onDefer,
    onReject,
}: InsightCardProps) {
    const [expanded, setExpanded] = useState(false);

    const statusColors = {
        pending: "bg-blue-50 dark:bg-indigo-600/30 border-blue-200 dark:border-blue-800",
        approved: "bg-green-50 dark:bg-green-600/30 border-green-200 dark:border-green-800",
        rejected: "bg-red-50 dark:bg-rose-600/30 border-red-200 dark:border-red-800",
    };

    const confidenceColor =
        confidence > 0.85
            ? "text-green-600 dark:text-green-400"
            : confidence > 0.7
                ? "text-yellow-600 dark:text-yellow-400"
                : "text-red-600 dark:text-rose-400";

    return (
        <Card padded={false}>
            <div className={`rounded-lg border p-6 ${statusColors[status]}`}>
                {/* Header */}
                <div className="flex justify-between items-start mb-4">
                    <div>
                        <p className="text-sm font-medium text-slate-600 dark:text-neutral-400">
                            🤖 AI Copilot
                        </p>
                        <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mt-1">
                            {title}
                        </h3>
                    </div>
                    <div className={`text-right ${confidenceColor}`}>
                        <p className="text-xs font-medium">Confidence</p>
                        <p className="text-lg font-bold">{(confidence * 100).toFixed(0)}%</p>
                    </div>
                </div>

                {/* Insight text */}
                <p className="text-sm text-slate-700 dark:text-neutral-300 mb-4">
                    {insight}
                </p>

                {/* Expandable reasoning */}
                {reasoning && (
                    <div className="mb-4">
                        <button
                            onClick={() => setExpanded(!expanded)}
                            className="text-xs font-semibold text-blue-600 dark:text-indigo-400 hover:underline"
                        >
                            {expanded ? "▼" : "▶"} Why?
                        </button>
                        {expanded && (
                            <p className="text-xs text-slate-600 dark:text-neutral-400 mt-2 ml-4 border-l-2 border-slate-300 dark:border-neutral-600 pl-3">
                                {reasoning}
                            </p>
                        )}
                    </div>
                )}

                {/* Status indicator */}
                <div className="text-xs font-medium text-slate-600 dark:text-neutral-400 mb-4">
                    Status:{" "}
                    <span
                        className={
                            status === "approved"
                                ? "text-green-600 dark:text-green-400"
                                : status === "rejected"
                                    ? "text-red-600 dark:text-rose-400"
                                    : "text-yellow-600 dark:text-yellow-400"
                        }
                    >
                        {status.charAt(0).toUpperCase() + status.slice(1)}
                    </span>
                </div>

                {/* Action buttons */}
                {status === "pending" && (
                    <div className="flex gap-2">
                        <Button
                            onClick={onApprove}
                            tone="success"
                            size="sm"
                            fullWidth
                        >
                            Approve
                        </Button>
                        <Button
                            onClick={onDefer}
                            variant="ghost"
                            tone="neutral"
                            size="sm"
                            fullWidth
                        >
                            Defer
                        </Button>
                        <Button
                            onClick={onReject}
                            tone="danger"
                            size="sm"
                            fullWidth
                        >
                            Reject
                        </Button>
                    </div>
                )}
            </div>
        </Card>
    );
}

export default InsightCard;
