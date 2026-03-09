/**
 * @fileoverview Content Analysis Summary Component
 *
 * Aggregated content analysis for domains with chat/messaging/posts.
 * Shows risk levels and flagged item counts.
 */

import React from 'react';
import { ContentRiskBadge } from '../../components/shared/StatusBadge';
import { ActionButton } from '../../components/shared/ActionButton';
import type { ContentSummaryByDomain } from '../../types';
import { formatRelativeTime } from '../../types';

export interface ContentAnalysisSummaryProps {
    contentSummary: ContentSummaryByDomain[];
    onSelectDomain: (domain: string) => void;
}

export function ContentAnalysisSummary({
    contentSummary,
    onSelectDomain,
}: ContentAnalysisSummaryProps) {
    // Filter to only show domains with some content analysis
    const domainsWithContent = contentSummary.filter(
        (d) => d.messagesAnalyzed > 0 || Object.keys(d.flaggedByType).length > 0
    );

    if (domainsWithContent.length === 0) {
        return (
            <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
                <h2 className="text-lg font-semibold text-gray-900 mb-4">Content Analysis</h2>
                <p className="text-sm text-gray-500 text-center py-8">
                    No content analysis data available.
                </p>
            </div>
        );
    }

    return (
        <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-4">
            <div className="flex items-center justify-between mb-4">
                <h2 className="text-lg font-semibold text-gray-900">Content Analysis</h2>
                <span className="text-xs text-gray-500">
                    {domainsWithContent.length} domains with messaging
                </span>
            </div>

            <div className="space-y-3">
                {domainsWithContent.map((summary) => (
                    <ContentDomainCard
                        key={summary.domain}
                        summary={summary}
                        onViewDetails={() => onSelectDomain(summary.domain)}
                    />
                ))}
            </div>
        </div>
    );
}

// ============================================================================
// Content Domain Card
// ============================================================================

interface ContentDomainCardProps {
    summary: ContentSummaryByDomain;
    onViewDetails: () => void;
}

function ContentDomainCard({ summary, onViewDetails }: ContentDomainCardProps) {
    const totalFlagged = Object.values(summary.flaggedByType).reduce(
        (sum, count) => sum + count,
        0
    );

    return (
        <div className="p-3 rounded-lg border border-gray-100 hover:border-gray-200 transition-colors">
            <div className="flex items-center justify-between mb-2">
                <span className="text-sm font-medium text-gray-900">{summary.domain}</span>
                <ContentRiskBadge risk={summary.risk} size="sm" />
            </div>

            <div className="flex items-center gap-4 text-xs text-gray-600 mb-2">
                <span>{summary.messagesAnalyzed} messages analyzed</span>
                {totalFlagged > 0 && (
                    <span className="text-orange-600">{totalFlagged} flagged</span>
                )}
                {summary.lastFlaggedTime && (
                    <span>Last flagged {formatRelativeTime(summary.lastFlaggedTime)}</span>
                )}
            </div>

            {/* Flagged types breakdown */}
            {Object.keys(summary.flaggedByType).length > 0 && (
                <div className="flex flex-wrap gap-1 mb-2">
                    {Object.entries(summary.flaggedByType).map(([type, count]) => (
                        <span
                            key={type}
                            className="px-2 py-0.5 text-xs rounded-full bg-orange-100 text-orange-700"
                        >
                            {type}: {count}
                        </span>
                    ))}
                </div>
            )}

            <div className="flex justify-end">
                <ActionButton variant="ghost" size="sm" onClick={onViewDetails}>
                    View domain details
                </ActionButton>
            </div>
        </div>
    );
}
