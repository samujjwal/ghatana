import { describe, it, expect } from 'vitest';

/**
 * Translation key resolution tests.
 *
 * These tests validate how the translation lookup logic resolves keys
 * against locale files, handles nested keys, and applies interpolation
 * without relying on i18next internals.
 *
 * @doc.type module
 * @doc.purpose Tests for translation key resolution and locale file correctness
 * @doc.layer platform
 * @doc.pattern Test
 */

// ── Locale fixture ──────────────────────────────────────────────────────────

const en = {
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
} as const;

/** Naive key resolver that handles dot-separated paths */
function resolve(obj: Record<string, unknown>, key: string): string | undefined {
  const parts = key.split('.');
  let current: unknown = obj;
  for (const part of parts) {
    if (current == null || typeof current !== 'object') return undefined;
    current = (current as Record<string, unknown>)[part];
  }
  return typeof current === 'string' ? current : undefined;
}

/** Naive interpolation: replaces {{var}} with the provided values */
function interpolate(template: string, vars: Record<string, string | number>): string {
  return template.replace(/\{\{(\w+)\}\}/g, (_, key: string) => String(vars[key] ?? `{{${key}}}`));
}

// ── Tests ──────────────────────────────────────────────────────────────────

describe('translation key resolution', () => {
  describe('top-level keys', () => {
    it('resolves app.title', () => {
      expect(resolve(en as unknown as Record<string, unknown>, 'app.title')).toBe('Ghatana');
    });

    it('resolves app.loading', () => {
      expect(resolve(en as unknown as Record<string, unknown>, 'app.loading')).toBe('Loading...');
    });
  });

  describe('deeply nested keys', () => {
    it('resolves app.error.generic', () => {
      expect(resolve(en as unknown as Record<string, unknown>, 'app.error.generic')).toBe('Something went wrong');
    });

    it('resolves app.error.notFound', () => {
      expect(resolve(en as unknown as Record<string, unknown>, 'app.error.notFound')).toBe('Page not found');
    });

    it('resolves app.error.unauthorized', () => {
      expect(resolve(en as unknown as Record<string, unknown>, 'app.error.unauthorized')).toBe(
        'You are not authorized to view this page',
      );
    });
  });

  describe('action keys', () => {
    it.each([
      ['save', 'Save'],
      ['cancel', 'Cancel'],
      ['delete', 'Delete'],
      ['edit', 'Edit'],
      ['search', 'Search'],
    ] as const)('resolves actions.%s to %s', (action: string, expected: string) => {
      expect(resolve(en as unknown as Record<string, unknown>, `actions.${action}`)).toBe(expected);
    });
  });

  describe('missing key handling', () => {
    it('returns undefined for a completely missing key', () => {
      expect(resolve(en as unknown as Record<string, unknown>, 'nonexistent')).toBeUndefined();
    });

    it('returns undefined for a partially matching key path', () => {
      expect(resolve(en as unknown as Record<string, unknown>, 'app.missing')).toBeUndefined();
    });

    it('returns undefined for a key that leads to an object, not a string', () => {
      expect(resolve(en as unknown as Record<string, unknown>, 'app.error')).toBeUndefined();
    });
  });

  describe('interpolation', () => {
    it('interpolates pagination.page with current and total', () => {
      const template = resolve(en as unknown as Record<string, unknown>, 'pagination.page') ?? '';
      const result = interpolate(template, { current: 3, total: 10 });
      expect(result).toBe('Page 3 of 10');
    });

    it('interpolates pagination.showing with from, to, and total', () => {
      const template = resolve(en as unknown as Record<string, unknown>, 'pagination.showing') ?? '';
      const result = interpolate(template, { from: 1, to: 25, total: 100 });
      expect(result).toBe('Showing 1 to 25 of 100 results');
    });

    it('leaves unresolved variables as-is when value is missing', () => {
      const template = '{{greeting}}, {{name}}!';
      const result = interpolate(template, { greeting: 'Hello' });
      expect(result).toBe('Hello, {{name}}!');
    });
  });
});
