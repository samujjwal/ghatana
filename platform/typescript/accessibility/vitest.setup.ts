import '@testing-library/jest-dom';
import { vi } from 'vitest';

Object.defineProperty(window, 'SpeechRecognition', {
  writable: true,
  value: vi.fn(),
});

Object.defineProperty(window, 'webkitSpeechRecognition', {
  writable: true,
  value: vi.fn(),
});

Object.defineProperty(window, 'localStorage', {
  value: {
    getItem: vi.fn(),
    setItem: vi.fn(),
    removeItem: vi.fn(),
    clear: vi.fn(),
  },
});

global.fetch = vi.fn();
