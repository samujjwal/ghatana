import React, { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router';

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
      fetch('/api/auth/sso/callback', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code, state }),
      })
        .then(async (res) => {
          if (!res.ok) throw new Error('SSO authentication failed');
          const { token } = await res.json();
          localStorage.setItem('auth_token', token);
          navigate(state ?? '/', { replace: true });
        })
        .catch(() => {
          navigate('/login?error=sso_failed', { replace: true });
        });
    }
  }, [searchParams, navigate]);

  return (
    <div className="flex min-h-screen items-center justify-center bg-zinc-950">
      <div className="text-center space-y-4">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500 mx-auto" />
        <p className="text-zinc-400">Completing Sign-In</p>
      </div>
    </div>
  );
};

export default SSOCallbackPage;
