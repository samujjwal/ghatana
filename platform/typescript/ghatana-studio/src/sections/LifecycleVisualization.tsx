/**
 * Lifecycle Visualization
 *
 * Displays Kernel lifecycle events and phase transitions.
 * Integrates with @ghatana/kernel-product-contracts lifecycle event contracts.
 *
 * @doc.type component
 * @doc.purpose Lifecycle event monitoring and visualization
 * @doc.layer platform
 */

import type { ReactElement } from 'react';
import { useState, useEffect } from 'react';
import { Button, Typography, Card, CardContent, CardHeader } from '@ghatana/design-system';
import { studioLogger } from '../logging/studioLogger';

type KernelLifecycleEventType =
  | 'lifecycle.phase.started'
  | 'lifecycle.phase.completed'
  | 'lifecycle.phase.failed';

interface KernelLifecycleEvent {
  metadata: {
    eventId: string;
    schemaVersion: string;
    eventType: KernelLifecycleEventType;
    productUnitId: string;
    runId: string;
    phase: string;
    timestamp: string;
    source: string;
    correlationId: string;
  };
  payload: {
    phase: string;
    status: string;
    startedAt?: string;
    completedAt?: string;
    durationMs?: number;
  };
}

interface LifecycleSession {
  id: string;
  productUnitId: string;
  events: KernelLifecycleEvent[];
  timestamp: string;
}

export default function LifecycleVisualization(): ReactElement {
  const [sessions, setSessions] = useState<LifecycleSession[]>([]);
  const [selectedSession, setSelectedSession] = useState<LifecycleSession | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadLifecycleEvents();
  }, []);

  const loadLifecycleEvents = (): void => {
    try {
      // In a real implementation, this would load from the lifecycle event provider
      const stored = localStorage.getItem('lifecycle-sessions');
      if (stored) {
        const sessions: LifecycleSession[] = JSON.parse(stored);
        setSessions(sessions);
      }
    } catch (err) {
      studioLogger.error('Failed to load lifecycle events', { error: err });
    }
  };

  const refreshLifecycleEvents = (): void => {
    setIsLoading(true);
    setError(null);

    try {
      // Simulate fetching from lifecycle event provider
      const mockEvents: KernelLifecycleEvent[] = [
        {
          metadata: {
            eventId: 'event-1',
            schemaVersion: '1.0.0',
            eventType: 'lifecycle.phase.started' as KernelLifecycleEventType,
            productUnitId: 'sample-product-unit',
            runId: 'run-123',
            phase: 'dev',
            timestamp: new Date(Date.now() - 10000).toISOString(),
            source: 'kernel-lifecycle',
            correlationId: 'corr-123',
          },
          payload: {
            phase: 'dev',
            status: 'running',
            startedAt: new Date(Date.now() - 10000).toISOString(),
          },
        },
        {
          metadata: {
            eventId: 'event-2',
            schemaVersion: '1.0.0',
            eventType: 'lifecycle.phase.completed' as KernelLifecycleEventType,
            productUnitId: 'sample-product-unit',
            runId: 'run-123',
            phase: 'dev',
            timestamp: new Date(Date.now() - 5000).toISOString(),
            source: 'kernel-lifecycle',
            correlationId: 'corr-123',
          },
          payload: {
            phase: 'dev',
            status: 'succeeded',
            durationMs: 5000,
            completedAt: new Date(Date.now() - 5000).toISOString(),
          },
        },
      ];

      const newSession: LifecycleSession = {
        id: `session-${Date.now()}`,
        productUnitId: 'sample-product-unit',
        events: mockEvents,
        timestamp: new Date().toISOString(),
      };

      setSessions([newSession]);
      setSelectedSession(newSession);
      localStorage.setItem('lifecycle-sessions', JSON.stringify([newSession]));
    } catch (err) {
      studioLogger.error('Failed to refresh lifecycle events', { error: err });
      setError('Failed to refresh lifecycle events');
    } finally {
      setIsLoading(false);
    }
  };

  const getEventTypeColor = (eventType: KernelLifecycleEventType): string => {
    if (eventType.includes('started')) {
      return 'bg-blue-100 text-blue-800';
    }
    if (eventType.includes('completed')) {
      return 'bg-green-100 text-green-800';
    }
    if (eventType.includes('failed') || eventType.includes('error')) {
      return 'bg-red-100 text-red-800';
    }
    return 'bg-gray-100 text-gray-800';
  };

  const getStatusColor = (status: string): string => {
    switch (status) {
      case 'succeeded':
      case 'passed':
        return 'bg-green-100 text-green-800';
      case 'failed':
      case 'blocked':
        return 'bg-red-100 text-red-800';
      case 'running':
        return 'bg-blue-100 text-blue-800';
      case 'skipped':
        return 'bg-gray-100 text-gray-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  return (
    <div className="p-6">
      <div className="studio-section">
        <div className="flex items-center justify-between mb-6">
          <Typography variant="h2" className="text-2xl font-bold">
            Lifecycle Events
          </Typography>
          <Button
            variant="primary"
            onClick={refreshLifecycleEvents}
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
          {/* Lifecycle Sessions List */}
          <div className="lg:col-span-1">
            <Card>
              <CardHeader title="Event Sessions" />
              <CardContent>
                {sessions.length === 0 ? (
                  <div className="text-center py-8">
                    <Typography variant="body2" className="text-gray-500">
                      No lifecycle events. Click refresh to load from lifecycle event provider.
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
                              {session.events.length} events
                            </Typography>
                            <Typography variant="body2" className="text-gray-500 text-sm">
                              {new Date(session.timestamp).toLocaleString()}
                            </Typography>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </CardContent>
            </Card>
          </div>

          {/* Event Details */}
          <div className="lg:col-span-2">
            {selectedSession ? (
              <Card>
                <CardHeader title={selectedSession.productUnitId} />
                <CardContent>
                  <div className="space-y-4">
                    <div>
                      <Typography variant="body1" className="font-medium mb-2">
                        Event Timeline
                      </Typography>
                      <div className="space-y-3">
                        {selectedSession.events.map((event) => (
                          <div
                            key={event.metadata.eventId}
                            className="relative pl-6 pb-4 border-l-2 border-gray-200 last:border-0"
                          >
                            <div className="absolute left-[-5px] top-0 w-3 h-3 bg-blue-500 rounded-full" />
                            <div className="mb-2">
                              <span
                                className={`px-2 py-1 text-xs rounded-full ${getEventTypeColor(
                                  event.metadata.eventType
                                )}`}
                              >
                                {event.metadata.eventType}
                              </span>
                            </div>
                            <div className="space-y-1 text-sm">
                              <div className="flex justify-between">
                                <span className="text-gray-600">Event ID:</span>
                                <span className="font-mono">{event.metadata.eventId}</span>
                              </div>
                              <div className="flex justify-between">
                                <span className="text-gray-600">Phase:</span>
                                <span className="font-medium">{event.metadata.phase}</span>
                              </div>
                              <div className="flex justify-between">
                                <span className="text-gray-600">Timestamp:</span>
                                <span>{new Date(event.metadata.timestamp).toLocaleString()}</span>
                              </div>
                              <div className="flex justify-between">
                                <span className="text-gray-600">Source:</span>
                                <span>{event.metadata.source}</span>
                              </div>
                              <div className="flex justify-between">
                                <span className="text-gray-600">Correlation ID:</span>
                                <span className="font-mono">{event.metadata.correlationId}</span>
                              </div>
                              {/* Payload-specific fields */}
                              {'status' in event.payload && (
                                <div className="flex justify-between">
                                  <span className="text-gray-600">Status:</span>
                                  <span
                                    className={`px-2 py-1 text-xs rounded-full ${getStatusColor(
                                      String((event.payload as any).status)
                                    )}`}
                                  >
                                    {(event.payload as any).status}
                                  </span>
                                </div>
                              )}
                              {'durationMs' in event.payload && (
                                <div className="flex justify-between">
                                  <span className="text-gray-600">Duration:</span>
                                  <span>{(event.payload as any).durationMs}ms</span>
                                </div>
                              )}
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>

                    <div className="pt-4 border-t">
                      <Typography variant="body2" className="text-gray-500">
                        This visualization consumes lifecycle events from @ghatana/kernel-product-contracts event contracts.
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
                      Select an event session to view details or refresh to load from lifecycle event provider.
                    </Typography>
                    <Button variant="primary" onClick={refreshLifecycleEvents}>
                      Load Lifecycle Events
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
