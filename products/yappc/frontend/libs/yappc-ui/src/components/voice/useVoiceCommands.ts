/**
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC UI - Voice Command Handler
 * 
 * Integrates with audio-video speech services (STT/TTS) to provide
 * voice control capabilities for YAPPC without duplicating speech infrastructure.
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
  assign_task: [
    /assign (?:task )?(.+?) to (.+)/i,
    /give (.+?) to (.+)/i,
  ],
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
  cancel: [
    /cancel/i,
    /stop/i,
    /nevermind/i,
    /abort/i,
  ],
  confirm: [
    /yes/i,
    /confirm/i,
    /proceed/i,
    /ok/i,
    /go ahead/i,
  ],
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
export function useVoiceCommands(options: {
  onCommand: (command: VoiceCommand) => void;
  onError?: (error: Error) => void;
  config?: Partial<VoiceHandlerConfig>;
}) {
  const { onCommand, onError, config = {} } = options;
  const mergedConfig = { ...DEFAULT_CONFIG, ...config };
  
  const [isListening, setIsListening] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);
  const [feedback, setFeedback] = useState<string>('');
  const [lastCommand, setLastCommand] = useState<VoiceCommand | null>(null);
  
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const audioChunksRef = useRef<Blob[]>([]);
  const silenceTimerRef = useRef<NodeJS.Timeout | null>(null);
  const wakeWordDetectedRef = useRef(false);

  // Request microphone permission
  const requestMicrophoneAccess = useCallback(async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      return stream;
    } catch (err) {
      throw new Error('Microphone access denied');
    }
  }, []);

  // Send audio to STT service
  const transcribeAudio = useCallback(async (audioBlob: Blob): Promise<string> => {
    const formData = new FormData();
    formData.append('audio', audioBlob, 'voice-command.wav');
    formData.append('language', mergedConfig.language);
    
    const response = await fetch(mergedConfig.sttEndpoint, {
      method: 'POST',
      body: formData,
    });
    
    if (!response.ok) {
      throw new Error('STT service error');
    }
    
    const data = await response.json();
    return data.text;
  }, [mergedConfig.sttEndpoint, mergedConfig.language]);

  // Speak feedback using TTS service
  const speakFeedback = useCallback(async (text: string) => {
    if (!mergedConfig.enableFeedback) return;
    
    setFeedback(text);
    
    try {
      const response = await fetch(mergedConfig.ttsEndpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          text,
          language: mergedConfig.language,
        }),
      });
      
      if (!response.ok) return;
      
      const audioBlob = await response.blob();
      const audioUrl = URL.createObjectURL(audioBlob);
      const audio = new Audio(audioUrl);
      await audio.play();
      
      audio.onended = () => {
        URL.revokeObjectURL(audioUrl);
        setFeedback('');
      };
    } catch {
      // Silent fail - feedback is optional
      setFeedback('');
    }
  }, [mergedConfig.ttsEndpoint, mergedConfig.language, mergedConfig.enableFeedback]);

  // Process recorded audio
  const processAudio = useCallback(async () => {
    if (audioChunksRef.current.length === 0) return;
    
    setIsProcessing(true);
    
    try {
      const audioBlob = new Blob(audioChunksRef.current, { type: 'audio/wav' });
      audioChunksRef.current = [];
      
      const transcribedText = await transcribeAudio(audioBlob);
      
      if (!transcribedText) {
        await speakFeedback("I didn't catch that. Could you try again?");
        return;
      }
      
      const command = parseIntent(transcribedText);
      setLastCommand(command);
      
      if (command.intent === 'unknown') {
        await speakFeedback(`I heard: "${transcribedText}". I'm not sure what to do with that.`);
      } else {
        await speakFeedback(`Processing: ${command.intent.replace('_', ' ')}`);
        onCommand(command);
      }
    } catch (err) {
      onError?.(err instanceof Error ? err : new Error('Processing failed'));
    } finally {
      setIsProcessing(false);
    }
  }, [transcribeAudio, speakFeedback, onCommand, onError]);

  // Start listening for voice commands
  const startListening = useCallback(async () => {
    if (isListening) return;
    
    try {
      const stream = await requestMicrophoneAccess();
      
      const mediaRecorder = new MediaRecorder(stream);
      mediaRecorderRef.current = mediaRecorder;
      audioChunksRef.current = [];
      
      mediaRecorder.ondataavailable = (event) => {
        if (event.data.size > 0) {
          audioChunksRef.current.push(event.data);
        }
      };
      
      mediaRecorder.onstop = () => {
        if (wakeWordDetectedRef.current) {
          processAudio();
        }
        stream.getTracks().forEach(track => track.stop());
      };
      
      mediaRecorder.start(100); // Collect data every 100ms
      setIsListening(true);
      wakeWordDetectedRef.current = false;
      
      // For now, auto-detect wake word in transcription
      // In production, would use wake word detection before starting full transcription
      wakeWordDetectedRef.current = true;
      
      // Auto-stop after silence threshold
      silenceTimerRef.current = setTimeout(() => {
        stopListening();
      }, mergedConfig.silenceThreshold + 5000);
      
    } catch (err) {
      onError?.(err instanceof Error ? err : new Error('Failed to start listening'));
    }
  }, [isListening, requestMicrophoneAccess, processAudio, mergedConfig.silenceThreshold, onError]);

  // Stop listening
  const stopListening = useCallback(() => {
    if (silenceTimerRef.current) {
      clearTimeout(silenceTimerRef.current);
      silenceTimerRef.current = null;
    }
    
    mediaRecorderRef.current?.stop();
    setIsListening(false);
  }, []);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      stopListening();
      if (silenceTimerRef.current) {
        clearTimeout(silenceTimerRef.current);
      }
    };
  }, [stopListening]);

  return {
    isListening,
    isProcessing,
    feedback,
    lastCommand,
    startListening,
    stopListening,
  };
}

/**
 * Voice command help text
 */
export const VOICE_COMMAND_HELP = `
Voice Commands:
- "Create project [name]" - Create a new project
- "Open project [name]" - Open an existing project
- "Advance to next stage" - Move to next lifecycle stage
- "Go back" - Return to previous stage
- "Create task [title]" - Add a new task
- "Assign [task] to [agent]" - Assign task to agent
- "Complete task [name]" - Mark task as done
- "Show tasks" - List all tasks
- "Show metrics" - View project metrics
- "Help" - Show this help
- "Cancel" - Cancel current operation
`;
