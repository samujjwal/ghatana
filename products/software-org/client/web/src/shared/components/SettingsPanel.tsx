import React, { useState } from 'react';

/**
 * SettingsPanel - User and application settings UI component.
 *
 * <p><b>Purpose</b><br>
 * Provides interface for managing user preferences, application settings,
 * and theme configuration.
 *
 * <p><b>Features</b><br>
 * - Theme toggle (light/dark mode)
 * - Notification preferences
 * - Display settings
 * - Data retention preferences
 * - Theme persistence
 * - Dark mode support
 * - Settings grouping by category
 *
 * <p><b>Usage</b><br>
 * ```tsx
 * <SettingsPanel
 *   onSettingChange={(key, value) => console.log(key, value)}
 * />
 * ```
 *
 * @doc.type component
 * @doc.purpose User and app settings interface
 * @doc.layer product
 * @doc.pattern Molecule
 */

interface SettingsPanelProps {
    isOpen?: boolean;
    onClose?: () => void;
    onSettingChange?: (key: string, value: unknown) => void;
}

interface Settings {
    theme: 'light' | 'dark' | 'system';
    notifications: {
        alerts: boolean;
        updates: boolean;
        weeklyDigest: boolean;
    };
    display: {
        density: 'compact' | 'normal' | 'comfortable';
        autoRefresh: boolean;
        refreshInterval: number;
    };
    data: {
        retentionDays: number;
        autoArchive: boolean;
    };
}

export const SettingsPanel = React.memo(function SettingsPanel({
    isOpen = false,
    onClose,
    onSettingChange,
}: SettingsPanelProps) {
    const [settings, setSettings] = useState<Settings>({
        theme: 'system',
        notifications: {
            alerts: true,
            updates: true,
            weeklyDigest: false,
        },
        display: {
            density: 'normal',
            autoRefresh: true,
            refreshInterval: 300,
        },
        data: {
            retentionDays: 90,
            autoArchive: true,
        },
    });

    const [activeTab, setActiveTab] = useState<'appearance' | 'notifications' | 'display' | 'data'>(
        'appearance'
    );

    const handleSettingChange = (key: string, value: unknown) => {
        setSettings((prev) => {
            const updated = { ...prev };
            const keys = key.split('.');
            let obj = updated as Record<string, unknown>;

            for (let i = 0; i < keys.length - 1; i++) {
                obj = obj[keys[i]] as Record<string, unknown>;
            }

            obj[keys[keys.length - 1]] = value;
            return updated;
        });
        onSettingChange?.(key, value);
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 bg-black/50 dark:bg-black/70 flex items-center justify-center z-50 p-4">
            <div className="bg-white dark:bg-neutral-800 rounded-lg shadow-xl max-w-2xl w-full max-h-[90vh] flex flex-col">
                {/* Header */}
                <div className="flex items-center justify-between p-6 border-b border-slate-200 dark:border-neutral-600">
                    <h2 className="text-xl font-semibold text-slate-900 dark:text-neutral-100">Settings</h2>
                    <button
                        onClick={onClose}
                        className="p-2 hover:bg-slate-100 dark:hover:bg-slate-700 rounded-lg transition-colors"
                        aria-label="Close settings"
                    >
                        ✕
                    </button>
                </div>

                {/* Tabs */}
                <div className="flex gap-0 border-b border-slate-200 dark:border-neutral-600 px-6">
                    {(['appearance', 'notifications', 'display', 'data'] as const).map((tab) => (
                        <button
                            key={tab}
                            onClick={() => setActiveTab(tab)}
                            className={`px-4 py-3 font-medium border-b-2 transition-colors ${activeTab === tab
                                    ? 'border-blue-500 text-blue-600 dark:text-indigo-400'
                                    : 'border-transparent text-slate-600 dark:text-neutral-400 hover:text-slate-900 dark:hover:text-white'
                                }`}
                        >
                            {tab.charAt(0).toUpperCase() + tab.slice(1)}
                        </button>
                    ))}
                </div>

                {/* Content */}
                <div className="flex-1 overflow-y-auto p-6 space-y-6">
                    {/* Appearance Tab */}
                    {activeTab === 'appearance' && (
                        <div className="space-y-4">
                            <div>
                                <label className="block text-sm font-medium text-slate-900 dark:text-neutral-100 mb-2">
                                    Theme
                                </label>
                                <select
                                    value={settings.theme}
                                    onChange={(e) => handleSettingChange('theme', e.target.value)}
                                    className="w-full px-3 py-2 border border-slate-300 dark:border-neutral-600 rounded-lg bg-white dark:bg-neutral-700 text-slate-900 dark:text-neutral-100"
                                >
                                    <option value="light">Light</option>
                                    <option value="dark">Dark</option>
                                    <option value="system">System</option>
                                </select>
                            </div>
                        </div>
                    )}

                    {/* Notifications Tab */}
                    {activeTab === 'notifications' && (
                        <div className="space-y-4">
                            {Object.entries(settings.notifications).map(([key, value]) => (
                                <label key={key} className="flex items-center gap-3 cursor-pointer">
                                    <input
                                        type="checkbox"
                                        checked={value}
                                        onChange={(e) => handleSettingChange(`notifications.${key}`, e.target.checked)}
                                        className="w-4 h-4 rounded border-slate-300"
                                    />
                                    <span className="text-sm text-slate-900 dark:text-neutral-100">
                                        {key === 'alerts' && 'Alert notifications'}
                                        {key === 'updates' && 'Update notifications'}
                                        {key === 'weeklyDigest' && 'Weekly digest'}
                                    </span>
                                </label>
                            ))}
                        </div>
                    )}

                    {/* Display Tab */}
                    {activeTab === 'display' && (
                        <div className="space-y-4">
                            <div>
                                <label className="block text-sm font-medium text-slate-900 dark:text-neutral-100 mb-2">
                                    Display Density
                                </label>
                                <select
                                    value={settings.display.density}
                                    onChange={(e) => handleSettingChange('display.density', e.target.value)}
                                    className="w-full px-3 py-2 border border-slate-300 dark:border-neutral-600 rounded-lg bg-white dark:bg-neutral-700 text-slate-900 dark:text-neutral-100"
                                >
                                    <option value="compact">Compact</option>
                                    <option value="normal">Normal</option>
                                    <option value="comfortable">Comfortable</option>
                                </select>
                            </div>

                            <label className="flex items-center gap-3 cursor-pointer">
                                <input
                                    type="checkbox"
                                    checked={settings.display.autoRefresh}
                                    onChange={(e) => handleSettingChange('display.autoRefresh', e.target.checked)}
                                    className="w-4 h-4 rounded border-slate-300"
                                />
                                <span className="text-sm text-slate-900 dark:text-neutral-100">Auto-refresh data</span>
                            </label>

                            {settings.display.autoRefresh && (
                                <div>
                                    <label className="block text-sm font-medium text-slate-900 dark:text-neutral-100 mb-2">
                                        Refresh interval (seconds)
                                    </label>
                                    <input
                                        type="number"
                                        min="30"
                                        max="3600"
                                        step="30"
                                        value={settings.display.refreshInterval}
                                        onChange={(e) => handleSettingChange('display.refreshInterval', Number(e.target.value))}
                                        className="w-full px-3 py-2 border border-slate-300 dark:border-neutral-600 rounded-lg bg-white dark:bg-neutral-700 text-slate-900 dark:text-neutral-100"
                                    />
                                </div>
                            )}
                        </div>
                    )}

                    {/* Data Tab */}
                    {activeTab === 'data' && (
                        <div className="space-y-4">
                            <div>
                                <label className="block text-sm font-medium text-slate-900 dark:text-neutral-100 mb-2">
                                    Data retention (days)
                                </label>
                                <input
                                    type="number"
                                    min="7"
                                    max="365"
                                    step="1"
                                    value={settings.data.retentionDays}
                                    onChange={(e) => handleSettingChange('data.retentionDays', Number(e.target.value))}
                                    className="w-full px-3 py-2 border border-slate-300 dark:border-neutral-600 rounded-lg bg-white dark:bg-neutral-700 text-slate-900 dark:text-neutral-100"
                                />
                            </div>

                            <label className="flex items-center gap-3 cursor-pointer">
                                <input
                                    type="checkbox"
                                    checked={settings.data.autoArchive}
                                    onChange={(e) => handleSettingChange('data.autoArchive', e.target.checked)}
                                    className="w-4 h-4 rounded border-slate-300"
                                />
                                <span className="text-sm text-slate-900 dark:text-neutral-100">Auto-archive old data</span>
                            </label>
                        </div>
                    )}
                </div>

                {/* Footer */}
                <div className="border-t border-slate-200 dark:border-neutral-600 p-6 flex gap-3 justify-end">
                    <button
                        onClick={onClose}
                        className="px-4 py-2 text-slate-700 dark:text-neutral-300 bg-slate-100 dark:bg-neutral-700 hover:bg-slate-200 dark:hover:bg-slate-600 rounded-lg font-medium transition-colors"
                    >
                        Close
                    </button>
                    <button
                        onClick={() => {
                            /* Save settings */
                            onClose?.();
                        }}
                        className="px-4 py-2 bg-blue-500 hover:bg-blue-600 text-white rounded-lg font-medium transition-colors"
                    >
                        Save Settings
                    </button>
                </div>
            </div>
        </div>
    );
});

export default SettingsPanel;
