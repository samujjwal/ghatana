import { describe, it, expect, vi, afterEach } from 'vitest';
import {
  createGoogleProvider,
  createGitHubProvider,
  createMicrosoftProvider,
  OAuthProviders,
} from '../oauth/providers.js';

afterEach(() => {
  vi.unstubAllEnvs();
});

describe('createGoogleProvider()', () => {
  it('returns a provider with name "google"', () => {
    const p = createGoogleProvider('client-1');
    expect(p.name).toBe('google');
  });

  it('sets clientId and leaves clientSecret undefined when not supplied', () => {
    const p = createGoogleProvider('client-1');
    expect(p.clientId).toBe('client-1');
    expect(p.clientSecret).toBeUndefined();
  });

  it('accepts an optional clientSecret', () => {
    const p = createGoogleProvider('cid', 'csecret');
    expect(p.clientSecret).toBe('csecret');
  });

  it('redirectUri contains /auth/callback/google', () => {
    const p = createGoogleProvider('cid');
    expect(p.redirectUri).toContain('/auth/callback/google');
  });

  it('includes openid in scopes', () => {
    const p = createGoogleProvider('cid');
    expect(p.scopes).toContain('openid');
  });

  it('authorizationUrl points to Google OAuth', () => {
    const p = createGoogleProvider('cid');
    expect(p.authorizationUrl).toContain('accounts.google.com');
  });
});

describe('createGitHubProvider()', () => {
  it('returns a provider with name "github"', () => {
    const p = createGitHubProvider('gh-client');
    expect(p.name).toBe('github');
  });

  it('includes read:user and user:email scopes', () => {
    const p = createGitHubProvider('gh-client');
    expect(p.scopes).toContain('read:user');
    expect(p.scopes).toContain('user:email');
  });

  it('redirectUri contains /auth/callback/github', () => {
    const p = createGitHubProvider('gh-client');
    expect(p.redirectUri).toContain('/auth/callback/github');
  });
});

describe('createMicrosoftProvider()', () => {
  it('returns a provider with name "microsoft"', () => {
    const p = createMicrosoftProvider('ms-client');
    expect(p.name).toBe('microsoft');
  });

  it('uses "common" tenant by default', () => {
    const p = createMicrosoftProvider('ms-client');
    expect(p.authorizationUrl).toContain('/common/');
    expect(p.tokenUrl).toContain('/common/');
  });

  it('uses custom tenant when specified', () => {
    const p = createMicrosoftProvider('ms-client', undefined, 'my-tenant');
    expect(p.authorizationUrl).toContain('/my-tenant/');
  });

  it('includes User.Read scope', () => {
    const p = createMicrosoftProvider('ms-client');
    expect(p.scopes).toContain('User.Read');
  });
});

describe('OAuthProviders.google()', () => {
  it('throws when REACT_APP_GOOGLE_CLIENT_ID is not set', () => {
    vi.stubEnv('REACT_APP_GOOGLE_CLIENT_ID', '');
    expect(() => OAuthProviders.google()).toThrow('REACT_APP_GOOGLE_CLIENT_ID is not configured');
  });

  it('returns a Google provider when env var is set', () => {
    vi.stubEnv('REACT_APP_GOOGLE_CLIENT_ID', 'g-client');
    const p = OAuthProviders.google();
    expect(p.name).toBe('google');
    expect(p.clientId).toBe('g-client');
  });
});

describe('OAuthProviders.github()', () => {
  it('throws when REACT_APP_GITHUB_CLIENT_ID is not set', () => {
    vi.stubEnv('REACT_APP_GITHUB_CLIENT_ID', '');
    expect(() => OAuthProviders.github()).toThrow('REACT_APP_GITHUB_CLIENT_ID is not configured');
  });

  it('returns a GitHub provider when env var is set', () => {
    vi.stubEnv('REACT_APP_GITHUB_CLIENT_ID', 'gh-client');
    const p = OAuthProviders.github();
    expect(p.name).toBe('github');
  });
});

describe('OAuthProviders.microsoft()', () => {
  it('throws when REACT_APP_MICROSOFT_CLIENT_ID is not set', () => {
    vi.stubEnv('REACT_APP_MICROSOFT_CLIENT_ID', '');
    expect(() => OAuthProviders.microsoft()).toThrow('REACT_APP_MICROSOFT_CLIENT_ID is not configured');
  });

  it('returns a Microsoft provider when env var is set', () => {
    vi.stubEnv('REACT_APP_MICROSOFT_CLIENT_ID', 'ms-client');
    const p = OAuthProviders.microsoft();
    expect(p.name).toBe('microsoft');
  });

  it('passes optional tenant to the provider', () => {
    vi.stubEnv('REACT_APP_MICROSOFT_CLIENT_ID', 'ms-client');
    const p = OAuthProviders.microsoft('custom-tenant');
    expect(p.authorizationUrl).toContain('/custom-tenant/');
  });
});
