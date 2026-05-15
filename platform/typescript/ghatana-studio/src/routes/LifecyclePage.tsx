import type { ReactElement } from 'react';
import { Badge } from '@ghatana/design-system';
import { useStudioLifecycleData } from '../data/StudioLifecycleDataContext';
import { useStudioTranslation } from '../i18n/studioTranslations';
import {
  describeLifecycleDataStatus,
  lifecycleDataBadgeTone,
} from './studioLifecycleRouteSupport';

const LIFECYCLE_STEPS = ['validate', 'build', 'package', 'deploy', 'verify'] as const;

export default function LifecyclePage(): ReactElement {
  const lifecycleData = useStudioLifecycleData();
  const t = useStudioTranslation();
  const selectedRun = lifecycleData.selectedRun;
  const lifecyclePlan = {
    productUnitId: lifecycleData.productUnit?.id ?? 'digital-marketing',
    runId: selectedRun?.runId ?? 'awaiting-run',
    correlationId: selectedRun?.correlationId ?? 'awaiting-correlation',
    phase: selectedRun?.phase ?? 'build',
    status: selectedRun?.status ?? describeLifecycleDataStatus(lifecycleData.status),
  } as const;
  const gateCount = lifecycleData.gateResultManifest?.gates.length ?? 0;
  const artifactCount = lifecycleData.artifactManifest?.artifacts.length ?? 0;
  const deploymentStatus = lifecycleData.deploymentManifest?.overallStatus ?? 'blocked';
  const verificationStatus = lifecycleData.verifyHealthReport?.status ?? 'unknown';

  return (
    <section className="space-y-6" aria-labelledby="lifecycle-title">
      <div className="space-y-2">
        <Badge tone={lifecycleDataBadgeTone(lifecycleData.status)} variant="soft">
          {describeLifecycleDataStatus(lifecycleData.status)}
        </Badge>
        <h2 id="lifecycle-title" className="text-2xl font-semibold text-gray-950">
          {t('studio.route.lifecycle.title')}
        </h2>
        <p className="max-w-3xl text-sm leading-6 text-gray-600">
          {t('studio.route.lifecycle.description')}
        </p>
      </div>

      <div className="grid gap-4 lg:grid-cols-[1.2fr_0.8fr]">
        <article className="studio-card space-y-3" aria-labelledby="plan-summary-title">
          <div className="flex items-center justify-between gap-3">
            <h3 id="plan-summary-title" className="text-base font-semibold text-gray-950">
              {t('studio.route.lifecycle.planTitle')}
            </h3>
            <Badge tone="info" variant="soft">{lifecyclePlan.status}</Badge>
          </div>
          <pre className="overflow-auto rounded-md bg-gray-950 p-4 text-xs leading-5 text-gray-100">
            {JSON.stringify(lifecyclePlan, null, 2)}
          </pre>
        </article>

        <article className="studio-card space-y-3" aria-labelledby="step-graph-title">
          <h3 id="step-graph-title" className="text-base font-semibold text-gray-950">
            {t('studio.route.lifecycle.stepGraphTitle')}
          </h3>
          <ol className="space-y-3">
            {LIFECYCLE_STEPS.map((step: string, index: number) => (
              <li key={step} className="flex items-center gap-3 text-sm">
                <span className="flex h-7 w-7 items-center justify-center rounded-full border border-gray-300 font-medium">
                  {index + 1}
                </span>
                <span className="font-medium text-gray-900">{step}</span>
              </li>
            ))}
          </ol>
        </article>
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <TruthPanel title="Gates" status={`${gateCount} gates`} detail="Security, policy, privacy, and release gates appear here with required/optional markers." />
        <TruthPanel title="Artifacts" status={`${artifactCount} artifacts`} detail="Expected and found artifacts are linked to the artifact manifest for this run." />
        <TruthPanel title="Deployment" status={deploymentStatus} detail="Deployment details remain blocked until approval and manifest validation pass." />
        <TruthPanel title="Verification" status={verificationStatus} detail="Health report and verifier evidence are shown after the verify phase writes truth." />
        <TruthPanel title="Approval requirement" status="pending approval" detail="Human approval is required before risky or production-facing actions." />
        <TruthPanel title="Failure diagnostics" status="requires verification" detail="Reason codes such as gate-failed, artifact-missing, or provider-unavailable stay visible." />
      </div>

      <article className="studio-card space-y-2" aria-labelledby="validation-command-title">
        <h3 id="validation-command-title" className="text-base font-semibold text-gray-950">
          {t('studio.route.lifecycle.validationCommandTitle')}
        </h3>
        <code className="block rounded-md bg-gray-100 p-3 text-sm text-gray-900">
          pnpm check:digital-marketing-lifecycle-pilot --smoke
        </code>
      </article>
    </section>
  );
}

interface TruthPanelProps {
  readonly title: string;
  readonly status: string;
  readonly detail: string;
}

function TruthPanel(props: TruthPanelProps): ReactElement {
  return (
    <article className="studio-card space-y-3">
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-base font-semibold text-gray-950">{props.title}</h3>
        <Badge tone="neutral" variant="outline">{props.status}</Badge>
      </div>
      <p className="text-sm leading-6 text-gray-600">{props.detail}</p>
    </article>
  );
}
