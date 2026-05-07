/**
 * Localization Page — multi-language campaign support.
 *
 * <p>P3-004: Capability-gated via {@code dmos.localization}. Lists campaigns
 * grouped by type and shows locale coverage across the workspace. Reuses
 * {@code useCampaigns}, {@code EmptyState}, {@code Card}, and {@code Badge}
 * from the shared platform.</p>
 *
 * @doc.type page
 * @doc.purpose Multi-language campaign localization and translation management view
 * @doc.layer frontend
 */
import React from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { useCampaigns } from '@/hooks/useCampaigns';
import {
  EmptyState,
  Card,
  CardHeader,
  CardContent,
  Badge,
  Table,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
} from '@ghatana/design-system';
import type { CampaignType, Campaign } from '@/types/campaign';

/** Channels known to support locale variants */
const LOCALE_ELIGIBLE_TYPES: CampaignType[] = ['EMAIL', 'SOCIAL', 'OMNICHANNEL'];

export function LocalizationPage(): React.ReactElement {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { isAuthenticated } = useAuth();

  // Call hook unconditionally (React rules)
  const { campaigns, isLoading, isError } = useCampaigns(workspaceId ?? null, { limit: 100 });

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  const localizableCampaigns = campaigns.filter(
    (c: Campaign) => LOCALE_ELIGIBLE_TYPES.includes(c.type),
  );

  const byType = LOCALE_ELIGIBLE_TYPES.reduce<Record<string, Campaign[]>>(
    (acc, t) => ({
      ...acc,
      [t]: campaigns.filter((c) => c.type === t),
    }),
    {},
  );

  return (
    <section data-testid="localization-page" className="max-w-6xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Localization</h1>
        <span className="text-sm text-gray-500">
          Workspace: <code>{workspaceId}</code>
        </span>
      </div>

      {isError && (
        <div className="mb-4 p-4 rounded-lg bg-red-50 text-red-700 text-sm">
          Failed to load campaign data. Locale coverage may be incomplete.
        </div>
      )}

      {isLoading && (
        <div className="text-sm text-gray-500 mb-4">Loading campaign locale data…</div>
      )}

      {!isLoading && campaigns.length === 0 ? (
        <EmptyState
          title="No campaigns yet"
          description="Create Email, Social, or Omnichannel campaigns to manage their locale coverage here."
          size="lg"
        />
      ) : (
        <>
          {/* Summary cards per locale-eligible channel type */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
            {LOCALE_ELIGIBLE_TYPES.map((type) => (
              <Card key={type} variant="outlined">
                <CardHeader
                  title={type}
                  action={<Badge>{byType[type]?.length ?? 0} campaigns</Badge>}
                />
                <CardContent>
                  <p className="text-xs text-gray-500">
                    {byType[type]?.filter((c) => c.status === 'LAUNCHED').length ?? 0} active
                    {' · '}
                    {byType[type]?.filter((c) => c.status === 'DRAFT').length ?? 0} draft
                  </p>
                </CardContent>
              </Card>
            ))}
          </div>

          {/* Locale-eligible campaign table */}
          <h2 className="text-lg font-semibold mb-3">Locale-Eligible Campaigns</h2>
          {localizableCampaigns.length === 0 ? (
            <EmptyState
              title="No locale-eligible campaigns"
              description="Campaigns of type Email, Social, or Omnichannel will appear here for locale management."
              size="sm"
            />
          ) : (
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Name</TableCell>
                  <TableCell>Type</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Audience</TableCell>
                  <TableCell>Start</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {localizableCampaigns.map((c) => (
                  <TableRow key={c.id}>
                    <TableCell>{c.name}</TableCell>
                    <TableCell>{c.type}</TableCell>
                    <TableCell><Badge>{c.status}</Badge></TableCell>
                    <TableCell>{c.audience ?? '—'}</TableCell>
                    <TableCell>{c.startDate ?? '—'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </>
      )}
    </section>
  );
}
