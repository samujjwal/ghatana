/**
 * @doc.type hook
 * @doc.purpose Jotai atoms for audio-video application state
 * @doc.layer application
 * @doc.pattern state management
 */

import { atom, useAtom, useAtomValue, useSetAtom } from 'jotai';
import type { AudioVideoSettings, ServiceType } from '@audio-video/types';

interface AudioVideoStore {
  // Settings
  settings: AudioVideoSettings;
  updateSettings: (settings: Partial<AudioVideoSettings>) => void;
  
  // UI State
  activeService: ServiceType;
  setActiveService: (service: ServiceType) => void;
  
  // Processing state
  isProcessing: boolean;
  setIsProcessing: (processing: boolean) => void;
  
  // Error state
  error: string | null;
  setError: (error: string | null) => void;
}

const defaultSettings: AudioVideoSettings = {
  services: {
    stt: {
      enabled: true,
      endpoint: 'http://localhost:50051',
      timeout: 30000,
      retries: 3,
      customSettings: {}
    },
    tts: {
      enabled: true,
      endpoint: 'http://localhost:50052',
      timeout: 30000,
      retries: 3,
      customSettings: {}
    },
    'ai-voice': {
      enabled: true,
      endpoint: 'http://localhost:50053',
      timeout: 30000,
      retries: 3,
      customSettings: {}
    },
    vision: {
      enabled: true,
      endpoint: 'http://localhost:50054',
      timeout: 30000,
      retries: 3,
      customSettings: {}
    },
    multimodal: {
      enabled: true,
      endpoint: 'http://localhost:50055',
      timeout: 60000,
      retries: 3,
      customSettings: {}
    }
  },
  ui: {
    theme: 'auto',
    language: 'en-US',
    fontSize: 'medium',
    layout: 'comfortable',
    animations: true,
    notifications: true
  },
  performance: {
    enableGPU: false,
    maxConcurrentRequests: 5,
    cacheSize: 1024,
    enableCompression: true,
    bufferSize: 8192
  },
  accessibility: {
    highContrast: false,
    reduceMotion: false,
    screenReader: false,
    keyboardNavigation: true,
    fontSize: 16,
    colorBlindMode: 'none'
  }
};

// ── Atoms ────────────────────────────────────────────────────────────────

export const settingsAtom = atom<AudioVideoSettings>(defaultSettings);
export const activeServiceAtom = atom<ServiceType>('stt');
export const isProcessingAtom = atom(false);
export const errorAtom = atom<string | null>(null);

export const updateSettingsAtom = atom(
  null,
  (get, set, newSettings: Partial<AudioVideoSettings>) => {
    set(settingsAtom, { ...get(settingsAtom), ...newSettings });
  }
);

// ── Composite hook (drop-in replacement for the old useAudioVideoStore) ───────
export function useAudioVideoStore() {
  const [settings, setSettings] = useAtom(settingsAtom);
  const [activeService, setActiveService] = useAtom(activeServiceAtom);
  const [isProcessing, setIsProcessing] = useAtom(isProcessingAtom);
  const [error, setError] = useAtom(errorAtom);

  const updateSettings = (newSettings: Partial<AudioVideoSettings>) => {
    setSettings((prev) => ({ ...prev, ...newSettings }));
  };

  return { settings, updateSettings, activeService, setActiveService, isProcessing, setIsProcessing, error, setError };
}
