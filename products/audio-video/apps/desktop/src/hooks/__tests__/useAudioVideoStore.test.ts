/**
 * Integration tests for useAudioVideoStore configuration loading
 *
 * @doc.type class
 * @doc.purpose Integration tests for service endpoint configuration
 * @doc.layer application
 * @doc.pattern Test
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { useAudioVideoStore } from '../useAudioVideoStore';

describe('useAudioVideoStore Configuration', () => {
  beforeEach(() => {
    // Reset environment variables before each test
    delete (import.meta.env as any).VITE_AV_STT_ENDPOINT;
    delete (import.meta.env as any).VITE_AV_TTS_ENDPOINT;
    delete (import.meta.env as any).VITE_AV_AI_VOICE_ENDPOINT;
    delete (import.meta.env as any).VITE_AV_VISION_ENDPOINT;
    delete (import.meta.env as any).VITE_AV_MULTIMODAL_ENDPOINT;
  });

  afterEach(() => {
    // Clean up after each test
    delete (import.meta.env as any).VITE_AV_STT_ENDPOINT;
    delete (import.meta.env as any).VITE_AV_TTS_ENDPOINT;
    delete (import.meta.env as any).VITE_AV_AI_VOICE_ENDPOINT;
    delete (import.meta.env as any).VITE_AV_VISION_ENDPOINT;
    delete (import.meta.env as any).VITE_AV_MULTIMODAL_ENDPOINT;
  });

  it('should load default localhost endpoints when no environment variables are set', () => {
    const { result } = renderHook(() => useAudioVideoStore());
    
    expect(result.current.settings.services.stt.endpoint).toBe('http://localhost:50051');
    expect(result.current.settings.services.tts.endpoint).toBe('http://localhost:50052');
    expect(result.current.settings.services['ai-voice'].endpoint).toBe('http://localhost:50053');
    expect(result.current.settings.services.vision.endpoint).toBe('http://localhost:50054');
    expect(result.current.settings.services.multimodal.endpoint).toBe('http://localhost:50055');
  });

  it('should load custom endpoints from environment variables', () => {
    (import.meta.env as any).VITE_AV_STT_ENDPOINT = 'https://stt.example.com';
    (import.meta.env as any).VITE_AV_TTS_ENDPOINT = 'https://tts.example.com';
    (import.meta.env as any).VITE_AV_AI_VOICE_ENDPOINT = 'https://ai-voice.example.com';
    (import.meta.env as any).VITE_AV_VISION_ENDPOINT = 'https://vision.example.com';
    (import.meta.env as any).VITE_AV_MULTIMODAL_ENDPOINT = 'https://multimodal.example.com';

    // Re-import the module to pick up new environment variables
    vi.resetModules();
    const { useAudioVideoStore: freshUseAudioVideoStore } = require('../useAudioVideoStore');
    
    const { result } = renderHook(() => freshUseAudioVideoStore());
    
    expect(result.current.settings.services.stt.endpoint).toBe('https://stt.example.com');
    expect(result.current.settings.services.tts.endpoint).toBe('https://tts.example.com');
    expect(result.current.settings.services['ai-voice'].endpoint).toBe('https://ai-voice.example.com');
    expect(result.current.settings.services.vision.endpoint).toBe('https://vision.example.com');
    expect(result.current.settings.services.multimodal.endpoint).toBe('https://multimodal.example.com');
  });

  it('should allow runtime updates to service endpoints', () => {
    const { result } = renderHook(() => useAudioVideoStore());
    
    const newEndpoint = 'https://custom-stt.example.com';
    result.current.updateSettings({
      services: {
        ...result.current.settings.services,
        stt: {
          ...result.current.settings.services.stt,
          endpoint: newEndpoint
        }
      }
    });
    
    expect(result.current.settings.services.stt.endpoint).toBe(newEndpoint);
  });

  it('should preserve other settings when updating a single service endpoint', () => {
    const { result } = renderHook(() => useAudioVideoStore());
    
    const originalTTSEndpoint = result.current.settings.services.tts.endpoint;
    const originalTimeout = result.current.settings.services.stt.timeout;
    
    result.current.updateSettings({
      services: {
        ...result.current.settings.services,
        stt: {
          ...result.current.settings.services.stt,
          endpoint: 'https://new-stt.example.com'
        }
      }
    });
    
    expect(result.current.settings.services.stt.endpoint).toBe('https://new-stt.example.com');
    expect(result.current.settings.services.tts.endpoint).toBe(originalTTSEndpoint);
    expect(result.current.settings.services.stt.timeout).toBe(originalTimeout);
  });

  it('should handle mixed environment variables and defaults', () => {
    (import.meta.env as any).VITE_AV_STT_ENDPOINT = 'https://stt.example.com';
    // Leave other endpoints as defaults
    
    vi.resetModules();
    const { useAudioVideoStore: freshUseAudioVideoStore } = require('../useAudioVideoStore');
    
    const { result } = renderHook(() => freshUseAudioVideoStore());
    
    expect(result.current.settings.services.stt.endpoint).toBe('https://stt.example.com');
    expect(result.current.settings.services.tts.endpoint).toBe('http://localhost:50052'); // Default
    expect(result.current.settings.services['ai-voice'].endpoint).toBe('http://localhost:50053'); // Default
  });
});
