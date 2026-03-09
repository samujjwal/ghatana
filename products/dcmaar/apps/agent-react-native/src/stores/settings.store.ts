/**
 * Settings State Management Store - Jotai Atoms
 *
 * Manages user preferences and application settings including:
 * - Language and localization
 * - Notification preferences
 * - Privacy settings
 * - App-specific preferences
 * - Device-specific settings
 *
 * Per copilot-instructions.md:
 * - App-scoped state using Jotai atoms
 * - Feature-centric organization
 * - Atomic updates for predictable state
 *
 * @doc.type module
 * @doc.purpose Settings and preferences state management
 * @doc.layer product
 * @doc.pattern Jotai Store
 */

import { atom } from 'jotai';

/**
 * Notification preferences.
 *
 * @interface NotificationPreferences
 * @property {boolean} pushEnabled - Allow push notifications
 * @property {boolean} emailEnabled - Allow email notifications
 * @property {boolean} smsEnabled - Allow SMS notifications
 * @property {boolean} quietHoursEnabled - Enable quiet hours
 * @property {string} quietHoursStart - Start time (HH:MM)
 * @property {string} quietHoursEnd - End time (HH:MM)
 */
export interface NotificationPreferences {
  pushEnabled: boolean;
  emailEnabled: boolean;
  smsEnabled: boolean;
  quietHoursEnabled: boolean;
  quietHoursStart: string;
  quietHoursEnd: string;
}

/**
 * Privacy settings.
 *
 * @interface PrivacySettings
 * @property {boolean} analyticsEnabled - Send usage analytics
 * @property {boolean} crashReportingEnabled - Send crash reports
 * @property {boolean} locationEnabled - Share location data
 * @property {'public' | 'friends' | 'private'} profileVisibility - Profile visibility level
 * @property {boolean} dataMinimization - Minimize data collection
 */
export interface PrivacySettings {
  analyticsEnabled: boolean;
  crashReportingEnabled: boolean;
  locationEnabled: boolean;
  profileVisibility: 'public' | 'friends' | 'private';
  dataMinimization: boolean;
}

/**
 * User preferences configuration.
 *
 * @interface SettingsState
 * @property {string} language - Selected language (e.g., 'en-US', 'es-ES')
 * @property {string} timezone - User timezone
 * @property {NotificationPreferences} notifications - Notification settings
 * @property {PrivacySettings} privacy - Privacy settings
 * @property {boolean} darkModeEnabled - Prefer dark mode
 * @property {boolean} autoLockEnabled - Auto-lock after inactivity
 * @property {number} autoLockTimeoutSecs - Seconds before auto-lock
 * @property {Record<string, any>} customPrefs - Custom preference map
 * @property {string | null} error - Settings operation error message
 */
export interface SettingsState {
  language: string;
  timezone: string;
  notifications: NotificationPreferences;
  privacy: PrivacySettings;
  darkModeEnabled: boolean;
  autoLockEnabled: boolean;
  autoLockTimeoutSecs: number;
  customPrefs: Record<string, any>;
  error: string | null;
}

/**
 * Default notification preferences.
 */
const defaultNotificationPreferences: NotificationPreferences = {
  pushEnabled: true,
  emailEnabled: false,
  smsEnabled: false,
  quietHoursEnabled: false,
  quietHoursStart: '22:00',
  quietHoursEnd: '08:00',
};

/**
 * Default privacy settings.
 */
const defaultPrivacySettings: PrivacySettings = {
  analyticsEnabled: true,
  crashReportingEnabled: true,
  locationEnabled: false,
  profileVisibility: 'private',
  dataMinimization: false,
};

/**
 * Initial settings state.
 *
 * GIVEN: App initialization or first load
 * WHEN: settingsAtom is first accessed
 * THEN: Settings load with defaults
 */
const initialSettingsState: SettingsState = {
  language: 'en-US',
  timezone: 'UTC',
  notifications: defaultNotificationPreferences,
  privacy: defaultPrivacySettings,
  darkModeEnabled: false,
  autoLockEnabled: true,
  autoLockTimeoutSecs: 300, // 5 minutes
  customPrefs: {},
  error: null,
};

/**
 * Core settings atom.
 *
 * Holds complete user settings including:
 * - Language and localization
 * - Notification preferences
 * - Privacy settings
 * - Custom preferences
 *
 * Usage (in components):
 * `const [settings, setSettings] = useAtom(settingsAtom);`
 */
export const settingsAtom = atom<SettingsState>(initialSettingsState);

/**
 * Derived atom: Current language.
 *
 * GIVEN: settingsAtom with language
 * WHEN: languageAtom is read
 * THEN: Returns selected language code
 *
 * Usage (in components):
 * `const [language] = useAtom(languageAtom);`
 * Apply language to UI
 */
export const languageAtom = atom<string>((get) => {
  return get(settingsAtom).language;
});

/**
 * Derived atom: Notification preferences.
 *
 * GIVEN: settingsAtom with notifications
 * WHEN: notificationPreferencesAtom is read
 * THEN: Returns notification settings
 *
 * Usage (in components):
 * `const [prefs] = useAtom(notificationPreferencesAtom);`
 */
export const notificationPreferencesAtom = atom<NotificationPreferences>(
  (get) => {
    return get(settingsAtom).notifications;
  }
);

/**
 * Derived atom: Privacy settings.
 *
 * GIVEN: settingsAtom with privacy
 * WHEN: privacySettingsAtom is read
 * THEN: Returns privacy settings
 *
 * Usage (in components):
 * `const [privacy] = useAtom(privacySettingsAtom);`
 */
export const privacySettingsAtom = atom<PrivacySettings>((get) => {
  return get(settingsAtom).privacy;
});

/**
 * Derived atom: Auto-lock status.
 *
 * GIVEN: settingsAtom with autoLockEnabled
 * WHEN: isAutoLockEnabledAtom is read
 * THEN: Returns auto-lock status
 *
 * Usage (in components):
 * `const [isAutoLocked] = useAtom(isAutoLockEnabledAtom);`
 */
export const isAutoLockEnabledAtom = atom<boolean>((get) => {
  return get(settingsAtom).autoLockEnabled;
});

/**
 * Action atom: Update language.
 *
 * GIVEN: User selects language
 * WHEN: setLanguageAtom is called
 * THEN: Updates language setting
 *       Clears error
 *
 * Usage (in components):
 * `const [, setLanguage] = useAtom(setLanguageAtom);`
 * setLanguage('es-ES');
 */
export const setLanguageAtom = atom<null, [language: string], void>(
  null,
  (get, set, language: string) => {
    const settings = get(settingsAtom);
    set(settingsAtom, {
      ...settings,
      language,
      error: null,
    });
  }
);

/**
 * Action atom: Update timezone.
 *
 * GIVEN: User changes timezone
 * WHEN: setTimezoneAtom is called
 * THEN: Updates timezone setting
 *
 * Usage (in components):
 * `const [, setTimezone] = useAtom(setTimezoneAtom);`
 * setTimezone('America/New_York');
 */
export const setTimezoneAtom = atom<null, [timezone: string], void>(
  null,
  (get, set, timezone: string) => {
    const settings = get(settingsAtom);
    set(settingsAtom, {
      ...settings,
      timezone,
      error: null,
    });
  }
);

/**
 * Action atom: Update notification preferences.
 *
 * GIVEN: User changes notification settings
 * WHEN: updateNotificationPrefsAtom is called
 * THEN: Merges updated preferences
 *
 * Usage (in components):
 * `const [, updateNotifPrefs] = useAtom(updateNotificationPrefsAtom);`
 * updateNotifPrefs({ pushEnabled: false });
 */
export const updateNotificationPrefsAtom = atom<
  null,
  [Partial<NotificationPreferences>],
  void
>(
  null,
  (get, set, updates) => {
    const settings = get(settingsAtom);
    set(settingsAtom, {
      ...settings,
      notifications: {
        ...settings.notifications,
        ...updates,
      },
      error: null,
    });
  }
);

/**
 * Action atom: Update privacy settings.
 *
 * GIVEN: User changes privacy settings
 * WHEN: updatePrivacySettingsAtom is called
 * THEN: Merges updated settings
 *
 * Usage (in components):
 * `const [, updatePrivacy] = useAtom(updatePrivacySettingsAtom);`
 * updatePrivacy({ analyticsEnabled: false });
 */
export const updatePrivacySettingsAtom = atom<null, [Partial<PrivacySettings>], void>(
  null,
  (get, set, updates) => {
    const settings = get(settingsAtom);
    set(settingsAtom, {
      ...settings,
      privacy: {
        ...settings.privacy,
        ...updates,
      },
      error: null,
    });
  }
);

/**
 * Action atom: Toggle auto-lock.
 *
 * GIVEN: User enables/disables auto-lock
 * WHEN: toggleAutoLockAtom is called
 * THEN: Toggles autoLockEnabled
 *
 * Usage (in components):
 * `const [, toggleAutoLock] = useAtom(toggleAutoLockAtom);`
 * toggleAutoLock();
 */
export const toggleAutoLockAtom = atom<null, [], void>(
  null,
  (get, set) => {
    const settings = get(settingsAtom);
    set(settingsAtom, {
      ...settings,
      autoLockEnabled: !settings.autoLockEnabled,
      error: null,
    });
  }
);

/**
 * Action atom: Set auto-lock timeout.
 *
 * GIVEN: User configures auto-lock timeout
 * WHEN: setAutoLockTimeoutAtom is called
 * THEN: Updates timeout in seconds
 *
 * Usage (in components):
 * `const [, setAutoLockTimeout] = useAtom(setAutoLockTimeoutAtom);`
 * setAutoLockTimeout(600); // 10 minutes
 */
export const setAutoLockTimeoutAtom = atom<null, [timeoutSecs: number], void>(
  null,
  (get, set, timeoutSecs: number) => {
    const settings = get(settingsAtom);
    set(settingsAtom, {
      ...settings,
      autoLockTimeoutSecs: timeoutSecs,
      error: null,
    });
  }
);

/**
 * Action atom: Set custom preference.
 *
 * GIVEN: App or component stores custom preference
 * WHEN: setCustomPrefAtom is called
 * THEN: Stores in customPrefs map
 *
 * Usage (in components):
 * `const [, setCustomPref] = useAtom(setCustomPrefAtom);`
 * setCustomPref('sidebar_width', 250);
 */
export const setCustomPrefAtom = atom<null, [key: string, value: unknown], void>(
  null,
  (get, set, key: string, value: unknown) => {
    const settings = get(settingsAtom);
    set(settingsAtom, {
      ...settings,
      customPrefs: {
        ...settings.customPrefs,
        [key]: value,
      },
      error: null,
    });
  }
);

/**
 * Action atom: Reset to defaults.
 *
 * GIVEN: User clicks "Reset Settings"
 * WHEN: resetSettingsAtom is called
 * THEN: Restores all settings to defaults
 *
 * Usage (in components):
 * `const [, reset] = useAtom(resetSettingsAtom);`
 * reset();
 */
export const resetSettingsAtom = atom<null, [], void>(
  null,
  (_get, set) => {
    set(settingsAtom, initialSettingsState);
  }
);

/**
 * Action atom: Set settings error.
 *
 * GIVEN: Settings operation fails
 * WHEN: setSettingsErrorAtom is called
 * THEN: Stores error message
 *
 * Usage (in store/service):
 * `const [, setError] = useAtom(setSettingsErrorAtom);`
 * setError('Failed to save settings');
 */
export const setSettingsErrorAtom = atom<null, [error: string | null], void>(
  null,
  (get, set, error: string | null) => {
    const settings = get(settingsAtom);
    set(settingsAtom, {
      ...settings,
      error,
    });
  }
);
