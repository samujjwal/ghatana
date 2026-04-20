import '@testing-library/jest-dom/vitest';

const storageFallback = {
	getItem: () => null,
	setItem: () => undefined,
	removeItem: () => undefined,
	clear: () => undefined,
	key: () => null,
	length: 0,
};

if (typeof window !== 'undefined') {
	const currentStorage = window.localStorage as Storage | undefined;
	if (
		!currentStorage ||
		typeof currentStorage.getItem !== 'function' ||
		typeof currentStorage.setItem !== 'function'
	) {
		Object.defineProperty(window, 'localStorage', {
			value: storageFallback,
			configurable: true,
		});
	}
}

if (typeof globalThis.localStorage === 'undefined' || typeof globalThis.localStorage.getItem !== 'function') {
	Object.defineProperty(globalThis, 'localStorage', {
		value: storageFallback,
		configurable: true,
	});
}
