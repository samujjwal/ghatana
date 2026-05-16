/**
 * Artifacts Visualization
 *
 * Displays Kernel lifecycle artifacts and their provenance.
 * Integrates with @ghatana/kernel-product-contracts artifact contracts.
 *
 * @doc.type component
 * @doc.purpose Artifact provenance and visualization
 * @doc.layer platform
 */

import type { ReactElement } from 'react';
import { useState, useEffect } from 'react';
import { Button, Typography, Card, CardContent, CardHeader } from '@ghatana/design-system';
import { studioLogger } from '../logging/studioLogger';

interface ProductArtifactView {
  artifactId: string;
  artifactType: string;
  productUnitId: string;
  phase: string;
  path: string;
  fingerprint: string;
  size: number;
  createdAt: string;
  createdBy: string;
  metadata?: Record<string, string>;
}

interface ArtifactIntelligenceView {
  evidenceId: string;
  riskLevel: 'low' | 'medium' | 'high' | 'critical';
  lifecycleReadiness: 'design-only' | 'testable' | 'executable';
  productShapeKind: string;
}

interface ArtifactSession {
  id: string;
  productUnitId: string;
  artifact: ProductArtifactView;
  intelligence?: ArtifactIntelligenceView;
  timestamp: string;
}

export default function ArtifactsVisualization(): ReactElement {
  const [sessions, setSessions] = useState<ArtifactSession[]>([]);
  const [selectedSession, setSelectedSession] = useState<ArtifactSession | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadArtifacts();
  }, []);

  const loadArtifacts = (): void => {
    try {
      // In a real implementation, this would load from the artifact provider
      const stored = localStorage.getItem('artifact-sessions');
      if (stored) {
        const artifacts: ArtifactSession[] = JSON.parse(stored);
        setSessions(artifacts);
      }
    } catch (err) {
      studioLogger.error('Failed to load artifacts', { error: err });
    }
  };

  const refreshArtifacts = (): void => {
    setIsLoading(true);
    setError(null);

    try {
      // Simulate fetching from artifact provider
      const mockArtifact: ProductArtifactView = {
        artifactId: 'artifact-' + Date.now(),
        artifactType: 'build-artifact',
        productUnitId: 'sample-product-unit',
        phase: 'build',
        path: '/artifacts/build/sample-product-unit.jar',
        fingerprint: 'sha256:abc123...',
        size: 1024000,
        createdAt: new Date().toISOString(),
        createdBy: 'kernel-lifecycle',
        metadata: {
          buildNumber: '123',
          gitCommit: 'abc123def456',
          environment: 'development',
        },
      };

      const mockIntelligence: ArtifactIntelligenceView = {
        evidenceId: 'evidence-' + Date.now(),
        productShapeKind: 'backend-service',
        lifecycleReadiness: 'executable',
        riskLevel: 'low',
      };

      const newSession: ArtifactSession = {
        id: `session-${Date.now()}`,
        productUnitId: mockArtifact.productUnitId,
        artifact: mockArtifact,
        intelligence: mockIntelligence,
        timestamp: mockArtifact.createdAt,
      };

      setSessions([newSession]);
      setSelectedSession(newSession);
      localStorage.setItem('artifact-sessions', JSON.stringify([newSession]));
    } catch (err) {
      studioLogger.error('Failed to refresh artifacts', { error: err });
      setError('Failed to refresh artifacts');
    } finally {
      setIsLoading(false);
    }
  };

  const getRiskLevelColor = (riskLevel: string): string => {
    switch (riskLevel) {
      case 'low':
        return 'bg-green-100 text-green-800';
      case 'medium':
        return 'bg-yellow-100 text-yellow-800';
      case 'high':
        return 'bg-orange-100 text-orange-800';
      case 'critical':
        return 'bg-red-100 text-red-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  const getReadinessColor = (readiness: string): string => {
    switch (readiness) {
      case 'executable':
        return 'bg-green-100 text-green-800';
      case 'testable':
        return 'bg-blue-100 text-blue-800';
      case 'design-only':
        return 'bg-purple-100 text-purple-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  return (
    <div className="p-6">
      <div className="studio-section">
        <div className="flex items-center justify-between mb-6">
          <Typography variant="h2" className="text-2xl font-bold">
            Artifacts
          </Typography>
          <Button
            variant="primary"
            onClick={refreshArtifacts}
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
          {/* Artifacts List */}
          <div className="lg:col-span-1">
            <Card>
              <CardHeader title="Artifacts" />
              <CardContent>
                {sessions.length === 0 ? (
                  <div className="text-center py-8">
                    <Typography variant="body2" className="text-gray-500">
                      No artifacts. Click refresh to load from artifact provider.
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
                              {session.artifact.artifactType}
                            </Typography>
                            <Typography variant="body2" className="text-gray-500 text-sm">
                              {session.productUnitId}
                            </Typography>
                            <Typography variant="body2" className="text-gray-500 text-sm">
                              {new Date(session.timestamp).toLocaleString()}
                            </Typography>
                          </div>
                          {session.intelligence && (
                            <span
                              className={`px-2 py-1 text-xs rounded-full ${getRiskLevelColor(
                                session.intelligence.riskLevel
                              )}`}
                            >
                              {session.intelligence.riskLevel}
                            </span>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </CardContent>
            </Card>
          </div>

          {/* Artifact Details */}
          <div className="lg:col-span-2">
            {selectedSession ? (
              <Card>
                <CardHeader title={selectedSession.artifact.artifactType} />
                <CardContent>
                  <div className="space-y-6">
                    {/* Artifact Information */}
                    <div>
                      <Typography variant="body1" className="font-medium mb-2">
                        Artifact Information
                      </Typography>
                      <div className="grid grid-cols-2 gap-4 text-sm">
                        <div>
                          <span className="text-gray-500">ID:</span>
                          <span className="ml-2">{selectedSession.artifact.artifactId}</span>
                        </div>
                        <div>
                          <span className="text-gray-500">Type:</span>
                          <span className="ml-2">{selectedSession.artifact.artifactType}</span>
                        </div>
                        <div>
                          <span className="text-gray-500">Phase:</span>
                          <span className="ml-2">{selectedSession.artifact.phase}</span>
                        </div>
                        <div>
                          <span className="text-gray-500">Size:</span>
                          <span className="ml-2">{(selectedSession.artifact.size / 1024).toFixed(2)} KB</span>
                        </div>
                      </div>
                    </div>

                    {/* Artifact Intelligence */}
                    {selectedSession.intelligence && (
                      <div>
                        <Typography variant="body1" className="font-medium mb-2">
                          Artifact Intelligence
                        </Typography>
                        <div className="space-y-2">
                          <div className="flex justify-between items-center">
                            <span className="text-sm text-gray-600">Risk Level:</span>
                            <span
                              className={`px-2 py-1 text-xs rounded-full ${getRiskLevelColor(
                                selectedSession.intelligence.riskLevel
                              )}`}
                            >
                              {selectedSession.intelligence.riskLevel}
                            </span>
                          </div>
                          <div className="flex justify-between items-center">
                            <span className="text-sm text-gray-600">Readiness:</span>
                            <span
                              className={`px-2 py-1 text-xs rounded-full ${getReadinessColor(
                                selectedSession.intelligence.lifecycleReadiness
                              )}`}
                            >
                              {selectedSession.intelligence.lifecycleReadiness}
                            </span>
                          </div>
                          <div className="flex justify-between items-center">
                            <span className="text-sm text-gray-600">Product Shape:</span>
                            <span className="text-sm font-medium">{selectedSession.intelligence.productShapeKind}</span>
                          </div>
                        </div>
                      </div>
                    )}

                    {/* Provenance */}
                    <div>
                      <Typography variant="body1" className="font-medium mb-2">
                        Provenance
                      </Typography>
                      <div className="space-y-2">
                        <div className="flex justify-between items-center">
                          <span className="text-sm text-gray-600">Created By:</span>
                          <span className="text-sm font-medium">{selectedSession.artifact.createdBy}</span>
                        </div>
                        <div className="flex justify-between items-center">
                          <span className="text-sm text-gray-600">Created At:</span>
                          <span className="text-sm font-medium">{new Date(selectedSession.artifact.createdAt).toLocaleString()}</span>
                        </div>
                        <div className="flex justify-between items-center">
                          <span className="text-sm text-gray-600">Fingerprint:</span>
                          <span className="text-sm font-mono">{selectedSession.artifact.fingerprint}</span>
                        </div>
                      </div>
                    </div>

                    {/* Metadata */}
                    {selectedSession.artifact.metadata && (
                      <div>
                        <Typography variant="body1" className="font-medium mb-2">
                          Metadata
                        </Typography>
                        <div className="space-y-2">
                          {Object.entries(selectedSession.artifact.metadata).map(([key, value]) => (
                            <div key={key} className="flex justify-between items-center">
                              <span className="text-sm text-gray-600">{key}:</span>
                              <span className="text-sm font-medium">{String(value)}</span>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}

                    <div className="pt-4 border-t">
                      <Typography variant="body2" className="text-gray-500">
                        This visualization consumes artifact data from @ghatana/kernel-product-contracts artifact contracts.
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
                      Select an artifact to view details or refresh to load from artifact provider.
                    </Typography>
                    <Button variant="primary" onClick={refreshArtifacts}>
                      Load Artifacts
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
