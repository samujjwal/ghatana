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

function UnavailablePanel({
    title,
    summary,
    details,
}: {
    title: string;
    summary: string;
    details: string[];
}): React.ReactElement {
    return (
        <div>
            <h2 className={cn(textStyles.h2, 'mb-6')}>{title}</h2>

            <div className={cn(cardStyles.base, cardStyles.padded, 'max-w-3xl')}>
                <div className="space-y-4">
                    <div className="rounded-lg border border-amber-300 bg-amber-50 p-4 text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200">
                        <p className="font-medium">Unavailable in current deployment</p>
                        <p className="mt-1 text-sm">{summary}</p>
                    </div>

                    <div>
                        <h3 className={cn(textStyles.h3, 'mb-3')}>Current boundary</h3>
                        <ul className="list-disc pl-5 space-y-2 text-sm text-gray-700 dark:text-gray-300">
                            {details.map((detail) => (
                                <li key={detail}>{detail}</li>
                            ))}
                        </ul>
                    </div>
                </div>
            </div>
        </div>
    );
}

/**
 * Profile Section
 */
function ProfileSection(): React.ReactElement {
    return (
        <UnavailablePanel
            title="Profile Settings"
            summary="User profile management is not exposed by the current Data Cloud UI backend, so this page no longer fabricates operator identity fields."
            details={[
                'Profile data must come from the authenticated identity provider or a dedicated user-profile API.',
                'Role and tenant membership are runtime concerns and should be surfaced from auth or session state, not hard-coded defaults.',
                'No save action is shown until a real write endpoint exists.',
            ]}
        />
    );
}

/**
 * Preferences Section
 */
function PreferencesSection(): React.ReactElement {
    return (
        <UnavailablePanel
            title="Preferences"
            summary="Preference persistence is not wired to a user settings API in this deployment."
            details={[
                'Theme, locale, timezone, and date formatting should be backed by a real user-preference store.',
                'Showing static defaults would imply persistence that does not exist.',
                'The shell still exposes the section so the missing capability is explicit instead of being faked.',
            ]}
        />
    );
}

/**
 * Notifications Section
 */
function NotificationsSection(): React.ReactElement {
    return (
        <UnavailablePanel
            title="Notification Settings"
            summary="Notification channel preferences are not backed by a delivery or user-preference service here."
            details={[
                'Email, Slack, workflow, and quality-alert subscriptions require a real notification backend.',
                'Hard-coded checked states looked live but were not connected to delivery behavior.',
                'Operators should configure notifications through the owning service until this surface is implemented.',
            ]}
        />
    );
}

/**
 * API Keys Section
 */
function ApiKeysSection(): React.ReactElement {
    return (
        <div>
            <UnavailablePanel
                title="API Keys"
                summary="API key inventory and rotation are enforced at launcher bootstrap, but the current UI does not expose key-management endpoints."
                details={[
                    'No API key list is rendered because there is no safe read endpoint for secret material or key metadata in this UI surface.',
                    'No Generate, Regenerate, or Revoke action is shown until a dedicated management API exists.',
                    'For non-local profiles, runtime enforcement is driven by DATACLOUD_API_KEYS and launcher validation.',
                ]}
            />

            <div className={cn(cardStyles.base, cardStyles.padded, 'max-w-3xl mt-6')}>
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
