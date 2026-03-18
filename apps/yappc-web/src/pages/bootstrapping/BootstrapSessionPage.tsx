/**
 * Bootstrap Session Page
 *
 * @description AI-powered interactive session for project bootstrapping.
 * Features split-pane layout with chat and live canvas preview.
 */

import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useLocation, useNavigate } from 'react-router';
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import { motion, AnimatePresence } from 'framer-motion';
import {
  PanelLeft,
  MessageSquare,
  Code2,
  Download,
  Share2,
  RotateCcw,
  CheckCircle2,
  Loader2,
  ArrowRight,
} from 'lucide-react';

import { cn } from '../../utils/cn';
import { AIChatInterface, ValidationPanel } from '../../components/placeholders';
import { CollaborativeCanvas } from '../../components/CollaborativeCanvas';
import {
  validationStateAtom,
  bootstrapSessionAtom,
  conversationHistoryAtom,
  aiAgentStateAtom,
  canvasStateAtom,
} from '../../state/atoms';
import { ROUTES } from '../../router/paths';

// =============================================================================
// Types
// =============================================================================

type ViewMode = 'split' | 'chat' | 'canvas' | 'validation';

interface SessionState {
  phase: 'gathering' | 'designing' | 'validating' | 'ready';
  progress: number;
  currentStep: string;
}

// =============================================================================
// Bootstrap Session Page Component
// =============================================================================

const BootstrapSessionPage: React.FC = () => {
  const { projectId, sessionId } = useParams<{ projectId: string; sessionId: string }>();
  const location = useLocation();
  const navigate = useNavigate();

  // State
  const [viewMode, setViewMode] = useState<ViewMode>('split');
  const [sessionState, setSessionState] = useState<SessionState>({
    phase: 'gathering',
    progress: 0,
    currentStep: 'Gathering requirements',
  });

  // Jotai state
  const agentState = useAtomValue(aiAgentStateAtom);
  const canvasState = useAtomValue(canvasStateAtom);
  const validation = useAtomValue(validationStateAtom);
  const [session, setSession] = useAtom(bootstrapSessionAtom);
  const setConversation = useSetAtom(conversationHistoryAtom);

  // Initialize session from location state (from StartProjectPage)
  useEffect(() => {
    const locationState = location.state as {
      description?: string;
      templateId?: string;
    } | null;

    if (locationState?.description || locationState?.templateId) {
      // Auto-start with the provided context
      setSession({
        id: sessionId || 'new-session',
        projectName: 'New Project',
        description: locationState.description || '',
        phase: 'enter',
        status: 'active',
        progress: 0,
        confidenceScore: 0,
        questionsAnswered: 0,
        totalQuestions: 10,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
        ownerId: 'current-user',
        collaboratorIds: [],
        features: [],
        techStack: [],
        timeline: {
          mvpWeeks: 4,
          totalWeeks: 12,
          phases: [],
          milestones: [],
        },
      });

      // Add initial AI message based on context
      if (locationState.description) {
        setConversation([
          {
            id: '1',
            role: 'user',
            content: locationState.description,
            timestamp: Date.now(),
          },
          {
            id: '2',
            role: 'assistant',
            content: `I understand you want to build: "${locationState.description}"\n\nLet me help you design the architecture. I'll ask a few clarifying questions to make sure we build exactly what you need.\n\n**First, let's define the scope:**\n1. What's the primary use case for this application?\n2. Who are the main users?\n3. Do you have any specific technology preferences?`,
            timestamp: Date.now(),
          },
        ]);
        setSessionState((prev) => ({ ...prev, phase: 'designing', progress: 25 }));
      } else if (locationState.templateId) {
        setConversation([
          {
            id: '1',
            role: 'assistant',
            content: `Great choice! I'll help you customize the **${locationState.templateId}** template for your needs.\n\nLet me know:\n1. What's the main purpose of your project?\n2. Any specific features you want to add or remove?\n3. What integrations do you need?`,
            timestamp: Date.now(),
          },
        ]);
        setSessionState((prev) => ({ ...prev, phase: 'gathering', progress: 10 }));
      }
    }
  }, [location.state, sessionId, setConversation, setSession]);

  // Phase indicator
  const phases = [
    { id: 'gathering', label: 'Requirements', icon: '📝' },
    { id: 'designing', label: 'Architecture', icon: '🏗️' },
    { id: 'validating', label: 'Validation', icon: '✅' },
    { id: 'ready', label: 'Ready', icon: '🚀' },
  ];

  const handleProceedToSetup = useCallback(() => {
    navigate(ROUTES.setup.root(projectId || ''));
  }, [navigate, projectId]);

  const handleExport = useCallback(() => {
    // Export project configuration
    const config = {
      session,
      nodes: canvasState,
      validation: validation,
    };
    const blob = new Blob([JSON.stringify(config, null, 2)], {
      type: 'application/json',
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `yappc-project-${sessionId || 'new'}.json`;
    a.click();
    URL.revokeObjectURL(url);
  }, [session, canvasState, validation, sessionId]);

  return (
    <div className="h-[calc(100vh-64px)] flex flex-col bg-zinc-950">
      {/* Top Bar */}
      <div className="h-14 border-b border-zinc-800 flex items-center justify-between px-4 bg-zinc-900/50">
        {/* Left: Phase Progress */}
        <div className="flex items-center gap-4">
          {phases.map((phase, index) => (
            <React.Fragment key={phase.id}>
              <div
                className={cn(
                  'flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm',
                  sessionState.phase === phase.id
                    ? 'bg-violet-500/20 text-violet-400 border border-violet-500/30'
                    : phases.findIndex((p) => p.id === sessionState.phase) > index
                      ? 'text-emerald-400'
                      : 'text-zinc-500'
                )}
              >
                <span>{phase.icon}</span>
                <span className="hidden sm:inline">{phase.label}</span>
              </div>
              {index < phases.length - 1 && (
                <div
                  className={cn(
                    'w-8 h-0.5 rounded',
                    phases.findIndex((p) => p.id === sessionState.phase) > index
                      ? 'bg-emerald-500'
                      : 'bg-zinc-700'
                  )}
                />
              )}
            </React.Fragment>
          ))}
        </div>

        {/* Center: View Mode Toggle */}
        <div className="flex items-center gap-1 p-1 bg-zinc-800 rounded-lg">
          {[
            { id: 'split', icon: <PanelLeft className="w-4 h-4" />, label: 'Split' },
            { id: 'chat', icon: <Code2 className="w-4 h-4" />, label: 'Chat' },
            { id: 'canvas', icon: <MessageSquare className="w-4 h-4" />, label: 'Canvas' },
            { id: 'validation', icon: <CheckCircle2 className="w-4 h-4" />, label: 'Validation' },
          ].map((view) => (
            <button
              key={view.id}
              onClick={() => setViewMode(view.id as ViewMode)}
              className={cn(
                'flex items-center gap-2 px-3 py-1.5 rounded text-sm transition-colors',
                viewMode === view.id
                  ? 'bg-violet-500 text-white'
                  : 'text-zinc-400 hover:text-white'
              )}
              title={view.label}
            >
              {view.icon}
              <span className="hidden md:inline">{view.label}</span>
            </button>
          ))}
        </div>

        {/* Right: Actions */}
        <div className="flex items-center gap-2">
          <button
            onClick={handleExport}
            className="p-2 rounded-lg hover:bg-zinc-800 text-zinc-400 hover:text-white transition-colors"
            title="Export Configuration"
          >
            <Download className="w-4 h-4" />
          </button>
          <button
            className="p-2 rounded-lg hover:bg-zinc-800 text-zinc-400 hover:text-white transition-colors"
            title="Share"
          >
            <Share2 className="w-4 h-4" />
          </button>
          <button
            className="p-2 rounded-lg hover:bg-zinc-800 text-zinc-400 hover:text-white transition-colors"
            title="Reset Session"
          >
            <RotateCcw className="w-4 h-4" />
          </button>

          {sessionState.phase === 'ready' && (
            <button
              onClick={handleProceedToSetup}
              className={cn(
                'flex items-center gap-2 px-4 py-2 rounded-lg font-medium text-sm',
                'bg-emerald-500 text-white hover:bg-emerald-600 transition-colors'
              )}
            >
              Proceed to Setup
              <ArrowRight className="w-4 h-4" />
            </button>
          )}
        </div>
      </div>

      {/* Main Content */}
      <div className="flex-1 flex overflow-hidden">
        {/* Chat Panel */}
        <AnimatePresence mode="popLayout">
          {(viewMode === 'split' || viewMode === 'chat') && (
            <motion.div
              initial={{ width: 0, opacity: 0 }}
              animate={{
                width: viewMode === 'chat' ? '100%' : '50%',
                opacity: 1,
              }}
              exit={{ width: 0, opacity: 0 }}
              transition={{ duration: 0.2 }}
              className="h-full border-r border-zinc-800 overflow-hidden"
            >
              <AIChatInterface
                messages={[]}
                onSendMessage={(message: string) => {
                  console.log('Send message:', message);
                }}
                isLoading={agentState.status === 'thinking'}
              />
            </motion.div>
          )}
        </AnimatePresence>

        {/* Canvas Panel */}
        <AnimatePresence mode="popLayout">
          {(viewMode === 'split' || viewMode === 'canvas') && (
            <motion.div
              initial={{ width: 0, opacity: 0 }}
              animate={{
                width: viewMode === 'canvas' ? '100%' : '50%',
                opacity: 1,
              }}
              exit={{ width: 0, opacity: 0 }}
              transition={{ duration: 0.2 }}
              className="h-full overflow-hidden"
            >
              <CollaborativeCanvas
                projectId={projectId || ''}
                readOnly={agentState.isProcessing || false}
              />
            </motion.div>
          )}
        </AnimatePresence>

        {/* Validation Panel */}
        <AnimatePresence mode="popLayout">
          {viewMode === 'validation' && (
            <motion.div
              initial={{ width: 0, opacity: 0 }}
              animate={{ width: '100%', opacity: 1 }}
              exit={{ width: 0, opacity: 0 }}
              transition={{ duration: 0.2 }}
              className="h-full overflow-hidden"
            >
              <ValidationPanel
                issues={(validation?.errors || []).map((err, idx) => ({ 
                  id: `error-${idx}`, 
                  severity: 'error', 
                  message: err 
                }))}
                onResolve={(_issueId: string) => {
                  // Handle auto-fix
                }}
                onIgnore={(_issueId: string) => {
                  // Ignore issue
                }}
              />
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {/* Bottom Status Bar */}
      <div className="h-8 border-t border-zinc-800 flex items-center justify-between px-4 bg-zinc-900/50">
        <div className="flex items-center gap-4 text-xs text-zinc-500">
          <span>
            Session: {sessionId || 'New'}
          </span>
          <span>
            {agentState.isProcessing ? (
              <span className="flex items-center gap-1 text-violet-400">
                <Loader2 className="w-3 h-3 animate-spin" />
                Processing...
              </span>
            ) : (
              <span className="text-emerald-400">Ready</span>
            )}
          </span>
        </div>

        <div className="flex items-center gap-4 text-xs text-zinc-500">
          <span>
            Nodes: {canvasState?.nodes?.length || 0}
          </span>
          <span>
            Issues: {validation?.errors?.length || 0}
          </span>
          <span>
            Progress: {sessionState.progress}%
          </span>
        </div>
      </div>
    </div>
  );
};

export default BootstrapSessionPage;
