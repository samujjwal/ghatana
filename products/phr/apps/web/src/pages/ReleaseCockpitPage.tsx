import React from 'react';
import { Button, Card, CardContent, CardHeader } from '@ghatana/design-system';
import { fetchReleaseReadiness } from '../api/releaseApi';
import { usePhrAccess } from '../auth/PhrAccessContext';
import { t } from '../i18n/phrI18n';
import type { PhrReleaseReadiness, PhrReleaseReadinessSection } from '../types';

type ReleaseEnvironment = 'local' | 'dev' | 'staging' | 'prod';

const environments = ['local', 'dev', 'staging', 'prod'] as const satisfies readonly ReleaseEnvironment[];
const sectionOrder = [
  'evidenceFreshness',
  'fhirRuntime',
  'consentCache',
  'deployment',
  'rollback',
  'dataCloudRuntime',
] as const;

export function ReleaseCockpitPage(): React.ReactElement {
  const { role, tenantId, principalId } = usePhrAccess();
  const [environment, setEnvironment] = React.useState<ReleaseEnvironment>('staging');
  const [readiness, setReadiness] = React.useState<PhrReleaseReadiness | null>(null);
  const [loading, setLoading] = React.useState<boolean>(true);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    fetchReleaseReadiness({ environment, role, tenantId, principalId })
      .then((payload) => {
        if (active) {
          setReadiness(payload);
        }
      })
      .catch((err: unknown) => {
        if (active) {
          setReadiness(null);
          setError(err instanceof Error ? err.message : t('release.error'));
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [environment, role, tenantId, principalId]);

  return (
    <div className="stack gap-lg">
      <section className="hero-panel">
        <div className="release-hero">
          <div>
            <p className="eyebrow">{t('release.eyebrow')}</p>
            <h2>{t('release.title')}</h2>
            <p className="muted">{t('release.subheader')}</p>
          </div>
          <div className="segmented-control" role="group" aria-label={t('release.environment')}>
            {environments.map((item) => (
              <Button
                key={item}
                className={item === environment ? 'primary-cta' : 'secondary-button'}
                onClick={() => setEnvironment(item)}
              >
                {environmentLabel(item)}
              </Button>
            ))}
          </div>
        </div>
      </section>

      {loading ? <div className="loading">{t('release.loading')}</div> : null}
      {error ? <div className="error">{t('release.errorPrefix')}: {error}</div> : null}
      {!loading && !error && readiness ? <ReadinessSummary readiness={readiness} /> : null}
    </div>
  );
}

function ReadinessSummary({ readiness }: { readiness: PhrReleaseReadiness }): React.ReactElement {
  const status = readiness.runtimeTruthBlocked ? t('release.status.blocked') : t('release.status.ready');
  const sections = sectionOrder.reduce<Array<readonly [string, PhrReleaseReadinessSection]>>((items, id) => {
    const section = readiness.sections[id];
    if (section) {
      items.push([id, section]);
    }
    return items;
  }, []);

  return (
    <>
      <section className="metric-strip release-metrics" aria-label={t('release.metrics')}>
        <div>
          <span>{status}</span>
          <small>{t('release.metric.runtimeTruth')}</small>
        </div>
        <div>
          <span>{readiness.releaseReadiness?.overallScore ?? '-'}</span>
          <small>{t('release.metric.score')}</small>
        </div>
        <div>
          <span>{readiness.environment}</span>
          <small>{t('release.metric.environment')}</small>
        </div>
        <div>
          <span>{shortSha(readiness.targetCommitSha)}</span>
          <small>{t('release.metric.commit')}</small>
        </div>
      </section>

      {readiness.runtimeTruthBlocked ? (
        <Card>
          <CardHeader title={t('release.blocked.title')} subheader={t('release.blocked.subheader')} />
          <CardContent>
            <p className="muted">{t('release.blocked.body')}</p>
          </CardContent>
        </Card>
      ) : null}

      <section className="dashboard-grid">
        {sections.map(([id, section]) => (
          <Card key={id}>
            <CardHeader title={section.label} subheader={section.message} />
            <CardContent>
              <div className="stack gap-md">
                <span className={section.runtimeProven ? 'pill' : 'pill warning'}>
                  {section.runtimeProven ? t('release.proof.runtimeProven') : t('release.proof.blocked')}
                </span>
                <code className="code-inline">{section.status}</code>
              </div>
            </CardContent>
          </Card>
        ))}
      </section>
    </>
  );
}

function shortSha(value: string): string {
  return value ? value.slice(0, 12) : '-';
}

function environmentLabel(environment: ReleaseEnvironment): string {
  switch (environment) {
    case 'local':
      return t('release.environment.local');
    case 'dev':
      return t('release.environment.dev');
    case 'staging':
      return t('release.environment.staging');
    case 'prod':
      return t('release.environment.prod');
  }
}
