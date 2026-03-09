/**
 * @ghatana/ai-voice-ui-react - useStemMixer
 * 
 * Hook for stem mixing control.
 * 
 * @doc.type function
 * @doc.purpose Stem mixer hook
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useCallback } from 'react';
import type { StemSet, StemType, MixerState, StemMixSettings } from '../types';

export interface UseStemMixerOptions {
  /** Initial stems */
  stems?: StemSet;
  
  /** Initial master volume */
  masterVolume?: number;
}

export interface UseStemMixerResult {
  /** Mixer state */
  state: MixerState;
  
  /** Set stem volume */
  setVolume: (stemType: StemType, volume: number) => void;
  
  /** Set stem pan */
  setPan: (stemType: StemType, pan: number) => void;
  
  /** Toggle stem mute */
  toggleMute: (stemType: StemType) => void;
  
  /** Toggle stem solo */
  toggleSolo: (stemType: StemType) => void;
  
  /** Set master volume */
  setMasterVolume: (volume: number) => void;
  
  /** Reset all settings */
  reset: () => void;
  
  /** Get effective volumes (accounting for mute/solo) */
  getEffectiveVolumes: () => Record<StemType, number>;
}

const defaultStemSettings: StemMixSettings = {
  volume: 1.0,
  pan: 0,
  muted: false,
  solo: false,
};

/**
 * Hook for stem mixing control.
 */
export function useStemMixer(options: UseStemMixerOptions = {}): UseStemMixerResult {
  const { stems, masterVolume = 1.0 } = options;
  
  const [state, setState] = useState<MixerState>(() => ({
    masterVolume,
    stems: {
      vocals: { ...defaultStemSettings },
      drums: { ...defaultStemSettings },
      bass: { ...defaultStemSettings },
      other: { ...defaultStemSettings },
    },
    effects: [],
  }));

  const setVolume = useCallback((stemType: StemType, volume: number) => {
    setState(prev => ({
      ...prev,
      stems: {
        ...prev.stems,
        [stemType]: {
          ...prev.stems[stemType],
          volume: Math.max(0, Math.min(2, volume)),
        },
      },
    }));
  }, []);

  const setPan = useCallback((stemType: StemType, pan: number) => {
    setState(prev => ({
      ...prev,
      stems: {
        ...prev.stems,
        [stemType]: {
          ...prev.stems[stemType],
          pan: Math.max(-1, Math.min(1, pan)),
        },
      },
    }));
  }, []);

  const toggleMute = useCallback((stemType: StemType) => {
    setState(prev => ({
      ...prev,
      stems: {
        ...prev.stems,
        [stemType]: {
          ...prev.stems[stemType],
          muted: !prev.stems[stemType]?.muted,
        },
      },
    }));
  }, []);

  const toggleSolo = useCallback((stemType: StemType) => {
    setState(prev => ({
      ...prev,
      stems: {
        ...prev.stems,
        [stemType]: {
          ...prev.stems[stemType],
          solo: !prev.stems[stemType]?.solo,
        },
      },
    }));
  }, []);

  const setMasterVolume = useCallback((volume: number) => {
    setState(prev => ({
      ...prev,
      masterVolume: Math.max(0, Math.min(2, volume)),
    }));
  }, []);

  const reset = useCallback(() => {
    setState({
      masterVolume: 1.0,
      stems: {
        vocals: { ...defaultStemSettings },
        drums: { ...defaultStemSettings },
        bass: { ...defaultStemSettings },
        other: { ...defaultStemSettings },
      },
      effects: [],
    });
  }, []);

  const getEffectiveVolumes = useCallback((): Record<StemType, number> => {
    const stemTypes: StemType[] = ['vocals', 'drums', 'bass', 'other'];
    const hasSolo = stemTypes.some(type => state.stems[type]?.solo);
    
    const volumes: Record<StemType, number> = {
      vocals: 0,
      drums: 0,
      bass: 0,
      other: 0,
    };
    
    for (const type of stemTypes) {
      const settings = state.stems[type];
      if (!settings) continue;
      
      if (settings.muted) {
        volumes[type] = 0;
      } else if (hasSolo && !settings.solo) {
        volumes[type] = 0;
      } else {
        volumes[type] = settings.volume * state.masterVolume;
      }
    }
    
    return volumes;
  }, [state]);

  return {
    state,
    setVolume,
    setPan,
    toggleMute,
    toggleSolo,
    setMasterVolume,
    reset,
    getEffectiveVolumes,
  };
}
