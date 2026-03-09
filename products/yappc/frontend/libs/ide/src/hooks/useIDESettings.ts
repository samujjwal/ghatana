/**
 * @ghatana/yappc-ide - IDE Settings Hook
 * 
 * React hook for managing IDE settings with Jotai atoms.
 * 
 * @doc.type hook
 * @doc.purpose IDE settings management
 * @doc.layer product
 * @doc.pattern React Hook
 */

import { useCallback } from 'react';
import { useAtom } from 'jotai';
import { ideSettingsAtom } from '../state/atoms';
import type { IDEWorkspaceSettings } from '../types';

/**
 * IDE settings hook return value
 */
export interface UseIDESettingsReturn {
  /** Current settings */
  settings: IDEWorkspaceSettings;
  /** Update settings */
  updateSettings: (settings: Partial<IDEWorkspaceSettings>) => void;
  /** Reset settings to defaults */
  resetSettings: () => void;
  /** Save settings to localStorage */
  saveSettings: () => void;
  /** Load settings from localStorage */
  loadSettings: () => void;
  /** Get setting value */
  getSetting: <K extends keyof IDEWorkspaceSettings>(key: K) => IDEWorkspaceSettings[K];
  /** Set setting value */
  setSetting: <K extends keyof IDEWorkspaceSettings>(key: K, value: IDEWorkspaceSettings[K]) => void;
}

/**
 * Default IDE settings
 */
const DEFAULT_SETTINGS: IDEWorkspaceSettings = {
  theme: 'dark',
  fontSize: 14,
  tabSize: 2,
  insertSpaces: true,
  autoSave: 'afterDelay',
  autoSaveDelay: 1000,
  formatOnSave: true,
  formatOnPaste: true,
  minimap: true,
  lineNumbers: true,
  wordWrap: true,
};

/**
 * IDE Settings Hook
 * 
 * @doc.returns Settings management utilities
 */
export function useIDESettings(): UseIDESettingsReturn {
  const [settings, setSettings] = useAtom(ideSettingsAtom);

  const updateSettings = useCallback((newSettings: Partial<IDEWorkspaceSettings>) => {
    setSettings(prev => ({ ...prev, ...newSettings }));
  }, [setSettings]);

  const resetSettings = useCallback(() => {
    setSettings(DEFAULT_SETTINGS);
  }, [setSettings]);

  const saveSettings = useCallback(() => {
    try {
      localStorage.setItem('ide-settings', JSON.stringify(settings));
    } catch (error) {
      console.error('Failed to save settings:', error);
    }
  }, [settings]);

  const loadSettings = useCallback(() => {
    try {
      const saved = localStorage.getItem('ide-settings');
      if (saved) {
        const parsedSettings = JSON.parse(saved);
        setSettings({ ...DEFAULT_SETTINGS, ...parsedSettings });
      }
    } catch (error) {
      console.error('Failed to load settings:', error);
      setSettings(DEFAULT_SETTINGS);
    }
  }, [setSettings]);

  const getSetting = useCallback(<K extends keyof IDEWorkspaceSettings>(key: K): IDEWorkspaceSettings[K] => {
    return settings[key];
  }, [settings]);

  const setSetting = useCallback(<K extends keyof IDEWorkspaceSettings>(key: K, value: IDEWorkspaceSettings[K]) => {
    setSettings(prev => ({ ...prev, [key]: value }));
  }, [setSettings]);

  return {
    settings,
    updateSettings,
    resetSettings,
    saveSettings,
    loadSettings,
    getSetting,
    setSetting,
  };
}
