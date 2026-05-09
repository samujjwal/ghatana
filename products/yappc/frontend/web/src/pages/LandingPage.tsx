import React from 'react';
import { Link } from 'react-router';
import { useI18n } from '../i18n/I18nProvider';

/**
 * LandingPage — unauthenticated marketing/entry page.
 *
 * @doc.type component
 * @doc.purpose Public landing page with CTA
 * @doc.layer product
 */
const LandingPage: React.FC = () => {
  const { t } = useI18n();
  return (
    <div className="flex min-h-screen flex-col bg-surface text-fg-muted">
      {/* Nav */}
      <nav className="flex items-center justify-between border-b border-border px-6 py-4">
        <span className="text-lg font-bold tracking-tight">YAPPC</span>
        <div className="flex items-center gap-4">
          <Link to="/auth/login" className="text-sm text-fg-muted hover:text-fg-muted">
          {t('landing.signIn')}
          </Link>
          <Link
            to="/auth/register"
            className="rounded-md bg-primary px-4 py-1.5 text-sm font-medium text-white hover:bg-info-bg"
          >
            {t('landing.getStarted')}
          </Link>
        </div>
      </nav>

      {/* Hero */}
      <main className="flex flex-1 flex-col items-center justify-center px-6 text-center">
        <h1 className="max-w-2xl text-4xl font-extrabold leading-tight sm:text-5xl">
          {t('landing.heroTitle1')}
          <br />
          <span className="text-info-color">{t('landing.heroTitle2')}</span>
        </h1>
        <p className="mt-4 max-w-lg text-lg text-fg-muted">
          {t('landing.heroSubtitle')}
        </p>

        <div className="mt-8 flex gap-4">
          <Link
            to="/auth/register"
            className="rounded-md bg-primary px-6 py-2.5 text-sm font-semibold text-white shadow hover:bg-info-bg"
          >
            {t('landing.startFreeTrial')}
          </Link>
          <Link
            to="/docs"
            className="rounded-md border border-border bg-surface px-6 py-2.5 text-sm font-semibold text-fg-muted hover:bg-surface"
          >
            {t('landing.documentation')}
          </Link>
        </div>

        {/* Feature highlights */}
        <div className="mt-16 grid max-w-4xl grid-cols-1 gap-8 sm:grid-cols-3">
          {[
            {
              title: t('landing.feature.canvasFirst.title'),
              desc: t('landing.feature.canvasFirst.desc'),
            },
            {
              title: t('landing.feature.aiNative.title'),
              desc: t('landing.feature.aiNative.desc'),
            },
            {
              title: t('landing.feature.devSecOps.title'),
              desc: t('landing.feature.devSecOps.desc'),
            },
          ].map((f) => (
            <div
              key={f.title}
              className="rounded-lg border border-border bg-surface/50 p-6 text-left"
            >
              <h3 className="text-sm font-semibold text-fg-muted">{f.title}</h3>
              <p className="mt-2 text-sm text-fg-muted">{f.desc}</p>
            </div>
          ))}
        </div>
      </main>

      {/* Footer */}
      <footer className="border-t border-border px-6 py-4 text-center text-xs text-fg-muted">
        {t('landing.footer', { year: String(new Date().getFullYear()) })}
      </footer>
    </div>
  );
};

export default LandingPage;
