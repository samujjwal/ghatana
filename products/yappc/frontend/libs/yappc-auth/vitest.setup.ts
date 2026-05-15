if (typeof window !== 'undefined' && typeof Storage !== 'undefined') {
  const localStorageCandidate = globalThis.localStorage as Storage | undefined;

  if (
    !localStorageCandidate ||
    typeof localStorageCandidate.getItem !== 'function' ||
    typeof localStorageCandidate.setItem !== 'function' ||
    typeof localStorageCandidate.removeItem !== 'function'
  ) {
    const storage = Object.create(Storage.prototype) as Storage;

    Object.defineProperty(window, 'localStorage', {
      configurable: true,
      value: storage,
    });
    Object.defineProperty(globalThis, 'localStorage', {
      configurable: true,
      value: storage,
    });
  }
}
