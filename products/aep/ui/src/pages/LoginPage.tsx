/**
 * LoginPage for the AEP console.
 *
 * The backend currently relies on a platform-issued bearer token rather than a
 * username/password exchange, so the UI accepts a JWT and stores it locally.
 *
 * @doc.type page
 * @doc.purpose Entry point for authenticated access to the AEP console
 * @doc.layer frontend
 */
import React, { useMemo, useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router';
import { useAuth } from '@/context/AuthContext';
import { Button, TextArea } from '@ghatana/design-system';

interface LoginLocationState {
  from?: string;
}

function readRedirectTarget(state: unknown): string {
  if (!state || typeof state !== 'object') {
    return '/operate';
  }

  const candidate = (state as LoginLocationState).from;
  return typeof candidate === 'string' && candidate.startsWith('/')
    ? candidate
    : '/operate';
}

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { isAuthenticated, isBootstrappingSession, loginWithToken } = useAuth();

  const redirectTarget = useMemo(() => readRedirectTarget(location.state), [location.state]);
  const [token, setToken] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (isAuthenticated) {
    return <Navigate to={redirectTarget} replace />;
  }

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault();
    setError(null);
    setIsSubmitting(true);

    try {
      await loginWithToken(token);
      navigate(redirectTarget, { replace: true });
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : 'Unable to sign in');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <main className="min-h-screen bg-slate-950 text-slate-50">
      <div className="mx-auto flex min-h-screen max-w-6xl flex-col justify-center gap-10 px-6 py-16 lg:flex-row lg:items-center lg:gap-16">
        <section className="max-w-xl space-y-6">
          <div className="inline-flex items-center rounded-full border border-cyan-400/30 bg-cyan-400/10 px-3 py-1 text-xs font-semibold uppercase tracking-[0.3em] text-cyan-200">
            Agentic Event Processor
          </div>
          <div className="space-y-4">
            <h1 className="text-4xl font-semibold tracking-tight text-white sm:text-5xl">
              Enter the AEP control plane with a platform-issued JWT.
            </h1>
            <p className="max-w-lg text-base leading-7 text-slate-300 sm:text-lg">
              The console uses the bearer token for API access and automatically requests an
              AEP session token for repeated calls when the backend accepts it.
            </p>
          </div>
          <div className="grid gap-3 text-sm text-slate-300 sm:grid-cols-3">
            <div className="rounded-2xl border border-white/10 bg-white/5 p-4">
              <div className="text-xs uppercase tracking-[0.25em] text-slate-500">Operate</div>
              <div className="mt-2 font-medium text-white">Runs, reviews, alerts</div>
            </div>
            <div className="rounded-2xl border border-white/10 bg-white/5 p-4">
              <div className="text-xs uppercase tracking-[0.25em] text-slate-500">Build</div>
              <div className="mt-2 font-medium text-white">Pipelines and patterns</div>
            </div>
            <div className="rounded-2xl border border-white/10 bg-white/5 p-4">
              <div className="text-xs uppercase tracking-[0.25em] text-slate-500">Govern</div>
              <div className="mt-2 font-medium text-white">Policy-aware execution</div>
            </div>
          </div>
        </section>

        <section className="w-full max-w-lg rounded-3xl border border-white/10 bg-white/5 p-6 shadow-2xl shadow-black/30 backdrop-blur sm:p-8">
          <form className="space-y-5" onSubmit={handleSubmit}>
            <div className="space-y-2">
              <h2 className="text-2xl font-semibold text-white">Sign in</h2>
              <p className="text-sm leading-6 text-slate-300">
                Paste the JWT issued by the platform identity provider. No username or password
                exchange is implemented inside the AEP UI.
              </p>
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium text-slate-200" htmlFor="jwt-token">
                JWT access token
              </label>
              <TextArea
                id="jwt-token"
                value={token}
                onChange={(event: React.ChangeEvent<HTMLTextAreaElement>) => setToken(event.target.value)}
                rows={8}
                spellCheck={false}
                autoCapitalize="off"
                autoCorrect="off"
                placeholder="eyJhbGciOi..."
                className="w-full rounded-2xl border border-slate-700 bg-slate-950/70 px-4 py-3 font-mono text-sm text-slate-100 outline-none transition focus:border-cyan-400"
              />
            </div>

            {error ? (
              <div className="rounded-2xl border border-rose-500/40 bg-rose-500/10 px-4 py-3 text-sm text-rose-200">
                {error}
              </div>
            ) : null}

            <Button
              type="submit"
              disabled={isSubmitting}
              variant="primary"
              fullWidth
            >
              {isSubmitting ? 'Signing in…' : 'Sign in'}
            </Button>

            <p className="text-xs leading-5 text-slate-400">
              {isBootstrappingSession
                ? 'Issuing AEP session token…'
                : 'AEP will request an X-AEP-Session token after sign-in when the backend allows it.'}
            </p>
          </form>
        </section>
      </div>
    </main>
  );
}