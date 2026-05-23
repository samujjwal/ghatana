/**
 * Product-family control plane page.
 *
 * @doc.type component
 * @doc.purpose Release readiness cockpit and reusable asset discovery driven by backend-owned YAPPC read models
 * @doc.layer product
 * @doc.pattern Page Component
 */

import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { Boxes, CheckCircle2, FileWarning, GitBranch, RefreshCw, Search } from 'lucide-react';

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

interface ReleasePanelProps {
  readonly productKey: 'phr' | 'digital-marketing';
  readonly title: string;
}

const ReleasePanel: React.FC<ReleasePanelProps> = ({ productKey, title }) => {
  const query = useQuery<ReleaseReadiness>({
    queryKey: ['product-family', 'release', productKey],
    queryFn: () => getReleaseReadiness(productKey),
    refetchInterval: 60_000,
  });

  if (query.isLoading) {
    return <div className="p-4 text-sm text-fg-muted">Loading release readiness...</div>;
  }

  if (query.isError || !query.data) {
    return (
      <Card variant="filled">
        <CardContent>
          <p className="text-sm text-destructive">Release readiness is unavailable from the backend.</p>
        </CardContent>
      </Card>
    );
  }

  const release = query.data;
  return (
    <div className="grid gap-4 lg:grid-cols-[1.2fr_0.8fr]">
      <Card>
        <CardHeader className="flex flex-row items-center justify-between gap-3">
          <CardTitle>{title}</CardTitle>
          <Badge variant={verdictVariant(release.verdict)}>{release.verdict}</Badge>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-3 md:grid-cols-3">
            <Metric label="Backend status" value={release.status} />
            <Metric label="Evidence refs" value={String(release.evidenceRefs.length)} />
            <Metric label="Trace ID" value={release.traceId || 'not recorded'} />
          </div>
          <ListBlock title="Gate status" items={release.gateStatus} />
          <ListBlock title="Blockers" items={release.blockers} />
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>Foundation Readiness By Usage</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <ListBlock title="Required, disabled, future, and unused slices" items={release.foundationReadiness} />
          <ListBlock title="Doc-truth warnings" items={release.docTruthWarnings} />
        </CardContent>
      </Card>
    </div>
  );
};

const AssetRegistryPanel: React.FC = () => {
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

  if (query.isLoading) {
    return <div className="p-4 text-sm text-fg-muted">Loading reusable assets...</div>;
  }

  const assets = query.data?.assets ?? [];
  return (
    <section className="space-y-4">
      <div className="flex flex-row items-center justify-between gap-3">
        <h2 className="text-lg font-semibold text-fg">Reusable Product Asset Registry</h2>
        <Badge variant={query.data?.status === 'READY' ? 'default' : 'secondary'}>
          {query.data?.status ?? 'UNAVAILABLE'}
        </Badge>
      </div>
      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
        <Input
          label="Search"
          value={filters.search}
          leftIcon={<Search className="h-4 w-4" aria-hidden="true" />}
          onChange={(event) => setFilters((current) => ({ ...current, search: event.target.value }))}
          fullWidth
        />
        <Select
          label="Product"
          value={filters.product}
          onChange={(event) => setFilters((current) => ({ ...current, product: event.target.value }))}
          options={[
            { value: '', label: 'All products' },
            { value: 'phr', label: 'PHR' },
            { value: 'digital-marketing', label: 'Digital Marketing' },
          ]}
          fullWidth
        />
        <Select
          label="Type"
          value={filters.type}
          onChange={(event) => setFilters((current) => ({ ...current, type: event.target.value }))}
          options={[
            { value: '', label: 'All types' },
            { value: 'module', label: 'Module' },
            { value: 'plugin', label: 'Plugin' },
            { value: 'template', label: 'Template' },
            { value: 'schema', label: 'Schema' },
            { value: 'connector', label: 'Connector' },
          ]}
          fullWidth
        />
        <Select
          label="Maturity"
          value={filters.maturity}
          onChange={(event) => setFilters((current) => ({ ...current, maturity: event.target.value }))}
          options={[
            { value: '', label: 'All maturity' },
            { value: 'candidate', label: 'Candidate' },
            { value: 'hardened', label: 'Hardened' },
            { value: 'production', label: 'Production' },
          ]}
          fullWidth
        />
        <Select
          label="Reuse mode"
          value={filters.reuseMode}
          onChange={(event) => setFilters((current) => ({ ...current, reuseMode: event.target.value }))}
          options={[
            { value: '', label: 'All modes' },
            { value: 'reference', label: 'Reference' },
            { value: 'shared-package', label: 'Shared package' },
            { value: 'plugin', label: 'Plugin' },
            { value: 'template', label: 'Template' },
          ]}
          fullWidth
        />
        <Input
          label="Domain"
          value={filters.domain}
          onChange={(event) => setFilters((current) => ({ ...current, domain: event.target.value }))}
          fullWidth
        />
        <Input
          label="Compatibility"
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
            Clear
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
                <p className="text-fg-muted">Owner: {asset.owner}</p>
                <p className="text-fg-muted">Promotion: {asset.promotionTarget || 'not assigned'}</p>
              </CardContent>
            </Card>
          ))}
        </div>
      </div>
    </section>
  );
};

const TruthAndReusePanel: React.FC = () => {
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
          <CardTitle>Doc / Registry / Code Truth</CardTitle>
        </CardHeader>
        <CardContent>
          <Badge variant={docTruth.data?.status === 'READY' ? 'default' : 'secondary'}>
            {docTruth.data?.status ?? 'LOADING'}
          </Badge>
          <ListBlock title="Warnings" items={docTruth.data?.warnings ?? []} />
        </CardContent>
      </Card>
      <GuidedReuseCard title="Tutorputor Guided Reuse" status={tutorputor.data?.status} items={tutorputor.data?.recommendations ?? []} />
      <GuidedReuseCard title="FlashIt Guided Reuse" status={flashit.data?.status} items={flashit.data?.recommendations ?? []} />
    </div>
  );
};

const KernelTimelinePanel: React.FC = () => {
  const query = useQuery({
    queryKey: ['product-family', 'kernel-timeline', 'phr'],
    queryFn: () => getKernelTimeline('phr'),
    refetchInterval: 60_000,
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>Kernel Lifecycle Timeline And Rollback Visibility</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <Badge variant={query.data?.status === 'READY' ? 'default' : 'secondary'}>
          {query.data?.status ?? 'LOADING'}
        </Badge>
        <ListBlock title="dev / validate / test / build / package / deploy / verify" items={query.data?.timeline ?? []} />
        <ListBlock title="Rollback readiness displayed by YAPPC, executed by Kernel" items={[query.data?.rollbackVisibility ?? {}]} />
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
      <p className="text-sm text-fg-muted">No backend records.</p>
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

interface GuidedReuseCardProps {
  readonly title: string;
  readonly status: string | undefined;
  readonly items: readonly unknown[];
}

const GuidedReuseCard: React.FC<GuidedReuseCardProps> = ({ title, status, items }) => (
  <Card>
    <CardHeader>
      <CardTitle>{title}</CardTitle>
    </CardHeader>
    <CardContent>
      <Badge variant={status === 'READY' ? 'default' : 'secondary'}>{status ?? 'LOADING'}</Badge>
      <ListBlock title="Recommendations" items={items} />
    </CardContent>
  </Card>
);

export const ProductFamilyControlPlanePage: React.FC = () => (
  <main className="min-h-full bg-bg-default p-6">
    <div className="mx-auto max-w-7xl space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-fg">Product Family Control Plane</h1>
          <p className="mt-1 text-sm text-fg-muted">
            Release readiness, Kernel visibility, reusable assets, and guided reuse from YAPPC backend truth.
          </p>
        </div>
        <Button type="button" variant="outline" onClick={() => window.location.reload()}>
          <RefreshCw className="mr-2 h-4 w-4" aria-hidden="true" />
          Refresh
        </Button>
      </div>

      <div className="grid gap-3 md:grid-cols-4">
        <SummaryTile icon={<CheckCircle2 className="h-5 w-5" />} label="PHR first" value="regulated readiness" />
        <SummaryTile icon={<GitBranch className="h-5 w-5" />} label="DMOS next" value="connector gates" />
        <SummaryTile icon={<Boxes className="h-5 w-5" />} label="Assets" value="Data Cloud catalog" />
        <SummaryTile icon={<FileWarning className="h-5 w-5" />} label="Truth" value="doc warnings" />
      </div>

      <Tabs defaultValue="phr" className="space-y-4">
        <TabsList>
          <TabsTrigger value="phr">PHR Release</TabsTrigger>
          <TabsTrigger value="dmos">Digital Marketing</TabsTrigger>
          <TabsTrigger value="assets">Assets</TabsTrigger>
          <TabsTrigger value="truth">Truth & Reuse</TabsTrigger>
          <TabsTrigger value="kernel">Kernel Timeline</TabsTrigger>
        </TabsList>
        <TabsContent value="phr"><ReleasePanel productKey="phr" title="PHR Release Readiness Cockpit" /></TabsContent>
        <TabsContent value="dmos"><ReleasePanel productKey="digital-marketing" title="Digital Marketing Release Readiness Cockpit" /></TabsContent>
        <TabsContent value="assets"><AssetRegistryPanel /></TabsContent>
        <TabsContent value="truth"><TruthAndReusePanel /></TabsContent>
        <TabsContent value="kernel"><KernelTimelinePanel /></TabsContent>
      </Tabs>
    </div>
  </main>
);

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
