import { describe, it, expect } from 'vitest';

/**
 * Missing translation tests — validates that untranslated keys are handled
 * gracefully and that coverage across all expected keys can be audited.
 *
 * @doc.type module
 * @doc.purpose Tests for missing translation detection and fallback behavior
 * @doc.layer platform
 * @doc.pattern Test
 */

// ── Locale fixtures ─────────────────────────────────────────────────────────

const localeEn: Record<string, unknown> = {
  app: {
    title: 'Ghatana',
    loading: 'Loading...',
    error: {
      generic: 'Something went wrong',
      notFound: 'Page not found',
      unauthorized: 'You are not authorized to view this page',
      networkError: 'Network error. Please check your connection.',
      retry: 'Try again',
    },
  },
  auth: {
    login: 'Log in',
    logout: 'Log out',
    signUp: 'Sign up',
    email: 'Email',
    password: 'Password',
    forgotPassword: 'Forgot password?',
  },
  actions: {
    save: 'Save',
    cancel: 'Cancel',
    delete: 'Delete',
    edit: 'Edit',
    create: 'Create',
    submit: 'Submit',
    confirm: 'Confirm',
    close: 'Close',
    search: 'Search',
    filter: 'Filter',
    refresh: 'Refresh',
    export: 'Export',
    import: 'Import',
  },
  status: {
    active: 'Active',
    inactive: 'Inactive',
    pending: 'Pending',
    error: 'Error',
    success: 'Success',
  },
  pagination: {
    previous: 'Previous',
    next: 'Next',
    page: 'Page {{current}} of {{total}}',
    showing: 'Showing {{from}} to {{to}} of {{total}} results',
  },
};

// French locale — intentionally incomplete (missing some keys) to simulate gaps
const localeFr: Record<string, unknown> = {
  app: {
    title: 'Ghatana',
    loading: 'Chargement...',
    error: {
      generic: 'Une erreur est survenue',
      notFound: 'Page non trouvée',
      // unauthorized, networkError, retry intentionally missing
    },
  },
  auth: {
    login: 'Se connecter',
    logout: 'Se déconnecter',
    // signUp, email, password, forgotPassword missing
  },
  actions: {
    save: 'Enregistrer',
    cancel: 'Annuler',
    delete: 'Supprimer',
    // others missing
  },
};

// ── Key extraction helper ────────────────────────────────────────────────────

function extractLeafKeys(obj: Record<string, unknown>, prefix = ''): string[] {
  const keys: string[] = [];
  for (const [k, v] of Object.entries(obj)) {
    const path = prefix ? `${prefix}.${k}` : k;
    if (v !== null && typeof v === 'object') {
      keys.push(...extractLeafKeys(v as Record<string, unknown>, path));
    } else {
      keys.push(path);
    }
  }
  return keys;
}

function resolveKey(obj: Record<string, unknown>, key: string): string | undefined {
  const parts = key.split('.');
  let current: unknown = obj;
  for (const part of parts) {
    if (current == null || typeof current !== 'object') return undefined;
    current = (current as Record<string, unknown>)[part];
  }
  return typeof current === 'string' ? current : undefined;
}

// ── Tests ───────────────────────────────────────────────────────────────────

describe('missing translation detection', () => {
  describe('key extraction from locale file', () => {
    it('extracts all leaf keys from the English locale', () => {
      const keys = extractLeafKeys(localeEn);
      expect(keys.length).toBeGreaterThan(20);
      expect(keys).toContain('app.title');
      expect(keys).toContain('app.error.generic');
      expect(keys).toContain('actions.save');
      expect(keys).toContain('pagination.page');
    });

    it('leaf keys do not include intermediate object nodes', () => {
      const keys = extractLeafKeys(localeEn);
      expect(keys).not.toContain('app');
      expect(keys).not.toContain('app.error');
    });
  });

  describe('incomplete locale detection', () => {
    it('detects keys present in English but missing from French', () => {
      const enKeys = extractLeafKeys(localeEn);
      const missingInFr = enKeys.filter((key) => resolveKey(localeFr, key) === undefined);

      expect(missingInFr.length).toBeGreaterThan(0);
      expect(missingInFr).toContain('app.error.unauthorized');
      expect(missingInFr).toContain('auth.email');
    });

    it('reports zero missing keys when locale is complete', () => {
      const enKeys = extractLeafKeys(localeEn);
      // English vs itself — no missing keys
      const missing = enKeys.filter((key) => resolveKey(localeEn, key) === undefined);
      expect(missing).toHaveLength(0);
    });
  });

  describe('fallback behavior for missing key', () => {
    it('returns undefined when a key is absent — consumer falls back to key', () => {
      const result = resolveKey(localeFr, 'auth.forgotPassword');
      expect(result).toBeUndefined();
    });

    it('returns the value when the key is present in French locale', () => {
      const result = resolveKey(localeFr, 'auth.login');
      expect(result).toBe('Se connecter');
    });

    it('returns undefined for a key with no translation and no fallback', () => {
      const result = resolveKey(localeFr, 'completely.missing.key');
      expect(result).toBeUndefined();
    });
  });

  describe('interpolation template preservation', () => {
    it('pagination.page in English contains template variables', () => {
      const value = resolveKey(localeEn, 'pagination.page');
      expect(value).toContain('{{current}}');
      expect(value).toContain('{{total}}');
    });
  });
});
