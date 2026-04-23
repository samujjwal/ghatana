/**
 * SSO Callback Page
 *
 * Handles the redirect back from the platform identity provider after
 * successful authentication. Extracts the session token from URL query
 * parameters, bootstraps the AEP session, and redirects to the originally
 * requested page.
 *
 * @doc.type page
 * @doc.purpose Complete platform SSO handshake and land user in AEP
 * @doc.layer frontend
 */
import React, { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { useAuth } from '@/context/AuthContext';
import { setAuthToken, setSessionToken } from '@/lib/http-client';

/**
 * Parse redirect target from query params with safety checks.
 */
function parseRedirectTarget(params: URLSearchParams): string {
  const raw = params.get('redirect');
  if (raw && raw.startsWith('/')) {
    return raw;
  }
  return '/operate';
}

/**
 * SSO Callback Page component.
 *
 * Expected query parameters from platform identity provider:
 *   - token: short-lived bearer token issued by platform IdP
 *   - session: AEP session token (if platform pre-exchanged)
 *   - redirect: original path before auth challenge (default /operate)
 *   - error: human-readable error message on failure
 */
export function SsoCallbackPage(): React.ReactElement {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { isAuthenticated } = useAuth();
  const [error, setError] = useState<string | null>(null);

  const redirectTarget = parseRedirectTarget(searchParams);

  useEffect(() => {
    if (isAuthenticated) {
      navigate(redirectTarget, { replace: true });
      return;
    }

    const token = searchParams.get('token');
    const session = searchParams.get('session');
    const errorParam = searchParams.get('error');

    if (errorParam) {
      setError(errorParam);
      return;
    }

    if (!token && !session) {
      setError('Missing authentication credentials from identity provider.');
      return;
    }

    try {
      if (token) {
        setAuthToken(token);
      }
      if (session) {
        setSessionToken(session);
      }
      // AuthProvider will detect the new tokens and set isAuthenticated
      // Redirect after a brief delay to allow state propagation
      const timer = setTimeout(() => {
        navigate(redirectTarget, { replace: true });
      }, 100);
      return () => clearTimeout(timer);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to process authentication response.');
    }
  }, [isAuthenticated, navigate, redirectTarget, searchParams]);

  if (error) {
    return (
      <main className="min-h-screen bg-slate-950 text-slate-50 flex items-center justify-center">
        <div className="max-w-md w-full mx-6 p-8 rounded-3xl border border-white/10 bg-white/5 text-center space-y-6">
          <div className="inline-flex items-center rounded-full border border-rose-400/30 bg-rose-400/10 px-3 py-1 text-xs font-semibold uppercase tracking-[0.3em] text-rose-200">
            Authentication Failed
          </div>
          <div className="space-y-2">
            <h1 className="text-2xl font-semibold text-white">Unable to sign in</h1>
            <p className="text-sm text-slate-300">{error}</p>
          </div>
          <a
            href="/login"
            className="inline-block rounded-xl bg-cyan-500/20 border border-cyan-500/30 px-6 py-3 text-sm font-medium text-cyan-200 hover:bg-cyan-500/30 transition"
          >
            Return to sign in
          </a>
        </div>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-slate-950 text-slate-50 flex items-center justify-center">
      <div className="text-center space-y-4">
        <div className="inline-block h-8 w-8 animate-spin rounded-full border-2 border-cyan-400 border-t-transparent" />
        <p className="text-sm text-slate-400">Completing sign-in…</p>
      </div>
    </main>
  );
}
