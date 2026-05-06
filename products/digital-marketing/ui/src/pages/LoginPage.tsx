/**
 * Login page.
 *
 * <p>P0-007: Manual credential entry is gated to local/dev/test only.
 * In production, this page redirects to the configured authentication provider.</p>
 *
 * @doc.type page
 * @doc.purpose Entry point for unauthenticated users
 * @doc.layer frontend
 */
import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';

// P0-007: Environment checks using Vite-safe env access
const isProduction = import.meta.env.MODE === 'production';
const isDevMode = import.meta.env.DEV === true;
const AUTH_PROVIDER_ENABLED = import.meta.env.VITE_AUTH_PROVIDER_ENABLED === 'true';
const AUTH_AUTHORIZE_ENDPOINT = import.meta.env.VITE_AUTH_AUTHORIZE_ENDPOINT;
const AUTH_CLIENT_ID = import.meta.env.VITE_AUTH_CLIENT_ID;

/**
 * P0-007: Initiates OAuth2 authorization code flow with PKCE.
 */
function initiateOAuthFlow(): void {
  if (!AUTH_AUTHORIZE_ENDPOINT || !AUTH_CLIENT_ID) {
    console.error('[DMOS] Auth provider not configured');
    return;
  }

  // Generate PKCE parameters
  const codeVerifier = generateCodeVerifier();
  const codeChallenge = generateCodeChallenge(codeVerifier);
  const state = generateState();

  // Store PKCE and state for callback verification
  sessionStorage.setItem('dmos_code_verifier', codeVerifier);
  sessionStorage.setItem('dmos_auth_state', state);

  // Build authorization URL
  const params = new URLSearchParams({
    response_type: 'code',
    client_id: AUTH_CLIENT_ID,
    redirect_uri: `${window.location.origin}/auth/callback`,
    state,
    code_challenge: codeChallenge,
    code_challenge_method: 'S256',
    scope: 'openid profile dmos',
  });

  window.location.href = `${AUTH_AUTHORIZE_ENDPOINT}?${params.toString()}`;
}

function generateCodeVerifier(): string {
  const array = new Uint8Array(32);
  crypto.getRandomValues(array);
  return base64URLEncode(array);
}

function generateCodeChallenge(verifier: string): string {
  // In production, this should use SHA-256
  // For now, S256 method requires async crypto.subtle.digest
  return verifier; // S256 implementation would go here
}

function generateState(): string {
  const array = new Uint8Array(16);
  crypto.getRandomValues(array);
  return base64URLEncode(array);
}

function base64URLEncode(buffer: Uint8Array): string {
  return btoa(String.fromCharCode(...buffer))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=/g, '');
}

function readDevRoles(): string[] {
  if (isProduction && !isDevMode) {
    return [];
  }

  const rawRoles = sessionStorage.getItem('dmos_roles');
  if (!rawRoles) {
    return [];
  }

  try {
    const parsed = JSON.parse(rawRoles);
    return Array.isArray(parsed) ? parsed.filter((role): role is string => typeof role === 'string') : [];
  } catch {
    return [];
  }
}

export function LoginPage(): React.ReactElement {
  const { login, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [token, setToken] = useState('');
  const [workspaceId, setWorkspaceId] = useState('');
  const [tenantId, setTenantId] = useState('');
  const [principalId, setPrincipalId] = useState('');
  const [error, setError] = useState<string | null>(null);

  // P0-007: Redirect to provider in production
  useEffect(() => {
    if (isProduction && AUTH_PROVIDER_ENABLED && !isAuthenticated) {
      initiateOAuthFlow();
    }
  }, [isAuthenticated]);

  // P0-007: Redirect authenticated users to dashboard
  useEffect(() => {
    if (isAuthenticated) {
      const targetWorkspace = workspaceId || 'default';
      void navigate(`/workspaces/${targetWorkspace}/dashboard`);
    }
  }, [isAuthenticated, workspaceId, navigate]);

  function handleSubmit(e: React.FormEvent): void {
    e.preventDefault();

    // P0-007: Extra safety check - manual login blocked in production
    if (isProduction && !isDevMode) {
      setError('Manual login is not allowed in production. Please use the authentication provider.');
      return;
    }

    if (!token.trim() || !workspaceId.trim() || !tenantId.trim() || !principalId.trim()) {
      setError('All fields are required.');
      return;
    }
    const sessionId = crypto.randomUUID();
    login(token.trim(), workspaceId.trim(), tenantId.trim(), principalId.trim(), sessionId, readDevRoles());
    void navigate(`/workspaces/${workspaceId.trim()}/dashboard`);
  }

  // P0-007: In production with auth provider, show simplified provider login UI
  const showManualForm = !isProduction || isDevMode;

  return (
    <main
      data-testid="login-page"
      className="min-h-screen flex items-center justify-center bg-gray-50"
    >
      <div className="bg-white rounded-lg shadow p-8 w-full max-w-sm">
        <h1 className="text-xl font-bold mb-6">DMOS — Sign In</h1>

        {error && (
          <p role="alert" className="text-red-600 text-sm mb-4">
            {error}
          </p>
        )}

        {!showManualForm && (
          <div className="text-center">
            <p className="text-gray-600 mb-4">
              Please sign in with your configured authentication provider.
            </p>
            <button
              onClick={initiateOAuthFlow}
              className="w-full bg-blue-600 text-white rounded px-4 py-2 text-sm hover:bg-blue-700"
              data-testid="provider-login-btn"
            >
              Sign In with Provider
            </button>
          </div>
        )}

        {showManualForm && (
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label htmlFor="token" className="block text-sm font-medium mb-1">
                Bearer Token
              </label>
              <input
                id="token"
                type="password"
                data-testid="login-token"
                value={token}
                onChange={(e) => setToken(e.target.value)}
                className="w-full border rounded px-3 py-2 text-sm"
              />
            </div>

            <div>
              <label
                htmlFor="workspaceId"
                className="block text-sm font-medium mb-1"
              >
                Workspace ID
              </label>
              <input
                id="workspaceId"
                type="text"
                data-testid="login-workspace-id"
                value={workspaceId}
                onChange={(e) => setWorkspaceId(e.target.value)}
                className="w-full border rounded px-3 py-2 text-sm"
              />
            </div>

            <div>
              <label
                htmlFor="tenantId"
                className="block text-sm font-medium mb-1"
              >
                Tenant ID
              </label>
              <input
                id="tenantId"
              type="text"
              data-testid="login-tenant-id"
              value={tenantId}
              onChange={(e) => setTenantId(e.target.value)}
              className="w-full border rounded px-3 py-2 text-sm"
            />
          </div>

          <div>
            <label
              htmlFor="principalId"
              className="block text-sm font-medium mb-1"
            >
              User / Principal ID
            </label>
            <input
              id="principalId"
              type="text"
              data-testid="login-principal-id"
              value={principalId}
              onChange={(e) => setPrincipalId(e.target.value)}
              className="w-full border rounded px-3 py-2 text-sm"
            />
          </div>

          {error && (
            <p role="alert" className="text-red-600 text-sm">
              {error}
            </p>
          )}

          <button
              type="submit"
              data-testid="login-submit"
              className="w-full bg-blue-600 text-white rounded px-4 py-2 text-sm hover:bg-blue-700"
            >
              Sign In
            </button>
          </form>
        )}
      </div>
    </main>
  );
}
