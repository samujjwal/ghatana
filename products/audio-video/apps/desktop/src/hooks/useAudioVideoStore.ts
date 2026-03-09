/**
 * @doc.type hook
 * @doc.purpose Zustand store for audio-video application state
 * @doc.layer application
 * @doc.pattern state management
 */

import { create } from 'zustand';
import type { AudioVideoSettings, ServiceType } from '@ghatana/audio-video-types';

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

export const useAudioVideoStore = create<AudioVideoStore>((set, get) => ({
  settings: defaultSettings,
  updateSettings: (newSettings) => set((state) => ({
    settings: { ...state.settings, ...newSettings }
  })),
  
  activeService: 'stt',
  setActiveService: (service) => set({ activeService: service }),
  
  isProcessing: false,
  setIsProcessing: (processing) => set({ isProcessing: processing }),
  
  error: null,
  setError: (error) => set({ error })
}));
