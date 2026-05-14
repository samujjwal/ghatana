import '@testing-library/jest-dom';
import { vi } from 'vitest';

// Mock global APIs that might not be available in test environment
Object.defineProperty(window, 'SpeechRecognition', {
  writable: true,
  value: vi.fn(),
});

Object.defineProperty(window, 'webkitSpeechRecognition', {
  writable: true,
  value: vi.fn(),
});

// Mock localStorage
const localStorageMock = {
  getItem: vi.fn(),
  setItem: vi.fn(),
  removeItem: vi.fn(),
  clear: vi.fn(),
};
Object.defineProperty(window, 'localStorage', {
  value: localStorageMock,
});

// Mock fetch
global.fetch = vi.fn();
