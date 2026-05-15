/**
 * Preview Lab
 *
 * Preview sandbox testing, device emulation, and trust validation.
 * Integrates with @ghatana/ui-builder preview protocol for secure rendering.
 *
 * @doc.type component
 * @doc.purpose Preview testing and device emulation
 * @doc.layer platform
 */

import type { ReactElement } from 'react';
import { useState, useEffect, useRef } from 'react';
import { Button, Typography, Card, CardContent, CardHeader, CardTitle } from '@ghatana/design-system';

// Import preview protocol from ui-builder platform
import {
  PreviewHostService,
  createSandboxProfile,
  PRESET_VIEWPORTS,
  type SandboxProfile,
  type Viewport,
  type HostToPreviewMessage,
  type PreviewToHostMessage,
} from '@ghatana/ui-builder/preview';
import type { BuilderDocument } from '@ghatana/ui-builder';

interface PreviewSession {
  id: string;
  document: BuilderDocument;
  sandbox: SandboxProfile;
  viewport: Viewport;
  isActive: boolean;
  messages: PreviewMessage[];
}

interface PreviewMessage {
  timestamp: string;
  direction: 'host-to-preview' | 'preview-to-host';
  type: string;
  data: unknown;
}

export default function PreviewLab(): ReactElement {
  const [sessions, setSessions] = useState<PreviewSession[]>([]);
  const [selectedSession, setSelectedSession] = useState<PreviewSession | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const [previewService, setPreviewService] = useState<PreviewHostService | null>(null);

  useEffect(() => {
    // Initialize preview service
    const service = new PreviewHostService();
    setPreviewService(service);

    // Set up message handlers
    service.onMessage((message: PreviewToHostMessage) => {
      if (selectedSession) {
        const newMessage: PreviewMessage = {
          timestamp: new Date().toISOString(),
          direction: 'preview-to-host',
          type: message.type,
          data: message,
        };

        setSessions(prev => prev.map(session => 
          session.id === selectedSession.id 
            ? { ...session, messages: [...session.messages, newMessage] }
            : session
        ));
      }
    });

    return () => {
      service.teardown();
    };
  }, [selectedSession]);

  const createPreviewSession = (document: BuilderDocument): void => {
    if (!previewService) {
      setError('Preview service not initialized');
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      const sandbox: SandboxProfile = createSandboxProfile({
        id: `sandbox-${Date.now()}`,
        name: 'Preview Lab Session',
        viewport: PRESET_VIEWPORTS.desktop,
        theme: 'default',
        locale: 'en-US',
        featureFlags: {},
        trustedOrigins: ['http://localhost:3000'],
      });

      const session: PreviewSession = {
        id: `session-${Date.now()}`,
        document,
        sandbox,
        viewport: sandbox.viewport,
        isActive: false,
        messages: [],
      };

      setSessions(prev => [...prev, session]);
      setSelectedSession(session);
    } catch (err) {
      console.error('Failed to create preview session:', err);
      setError('Failed to create preview session');
    } finally {
      setIsLoading(false);
    }
  };

  const launchPreview = async (): Promise<void> => {
    if (!selectedSession || !previewService || !iframeRef.current) {
      setError('No session selected or preview service not ready');
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      // Mount the document in the preview iframe
      await previewService.mount(selectedSession.document, selectedSession.sandbox);

      // Update session state
      setSessions(prev => prev.map(session => 
        session.id === selectedSession.id 
          ? { ...session, isActive: true }
          : session
      ));

      setSelectedSession(prev => prev ? { ...prev, isActive: true } : null);
    } catch (err) {
      console.error('Failed to launch preview:', err);
      setError('Failed to launch preview');
    } finally {
      setIsLoading(false);
    }
  };

  const updateViewport = (viewport: Viewport): void => {
    if (!selectedSession || !previewService) return;

    previewService.setViewport(viewport);

    setSessions(prev => prev.map(session => 
      session.id === selectedSession.id 
        ? { ...session, viewport }
        : session
    ));

    setSelectedSession(prev => prev ? { ...prev, viewport } : null);
  };

  const stopPreview = async (): Promise<void> => {
    if (!selectedSession || !previewService) return;

    try {
      await previewService.teardown();

      setSessions(prev => prev.map(session => 
        session.id === selectedSession.id 
          ? { ...session, isActive: false }
          : session
      ));

      setSelectedSession(prev => prev ? { ...prev, isActive: false } : null);
    } catch (err) {
      console.error('Failed to stop preview:', err);
      setError('Failed to stop preview');
    }
  };

  const loadSampleDocument = (): void => {
    // Create a sample BuilderDocument for testing
    const sampleDocument: BuilderDocument = {
      schemaVersion: '1.0.0',
      documentId: `sample-${Date.now()}` as any,
      owner: 'preview-lab',
      root: 'root-node',
      nodes: {
        'root-node': {
          id: 'root-node' as any,
          contractName: 'RootContainer',
          props: {},
          slots: {},
          bindings: [],
          metadata: {
            name: 'Root',
            locked: false,
            hidden: false,
          },
        },
      },
      bindings: [],
      layout: {
        type: 'flex',
        nodes: {
          'root-node': {
            id: 'root-node',
            type: 'root',
            children: [],
            layout: 'flex',
            layoutProps: { direction: 'vertical' },
          },
        },
        rootId: 'root-node',
      },
      metadata: {
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        changeCount: 0,
      },
    };

    createPreviewSession(sampleDocument);
  };

  return (
    <div className="p-6">
      <div className="studio-section">
        <div className="flex items-center justify-between mb-6">
          <Typography variant="h2" className="text-2xl font-bold">
            Preview Lab
          </Typography>
          <div className="flex gap-2">
            <Button 
              variant="secondary" 
              onClick={loadSampleDocument}
              disabled={isLoading}
            >
              Load Sample
            </Button>
            <Button 
              variant="primary" 
              onClick={launchPreview}
              disabled={!selectedSession || isLoading || selectedSession.isActive}
            >
              {selectedSession?.isActive ? 'Preview Running' : 'Launch Preview'}
            </Button>
          </div>
        </div>

        {error && (
          <div className="mb-4 p-4 bg-red-50 border border-red-200 rounded-lg">
            <Typography variant="body2" className="text-red-600">
              {error}
            </Typography>
          </div>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Session Controls */}
          <div className="lg:col-span-1">
            <Card>
              <CardHeader>
                <CardTitle>Preview Sessions</CardTitle>
              </CardHeader>
              <CardContent>
                {sessions.length === 0 ? (
                  <div className="text-center py-8">
                    <Typography variant="body2" className="text-gray-500">
                      No preview sessions. Load a sample document to get started.
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
                              {session.sandbox.name}
                            </Typography>
                            <Typography variant="body2" className="text-gray-500 text-sm">
                              {session.viewport.label}
                            </Typography>
                            <div className="mt-1">
                              <span className={`inline-flex px-2 py-1 text-xs rounded-full ${
                                session.isActive 
                                  ? 'bg-green-100 text-green-800' 
                                  : 'bg-gray-100 text-gray-800'
                              }`}>
                                {session.isActive ? 'Active' : 'Inactive'}
                              </span>
                            </div>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </CardContent>
            </Card>

            {/* Viewport Controls */}
            {selectedSession && (
              <Card className="mt-4">
                <CardHeader>
                  <CardTitle>Viewport</CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="space-y-2">
                    {Object.entries(PRESET_VIEWPORTS).map(([key, viewport]) => (
                      <Button
                        key={key}
                        variant={selectedSession.viewport.label === viewport.label ? "primary" : "ghost"}
                        size="sm"
                        className="w-full justify-start"
                        onClick={() => updateViewport(viewport)}
                      >
                        {viewport.label}
                      </Button>
                    ))}
                  </div>
                  {selectedSession.isActive && (
                    <Button 
                      variant="destructive" 
                      size="sm" 
                      className="w-full mt-4"
                      onClick={stopPreview}
                    >
                      Stop Preview
                    </Button>
                  )}
                </CardContent>
              </Card>
            )}
          </div>

          {/* Preview Area */}
          <div className="lg:col-span-2">
            <Card>
              <CardHeader>
                <CardTitle>Preview Sandbox</CardTitle>
              </CardHeader>
              <CardContent>
                {selectedSession ? (
                  <div className="space-y-4">
                    <div className="border rounded-lg overflow-hidden">
                      <div 
                        className="bg-gray-100 p-2 text-sm text-gray-600"
                        style={{ 
                          width: `${Math.min(selectedSession.viewport.width, 600)}px`,
                          margin: '0 auto'
                        }}
                      >
                        {selectedSession.viewport.width} × {selectedSession.viewport.height}
                      </div>
                      <iframe
                        ref={iframeRef}
                        src="about:blank"
                        className="border-0"
                        style={{
                          width: `${Math.min(selectedSession.viewport.width, 600)}px`,
                          height: `${Math.min(selectedSession.viewport.height, 400)}px`,
                          margin: '0 auto',
                          display: 'block',
                        }}
                        sandbox="allow-scripts allow-same-origin allow-forms"
                      />
                    </div>

                    <div>
                      <Typography variant="body1" className="font-medium mb-2">
                        Session Details
                      </Typography>
                      <div className="grid grid-cols-2 gap-4 text-sm">
                        <div>
                          <span className="text-gray-500">Sandbox ID:</span>
                          <span className="ml-2">{selectedSession.sandbox.id}</span>
                        </div>
                        <div>
                          <span className="text-gray-500">Theme:</span>
                          <span className="ml-2">{selectedSession.sandbox.theme}</span>
                        </div>
                        <div>
                          <span className="text-gray-500">Locale:</span>
                          <span className="ml-2">{selectedSession.sandbox.locale}</span>
                        </div>
                        <div>
                          <span className="text-gray-500">Messages:</span>
                          <span className="ml-2">{selectedSession.messages.length}</span>
                        </div>
                      </div>
                    </div>

                    <div className="pt-4 border-t">
                      <Typography variant="body2" className="text-gray-500">
                        This preview uses the @ghatana/ui-builder preview protocol for secure sandboxed rendering.
                      </Typography>
                    </div>
                  </div>
                ) : (
                  <div className="text-center py-12">
                    <Typography variant="body1" className="text-gray-500 mb-4">
                      Select a preview session or load a sample document to get started.
                    </Typography>
                    <Button variant="primary" onClick={loadSampleDocument}>
                      Load Sample Document
                    </Button>
                  </div>
                )}
              </CardContent>
            </Card>
          </div>
        </div>
      </div>
    </div>
  );
}
