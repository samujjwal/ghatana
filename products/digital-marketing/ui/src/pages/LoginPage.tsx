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
import { Button, TextField } from '@ghatana/design-system';

// P0-007: Environment checks using Vite-safe env access
const isProduction = import.meta.env.MODE === 'production';
const isDevMode = import.meta.env.DEV === true;
const AUTH_PROVIDER_ENABLED = import.meta.env.VITE_AUTH_PROVIDER_ENABLED === 'true';
const AUTH_AUTHORIZE_ENDPOINT = import.meta.env.VITE_AUTH_AUTHORIZE_ENDPOINT;
const AUTH_CLIENT_ID = import.meta.env.VITE_AUTH_CLIENT_ID;
const LOGIN_DIAGNOSTIC_EVENT = 'dmos:login-diagnostic';

interface LoginDiagnostic {
  code: string;
  message: string;
}

function recordLoginDiagnostic(diagnostic: LoginDiagnostic): void {
  if (typeof window === 'undefined') {
    return;
  }

  window.dispatchEvent(new CustomEvent<LoginDiagnostic>(LOGIN_DIAGNOSTIC_EVENT, { detail: diagnostic }));
}

/**
 * P0-001 + P0-002: Initiates OAuth2 authorization code flow with PKCE S256.
 * Fails closed in production when provider is not configured.
 */
async function initiateOAuthFlow(): Promise<void> {
  if (!AUTH_AUTHORIZE_ENDPOINT || !AUTH_CLIENT_ID) {
    if (isProduction) {
      throw new Error('[DMOS] Auth provider not configured. VITE_AUTH_AUTHORIZE_ENDPOINT and VITE_AUTH_CLIENT_ID are required in production.');
    }
    recordLoginDiagnostic({
      code: 'DMOS_AUTH_PROVIDER_NOT_CONFIGURED',
      message: 'Auth provider is not configured for the current environment.',
    });
    return;
  }

  // Generate PKCE parameters
  const codeVerifier = generateCodeVerifier();
  const codeChallenge = await generateCodeChallenge(codeVerifier);
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

async function generateCodeChallenge(verifier: string): Promise<string> {
  const encoder = new TextEncoder();
  const data = encoder.encode(verifier);
  const digest = await crypto.subtle.digest('SHA-256', data);
  return base64URLEncode(new Uint8Array(digest));
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

  // P0-001 + P0-002: Redirect to provider in production; fail closed on config errors
  useEffect(() => {
    if (isProduction && AUTH_PROVIDER_ENABLED && !isAuthenticated) {
      initiateOAuthFlow().catch((err: unknown) => {
        const message = err instanceof Error ? err.message : 'Auth provider configuration error.';
        setError(message);
      });
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
            <Button
              onClick={() => {
                initiateOAuthFlow().catch((err: unknown) => {
                  const message = err instanceof Error ? err.message : 'Auth provider configuration error.';
                  setError(message);
                });
              }}
              fullWidth
              tone="primary"
              data-testid="provider-login-btn"
            >
              Sign In with Provider
            </Button>
          </div>
        )}

        {showManualForm && (
          <form onSubmit={handleSubmit} className="space-y-4">
            <TextField
              id="token"
              label="Bearer Token"
              type="password"
              data-testid="login-token"
              value={token}
              onChange={(e) => setToken(e.target.value)}
              fullWidth
            />

            <TextField
              id="workspaceId"
              label="Workspace ID"
              type="text"
              data-testid="login-workspace-id"
              value={workspaceId}
              onChange={(e) => setWorkspaceId(e.target.value)}
              fullWidth
            />

            <TextField
              id="tenantId"
              label="Tenant ID"
              type="text"
              data-testid="login-tenant-id"
              value={tenantId}
              onChange={(e) => setTenantId(e.target.value)}
              fullWidth
            />

            <TextField
              id="principalId"
              label="User / Principal ID"
              type="text"
              data-testid="login-principal-id"
              value={principalId}
              onChange={(e) => setPrincipalId(e.target.value)}
              fullWidth
            />

            {error && (
              <p role="alert" className="text-red-600 text-sm">
                {error}
              </p>
            )}

            <Button
              type="submit"
              data-testid="login-submit"
              fullWidth
              tone="primary"
            >
              Sign In
            </Button>
          </form>
        )}
      </div>
    </main>
  );
}
