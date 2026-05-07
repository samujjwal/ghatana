/**
 * Compatibility notice for retained project deep links.
 *
 * @doc.type component
 * @doc.purpose Marks legacy project routes as compatibility-only
 * @doc.layer product
 * @doc.pattern Route Helper
 */

interface LegacyRouteCompatibilityNoticeProps {
  readonly projectId: string | undefined;
  readonly legacySurface: string;
  readonly canonicalPhase: 'intent' | 'shape' | 'validate' | 'generate' | 'run' | 'observe' | 'learn' | 'evolve';
  readonly reason: string;
}

export function LegacyRouteCompatibilityNotice(props: LegacyRouteCompatibilityNoticeProps) {
  const { projectId, legacySurface, canonicalPhase, reason } = props;
  const canonicalHref = projectId ? `/p/${projectId}/${canonicalPhase}` : undefined;

  return (
    <aside
      className="m-4 rounded-xl border border-info-border bg-info-bg px-4 py-3 text-sm text-info-color dark:border-info-border/60 dark:bg-info-bg/30 dark:text-info-color"
      data-testid="legacy-route-compatibility-notice"
    >
      <p className="font-semibold">{legacySurface} is a compatibility deep link.</p>
      <p className="mt-1">
        {reason} The canonical project journey now runs through the eight phase tabs.
        {canonicalHref ? (
          <>
            {' '}
            Continue in the{' '}
            <a className="font-semibold underline underline-offset-2" href={canonicalHref}>
              {canonicalPhase} phase cockpit
            </a>
            .
          </>
        ) : null}
      </p>
    </aside>
  );
}
