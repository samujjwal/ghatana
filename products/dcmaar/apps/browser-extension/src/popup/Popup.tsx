/**
 * @fileoverview Guardian Popup Component (Minimal UX)
 *
 * Minimal popup showing:
 * - Current domain status (allowed/blocked/limited)
 * - Usage for this domain today
 * - Two actions: Block this domain, View details
 */

import React, { useEffect, useState } from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserMessageRouter } from '@ghatana/dcmaar-browser-extension-core';
import browser from 'webextension-polyfill';
import '../styles/globals.css';
import { StatusBadge, MonitoringStatus } from '../components/shared/StatusBadge';
import { ActionButton } from '../components/shared/ActionButton';
import type {
    AccessStatus,
    PolicyEvaluationResult,
    DomainUsageToday,
    LastBlockedEvent,
} from '../types';
import { extractDomain, formatDuration, formatRelativeTime } from '../types';

// ============================================================================
// Popup State
// ============================================================================

interface PopupState {
    loading: boolean;
    error: string | null;
    currentUrl: string | null;
    currentDomain: string | null;
    policyResult: PolicyEvaluationResult | null;
    usageToday: DomainUsageToday | null;
    lastBlocked: LastBlockedEvent | null;
    monitoringActive: boolean;
}

const initialState: PopupState = {
    loading: true,
    error: null,
    currentUrl: null,
    currentDomain: null,
    policyResult: null,
    usageToday: null,
    lastBlocked: null,
    monitoringActive: false,
};

// ============================================================================
// Popup Component
// ============================================================================

export function Popup() {
    const [state, setState] = useState<PopupState>(initialState);
    const [blocking, setBlocking] = useState(false);

    useEffect(() => {
        loadPopupData();
    }, []);

    const loadPopupData = async () => {
        try {
            const router = new BrowserMessageRouter();

            // Get active tab URL
            const tabs = await browser.tabs.query({ active: true, currentWindow: true });
            const currentUrl = tabs[0]?.url || null;
            const currentDomain = currentUrl ? extractDomain(currentUrl) : null;

            if (!currentUrl || !currentDomain || currentUrl.startsWith('chrome://') || currentUrl.startsWith('about:')) {
                setState({
                    ...initialState,
                    loading: false,
                    currentUrl,
                    currentDomain,
                    error: 'Cannot monitor this page',
                });
                return;
            }

            // Fetch data in parallel
            const [policyRes, usageRes, blockedRes, stateRes] = await Promise.all([
                router.sendToBackground({ type: 'EVALUATE_POLICY', payload: { url: currentUrl } }),
                router.sendToBackground({ type: 'GET_DOMAIN_USAGE_TODAY', payload: { domain: currentDomain } }),
                router.sendToBackground({ type: 'GET_LAST_BLOCKED_EVENT', payload: { url: currentUrl } }),
                router.sendToBackground({ type: 'GET_STATE', payload: {} }),
            ]);

            // Map policy decision to AccessStatus
            let status: AccessStatus = 'allowed';
            let reason: string | undefined;
            if (policyRes?.success && policyRes.data) {
                const decision = (policyRes.data as any).decision;
                if (decision === 'block') status = 'blocked';
                else if (decision === 'warn') status = 'limited';
                reason = (policyRes.data as any).reason;
            }

            setState({
                loading: false,
                error: null,
                currentUrl,
                currentDomain,
                policyResult: { status, reason, policyId: (policyRes?.data as any)?.policyId },
                usageToday: usageRes?.success ? (usageRes.data as DomainUsageToday) : null,
                lastBlocked: blockedRes?.success ? (blockedRes.data as LastBlockedEvent | null) : null,
                monitoringActive: stateRes?.success ? (stateRes.data as any)?.metricsCollecting ?? false : false,
            });
        } catch (error) {
            console.error('[Popup] Failed to load data:', error);
            setState({
                ...initialState,
                loading: false,
                error: 'Failed to load data',
            });
        }
    };

    const handleBlockDomain = async () => {
        if (!state.currentDomain || blocking) return;

        setBlocking(true);
        try {
            const router = new BrowserMessageRouter();
            const isCurrentlyBlocked = state.policyResult?.status === 'blocked';

            await router.sendToBackground({
                type: 'UPDATE_DOMAIN_POLICY',
                payload: {
                    domain: state.currentDomain,
                    action: isCurrentlyBlocked ? 'default' : 'block',
                },
            });

            // Reload data to reflect change
            await loadPopupData();
        } catch (error) {
            console.error('[Popup] Failed to update policy:', error);
        } finally {
            setBlocking(false);
        }
    };

    const openDashboard = () => {
        const dashboardUrl = browser.runtime.getURL('src/dashboard/index.html');
        const url = state.currentDomain
            ? `${dashboardUrl}?domain=${encodeURIComponent(state.currentDomain)}`
            : dashboardUrl;

        browser.tabs.create({ url });
        window.close();
    };

    // Loading state
    if (state.loading) {
        return (
            <div className="w-80 p-6 bg-white">
                <div className="flex items-center justify-center gap-2">
                    <div className="animate-spin rounded-full h-5 w-5 border-2 border-blue-600 border-t-transparent" />
                    <span className="text-sm text-gray-600">Loading...</span>
                </div>
            </div>
        );
    }

    // Error or unsupported page
    if (state.error || !state.currentDomain) {
        return (
            <div className="w-80 bg-white">
                <Header monitoringActive={state.monitoringActive} />
                <div className="p-4">
                    <p className="text-sm text-gray-500 text-center">
                        {state.error || 'No domain to monitor'}
                    </p>
                    <div className="mt-4">
                        <ActionButton variant="primary" fullWidth onClick={openDashboard}>
                            Open Dashboard
                        </ActionButton>
                    </div>
                </div>
            </div>
        );
    }

    const isBlocked = state.policyResult?.status === 'blocked';

    return (
        <div className="w-80 bg-white">
            {/* Header */}
            <Header monitoringActive={state.monitoringActive} />

            {/* Current Domain Section */}
            <div className="p-4 border-b border-gray-100">
                <div className="flex items-center justify-between mb-2">
                    <span className="text-base font-semibold text-gray-900 truncate flex-1">
                        {state.currentDomain}
                    </span>
                    <StatusBadge status={state.policyResult?.status || 'allowed'} size="md" />
                </div>

                {state.policyResult?.reason && (
                    <p className="text-xs text-gray-500 mb-2">{state.policyResult.reason}</p>
                )}

                {/* Usage today */}
                {state.usageToday && (
                    <div className="flex items-center gap-3 text-xs text-gray-600">
                        <span>{formatDuration(state.usageToday.timeMinutes)} today</span>
                        <span>•</span>
                        <span>{state.usageToday.visits} visits</span>
                    </div>
                )}

                {/* Last blocked info */}
                {isBlocked && state.lastBlocked && (
                    <div className="mt-2 p-2 bg-red-50 rounded text-xs text-red-700">
                        Last blocked {formatRelativeTime(state.lastBlocked.timestamp)}
                        {state.lastBlocked.reason && `: ${state.lastBlocked.reason}`}
                    </div>
                )}
            </div>

            {/* Actions */}
            <div className="p-4 space-y-2">
                <ActionButton
                    variant={isBlocked ? 'secondary' : 'danger'}
                    fullWidth
                    loading={blocking}
                    onClick={handleBlockDomain}
                >
                    {isBlocked ? 'Unblock this domain' : 'Block this domain'}
                </ActionButton>

                <ActionButton variant="primary" fullWidth onClick={openDashboard}>
                    View details
                </ActionButton>
            </div>
        </div>
    );
}

// ============================================================================
// Header Component
// ============================================================================

interface HeaderProps {
    monitoringActive: boolean;
}

function Header({ monitoringActive }: HeaderProps) {
    return (
        <div className="bg-gradient-to-r from-blue-600 to-indigo-600 text-white p-4">
            <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                    <span className="text-lg font-bold">Guardian</span>
                    <span className="text-blue-200 text-xs">Web Protection</span>
                </div>
                <div className="flex items-center gap-1.5">
                    <MonitoringStatus active={monitoringActive} size="sm" variant="onDark" />
                </div>
            </div>
        </div>
    );
}

// Mount the component only if we're not in a test environment
if (typeof document !== 'undefined' && document.getElementById('root') && !(import.meta as any).env?.VITEST) {
    const root = ReactDOM.createRoot(document.getElementById('root')!);
    root.render(<Popup />);
}
