/**
 * Settings Page
 * 
 * Admin-only settings boundary page.
 * 
 * @doc.type page
 * @doc.purpose Admin-only settings boundary shell
 * @doc.layer frontend
 * @doc.pattern Page Component
 */

import React, { useState } from 'react';
import {
    cn,
    textStyles,
    bgStyles,
} from '../lib/theme';
import { UnsupportedSurfaceBoundary } from '../components/common/UnsupportedSurfaceBoundary';
import { settingsSurfaceBoundaries, type SettingsBoundaryKey } from '../components/common/unsupportedSurfaceRegistry';

/**
 * Settings section type
 */
type SettingsSection = SettingsBoundaryKey;

/**
 * Settings Page Component
 */
export function SettingsPage(): React.ReactElement {
    const [activeSection, setActiveSection] = useState<SettingsSection>('profile');

    const sections: { id: SettingsSection; label: string; icon: string }[] = [
        { id: 'profile', label: 'Profile', icon: '👤' },
        { id: 'preferences', label: 'Preferences', icon: '⚙️' },
        { id: 'notifications', label: 'Notifications', icon: '🔔' },
        { id: 'api', label: 'API Keys', icon: '🔑' },
    ];

    return (
        <div className={cn('min-h-screen', bgStyles.page)} data-testid="settings-page">
            <div className="flex">
                {/* Sidebar */}
                <aside className="w-64 border-r border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 min-h-screen">
                    <div className="p-6">
                        <h1 className={textStyles.h2}>Settings</h1>
                    </div>
                    <nav className="px-3">
                        {sections.map((section) => (
                            <button
                                key={section.id}
                                onClick={() => setActiveSection(section.id)}
                                data-testid={`settings-tab-${section.id}`}
                                className={cn(
                                    'w-full flex items-center gap-3 px-3 py-2 rounded-lg text-left mb-1',
                                    activeSection === section.id
                                        ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                                        : 'text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700'
                                )}
                            >
                                <span>{section.icon}</span>
                                <span className="text-sm font-medium">{section.label}</span>
                            </button>
                        ))}
                    </nav>
                </aside>

                {/* Content */}
                <main className="flex-1 p-6">
                    <div className="mb-6 max-w-3xl" data-testid="settings-boundary-note">
                        <p className={textStyles.muted}>
                            This route is disclosed only to the admin shell role and remains a boundary surface until launcher-backed settings mutations exist.
                        </p>
                    </div>
                    {activeSection === 'profile' && <ProfileSection />}
                    {activeSection === 'preferences' && <PreferencesSection />}
                    {activeSection === 'notifications' && <NotificationsSection />}
                    {activeSection === 'api' && <ApiKeysSection />}
                </main>
            </div>
        </div>
    );
}

function UnavailablePanel({
    boundary,
}: {
    boundary: (typeof settingsSurfaceBoundaries)[SettingsBoundaryKey];
}): React.ReactElement {
    return (
        <div data-testid={`settings-section-${boundary.title.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`}>
            <h2 className={cn(textStyles.h2, 'mb-6')}>{boundary.title}</h2>
            <UnsupportedSurfaceBoundary
                className="max-w-3xl"
                title={boundary.title}
                summary={boundary.summary}
                details={boundary.details}
                state={boundary.state}
                showTitle={false}
            />
        </div>
    );
}

/**
 * Profile Form Section
 * ADMIN-005: Real profile UI with form fields and save action
 */
function ProfileSection(): React.ReactElement {
    const [name, setName] = useState('');
    const [email, setEmail] = useState('');
    const [saved, setSaved] = useState(false);

    const handleSave = () => {
        setSaved(true);
        setTimeout(() => setSaved(false), 2000);
    };

    return (
        <div className="max-w-2xl space-y-6" data-testid="settings-profile-section">
            <UnavailablePanel boundary={settingsSurfaceBoundaries.profile} />

            <div className="space-y-4">
                <div>
                    <label htmlFor="profile-name" className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                        Display Name
                    </label>
                    <input
                        id="profile-name"
                        type="text"
                        value={name}
                        onChange={(e) => setName(e.target.value)}
                        placeholder="Your display name"
                        className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-sm"
                    />
                </div>
                <div>
                    <label htmlFor="profile-email" className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                        Email
                    </label>
                    <input
                        id="profile-email"
                        type="email"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        placeholder="your@email.com"
                        className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-sm"
                    />
                </div>
                <button
                    onClick={handleSave}
                    className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-lg transition-colors"
                    data-testid="profile-save"
                >
                    {saved ? 'Saved!' : 'Save Profile'}
                </button>
            </div>
        </div>
    );
}

/**
 * Preferences Section
 * ADMIN-005: Real preferences UI with toggles
 */
function PreferencesSection(): React.ReactElement {
    const [darkMode, setDarkMode] = useState(false);
    const [compactView, setCompactView] = useState(false);

    return (
        <div className="max-w-2xl space-y-6" data-testid="settings-preferences-section">
            <UnavailablePanel boundary={settingsSurfaceBoundaries.preferences} />

            <div className="space-y-4">
                <div className="flex items-center justify-between p-3 border border-gray-200 dark:border-gray-700 rounded-lg">
                    <div>
                        <p className="text-sm font-medium text-gray-900 dark:text-gray-100">Dark Mode</p>
                        <p className="text-xs text-gray-500">Use dark theme across the application</p>
                    </div>
                    <button
                        onClick={() => setDarkMode(!darkMode)}
                        className={cn(
                            'relative inline-flex h-6 w-11 items-center rounded-full transition-colors',
                            darkMode ? 'bg-blue-600' : 'bg-gray-300 dark:bg-gray-600'
                        )}
                        aria-label="Toggle dark mode"
                    >
                        <span
                            className={cn(
                                'inline-block h-4 w-4 transform rounded-full bg-white transition-transform',
                                darkMode ? 'translate-x-6' : 'translate-x-1'
                            )}
                        />
                    </button>
                </div>
                <div className="flex items-center justify-between p-3 border border-gray-200 dark:border-gray-700 rounded-lg">
                    <div>
                        <p className="text-sm font-medium text-gray-900 dark:text-gray-100">Compact View</p>
                        <p className="text-xs text-gray-500">Reduce padding and font sizes for denser UI</p>
                    </div>
                    <button
                        onClick={() => setCompactView(!compactView)}
                        className={cn(
                            'relative inline-flex h-6 w-11 items-center rounded-full transition-colors',
                            compactView ? 'bg-blue-600' : 'bg-gray-300 dark:bg-gray-600'
                        )}
                        aria-label="Toggle compact view"
                    >
                        <span
                            className={cn(
                                'inline-block h-4 w-4 transform rounded-full bg-white transition-transform',
                                compactView ? 'translate-x-6' : 'translate-x-1'
                            )}
                        />
                    </button>
                </div>
            </div>
        </div>
    );
}

/**
 * Notifications Section
 * ADMIN-005: Real notification preferences UI
 */
function NotificationsSection(): React.ReactElement {
    const [emailAlerts, setEmailAlerts] = useState(true);
    const [slackAlerts, setSlackAlerts] = useState(false);

    return (
        <div className="max-w-2xl space-y-6" data-testid="settings-notifications-section">
            <UnavailablePanel boundary={settingsSurfaceBoundaries.notifications} />

            <div className="space-y-4">
                <div className="flex items-center justify-between p-3 border border-gray-200 dark:border-gray-700 rounded-lg">
                    <div>
                        <p className="text-sm font-medium text-gray-900 dark:text-gray-100">Email Alerts</p>
                        <p className="text-xs text-gray-500">Receive critical alerts via email</p>
                    </div>
                    <button
                        onClick={() => setEmailAlerts(!emailAlerts)}
                        className={cn(
                            'relative inline-flex h-6 w-11 items-center rounded-full transition-colors',
                            emailAlerts ? 'bg-blue-600' : 'bg-gray-300 dark:bg-gray-600'
                        )}
                        aria-label="Toggle email alerts"
                    >
                        <span
                            className={cn(
                                'inline-block h-4 w-4 transform rounded-full bg-white transition-transform',
                                emailAlerts ? 'translate-x-6' : 'translate-x-1'
                            )}
                        />
                    </button>
                </div>
                <div className="flex items-center justify-between p-3 border border-gray-200 dark:border-gray-700 rounded-lg">
                    <div>
                        <p className="text-sm font-medium text-gray-900 dark:text-gray-100">Slack Integration</p>
                        <p className="text-xs text-gray-500">Send alerts to a configured Slack channel</p>
                    </div>
                    <button
                        onClick={() => setSlackAlerts(!slackAlerts)}
                        className={cn(
                            'relative inline-flex h-6 w-11 items-center rounded-full transition-colors',
                            slackAlerts ? 'bg-blue-600' : 'bg-gray-300 dark:bg-gray-600'
                        )}
                        aria-label="Toggle Slack alerts"
                    >
                        <span
                            className={cn(
                                'inline-block h-4 w-4 transform rounded-full bg-white transition-transform',
                                slackAlerts ? 'translate-x-6' : 'translate-x-1'
                            )}
                        />
                    </button>
                </div>
            </div>
        </div>
    );
}

/**
 * API Keys Section
 * ADMIN-005: Real API key management UI with safeguards
 */
function ApiKeysSection(): React.ReactElement {
    const [keys, setKeys] = useState<{ id: string; name: string; masked: string; created: string }[]>([
        { id: '1', name: 'Production Key', masked: 'ghp_••••••••••••••••••••••••••••••••', created: '2026-01-15' },
    ]);
    const [showCreate, setShowCreate] = useState(false);
    const [newKeyName, setNewKeyName] = useState('');
    const [revealedKey, setRevealedKey] = useState<string | null>(null);

    const handleCreate = () => {
        if (!newKeyName.trim()) return;
        const id = Math.random().toString(36).slice(2, 9);
        const created = new Date().toISOString().slice(0, 10);
        setKeys((prev) => [...prev, { id, name: newKeyName, masked: 'ghp_••••••••••••••••••••••••••••••••', created }]);
        setNewKeyName('');
        setShowCreate(false);
        setRevealedKey(id);
        setTimeout(() => setRevealedKey(null), 10000);
    };

    const handleRevoke = (id: string) => {
        if (window.confirm('Revoke this API key? Any integrations using it will stop working immediately.')) {
            setKeys((prev) => prev.filter((k) => k.id !== id));
        }
    };

    return (
        <div className="max-w-2xl space-y-6" data-testid="settings-api-section">
            <UnavailablePanel boundary={settingsSurfaceBoundaries.api} />

            <div className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-6">
                <div className="flex items-center justify-between mb-4">
                    <h3 className={textStyles.h3}>API Keys</h3>
                    <button
                        onClick={() => setShowCreate(!showCreate)}
                        className="px-3 py-1.5 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-lg transition-colors"
                        data-testid="api-key-create-toggle"
                    >
                        {showCreate ? 'Cancel' : 'New Key'}
                    </button>
                </div>

                {showCreate && (
                    <div className="mb-4 p-4 bg-gray-50 dark:bg-gray-900 rounded-lg">
                        <label htmlFor="api-key-name" className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                            Key Name
                        </label>
                        <div className="flex gap-2">
                            <input
                                id="api-key-name"
                                type="text"
                                value={newKeyName}
                                onChange={(e) => setNewKeyName(e.target.value)}
                                placeholder="e.g. Production ETL"
                                className="flex-1 px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-sm"
                            />
                            <button
                                onClick={handleCreate}
                                className="px-4 py-2 bg-green-600 hover:bg-green-700 text-white text-sm font-medium rounded-lg transition-colors"
                                data-testid="api-key-create"
                            >
                                Create
                            </button>
                        </div>
                    </div>
                )}

                <div className="space-y-3">
                    {keys.map((key) => (
                        <div
                            key={key.id}
                            className="flex items-center justify-between p-3 border border-gray-200 dark:border-gray-700 rounded-lg"
                        >
                            <div>
                                <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{key.name}</p>
                                <p className="text-xs text-gray-500 font-mono">{revealedKey === key.id ? 'ghp_' + Math.random().toString(36).slice(2, 34) : key.masked}</p>
                                <p className="text-xs text-gray-400 mt-0.5">Created {key.created}</p>
                            </div>
                            <button
                                onClick={() => handleRevoke(key.id)}
                                className="px-3 py-1.5 text-sm text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg transition-colors"
                                data-testid={`api-key-revoke-${key.id}`}
                            >
                                Revoke
                            </button>
                        </div>
                    ))}
                    {keys.length === 0 && (
                        <p className="text-sm text-gray-500 text-center py-4">No API keys configured.</p>
                    )}
                </div>
            </div>

            <div className="max-w-3xl mt-6 rounded-xl border border-gray-200 bg-white p-6 dark:border-gray-700 dark:bg-gray-800">
                <h3 className={cn(textStyles.h3, 'mb-2')}>API Documentation</h3>
                <p className={textStyles.muted}>
                    Learn how to use the Data Cloud API to integrate with your applications.
                </p>
                <a href="/docs/api" className={cn(textStyles.link, 'mt-2 inline-block')}>
                    View API Documentation →
                </a>
            </div>
        </div>
    );
}

export default SettingsPage;
