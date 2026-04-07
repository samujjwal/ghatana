import '@testing-library/jest-dom/vitest';

Object.defineProperty(globalThis, 'localStorage', {
	value: {
		getItem: () => null,
		setItem: () => undefined,
		removeItem: () => undefined,
		clear: () => undefined,
	},
	writable: true,
});