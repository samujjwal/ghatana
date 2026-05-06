/**
 * Auth Callback Page.
 *
 * <p>P0-006: Production authentication callback handler for OAuth2/OIDC flows.
 * This page handles the callback from the authentication provider after
 * successful login, exchanges the authorization code for tokens, and
 * bootstraps the DMOS session from the validated identity.</p>
 *
 * <p>Route: /auth/callback</p>
 *
 * @doc.type page
 * @doc.purpose OAuth2/OIDC callback handler for production authentication (DMOS-P0-006)
 * @doc.layer frontend
 */
import React, { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';

/**
 * Token response from the auth provider token endpoint.
 */
interface TokenResponse {
  access_token: string;
  refresh_token?: string;
  expires_in: number;
  token_type: string;
}

/**
 * User info from the auth provider / userinfo endpoint.
 */
interface UserInfo {
  sub: string;
  workspace_id: string;
  tenant_id: string;
  roles?: string[];
}

export function AuthCallbackPage(): React.ReactElement {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { login } = useAuth();
  const [error, setError] = useState<string | null>(null);
  const [isProcessing, setIsProcessing] = useState(true);

  useEffect(() => {
    const handleCallback = async () => {
      const code = searchParams.get('code');
      const state = searchParams.get('state');
      const errorParam = searchParams.get('error');
      const errorDescription = searchParams.get('error_description');

      // Check for OAuth error response
      if (errorParam) {
        setError(`Authentication failed: ${errorDescription || errorParam}`);
        setIsProcessing(false);
        return;
      }

      // Validate required parameters
      if (!code) {
        setError('Invalid authentication response: missing authorization code');
        setIsProcessing(false);
        return;
      }

      // Verify state parameter to prevent CSRF
      const storedState = sessionStorage.getItem('dmos_auth_state');
      sessionStorage.removeItem('dmos_auth_state');
      if (state !== storedState) {
        setError('Invalid authentication response: state mismatch');
        setIsProcessing(false);
        return;
      }

      try {
        // P0-006: Exchange authorization code for tokens
        const tokenEndpoint = import.meta.env.VITE_AUTH_TOKEN_ENDPOINT;
        if (!tokenEndpoint) {
          throw new Error('Auth configuration error: token endpoint not configured');
        }

        const tokenResponse = await fetch(tokenEndpoint, {
          method: 'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body: new URLSearchParams({
            grant_type: 'authorization_code',
            code,
            client_id: import.meta.env.VITE_AUTH_CLIENT_ID || '',
            redirect_uri: `${window.location.origin}/auth/callback`,
            code_verifier: sessionStorage.getItem('dmos_code_verifier') || '',
          }),
        });

        sessionStorage.removeItem('dmos_code_verifier');

        if (!tokenResponse.ok) {
          const errorText = await tokenResponse.text();
          throw new Error(`Token exchange failed: ${errorText}`);
        }

        const tokens: TokenResponse = await tokenResponse.json();

        // P0-006: Fetch user info to get workspace/tenant/roles
        const userInfoEndpoint = import.meta.env.VITE_AUTH_USERINFO_ENDPOINT;
        if (!userInfoEndpoint) {
          throw new Error('Auth configuration error: userinfo endpoint not configured');
        }

        const userInfoResponse = await fetch(userInfoEndpoint, {
          headers: {
            Authorization: `${tokens.token_type} ${tokens.access_token}`,
          },
        });

        if (!userInfoResponse.ok) {
          throw new Error('Failed to fetch user information');
        }

        const userInfo: UserInfo = await userInfoResponse.json();

        // P0-006: Bootstrap DMOS session from validated identity
        // Session ID derived from token for correlation
        const sessionId = crypto.randomUUID();

        login(
          tokens.access_token,
          userInfo.workspace_id,
          userInfo.tenant_id,
          userInfo.sub,
          sessionId,
          userInfo.roles || []
        );

        // Navigate to dashboard
        navigate(`/workspaces/${userInfo.workspace_id}/dashboard`);
      } catch (err) {
        console.error('[DMOS] Auth callback error:', err);
        setError(err instanceof Error ? err.message : 'Authentication failed');
        setIsProcessing(false);
      }
    };

    void handleCallback();
  }, [searchParams, login, navigate]);

  if (isProcessing) {
    return (
      <main
        data-testid="auth-callback-page"
        className="min-h-screen flex items-center justify-center bg-gray-50"
      >
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4" />
          <p className="text-gray-600">Completing sign-in...</p>
        </div>
      </main>
    );
  }

  if (error) {
    return (
      <main
        data-testid="auth-callback-error"
        className="min-h-screen flex items-center justify-center bg-gray-50"
      >
        <div className="bg-white rounded-lg shadow p-8 w-full max-w-md text-center">
          <div className="mb-4">
            <svg
              className="mx-auto h-12 w-12 text-red-400"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              aria-hidden="true"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
              />
            </svg>
          </div>

          <h1 className="text-xl font-bold mb-2 text-red-600">Authentication Failed</h1>
          <p className="text-gray-600 mb-6">{error}</p>

          <button
            onClick={() => navigate('/login')}
            className="w-full bg-blue-600 text-white rounded px-4 py-2 text-sm hover:bg-blue-700"
            data-testid="back-to-login"
          >
            Back to Login
          </button>
        </div>
      </main>
    );
  }

  // Should not reach here, but fail closed with a visible recovery path.
  return (
    <main
      data-testid="auth-callback-fallback"
      className="min-h-screen flex items-center justify-center bg-gray-50"
    >
      <div className="bg-white rounded-lg shadow p-8 w-full max-w-sm text-center">
        <h1 className="text-xl font-bold mb-2">Authentication Pending</h1>
        <p className="text-gray-600 mb-6">
          We could not determine the authentication state. Please return to login and try again.
        </p>
        <button
          onClick={() => navigate('/login')}
          className="w-full bg-blue-600 text-white rounded px-4 py-2 text-sm hover:bg-blue-700"
          data-testid="auth-callback-fallback-login"
        >
          Back to Login
        </button>
      </div>
    </main>
  );
}
