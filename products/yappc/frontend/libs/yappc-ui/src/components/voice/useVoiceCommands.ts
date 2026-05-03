/**
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC UI - Voice Command Handler
 *
 * Voice commands are currently disabled because the required backend speech
 * endpoints (/api/v1/speech/stt, /api/v1/tts) do not exist.
 * Activation requires integration with products/audio-video speech services.
 */

import { useState, useCallback, useRef, useEffect } from 'react';

export type VoiceIntent =
  | 'create_project'
  | 'open_project'
  | 'advance_stage'
  | 'go_back_stage'
  | 'create_task'
  | 'assign_task'
  | 'complete_task'
  | 'show_tasks'
  | 'show_metrics'
  | 'help'
  | 'cancel'
  | 'confirm'
  | 'unknown';

export interface VoiceCommand {
  intent: VoiceIntent;
  entities: Record<string, string>;
  rawText: string;
  confidence: number;
}

export interface VoiceHandlerConfig {
  /** STT service endpoint */
  sttEndpoint: string;
  /** TTS service endpoint */
  ttsEndpoint: string;
  /** Language code (e.g., 'en-US') */
  language: string;
  /** Enable voice feedback via TTS */
  enableFeedback: boolean;
  /** Wake word to activate voice commands */
  wakeWord: string;
  /** Silence threshold to stop listening (ms) */
  silenceThreshold: number;
}

const DEFAULT_CONFIG: VoiceHandlerConfig = {
  sttEndpoint: '/api/v1/speech/stt',
  ttsEndpoint: '/api/v1/speech/tts',
  language: 'en-US',
  enableFeedback: true,
  wakeWord: 'yappc',
  silenceThreshold: 2000,
};

// Intent patterns for matching voice commands
const INTENT_PATTERNS: Record<VoiceIntent, RegExp[]> = {
  create_project: [
    /create (?:new )?project (?:called |named )?(.+)/i,
    /start (?:new )?project (.+)/i,
    /new project (.+)/i,
  ],
  open_project: [
    /open project (.+)/i,
    /show project (.+)/i,
    /go to project (.+)/i,
  ],
  advance_stage: [
    /advance (?:to )?next stage/i,
    /move (?:to )?next phase/i,
    /proceed (?:to )?next/i,
    /continue/i,
  ],
  go_back_stage: [
    /go back/i,
    /return to previous/i,
    /back to (?:the )?previous stage/i,
  ],
  create_task: [
    /create (?:new )?task (?:called |named )?(.+)/i,
    /add task (.+)/i,
    /new task (.+)/i,
  ],
  assign_task: [/assign (?:task )?(.+?) to (.+)/i, /give (.+?) to (.+)/i],
  complete_task: [
    /complete task (.+)/i,
    /mark (?:task )?(.+?) as done/i,
    /finish (?:task )?(.+)/i,
  ],
  show_tasks: [
    /show (?:my )?tasks/i,
    /list tasks/i,
    /what are (?:my )?tasks/i,
    /view tasks/i,
  ],
  show_metrics: [
    /show metrics/i,
    /project metrics/i,
    /show statistics/i,
    /how is the project doing/i,
  ],
  help: [
    /help/i,
    /what can i say/i,
    /voice commands/i,
    /what are (?:the )?commands/i,
  ],
  cancel: [/cancel/i, /stop/i, /nevermind/i, /abort/i],
  confirm: [/yes/i, /confirm/i, /proceed/i, /ok/i, /go ahead/i],
  unknown: [],
};

/**
 * Parses voice input to extract intent and entities
 */
function parseIntent(text: string): VoiceCommand {
  const normalizedText = text.toLowerCase().trim();

  for (const [intent, patterns] of Object.entries(INTENT_PATTERNS)) {
    for (const pattern of patterns) {
      const match = normalizedText.match(pattern);
      if (match) {
        const entities: Record<string, string> = {};

        // Extract entities based on intent
        if (intent === 'create_project' || intent === 'open_project') {
          entities.projectName = match[1]?.trim();
        } else if (intent === 'create_task') {
          entities.taskTitle = match[1]?.trim();
        } else if (intent === 'assign_task') {
          entities.taskName = match[1]?.trim();
          entities.assignee = match[2]?.trim();
        } else if (intent === 'complete_task') {
          entities.taskName = match[1]?.trim();
        }

        return {
          intent: intent as VoiceIntent,
          entities,
          rawText: text,
          confidence: 0.9,
        };
      }
    }
  }

  return {
    intent: 'unknown',
    entities: {},
    rawText: text,
    confidence: 0,
  };
}

/**
 * useVoiceCommands Hook
 *
 * Provides voice command functionality using existing STT/TTS services.
 * No speech processing duplication - delegates to audio-video gRPC services.
 *
 * @example
 * ```tsx
 * const {
 *   isListening,
 *   startListening,
 *   stopListening,
 *   lastCommand,
 *   feedback
 * } = useVoiceCommands({
 *   onCommand: (cmd) => handleVoiceCommand(cmd)
 * });
 * ```
 */
/**
 * Voice commands hook - currently disabled pending backend integration.
 * @deprecated Voice functionality requires products/audio-video service integration
 */
export function useVoiceCommands(options: {
  onCommand: (command: VoiceCommand) => void;
  onError?: (error: Error) => void;
  config?: Partial<VoiceHandlerConfig>;
}): {
  isListening: boolean;
  isProcessing: boolean;
  feedback: string;
  lastCommand: VoiceCommand | null;
  startListening: () => Promise<void>;
  stopListening: () => void;
} {
  const { onError } = options;

  // Voice commands are disabled - backend endpoints do not exist
  const error = new Error(
    'Voice commands are not available. ' +
    'Required speech endpoints (/api/v1/speech/stt, /api/v1/speech/tts) are not implemented. ' +
    'This feature requires integration with products/audio-video speech services.'
  );

  // Report error immediately if handler provided
  if (onError) {
    onError(error);
  }

  // Return disabled state
  return {
    isListening: false,
    isProcessing: false,
    feedback: '',
    lastCommand: null,
    startListening: async () => {
      console.warn('[VoiceCommands] Attempted to start listening but voice commands are disabled:', error.message);
      // Gracefully no-op instead of throwing to prevent UI crashes
    },
    stopListening: () => {
      // No-op when disabled
    },
  };
}

/**
 * Voice command help text - disabled pending backend integration
 * @deprecated Voice functionality requires products/audio-video service integration
 */
export const VOICE_COMMAND_HELP = `
Voice Commands (Not Available):
Voice command functionality is currently disabled.

Required: Integration with products/audio-video speech services
Endpoints needed:
- POST /api/v1/speech/stt (Speech-to-Text)
- POST /api/v1/speech/tts (Text-to-Speech)
`;
