import { memo, useState } from 'react';
import { useNavigate } from 'react-router';

/**
 * User settings and preferences dashboard.
 *
 * <p><b>Purpose</b><br>
 * Centralized settings interface for user preferences, notification settings,
 * display preferences, integrations, and account management.
 *
 * <p><b>Features</b><br>
 * - Theme preferences (light/dark)
 * - Notification settings
 * - Display preferences
 * - Account settings
 * - API keys and integrations
 * - Data export/import
 * - Session management
 *
 * <p><b>Props</b><br>
 * None - component manages its own state
 *
 * @doc.type page
 * @doc.purpose Settings and preferences page
 * @doc.layer product
 * @doc.pattern Settings Page
 */

interface Settings {
    theme: 'light' | 'dark' | 'auto';
    notifications: {
        email: boolean;
        desktop: boolean;
        slack: boolean;
    };
    display: {
        compactMode: boolean;
        showGridLines: boolean;
        chartsStyle: 'line' | 'bar' | 'area';
    };
    timezone: string;
    dateFormat: string;
}

const DEFAULT_SETTINGS: Settings = {
    theme: 'dark',
    notifications: {
        email: true,
        desktop: true,
        slack: false,
    },
    display: {
        compactMode: false,
        showGridLines: true,
        chartsStyle: 'line',
    },
    timezone: 'UTC',
    dateFormat: 'YYYY-MM-DD HH:mm:ss',
};

export const SettingsPage = memo(function SettingsPage() {
    // GIVEN: User on settings page
    // WHEN: User modifies preferences
    // THEN: Update settings and persist

    const navigate = useNavigate();
    const [settings, setSettings] = useState<Settings>(DEFAULT_SETTINGS);
    const [activeTab, setActiveTab] = useState<'general' | 'notifications' | 'integrations' | 'account'>('general');
    const [_saved, setSaved] = useState(false);

    const handleThemeChange = (theme: 'light' | 'dark' | 'auto') => {
        setSettings((prev) => ({ ...prev, theme }));
        setSaved(false);
    };

    const handleNotificationChange = (key: 'email' | 'desktop' | 'slack', value: boolean) => {
        setSettings((prev) => ({
            ...prev,
            notifications: { ...prev.notifications, [key]: value },
        }));
        setSaved(false);
    };

    const handleSave = () => {
        setSaved(true);
        setTimeout(() => setSaved(false), 2000);
    };

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-slate-900 p-4">
            <div className="max-w-4xl mx-auto">
                {/* Header */}
                <div className="flex flex-col md:flex-row md:justify-between md:items-start gap-4 mb-8">
                    <div>
                        <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100 mb-2">Settings</h1>
                        <p className="text-slate-600 dark:text-neutral-400">Manage your preferences and account settings</p>
                    </div>
                    <button
                        onClick={() => navigate('/org?type=integration')}
                        className="inline-flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium bg-slate-100 dark:bg-neutral-700 text-slate-700 dark:text-neutral-300 hover:bg-slate-200 dark:hover:bg-slate-600 transition-colors"
                    >
                        <span>🏗️</span>
                        <span>Open in Org Builder</span>
                    </button>
                </div>

                <div className="grid grid-cols-4 gap-6">
                    {/* Sidebar */}
                    <div className="col-span-1">
                        <div className="bg-white dark:bg-neutral-800 rounded-lg border border-slate-200 dark:border-neutral-600 divide-y divide-slate-200 dark:divide-slate-700 sticky top-4">
                            {['general', 'notifications', 'integrations', 'account'].map((tab) => (
                                <button
                                    key={tab}
                                    onClick={() => setActiveTab(tab as typeof activeTab)}
                                    className={`w-full px-4 py-3 text-left text-sm font-medium transition-colors ${activeTab === tab
                                        ? 'bg-blue-600 text-white'
                                        : 'text-slate-700 dark:text-neutral-300 hover:bg-slate-100 dark:hover:bg-slate-700'
                                        }`}
                                >
                                    {tab === 'general' && '⚙️ General'}
                                    {tab === 'notifications' && '🔔 Notifications'}
                                    {tab === 'integrations' && '🔗 Integrations'}
                                    {tab === 'account' && '👤 Account'}
                                </button>
                            ))}
                        </div>
                    </div>

                    {/* Main Content */}
                    <div className="col-span-3">
                        <div className="bg-white dark:bg-neutral-800 rounded-lg border border-slate-200 dark:border-neutral-600 p-6 space-y-6">
                            {/* General Settings */}
                            {activeTab === 'general' && (
                                <>
                                    <div>
                                        <h2 className="text-2xl font-bold text-slate-900 dark:text-neutral-100 mb-6">General Settings</h2>

                                        {/* Theme */}
                                        <div className="mb-6 pb-6 border-b border-slate-200 dark:border-neutral-600">
                                            <label className="block text-sm font-medium text-slate-900 dark:text-neutral-100 mb-3">Theme</label>
                                            <div className="flex gap-4">
                                                {(['light', 'dark', 'auto'] as const).map((theme) => (
                                                    <label key={theme} className="flex items-center gap-2 cursor-pointer">
                                                        <input
                                                            type="radio"
                                                            checked={settings.theme === theme}
                                                            onChange={() => handleThemeChange(theme)}
                                                            className="w-4 h-4"
                                                        />
                                                        <span className="text-slate-700 dark:text-neutral-300 capitalize">{theme}</span>
                                                    </label>
                                                ))}
                                            </div>
                                        </div>

                                        {/* Timezone */}
                                        <div className="mb-6 pb-6 border-b border-slate-200 dark:border-neutral-600">
                                            <label className="block text-sm font-medium text-slate-900 dark:text-neutral-100 mb-3">Timezone</label>
                                            <select className="w-full px-4 py-2 bg-slate-100 dark:bg-slate-900 border border-slate-200 dark:border-neutral-600 rounded text-slate-900 dark:text-slate-200">
                                                <option>UTC</option>
                                                <option>PST</option>
                                                <option>EST</option>
                                                <option>CET</option>
                                                <option>IST</option>
                                            </select>
                                        </div>

                                        {/* Date Format */}
                                        <div className="mb-6">
                                            <label className="block text-sm font-medium text-slate-900 dark:text-neutral-100 mb-3">Date Format</label>
                                            <select className="w-full px-4 py-2 bg-slate-100 dark:bg-slate-900 border border-slate-200 dark:border-neutral-600 rounded text-slate-900 dark:text-slate-200">
                                                <option>YYYY-MM-DD HH:mm:ss</option>
                                                <option>DD/MM/YYYY HH:mm</option>
                                                <option>MM/DD/YYYY HH:mm</option>
                                            </select>
                                        </div>

                                        {/* Display Options */}
                                        <div className="space-y-3">
                                            <label className="flex items-center gap-3 cursor-pointer">
                                                <input
                                                    type="checkbox"
                                                    checked={settings.display.compactMode}
                                                    onChange={(e) =>
                                                        setSettings((prev) => ({
                                                            ...prev,
                                                            display: { ...prev.display, compactMode: e.target.checked },
                                                        }))
                                                    }
                                                    className="w-4 h-4"
                                                />
                                                <span className="text-slate-700 dark:text-neutral-300">Compact mode</span>
                                            </label>
                                            <label className="flex items-center gap-3 cursor-pointer">
                                                <input
                                                    type="checkbox"
                                                    checked={settings.display.showGridLines}
                                                    onChange={(e) =>
                                                        setSettings((prev) => ({
                                                            ...prev,
                                                            display: { ...prev.display, showGridLines: e.target.checked },
                                                        }))
                                                    }
                                                    className="w-4 h-4"
                                                />
                                                <span className="text-slate-700 dark:text-neutral-300">Show grid lines in charts</span>
                                            </label>
                                        </div>
                                    </div>
                                </>
                            )}

                            {/* Notification Settings */}
                            {activeTab === 'notifications' && (
                                <>
                                    <h2 className="text-2xl font-bold text-slate-900 dark:text-neutral-100 mb-6">Notification Preferences</h2>

                                    <div className="space-y-4">
                                        <label className="flex items-center justify-between p-4 bg-slate-100 dark:bg-slate-900 rounded border border-slate-200 dark:border-neutral-600 cursor-pointer hover:border-slate-300 dark:hover:border-slate-600">
                                            <div>
                                                <span className="text-slate-900 dark:text-neutral-100 font-medium">Email Notifications</span>
                                                <p className="text-xs text-slate-500 dark:text-slate-500 dark:text-neutral-400 mt-1">Receive alerts via email</p>
                                            </div>
                                            <input
                                                type="checkbox"
                                                checked={settings.notifications.email}
                                                onChange={(e) => handleNotificationChange('email', e.target.checked)}
                                                className="w-4 h-4"
                                            />
                                        </label>

                                        <label className="flex items-center justify-between p-4 bg-slate-100 dark:bg-slate-900 rounded border border-slate-200 dark:border-neutral-600 cursor-pointer hover:border-slate-300 dark:hover:border-slate-600">
                                            <div>
                                                <span className="text-slate-900 dark:text-neutral-100 font-medium">Desktop Notifications</span>
                                                <p className="text-xs text-slate-500 dark:text-slate-500 dark:text-neutral-400 mt-1">System desktop alerts</p>
                                            </div>
                                            <input
                                                type="checkbox"
                                                checked={settings.notifications.desktop}
                                                onChange={(e) => handleNotificationChange('desktop', e.target.checked)}
                                                className="w-4 h-4"
                                            />
                                        </label>

                                        <label className="flex items-center justify-between p-4 bg-slate-100 dark:bg-slate-900 rounded border border-slate-200 dark:border-neutral-600 cursor-pointer hover:border-slate-300 dark:hover:border-slate-600">
                                            <div>
                                                <span className="text-slate-900 dark:text-neutral-100 font-medium">Slack Integration</span>
                                                <p className="text-xs text-slate-500 dark:text-slate-500 dark:text-neutral-400 mt-1">Send alerts to Slack</p>
                                            </div>
                                            <input
                                                type="checkbox"
                                                checked={settings.notifications.slack}
                                                onChange={(e) => handleNotificationChange('slack', e.target.checked)}
                                                className="w-4 h-4"
                                            />
                                        </label>
                                    </div>

                                    {/* Alert Types */}
                                    <div className="mt-6 pt-6 border-t border-slate-200 dark:border-neutral-600">
                                        <h3 className="font-medium text-slate-900 dark:text-neutral-100 mb-3">Alert Types</h3>
                                        <div className="space-y-2">
                                            {['High Latency Detected', 'Model Training Failed', 'SLA Breach', 'Security Alert'].map((alert) => (
                                                <label key={alert} className="flex items-center gap-2">
                                                    <input type="checkbox" defaultChecked className="w-4 h-4" />
                                                    <span className="text-slate-700 dark:text-neutral-300 text-sm">{alert}</span>
                                                </label>
                                            ))}
                                        </div>
                                    </div>
                                </>
                            )}

                            {/* Integrations */}
                            {activeTab === 'integrations' && (
                                <>
                                    <h2 className="text-2xl font-bold text-slate-900 dark:text-neutral-100 mb-6">Integrations</h2>

                                    <div className="space-y-3">
                                        {[
                                            { name: 'Slack', icon: '💬', status: 'disconnected' },
                                            { name: 'GitHub', icon: '🐙', status: 'connected' },
                                            { name: 'PagerDuty', icon: '📞', status: 'disconnected' },
                                            { name: 'Datadog', icon: '📊', status: 'connected' },
                                        ].map((integration) => (
                                            <div
                                                key={integration.name}
                                                className="flex items-center justify-between p-4 bg-slate-100 dark:bg-slate-900 rounded border border-slate-200 dark:border-neutral-600"
                                            >
                                                <div className="flex items-center gap-3">
                                                    <span className="text-2xl">{integration.icon}</span>
                                                    <div>
                                                        <div className="font-medium text-slate-900 dark:text-neutral-100">{integration.name}</div>
                                                        <span
                                                            className={`text-xs ${integration.status === 'connected' ? 'text-green-600 dark:text-green-400' : 'text-slate-500 dark:text-slate-500'}`}
                                                        >
                                                            {integration.status === 'connected' ? '✓ Connected' : 'Not connected'}
                                                        </span>
                                                    </div>
                                                </div>
                                                <button
                                                    className={`px-4 py-2 rounded text-sm font-medium ${integration.status === 'connected'
                                                        ? 'bg-red-100 dark:bg-red-900/30 hover:bg-red-200 dark:hover:bg-red-900/50 text-red-700 dark:text-red-300'
                                                        : 'bg-blue-600 hover:bg-blue-500 text-white'
                                                        }`}
                                                >
                                                    {integration.status === 'connected' ? 'Disconnect' : 'Connect'}
                                                </button>
                                            </div>
                                        ))}
                                    </div>
                                </>
                            )}

                            {/* Account */}
                            {activeTab === 'account' && (
                                <>
                                    <h2 className="text-2xl font-bold text-slate-900 dark:text-neutral-100 mb-6">Account Settings</h2>

                                    <div className="space-y-6">
                                        <div>
                                            <label className="block text-sm font-medium text-slate-900 dark:text-neutral-100 mb-2">Email</label>
                                            <input
                                                type="email"
                                                defaultValue="user@example.com"
                                                className="w-full px-4 py-2 bg-slate-100 dark:bg-slate-900 border border-slate-200 dark:border-neutral-600 rounded text-slate-900 dark:text-slate-200"
                                            />
                                        </div>

                                        <div>
                                            <label className="block text-sm font-medium text-slate-900 dark:text-neutral-100 mb-2">Full Name</label>
                                            <input
                                                type="text"
                                                defaultValue="John Doe"
                                                className="w-full px-4 py-2 bg-slate-100 dark:bg-slate-900 border border-slate-200 dark:border-neutral-600 rounded text-slate-900 dark:text-slate-200"
                                            />
                                        </div>

                                        <div className="pt-6 border-t border-slate-200 dark:border-neutral-600">
                                            <h3 className="font-medium text-slate-900 dark:text-neutral-100 mb-3">Active Sessions</h3>
                                            <div className="space-y-2 text-sm">
                                                <div className="flex items-center justify-between p-3 bg-slate-100 dark:bg-slate-900 rounded">
                                                    <span className="text-slate-700 dark:text-neutral-300">Current Session (Chrome, macOS)</span>
                                                    <span className="text-green-600 dark:text-green-400">Active</span>
                                                </div>
                                                <div className="flex items-center justify-between p-3 bg-slate-100 dark:bg-slate-900 rounded">
                                                    <span className="text-slate-700 dark:text-neutral-300">Mobile App (iPhone)</span>
                                                    <button className="text-red-600 dark:text-rose-400 hover:text-red-500 dark:hover:text-red-300 text-xs">Sign out</button>
                                                </div>
                                            </div>
                                        </div>

                                        <div className="pt-6 border-t border-slate-200 dark:border-neutral-600">
                                            <button className="px-4 py-2 bg-red-100 dark:bg-red-900/30 hover:bg-red-200 dark:hover:bg-red-900/50 text-red-700 dark:text-red-300 rounded font-medium text-sm">
                                                🔑 Change Password
                                            </button>
                                        </div>

                                        <div className="pt-6 border-t border-slate-200 dark:border-neutral-600">
                                            <button className="px-4 py-2 bg-slate-200 dark:bg-neutral-700 hover:bg-slate-300 dark:hover:bg-slate-600 text-red-600 dark:text-rose-400 rounded font-medium text-sm">
                                                🗑 Delete Account
                                            </button>
                                        </div>
                                    </div>
                                </>
                            )}

                            {/* Save Button */}
                            <div className="flex gap-2 pt-6 border-t border-slate-200 dark:border-neutral-600">
                                <button
                                    onClick={handleSave}
                                    className="px-6 py-2 bg-blue-600 hover:bg-blue-500 text-white font-medium rounded transition-colors"
                                >
                                    ✓ Save Changes
                                </button>
                                <button className="px-6 py-2 bg-slate-200 dark:bg-neutral-700 hover:bg-slate-300 dark:hover:bg-slate-600 text-slate-900 dark:text-slate-200 font-medium rounded transition-colors">
                                    Cancel
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
});

export default SettingsPage;
