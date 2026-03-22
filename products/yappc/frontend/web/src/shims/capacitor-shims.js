// Minimal runtime shim for Capacitor imports used in the app during
// dependency scanning. This file exists only to satisfy bundler import
// resolution in the Storybook/dev environment where native Capacitor
// packages may not be available.

export const Haptics = {
  impact: async () => {
    // no-op
  },
  selectionStart: async () => {},
  selectionChanged: async () => {},
  selectionEnd: async () => {},
};

export const ImpactStyle = {
  Light: 'LIGHT',
  Medium: 'MEDIUM',
  Heavy: 'HEAVY',
};

// Minimal Network shim: mirrors the Capacitor Network API surface used by the app.
export const Network = {
  getStatus: async () => ({ connected: true, connectionType: 'wifi' }),
  addListener: async (_eventName, _cb) => ({ remove: async () => {} }),
};

// Minimal Share shim: matches the Capacitor Share API used in web/dev environment.
export const Share = async ({ title, text, url }) => {
  // Fallback: attempt to use the Web Share API if available, otherwise no-op
  if (typeof navigator !== 'undefined' && navigator.share) {
    try {
      await navigator.share({ title, text, url });
      return { completed: true };
    } catch (err) {
      return { completed: false, error: String(err) };
    }
  }
  return { completed: false };
};

export default {};
