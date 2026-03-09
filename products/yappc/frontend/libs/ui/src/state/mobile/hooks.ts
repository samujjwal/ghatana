/**
 * Mobile State Hooks
 *
 * Convenience hooks for accessing mobile-specific state managed by the shared StateManager.
 *
 * @module state/mobile/hooks
 * @doc.type module
 * @doc.purpose Provide strongly typed hooks for mobile state atoms
 * @doc.layer product
 */

import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import { useCallback } from 'react';

import {
  mobileOfflineSettingsAtom,
  mobilePlatformAtom,
  mobileSettingsAtom,
  mobileNotificationSettingsAtom,
  mobileSystemThemeAtom,
  resolvedMobileThemeAtom,
} from './atoms';

import type { MobilePlatform, MobileSettings, MobileThemePreference } from './atoms';

/**
 * Hook to read the active mobile platform and update it.
 */
export function useMobilePlatform(): [MobilePlatform, (platform: MobilePlatform) => void] {
  return useAtom(mobilePlatformAtom);
}

/**
 * Hook to read the mobile settings and update them.
 */
export function useMobileSettings(): [MobileSettings, (settings: MobileSettings) => void] {
  return useAtom(mobileSettingsAtom);
}

/**
 * Hook to read-only access the mobile settings value.
 */
export function useMobileSettingsValue(): MobileSettings {
  return useAtomValue(mobileSettingsAtom);
}

/**
 * Hook to update the mobile settings atom directly.
 */
export function useSetMobileSettings(): (
  update: MobileSettings | ((prev: MobileSettings) => MobileSettings)
) => void {
  const set = useSetAtom(mobileSettingsAtom);
  return useCallback(
    (update: MobileSettings | ((prev: MobileSettings) => MobileSettings)) => {
      set(update as unknown);
    },
    [set]
  );
}

/**
 * Hook to read and update the mobile theme preference.
 */
export function useMobileThemePreference(): [MobileThemePreference, (preference: MobileThemePreference) => void] {
  const [settings, setSettings] = useMobileSettings();

  const setPreference = useCallback(
    (preference: MobileThemePreference) => {
      setSettings({
        ...settings,
        theme: preference,
      });
    },
    [settings, setSettings]
  );

  return [settings.theme, setPreference];
}

/**
 * Hook exposing the resolved theme value ('light' | 'dark').
 */
export function useResolvedMobileTheme(): 'light' | 'dark' {
  return useAtomValue(resolvedMobileThemeAtom);
}

/**
 * Hook to read and update the detected system theme.
 */
export function useMobileSystemTheme(): ['light' | 'dark', (theme: 'light' | 'dark') => void] {
  return useAtom(mobileSystemThemeAtom);
}

/**
 * Hook to get the mobile notification preferences (read-only).
 */
export function useMobileNotificationSettings(): MobileSettings['notifications'] {
  return useAtomValue(mobileNotificationSettingsAtom);
}

/**
 * Hook to update notification settings via an updater function.
 */
export function useSetMobileNotificationSettings(): (
  updater: (prev: MobileSettings['notifications']) => MobileSettings['notifications']
) => void {
  const settings = useMobileSettingsValue();
  const setSettings = useSetAtom(mobileSettingsAtom);

  return useCallback(
    (updater: (prev: MobileSettings['notifications']) => MobileSettings['notifications']) => {
      setSettings({
        ...settings,
        notifications: updater(settings.notifications),
      });
    },
    [settings, setSettings]
  );
}

/**
 * Hook to get the mobile offline preferences (read-only).
 */
export function useMobileOfflineSettings(): MobileSettings['offline'] {
  return useAtomValue(mobileOfflineSettingsAtom);
}

/**
 * Hook to update offline settings via an updater function.
 */
export function useSetMobileOfflineSettings(): (
  updater: (prev: MobileSettings['offline']) => MobileSettings['offline']
) => void {
  const settings = useMobileSettingsValue();
  const setSettings = useSetAtom(mobileSettingsAtom);

  return useCallback(
    (updater: (prev: MobileSettings['offline']) => MobileSettings['offline']) => {
      setSettings({
        ...settings,
        offline: updater(settings.offline),
      });
    },
    [settings, setSettings]
  );
}
