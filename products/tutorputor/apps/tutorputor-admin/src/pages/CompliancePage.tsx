import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Card, Badge } from '../components/ui';
import { Button, Input, Spinner } from '@ghatana/ui';
import { useAuth } from '../hooks/useAuth';

interface DataExportRequest {
  id: string;
  userId: string;
  userEmail: string;
  requestType: 'export' | 'deletion';
  status: 'pending' | 'processing' | 'completed' | 'failed';
  requestedAt: string;
  completedAt?: string;
  downloadUrl?: string;
  expiresAt?: string;
}

interface ComplianceStats {
  pendingExports: number;
  pendingDeletions: number;
  completedThisMonth: number;
  averageProcessingTime: number;
}

export function CompliancePage() {
  const { tenantId } = useAuth();
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState<'requests' | 'policies'>('requests');
  const [filter, setFilter] = useState<'all' | 'export' | 'deletion'>('all');

  const { data: stats } = useQuery({
    queryKey: ['compliance-stats', tenantId],
    queryFn: async () => {
      // Use existing ComplianceService routes (reuse-first principle)
      const res = await fetch('/admin/compliance/stats');
      if (!res.ok) throw new Error('Failed to fetch stats');
      return res.json() as Promise<ComplianceStats>;
    },
  });

  const { data: requests, isLoading } = useQuery({
    queryKey: ['compliance-requests', tenantId, filter],
    queryFn: async () => {
      // Use existing ComplianceService routes
      const params = filter !== 'all' ? `?type=${filter}` : '';
      const res = await fetch(`/admin/compliance/requests${params}`);
      if (!res.ok) throw new Error('Failed to fetch requests');
      return res.json() as Promise<{ requests: DataExportRequest[] }>;
    },
  });

  const processMutation = useMutation({
    mutationFn: async (requestId: string) => {
      // Use existing ComplianceService routes
      const res = await fetch(`/admin/compliance/requests/${requestId}/process`, {
        method: 'POST',
      });
      if (!res.ok) throw new Error('Failed to process request');
      return res.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['compliance-requests'] });
      queryClient.invalidateQueries({ queryKey: ['compliance-stats'] });
    },
  });

  const getStatusColor = (status: string): 'default' | 'secondary' | 'destructive' | 'outline' => {
    switch (status) {
      case 'completed':
        return 'default';
      case 'processing':
        return 'secondary';
      case 'failed':
        return 'destructive';
      default:
        return 'outline';
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-gray-900 dark:text-white">
          Compliance & Privacy
        </h1>
        <p className="text-gray-600 dark:text-gray-400">
          Manage data export requests and privacy compliance
        </p>
      </div>

      {/* Stats */}
      {stats && (
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          <Card className="p-4">
            <p className="text-sm text-gray-500">Pending Exports</p>
            <p className="text-2xl font-bold text-gray-900 dark:text-white">
              {stats.pendingExports}
            </p>
          </Card>
          <Card className="p-4">
            <p className="text-sm text-gray-500">Pending Deletions</p>
            <p className="text-2xl font-bold text-gray-900 dark:text-white">
              {stats.pendingDeletions}
            </p>
          </Card>
          <Card className="p-4">
            <p className="text-sm text-gray-500">Completed This Month</p>
            <p className="text-2xl font-bold text-gray-900 dark:text-white">
              {stats.completedThisMonth}
            </p>
          </Card>
          <Card className="p-4">
            <p className="text-sm text-gray-500">Avg Processing Time</p>
            <p className="text-2xl font-bold text-gray-900 dark:text-white">
              {stats.averageProcessingTime}h
            </p>
          </Card>
        </div>
      )}

      {/* Tabs */}
      <div className="border-b border-gray-200 dark:border-gray-700">
        <nav className="flex gap-4">
          <button
            onClick={() => setActiveTab('requests')}
            className={`px-4 py-3 border-b-2 transition-colors ${activeTab === 'requests'
                ? 'border-blue-500 text-blue-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}
          >
            Data Requests
          </button>
          <button
            onClick={() => setActiveTab('policies')}
            className={`px-4 py-3 border-b-2 transition-colors ${activeTab === 'policies'
                ? 'border-blue-500 text-blue-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}
          >
            Policies & Consent
          </button>
        </nav>
      </div>

      {activeTab === 'requests' && (
        <>
          {/* Filter */}
          <div className="flex gap-2">
            {(['all', 'export', 'deletion'] as const).map((f) => (
              <Button
                key={f}
                variant={filter === f ? 'default' : 'outline'}
                size="sm"
                onClick={() => setFilter(f)}
              >
                {f === 'all' ? 'All Requests' : f === 'export' ? 'Exports' : 'Deletions'}
              </Button>
            ))}
          </div>

          {/* Requests List */}
          {isLoading ? (
            <div className="flex justify-center py-12">
              <Spinner size="lg" />
            </div>
          ) : requests?.requests.length === 0 ? (
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
                  d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"
                />
              </svg>
              <h3 className="mt-4 text-lg font-medium text-gray-900 dark:text-white">
                No pending requests
              </h3>
              <p className="mt-2 text-gray-500">
                All data requests have been processed.
              </p>
            </Card>
          ) : (
            <Card className="overflow-hidden">
              <table className="w-full">
                <thead className="bg-gray-50 dark:bg-gray-800">
                  <tr>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                      User
                    </th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                      Type
                    </th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                      Status
                    </th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                      Requested
                    </th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                      Actions
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200 dark:divide-gray-700">
                  {requests?.requests.map((request) => (
                    <tr key={request.id}>
                      <td className="px-4 py-3 text-gray-900 dark:text-white">
                        {request.userEmail}
                      </td>
                      <td className="px-4 py-3">
                        <Badge
                          variant={
                            request.requestType === 'deletion'
                              ? 'destructive'
                              : 'secondary'
                          }
                        >
                          {request.requestType}
                        </Badge>
                      </td>
                      <td className="px-4 py-3">
                        <Badge variant={getStatusColor(request.status)}>
                          {request.status}
                        </Badge>
                      </td>
                      <td className="px-4 py-3 text-gray-500 text-sm">
                        {new Date(request.requestedAt).toLocaleDateString()}
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex gap-2">
                          {request.status === 'pending' && (
                            <Button
                              size="sm"
                              onClick={() => processMutation.mutate(request.id)}
                              disabled={processMutation.isPending}
                            >
                              Process
                            </Button>
                          )}
                          {request.status === 'completed' && request.downloadUrl && (
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() => window.open(request.downloadUrl, '_blank')}
                            >
                              Download
                            </Button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Card>
          )}
        </>
      )}

      {activeTab === 'policies' && (
        <PoliciesTab />
      )}
    </div>
  );
}

function PoliciesTab() {
  const [dataRetention, setDataRetention] = useState({
    userDataDays: 365,
    analyticsDataDays: 90,
    auditLogDays: 730,
  });

  const [consents, setConsents] = useState({
    requireExplicitConsent: true,
    allowDataSharing: false,
    anonymizeAfterDeletion: true,
  });

  return (
    <div className="space-y-6">
      {/* Data Retention Policy */}
      <Card className="p-6">
        <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
          Data Retention Policy
        </h3>
        <div className="space-y-4">
          <div className="grid grid-cols-3 gap-4">
            <div>
              <label className="block text-sm font-medium mb-2">
                User Data Retention
              </label>
              <div className="flex items-center gap-2">
                <Input
                  type="number"
                  value={dataRetention.userDataDays}
                  onChange={(e) =>
                    setDataRetention({
                      ...dataRetention,
                      userDataDays: parseInt(e.target.value),
                    })
                  }
                  className="w-24"
                />
                <span className="text-gray-500">days</span>
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium mb-2">
                Analytics Data Retention
              </label>
              <div className="flex items-center gap-2">
                <Input
                  type="number"
                  value={dataRetention.analyticsDataDays}
                  onChange={(e) =>
                    setDataRetention({
                      ...dataRetention,
                      analyticsDataDays: parseInt(e.target.value),
                    })
                  }
                  className="w-24"
                />
                <span className="text-gray-500">days</span>
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium mb-2">
                Audit Log Retention
              </label>
              <div className="flex items-center gap-2">
                <Input
                  type="number"
                  value={dataRetention.auditLogDays}
                  onChange={(e) =>
                    setDataRetention({
                      ...dataRetention,
                      auditLogDays: parseInt(e.target.value),
                    })
                  }
                  className="w-24"
                />
                <span className="text-gray-500">days</span>
              </div>
            </div>
          </div>
          <Button>Save Retention Policy</Button>
        </div>
      </Card>

      {/* Consent Settings */}
      <Card className="p-6">
        <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
          Consent Settings
        </h3>
        <div className="space-y-4">
          <label className="flex items-center gap-3">
            <input
              type="checkbox"
              checked={consents.requireExplicitConsent}
              onChange={(e) =>
                setConsents({
                  ...consents,
                  requireExplicitConsent: e.target.checked,
                })
              }
              className="w-4 h-4 rounded"
            />
            <span>Require explicit consent for data collection</span>
          </label>
          <label className="flex items-center gap-3">
            <input
              type="checkbox"
              checked={consents.allowDataSharing}
              onChange={(e) =>
                setConsents({
                  ...consents,
                  allowDataSharing: e.target.checked,
                })
              }
              className="w-4 h-4 rounded"
            />
            <span>Allow anonymous data sharing for analytics</span>
          </label>
          <label className="flex items-center gap-3">
            <input
              type="checkbox"
              checked={consents.anonymizeAfterDeletion}
              onChange={(e) =>
                setConsents({
                  ...consents,
                  anonymizeAfterDeletion: e.target.checked,
                })
              }
              className="w-4 h-4 rounded"
            />
            <span>Anonymize user contributions after account deletion</span>
          </label>
          <Button>Save Consent Settings</Button>
        </div>
      </Card>

      {/* Compliance Reports */}
      <Card className="p-6">
        <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
          Compliance Reports
        </h3>
        <div className="grid grid-cols-2 gap-4">
          <Button variant="outline">
            Download GDPR Compliance Report
          </Button>
          <Button variant="outline">
            Download COPPA Compliance Report
          </Button>
          <Button variant="outline">
            Download FERPA Compliance Report
          </Button>
          <Button variant="outline">
            Download Data Processing Agreement
          </Button>
        </div>
      </Card>
    </div>
  );
}
