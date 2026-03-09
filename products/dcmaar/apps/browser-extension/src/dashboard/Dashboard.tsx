/**
 * @fileoverview Guardian Dashboard (Minimal UX)
 *
 * Simplified dashboard showing domain usage, page usage, blocked events,
 * and content analysis using shared components.
 */

import React, { useEffect, useState } from 'react';
import { BrowserMessageRouter } from '@ghatana/dcmaar-browser-extension-core';
import browser from 'webextension-polyfill';
import '../styles/globals.css';
import { MonitoringStatus } from '../components/shared/StatusBadge';
import { ActionButton } from '../components/shared/ActionButton';
import { StatsCard, StatsCardGrid } from '../components/analytics/StatsCard';
import {
    DomainUsageTable,
    PageUsageSection,
    BlockedPagesLog,
    ContentAnalysisSummary,
} from './components';
import type {
    DomainUsageSummary,
    PageUsageSummary,
    BlockedEvent,
    ContentSummaryByDomain,
    ExtendedAnalyticsSummary,
} from '../types';
import { formatRelativeTime, formatDuration } from '../types';

interface DashboardState {
    loading: boolean;
    error: string | null;
    monitoringActive: boolean;
    domains: DomainUsageSummary[];
    pages: PageUsageSummary[];
    blockedEvents: BlockedEvent[];
    contentSummary: ContentSummaryByDomain[];
    selectedDomain: string | null;
    lastUpdated: number | null;
}

const initialState: DashboardState = {
    loading: true,
    error: null,
    monitoringActive: false,
    domains: [],
    pages: [],
    blockedEvents: [],
    contentSummary: [],
    selectedDomain: null,
    lastUpdated: null,
};

export function Dashboard() {
    const [state, setState] = useState<DashboardState>(initialState);

    useEffect(() => {
        const router = new BrowserMessageRouter();

        const fetchData = async (domainFromUrl: string | null, retries = 3) => {
            let lastError: any;
            
            for (let attempt = 0; attempt < retries; attempt++) {
                try {
                    // First, test if background script is responding at all
                    const pingRes = await router.sendToBackground({ type: 'PING', payload: {} });
                    console.debug('[Dashboard] PING response:', pingRes);
                    
                    if (!pingRes?.success) {
                        console.warn('[Dashboard] Background script not responding to PING');
                        if (attempt < retries - 1) {
                            await new Promise(resolve => setTimeout(resolve, 200 * (attempt + 1)));
                            continue;
                        }
                    }
                    
                    const [analyticsRes, stateRes] = await Promise.all([
                        router.sendToBackground({ type: 'GET_ANALYTICS', payload: {} }),
                        router.sendToBackground({ type: 'GET_STATE', payload: {} }),
                    ]);

                    console.debug('[Dashboard] Analytics response (attempt ' + (attempt + 1) + '):', analyticsRes);
                    console.debug('[Dashboard] State response:', stateRes);

                    if (!analyticsRes?.success || !analyticsRes.data) {
                        lastError = new Error(analyticsRes?.error || 'Failed to load analytics');
                        if (attempt < retries - 1) {
                            // Wait before retrying
                            await new Promise(resolve => setTimeout(resolve, 100 * (attempt + 1)));
                            continue;
                        }
                        throw lastError;
                    }

                    // Success! Use the data
                    const analytics = analyticsRes.data as Partial<ExtendedAnalyticsSummary>;
                    const monitoringActive =
                        stateRes?.success && stateRes.data
                            ? ((stateRes.data as any).metricsCollecting ?? false)
                            : false;

                    setState((prev) => {
                        const domains = analytics.domains ?? [];
                        const fallbackDomain =
                            domainFromUrl ||
                            prev.selectedDomain ||
                            (domains.length > 0 ? domains[0].domain : null);

                        return {
                            ...prev,
                            loading: false,
                            error: null,
                            monitoringActive,
                            domains: analytics.domains ?? [],
                            pages: analytics.pages ?? [],
                            blockedEvents: analytics.blockedEvents ?? [],
                            contentSummary: analytics.contentSummary ?? [],
                            selectedDomain: fallbackDomain,
                            lastUpdated: analytics.lastUpdated ?? Date.now(),
                        };
                    });
                    
                    return; // Success, exit retry loop
                } catch (error) {
                    lastError = error;
                    console.error('[Dashboard] Attempt ' + (attempt + 1) + ' failed:', error);
                    if (attempt === retries - 1) {
                        // Last attempt failed
                        setState((prev) => ({
                            ...prev,
                            loading: false,
                            error:
                                error instanceof Error
                                    ? error.message
                                    : 'Failed to load dashboard data',
                        }));
                    }
                }
            }
        };

        const params = new URLSearchParams(window.location.search);
        const domainParam = params.get('domain');

        void fetchData(domainParam);

        const interval = setInterval(() => {
            void fetchData(null);
        }, 30000);

        return () => clearInterval(interval);
    }, []);

    const handleSelectDomain = (domain: string) => {
        setState((prev) => ({
            ...prev,
            selectedDomain: domain,
        }));
    };

    const updateDomainPolicy = async (
        domain: string,
        action: 'block' | 'allow' | 'default' | 'temp-allow',
        durationMinutes?: number,
    ) => {
        try {
            const router = new BrowserMessageRouter();
            await router.sendToBackground({
                type: 'UPDATE_DOMAIN_POLICY',
                payload: { domain, action, durationMinutes },
            });

            const [analyticsRes, stateRes] = await Promise.all([
                router.sendToBackground({ type: 'GET_ANALYTICS', payload: {} }),
                router.sendToBackground({ type: 'GET_STATE', payload: {} }),
            ]);

            if (!analyticsRes?.success || !analyticsRes.data) {
                return;
            }

            const analytics = analyticsRes.data as ExtendedAnalyticsSummary;
            const monitoringActive =
                stateRes?.success && stateRes.data
                    ? ((stateRes.data as any).metricsCollecting ?? false)
                    : false;

            setState((prev) => ({
                ...prev,
                loading: false,
                error: null,
                monitoringActive,
                domains: analytics.domains ?? [],
                pages: analytics.pages ?? [],
                blockedEvents: analytics.blockedEvents ?? [],
                contentSummary: analytics.contentSummary ?? [],
                selectedDomain: domain,
                lastUpdated: analytics.lastUpdated ?? Date.now(),
            }));
        } catch (error) {
            console.error('[Dashboard] Failed to update domain policy:', error);
        }
    };

    const handleToggleBlock = (domain: string, block: boolean) => {
        void updateDomainPolicy(domain, block ? 'block' : 'default');
    };

    const handleBlockFromPages = (domain: string) => {
        void updateDomainPolicy(domain, 'block');
    };

    const handleAllowDomain = (domain: string) => {
        void updateDomainPolicy(domain, 'allow');
    };

    const handleTempAllowDomain = (domain: string) => {
        void updateDomainPolicy(domain, 'temp-allow', 30);
    };

    const openSettings = () => {
        const url = browser.runtime.getURL('src/options/index.html');
        window.location.href = url;
    };

    const domains = Array.isArray(state.domains) ? state.domains : [];
    const blockedEvents = Array.isArray(state.blockedEvents) ? state.blockedEvents : [];
    const contentSummary = Array.isArray(state.contentSummary) ? state.contentSummary : [];

    const totalTimeLast7Days = domains.reduce(
        (sum, domain) => sum + domain.timeLast7DaysMinutes,
        0,
    );
    const blockedCount = blockedEvents.length;
    const highRiskDomains = contentSummary.filter(
        (summary) => summary.risk === 'high',
    ).length;

    if (state.loading) {
        return (
            <div className="min-h-screen flex items-center justify-center bg-gray-50">
                <div className="flex items-center gap-3 text-gray-600">
                    <div className="h-6 w-6 border-2 border-blue-600 border-t-transparent rounded-full animate-spin" />
                    <span className="text-sm">Loading dashboard...</span>
                </div>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-gray-50">
            <header className="bg-white border-b border-gray-200">
                <div className="max-w-6xl mx-auto px-4 py-3 flex items-center justify-between">
                    <div>
                        <h1 className="text-lg font-semibold text-gray-900">Guardian Dashboard</h1>
                        {state.lastUpdated && (
                            <p className="text-xs text-gray-500">
                                Updated {formatRelativeTime(state.lastUpdated)}
                            </p>
                        )}
                    </div>
                    <div className="flex items-center gap-3">
                        <div className="bg-white px-2 py-1 rounded-full border border-gray-200">
                            <MonitoringStatus active={state.monitoringActive} size="sm" />
                        </div>
                        <ActionButton variant="secondary" size="sm" onClick={openSettings}>
                            Settings
                        </ActionButton>
                    </div>
                </div>
            </header>

            {state.error && (
                <div className="max-w-6xl mx-auto mt-4 px-4">
                    <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded-lg p-3">
                        {state.error}
                    </div>
                </div>
            )}

            <main className="max-w-6xl mx-auto px-4 py-4 space-y-4">
                <section>
                    <StatsCardGrid>
                        <StatsCard
                            title="Time last 7 days"
                            value={formatDuration(totalTimeLast7Days)}
                            subtitle="Across monitored domains"
                            icon="⏱️"
                        />
                        <StatsCard
                            title="Blocked pages"
                            value={blockedCount}
                            subtitle="Recent blocked events"
                            icon="🚫"
                            variant="danger"
                        />
                        <StatsCard
                            title="High-risk domains"
                            value={highRiskDomains}
                            subtitle="With flagged content"
                            icon="⚠️"
                            variant="warning"
                        />
                    </StatsCardGrid>
                </section>
                <section className="grid gap-4 lg:grid-cols-3">
                    <div className="lg:col-span-2">
                        <DomainUsageTable
                            domains={state.domains}
                            selectedDomain={state.selectedDomain}
                            onSelectDomain={handleSelectDomain}
                            onToggleBlock={handleToggleBlock}
                        />
                    </div>
                    <div>
                        <BlockedPagesLog
                            events={
                                state.selectedDomain
                                    ? state.blockedEvents.filter(
                                        (event) => event.domain === state.selectedDomain,
                                    )
                                    : state.blockedEvents
                            }
                            onAllow={handleAllowDomain}
                            onTempAllow={handleTempAllowDomain}
                        />
                    </div>
                </section>

                <section className="grid gap-4 lg:grid-cols-3">
                    <div className="lg:col-span-2">
                        <PageUsageSection
                            domain={state.selectedDomain}
                            pages={state.pages}
                            onBlockDomain={handleBlockFromPages}
                        />
                    </div>
                    <div>
                        <ContentAnalysisSummary
                            contentSummary={state.contentSummary}
                            onSelectDomain={handleSelectDomain}
                        />
                    </div>
                </section>
            </main>
        </div>
    );
}
