/**
 * Product-family control plane page.
 *
 * @doc.type component
 * @doc.purpose Release readiness cockpit and reusable asset discovery driven by backend-owned YAPPC read models
 * @doc.layer product
 * @doc.pattern Page Component
 */

import React from 'react';
import { useTranslation } from '@ghatana/i18n';
import { useMutation, useQuery } from '@tanstack/react-query';
import { AlertTriangle, Boxes, CheckCircle2, FileWarning, GitBranch, RefreshCw, Search } from 'lucide-react';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/Button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import {
  getKernelTimeline,
  getReleaseReadiness,
  listDocTruthWarnings,
  listGuidedReuse,
  listProductAssets,
  promoteProductAsset,
  type ProductAsset,
  type ReleaseReadiness,
} from '@/clients/productFamilyClient';

const asText = (value: unknown): string => {
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  return JSON.stringify(value);
};

const verdictVariant = (verdict: string): 'default' | 'secondary' | 'destructive' | 'outline' => {
  if (verdict === 'READY' || verdict === 'PASS') return 'default';
  if (verdict === 'BLOCKED' || verdict === 'FAIL') return 'destructive';
  if (verdict === 'WARNING') return 'secondary';
  return 'outline';
};

const nextPromotionState = (asset: ProductAsset): 'hardened' | 'production' | 'shared-package' | null => {
  const state = (asset.promotionState || asset.maturity).toLowerCase();
  if (state === 'candidate') return 'hardened';
  if (state === 'hardened') return 'production';
  if (state === 'production') return 'shared-package';
  return null;
};

interface ReleasePanelProps {
  readonly productKey: 'phr' | 'digital-marketing';
  readonly title: string;
}

// YAPPC-002: Map environment tier to a display variant
const environmentTierVariant = (tier: string): 'default' | 'secondary' | 'destructive' | 'outline' => {
  if (tier === 'production') return 'default';
  if (tier === 'staging') return 'secondary';
  if (tier === 'dev') return 'outline';
  return 'outline';
};

const ReleasePanel: React.FC<ReleasePanelProps> = ({ productKey, title }) => {
  const { t } = useTranslation('common');
  const query = useQuery<ReleaseReadiness>({
    queryKey: ['product-family', 'release', productKey],
    queryFn: () => getReleaseReadiness(productKey),
    refetchInterval: 60_000,
  });

  if (query.isLoading) {
    return <div className="p-4 text-sm text-fg-muted">{t('productFamily.loading.releaseReadiness')}</div>;
  }

  if (query.isError || !query.data) {
    return (
      <Card variant="filled">
        <CardContent>
          <p className="text-sm text-destructive">{t('productFamily.error.releaseReadinessUnavailable')}</p>
        </CardContent>
      </Card>
    );
  }

  const release = query.data;
  const showDigitalMarketingGates = productKey === 'digital-marketing';
  const commitMismatch = release.commitMismatch === true;
  const environmentTier = release.environmentTier ?? 'local';

  return (
    <div className="space-y-3">
      {/* YAPPC-001: Stale evidence warning banner */}
      {commitMismatch ? (
        <div
          role="alert"
          aria-live="polite"
          className="flex items-start gap-3 rounded-md border border-warning-color bg-warning-muted px-4 py-3"
        >
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-warning-color" aria-hidden="true" />
          <div className="space-y-0.5">
            <p className="text-sm font-semibold text-warning-color">
              {t('productFamily.release.commitMismatch.title')}
            </p>
            <p className="text-xs text-fg-muted">
              {t('productFamily.release.commitMismatch.detail', {
                evidenceCommit: release.evidenceCommit ? release.evidenceCommit.slice(0, 12) : '—',
                targetCommit: release.targetCommit ? release.targetCommit.slice(0, 12) : '—',
              })}
            </p>
          </div>
        </div>
      ) : null}

      <div className="grid gap-4 lg:grid-cols-[1.2fr_0.8fr]">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between gap-3">
            <h2 className="text-lg font-semibold text-fg">{title}</h2>
            <div className="flex items-center gap-2">
              {/* YAPPC-002: Environment tier badge */}
              <Badge variant={environmentTierVariant(environmentTier)}>
                {environmentTier}
              </Badge>
              <Badge variant={verdictVariant(release.verdict)}>{release.verdict}</Badge>
            </div>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid gap-3 md:grid-cols-3">
              <Metric label={t('productFamily.metric.backendStatus')} value={release.status} />
              <Metric label={t('productFamily.metric.evidenceRefs')} value={String(release.evidenceRefs.length)} />
              <Metric label={t('productFamily.metric.traceId')} value={release.traceId || t('productFamily.fallback.notRecorded')} />
            </div>
            {/* YAPPC-001: Commit alignment metrics */}
            {(release.evidenceCommit || release.targetCommit) ? (
              <div className="grid gap-3 md:grid-cols-2">
                <Metric
                  label={t('productFamily.metric.evidenceCommit')}
                  value={release.evidenceCommit ? release.evidenceCommit.slice(0, 12) : t('productFamily.fallback.unknown')}
                />
                <Metric
                  label={t('productFamily.metric.targetCommit')}
                  value={release.targetCommit ? release.targetCommit.slice(0, 12) : t('productFamily.fallback.unknown')}
                />
              </div>
            ) : null}
            <ListBlock title={t('productFamily.release.gateStatus')} items={release.gateStatus} />
            <ListBlock title={t('productFamily.release.blockers')} items={release.blockers} />
            {showDigitalMarketingGates ? (
              <div className="grid gap-3 md:grid-cols-3">
                <ListBlock title={t('productFamily.release.connectorGates')} items={release.connectorGates ?? []} />
                <ListBlock title={t('productFamily.release.approvalGates')} items={release.approvalGates ?? []} />
                <ListBlock title={t('productFamily.release.aiActionGates')} items={release.aiActionGates ?? []} />
              </div>
            ) : null}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <h2 className="text-lg font-semibold text-fg">{t('productFamily.release.foundationReadinessTitle')}</h2>
          </CardHeader>
          <CardContent className="space-y-3">
            <ListBlock title={t('productFamily.release.foundationSlices')} items={release.foundationReadiness} />
            <ListBlock title={t('productFamily.release.docTruthWarnings')} items={release.docTruthWarnings} />
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

const AssetRegistryPanel: React.FC = () => {
  const { t } = useTranslation('common');
  const [filters, setFilters] = React.useState({
    search: '',
    product: '',
    domain: '',
    type: '',
    maturity: '',
    reuseMode: '',
    compatibility: '',
  });
  const activeFilters = React.useMemo(
    () => Object.fromEntries(Object.entries(filters).filter(([, value]) => value.trim().length > 0)),
    [filters],
  );
  const query = useQuery({
    queryKey: ['product-family', 'assets', activeFilters],
    queryFn: () => listProductAssets(activeFilters),
    refetchInterval: 60_000,
  });
  const promotion = useMutation({
    mutationFn: ({ assetId, targetState }: { readonly assetId: string; readonly targetState: 'hardened' | 'production' | 'shared-package' }) =>
      promoteProductAsset(assetId, {
        targetState,
        promotionTarget: targetState === 'shared-package' ? 'shared package' : undefined,
        reason: t('productFamily.assets.promotionReason'),
      }),
    onSuccess: () => {
      void query.refetch();
    },
  });

  if (query.isLoading) {
    return <div className="p-4 text-sm text-fg-muted">{t('productFamily.loading.reusableAssets')}</div>;
  }

  const assets = query.data?.assets ?? [];
  return (
    <section className="space-y-4">
      <div className="flex flex-row items-center justify-between gap-3">
        <h2 className="text-lg font-semibold text-fg">{t('productFamily.assets.title')}</h2>
        <Badge variant={query.data?.status === 'READY' ? 'default' : 'secondary'}>
          {query.data?.status ?? t('productFamily.status.unavailable')}
        </Badge>
      </div>
      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
        <Input
          label={t('productFamily.assets.search')}
          value={filters.search}
          leftIcon={<Search className="h-4 w-4" aria-hidden="true" />}
          onChange={(event) => setFilters((current) => ({ ...current, search: event.target.value }))}
          fullWidth
        />
        <Select
          label={t('productFamily.assets.product')}
          value={filters.product}
          onChange={(event) => setFilters((current) => ({ ...current, product: event.target.value }))}
          options={[
            { value: '', label: t('productFamily.assets.allProducts') },
            { value: 'phr', label: t('productFamily.product.phr') },
            { value: 'digital-marketing', label: t('productFamily.product.digitalMarketing') },
          ]}
          fullWidth
        />
        <Select
          label={t('productFamily.assets.type')}
          value={filters.type}
          onChange={(event) => setFilters((current) => ({ ...current, type: event.target.value }))}
          options={[
            { value: '', label: t('productFamily.assets.allTypes') },
            { value: 'module', label: t('productFamily.assetType.module') },
            { value: 'plugin', label: t('productFamily.assetType.plugin') },
            { value: 'template', label: t('productFamily.assetType.template') },
            { value: 'schema', label: t('productFamily.assetType.schema') },
            { value: 'connector', label: t('productFamily.assetType.connector') },
          ]}
          fullWidth
        />
        <Select
          label={t('productFamily.assets.maturity')}
          value={filters.maturity}
          onChange={(event) => setFilters((current) => ({ ...current, maturity: event.target.value }))}
          options={[
            { value: '', label: t('productFamily.assets.allMaturity') },
            { value: 'candidate', label: t('productFamily.maturity.candidate') },
            { value: 'hardened', label: t('productFamily.maturity.hardened') },
            { value: 'production', label: t('productFamily.maturity.production') },
          ]}
          fullWidth
        />
        <Select
          label={t('productFamily.assets.reuseMode')}
          value={filters.reuseMode}
          onChange={(event) => setFilters((current) => ({ ...current, reuseMode: event.target.value }))}
          options={[
            { value: '', label: t('productFamily.assets.allModes') },
            { value: 'reference', label: t('productFamily.reuseMode.reference') },
            { value: 'shared-package', label: t('productFamily.reuseMode.sharedPackage') },
            { value: 'plugin', label: t('productFamily.reuseMode.plugin') },
            { value: 'template', label: t('productFamily.reuseMode.template') },
          ]}
          fullWidth
        />
        <Input
          label={t('productFamily.assets.domain')}
          value={filters.domain}
          onChange={(event) => setFilters((current) => ({ ...current, domain: event.target.value }))}
          fullWidth
        />
        <Input
          label={t('productFamily.assets.compatibility')}
          value={filters.compatibility}
          onChange={(event) => setFilters((current) => ({ ...current, compatibility: event.target.value }))}
          fullWidth
        />
        <div className="flex items-end">
          <Button type="button" variant="outline" onClick={() => setFilters({
            search: '',
            product: '',
            domain: '',
            type: '',
            maturity: '',
            reuseMode: '',
            compatibility: '',
          })}>
            {t('productFamily.assets.clear')}
          </Button>
        </div>
      </div>
      <div>
        {query.data?.warnings.map((warning) => (
          <p key={warning} className="mb-3 text-sm text-warning-color">{warning}</p>
        ))}
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          {assets.map((asset: ProductAsset) => (
            <Card key={asset.assetId} variant="filled" size="sm">
              <CardHeader>
                <CardTitle className="text-base">{asset.displayName}</CardTitle>
              </CardHeader>
              <CardContent className="space-y-2 text-sm">
                <div className="flex flex-wrap gap-2">
                  <Badge variant="outline">{asset.type}</Badge>
                  <Badge variant="secondary">{asset.maturity}</Badge>
                  <Badge variant="outline">{asset.reuseMode}</Badge>
                </div>
                <p className="text-fg-muted">{asset.sourceProduct} / {asset.domain}</p>
                <p className="text-fg-muted">{t('productFamily.assets.owner', { owner: asset.owner })}</p>
                <p className="text-fg-muted">
                  {t('productFamily.assets.promotion', {
                    target: asset.promotionTarget || t('productFamily.fallback.notAssigned'),
                  })}
                </p>
                {nextPromotionState(asset) ? (
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={promotion.isPending}
                    onClick={() => {
                      const targetState = nextPromotionState(asset);
                      if (targetState) {
                        promotion.mutate({ assetId: asset.assetId, targetState });
                      }
                    }}
                  >
                    {t('productFamily.assets.promoteTo', {
                      state: t(`productFamily.promotionState.${nextPromotionState(asset)}`),
                    })}
                  </Button>
                ) : null}
              </CardContent>
            </Card>
          ))}
        </div>
        {promotion.isError ? (
          <p className="mt-3 text-sm text-destructive">{t('productFamily.assets.promotionFailed')}</p>
        ) : null}
      </div>
    </section>
  );
};

const TruthAndReusePanel: React.FC = () => {
  const { t } = useTranslation('common');
  const docTruth = useQuery({
    queryKey: ['product-family', 'doc-truth'],
    queryFn: listDocTruthWarnings,
    refetchInterval: 60_000,
  });
  const tutorputor = useQuery({
    queryKey: ['product-family', 'reuse', 'tutorputor'],
    queryFn: () => listGuidedReuse('tutorputor'),
  });
  const flashit = useQuery({
    queryKey: ['product-family', 'reuse', 'flashit'],
    queryFn: () => listGuidedReuse('flashit'),
  });

  return (
    <div className="grid gap-4 lg:grid-cols-3">
      <Card>
        <CardHeader>
          <h2 className="text-lg font-semibold text-fg">{t('productFamily.truth.title')}</h2>
        </CardHeader>
        <CardContent>
          <Badge variant={docTruth.data?.status === 'READY' ? 'default' : 'secondary'}>
            {docTruth.data?.status ?? t('productFamily.status.loading')}
          </Badge>
          <ListBlock title={t('productFamily.truth.warnings')} items={docTruth.data?.warnings ?? []} />
        </CardContent>
      </Card>
      <GuidedReuseCard title={t('productFamily.reuse.tutorputor')} status={tutorputor.data?.status} items={tutorputor.data?.recommendations ?? []} targetProduct="tutorputor" />
      <GuidedReuseCard title={t('productFamily.reuse.flashit')} status={flashit.data?.status} items={flashit.data?.recommendations ?? []} targetProduct="flashit" />
    </div>
  );
};

const KernelTimelinePanel: React.FC = () => {
  const { t } = useTranslation('common');
  const [productUnitId, setProductUnitId] = React.useState('phr');
  const query = useQuery({
    queryKey: ['product-family', 'kernel-timeline', productUnitId],
    queryFn: () => getKernelTimeline(productUnitId),
    refetchInterval: 60_000,
  });

  return (
    <Card>
      <CardHeader>
        <h2 className="text-lg font-semibold text-fg">{t('productFamily.kernel.title')}</h2>
      </CardHeader>
      <CardContent className="space-y-4">
        <Select
          label={t('productFamily.kernel.productUnit')}
          value={productUnitId}
          onChange={(event) => setProductUnitId(event.target.value)}
          options={[
            { value: 'phr', label: t('productFamily.product.phr') },
            { value: 'digital-marketing', label: t('productFamily.product.digitalMarketing') },
          ]}
          fullWidth
        />
        <Badge variant={query.data?.status === 'READY' ? 'default' : 'secondary'}>
          {query.data?.status ?? t('productFamily.status.loading')}
        </Badge>
        <ListBlock title={t('productFamily.kernel.timelineStages')} items={query.data?.timeline ?? []} />
        <ListBlock title={t('productFamily.kernel.rollbackVisibility')} items={[query.data?.rollbackVisibility ?? {}]} />
      </CardContent>
    </Card>
  );
};

interface MetricProps {
  readonly label: string;
  readonly value: string;
}

const Metric: React.FC<MetricProps> = ({ label, value }) => (
  <div className="rounded-md border border-border p-3">
    <p className="text-xs text-fg-muted">{label}</p>
    <p className="mt-1 truncate text-sm font-semibold text-fg">{value}</p>
  </div>
);

interface ListBlockProps {
  readonly title: string;
  readonly items: readonly unknown[];
}

const ListBlock: React.FC<ListBlockProps> = ({ title, items }) => (
  <div className="space-y-2">
    <p className="text-sm font-semibold text-fg">{title}</p>
    {items.length === 0 ? (
      <ListBlockEmpty />
    ) : (
      <ul className="space-y-2">
        {items.slice(0, 8).map((item, index) => (
          <li key={`${title}-${index}`} className="rounded-md bg-surface-muted p-2 text-xs text-fg-muted">
            {asText(item)}
          </li>
        ))}
      </ul>
    )}
  </div>
);

const ListBlockEmpty: React.FC = () => {
  const { t } = useTranslation('common');
  return <p className="text-sm text-fg-muted">{t('productFamily.fallback.noBackendRecords')}</p>;
};

// YAPPC-003: Candidate-blocked products must show a clear status, not a false "READY"
const CANDIDATE_BLOCKED_PRODUCTS = new Set(['tutorputor', 'flashit']);

interface GuidedReuseCardProps {
  readonly title: string;
  readonly status: string | undefined;
  readonly items: readonly unknown[];
  readonly targetProduct?: string;
}

const GuidedReuseCard: React.FC<GuidedReuseCardProps> = ({ title, status, items, targetProduct }) => {
  const { t } = useTranslation('common');
  const isBlocked = targetProduct != null && CANDIDATE_BLOCKED_PRODUCTS.has(targetProduct);
  const effectiveStatus = isBlocked ? 'CANDIDATE' : (status ?? 'LOADING');
  const badgeVariant = isBlocked ? 'secondary' : (status === 'READY' ? 'default' : 'secondary');
  return (
    <Card>
      <CardHeader>
        <h2 className="text-lg font-semibold text-fg">{title}</h2>
      </CardHeader>
      <CardContent className="space-y-3">
        <Badge variant={badgeVariant}>{effectiveStatus}</Badge>
        {isBlocked ? (
          <div
            role="status"
            className="flex items-start gap-2 rounded-md border border-border bg-surface-muted p-3"
          >
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-fg-muted" aria-hidden="true" />
            <p className="text-xs text-fg-muted">
              {t('productFamily.reuse.candidateBlocked', { product: title })}
            </p>
          </div>
        ) : null}
        <GuidedReuseItems items={items} />
      </CardContent>
    </Card>
  );
};

const GuidedReuseItems: React.FC<{ readonly items: readonly unknown[] }> = ({ items }) => {
  const { t } = useTranslation('common');
  return <ListBlock title={t('productFamily.reuse.recommendations')} items={items} />;
};

export const ProductFamilyControlPlanePage: React.FC = () => {
  const { t } = useTranslation('common');

  return (
    <main className="min-h-full bg-bg-default p-6">
      <div className="mx-auto max-w-7xl space-y-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h1 className="text-2xl font-semibold text-fg">{t('productFamily.title')}</h1>
            <p className="mt-1 text-sm text-fg-muted">{t('productFamily.subtitle')}</p>
          </div>
          <Button type="button" variant="outline" onClick={() => window.location.reload()}>
            <RefreshCw className="mr-2 h-4 w-4" aria-hidden="true" />
            {t('productFamily.refresh')}
          </Button>
        </div>

        <div className="grid gap-3 md:grid-cols-4">
          <SummaryTile icon={<CheckCircle2 className="h-5 w-5" />} label={t('productFamily.summary.phr.label')} value={t('productFamily.summary.phr.value')} />
          <SummaryTile icon={<GitBranch className="h-5 w-5" />} label={t('productFamily.summary.dmos.label')} value={t('productFamily.summary.dmos.value')} />
          <SummaryTile icon={<Boxes className="h-5 w-5" />} label={t('productFamily.summary.assets.label')} value={t('productFamily.summary.assets.value')} />
          <SummaryTile icon={<FileWarning className="h-5 w-5" />} label={t('productFamily.summary.truth.label')} value={t('productFamily.summary.truth.value')} />
        </div>

        <Tabs defaultValue="phr" className="space-y-4">
          <TabsList>
            <TabsTrigger value="phr">{t('productFamily.tabs.phr')}</TabsTrigger>
            <TabsTrigger value="dmos">{t('productFamily.tabs.dmos')}</TabsTrigger>
            <TabsTrigger value="assets">{t('productFamily.tabs.assets')}</TabsTrigger>
            <TabsTrigger value="truth">{t('productFamily.tabs.truth')}</TabsTrigger>
            <TabsTrigger value="kernel">{t('productFamily.tabs.kernel')}</TabsTrigger>
          </TabsList>
          <TabsContent value="phr"><ReleasePanel productKey="phr" title={t('productFamily.release.phrTitle')} /></TabsContent>
          <TabsContent value="dmos"><ReleasePanel productKey="digital-marketing" title={t('productFamily.release.dmosTitle')} /></TabsContent>
          <TabsContent value="assets"><AssetRegistryPanel /></TabsContent>
          <TabsContent value="truth"><TruthAndReusePanel /></TabsContent>
          <TabsContent value="kernel"><KernelTimelinePanel /></TabsContent>
        </Tabs>
      </div>
    </main>
  );
};

const SummaryTile: React.FC<{ readonly icon: React.ReactNode; readonly label: string; readonly value: string }> = ({
  icon,
  label,
  value,
}) => (
  <div className="flex items-center gap-3 rounded-md border border-border bg-surface p-3">
    <div className="text-info-color">{icon}</div>
    <div>
      <p className="text-xs text-fg-muted">{label}</p>
      <p className="text-sm font-semibold text-fg">{value}</p>
    </div>
  </div>
);

export default ProductFamilyControlPlanePage;
