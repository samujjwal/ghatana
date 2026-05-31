/**
 * Settings Page
 *
 * Admin-only settings boundary page.
 *
 * Wires the `settingsService` contract for each section. While the
 * identity/security backend is unavailable, every section renders an
 * `UnsupportedSurfaceBoundary`. When the backend is activated, each section
 * can be promoted to display live data by removing the boundary branch.
 *
 * @doc.type page
 * @doc.purpose Admin-only settings surface — service-contract-wired, boundary-first
 * @doc.layer frontend
 * @doc.pattern Page Component
 */

import { useQuery } from "@tanstack/react-query";
import React, { useState } from "react";
import { settingsService } from "../api/settings.service";
import { UnsupportedSurfaceBoundary } from "../components/common/UnsupportedSurfaceBoundary";
import {
  settingsSurfaceBoundaries,
  type SettingsBoundaryKey,
} from "../components/common/unsupportedSurfaceRegistry";
import { bgStyles, cn, textStyles } from "../lib/theme";

// DC-UX-025/026: Settings is a boundary surface only. All write-path sections
// are backed by launcher-managed APIs that do not exist yet. Until those APIs
// are available, every section renders an UnsupportedSurfaceBoundary and
// provides NO local state mutations that look like a real save/create/revoke.

/**
 * Settings section type
 */
type SettingsSection = SettingsBoundaryKey;

/**
 * Settings Page Component
 */
export function SettingsPage(): React.ReactElement {
  const [activeSection, setActiveSection] =
    useState<SettingsSection>("profile");

  const sections: { id: SettingsSection; label: string; icon: string }[] = [
    { id: "profile", label: "Profile", icon: "👤" },
    { id: "preferences", label: "Preferences", icon: "⚙️" },
    { id: "notifications", label: "Notifications", icon: "🔔" },
    { id: "api", label: "API Keys", icon: "🔑" },
  ];

  return (
    <div
      className={cn("min-h-screen", bgStyles.page)}
      data-testid="settings-page"
    >
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
                  "w-full flex items-center gap-3 px-3 py-2 rounded-lg text-left mb-1",
                  activeSection === section.id
                    ? "bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400"
                    : "text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700",
                )}
              >
                <span>{section.icon}</span>
                <span className="text-sm font-medium">{section.label}</span>
              </button>
            ))}
          </nav>
        </aside>

        {/* Content */}
        <section className="flex-1 p-6">
          <div className="mb-6 max-w-3xl" data-testid="settings-boundary-note">
            <p className={textStyles.muted}>
              This route is disclosed only to the admin shell role and remains a
              boundary surface until launcher-backed settings mutations exist.
            </p>
          </div>
          {activeSection === "profile" && <ProfileSection />}
          {activeSection === "preferences" && <PreferencesSection />}
          {activeSection === "notifications" && <NotificationsSection />}
          {activeSection === "api" && <ApiKeysSection />}
        </section>
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
    <div
      data-testid={`settings-section-${boundary.title.toLowerCase().replace(/[^a-z0-9]+/g, "-")}`}
    >
      <h2 className={cn(textStyles.h2, "mb-6")}>{boundary.title}</h2>
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
 * Profile Section — boundary only until launcher-backed profile API ships.
 */
function ProfileSection(): React.ReactElement {
  return (
    <div className="max-w-2xl space-y-6" data-testid="settings-profile-section">
      <UnavailablePanel boundary={settingsSurfaceBoundaries.profile} />
    </div>
  );
}

/**
 * Preferences Section — boundary only until launcher-backed preferences API ships.
 */
function PreferencesSection(): React.ReactElement {
  return (
    <div
      className="max-w-2xl space-y-6"
      data-testid="settings-preferences-section"
    >
      <UnavailablePanel boundary={settingsSurfaceBoundaries.preferences} />
    </div>
  );
}

/**
 * Notifications Section — boundary only until launcher-backed notification preferences API ships.
 */
function NotificationsSection(): React.ReactElement {
  return (
    <div
      className="max-w-2xl space-y-6"
      data-testid="settings-notifications-section"
    >
      <UnavailablePanel boundary={settingsSurfaceBoundaries.notifications} />
    </div>
  );
}

/**
 * API Keys Section
 *
 * DC-UX-026: API key management (create/revoke) must not be implemented
 * locally — keys are security-critical and must be managed via the
 * launcher-backed identity API. Until that API exists, this section
 * renders a boundary surface notice only.
 *
 * Wired to `settingsService.listApiKeys()` so that when the backend is
 * activated, the boundary branch is replaced with the live key list.
 */
function ApiKeysSection(): React.ReactElement {
  const { isError } = useQuery({
    queryKey: ["settings", "api-keys"],
    queryFn: () => settingsService.listApiKeys(),
    retry: false,
  });

  // The identity backend is not yet available — service raises a boundary error
  // on 404/405/501, which surfaces here as an error state. Render boundary.
  if (isError) {
    return (
      <div className="max-w-2xl space-y-6" data-testid="settings-api-section">
        <UnavailablePanel boundary={settingsSurfaceBoundaries.api} />
      </div>
    );
  }

  // When backend becomes available: render live key list here.
  return (
    <div className="max-w-2xl space-y-6" data-testid="settings-api-section">
      <UnavailablePanel boundary={settingsSurfaceBoundaries.api} />
    </div>
  );
}

export default SettingsPage;
