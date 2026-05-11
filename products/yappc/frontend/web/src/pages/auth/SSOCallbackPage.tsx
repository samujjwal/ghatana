import React, { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { yappcApi } from '@/lib/api/client';
import { useTranslation } from '@ghatana/i18n';

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
  const { t } = useTranslation('common');

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
          const { token } = await yappcApi.auth.ssoCallback({ code, state });
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
        <p className="text-fg-muted">{t('auth.completingSignIn')}</p>
      </div>
    </div>
  );
};

export default SSOCallbackPage;
