/**
 * Mobile State Manager Atoms
 *
 * Centralized mobile-specific state definitions powered by @ghatana/state primitives.
 *
 * @module state/mobile/atoms
 * @doc.type module
 * @doc.purpose Provide shared mobile state atoms for the App Creator mobile shell
 * @doc.layer product
 */

import {
  createAtom,
  createPersistentAtom,
  createDerivedAtom,
} from '@ghatana/state';

/**
 * Namespace used for all mobile state keys.
 */
const MOBILE_NAMESPACE = 'mobile';

/**
 * Helper to create namespaced mobile atom keys.
 */
const createMobileKey = (suffix: string): string =>
  `${MOBILE_NAMESPACE}:${suffix}`;

/**
 * Supported runtime platforms for the mobile shell.
 */
export type MobilePlatform = 'web' | 'desktop' | 'mobile';

/**
 * Tracks the active execution platform for the mobile shell.
 */
export const mobilePlatformAtom = createAtom<MobilePlatform>(
  createMobileKey('platform'),
  'mobile',
  'Active execution platform for the mobile shell'
);

/**
 * Theme preference values supported by the mobile app.
 */
export type MobileThemePreference = 'light' | 'dark' | 'auto';

/**
 * Persistent mobile application settings shared across the shell.
 */
export interface MobileSettings {
  notifications: {
    enabled: boolean;
    sound: boolean;
    badge: boolean;
  };
  offline: {
    enabled: boolean;
    syncOnWifi: boolean;
  };
  theme: MobileThemePreference;
  language: string;
}

/**
 * Create the default mobile settings payload.
 */
const createDefaultMobileSettings = (): MobileSettings => ({
  notifications: {
    enabled: false,
    sound: true,
    badge: true,
  },
  offline: {
    enabled: false,
    syncOnWifi: true,
  },
  theme: 'auto',
  language: 'en',
});

/**
 * Persistent storage for mobile application settings.
 */
export const mobileSettingsAtom =
  createPersistentAtom<MobileSettings>(
    createMobileKey('settings'),
    createDefaultMobileSettings(),
    {
      storage: 'localStorage',
      storageKey: createMobileKey('settings'),
    },
    'Mobile application persistent settings'
  );

/**
 * Read-only atom exposing the current mobile theme preference.
 */
export const mobileThemePreferenceAtom =
  createDerivedAtom<MobileThemePreference>(
    createMobileKey('themePreference'),
    (get) => get(mobileSettingsAtom).theme
  );

/**
 * Utility to resolve the system colour scheme.
 */
export const resolveSystemTheme = (): 'light' | 'dark' => {
  if (
    typeof window === 'undefined' ||
    typeof window.matchMedia !== 'function'
  ) {
    return 'light';
  }

  return window.matchMedia('(prefers-color-scheme: dark)').matches
    ? 'dark'
    : 'light';
};

/**
 * Tracks the detected system colour scheme for auto theme resolution.
 */
export const mobileSystemThemeAtom = createAtom<'light' | 'dark'>(
  createMobileKey('systemTheme'),
  resolveSystemTheme(),
  'Detected system colour scheme for the mobile shell'
);

/**
 * Derived atom providing the effective theme taking "auto" into account.
 */
export const resolvedMobileThemeAtom = createDerivedAtom<
  'light' | 'dark'
>(
  createMobileKey('resolvedTheme'),
  (get) => {
    const preference = get(mobileThemePreferenceAtom);
    const systemTheme = get(mobileSystemThemeAtom);
    return preference === 'auto' ? systemTheme : preference;
  }
);

/**
 * Derived atom exposing notification preferences for convenience.
 */
export const mobileNotificationSettingsAtom = createDerivedAtom(
  createMobileKey('notificationSettings'),
  (get) => get(mobileSettingsAtom).notifications
);

/**
 * Derived atom exposing offline preferences for convenience.
 */
export const mobileOfflineSettingsAtom = createDerivedAtom(
  createMobileKey('offlineSettings'),
  (get) => get(mobileSettingsAtom).offline
);
