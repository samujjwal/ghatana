import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Card, Badge } from '../components/ui';
import { Button, Input, Spinner } from '@ghatana/design-system';
import { useAuth } from '../hooks/useAuth';

interface AuditLogEntry {
  id: string;
  timestamp: string;
  actorId: string;
  actorEmail: string;
  actorRole: string;
  action: string;
  resourceType: string;
  resourceId: string;
  details: Record<string, unknown>;
  ipAddress: string;
  userAgent: string;
  outcome: 'success' | 'failure';
  errorMessage?: string;
}

interface AuditFilters {
  search: string;
  action: string;
  resourceType: string;
  outcome: string;
  dateFrom: string;
  dateTo: string;
}

const ACTION_TYPES = [
  'all',
  'user.login',
  'user.logout',
  'user.create',
  'user.update',
  'user.delete',
  'role.assign',
  'role.revoke',
  'sso.configure',
  'sso.login',
  'module.create',
  'module.publish',
  'classroom.create',
  'settings.update',
  'export.request',
  'data.delete',
];

const RESOURCE_TYPES = [
  'all',
  'user',
  'role',
  'sso_provider',
  'module',
  'classroom',
  'organization',
  'settings',
  'export',
];

export function AuditPage() {
  const { tenantId } = useAuth();
  const [page, setPage] = useState(1);
  const [selectedEntry, setSelectedEntry] = useState<AuditLogEntry | null>(null);
  const [filters, setFilters] = useState<AuditFilters>({
    search: '',
    action: 'all',
    resourceType: 'all',
    outcome: 'all',
    dateFrom: '',
    dateTo: '',
  });

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['audit-logs', tenantId, page, filters],
    queryFn: async () => {
      const params = new URLSearchParams({
        limit: '50',
      });

      if (filters.action !== 'all') params.set('action', filters.action);
      if (filters.resourceType !== 'all') params.set('resourceType', filters.resourceType);
      if (filters.dateFrom) params.set('startDate', new Date(filters.dateFrom).toISOString());
      if (filters.dateTo) params.set('endDate', new Date(filters.dateTo).toISOString());

      const res = await fetch(`/admin/api/v1/audit/events?${params}`);
      if (!res.ok) throw new Error('Failed to fetch audit logs');
      const result = await res.json();
      
      // Transform to match expected format
      return {
        logs: result.items.map((item: any) => ({
          id: item.id,
          timestamp: item.timestamp,
          actorId: item.actorId,
          actorEmail: item.actorEmail || 'Unknown',
          actorRole: 'admin', // Default role
          action: item.action,
          resourceType: item.resourceType,
          resourceId: item.resourceId,
          details: item.metadata || {},
          ipAddress: item.ipAddress || 'N/A',
          userAgent: item.userAgent || 'N/A',
          outcome: 'success' as const,
        })),
        total: result.totalCount,
        pages: Math.ceil(result.totalCount / 50),
      };
    },
  });

  const handleExport = async () => {
    const params = new URLSearchParams();
    if (filters.search) params.set('search', filters.search);
    if (filters.action !== 'all') params.set('action', filters.action);
    if (filters.resourceType !== 'all') params.set('resourceType', filters.resourceType);
    if (filters.outcome !== 'all') params.set('outcome', filters.outcome);
    if (filters.dateFrom) params.set('from', filters.dateFrom);
    if (filters.dateTo) params.set('to', filters.dateTo);

    window.open(`/admin/api/v1/audit/export?${params}`, '_blank');
  };

  const clearFilters = () => {
    setFilters({
      search: '',
      action: 'all',
      resourceType: 'all',
      outcome: 'all',
      dateFrom: '',
      dateTo: '',
    });
    setPage(1);
  };

  const getActionColor = (action: string): 'default' | 'secondary' | 'destructive' | 'outline' => {
    if (action.includes('delete') || action.includes('revoke')) return 'destructive';
    if (action.includes('create') || action.includes('assign')) return 'default';
    if (action.includes('login') || action.includes('logout')) return 'secondary';
    return 'outline';
  };

  const getOutcomeColor = (outcome: string): 'default' | 'destructive' => {
    return outcome === 'success' ? 'default' : 'destructive';
  };

  const formatAction = (action: string): string => {
    return action.replace('.', ' → ').replace(/_/g, ' ');
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-white">
            Audit Logs
          </h1>
          <p className="text-gray-600 dark:text-gray-400">
            Track all administrative actions and security events
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => refetch()}>
            Refresh
          </Button>
          <Button variant="outline" onClick={handleExport}>
            Export CSV
          </Button>
        </div>
      </div>

      {/* Filters */}
      <Card className="p-4">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-6 gap-4">
          <div className="lg:col-span-2">
            <Input
              placeholder="Search by user, action, or resource..."
              value={filters.search}
              onChange={(e) => setFilters({ ...filters, search: e.target.value })}
            />
          </div>

          <select
            className="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-sm"
            value={filters.action}
            onChange={(e) => setFilters({ ...filters, action: e.target.value })}
          >
            <option value="all">All Actions</option>
            {ACTION_TYPES.filter(a => a !== 'all').map((action) => (
              <option key={action} value={action}>
                {formatAction(action)}
              </option>
            ))}
          </select>

          <select
            className="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-sm"
            value={filters.resourceType}
            onChange={(e) => setFilters({ ...filters, resourceType: e.target.value })}
          >
            <option value="all">All Resources</option>
            {RESOURCE_TYPES.filter(r => r !== 'all').map((type) => (
              <option key={type} value={type}>
                {type.replace(/_/g, ' ')}
              </option>
            ))}
          </select>

          <select
            className="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-sm"
            value={filters.outcome}
            onChange={(e) => setFilters({ ...filters, outcome: e.target.value })}
          >
            <option value="all">All Outcomes</option>
            <option value="success">Success</option>
            <option value="failure">Failure</option>
          </select>

          <Button variant="outline" size="sm" onClick={clearFilters}>
            Clear Filters
          </Button>
        </div>

        {/* Date Range */}
        <div className="flex gap-4 mt-4">
          <div className="flex items-center gap-2">
            <label className="text-sm text-gray-600 dark:text-gray-400">From:</label>
            <Input
              type="date"
              value={filters.dateFrom}
              onChange={(e) => setFilters({ ...filters, dateFrom: e.target.value })}
              className="w-auto"
            />
          </div>
          <div className="flex items-center gap-2">
            <label className="text-sm text-gray-600 dark:text-gray-400">To:</label>
            <Input
              type="date"
              value={filters.dateTo}
              onChange={(e) => setFilters({ ...filters, dateTo: e.target.value })}
              className="w-auto"
            />
          </div>
        </div>
      </Card>

      {/* Results Summary */}
      {data && (
        <div className="text-sm text-gray-600 dark:text-gray-400">
          Showing {data.logs.length} of {data.total} entries
        </div>
      )}

      {/* Audit Log Table */}
      {isLoading ? (
        <div className="flex justify-center py-12">
          <Spinner size="lg" />
        </div>
      ) : data?.logs.length === 0 ? (
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
              d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
            />
          </svg>
          <h3 className="mt-4 text-lg font-medium text-gray-900 dark:text-white">
            No audit logs found
          </h3>
          <p className="mt-2 text-gray-500">
            Try adjusting your filters or date range.
          </p>
        </Card>
      ) : (
        <Card className="overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-gray-50 dark:bg-gray-800">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Timestamp
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    User
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Action
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Resource
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Outcome
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    IP Address
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200 dark:divide-gray-700">
                {data?.logs.map((entry) => (
                  <tr
                    key={entry.id}
                    className="hover:bg-gray-50 dark:hover:bg-gray-800/50"
                  >
                    <td className="px-4 py-3 text-sm text-gray-900 dark:text-white whitespace-nowrap">
                      {new Date(entry.timestamp).toLocaleString()}
                    </td>
                    <td className="px-4 py-3">
                      <div className="text-sm text-gray-900 dark:text-white">
                        {entry.actorEmail}
                      </div>
                      <div className="text-xs text-gray-500">{entry.actorRole}</div>
                    </td>
                    <td className="px-4 py-3">
                      <Badge variant={getActionColor(entry.action)}>
                        {formatAction(entry.action)}
                      </Badge>
                    </td>
                    <td className="px-4 py-3">
                      <div className="text-sm text-gray-900 dark:text-white">
                        {entry.resourceType.replace(/_/g, ' ')}
                      </div>
                      <div className="text-xs text-gray-500 font-mono">
                        {entry.resourceId.substring(0, 8)}...
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <Badge variant={getOutcomeColor(entry.outcome)}>
                        {entry.outcome}
                      </Badge>
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-500 font-mono">
                      {entry.ipAddress}
                    </td>
                    <td className="px-4 py-3">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => setSelectedEntry(entry)}
                      >
                        Details
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {/* Pagination */}
      {data && data.pages > 1 && (
        <div className="flex justify-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => setPage(page - 1)}
            disabled={page === 1}
          >
            Previous
          </Button>
          <span className="px-4 py-2 text-sm text-gray-600 dark:text-gray-400">
            Page {page} of {data.pages}
          </span>
          <Button
            variant="outline"
            size="sm"
            onClick={() => setPage(page + 1)}
            disabled={page === data.pages}
          >
            Next
          </Button>
        </div>
      )}

      {/* Detail Modal */}
      {selectedEntry && (
        <AuditDetailModal
          entry={selectedEntry}
          onClose={() => setSelectedEntry(null)}
        />
      )}
    </div>
  );
}

function AuditDetailModal({
  entry,
  onClose,
}: {
  entry: AuditLogEntry;
  onClose: () => void;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="fixed inset-0 bg-black/50" onClick={onClose}></div>
      <Card className="relative z-10 w-full max-w-2xl p-6 max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-xl font-bold">Audit Log Details</h2>
          <Button variant="outline" size="sm" onClick={onClose}>
            Close
          </Button>
        </div>

        <div className="space-y-6">
          {/* Basic Info */}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="text-sm font-medium text-gray-500">Timestamp</label>
              <p className="text-gray-900 dark:text-white">
                {new Date(entry.timestamp).toLocaleString()}
              </p>
            </div>
            <div>
              <label className="text-sm font-medium text-gray-500">Outcome</label>
              <p>
                <Badge
                  variant={entry.outcome === 'success' ? 'default' : 'destructive'}
                >
                  {entry.outcome}
                </Badge>
              </p>
            </div>
          </div>

          {/* Actor Info */}
          <div>
            <h3 className="text-sm font-semibold text-gray-900 dark:text-white mb-2">
              Actor
            </h3>
            <Card className="p-3 bg-gray-50 dark:bg-gray-800">
              <div className="grid grid-cols-2 gap-4 text-sm">
                <div>
                  <span className="text-gray-500">Email:</span>{' '}
                  <span className="text-gray-900 dark:text-white">{entry.actorEmail}</span>
                </div>
                <div>
                  <span className="text-gray-500">Role:</span>{' '}
                  <span className="text-gray-900 dark:text-white">{entry.actorRole}</span>
                </div>
                <div>
                  <span className="text-gray-500">ID:</span>{' '}
                  <span className="font-mono text-gray-900 dark:text-white">
                    {entry.actorId}
                  </span>
                </div>
              </div>
            </Card>
          </div>

          {/* Action Info */}
          <div>
            <h3 className="text-sm font-semibold text-gray-900 dark:text-white mb-2">
              Action
            </h3>
            <Card className="p-3 bg-gray-50 dark:bg-gray-800">
              <div className="grid grid-cols-2 gap-4 text-sm">
                <div>
                  <span className="text-gray-500">Action:</span>{' '}
                  <Badge variant="outline">{entry.action}</Badge>
                </div>
                <div>
                  <span className="text-gray-500">Resource Type:</span>{' '}
                  <span className="text-gray-900 dark:text-white">
                    {entry.resourceType}
                  </span>
                </div>
                <div className="col-span-2">
                  <span className="text-gray-500">Resource ID:</span>{' '}
                  <span className="font-mono text-gray-900 dark:text-white">
                    {entry.resourceId}
                  </span>
                </div>
              </div>
            </Card>
          </div>

          {/* Request Info */}
          <div>
            <h3 className="text-sm font-semibold text-gray-900 dark:text-white mb-2">
              Request Details
            </h3>
            <Card className="p-3 bg-gray-50 dark:bg-gray-800">
              <div className="space-y-2 text-sm">
                <div>
                  <span className="text-gray-500">IP Address:</span>{' '}
                  <span className="font-mono text-gray-900 dark:text-white">
                    {entry.ipAddress}
                  </span>
                </div>
                <div>
                  <span className="text-gray-500">User Agent:</span>{' '}
                  <span className="text-gray-900 dark:text-white break-all">
                    {entry.userAgent}
                  </span>
                </div>
              </div>
            </Card>
          </div>

          {/* Error Message (if failure) */}
          {entry.outcome === 'failure' && entry.errorMessage && (
            <div>
              <h3 className="text-sm font-semibold text-red-600 mb-2">
                Error Message
              </h3>
              <Card className="p-3 bg-red-50 dark:bg-red-900/20 border-red-200 dark:border-red-800">
                <p className="text-sm text-red-700 dark:text-red-300">
                  {entry.errorMessage}
                </p>
              </Card>
            </div>
          )}

          {/* Details (JSON) */}
          {Object.keys(entry.details).length > 0 && (
            <div>
              <h3 className="text-sm font-semibold text-gray-900 dark:text-white mb-2">
                Additional Details
              </h3>
              <Card className="p-3 bg-gray-50 dark:bg-gray-800">
                <pre className="text-xs overflow-x-auto text-gray-700 dark:text-gray-300">
                  {JSON.stringify(entry.details, null, 2)}
                </pre>
              </Card>
            </div>
          )}
        </div>
      </Card>
    </div>
  );
}
