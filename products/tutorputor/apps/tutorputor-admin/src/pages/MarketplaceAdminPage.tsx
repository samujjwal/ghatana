import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Card, Badge } from '../components/ui';
import { Button, Input, Spinner } from '@ghatana/design-system';
import { useAuth } from '../hooks/useAuth';

interface MarketplaceListing {
  id: string;
  moduleId: string;
  title: string;
  status: 'DRAFT' | 'ACTIVE' | 'ARCHIVED';
  visibility: 'PUBLIC' | 'PRIVATE';
  priceCents: number;
  createdAt: string;
  publishedAt?: string;
  module?: {
    id: string;
    title: string;
    slug: string;
    domain: string;
  };
}

interface MarketplaceStats {
  totalListings: number;
  activeListings: number;
  draftListings: number;
  archivedListings: number;
  totalRevenueCents: number;
  topListings: Array<{ listingId: string; purchaseCount: number }>;
}

export function MarketplaceAdminPage() {
  const { tenantId } = useAuth();
  const queryClient = useQueryClient();
  const [selectedListing, setSelectedListing] = useState<MarketplaceListing | null>(null);
  const [filters, setFilters] = useState({
    status: 'all',
    visibility: 'all',
    search: '',
  });

  // Fetch marketplace stats
  const { data: stats } = useQuery({
    queryKey: ['marketplace-stats', tenantId],
    queryFn: async () => {
      const res = await fetch('/admin/api/v1/marketplace/stats');
      if (!res.ok) throw new Error('Failed to fetch stats');
      return res.json() as Promise<MarketplaceStats>;
    },
  });

  // Fetch listings
  const { data: listingsData, isLoading } = useQuery({
    queryKey: ['marketplace-listings', tenantId, filters],
    queryFn: async () => {
      const params = new URLSearchParams({ limit: '50' });
      if (filters.status !== 'all') params.set('status', filters.status);
      if (filters.visibility !== 'all') params.set('visibility', filters.visibility);

      const res = await fetch(`/admin/api/v1/marketplace/listings?${params}`);
      if (!res.ok) throw new Error('Failed to fetch listings');
      return res.json() as Promise<{ listings: MarketplaceListing[] }>;
    },
  });

  // Update listing mutation
  const updateListingMutation = useMutation({
    mutationFn: async ({
      id,
      status,
      visibility,
    }: {
      id: string;
      status?: string;
      visibility?: string;
    }) => {
      const res = await fetch(`/admin/api/v1/marketplace/listings/${id}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status, visibility }),
      });
      if (!res.ok) throw new Error('Failed to update listing');
      return res.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['marketplace-listings'] });
      queryClient.invalidateQueries({ queryKey: ['marketplace-stats'] });
      setSelectedListing(null);
    },
  });

  const handleApprove = (listing: MarketplaceListing) => {
    updateListingMutation.mutate({
      id: listing.id,
      status: 'ACTIVE',
      visibility: 'PUBLIC',
    });
  };

  const handleArchive = (listing: MarketplaceListing) => {
    updateListingMutation.mutate({
      id: listing.id,
      status: 'ARCHIVED',
    });
  };

  const getStatusColor = (status: string): 'default' | 'secondary' | 'destructive' => {
    switch (status) {
      case 'ACTIVE':
        return 'default';
      case 'DRAFT':
        return 'secondary';
      case 'ARCHIVED':
        return 'destructive';
      default:
        return 'secondary';
    }
  };

  const getVisibilityColor = (visibility: string): 'default' | 'outline' => {
    return visibility === 'PUBLIC' ? 'default' : 'outline';
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-white">
            Marketplace Administration
          </h1>
          <p className="text-gray-600 dark:text-gray-400">
            Manage marketplace listings, approvals, and revenue
          </p>
        </div>
      </div>

      {/* Stats Cards */}
      {stats && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          <Card className="p-4">
            <div className="text-sm text-gray-600 dark:text-gray-400">Total Listings</div>
            <div className="text-2xl font-bold text-gray-900 dark:text-white mt-1">
              {stats.totalListings}
            </div>
          </Card>
          <Card className="p-4">
            <div className="text-sm text-gray-600 dark:text-gray-400">Active Listings</div>
            <div className="text-2xl font-bold text-green-600 dark:text-green-400 mt-1">
              {stats.activeListings}
            </div>
          </Card>
          <Card className="p-4">
            <div className="text-sm text-gray-600 dark:text-gray-400">Draft Listings</div>
            <div className="text-2xl font-bold text-yellow-600 dark:text-yellow-400 mt-1">
              {stats.draftListings}
            </div>
          </Card>
          <Card className="p-4">
            <div className="text-sm text-gray-600 dark:text-gray-400">Total Revenue</div>
            <div className="text-2xl font-bold text-blue-600 dark:text-blue-400 mt-1">
              ${(stats.totalRevenueCents / 100).toLocaleString()}
            </div>
          </Card>
        </div>
      )}

      {/* Filters */}
      <Card className="p-4">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <Input
            placeholder="Search listings..."
            value={filters.search}
            onChange={(e) => setFilters({ ...filters, search: e.target.value })}
          />
          <select
            className="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-sm"
            value={filters.status}
            onChange={(e) => setFilters({ ...filters, status: e.target.value })}
          >
            <option value="all">All Statuses</option>
            <option value="DRAFT">Draft</option>
            <option value="ACTIVE">Active</option>
            <option value="ARCHIVED">Archived</option>
          </select>
          <select
            className="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-sm"
            value={filters.visibility}
            onChange={(e) => setFilters({ ...filters, visibility: e.target.value })}
          >
            <option value="all">All Visibility</option>
            <option value="PUBLIC">Public</option>
            <option value="PRIVATE">Private</option>
          </select>
        </div>
      </Card>

      {/* Listings Table */}
      {isLoading ? (
        <div className="flex justify-center py-12">
          <Spinner size="lg" />
        </div>
      ) : listingsData?.listings.length === 0 ? (
        <Card className="p-12 text-center">
          <svg
            className="w-12 h-12 mx-auto text-gray-400"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={1.5}
              d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z"
            />
          </svg>
          <h3 className="mt-4 text-lg font-medium text-gray-900 dark:text-white">
            No listings found
          </h3>
          <p className="mt-2 text-gray-500">
            Try adjusting your filters or create a new listing.
          </p>
        </Card>
      ) : (
        <Card className="overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-gray-50 dark:bg-gray-800">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Module
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Status
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Visibility
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Price
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Published
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200 dark:divide-gray-700">
                {listingsData?.listings.map((listing) => (
                  <tr
                    key={listing.id}
                    className="hover:bg-gray-50 dark:hover:bg-gray-800/50"
                  >
                    <td className="px-4 py-3">
                      <div className="text-sm font-medium text-gray-900 dark:text-white">
                        {listing.module?.title || 'Unknown Module'}
                      </div>
                      <div className="text-xs text-gray-500">
                        {listing.module?.domain || 'N/A'}
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <Badge variant={getStatusColor(listing.status)}>
                        {listing.status}
                      </Badge>
                    </td>
                    <td className="px-4 py-3">
                      <Badge variant={getVisibilityColor(listing.visibility)}>
                        {listing.visibility}
                      </Badge>
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-900 dark:text-white">
                      ${(listing.priceCents / 100).toFixed(2)}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-500">
                      {listing.publishedAt
                        ? new Date(listing.publishedAt).toLocaleDateString()
                        : 'Not published'}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex gap-2">
                        {listing.status !== 'ACTIVE' && (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleApprove(listing)}
                            disabled={updateListingMutation.isPending}
                          >
                            Approve
                          </Button>
                        )}
                        {listing.status !== 'ARCHIVED' && (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleArchive(listing)}
                            disabled={updateListingMutation.isPending}
                          >
                            Archive
                          </Button>
                        )}
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => setSelectedListing(listing)}
                        >
                          Details
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {/* Top Listings */}
      {stats && stats.topListings && stats.topListings.length > 0 && (
        <Card className="p-6">
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
            Top Performing Listings
          </h3>
          <div className="space-y-2">
            {stats.topListings.map((item, index) => (
              <div
                key={item.listingId}
                className="flex items-center justify-between p-3 bg-gray-50 dark:bg-gray-800 rounded-lg"
              >
                <div className="flex items-center gap-3">
                  <div className="text-sm font-medium text-gray-500">#{index + 1}</div>
                  <div className="text-sm text-gray-900 dark:text-white font-mono">
                    {item.listingId.substring(0, 8)}...
                  </div>
                </div>
                <Badge variant="default">{item.purchaseCount} purchases</Badge>
              </div>
            ))}
          </div>
        </Card>
      )}

      {/* Detail Modal */}
      {selectedListing && (
        <ListingDetailModal
          listing={selectedListing}
          onClose={() => setSelectedListing(null)}
          onUpdate={(updates) => {
            updateListingMutation.mutate({ id: selectedListing.id, ...updates });
          }}
        />
      )}
    </div>
  );
}

function ListingDetailModal({
  listing,
  onClose,
  onUpdate,
}: {
  listing: MarketplaceListing;
  onClose: () => void;
  onUpdate: (updates: { status?: string; visibility?: string }) => void;
}) {
  const [status, setStatus] = useState(listing.status);
  const [visibility, setVisibility] = useState(listing.visibility);

  const handleSave = () => {
    onUpdate({ status, visibility });
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="fixed inset-0 bg-black/50" onClick={onClose}></div>
      <Card className="relative z-10 w-full max-w-2xl p-6 max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-xl font-bold">Listing Details</h2>
          <Button variant="outline" size="sm" onClick={onClose}>
            Close
          </Button>
        </div>

        <div className="space-y-6">
          {/* Module Info */}
          <div>
            <h3 className="text-sm font-semibold text-gray-900 dark:text-white mb-2">
              Module Information
            </h3>
            <Card className="p-4 bg-gray-50 dark:bg-gray-800">
              <div className="space-y-2 text-sm">
                <div>
                  <span className="text-gray-500">Title:</span>{' '}
                  <span className="text-gray-900 dark:text-white font-medium">
                    {listing.module?.title || 'Unknown'}
                  </span>
                </div>
                <div>
                  <span className="text-gray-500">Domain:</span>{' '}
                  <span className="text-gray-900 dark:text-white">
                    {listing.module?.domain || 'N/A'}
                  </span>
                </div>
                <div>
                  <span className="text-gray-500">Slug:</span>{' '}
                  <span className="font-mono text-gray-900 dark:text-white">
                    {listing.module?.slug || 'N/A'}
                  </span>
                </div>
              </div>
            </Card>
          </div>

          {/* Listing Settings */}
          <div>
            <h3 className="text-sm font-semibold text-gray-900 dark:text-white mb-2">
              Listing Settings
            </h3>
            <Card className="p-4 bg-gray-50 dark:bg-gray-800">
              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                    Status
                  </label>
                  <select
                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-900"
                    value={status}
                    onChange={(e) => setStatus(e.target.value as any)}
                  >
                    <option value="DRAFT">Draft</option>
                    <option value="ACTIVE">Active</option>
                    <option value="ARCHIVED">Archived</option>
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                    Visibility
                  </label>
                  <select
                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-900"
                    value={visibility}
                    onChange={(e) => setVisibility(e.target.value as any)}
                  >
                    <option value="PUBLIC">Public</option>
                    <option value="PRIVATE">Private</option>
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                    Price
                  </label>
                  <div className="text-lg font-semibold text-gray-900 dark:text-white">
                    ${(listing.priceCents / 100).toFixed(2)}
                  </div>
                </div>
              </div>
            </Card>
          </div>

          {/* Metadata */}
          <div>
            <h3 className="text-sm font-semibold text-gray-900 dark:text-white mb-2">
              Metadata
            </h3>
            <Card className="p-4 bg-gray-50 dark:bg-gray-800">
              <div className="grid grid-cols-2 gap-4 text-sm">
                <div>
                  <span className="text-gray-500">Created:</span>{' '}
                  <span className="text-gray-900 dark:text-white">
                    {new Date(listing.createdAt).toLocaleString()}
                  </span>
                </div>
                <div>
                  <span className="text-gray-500">Published:</span>{' '}
                  <span className="text-gray-900 dark:text-white">
                    {listing.publishedAt
                      ? new Date(listing.publishedAt).toLocaleString()
                      : 'Not published'}
                  </span>
                </div>
                <div className="col-span-2">
                  <span className="text-gray-500">Listing ID:</span>{' '}
                  <span className="font-mono text-gray-900 dark:text-white">
                    {listing.id}
                  </span>
                </div>
              </div>
            </Card>
          </div>

          {/* Actions */}
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button onClick={handleSave}>Save Changes</Button>
          </div>
        </div>
      </Card>
    </div>
  );
}
