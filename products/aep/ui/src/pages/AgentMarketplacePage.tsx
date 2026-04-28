/**
 * AgentMarketplacePage — publish and review reusable agent packs.
 *
 * @doc.type page
 * @doc.purpose Marketplace discovery and tenant publishing surface for AEP agents
 * @doc.layer frontend
 */
import React, { useCallback, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAtomValue } from 'jotai';
import { tenantIdAtom } from '@/stores/tenant.store';
import { toast } from 'sonner';
import {
  createMarketplaceReview,
  getMarketplaceAgent,
  installMarketplaceAgent,
  listMarketplaceAgents,
  publishMarketplaceAgent,
  simulateMarketplaceInstall,
  type MarketplaceAgentListing,
  type MarketplaceInstallInput,
  type PublishMarketplaceAgentInput,
} from '@/api/aep.api';
import { Button } from '@ghatana/design-system';
import { TextField } from '@ghatana/design-system';
import { TextArea } from '@ghatana/design-system';
import { SensitiveActionDialog } from '@/components/shared/SensitiveActionDialog';
import { useAuth } from '@/context/AuthContext';
import { MarketplaceInstallDialog } from '@/components/marketplace/MarketplaceInstallDialog';

interface PublishFormState {
  name: string;
  description: string;
  version: string;
  domain: string;
  level: string;
  capabilities: string;
  tags: string;
}

interface ReviewFormState {
  reviewer: string;
  rating: string;
  title: string;
  comment: string;
}

function toList(raw: string): string[] {
  return raw
    .split(',')
    .map((item) => item.trim())
    .filter((item) => item.length > 0);
}

function formatTimestamp(iso?: string) {
  if (!iso) {
    return 'Not published yet';
  }
  return new Date(iso).toLocaleString([], {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function RatingStars({ rating }: { rating: number }) {
  const rounded = Math.round(rating);
  return (
    <div className="flex items-center gap-1" aria-label={`Rating ${rating.toFixed(1)} out of 5`}>
      {Array.from({ length: 5 }).map((_, index) => (
        <span
          key={index}
          className={index < rounded ? 'text-amber-500' : 'text-gray-300 dark:text-gray-600'}
        >
          ★
        </span>
      ))}
    </div>
  );
}

function MarketplaceCard({
  listing,
  selected,
  onSelect,
}: {
  listing: MarketplaceAgentListing;
  selected: boolean;
  onSelect: (id: string) => void;
}) {
  return (
    <Button
      type="button"
      onClick={() => onSelect(listing.id)}
      variant="ghost"
      className={[
        'w-full rounded-xl border p-4 text-left transition-colors',
        selected
          ? 'border-indigo-500 bg-indigo-50 dark:border-indigo-400 dark:bg-indigo-950/40'
          : 'border-gray-200 bg-white hover:border-gray-300 dark:border-gray-800 dark:bg-gray-900',
      ].join(' ')}
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100">{listing.name}</h3>
          <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">{listing.description}</p>
        </div>
        <span className="rounded-full bg-gray-100 px-2 py-1 text-[11px] font-medium uppercase tracking-wide text-gray-600 dark:bg-gray-800 dark:text-gray-300">
          {listing.source}
        </span>
      </div>

      <div className="mt-3 flex items-center gap-3 text-xs text-gray-500 dark:text-gray-400">
        <span>{listing.domain}</span>
        <span>{listing.level}</span>
        <span>v{listing.version}</span>
      </div>

      <div className="mt-3 flex flex-wrap gap-1">
        {listing.capabilities.slice(0, 3).map((capability) => (
          <span
            key={capability}
            className="rounded-full bg-blue-50 px-2 py-1 text-[11px] text-blue-700 dark:bg-blue-950 dark:text-blue-300"
          >
            {capability}
          </span>
        ))}
      </div>

      <div className="mt-4 flex items-center justify-between text-xs text-gray-500 dark:text-gray-400">
        <div className="flex items-center gap-2">
          <RatingStars rating={listing.averageRating} />
          <span>{listing.reviewCount} reviews</span>
        </div>
        <span>{formatTimestamp(listing.updatedAt ?? listing.publishedAt)}</span>
      </div>
    </Button>
  );
}

export function AgentMarketplacePage() {
  const { hasAnyRole, isVerifyingAuth } = useAuth();
  const tenantId = useAtomValue(tenantIdAtom);
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [selectedAgentId, setSelectedAgentId] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'browse' | 'publish'>('browse');
  const [publishForm, setPublishForm] = useState<PublishFormState>({
    name: '',
    description: '',
    version: '1.0.0',
    domain: 'general',
    level: 'worker',
    capabilities: '',
    tags: '',
  });
  const [reviewForm, setReviewForm] = useState<ReviewFormState>({
    reviewer: '',
    rating: '5',
    title: '',
    comment: '',
  });
  const [publishConfirmOpen, setPublishConfirmOpen] = useState(false);
  const [installTarget, setInstallTarget] = useState<MarketplaceAgentListing | null>(null);
  const [installEnvironment, setInstallEnvironment] = useState<'sandbox' | 'staging' | 'production'>('sandbox');
  const canManageMarketplace = hasAnyRole(['admin', 'operator']);

  const { data: listings, isLoading, isError, error } = useQuery({
    queryKey: ['marketplace-agents', tenantId],
    queryFn: () => listMarketplaceAgents(tenantId),
    staleTime: 30_000,
  });

  const filteredListings = useMemo(() => {
    if (!listings) {
      return [];
    }
    if (!search.trim()) {
      return listings;
    }
    const query = search.toLowerCase();
    return listings.filter(
      (listing) =>
        listing.name.toLowerCase().includes(query) ||
        listing.description.toLowerCase().includes(query) ||
        listing.capabilities.some((capability) => capability.toLowerCase().includes(query)) ||
        listing.tags.some((tag) => tag.toLowerCase().includes(query)),
    );
  }, [listings, search]);

  const { data: selectedAgent } = useQuery({
    queryKey: ['marketplace-agent', tenantId, selectedAgentId],
    queryFn: () => getMarketplaceAgent(selectedAgentId ?? '', tenantId),
    enabled: selectedAgentId !== null,
  });

  const publishMutation = useMutation({
    mutationFn: (input: PublishMarketplaceAgentInput) => publishMarketplaceAgent(input, tenantId),
    onSuccess: (createdAgent) => {
      queryClient.invalidateQueries({ queryKey: ['marketplace-agents', tenantId] });
      queryClient.invalidateQueries({ queryKey: ['marketplace-agent', tenantId, createdAgent.id] });
      setSelectedAgentId(createdAgent.id);
      setPublishForm({
        name: '',
        description: '',
        version: '1.0.0',
        domain: 'general',
        level: 'worker',
        capabilities: '',
        tags: '',
      });
    },
  });

  const doPublish = useCallback(
    (input: PublishMarketplaceAgentInput) => {
      publishMutation.mutate(input);
      setPublishConfirmOpen(false);
    },
    [publishMutation],
  );

  const reviewMutation = useMutation({
    mutationFn: (agentId: string) =>
      createMarketplaceReview(
        agentId,
        {
          reviewer: reviewForm.reviewer || undefined,
          rating: Number(reviewForm.rating),
          title: reviewForm.title || undefined,
          comment: reviewForm.comment || undefined,
        },
        tenantId,
      ),
    onSuccess: (_review, agentId) => {
      queryClient.invalidateQueries({ queryKey: ['marketplace-agents', tenantId] });
      queryClient.invalidateQueries({ queryKey: ['marketplace-agent', tenantId, agentId] });
      setReviewForm({ reviewer: '', rating: '5', title: '', comment: '' });
    },
  });

  const installSimulationInput = useMemo<MarketplaceInstallInput | null>(() => {
    if (!installTarget) {
      return null;
    }
    return {
      targetEnvironment: installEnvironment,
      expectedVersion: installTarget.version,
    };
  }, [installEnvironment, installTarget]);

  const { data: installSimulation, isLoading: isSimulatingInstall, error: installSimulationError } = useQuery({
    queryKey: ['marketplace-install-simulation', tenantId, installTarget?.id, installEnvironment, installTarget?.version],
    queryFn: () => simulateMarketplaceInstall(installTarget?.id ?? '', installSimulationInput!, tenantId),
    enabled: installTarget !== null && installSimulationInput !== null,
  });

  const installMutation = useMutation({
    mutationFn: ({ agentId, input }: { agentId: string; input: MarketplaceInstallInput }) =>
      installMarketplaceAgent(agentId, input, tenantId),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ['marketplace-agents', tenantId] });
      queryClient.invalidateQueries({ queryKey: ['marketplace-agent', tenantId, result.agentId] });
      toast.success(
        `Agent "${result.agentName}" installed for ${result.targetEnvironment}. Production execution remains ${result.productionExecutionMode.toLowerCase().replaceAll('_', ' ')}.`,
      );
      setInstallTarget(null);
      setInstallEnvironment('sandbox');
    },
    onError: (mutationError) => {
      toast.error(mutationError instanceof Error ? mutationError.message : 'Failed to install marketplace agent.');
    },
  });

  return (
    <div className="grid h-full grid-cols-1 gap-6 overflow-hidden bg-gray-50 p-6 dark:bg-gray-950 xl:grid-cols-[minmax(0,1.3fr)_minmax(360px,0.9fr)]">
      <section className="flex min-h-0 flex-col overflow-hidden rounded-2xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900">
        <div className="border-b border-gray-200 px-6 py-4 dark:border-gray-800">
          <h1 className="text-lg font-semibold text-gray-900 dark:text-gray-100">Agent Marketplace</h1>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
            Publish reusable agents, review operator feedback, and discover what each tenant can safely reuse.
          </p>
        </div>

        {/* Tab switcher: Browse | Publish */}
        <div className="flex gap-1 border-b border-gray-200 px-6 py-2 dark:border-gray-800">
          {(['browse', 'publish'] as const).map((tab) => (
            <button
              key={tab}
              type="button"
              onClick={() => {
                if (tab === 'publish' && !canManageMarketplace) {
                  return;
                }
                setActiveTab(tab);
              }}
              disabled={tab === 'publish' && !canManageMarketplace}
              className={[
                'rounded-md px-3 py-1.5 text-sm font-medium transition',
                activeTab === tab
                  ? 'bg-indigo-50 text-indigo-700 dark:bg-indigo-950 dark:text-indigo-300'
                  : 'text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800',
                tab === 'publish' && !canManageMarketplace ? 'cursor-not-allowed opacity-50' : '',
              ].join(' ')}
            >
              {tab === 'browse' ? 'Browse' : 'Publish'}
            </button>
          ))}
        </div>

        {activeTab === 'browse' ? (
          <>
            <div className="border-b border-gray-100 px-6 py-4 dark:border-gray-800">
              <TextField
                type="search"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Search marketplace agents"
                className="w-full text-sm"
              />
            </div>

            <div className="min-h-0 flex-1 overflow-y-auto px-6 py-5">
              {isLoading ? <p className="text-sm text-gray-500 dark:text-gray-400">Loading marketplace agents…</p> : null}
              {isError ? (
                <p className="text-sm text-red-600 dark:text-red-400">
                  Failed to load marketplace agents: {error instanceof Error ? error.message : 'Unknown error'}
                </p>
              ) : null}
              {!isLoading && !isError && filteredListings.length === 0 ? (
                <p className="text-sm text-gray-500 dark:text-gray-400">No marketplace agents match the current filters.</p>
              ) : null}
              <div className="grid gap-4 xl:grid-cols-2">
                {filteredListings.map((listing) => (
                  <MarketplaceCard
                    key={listing.id}
                    listing={listing}
                    selected={listing.id === selectedAgentId}
                    onSelect={setSelectedAgentId}
                  />
                ))}
              </div>
            </div>
          </>
        ) : (
          <div className="min-h-0 flex-1 overflow-y-auto px-6 py-5">
            {!isVerifyingAuth && !canManageMarketplace && (
              <div className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200">
                Marketplace publishing is limited to operator or admin roles. Browse remains available in read-only mode.
              </div>
            )}
            <div className="grid gap-4 lg:grid-cols-2">
              <label className="space-y-2 text-sm text-gray-600 dark:text-gray-300">
                <span className="font-medium">Agent name</span>
                <TextField
                  value={publishForm.name}
                  onChange={(event) => setPublishForm((current) => ({ ...current, name: event.target.value }))}
                  className="w-full text-sm"
                />
              </label>
              <label className="space-y-2 text-sm text-gray-600 dark:text-gray-300">
                <span className="font-medium">Version</span>
                <TextField
                  value={publishForm.version}
                  onChange={(event) => setPublishForm((current) => ({ ...current, version: event.target.value }))}
                  className="w-full text-sm"
                />
              </label>
              <label className="space-y-2 text-sm text-gray-600 dark:text-gray-300 lg:col-span-2">
                <span className="font-medium">Description</span>
                <TextArea
                  value={publishForm.description}
                  onChange={(event) => setPublishForm((current) => ({ ...current, description: event.target.value }))}
                  rows={2}
                  className="w-full text-sm"
                />
              </label>
              <label className="space-y-2 text-sm text-gray-600 dark:text-gray-300">
                <span className="font-medium">Capabilities</span>
                <TextField
                  value={publishForm.capabilities}
                  onChange={(event) => setPublishForm((current) => ({ ...current, capabilities: event.target.value }))}
                  placeholder="triage, explain, deploy"
                  className="w-full text-sm"
                />
              </label>
              <label className="space-y-2 text-sm text-gray-600 dark:text-gray-300">
                <span className="font-medium">Tags</span>
                <TextField
                  value={publishForm.tags}
                  onChange={(event) => setPublishForm((current) => ({ ...current, tags: event.target.value }))}
                  placeholder="beta, operator-approved"
                  className="w-full text-sm"
                />
              </label>
              <label className="space-y-2 text-sm text-gray-600 dark:text-gray-300">
                <span className="font-medium">Domain</span>
                <TextField
                  value={publishForm.domain}
                  onChange={(event) => setPublishForm((current) => ({ ...current, domain: event.target.value }))}
                  className="w-full text-sm"
                />
              </label>
              <label className="space-y-2 text-sm text-gray-600 dark:text-gray-300">
                <span className="font-medium">Level</span>
                <select
                  value={publishForm.level}
                  onChange={(event) => setPublishForm((current) => ({ ...current, level: event.target.value }))}
                  className="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-700 dark:bg-gray-950 dark:text-gray-100"
                >
                  <option value="worker">worker</option>
                  <option value="expert">expert</option>
                  <option value="strategic">strategic</option>
                </select>
              </label>

              <div className="lg:col-span-2 flex items-center justify-between gap-3">
                <p className="text-xs text-gray-500 dark:text-gray-400">
                  Marketplace publishing stays tenant-scoped until shared distribution governance is enabled.
                </p>
                <Button
                  type="button"
                  onClick={() => {
                    if (!publishForm.description.trim() || !publishForm.tags.trim()) {
                      toast.warning(
                        'Governance cue: Missing description or tags reduces discoverability and complicates reuse governance. Consider adding them.',
                        { duration: 8000 },
                      );
                    }
                    setPublishConfirmOpen(true);
                  }}
                  disabled={!publishForm.name.trim() || publishMutation.isPending || !canManageMarketplace}
                  variant="primary"
                  className="rounded-lg px-4 py-2 text-sm font-medium"
                  title={canManageMarketplace ? undefined : 'Requires operator or admin role'}
                >
                  {publishMutation.isPending ? 'Publishing…' : 'Publish Agent'}
                </Button>
              </div>
            </div>
          </div>
        )}
      </section>

      <aside className="flex min-h-0 flex-col overflow-hidden rounded-2xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900">
        <div className="border-b border-gray-200 px-6 py-4 dark:border-gray-800">
          <h2 className="text-base font-semibold text-gray-900 dark:text-gray-100">Agent Detail</h2>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
            Ratings, provenance, and tenant review history for the selected listing.
          </p>
        </div>

        {selectedAgent ? (
          <div className="min-h-0 flex-1 overflow-y-auto px-6 py-5">
            <div className="rounded-xl border border-gray-200 bg-gray-50 p-4 dark:border-gray-800 dark:bg-gray-950">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
                    {selectedAgent.listing.name}
                  </h3>
                  <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
                    {selectedAgent.listing.description}
                  </p>
                </div>
                <span className="rounded-full bg-indigo-100 px-3 py-1 text-xs font-medium uppercase tracking-wide text-indigo-700 dark:bg-indigo-950 dark:text-indigo-300">
                  {selectedAgent.listing.source}
                </span>
              </div>

              <div className="mt-4 grid grid-cols-2 gap-3 text-sm text-gray-600 dark:text-gray-300">
                <div>
                  <div className="text-xs uppercase tracking-wide text-gray-400">Domain</div>
                  <div className="mt-1">{selectedAgent.listing.domain}</div>
                </div>
                <div>
                  <div className="text-xs uppercase tracking-wide text-gray-400">Level</div>
                  <div className="mt-1">{selectedAgent.listing.level}</div>
                </div>
                <div>
                  <div className="text-xs uppercase tracking-wide text-gray-400">Owner</div>
                  <div className="mt-1">{selectedAgent.listing.owner}</div>
                </div>
                <div>
                  <div className="text-xs uppercase tracking-wide text-gray-400">Updated</div>
                  <div className="mt-1">{formatTimestamp(selectedAgent.listing.updatedAt ?? selectedAgent.listing.publishedAt)}</div>
                </div>
              </div>

              <div className="mt-4 flex flex-wrap gap-2">
                {selectedAgent.listing.capabilities.map((capability) => (
                  <span
                    key={capability}
                    className="rounded-full bg-blue-50 px-2 py-1 text-xs text-blue-700 dark:bg-blue-950 dark:text-blue-300"
                  >
                    {capability}
                  </span>
                ))}
              </div>
            </div>

            <div className="mt-6 rounded-xl border border-gray-200 p-4 dark:border-gray-800">
              <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100">Reviews</h3>
              <div className="mt-4 space-y-3">
                {selectedAgent.reviews.length === 0 ? (
                  <p className="text-sm text-gray-500 dark:text-gray-400">No operator reviews have been submitted yet.</p>
                ) : (
                  selectedAgent.reviews.map((review) => (
                    <div key={review.id} className="rounded-lg border border-gray-200 p-3 dark:border-gray-800">
                      <div className="flex items-center justify-between gap-3">
                        <div>
                          <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{review.title}</p>
                          <p className="text-xs text-gray-500 dark:text-gray-400">{review.reviewer}</p>
                        </div>
                        <div className="flex items-center gap-2">
                          <RatingStars rating={review.rating} />
                          <span className="text-xs text-gray-500 dark:text-gray-400">
                            {formatTimestamp(review.createdAt)}
                          </span>
                        </div>
                      </div>
                      <p className="mt-2 text-sm text-gray-600 dark:text-gray-300">{review.comment}</p>
                    </div>
                  ))
                )}
              </div>
            </div>

            {canManageMarketplace ? (
              <div className="mt-4 flex gap-2">
                <Button
                  type="button"
                  variant="primary"
                  className="flex-1 rounded-lg px-4 py-2 text-sm font-medium"
                  onClick={() => {
                    if (selectedAgent) {
                      setInstallEnvironment('sandbox');
                      setInstallTarget(selectedAgent.listing);
                    }
                  }}
                >
                  Install to tenant
                </Button>
              </div>
            ) : !isVerifyingAuth ? (
              <div className="mt-4 rounded-lg border border-gray-200 bg-gray-50 px-4 py-3 text-sm text-gray-600 dark:border-gray-800 dark:bg-gray-950 dark:text-gray-300">
                Installing marketplace agents requires an operator or admin role.
              </div>
            ) : null}

            <div className="mt-6 rounded-xl border border-gray-200 p-4 dark:border-gray-800">
              <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100">Add Review</h3>
              <div className="mt-4 grid gap-3">
                <TextField
                  value={reviewForm.reviewer}
                  onChange={(event) => setReviewForm((current) => ({ ...current, reviewer: event.target.value }))}
                  placeholder="Reviewer"
                  className="rounded-lg text-sm"
                />
                <div className="grid grid-cols-[minmax(0,1fr)_120px] gap-3">
                  <TextField
                    value={reviewForm.title}
                    onChange={(event) => setReviewForm((current) => ({ ...current, title: event.target.value }))}
                    placeholder="Review title"
                    className="rounded-lg text-sm"
                  />
                  <select
                    value={reviewForm.rating}
                    onChange={(event) => setReviewForm((current) => ({ ...current, rating: event.target.value }))}
                    className="rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-700 dark:bg-gray-950 dark:text-gray-100"
                  >
                    <option value="5">5 stars</option>
                    <option value="4">4 stars</option>
                    <option value="3">3 stars</option>
                    <option value="2">2 stars</option>
                    <option value="1">1 star</option>
                  </select>
                </div>
                <TextArea
                  value={reviewForm.comment}
                  onChange={(event) => setReviewForm((current) => ({ ...current, comment: event.target.value }))}
                  placeholder="What worked well? What still needs review?"
                  rows={4}
                  className="rounded-lg text-sm"
                />
                <Button
                  type="button"
                  disabled={!selectedAgentId || reviewMutation.isPending}
                  onClick={() => {
                    if (!selectedAgentId) return;
                    // Self-review guard: prevent submitting a review for an agent published by the current tenant
                    const isSelfReview = selectedAgent.listing.owner === tenantId;
                    if (isSelfReview) {
                      toast.error('You cannot review an agent published by your own tenant.', { duration: 6000 });
                      return;
                    }
                    reviewMutation.mutate(selectedAgentId);
                  }}
                  variant="secondary"
                  className="rounded-lg px-4 py-2 text-sm font-medium bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-950"
                >
                  {reviewMutation.isPending ? 'Submitting…' : 'Submit Review'}
                </Button>
              </div>
            </div>
          </div>
        ) : (
          <div className="flex flex-1 items-center justify-center px-6 text-center text-sm text-gray-500 dark:text-gray-400">
            Select a marketplace listing to inspect provenance, reviews, and publication details.
          </div>
        )}
      </aside>

      {/* Publish confirmation dialog */}
      <SensitiveActionDialog
        open={publishConfirmOpen}
        title="Publish agent to marketplace"
        description={`This will publish "${publishForm.name}" as a marketplace listing visible to other tenants. Ensure the description, capabilities, and tags are accurate before publishing.`}
        confirmKeyword="PUBLISH"
        impactItems={[
          { label: 'Agent name', value: publishForm.name, severity: 'high' },
          { label: 'Version', value: publishForm.version, severity: 'low' },
          { label: 'Domain', value: publishForm.domain, severity: 'low' },
          { label: 'Level', value: publishForm.level, severity: 'low' },
          { label: 'Tenant', value: tenantId, severity: 'medium' },
        ]}
        auditMessage={`Agent ${publishForm.name} published to marketplace by tenant ${tenantId}`}
        reasonRequired
        onConfirm={(reason) => {
          doPublish({
            name: publishForm.name,
            description: publishForm.description || undefined,
            version: publishForm.version || undefined,
            domain: publishForm.domain || undefined,
            level: publishForm.level || undefined,
            capabilities: toList(publishForm.capabilities),
            tags: toList(publishForm.tags),
          });
        }}
        onCancel={() => setPublishConfirmOpen(false)}
      />

      {installTarget && (
        <MarketplaceInstallDialog
          listing={installTarget}
          tenantId={tenantId}
          environment={installEnvironment}
          onEnvironmentChange={setInstallEnvironment}
          simulation={installSimulation}
          isSimulating={isSimulatingInstall}
          isInstalling={installMutation.isPending}
          errorMessage={installSimulationError instanceof Error ? installSimulationError.message : null}
          onConfirm={(_reason) => {
            installMutation.mutate({
              agentId: installTarget.id,
              input: {
                targetEnvironment: installEnvironment,
                expectedVersion: installTarget.version,
              },
            });
          }}
          onCancel={() => {
            setInstallTarget(null);
            setInstallEnvironment('sandbox');
          }}
        />
      )}
    </div>
  );
}
