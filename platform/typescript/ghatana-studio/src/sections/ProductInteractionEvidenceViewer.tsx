/**
 * Product Interaction Evidence Viewer
 *
 * Displays product interaction evidence records with filtering and timeline view.
 * Integrates with ProductInteractionEvidenceReader for evidence readback.
 *
 * @doc.type component
 * @doc.purpose View and filter product interaction evidence records
 * @doc.layer platform
 */

import type { ReactElement } from 'react';
import { useState, useEffect } from 'react';
import { Button, Typography, Card, CardContent, CardHeader, Badge, Select } from '@ghatana/design-system';
import { studioLogger } from '../logging/studioLogger';

interface ProductInteractionEvidenceRecord {
  readonly evidenceId: string;
  readonly schemaVersion: string;
  readonly manifestType: string;
  readonly contractId: string;
  readonly contractVersion: string;
  readonly providerProductId: string;
  readonly consumerProductId: string;
  readonly mode: string;
  readonly tenantId: string;
  readonly workspaceId: string;
  readonly productUnitId: string;
  readonly runId: string;
  readonly correlationId: string;
  readonly requestedAt: string;
  readonly completedAt: string;
  readonly status: string;
  readonly reasonCode?: string;
  readonly policyDecision: string;
  readonly evidenceRefs: readonly string[];
  readonly provenanceRefs: readonly string[];
  readonly capturedAt: string;
}

interface EvidenceFilter {
  readonly contractId?: string;
  readonly status?: string;
  readonly tenantId?: string;
  readonly runId?: string;
}

export default function ProductInteractionEvidenceViewer(): ReactElement {
  const [evidenceRecords, setEvidenceRecords] = useState<ProductInteractionEvidenceRecord[]>([]);
  const [filteredRecords, setFilteredRecords] = useState<ProductInteractionEvidenceRecord[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedRecord, setSelectedRecord] = useState<ProductInteractionEvidenceRecord | null>(null);
  const [filter, setFilter] = useState<EvidenceFilter>({});

  useEffect(() => {
    loadEvidenceRecords();
  }, []);

  useEffect(() => {
    applyFilter();
  }, [filter, evidenceRecords]);

  const loadEvidenceRecords = (): void => {
    setIsLoading(true);
    setError(null);

    try {
      // In a real implementation, this would fetch from the evidence reader API
      // For now, using mock data to demonstrate the UI
      const mockRecords: ProductInteractionEvidenceRecord[] = [
        {
          evidenceId: 'interaction-evidence-abc123',
          schemaVersion: '1.0.0',
          manifestType: 'interaction-evidence',
          contractId: 'kernel://interactions/phr.consent-status.v1',
          contractVersion: '1.0.0',
          providerProductId: 'phr',
          consumerProductId: 'digital-marketing',
          mode: 'request-response',
          tenantId: 'tenant-1',
          workspaceId: 'workspace-1',
          productUnitId: 'phr-product-unit',
          runId: 'run-123',
          correlationId: 'corr-456',
          requestedAt: '2026-05-23T10:00:00Z',
          completedAt: '2026-05-23T10:00:05Z',
          status: 'succeeded',
          policyDecision: 'allowed',
          evidenceRefs: ['products/phr/lifecycle/gate-packs/consent.yaml'],
          provenanceRefs: ['consent-grant-001'],
          capturedAt: '2026-05-23T10:00:06Z',
        },
        {
          evidenceId: 'interaction-evidence-def456',
          schemaVersion: '1.0.0',
          manifestType: 'interaction-evidence',
          contractId: 'kernel://interactions/digital-marketing.notification-preference.v1',
          contractVersion: '1.0.0',
          providerProductId: 'digital-marketing',
          consumerProductId: 'phr',
          mode: 'request-response',
          tenantId: 'tenant-1',
          workspaceId: 'workspace-1',
          productUnitId: 'dmos-product-unit',
          runId: 'run-124',
          correlationId: 'corr-789',
          requestedAt: '2026-05-23T10:05:00Z',
          completedAt: '2026-05-23T10:05:02Z',
          status: 'denied',
          reasonCode: 'product_interaction.consent_missing',
          policyDecision: 'denied',
          evidenceRefs: ['products/digital-marketing/lifecycle/evidence/notification-preference.yaml'],
          provenanceRefs: [],
          capturedAt: '2026-05-23T10:05:03Z',
        },
      ];

      setEvidenceRecords(mockRecords);
      setFilteredRecords(mockRecords);
    } catch (err) {
      studioLogger.error('Failed to load evidence records', { error: err });
      setError('Failed to load evidence records');
    } finally {
      setIsLoading(false);
    }
  };

  const applyFilter = (): void => {
    let filtered = [...evidenceRecords];

    if (filter.contractId) {
      filtered = filtered.filter((r) => r.contractId.includes(filter.contractId!));
    }
    if (filter.status) {
      filtered = filtered.filter((r) => r.status === filter.status);
    }
    if (filter.tenantId) {
      filtered = filtered.filter((r) => r.tenantId.includes(filter.tenantId!));
    }
    if (filter.runId) {
      filtered = filtered.filter((r) => r.runId.includes(filter.runId!));
    }

    setFilteredRecords(filtered);
  };

  const getStatusColor = (status: string): string => {
    switch (status) {
      case 'succeeded':
      case 'allowed':
        return 'bg-green-100 text-green-800';
      case 'denied':
      case 'blocked':
      case 'failed':
        return 'bg-red-100 text-red-800';
      case 'pending':
      case 'running':
        return 'bg-blue-100 text-blue-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  const getPolicyDecisionColor = (decision: string): string => {
    switch (decision) {
      case 'allowed':
        return 'bg-green-100 text-green-800';
      case 'denied':
        return 'bg-red-100 text-red-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  const uniqueContractIds = Array.from(new Set(evidenceRecords.map((r) => r.contractId)));
  const uniqueStatuses = Array.from(new Set(evidenceRecords.map((r) => r.status)));
  const uniqueTenantIds = Array.from(new Set(evidenceRecords.map((r) => r.tenantId)));
  const uniqueRunIds = Array.from(new Set(evidenceRecords.map((r) => r.runId)));

  return (
    <div className="p-6">
      <div className="studio-section">
        <div className="flex items-center justify-between mb-6">
          <Typography variant="h2" className="text-2xl font-bold">
            Product Interaction Evidence
          </Typography>
          <Button
            variant="primary"
            onClick={loadEvidenceRecords}
            disabled={isLoading}
          >
            {isLoading ? 'Refreshing...' : 'Refresh'}
          </Button>
        </div>

        {error && (
          <div className="mb-4 p-4 bg-red-50 border border-red-200 rounded-lg">
            <Typography variant="body2" className="text-red-600">
              {error}
            </Typography>
          </div>
        )}

        {/* Filter Controls */}
        <Card className="mb-6">
          <CardContent className="pt-6">
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Contract ID
                </label>
                <Select
                  value={filter.contractId || ''}
                  onChange={(e) => setFilter({ ...filter, contractId: e.target.value || undefined })}
                  className="w-full"
                >
                  <option value="">All Contracts</option>
                  {uniqueContractIds.map((id) => (
                    <option key={id} value={id}>
                      {id}
                    </option>
                  ))}
                </Select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Status
                </label>
                <Select
                  value={filter.status || ''}
                  onChange={(e) => setFilter({ ...filter, status: e.target.value || undefined })}
                  className="w-full"
                >
                  <option value="">All Statuses</option>
                  {uniqueStatuses.map((status) => (
                    <option key={status} value={status}>
                      {status}
                    </option>
                  ))}
                </Select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Tenant ID
                </label>
                <Select
                  value={filter.tenantId || ''}
                  onChange={(e) => setFilter({ ...filter, tenantId: e.target.value || undefined })}
                  className="w-full"
                >
                  <option value="">All Tenants</option>
                  {uniqueTenantIds.map((id) => (
                    <option key={id} value={id}>
                      {id}
                    </option>
                  ))}
                </Select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Run ID
                </label>
                <Select
                  value={filter.runId || ''}
                  onChange={(e) => setFilter({ ...filter, runId: e.target.value || undefined })}
                  className="w-full"
                >
                  <option value="">All Runs</option>
                  {uniqueRunIds.map((id) => (
                    <option key={id} value={id}>
                      {id}
                    </option>
                  ))}
                </Select>
              </div>
            </div>
          </CardContent>
        </Card>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Evidence Records List */}
          <div className="lg:col-span-1">
            <Card>
              <CardHeader
                title={`Evidence Records (${filteredRecords.length})`}
              />
              <CardContent>
                {filteredRecords.length === 0 ? (
                  <div className="text-center py-8">
                    <Typography variant="body2" className="text-gray-500">
                      No evidence records found. Adjust filters or refresh to load from evidence store.
                    </Typography>
                  </div>
                ) : (
                  <div className="space-y-2 max-h-[600px] overflow-y-auto">
                    {filteredRecords.map((record) => (
                      <div
                        key={record.evidenceId}
                        className={`p-3 border rounded-lg cursor-pointer transition-colors ${
                          selectedRecord?.evidenceId === record.evidenceId
                            ? 'border-blue-500 bg-blue-50'
                            : 'border-gray-200 hover:border-gray-300'
                        }`}
                        onClick={() => setSelectedRecord(record)}
                      >
                        <div className="flex justify-between items-start mb-2">
                          <div className="flex-1 min-w-0">
                            <Typography variant="body1" className="font-medium truncate">
                              {record.contractId}
                            </Typography>
                            <Typography variant="body2" className="text-gray-500 text-sm truncate">
                              {record.providerProductId} → {record.consumerProductId}
                            </Typography>
                          </div>
                          <Badge
                            tone={record.status === 'succeeded' ? 'success' : 'neutral'}
                            variant="soft"
                            className="text-xs ml-2"
                          >
                            {record.status}
                          </Badge>
                        </div>
                        <div className="text-xs text-gray-500">
                          {new Date(record.capturedAt).toLocaleString()}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </CardContent>
            </Card>
          </div>

          {/* Evidence Details */}
          <div className="lg:col-span-2">
            {selectedRecord ? (
              <Card>
                <CardHeader title="Evidence Details" />
                <CardContent>
                  <div className="space-y-4">
                    {/* Timeline View */}
                    <div>
                      <Typography variant="body1" className="font-medium mb-2">
                        Interaction Timeline
                      </Typography>
                      <div className="space-y-3">
                        <div className="relative pl-6 pb-4 border-l-2 border-gray-200">
                          <div className="absolute left-[-5px] top-0 w-3 h-3 bg-blue-500 rounded-full" />
                          <div className="text-sm">
                            <div className="font-medium">Requested</div>
                            <div className="text-gray-600">{new Date(selectedRecord.requestedAt).toLocaleString()}</div>
                          </div>
                        </div>
                        <div className="relative pl-6 pb-4 border-l-2 border-gray-200">
                          <div className="absolute left-[-5px] top-0 w-3 h-3 bg-green-500 rounded-full" />
                          <div className="text-sm">
                            <div className="font-medium">Completed</div>
                            <div className="text-gray-600">{new Date(selectedRecord.completedAt).toLocaleString()}</div>
                          </div>
                        </div>
                        <div className="relative pl-6 border-l-2 border-gray-200">
                          <div className="absolute left-[-5px] top-0 w-3 h-3 bg-gray-500 rounded-full" />
                          <div className="text-sm">
                            <div className="font-medium">Captured</div>
                            <div className="text-gray-600">{new Date(selectedRecord.capturedAt).toLocaleString()}</div>
                          </div>
                        </div>
                      </div>
                    </div>

                    {/* Key Information */}
                    <div className="grid grid-cols-2 gap-4">
                      <div>
                        <Typography variant="body2" className="text-gray-600">
                          Contract ID
                        </Typography>
                        <Typography variant="body1" className="font-mono text-sm break-all">
                          {selectedRecord.contractId}
                        </Typography>
                      </div>
                      <div>
                        <Typography variant="body2" className="text-gray-600">
                          Status
                        </Typography>
                        <Badge
                          tone={selectedRecord.status === 'succeeded' ? 'success' : 'neutral'}
                          variant="soft"
                          className={getStatusColor(selectedRecord.status)}
                        >
                          {selectedRecord.status}
                        </Badge>
                      </div>
                      <div>
                        <Typography variant="body2" className="text-gray-600">
                          Provider
                        </Typography>
                        <Typography variant="body1" className="font-medium">
                          {selectedRecord.providerProductId}
                        </Typography>
                      </div>
                      <div>
                        <Typography variant="body2" className="text-gray-600">
                          Consumer
                        </Typography>
                        <Typography variant="body1" className="font-medium">
                          {selectedRecord.consumerProductId}
                        </Typography>
                      </div>
                      <div>
                        <Typography variant="body2" className="text-gray-600">
                          Policy Decision
                        </Typography>
                        <Badge
                          tone={selectedRecord.policyDecision === 'allowed' ? 'success' : 'neutral'}
                          variant="soft"
                          className={getPolicyDecisionColor(selectedRecord.policyDecision)}
                        >
                          {selectedRecord.policyDecision}
                        </Badge>
                      </div>
                      <div>
                        <Typography variant="body2" className="text-gray-600">
                          Mode
                        </Typography>
                        <Typography variant="body1" className="font-medium">
                          {selectedRecord.mode}
                        </Typography>
                      </div>
                    </div>

                    {selectedRecord.reasonCode && (
                      <div>
                        <Typography variant="body2" className="text-gray-600">
                          Reason Code
                        </Typography>
                        <Typography variant="body1" className="font-mono text-sm">
                          {selectedRecord.reasonCode}
                        </Typography>
                      </div>
                    )}

                    {/* Evidence References */}
                    {selectedRecord.evidenceRefs.length > 0 && (
                      <div>
                        <Typography variant="body2" className="text-gray-600 mb-1">
                          Evidence References
                        </Typography>
                        <div className="space-y-1">
                          {selectedRecord.evidenceRefs.map((ref, idx) => (
                            <div key={idx} className="text-sm font-mono bg-gray-50 p-2 rounded">
                              {ref}
                            </div>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* Provenance References */}
                    {selectedRecord.provenanceRefs.length > 0 && (
                      <div>
                        <Typography variant="body2" className="text-gray-600 mb-1">
                          Provenance References
                        </Typography>
                        <div className="space-y-1">
                          {selectedRecord.provenanceRefs.map((ref, idx) => (
                            <div key={idx} className="text-sm font-mono bg-gray-50 p-2 rounded">
                              {ref}
                            </div>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* Metadata */}
                    <div className="pt-4 border-t">
                      <Typography variant="body2" className="text-gray-500">
                        Evidence ID: {selectedRecord.evidenceId}
                      </Typography>
                      <Typography variant="body2" className="text-gray-500">
                        Correlation ID: {selectedRecord.correlationId}
                      </Typography>
                      <Typography variant="body2" className="text-gray-500">
                        Run ID: {selectedRecord.runId}
                      </Typography>
                    </div>
                  </div>
                </CardContent>
              </Card>
            ) : (
              <Card>
                <CardContent className="py-12">
                  <div className="text-center">
                    <Typography variant="body1" className="text-gray-500 mb-4">
                      Select an evidence record to view details or refresh to load from evidence store.
                    </Typography>
                    <Button variant="primary" onClick={loadEvidenceRecords}>
                      Load Evidence Records
                    </Button>
                  </div>
                </CardContent>
              </Card>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
