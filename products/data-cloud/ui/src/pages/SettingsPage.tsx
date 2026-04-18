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
 * Profile Section
 */
function ProfileSection(): React.ReactElement {
    return <UnavailablePanel boundary={settingsSurfaceBoundaries.profile} />;
}

/**
 * Preferences Section
 */
function PreferencesSection(): React.ReactElement {
    return <UnavailablePanel boundary={settingsSurfaceBoundaries.preferences} />;
}

/**
 * Notifications Section
 */
function NotificationsSection(): React.ReactElement {
    return <UnavailablePanel boundary={settingsSurfaceBoundaries.notifications} />;
}

/**
 * API Keys Section
 */
function ApiKeysSection(): React.ReactElement {
    return (
        <div>
            <UnavailablePanel boundary={settingsSurfaceBoundaries.api} />

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
