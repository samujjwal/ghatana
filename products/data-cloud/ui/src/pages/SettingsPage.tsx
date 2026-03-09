/**
 * Settings Page
 * 
 * User preferences and application settings.
 * 
 * @doc.type page
 * @doc.purpose User settings management
 * @doc.layer frontend
 * @doc.pattern Page Component
 */

import React, { useState } from 'react';
import {
    cn,
    cardStyles,
    textStyles,
    bgStyles,
    buttonStyles,
    inputStyles,
} from '../lib/theme';

/**
 * Settings section type
 */
type SettingsSection = 'profile' | 'preferences' | 'notifications' | 'api';

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
        <div className={cn('min-h-screen', bgStyles.page)}>
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
                    {activeSection === 'profile' && <ProfileSection />}
                    {activeSection === 'preferences' && <PreferencesSection />}
                    {activeSection === 'notifications' && <NotificationsSection />}
                    {activeSection === 'api' && <ApiKeysSection />}
                </main>
            </div>
        </div>
    );
}

/**
 * Profile Section
 */
function ProfileSection(): React.ReactElement {
    return (
        <div>
            <h2 className={cn(textStyles.h2, 'mb-6')}>Profile Settings</h2>

            <div className={cn(cardStyles.base, cardStyles.padded, 'max-w-2xl')}>
                <div className="space-y-6">
                    <div className="flex items-center gap-4">
                        <div className="w-20 h-20 rounded-full bg-blue-500 flex items-center justify-center text-white text-2xl font-bold">
                            JD
                        </div>
                        <div>
                            <button className={buttonStyles.secondary}>Change Avatar</button>
                        </div>
                    </div>

                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className={cn(textStyles.label, 'block mb-1')}>First Name</label>
                            <input type="text" defaultValue="John" className={inputStyles.base} />
                        </div>
                        <div>
                            <label className={cn(textStyles.label, 'block mb-1')}>Last Name</label>
                            <input type="text" defaultValue="Doe" className={inputStyles.base} />
                        </div>
                    </div>

                    <div>
                        <label className={cn(textStyles.label, 'block mb-1')}>Email</label>
                        <input type="email" defaultValue="john.doe@example.com" className={inputStyles.base} />
                    </div>

                    <div>
                        <label className={cn(textStyles.label, 'block mb-1')}>Role</label>
                        <input type="text" defaultValue="Data Steward" disabled className={cn(inputStyles.base, 'bg-gray-100 dark:bg-gray-600')} />
                    </div>

                    <div className="pt-4 border-t border-gray-200 dark:border-gray-700">
                        <button className={buttonStyles.primary}>Save Changes</button>
                    </div>
                </div>
            </div>
        </div>
    );
}

/**
 * Preferences Section
 */
function PreferencesSection(): React.ReactElement {
    const [theme, setTheme] = useState('system');
    const [language, setLanguage] = useState('en');

    return (
        <div>
            <h2 className={cn(textStyles.h2, 'mb-6')}>Preferences</h2>

            <div className={cn(cardStyles.base, cardStyles.padded, 'max-w-2xl')}>
                <div className="space-y-6">
                    <div>
                        <label className={cn(textStyles.label, 'block mb-1')}>Theme</label>
                        <select
                            value={theme}
                            onChange={(e) => setTheme(e.target.value)}
                            className={inputStyles.select}
                        >
                            <option value="system">System Default</option>
                            <option value="light">Light</option>
                            <option value="dark">Dark</option>
                        </select>
                    </div>

                    <div>
                        <label className={cn(textStyles.label, 'block mb-1')}>Language</label>
                        <select
                            value={language}
                            onChange={(e) => setLanguage(e.target.value)}
                            className={inputStyles.select}
                        >
                            <option value="en">English</option>
                            <option value="es">Spanish</option>
                            <option value="fr">French</option>
                            <option value="de">German</option>
                        </select>
                    </div>

                    <div>
                        <label className={cn(textStyles.label, 'block mb-1')}>Timezone</label>
                        <select className={inputStyles.select}>
                            <option>UTC</option>
                            <option>America/New_York</option>
                            <option>America/Los_Angeles</option>
                            <option>Europe/London</option>
                            <option>Asia/Tokyo</option>
                        </select>
                    </div>

                    <div>
                        <label className={cn(textStyles.label, 'block mb-1')}>Date Format</label>
                        <select className={inputStyles.select}>
                            <option>MM/DD/YYYY</option>
                            <option>DD/MM/YYYY</option>
                            <option>YYYY-MM-DD</option>
                        </select>
                    </div>

                    <div className="pt-4 border-t border-gray-200 dark:border-gray-700">
                        <button className={buttonStyles.primary}>Save Preferences</button>
                    </div>
                </div>
            </div>
        </div>
    );
}

/**
 * Notifications Section
 */
function NotificationsSection(): React.ReactElement {
    const [emailAlerts, setEmailAlerts] = useState(true);
    const [slackAlerts, setSlackAlerts] = useState(false);
    const [workflowNotifs, setWorkflowNotifs] = useState(true);
    const [dataQualityNotifs, setDataQualityNotifs] = useState(true);

    return (
        <div>
            <h2 className={cn(textStyles.h2, 'mb-6')}>Notification Settings</h2>

            <div className={cn(cardStyles.base, cardStyles.padded, 'max-w-2xl')}>
                <div className="space-y-6">
                    <div>
                        <h3 className={cn(textStyles.h3, 'mb-4')}>Channels</h3>
                        <div className="space-y-3">
                            <label className="flex items-center gap-3">
                                <input
                                    type="checkbox"
                                    checked={emailAlerts}
                                    onChange={(e) => setEmailAlerts(e.target.checked)}
                                    className="w-4 h-4 rounded border-gray-300"
                                />
                                <span className={textStyles.body}>Email Notifications</span>
                            </label>
                            <label className="flex items-center gap-3">
                                <input
                                    type="checkbox"
                                    checked={slackAlerts}
                                    onChange={(e) => setSlackAlerts(e.target.checked)}
                                    className="w-4 h-4 rounded border-gray-300"
                                />
                                <span className={textStyles.body}>Slack Notifications</span>
                            </label>
                        </div>
                    </div>

                    <div className="pt-4 border-t border-gray-200 dark:border-gray-700">
                        <h3 className={cn(textStyles.h3, 'mb-4')}>Notification Types</h3>
                        <div className="space-y-3">
                            <label className="flex items-center gap-3">
                                <input
                                    type="checkbox"
                                    checked={workflowNotifs}
                                    onChange={(e) => setWorkflowNotifs(e.target.checked)}
                                    className="w-4 h-4 rounded border-gray-300"
                                />
                                <span className={textStyles.body}>Workflow Execution Updates</span>
                            </label>
                            <label className="flex items-center gap-3">
                                <input
                                    type="checkbox"
                                    checked={dataQualityNotifs}
                                    onChange={(e) => setDataQualityNotifs(e.target.checked)}
                                    className="w-4 h-4 rounded border-gray-300"
                                />
                                <span className={textStyles.body}>Data Quality Alerts</span>
                            </label>
                        </div>
                    </div>

                    <div className="pt-4 border-t border-gray-200 dark:border-gray-700">
                        <button className={buttonStyles.primary}>Save Notification Settings</button>
                    </div>
                </div>
            </div>
        </div>
    );
}

/**
 * API Keys Section
 */
function ApiKeysSection(): React.ReactElement {
    const mockApiKeys = [
        { id: 'key-1', name: 'Production API Key', prefix: 'dc_prod_****', createdAt: '2024-01-01', lastUsed: '2024-01-12' },
        { id: 'key-2', name: 'Development Key', prefix: 'dc_dev_****', createdAt: '2024-01-05', lastUsed: '2024-01-11' },
    ];

    return (
        <div>
            <div className="flex justify-between items-center mb-6">
                <h2 className={textStyles.h2}>API Keys</h2>
                <button className={buttonStyles.primary}>+ Generate New Key</button>
            </div>

            <div className={cn(cardStyles.base, 'max-w-2xl overflow-hidden')}>
                <div className="divide-y divide-gray-200 dark:divide-gray-700">
                    {mockApiKeys.map((key) => (
                        <div key={key.id} className="p-4 flex items-center justify-between">
                            <div>
                                <p className={textStyles.h4}>{key.name}</p>
                                <p className={cn(textStyles.mono, 'mt-1')}>{key.prefix}</p>
                                <p className={cn(textStyles.xs, 'mt-1')}>
                                    Created: {key.createdAt} • Last used: {key.lastUsed}
                                </p>
                            </div>
                            <div className="flex gap-2">
                                <button className={cn(buttonStyles.ghost, buttonStyles.sm)}>Regenerate</button>
                                <button className={cn(buttonStyles.danger, buttonStyles.sm)}>Revoke</button>
                            </div>
                        </div>
                    ))}
                </div>
            </div>

            <div className={cn(cardStyles.base, cardStyles.padded, 'max-w-2xl mt-6')}>
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
