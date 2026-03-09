/**
 * @fileoverview Page Usage Section Component
 *
 * Displays page-level usage for a selected domain.
 * Shows when a domain is selected from the DomainUsageTable.
 */

import React from 'react';
import { ActionButton } from '../../components/shared/ActionButton';
import { ContentRiskBadge } from '../../components/shared/StatusBadge';
import type { PageUsageSummary } from '../../types';
import { formatDuration, formatRelativeTime } from '../../types';
import browser from 'webextension-polyfill';

export interface PageUsageSectionProps {
    domain: string | null;
    pages: PageUsageSummary[];
    onBlockDomain: (domain: string) => void;
}

export function PageUsageSection({
    domain,
    pages,
    onBlockDomain,
}: PageUsageSectionProps) {
    if (!domain) {
        return (
            <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
                <h2 className="text-lg font-semibold text-gray-900 mb-4">Page Usage</h2>
                <p className="text-sm text-gray-500 text-center py-8">
                    Select a domain above to see page-level usage.
                </p>
            </div>
        );
    }

    // Filter pages for the selected domain
    const domainPages = pages.filter((p) => p.domain === domain);

    if (domainPages.length === 0) {
        return (
            <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
                <div className="flex items-center justify-between mb-4">
                    <h2 className="text-lg font-semibold text-gray-900">
                        Pages on <span className="text-blue-600">{domain}</span>
                    </h2>
                    <ActionButton
                        variant="danger"
                        size="sm"
                        onClick={() => onBlockDomain(domain)}
                    >
                        Block domain
                    </ActionButton>
                </div>
                <p className="text-sm text-gray-500 text-center py-8">
                    No page-level data available for this domain.
                </p>
            </div>
        );
    }

    const openPage = (url: string) => {
        browser.tabs.create({ url });
    };

    return (
        <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-4">
            <div className="flex items-center justify-between mb-4">
                <h2 className="text-lg font-semibold text-gray-900">
                    Pages on <span className="text-blue-600">{domain}</span>
                </h2>
                <ActionButton
                    variant="danger"
                    size="sm"
                    onClick={() => onBlockDomain(domain)}
                >
                    Block domain
                </ActionButton>
            </div>

            <div className="overflow-x-auto">
                <table className="w-full text-sm">
                    <thead>
                        <tr className="border-b border-gray-200">
                            <th className="text-left py-2 px-2 font-medium text-gray-600">Page</th>
                            <th className="text-right py-2 px-2 font-medium text-gray-600">Time</th>
                            <th className="text-right py-2 px-2 font-medium text-gray-600">Last Visit</th>
                            <th className="text-right py-2 px-2 font-medium text-gray-600">Blocked</th>
                            <th className="text-center py-2 px-2 font-medium text-gray-600">Chats</th>
                            <th className="text-center py-2 px-2 font-medium text-gray-600">Risk</th>
                            <th className="text-right py-2 px-2 font-medium text-gray-600">Action</th>
                        </tr>
                    </thead>
                    <tbody>
                        {domainPages.map((page, index) => (
                            <tr
                                key={`${page.url}-${index}`}
                                className="border-b border-gray-100 hover:bg-gray-50"
                            >
                                <td className="py-2 px-2">
                                    <div className="max-w-xs truncate" title={page.url}>
                                        {page.title || page.url}
                                    </div>
                                </td>
                                <td className="py-2 px-2 text-right text-gray-600">
                                    {formatDuration(page.timeLast7DaysMinutes)}
                                </td>
                                <td className="py-2 px-2 text-right text-gray-500 text-xs">
                                    {formatRelativeTime(page.lastVisited)}
                                </td>
                                <td className="py-2 px-2 text-right">
                                    {page.blockedAttempts > 0 ? (
                                        <span className="text-red-600">{page.blockedAttempts}</span>
                                    ) : (
                                        <span className="text-gray-400">0</span>
                                    )}
                                </td>
                                <td className="py-2 px-2 text-center">
                                    {page.hasChatsOrPosts ? (
                                        <span className="text-blue-600">Yes</span>
                                    ) : (
                                        <span className="text-gray-400">No</span>
                                    )}
                                </td>
                                <td className="py-2 px-2 text-center">
                                    <ContentRiskBadge risk={page.contentRisk} size="sm" />
                                </td>
                                <td className="py-2 px-2 text-right">
                                    <ActionButton
                                        variant="ghost"
                                        size="sm"
                                        onClick={() => openPage(page.url)}
                                    >
                                        Open
                                    </ActionButton>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </div>
    );
}
