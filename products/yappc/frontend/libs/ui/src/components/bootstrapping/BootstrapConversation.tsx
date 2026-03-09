/**
 * Bootstrap Conversation Panel
 *
 * @description Wrapper around AIChatInterface specifically configured for
 * bootstrapping phase conversations. Integrates with bootstrapping state atoms
 * and provides phase-specific question handling.
 *
 * @doc.type component
 * @doc.purpose Bootstrapping conversation UI
 * @doc.layer presentation
 * @doc.phase bootstrapping
 * @doc.reuses AIChatInterface
 */

import React, { useCallback, useMemo } from 'react';
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import { motion } from 'framer-motion';
import {
  Sparkles,
  FileText,
  Upload,
  Mic,
  Layout,
  ChevronDown,
  ChevronUp,
} from 'lucide-react';

import { cn } from '@ghatana/ui';
import { Button } from '@ghatana/ui';
import { Badge } from '@ghatana/ui';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@ghatana/yappc-ui';

// Reuse existing chat component
import { AIChatInterface } from '../chat/AIChatInterface';

// Import bootstrapping state
import type {
  BootstrapPhase,
  Question,
  InputMode,
  AgentStatus,
} from '@ghatana/yappc-canvas';
import {
  currentPhaseAtom,
  conversationHistoryAtom,
  currentQuestionAtom,
  pendingAnswerAtom,
  agentStatusAtom,
  agentStatusMessageAtom,
  inputModeAtom,
  confidenceScoreAtom,
} from '@ghatana/yappc-canvas';

// =============================================================================
// Types
// =============================================================================

export interface BootstrapConversationProps {
  /** Session ID for the bootstrap session */
  sessionId: string;
  /** Called when user sends a message */
  onSendMessage: (content: string, attachments?: File[]) => Promise<void>;
  /** Called when answer is submitted for current question */
  onAnswerSubmit?: (questionId: string, answer: string) => Promise<void>;
  /** Called when input mode changes */
  onInputModeChange?: (mode: InputMode) => void;
  /** Called when user wants to skip current question */
  onSkipQuestion?: () => void;
  /** Enable document upload */
  allowDocumentUpload?: boolean;
  /** Enable voice input */
  allowVoiceInput?: boolean;
  /** Enable template selection */
  allowTemplates?: boolean;
  /** Show current question prominently */
  showActiveQuestion?: boolean;
  /** Collapse/expand conversation history */
  collapsible?: boolean;
  /** Initial collapsed state */
  defaultCollapsed?: boolean;
  /** Additional CSS classes */
  className?: string;
}

// =============================================================================
// Input Mode Selector
// =============================================================================

interface InputModeSelectorProps {
  currentMode: InputMode;
  onModeChange: (mode: InputMode) => void;
  allowUpload: boolean;
  allowVoice: boolean;
  allowTemplates: boolean;
}

const INPUT_MODES: Array<{
  id: InputMode;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  description: string;
}> = [
  {
    id: 'text',
    label: 'Text',
    icon: FileText,
    description: 'Type your response',
  },
  {
    id: 'upload',
    label: 'Upload',
    icon: Upload,
    description: 'Upload documents for analysis',
  },
  {
    id: 'voice',
    label: 'Voice',
    icon: Mic,
    description: 'Speak your response',
  },
  {
    id: 'template',
    label: 'Template',
    icon: Layout,
    description: 'Start from a template',
  },
];

const InputModeSelector: React.FC<InputModeSelectorProps> = ({
  currentMode,
  onModeChange,
  allowUpload,
  allowVoice,
  allowTemplates,
}) => {
  const availableModes = INPUT_MODES.filter((mode) => {
    if (mode.id === 'upload' && !allowUpload) return false;
    if (mode.id === 'voice' && !allowVoice) return false;
    if (mode.id === 'template' && !allowTemplates) return false;
    return true;
  });

  const currentModeConfig = INPUT_MODES.find((m) => m.id === currentMode);
  const Icon = currentModeConfig?.icon || FileText;

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="outline" size="sm" className="gap-2">
          <Icon className="h-4 w-4" />
          {currentModeConfig?.label}
          <ChevronDown className="h-3 w-3 opacity-50" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start">
        {availableModes.map((mode) => (
          <DropdownMenuItem
            key={mode.id}
            onClick={() => onModeChange(mode.id)}
            className={cn(currentMode === mode.id && 'bg-zinc-800')}
          >
            <mode.icon className="mr-2 h-4 w-4" />
            <div>
              <div className="font-medium">{mode.label}</div>
              <div className="text-xs text-zinc-400">{mode.description}</div>
            </div>
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
};

// =============================================================================
// Active Question Display
// =============================================================================

interface ActiveQuestionProps {
  question: Question;
  phase: BootstrapPhase;
  onSkip?: () => void;
}

const PHASE_LABELS: Record<BootstrapPhase, string> = {
  enter: 'Getting Started',
  explore: 'Exploring Requirements',
  refine: 'Refining Details',
  validate: 'Validating Blueprint',
  complete: 'Ready to Build',
};

const ActiveQuestion: React.FC<ActiveQuestionProps> = ({
  question,
  phase,
  onSkip,
}) => {
  return (
    <motion.div
      initial={{ opacity: 0, y: -10 }}
      animate={{ opacity: 1, y: 0 }}
      className="rounded-lg border border-primary-500/30 bg-primary-500/5 p-4"
    >
      <div className="mb-2 flex items-center justify-between">
        <Badge variant="outline" className="text-primary-400">
          <Sparkles className="mr-1 h-3 w-3" />
          {PHASE_LABELS[phase]}
        </Badge>
        {!question.required && onSkip && (
          <Button variant="ghost" size="sm" onClick={onSkip}>
            Skip
          </Button>
        )}
      </div>
      <p className="text-sm font-medium text-zinc-100">{question.text}</p>
      {question.hint && (
        <p className="mt-1 text-xs text-zinc-400">{question.hint}</p>
      )}

      {/* Question options for choice types */}
      {question.options && question.options.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-2">
          {question.options.map((option) => (
            <Badge
              key={option.id}
              variant="secondary"
              className="cursor-pointer hover:bg-zinc-700"
            >
              {option.icon && <span className="mr-1">{option.icon}</span>}
              {option.label}
            </Badge>
          ))}
        </div>
      )}
    </motion.div>
  );
};

// =============================================================================
// Agent Status Display
// =============================================================================

interface AgentStatusProps {
  status: AgentStatus;
  message: string;
}

const AgentStatusDisplay: React.FC<AgentStatusProps> = ({ status, message }) => {
  if (status === 'idle') return null;

  const statusConfig: Record<
    AgentStatus,
    { color: string; animation?: string }
  > = {
    idle: { color: 'text-zinc-400' },
    thinking: { color: 'text-yellow-400', animation: 'animate-pulse' },
    typing: { color: 'text-blue-400', animation: 'animate-pulse' },
    waiting: { color: 'text-zinc-400' },
    error: { color: 'text-red-400' },
  };

  const config = statusConfig[status];

  return (
    <motion.div
      initial={{ opacity: 0, height: 0 }}
      animate={{ opacity: 1, height: 'auto' }}
      exit={{ opacity: 0, height: 0 }}
      className={cn('flex items-center gap-2 px-4 py-2 text-sm', config.color)}
    >
      <Sparkles className={cn('h-4 w-4', config.animation)} />
      <span>{message || `AI is ${status}...`}</span>
    </motion.div>
  );
};

// =============================================================================
// Main Component
// =============================================================================

export const BootstrapConversation: React.FC<BootstrapConversationProps> = ({
  sessionId,
  onSendMessage,
  onAnswerSubmit,
  onInputModeChange,
  onSkipQuestion,
  allowDocumentUpload = true,
  allowVoiceInput = true,
  allowTemplates = true,
  showActiveQuestion = true,
  collapsible = false,
  defaultCollapsed = false,
  className,
}) => {
  // State from atoms
  const currentPhase = useAtomValue(currentPhaseAtom);
  const currentQuestion = useAtomValue(currentQuestionAtom);
  const agentStatus = useAtomValue(agentStatusAtom);
  const agentMessage = useAtomValue(agentStatusMessageAtom);
  const confidenceScore = useAtomValue(confidenceScoreAtom);
  const [inputMode, setInputMode] = useAtom(inputModeAtom);

  const [isCollapsed, setIsCollapsed] = React.useState(defaultCollapsed);

  // Handle input mode change
  const handleInputModeChange = useCallback(
    (mode: InputMode) => {
      setInputMode(mode);
      onInputModeChange?.(mode);
    },
    [setInputMode, onInputModeChange]
  );

  // Handle message send with answer tracking
  const handleSendMessage = useCallback(
    async (content: string, attachments?: File[]) => {
      // If there's an active question, submit as answer
      if (currentQuestion && onAnswerSubmit) {
        await onAnswerSubmit(currentQuestion.id, content);
      }
      // Also send through standard message handler
      await onSendMessage(content, attachments);
    },
    [currentQuestion, onAnswerSubmit, onSendMessage]
  );

  return (
    <div className={cn('flex h-full flex-col', className)}>
      {/* Collapsible Header */}
      {collapsible && (
        <button
          onClick={() => setIsCollapsed(!isCollapsed)}
          className="flex items-center justify-between border-b border-zinc-800 px-4 py-3 hover:bg-zinc-800/50"
        >
          <div className="flex items-center gap-2">
            <Sparkles className="h-4 w-4 text-primary-400" />
            <span className="font-medium">AI Assistant</span>
            {confidenceScore > 0 && (
              <Badge variant="outline" className="text-xs">
                {confidenceScore}% confidence
              </Badge>
            )}
          </div>
          {isCollapsed ? (
            <ChevronDown className="h-4 w-4 text-zinc-400" />
          ) : (
            <ChevronUp className="h-4 w-4 text-zinc-400" />
          )}
        </button>
      )}

      {/* Main Content */}
      {!isCollapsed && (
        <>
          {/* Active Question */}
          {showActiveQuestion && currentQuestion && (
            <div className="border-b border-zinc-800 p-4">
              <ActiveQuestion
                question={currentQuestion}
                phase={currentPhase}
                onSkip={onSkipQuestion}
              />
            </div>
          )}

          {/* Agent Status */}
          <AgentStatusDisplay status={agentStatus} message={agentMessage} />

          {/* Chat Interface - REUSING EXISTING COMPONENT */}
          <div className="flex-1 overflow-hidden">
            <AIChatInterface
              sessionId={sessionId}
              onSendMessage={handleSendMessage}
              showVoiceInput={allowVoiceInput && inputMode === 'voice'}
              placeholder={
                currentQuestion
                  ? 'Type your answer...'
                  : 'Describe your project idea...'
              }
            />
          </div>

          {/* Input Mode Selector */}
          <div className="border-t border-zinc-800 p-2">
            <InputModeSelector
              currentMode={inputMode}
              onModeChange={handleInputModeChange}
              allowUpload={allowDocumentUpload}
              allowVoice={allowVoiceInput}
              allowTemplates={allowTemplates}
            />
          </div>
        </>
      )}
    </div>
  );
};

BootstrapConversation.displayName = 'BootstrapConversation';

export default BootstrapConversation;
