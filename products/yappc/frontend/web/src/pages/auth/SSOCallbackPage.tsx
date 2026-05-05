import React, { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { parseJsonResponse, readErrorResponse } from '@/lib/http';

/**
 * SSOCallbackPage — SSO/OAuth callback handler.
 *
 * @doc.type component
 * @doc.purpose Handles SSO authentication callback
 * @doc.layer product
 */
const SSOCallbackPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  useEffect(() => {
    const code = searchParams.get('code');
    const state = searchParams.get('state');
    const error = searchParams.get('error');

    if (error) {
      navigate(`/login?error=${encodeURIComponent(error)}`, { replace: true });
      return;
    }

    if (code) {
      void (async () => {
        try {
          const res = await fetch('/api/auth/sso/callback', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ code, state }),
          });

          if (!res.ok) {
            throw new Error(await readErrorResponse(res, 'SSO authentication failed'));
          }

          const { token } = await parseJsonResponse<{ token: string }>(res, 'sso callback');
          localStorage.setItem('auth_token', token);
          navigate(state ?? '/', { replace: true });
        } catch {
          navigate('/login?error=sso_failed', { replace: true });
        }
      })();
    }
  }, [searchParams, navigate]);

  return (
    <div className="flex min-h-screen items-center justify-center bg-surface">
      <div className="text-center space-y-4">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-info-border mx-auto" />
        <p className="text-fg-muted">Completing Sign-In</p>
      </div>
    </div>
  );
};

export default SSOCallbackPage;
