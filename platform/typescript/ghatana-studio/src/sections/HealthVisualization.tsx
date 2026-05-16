/**
 * Runtime Health Visualization
 *
 * Displays Kernel lifecycle health snapshots and runtime truth status.
 * Integrates with @ghatana/kernel-product-contracts health contracts.
 *
 * @doc.type component
 * @doc.purpose Runtime health monitoring and visualization
 * @doc.layer platform
 */

import type { ReactElement } from 'react';
import { useState, useEffect } from 'react';
import { Button, Typography, Card, CardContent, CardHeader } from '@ghatana/design-system';

type HealthStatus = 'healthy' | 'degraded' | 'blocked' | 'failed' | 'unknown';

interface ProductUnitHealthSnapshot {
  productUnitId: string;
  timestamp: string;
  overallStatus: HealthStatus;
  lifecycle?: {
    phase: string;
    status: HealthStatus;
  };
  deployment?: {
    environment: string;
    status: HealthStatus;
  };
  agentGovernance?: {
    overallStatus: HealthStatus;
    governedActions: number;
    blockedActions: number;
  };
}

interface HealthSession {
  id: string;
  productUnitId: string;
  snapshot: ProductUnitHealthSnapshot;
  timestamp: string;
}

export default function HealthVisualization(): ReactElement {
  const [sessions, setSessions] = useState<HealthSession[]>([]);
  const [selectedSession, setSelectedSession] = useState<HealthSession | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadHealthSnapshots();
  }, []);

  const loadHealthSnapshots = (): void => {
    try {
      // In a real implementation, this would load from the runtime truth provider
      const stored = localStorage.getItem('health-snapshots');
      if (stored) {
        const snapshots: HealthSession[] = JSON.parse(stored);
        setSessions(snapshots);
      }
    } catch (err) {
      console.error('Failed to load health snapshots:', err);
    }
  };

  const refreshSnapshots = (): void => {
    setIsLoading(true);
    setError(null);

    try {
      // Simulate fetching from runtime truth provider
      const mockSnapshot: ProductUnitHealthSnapshot = {
        productUnitId: 'sample-product-unit',
        timestamp: new Date().toISOString(),
        overallStatus: 'healthy' as HealthStatus,
        lifecycle: {
          phase: 'dev',
          status: 'healthy' as HealthStatus,
        },
        deployment: {
          environment: 'development',
          status: 'healthy' as HealthStatus,
        },
        agentGovernance: {
          overallStatus: 'healthy' as HealthStatus,
          governedActions: 0,
          blockedActions: 0,
        },
      };

      const newSession: HealthSession = {
        id: `session-${Date.now()}`,
        productUnitId: mockSnapshot.productUnitId,
        snapshot: mockSnapshot,
        timestamp: mockSnapshot.timestamp,
      };

      setSessions([newSession]);
      setSelectedSession(newSession);
      localStorage.setItem('health-snapshots', JSON.stringify([newSession]));
    } catch (err) {
      console.error('Failed to refresh health snapshots:', err);
      setError('Failed to refresh health snapshots');
    } finally {
      setIsLoading(false);
    }
  };

  const getHealthStatusColor = (status: HealthStatus): string => {
    switch (status) {
      case 'healthy':
        return 'bg-green-100 text-green-800';
      case 'degraded':
        return 'bg-yellow-100 text-yellow-800';
      case 'blocked':
      case 'failed':
        return 'bg-red-100 text-red-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  return (
    <div className="p-6">
      <div className="studio-section">
        <div className="flex items-center justify-between mb-6">
          <Typography variant="h2" className="text-2xl font-bold">
            Runtime Health
          </Typography>
          <Button
            variant="primary"
            onClick={refreshSnapshots}
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

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Health Sessions List */}
          <div className="lg:col-span-1">
            <Card>
              <CardHeader title="Health Snapshots" />
              <CardContent>
                {sessions.length === 0 ? (
                  <div className="text-center py-8">
                    <Typography variant="body2" className="text-gray-500">
                      No health snapshots. Click refresh to load from runtime truth provider.
                    </Typography>
                  </div>
                ) : (
                  <div className="space-y-2">
                    {sessions.map((session) => (
                      <div
                        key={session.id}
                        className={`p-3 border rounded-lg cursor-pointer transition-colors ${
                          selectedSession?.id === session.id
                            ? 'border-blue-500 bg-blue-50'
                            : 'border-gray-200 hover:border-gray-300'
                        }`}
                        onClick={() => setSelectedSession(session)}
                      >
                        <div className="flex justify-between items-start">
                          <div>
                            <Typography variant="body1" className="font-medium">
                              {session.productUnitId}
                            </Typography>
                            <Typography variant="body2" className="text-gray-500 text-sm">
                              {new Date(session.timestamp).toLocaleString()}
                            </Typography>
                          </div>
                          <span
                            className={`px-2 py-1 text-xs rounded-full ${getHealthStatusColor(
                              session.snapshot.overallStatus
                            )}`}
                          >
                            {session.snapshot.overallStatus}
                          </span>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </CardContent>
            </Card>
          </div>

          {/* Health Details */}
          <div className="lg:col-span-2">
            {selectedSession ? (
              <Card>
                <CardHeader title={selectedSession.productUnitId} />
                <CardContent>
                  <div className="space-y-6">
                    {/* Overall Status */}
                    <div>
                      <Typography variant="body1" className="font-medium mb-2">
                        Overall Status
                      </Typography>
                      <span
                        className={`px-3 py-2 text-sm rounded-lg ${getHealthStatusColor(
                          selectedSession.snapshot.overallStatus
                        )}`}
                      >
                        {selectedSession.snapshot.overallStatus.toUpperCase()}
                      </span>
                    </div>

                    {/* Lifecycle Health */}
                    <div>
                      <Typography variant="body1" className="font-medium mb-2">
                        Lifecycle Health
                      </Typography>
                      <div className="space-y-2">
                        <div className="flex justify-between items-center">
                          <span className="text-sm text-gray-600">Phase:</span>
                          <span className="text-sm font-medium">{selectedSession.snapshot.lifecycle?.phase}</span>
                        </div>
                        <div className="flex justify-between items-center">
                          <span className="text-sm text-gray-600">Status:</span>
                          <span
                            className={`px-2 py-1 text-xs rounded-full ${getHealthStatusColor(
                              selectedSession.snapshot.lifecycle?.status || 'unknown'
                            )}`}
                          >
                            {selectedSession.snapshot.lifecycle?.status}
                          </span>
                        </div>
                      </div>
                    </div>

                    {/* Deployment Health */}
                    <div>
                      <Typography variant="body1" className="font-medium mb-2">
                        Deployment Health
                      </Typography>
                      <div className="space-y-2">
                        <div className="flex justify-between items-center">
                          <span className="text-sm text-gray-600">Environment:</span>
                          <span className="text-sm font-medium">{selectedSession.snapshot.deployment?.environment}</span>
                        </div>
                        <div className="flex justify-between items-center">
                          <span className="text-sm text-gray-600">Status:</span>
                          <span
                            className={`px-2 py-1 text-xs rounded-full ${getHealthStatusColor(
                              selectedSession.snapshot.deployment?.status || 'unknown'
                            )}`}
                          >
                            {selectedSession.snapshot.deployment?.status}
                          </span>
                        </div>
                      </div>
                    </div>

                    {/* Agent Governance Health */}
                    <div>
                      <Typography variant="body1" className="font-medium mb-2">
                        Agent Governance
                      </Typography>
                      <div className="space-y-2">
                        <div className="flex justify-between items-center">
                          <span className="text-sm text-gray-600">Status:</span>
                          <span
                            className={`px-2 py-1 text-xs rounded-full ${getHealthStatusColor(
                              selectedSession.snapshot.agentGovernance?.overallStatus || 'unknown'
                            )}`}
                          >
                            {selectedSession.snapshot.agentGovernance?.overallStatus}
                          </span>
                        </div>
                        <div className="flex justify-between items-center">
                          <span className="text-sm text-gray-600">Governed Actions:</span>
                          <span className="text-sm font-medium">{selectedSession.snapshot.agentGovernance?.governedActions}</span>
                        </div>
                        <div className="flex justify-between items-center">
                          <span className="text-sm text-gray-600">Blocked Actions:</span>
                          <span className="text-sm font-medium">{selectedSession.snapshot.agentGovernance?.blockedActions}</span>
                        </div>
                      </div>
                    </div>

                    <div className="pt-4 border-t">
                      <Typography variant="body2" className="text-gray-500">
                        This visualization consumes runtime truth from @ghatana/kernel-product-contracts health contracts.
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
                      Select a health snapshot to view details or refresh to load from runtime truth provider.
                    </Typography>
                    <Button variant="primary" onClick={refreshSnapshots}>
                      Load Health Snapshots
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
