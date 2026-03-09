/**
 * @fileoverview Minimal Settings Component
 *
 * Simplified settings with only four controls:
 * - Monitoring on/off
 * - Data retention (7/30/90 days)
 * - Alerts on/off
 * - Domain block/allow list
 */

import React, { useEffect, useState } from 'react';
import { BrowserMessageRouter } from '@ghatana/dcmaar-browser-extension-core';
import browser from 'webextension-polyfill';
import { ActionButton } from '../shared/ActionButton';
import { DomainPolicyRow } from '../shared/DomainRow';
import type { DomainPolicyStatus, MinimalGuardianSettings } from '../../types';

// ============================================================================
// Settings State
// ============================================================================

interface SettingsState {
    loading: boolean;
    saving: boolean;
    error: string | null;
    success: boolean;
    settings: MinimalGuardianSettings;
    newDomain: string;
}

const defaultSettings: MinimalGuardianSettings = {
    monitoringEnabled: true,
    dataRetentionDays: 7,
    alertsEnabled: true,
    domainPolicies: [],
};

const initialState: SettingsState = {
    loading: true,
    saving: false,
    error: null,
    success: false,
    settings: defaultSettings,
    newDomain: '',
};

// ============================================================================
// Backend Sync State
// ============================================================================

interface BackendSyncState {
    apiBaseUrl: string;
    deviceId: string;
    childId: string;
    syncEnabled: boolean;
    syncing: boolean;
    lastSyncTime: string | null;
    error: string | null;
    success: string | null;
}

const initialBackendSyncState: BackendSyncState = {
    apiBaseUrl: '',
    deviceId: '',
    childId: '',
    syncEnabled: false,
    syncing: false,
    lastSyncTime: null,
    error: null,
    success: null,
};

// ============================================================================
// Backend Sync Section Component
// ============================================================================

function BackendSyncSection() {
    const [state, setState] = useState<BackendSyncState>(initialBackendSyncState);

    useEffect(() => {
        // Load saved backend config from storage
        loadBackendConfig();
    }, []);

    const loadBackendConfig = async () => {
        try {
            const result = await browser.storage.local.get(['backendSyncConfig']);
            if (result.backendSyncConfig) {
                setState((prev) => ({
                    ...prev,
                    ...result.backendSyncConfig,
                }));
            }
        } catch (error) {
            console.error('[BackendSync] Failed to load config:', error);
        }
    };

    const saveBackendConfig = async () => {
        setState((prev) => ({ ...prev, error: null, success: null }));

        try {
            const config = {
                apiBaseUrl: state.apiBaseUrl,
                deviceId: state.deviceId,
                childId: state.childId,
                syncEnabled: state.syncEnabled,
            };

            // Save to local storage
            await browser.storage.local.set({ backendSyncConfig: config });

            // Configure the controller
            const router = new BrowserMessageRouter();
            const response = await router.sendToBackground({
                type: 'CONFIGURE_BACKEND_SYNC',
                payload: config,
            });

            if (response?.success) {
                setState((prev) => ({
                    ...prev,
                    success: 'Backend sync configured successfully',
                }));
            } else {
                setState((prev) => ({
                    ...prev,
                    error: response?.error || 'Failed to configure backend sync',
                }));
            }
        } catch (error) {
            console.error('[BackendSync] Failed to save config:', error);
            setState((prev) => ({
                ...prev,
                error: 'Failed to save backend sync configuration',
            }));
        }
    };

    const triggerManualSync = async () => {
        if (!state.syncEnabled || !state.apiBaseUrl || !state.deviceId) {
            setState((prev) => ({
                ...prev,
                error: 'Please configure and enable backend sync first',
            }));
            return;
        }

        setState((prev) => ({ ...prev, syncing: true, error: null, success: null }));

        try {
            const router = new BrowserMessageRouter();
            const response = await router.sendToBackground({
                type: 'SYNC_TO_BACKEND',
                payload: {},
            });

            if (response?.success) {
                setState((prev) => ({
                    ...prev,
                    syncing: false,
                    lastSyncTime: new Date().toISOString(),
                    success: `Synced successfully: ${response.eventCount || 0} events sent`,
                }));
            } else {
                setState((prev) => ({
                    ...prev,
                    syncing: false,
                    error: response?.error || 'Sync failed',
                }));
            }
        } catch (error) {
            console.error('[BackendSync] Sync failed:', error);
            setState((prev) => ({
                ...prev,
                syncing: false,
                error: 'Failed to sync data to backend',
            }));
        }
    };

    return (
        <section className="settings-section">
            <h3 className="text-lg font-medium text-gray-900 dark:text-gray-100 mb-4">
                Backend Sync
            </h3>
            <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">
                Configure synchronization with the Guardian backend server.
            </p>

            {/* API Base URL */}
            <div className="mb-4">
                <label
                    htmlFor="apiBaseUrl"
                    className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1"
                >
                    API Base URL
                </label>
                <input
                    type="url"
                    id="apiBaseUrl"
                    value={state.apiBaseUrl}
                    onChange={(e) =>
                        setState((prev) => ({ ...prev, apiBaseUrl: e.target.value }))
                    }
                    placeholder="http://localhost:3001"
                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-700 dark:text-white"
                />
            </div>

            {/* Device ID */}
            <div className="mb-4">
                <label
                    htmlFor="deviceId"
                    className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1"
                >
                    Device ID
                </label>
                <input
                    type="text"
                    id="deviceId"
                    value={state.deviceId}
                    onChange={(e) =>
                        setState((prev) => ({ ...prev, deviceId: e.target.value }))
                    }
                    placeholder="unique-device-identifier"
                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-700 dark:text-white"
                />
            </div>

            {/* Child ID */}
            <div className="mb-4">
                <label
                    htmlFor="childId"
                    className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1"
                >
                    Child ID
                </label>
                <input
                    type="text"
                    id="childId"
                    value={state.childId}
                    onChange={(e) =>
                        setState((prev) => ({ ...prev, childId: e.target.value }))
                    }
                    placeholder="child-profile-id"
                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-700 dark:text-white"
                />
            </div>

            {/* Enable Sync Toggle */}
            <div className="flex items-center justify-between mb-4">
                <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
                    Enable Backend Sync
                </span>
                <button
                    type="button"
                    onClick={() =>
                        setState((prev) => ({ ...prev, syncEnabled: !prev.syncEnabled }))
                    }
                    className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
                        state.syncEnabled ? 'bg-blue-600' : 'bg-gray-300 dark:bg-gray-600'
                    }`}
                >
                    <span
                        className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
                            state.syncEnabled ? 'translate-x-6' : 'translate-x-1'
                        }`}
                    />
                </button>
            </div>

            {/* Action Buttons */}
            <div className="flex gap-3 mb-4">
                <ActionButton
                    onClick={saveBackendConfig}
                    className="flex-1"
                >
                    Save Configuration
                </ActionButton>
                <ActionButton
                    onClick={triggerManualSync}
                    disabled={state.syncing || !state.syncEnabled}
                    className="flex-1"
                >
                    {state.syncing ? 'Syncing...' : 'Sync Now'}
                </ActionButton>
            </div>

            {/* Status Messages */}
            {state.error && (
                <div className="p-3 rounded-md bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-300 text-sm mb-2">
                    {state.error}
                </div>
            )}
            {state.success && (
                <div className="p-3 rounded-md bg-green-50 dark:bg-green-900/20 text-green-700 dark:text-green-300 text-sm mb-2">
                    {state.success}
                </div>
            )}
            {state.lastSyncTime && (
                <div className="text-xs text-gray-500 dark:text-gray-400">
                    Last synced: {new Date(state.lastSyncTime).toLocaleString()}
                </div>
            )}
        </section>
    );
}

// ============================================================================
// Minimal Settings Component
// ============================================================================

export function Settings() {
    const [state, setState] = useState<SettingsState>(initialState);

    useEffect(() => {
        loadSettings();
    }, []);

    const loadSettings = async () => {
        try {
            const router = new BrowserMessageRouter();
            const response = await router.sendToBackground({
                type: 'GET_MINIMAL_SETTINGS',
                payload: {},
            });

            if (response?.success && response.data) {
                setState((prev) => ({
                    ...prev,
                    loading: false,
                    settings: response.data as MinimalGuardianSettings,
                }));
            } else {
                setState((prev) => ({
                    ...prev,
                    loading: false,
                    error: response?.error || 'Failed to load settings',
                }));
            }
        } catch (error) {
            console.error('[Settings] Failed to load:', error);
            setState((prev) => ({
                ...prev,
                loading: false,
                error: 'Failed to load settings',
            }));
        }
    };

    const saveSettings = async () => {
        setState((prev) => ({ ...prev, saving: true, error: null, success: false }));

        try {
            const router = new BrowserMessageRouter();
            const response = await router.sendToBackground({
                type: 'SAVE_MINIMAL_SETTINGS',
                payload: state.settings,
            });

            if (response?.success) {
                setState((prev) => ({ ...prev, saving: false, success: true }));
                setTimeout(() => setState((prev) => ({ ...prev, success: false })), 3000);
            } else {
                setState((prev) => ({
                    ...prev,
                    saving: false,
                    error: response?.error || 'Failed to save settings',
                }));
            }
        } catch (error) {
            console.error('[Settings] Failed to save:', error);
            setState((prev) => ({
                ...prev,
                saving: false,
                error: 'Failed to save settings',
            }));
        }
    };

    const updateSetting = <K extends keyof MinimalGuardianSettings>(
        key: K,
        value: MinimalGuardianSettings[K]
    ) => {
        setState((prev) => ({
            ...prev,
            settings: { ...prev.settings, [key]: value },
        }));
    };

    const updateDomainPolicy = (domain: string, status: DomainPolicyStatus) => {
        setState((prev) => {
            const policies = prev.settings.domainPolicies.map((p) =>
                p.domain === domain ? { ...p, status } : p
            );
            return {
                ...prev,
                settings: { ...prev.settings, domainPolicies: policies },
            };
        });
    };

    const removeDomainPolicy = (domain: string) => {
        setState((prev) => ({
            ...prev,
            settings: {
                ...prev.settings,
                domainPolicies: prev.settings.domainPolicies.filter((p) => p.domain !== domain),
            },
        }));
    };

    const addDomainPolicy = () => {
        const domain = state.newDomain.trim().toLowerCase();
        if (!domain) return;

        // Check if already exists
        if (state.settings.domainPolicies.some((p) => p.domain === domain)) {
            setState((prev) => ({ ...prev, error: 'Domain already in list' }));
            return;
        }

        setState((prev) => ({
            ...prev,
            newDomain: '',
            settings: {
                ...prev.settings,
                domainPolicies: [
                    ...prev.settings.domainPolicies,
                    { domain, status: 'blocked' as DomainPolicyStatus },
                ],
            },
        }));
    };

    const openDashboard = () => {
        const dashboardUrl = browser.runtime.getURL('src/dashboard/index.html');
        window.location.href = dashboardUrl;
    };

    // Loading state
    if (state.loading) {
        return (
            <div className="min-h-screen flex items-center justify-center bg-gray-50">
                <div className="animate-spin rounded-full h-6 w-6 border-2 border-blue-600 border-t-transparent" />
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-gray-50">
            <header className="bg-white border-b border-gray-200">
                <div className="max-w-6xl mx-auto px-4 py-3 flex items-center justify-between">
                    <div>
                        <h1 className="text-lg font-semibold text-gray-900">Guardian Settings</h1>
                        <p className="text-xs text-gray-500 mt-0.5">Configure Guardian for this browser</p>
                    </div>
                    <ActionButton variant="secondary" size="sm" onClick={openDashboard}>
                        Open Dashboard
                    </ActionButton>
                </div>
            </header>

            <main className="max-w-6xl mx-auto px-4 py-4 space-y-8">
                {/* Error/Success Messages */}
                {state.error && (
                    <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 text-sm">
                        {state.error}
                    </div>
                )}
                {state.success && (
                    <div className="bg-green-50 border border-green-200 rounded-lg p-4 text-green-700 text-sm">
                        Settings saved successfully
                    </div>
                )}

                {/* Monitoring Toggle */}
                <section className="bg-white rounded-lg border border-gray-200 p-6">
                    <div className="flex items-center justify-between">
                        <div>
                            <h2 className="text-lg font-semibold text-gray-900">Monitoring</h2>
                            <p className="text-sm text-gray-500 mt-1">
                                Track browsing activity on this browser
                            </p>
                        </div>
                        <ToggleSwitch
                            checked={state.settings.monitoringEnabled}
                            onChange={(checked) => updateSetting('monitoringEnabled', checked)}
                        />
                    </div>
                </section>

                {/* Data Retention */}
                <section className="bg-white rounded-lg border border-gray-200 p-6">
                    <h2 className="text-lg font-semibold text-gray-900 mb-2">Data Retention</h2>
                    <p className="text-sm text-gray-500 mb-4">
                        How long to keep browsing history
                    </p>
                    <select
                        value={state.settings.dataRetentionDays}
                        onChange={(e) => updateSetting('dataRetentionDays', Number(e.target.value) as 7 | 30 | 90)}
                        className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    >
                        <option value={7}>7 days</option>
                        <option value={30}>30 days</option>
                        <option value={90}>90 days</option>
                    </select>
                </section>

                {/* Alerts Toggle */}
                <section className="bg-white rounded-lg border border-gray-200 p-6">
                    <div className="flex items-center justify-between">
                        <div>
                            <h2 className="text-lg font-semibold text-gray-900">Alerts</h2>
                            <p className="text-sm text-gray-500 mt-1">
                                Show alerts for blocked pages and high-risk content
                            </p>
                        </div>
                        <ToggleSwitch
                            checked={state.settings.alertsEnabled}
                            onChange={(checked) => updateSetting('alertsEnabled', checked)}
                        />
                    </div>
                </section>

                {/* Domain Block/Allow List */}
                <section className="bg-white rounded-lg border border-gray-200 p-6">
                    <h2 className="text-lg font-semibold text-gray-900 mb-2">Block / Allow List</h2>
                    <p className="text-sm text-gray-500 mb-4">
                        Manage which domains are blocked or allowed
                    </p>

                    {/* Add domain input */}
                    <div className="flex gap-2 mb-4">
                        <input
                            type="text"
                            value={state.newDomain}
                            onChange={(e) => setState((prev) => ({ ...prev, newDomain: e.target.value }))}
                            onKeyDown={(e) => e.key === 'Enter' && addDomainPolicy()}
                            placeholder="Enter domain (e.g., example.com)"
                            className="flex-1 border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                        />
                        <ActionButton variant="primary" size="md" onClick={addDomainPolicy}>
                            Add
                        </ActionButton>
                    </div>

                    {/* Domain list */}
                    {state.settings.domainPolicies.length === 0 ? (
                        <p className="text-sm text-gray-500 text-center py-4">
                            No domains in the list. Add a domain above.
                        </p>
                    ) : (
                        <div className="space-y-2">
                            {state.settings.domainPolicies.map((policy) => (
                                <DomainPolicyRow
                                    key={policy.domain}
                                    domain={policy.domain}
                                    status={policy.status}
                                    onStatusChange={(status) => updateDomainPolicy(policy.domain, status)}
                                    onRemove={() => removeDomainPolicy(policy.domain)}
                                />
                            ))}
                        </div>
                    )}
                </section>

                {/* Backend Sync Section */}
                <BackendSyncSection />

                {/* Save Button */}
                <div className="flex justify-end">
                    <ActionButton
                        variant="primary"
                        size="lg"
                        loading={state.saving}
                        onClick={saveSettings}
                    >
                        Save Settings
                    </ActionButton>
                </div>
            </main>
        </div>
    );
}

// ============================================================================
// Toggle Switch Component
// ============================================================================

interface ToggleSwitchProps {
    checked: boolean;
    onChange: (checked: boolean) => void;
    disabled?: boolean;
}

function ToggleSwitch({ checked, onChange, disabled = false }: ToggleSwitchProps) {
    return (
        <button
            type="button"
            role="switch"
            aria-checked={checked}
            disabled={disabled}
            onClick={() => onChange(!checked)}
            className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 ${checked ? 'bg-blue-600' : 'bg-gray-200'
                } ${disabled ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
        >
            <span
                className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${checked ? 'translate-x-6' : 'translate-x-1'
                    }`}
            />
        </button>
    );
}
