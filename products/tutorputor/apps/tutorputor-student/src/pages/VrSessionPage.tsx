import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useParams, useSearchParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation } from '@tanstack/react-query';
import {
  Card,
  CardContent,
  Button,
  Badge,
  Progress,
  Spinner,
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@ghatana/design-system';

interface VrExperiment {
  id: string;
  title: string;
  description: string;
  category: string;
  difficulty: 'beginner' | 'intermediate' | 'advanced';
  instructions: string[];
  objectives: {
    id: string;
    title: string;
    description: string;
    points: number;
  }[];
  assets: {
    id: string;
    type: 'model' | 'texture' | 'audio' | 'script';
    url: string;
    name: string;
  }[];
}

interface VrSession {
  id: string;
  experimentId: string;
  status: 'initializing' | 'active' | 'paused' | 'completed';
  startedAt: string;
  duration: number;
  completedObjectives: string[];
  score: number;
  interactions: number;
}

interface InteractionEvent {
  type: 'grab' | 'release' | 'click' | 'hover' | 'collision' | 'checkpoint';
  objectId: string;
  objectName: string;
  position?: { x: number; y: number; z: number };
  timestamp: number;
  metadata?: Record<string, unknown>;
}

/**
 * @doc.type component
 * @doc.purpose VR experiment session runner with WebXR support
 * @doc.layer product
 * @doc.pattern Page
 */
export function VrSessionPage() {
  const { experimentId } = useParams<{ experimentId: string }>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const deviceType = (searchParams.get('device') as 'vr' | 'ar' | 'desktop') || 'desktop';

  const canvasRef = useRef<HTMLCanvasElement>(null);
  const xrSessionRef = useRef<XRSession | null>(null);
  const animationFrameRef = useRef<number | null>(null);

  const [sessionState, setSessionState] = useState<VrSession | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [showExitDialog, setShowExitDialog] = useState(false);
  const [showCompletionDialog, setShowCompletionDialog] = useState(false);
  const [loadingProgress, setLoadingProgress] = useState(0);
  const [isPaused, setIsPaused] = useState(false);
  const [interactions, setInteractions] = useState<InteractionEvent[]>([]);

  // Fetch experiment data
  const { data: experiment, isLoading: experimentLoading } = useQuery({
    queryKey: ['vr-experiment', experimentId],
    queryFn: async () => {
      const res = await fetch(`/api/v1/vr/experiments/${experimentId}`);
      return res.json() as Promise<VrExperiment>;
    },
    enabled: !!experimentId,
  });

  // Start session mutation
  const startSessionMutation = useMutation({
    mutationFn: async () => {
      const res = await fetch(`/api/v1/vr/experiments/${experimentId}/sessions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ deviceType }),
      });
      return res.json() as Promise<VrSession>;
    },
    onSuccess: (session) => {
      setSessionState(session);
    },
  });

  // Update session mutation
  const updateSessionMutation = useMutation({
    mutationFn: async (updates: Partial<VrSession>) => {
      if (!sessionState) return null;
      const res = await fetch(`/api/v1/vr/sessions/${sessionState.id}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(updates),
      });
      return res.json() as Promise<VrSession>;
    },
    onSuccess: (session) => {
      if (session) setSessionState(session);
    },
  });

  // Log interaction mutation
  const logInteractionMutation = useMutation({
    mutationFn: async (event: InteractionEvent) => {
      if (!sessionState) return;
      await fetch(`/api/v1/vr/sessions/${sessionState.id}/interactions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(event),
      });
    },
  });

  // Complete session mutation
  const completeSessionMutation = useMutation({
    mutationFn: async () => {
      if (!sessionState) return null;
      const res = await fetch(`/api/v1/vr/sessions/${sessionState.id}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          completedObjectives: sessionState.completedObjectives,
          score: sessionState.score,
          interactions: interactions.length,
        }),
      });
      return res.json();
    },
    onSuccess: () => {
      setShowCompletionDialog(true);
    },
  });

  // Initialize VR session
  useEffect(() => {
    if (!experimentLoading && experiment) {
      initializeSession();
    }
    return () => {
      cleanup();
    };
  }, [experimentLoading, experiment]);

  const initializeSession = async () => {
    setIsLoading(true);

    // Simulate loading assets
    for (let i = 0; i <= 100; i += 10) {
      await new Promise((resolve) => setTimeout(resolve, 100));
      setLoadingProgress(i);
    }

    // Start backend session
    await startSessionMutation.mutateAsync();

    // Initialize WebXR if needed
    if (deviceType !== 'desktop' && navigator.xr) {
      await initializeWebXR();
    } else {
      initializeDesktopMode();
    }

    setIsLoading(false);
  };

  const initializeWebXR = async () => {
    if (!navigator.xr) return;

    try {
      const sessionType = deviceType === 'vr' ? 'immersive-vr' : 'immersive-ar';
      const isSupported = await navigator.xr.isSessionSupported(sessionType);

      if (!isSupported) {
        console.warn(`${sessionType} not supported, falling back to desktop`);
        initializeDesktopMode();
        return;
      }

      const xrSession = await navigator.xr.requestSession(sessionType, {
        requiredFeatures: ['local-floor'],
        optionalFeatures: ['hand-tracking', 'bounded-floor'],
      });

      xrSessionRef.current = xrSession;

      xrSession.addEventListener('end', () => {
        xrSessionRef.current = null;
        handleSessionEnd();
      });

      // Initialize WebXR rendering loop
      const canvas = canvasRef.current;
      if (canvas) {
        const gl = canvas.getContext('webgl2', { xrCompatible: true });
        if (gl) {
          await xrSession.updateRenderState({ baseLayer: new XRWebGLLayer(xrSession, gl) });
          startRenderLoop(xrSession, gl);
        }
      }
    } catch (error) {
      console.error('Failed to initialize WebXR:', error);
      initializeDesktopMode();
    }
  };

  const initializeDesktopMode = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const gl = canvas.getContext('webgl2');
    if (!gl) return;

    // Basic desktop 3D rendering setup
    gl.clearColor(0.1, 0.1, 0.2, 1.0);
    gl.clear(gl.COLOR_BUFFER_BIT);

    // Add mouse/keyboard controls for desktop mode
    canvas.addEventListener('click', handleDesktopClick);
    canvas.addEventListener('mousemove', handleDesktopMouseMove);
    document.addEventListener('keydown', handleDesktopKeyDown);

    // Start desktop render loop
    const render = () => {
      if (isPaused) return;
      gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
      // Render 3D scene here
      animationFrameRef.current = requestAnimationFrame(render);
    };
    render();
  };

  const startRenderLoop = (xrSession: XRSession, gl: WebGL2RenderingContext) => {
    const onXRFrame: XRFrameRequestCallback = (time, frame) => {
      if (!xrSessionRef.current) return;

      const pose = frame.getViewerPose(xrSession.renderState.baseLayer?.getViewport);

      gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);

      // Render 3D scene for each eye
      // This would integrate with Three.js or custom WebGL rendering

      xrSession.requestAnimationFrame(onXRFrame);
    };

    xrSession.requestAnimationFrame(onXRFrame);
  };

  const handleDesktopClick = useCallback((event: MouseEvent) => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const rect = canvas.getBoundingClientRect();
    const x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
    const y = -((event.clientY - rect.top) / rect.height) * 2 + 1;

    const interaction: InteractionEvent = {
      type: 'click',
      objectId: 'scene',
      objectName: 'Scene Click',
      position: { x, y, z: 0 },
      timestamp: Date.now(),
    };

    setInteractions((prev) => [...prev, interaction]);
    logInteractionMutation.mutate(interaction);
  }, []);

  const handleDesktopMouseMove = useCallback((event: MouseEvent) => {
    // Handle hover interactions
  }, []);

  const handleDesktopKeyDown = useCallback((event: KeyboardEvent) => {
    switch (event.key) {
      case 'Escape':
        setShowExitDialog(true);
        break;
      case 'p':
      case 'P':
        togglePause();
        break;
    }
  }, []);

  const togglePause = () => {
    setIsPaused((prev) => !prev);
    if (sessionState) {
      updateSessionMutation.mutate({
        status: isPaused ? 'active' : 'paused',
      });
    }
  };

  const handleSessionEnd = () => {
    if (sessionState?.status !== 'completed') {
      setShowExitDialog(true);
    }
  };

  const handleExit = (save: boolean) => {
    setShowExitDialog(false);
    if (save) {
      updateSessionMutation.mutate({ status: 'completed' });
    }
    cleanup();
    navigate('/vr-labs');
  };

  const handleComplete = () => {
    completeSessionMutation.mutate();
  };

  const cleanup = () => {
    if (xrSessionRef.current) {
      xrSessionRef.current.end();
      xrSessionRef.current = null;
    }
    if (animationFrameRef.current) {
      cancelAnimationFrame(animationFrameRef.current);
    }
    const canvas = canvasRef.current;
    if (canvas) {
      canvas.removeEventListener('click', handleDesktopClick as EventListener);
      canvas.removeEventListener('mousemove', handleDesktopMouseMove as EventListener);
    }
    document.removeEventListener('keydown', handleDesktopKeyDown);
  };

  const completeObjective = (objectiveId: string) => {
    if (!sessionState) return;

    const newCompleted = [...sessionState.completedObjectives, objectiveId];
    const objective = experiment?.objectives.find((o) => o.id === objectiveId);
    const newScore = sessionState.score + (objective?.points ?? 0);

    setSessionState({
      ...sessionState,
      completedObjectives: newCompleted,
      score: newScore,
    });

    updateSessionMutation.mutate({
      completedObjectives: newCompleted,
      score: newScore,
    });

    // Check if all objectives completed
    if (newCompleted.length === experiment?.objectives.length) {
      handleComplete();
    }
  };

  if (experimentLoading || !experiment) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-900">
        <Spinner size="lg" />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-900 text-white">
      {/* Loading Screen */}
      {isLoading && (
        <div className="absolute inset-0 z-50 flex flex-col items-center justify-center bg-gray-900">
          <div className="text-6xl mb-4">🔬</div>
          <h2 className="text-2xl font-bold mb-4">{experiment.title}</h2>
          <div className="w-64 mb-2">
            <Progress value={loadingProgress} max={100} />
          </div>
          <p className="text-gray-400">Loading experiment assets...</p>
        </div>
      )}

      {/* Main VR Canvas */}
      <canvas
        ref={canvasRef}
        className="w-full h-screen"
        style={{ display: isLoading ? 'none' : 'block' }}
      />

      {/* HUD Overlay */}
      {!isLoading && (
        <>
          {/* Top Bar */}
          <div className="absolute top-0 left-0 right-0 p-4 flex items-center justify-between bg-gradient-to-b from-black/50 to-transparent">
            <div className="flex items-center gap-4">
              <Button variant="ghost" size="sm" onClick={() => setShowExitDialog(true)}>
                ← Exit
              </Button>
              <div>
                <h1 className="font-bold">{experiment.title}</h1>
                <p className="text-sm text-gray-300">{experiment.category}</p>
              </div>
            </div>
            <div className="flex items-center gap-4">
              <Badge variant={isPaused ? 'warning' : 'success'}>
                {isPaused ? 'Paused' : 'Active'}
              </Badge>
              <div className="text-right">
                <div className="text-2xl font-bold">{sessionState?.score ?? 0}</div>
                <div className="text-xs text-gray-400">Points</div>
              </div>
            </div>
          </div>

          {/* Side Panel - Objectives */}
          <div className="absolute top-20 right-4 w-72">
            <Card className="bg-black/70 border-gray-700">
              <CardContent className="p-4">
                <h3 className="font-bold mb-3">🎯 Objectives</h3>
                <div className="space-y-2">
                  {experiment.objectives.map((objective) => {
                    const isCompleted = sessionState?.completedObjectives.includes(objective.id);
                    return (
                      <div
                        key={objective.id}
                        className={`p-2 rounded ${
                          isCompleted
                            ? 'bg-green-900/50 border-green-500'
                            : 'bg-gray-800 border-gray-700'
                        } border`}
                      >
                        <div className="flex items-center justify-between">
                          <span className={isCompleted ? 'line-through text-gray-400' : ''}>
                            {objective.title}
                          </span>
                          <span className="text-xs text-yellow-400">+{objective.points}</span>
                        </div>
                      </div>
                    );
                  })}
                </div>
                <div className="mt-4">
                  <Progress
                    value={(sessionState?.completedObjectives.length ?? 0)}
                    max={experiment.objectives.length}
                  />
                  <p className="text-xs text-gray-400 mt-1">
                    {sessionState?.completedObjectives.length ?? 0} / {experiment.objectives.length} completed
                  </p>
                </div>
              </CardContent>
            </Card>
          </div>

          {/* Bottom Controls */}
          <div className="absolute bottom-4 left-1/2 -translate-x-1/2 flex items-center gap-4">
            <Button variant="outline" onClick={togglePause}>
              {isPaused ? '▶️ Resume' : '⏸️ Pause'}
            </Button>
            {deviceType === 'desktop' && (
              <Button variant="outline" onClick={() => {}}>
                📖 Instructions
              </Button>
            )}
            <Button onClick={handleComplete}>
              ✅ Complete
            </Button>
          </div>

          {/* Device Mode Indicator */}
          <div className="absolute bottom-4 right-4 flex items-center gap-2 text-sm text-gray-400">
            <span>
              {deviceType === 'vr' ? '🥽' : deviceType === 'ar' ? '📱' : '🖥️'}
            </span>
            <span>{deviceType.toUpperCase()} Mode</span>
          </div>
        </>
      )}

      {/* Exit Dialog */}
      <Dialog open={showExitDialog} onOpenChange={setShowExitDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Exit Experiment?</DialogTitle>
          </DialogHeader>
          <p className="text-gray-600 dark:text-gray-400">
            Your progress will be saved. You can resume this experiment later.
          </p>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowExitDialog(false)}>
              Cancel
            </Button>
            <Button variant="destructive" onClick={() => handleExit(false)}>
              Exit Without Saving
            </Button>
            <Button onClick={() => handleExit(true)}>Save & Exit</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Completion Dialog */}
      <Dialog open={showCompletionDialog} onOpenChange={setShowCompletionDialog}>
        <DialogContent className="text-center">
          <DialogHeader>
            <DialogTitle>🎉 Experiment Complete!</DialogTitle>
          </DialogHeader>
          <div className="py-8">
            <div className="text-6xl mb-4">🏆</div>
            <div className="text-4xl font-bold text-purple-600 dark:text-purple-400 mb-2">
              {sessionState?.score ?? 0} Points
            </div>
            <p className="text-gray-600 dark:text-gray-400">
              You completed {sessionState?.completedObjectives.length ?? 0} out of{' '}
              {experiment.objectives.length} objectives
            </p>
          </div>
          <DialogFooter className="justify-center">
            <Button onClick={() => navigate('/vr-labs')}>
              Back to Labs
            </Button>
            <Button variant="outline" onClick={() => navigate(`/vr/session/${experimentId}?device=${deviceType}`)}>
              Try Again
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

export default VrSessionPage;
