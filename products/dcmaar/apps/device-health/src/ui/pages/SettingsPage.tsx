import React, { useState, useEffect } from 'react';
import browser from 'webextension-polyfill';
import { Card, Toggle, Select, Button, Badge } from '@ghatana/dcmaar-shared-ui-tailwind';
import { DataRetentionSettings } from '../components/settings/DataRetentionSettings';

type Theme = 'light' | 'dark' | 'auto';

interface Settings {
    theme: Theme;
    notifications: boolean;
    soundEnabled: boolean;
    dataCollection: boolean;
    autoScroll: boolean;
    compactMode: boolean;
    showTimestamps: boolean;
    pollingInterval: number;
    maxEventsToStore: number;
    debugLogs: boolean;
    enableCompression: boolean;
}

interface StorageData {
    eventsSize: string;
    configSize: string;
    cacheSize: string;
}

const DEFAULT_SETTINGS: Settings = {
    theme: 'auto',
    notifications: true,
    soundEnabled: true,
    dataCollection: true,
    autoScroll: true,
    compactMode: false,
    showTimestamps: true,
    pollingInterval: 5,
    maxEventsToStore: 1000,
    debugLogs: false,
    enableCompression: true,
};

export const SettingsPage: React.FC = () => {
    const [settings, setSettings] = useState<Settings>(DEFAULT_SETTINGS);
    const [storageData, setStorageData] = useState<StorageData>({
        eventsSize: '0 MB',
        configSize: '0 MB',
        cacheSize: '0 MB',
    });
    const [advancedOpen, setAdvancedOpen] = useState(false);
    const [saving, setSaving] = useState(false);
    const [lastSaved, setLastSaved] = useState<Date | null>(null);

    const themeOptions = [
        { value: 'light', label: '☀️ Light' },
        { value: 'dark', label: '🌙 Dark' },
        { value: 'auto', label: '🔄 Auto' },
    ];

    // Load settings from storage on mount
    useEffect(() => {
        loadSettings();
        calculateStorageSize();
    }, []);

    // Save settings whenever they change
    useEffect(() => {
        if (lastSaved !== null) {
            saveSettings();
        }
    }, [settings]);

    const loadSettings = async () => {
        try {
            const result = await browser.storage.local.get('settings');
            if (result.settings) {
                setSettings({ ...DEFAULT_SETTINGS, ...result.settings });
            }
        } catch (error) {
            console.error('Failed to load settings:', error);
        }
    };

    const saveSettings = async () => {
        setSaving(true);
        try {
            await browser.storage.local.set({ settings });
            setLastSaved(new Date());

            // Apply theme if changed
            applyTheme(settings.theme);
        } catch (error) {
            console.error('Failed to save settings:', error);
        } finally {
            setSaving(false);
        }
    };

    const applyTheme = (theme: Theme) => {
        const root = document.documentElement;

        if (theme === 'auto') {
            const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
            root.classList.toggle('dark', prefersDark);
        } else {
            root.classList.toggle('dark', theme === 'dark');
        }
    };

    const calculateStorageSize = async () => {
        try {
            const allData = await browser.storage.local.get(null);

            // Calculate sizes
            const eventsData = allData.events || [];
            const configData = allData.config || {};
            const cacheData = allData.cache || {};

            const eventsSize = new Blob([JSON.stringify(eventsData)]).size;
            const configSize = new Blob([JSON.stringify(configData)]).size;
            const cacheSize = new Blob([JSON.stringify(cacheData)]).size;

            setStorageData({
                eventsSize: formatBytes(eventsSize),
                configSize: formatBytes(configSize),
                cacheSize: formatBytes(cacheSize),
            });
        } catch (error) {
            console.error('Failed to calculate storage size:', error);
        }
    };

    const formatBytes = (bytes: number): string => {
        if (bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return `${(bytes / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`;
    };

    const handleClearStorage = async () => {
        if (!confirm('Are you sure you want to clear all stored data? This action cannot be undone.')) {
            return;
        }

        try {
            await browser.storage.local.clear();
            // Restore settings
            await browser.storage.local.set({ settings });
            await calculateStorageSize();
            alert('Storage cleared successfully!');
        } catch (error) {
            console.error('Failed to clear storage:', error);
            alert('Failed to clear storage. Please try again.');
        }
    };

    const handleResetSettings = async () => {
        if (!confirm('Are you sure you want to reset all settings to defaults? This action cannot be undone.')) {
            return;
        }

        setSettings(DEFAULT_SETTINGS);
        setLastSaved(new Date());
        alert('Settings reset successfully!');
    };

    const updateSetting = <K extends keyof Settings>(key: K, value: Settings[K]) => {
        setSettings(prev => ({ ...prev, [key]: value }));
        setLastSaved(new Date());
    };

    return (
        <div className="p-6 space-y-6 max-w-3xl">
            {/* Appearance Settings */}
            <Card title="Appearance" description="Customize how DCMAAR looks">
                <div className="space-y-4">
                    <Select
                        label="Theme"
                        value={settings.theme}
                        onChange={(e) => updateSetting('theme', e.currentTarget.value as Theme)}
                        options={themeOptions}
                    />

                    <div className="p-3 bg-blue-50 border border-blue-200 rounded-lg text-sm text-blue-700">
                        💡 Changes take effect immediately and are saved automatically.
                        {saving && <span className="ml-2">Saving...</span>}
                        {!saving && lastSaved && (
                            <span className="ml-2 text-gray-600">
                                Last saved: {lastSaved.toLocaleTimeString()}
                            </span>
                        )}
                    </div>
                </div>
            </Card>

            {/* Notification Settings */}
            <Card title="Notifications" description="Control how DCMAAR notifies you">
                <div className="space-y-4">
                    <div className="flex items-center justify-between">
                        <div>
                            <p className="text-sm font-medium text-gray-900">Enable Notifications</p>
                            <p className="text-xs text-gray-600 mt-1">Show desktop notifications for important events</p>
                        </div>
                        <Toggle
                            checked={settings.notifications}
                            onChange={(checked) => updateSetting('notifications', checked)}
                        />
                    </div>

                    {settings.notifications && (
                        <>
                            <div className="border-t border-gray-200 pt-4 flex items-center justify-between">
                                <div>
                                    <p className="text-sm font-medium text-gray-900">Sound Alerts</p>
                                    <p className="text-xs text-gray-600 mt-1">Play sound when events arrive</p>
                                </div>
                                <Toggle
                                    checked={settings.soundEnabled}
                                    onChange={(checked) => updateSetting('soundEnabled', checked)}
                                />
                            </div>

                            <div className="bg-gray-50 p-3 rounded-lg text-xs text-gray-600">
                                <p className="font-semibold mb-2">Notification types:</p>
                                <ul className="space-y-1 ml-4">
                                    <li>• Connection status changes</li>
                                    <li>• High error rates detected</li>
                                    <li>• Storage limit approaching</li>
                                </ul>
                            </div>
                        </>
                    )}
                </div>
            </Card>

            {/* Privacy & Data */}
            <Card title="Privacy & Data" description="Manage your data and privacy settings">
                <div className="space-y-4">
                    <div className="flex items-center justify-between">
                        <div>
                            <p className="text-sm font-medium text-gray-900">Data Collection</p>
                            <p className="text-xs text-gray-600 mt-1">Collect and store event data locally</p>
                        </div>
                        <Toggle
                            checked={settings.dataCollection}
                            onChange={(checked) => updateSetting('dataCollection', checked)}
                        />
                    </div>

                    <div className="border-t border-gray-200 pt-4 space-y-3">
                        <div className="text-sm">
                            <p className="font-medium text-gray-900 mb-2">Stored Data</p>
                            <div className="space-y-2">
                                {[
                                    { label: 'Events', size: storageData.eventsSize },
                                    { label: 'Configuration', size: storageData.configSize },
                                    { label: 'Cache', size: storageData.cacheSize },
                                ].map((item) => (
                                    <div key={item.label} className="flex justify-between text-xs text-gray-700 p-2 bg-gray-50 rounded">
                                        <span>{item.label}</span>
                                        <span className="font-mono">{item.size}</span>
                                    </div>
                                ))}
                            </div>
                        </div>
                        <div className="pt-2">
                            <Button variant="secondary" className="w-full" onClick={handleClearStorage}>
                                Clear Storage
                            </Button>
                        </div>
                    </div>
                </div>
            </Card>

            {/* Data Retention */}
            <DataRetentionSettings />

            {/* UI Preferences */}
            <Card title="UI Preferences" description="Adjust interface behavior">
                <div className="space-y-4">
                    <div className="flex items-center justify-between">
                        <div>
                            <p className="text-sm font-medium text-gray-900">Auto-scroll Recent Activity</p>
                            <p className="text-xs text-gray-600 mt-1">Scroll to newest events automatically</p>
                        </div>
                        <Toggle
                            checked={settings.autoScroll}
                            onChange={(checked) => updateSetting('autoScroll', checked)}
                        />
                    </div>

                    <div className="border-t border-gray-200 pt-4 flex items-center justify-between">
                        <div>
                            <p className="text-sm font-medium text-gray-900">Compact Mode</p>
                            <p className="text-xs text-gray-600 mt-1">Reduce padding and spacing</p>
                        </div>
                        <Toggle
                            checked={settings.compactMode}
                            onChange={(checked) => updateSetting('compactMode', checked)}
                        />
                    </div>

                    <div className="border-t border-gray-200 pt-4 flex items-center justify-between">
                        <div>
                            <p className="text-sm font-medium text-gray-900">Show Timestamps</p>
                            <p className="text-xs text-gray-600 mt-1">Display event timestamps in list</p>
                        </div>
                        <Toggle
                            checked={settings.showTimestamps}
                            onChange={(checked) => updateSetting('showTimestamps', checked)}
                        />
                    </div>
                </div>
            </Card>

            {/* Advanced Settings */}
            <Card title="Advanced Settings">
                <div className="space-y-3">
                    <button
                        onClick={() => setAdvancedOpen(!advancedOpen)}
                        className="w-full flex items-center justify-between p-3 hover:bg-gray-50 rounded-lg transition-colors"
                    >
                        <span className="font-semibold text-gray-900">Advanced Options</span>
                        <span className={`text-gray-600 transition-transform ${advancedOpen ? 'rotate-180' : ''}`}>
                            ▼
                        </span>
                    </button>

                    {advancedOpen && (
                        <div className="space-y-4 border-t border-gray-200 pt-4">
                            <div className="space-y-2">
                                <label className="block text-sm font-medium text-gray-900">Polling Interval (seconds)</label>
                                <input
                                    type="number"
                                    value={settings.pollingInterval}
                                    onChange={(e) => updateSetting('pollingInterval', parseInt(e.target.value) || 5)}
                                    min="1"
                                    max="60"
                                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:border-blue-500 focus:ring-2 focus:ring-blue-200 focus:outline-none"
                                />
                            </div>

                            <div className="space-y-2">
                                <label className="block text-sm font-medium text-gray-900">Max Events to Store</label>
                                <input
                                    type="number"
                                    value={settings.maxEventsToStore}
                                    onChange={(e) => updateSetting('maxEventsToStore', parseInt(e.target.value) || 1000)}
                                    min="100"
                                    max="10000"
                                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:border-blue-500 focus:ring-2 focus:ring-blue-200 focus:outline-none"
                                />
                            </div>

                            <div className="flex items-center justify-between">
                                <div>
                                    <p className="text-sm font-medium text-gray-900">Enable Debug Logs</p>
                                    <p className="text-xs text-gray-600 mt-1">Log verbose output to console</p>
                                </div>
                                <Toggle
                                    checked={settings.debugLogs}
                                    onChange={(checked) => updateSetting('debugLogs', checked)}
                                />
                            </div>

                            <div className="flex items-center justify-between">
                                <div>
                                    <p className="text-sm font-medium text-gray-900">Enable Compression</p>
                                    <p className="text-xs text-gray-600 mt-1">Compress stored events</p>
                                </div>
                                <Toggle
                                    checked={settings.enableCompression}
                                    onChange={(checked) => updateSetting('enableCompression', checked)}
                                />
                            </div>

                            <div className="pt-3 border-t border-gray-200 space-y-2">
                                <p className="text-sm font-medium text-gray-900">Dangerous Actions</p>
                                <Button variant="danger" className="w-full text-sm" onClick={handleResetSettings}>
                                    Reset All Settings
                                </Button>
                            </div>
                        </div>
                    )}
                </div>
            </Card>

            {/* Help & Support */}
            <Card title="Help & Support">
                <div className="space-y-3">
                    {[
                        { title: 'Documentation', desc: 'Read the full documentation', icon: '📚' },
                        { title: 'Report Bug', desc: 'Report issues on GitHub', icon: '🐛' },
                        { title: 'Contact Support', desc: 'Email support@dcmaar.io', icon: '📧' },
                    ].map((item) => (
                        <a
                            key={item.title}
                            href="#"
                            className="flex items-center gap-3 p-3 rounded-lg border border-gray-200 hover:border-blue-400 hover:bg-blue-50 transition-colors"
                        >
                            <span className="text-xl">{item.icon}</span>
                            <div className="flex-1">
                                <p className="font-medium text-gray-900">{item.title}</p>
                                <p className="text-xs text-gray-600">{item.desc}</p>
                            </div>
                            <span className="text-gray-400">→</span>
                        </a>
                    ))}
                </div>
            </Card>

            {/* Footer */}
            <div className="flex items-center justify-between pt-4 border-t border-gray-200">
                <div className="text-xs text-gray-600">
                    <p className="font-semibold text-gray-900">DCMAAR v1.0.0</p>
                    <p>All settings are saved automatically</p>
                </div>
                {saving ? (
                    <Badge variant="warning" label="Saving..." />
                ) : lastSaved ? (
                    <Badge variant="success" label="Saved" />
                ) : null}
            </div>
        </div>
    );
};
