import { expect, afterEach, vi } from 'vitest';
import { cleanup } from '@testing-library/react';
import '@testing-library/jest-dom';

function createStorageMock() {
    const store = new Map<string, string>();

    return {
        getItem: vi.fn((key: string) => store.get(key) ?? null),
        setItem: vi.fn((key: string, value: string) => {
            store.set(key, String(value));
        }),
        removeItem: vi.fn((key: string) => {
            store.delete(key);
        }),
        clear: vi.fn(() => {
            store.clear();
        }),
        key: vi.fn((index: number) => Array.from(store.keys())[index] ?? null),
        get length() {
            return store.size;
        },
    };
}

function ensureStorage(name: 'localStorage' | 'sessionStorage') {
    const current = window[name];
    if (current && typeof current.getItem === 'function') {
        return current;
    }

    const storage = createStorageMock();
    Object.defineProperty(window, name, {
        configurable: true,
        writable: true,
        value: storage,
    });
    return storage;
}

ensureStorage('localStorage');
ensureStorage('sessionStorage');

// Cleanup after each test
afterEach(() => {
    cleanup();
});

// Mock window.matchMedia for dark mode tests
Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation(query => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
    })),
});

// Mock ResizeObserver
global.ResizeObserver = class ResizeObserver {
    observe() { }
    unobserve() { }
    disconnect() { }
};

// Mock IntersectionObserver
global.IntersectionObserver = class IntersectionObserver {
    constructor() { }
    disconnect() { }
    observe() { }
    takeRecords() {
        return [];
    }
    unobserve() { }
} as any;
