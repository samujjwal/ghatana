import React, { useState } from 'react';
import { Button, Card, CardContent, CardHeader, Input } from '@ghatana/design-system';
import { useNavigate } from 'react-router-dom';
import { loginWithCredentials } from '../api/authApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import { t } from '../i18n/phrI18n';

export function LoginPage(): React.ReactElement {
  const [nationalId, setNationalId] = useState<string>('');
  const [password, setPassword] = useState<string>('');
  const [signingIn, setSigningIn] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const { setSession } = usePhrSession();
  const navigate = useNavigate();
  const errorRef = React.useRef<HTMLDivElement>(null);

  // Focus error message when error is set for accessibility
  React.useEffect(() => {
    if (error && errorRef.current) {
      errorRef.current.focus();
    }
  }, [error]);

  const handleSignIn = async (event: React.FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault();
    setError(null);

    if (!nationalId.trim()) {
      setError(t('validation.required', { field: t('login.nationalId.label') }));
      return;
    }
    if (!password) {
      setError(t('validation.required', { field: t('login.password.label') }));
      return;
    }

    setSigningIn(true);
    try {
      const session = await loginWithCredentials({ nationalId: nationalId.trim(), password });
      setSession(session);
      navigate('/dashboard', { replace: true });
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : t('login.error.networkError'));
    } finally {
      setSigningIn(false);
    }
  };

  return (
    <div className="centered-page">
      <Card className="hero-card">
        <CardHeader title={t('login.title')} subheader={t('login.subheader')} />
        <CardContent>
          {error && (
            <div 
              ref={errorRef}
              role="alert" 
              className="error mb-4"
              tabIndex={-1}
            >
              {error}
            </div>
          )}
          <form onSubmit={(e) => void handleSignIn(e)} className="stack gap-md" noValidate>
            <Input
              aria-label={t('login.nationalId.label')}
              placeholder={t('login.nationalId.placeholder')}
              value={nationalId}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => setNationalId(e.target.value)}
              autoComplete="username"
              required
            />
            <Input
              aria-label={t('login.password.label')}
              type="password"
              placeholder={t('login.password.placeholder')}
              value={password}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => setPassword(e.target.value)}
              autoComplete="current-password"
              required
            />
            <Button type="submit" className="primary-cta" disabled={signingIn}>
              {signingIn ? t('login.signingIn') : t('login.signIn')}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
