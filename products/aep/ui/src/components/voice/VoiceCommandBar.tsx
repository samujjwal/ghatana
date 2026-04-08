/**
 * Voice command bar for AEP operations
 *
 * Provides voice commands for common AEP actions like navigating to pages,
 * triggering pipeline runs, approving HITL items, and more.
 *
 * Designed for cross-product reuse. Can be extracted to @ghatana/voice-ui
 * after validation in AEP and Data Cloud.
 *
 * @doc.type component
 * @doc.purpose Provide voice commands for common AEP actions
 * @doc.layer frontend
 */

import React, { useState, useCallback, useRef } from 'react';
import { Mic, Command, X } from 'lucide-react';
import { useSpeechSynthesis } from '@audio-video/ui';
import { useConsent } from '../privacy/ConsentManager';

/**
 * Voice command intent
 */
export interface VoiceIntent {
  action: 'navigate' | 'trigger' | 'approve' | 'reject' | 'search' | 'cancel';
  target?: string;
  parameters?: Record<string, string>;
}

/**
 * VoiceCommandBar component props
 */
interface VoiceCommandBarProps {
  /**
   * Callback when a voice command is recognized
   */
  onCommand: (intent: VoiceIntent) => void;
  /**
   * Optional: Custom placeholder text
   */
  placeholder?: string;
  /**
   * Optional: Enable keyboard shortcut (Cmd+K)
   */
  enableShortcut?: boolean;
  className?: string;
}

/**
 * Voice command patterns mapping
 */
const COMMAND_PATTERNS: Record<string, RegExp> = {
  navigate: /(?:go to|navigate|open|show)\s+(.+)/i,
  trigger: /(?:trigger|run|start|execute)\s+(.+)/i,
  approve: /(?:approve|accept|confirm)\s+(.+)/i,
  reject: /(?:reject|deny|decline)\s+(.+)/i,
  search: /(?:search|find|look for)\s+(.+)/i,
  cancel: /(?:cancel|stop|abort)/i,
};

/**
 * VoiceCommandBar component
 *
 * Provides a voice command interface that recognizes natural language commands
 * and converts them to structured intents. Includes keyboard shortcut support
 * and consent management.
 */
export const VoiceCommandBar: React.FC<VoiceCommandBarProps> = ({
  onCommand,
  placeholder = 'Say "Go to monitoring" or "Trigger pipeline"',
  enableShortcut = true,
  className = '',
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const [transcript, setTranscript] = useState('');
  const [isListening, setIsListening] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const { speak } = useSpeechSynthesis();
  const { consentGranted: voiceConsent } = useConsent('voice_processing');

  /**
   * Parse transcript into voice intent
   */
  const parseIntent = useCallback((text: string): VoiceIntent | null => {
    const trimmed = text.trim();
    
    for (const [action, pattern] of Object.entries(COMMAND_PATTERNS)) {
      const match = trimmed.match(pattern);
      if (match) {
        return {
          action: action as VoiceIntent['action'],
          target: match[1]?.trim(),
        };
      }
    }
    
    return null;
  }, []);

  /**
   * Handle transcript change
   */
  const handleTranscriptChange = useCallback((text: string) => {
    setTranscript(text);
    
    const intent = parseIntent(text);
    if (intent) {
      speak(`Executing: ${intent.action} ${intent.target || ''}`);
      onCommand(intent);
      setIsOpen(false);
      setTranscript('');
    }
  }, [parseIntent, speak, onCommand]);

  /**
   * Handle keyboard shortcut
   */
  React.useEffect(() => {
    if (!enableShortcut) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        setIsOpen((prev) => !prev);
        if (!isOpen) {
          setTimeout(() => inputRef.current?.focus(), 0);
        }
      }
      if (e.key === 'Escape' && isOpen) {
        setIsOpen(false);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [enableShortcut, isOpen]);

  /**
   * Focus input when opened
   */
  React.useEffect(() => {
    if (isOpen) {
      inputRef.current?.focus();
    }
  }, [isOpen]);

  if (!isOpen) {
    return (
      <button
        onClick={() => setIsOpen(true)}
        className={cn(
          'flex items-center gap-2 px-3 py-1.5',
          'bg-gray-100 dark:bg-gray-800',
          'text-gray-600 dark:text-gray-400',
          'rounded-lg text-sm',
          'hover:bg-gray-200 dark:hover:bg-gray-700',
          'transition-colors',
          className
        )}
        aria-label="Open voice commands (Cmd+K)"
      >
        <Command className="h-4 w-4" />
        <span>Cmd+K</span>
      </button>
    );
  }

  return (
    <div className={cn(
      'fixed inset-0 bg-black/50 flex items-start justify-center pt-24 z-50',
      'animate-in fade-in duration-200'
    )}>
      <div className="bg-white dark:bg-gray-900 rounded-lg shadow-xl max-w-2xl w-full mx-4 p-4">
        <div className="flex items-center gap-3 mb-4">
          <Mic className={cn(
            'h-5 w-5',
            isListening ? 'text-red-500 animate-pulse' : 'text-gray-400'
          )} />
          <input
            ref={inputRef}
            type="text"
            value={transcript}
            onChange={(e) => setTranscript(e.target.value)}
            placeholder={placeholder}
            className="flex-1 bg-transparent border-none outline-none text-gray-900 dark:text-white placeholder:text-gray-500"
          />
          <button
            onClick={() => setIsOpen(false)}
            className="p-1 hover:bg-gray-100 dark:hover:bg-gray-800 rounded transition-colors"
            aria-label="Close"
          >
            <X className="h-4 w-4 text-gray-500" />
          </button>
        </div>
        
        <div className="text-xs text-gray-500 dark:text-gray-400 space-y-1">
          <p className="font-medium">Available commands:</p>
          <ul className="space-y-0.5 ml-4">
            <li>"Go to monitoring" - Navigate to monitoring dashboard</li>
            <li>"Trigger pipeline" - Start a pipeline run</li>
            <li>"Approve item" - Approve a HITL review item</li>
            <li>"Reject item" - Reject a HITL review item</li>
            <li>"Search [query]" - Search for items</li>
            <li>"Cancel" - Cancel current operation</li>
          </ul>
        </div>
      </div>
    </div>
  );
};

/**
 * Utility function to combine class names
 */
function cn(...classes: (string | undefined)[]): string {
  return classes.filter(Boolean).join(' ');
}
