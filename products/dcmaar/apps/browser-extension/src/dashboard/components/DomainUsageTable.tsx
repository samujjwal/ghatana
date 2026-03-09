/**
 * @fileoverview Domain Usage Table Component
 *
 * Displays domain-level usage data with actions for blocking/unblocking.
 * Primary view in the Dashboard.
 */

import React from 'react';
import { DomainUsageRow } from '../../components/shared/DomainRow';
import type { DomainUsageSummary } from '../../types';

export interface DomainUsageTableProps {
    domains: DomainUsageSummary[];
    selectedDomain: string | null;
    onSelectDomain: (domain: string) => void;
    onToggleBlock: (domain: string, block: boolean) => void;
}

export function DomainUsageTable({
    domains,
    selectedDomain,
    onSelectDomain,
    onToggleBlock,
}: DomainUsageTableProps) {
    if (domains.length === 0) {
        return (
            <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
                <h2 className="text-lg font-semibold text-gray-900 mb-4">Domain Usage</h2>
                <p className="text-sm text-gray-500 text-center py-8">
                    No domain usage data available yet.
                </p>
            </div>
        );
    }

    return (
        <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-4">
            <div className="flex items-center justify-between mb-4">
                <h2 className="text-lg font-semibold text-gray-900">Domain Usage</h2>
                <span className="text-xs text-gray-500">Last 7 days</span>
            </div>

            <div className="space-y-2">
                {domains.map((domain) => (
                    <DomainUsageRow
                        key={domain.domain}
                        domain={domain.domain}
                        timeMinutes={domain.timeLast7DaysMinutes}
                        visits={domain.visitsLast7Days}
                        blockedAttempts={domain.blockedAttempts}
                        contentRisk={domain.contentRisk}
                        status={domain.status}
                        selected={selectedDomain === domain.domain}
                        onSelect={() => onSelectDomain(domain.domain)}
                        onBlock={() => onToggleBlock(domain.domain, true)}
                        onUnblock={() => onToggleBlock(domain.domain, false)}
                    />
                ))}
            </div>
        </div>
    );
}
