import type { ReactElement } from 'react';
import { Badge } from '@ghatana/design-system';
import { useStudioLifecycleData } from '../data/StudioLifecycleDataContext';
import { useStudioTranslation } from '../i18n/studioTranslations';
import {
  describeLifecycleDataStatus,
  formatBytes,
  lifecycleDataBadgeTone,
} from './studioLifecycleRouteSupport';

export default function ArtifactsPage(): ReactElement {
  const lifecycleData = useStudioLifecycleData();
  const t = useStudioTranslation();
  const snapshot = lifecycleData.snapshot;
  const artifacts = snapshot.artifactManifest?.artifacts ?? [];

  return (
    <section className="space-y-6" aria-labelledby="artifacts-title">
      <div className="space-y-2">
        <Badge tone={lifecycleDataBadgeTone(snapshot.status)} variant="soft">
          {describeLifecycleDataStatus(snapshot.status)}
        </Badge>
        <h2 id="artifacts-title" className="text-2xl font-semibold text-gray-950">
          {t('studio.route.artifacts.title')}
        </h2>
        <p className="max-w-3xl text-sm leading-6 text-gray-600">
          {t('studio.route.artifacts.description')}
        </p>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        {artifacts.length === 0 ? (
          <article className="studio-card space-y-2">
            <h3 className="text-base font-semibold text-gray-950">
              {t('studio.route.artifacts.emptyTitle')}
            </h3>
            <p className="text-sm leading-6 text-gray-600">
              {t('studio.route.artifacts.emptyDescription')}
            </p>
          </article>
        ) : null}
        {artifacts.map((artifact) => (
          <article key={artifact.id} className="studio-card space-y-3">
            <div className="flex items-center justify-between gap-3">
              <h3 className="text-base font-semibold text-gray-950">{artifact.id}</h3>
              <Badge tone={artifact.found ? 'success' : 'danger'} variant="soft">
                {artifact.found ? 'found' : 'missing'}
              </Badge>
            </div>
            <dl className="grid gap-2 text-sm text-gray-600">
              <div className="flex justify-between gap-3">
                <dt className="font-medium text-gray-900">Type</dt>
                <dd>{artifact.metadata.type}</dd>
              </div>
              <div className="flex justify-between gap-3">
                <dt className="font-medium text-gray-900">Packaging</dt>
                <dd>{artifact.metadata.packaging}</dd>
              </div>
              <div className="flex justify-between gap-3">
                <dt className="font-medium text-gray-900">Fingerprint</dt>
                <dd>{`${artifact.fingerprint.algorithm}:${artifact.fingerprint.hash}`}</dd>
              </div>
              <div className="flex justify-between gap-3">
                <dt className="font-medium text-gray-900">Size</dt>
                <dd>{formatBytes(artifact.metadata.sizeBytes)}</dd>
              </div>
            </dl>
          </article>
        ))}
      </div>
    </section>
  );
}
