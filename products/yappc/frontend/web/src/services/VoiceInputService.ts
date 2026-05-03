/**
 * VoiceInputService
 * 
 * Provides speech-to-text functionality for voice commands.
 * Uses Web Speech API with fallback support.
 * 
 * @doc.type service
 * @doc.purpose Voice input handling
 * @doc.layer product
 * @doc.pattern Service
 */

import { logger } from '../utils/Logger';

export type VoiceInputStatus = 'idle' | 'listening' | 'processing' | 'error';

export interface VoiceInputResult {
  transcript: string;
  confidence: number;
  isFinal: boolean;
}

export interface VoiceInputOptions {
  language?: string;
  continuous?: boolean;
  interimResults?: boolean;
  maxAlternatives?: number;
  onResult?: (result: VoiceInputResult) => void;
  onError?: (error: string) => void;
  onStatusChange?: (status: VoiceInputStatus) => void;
}

interface SpeechRecognitionResultEventLike {
  results: Array<{
    0: { transcript: string; confidence: number };
    isFinal: boolean;
  }>;
}

interface SpeechRecognitionErrorEventLike {
  error: string;
}

// Define a minimal SpeechRecognition interface for type safety
interface SpeechRecognitionInstance {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  maxAlternatives: number;
  onstart: (() => void) | null;
  onresult: ((event: SpeechRecognitionResultEventLike) => void) | null;
  onerror: ((event: SpeechRecognitionErrorEventLike) => void) | null;
  onend: (() => void) | null;
  start(): void;
  stop(): void;
  abort(): void;
}

interface SpeechRecognitionConstructor {
  new (): SpeechRecognitionInstance;
}

interface SpeechRecognitionWindow extends Window {
  SpeechRecognition?: SpeechRecognitionConstructor;
  webkitSpeechRecognition?: SpeechRecognitionConstructor;
}

// Check for browser support
const SpeechRecognition =
  typeof window !== 'undefined'
    ? (window as unknown as SpeechRecognitionWindow).SpeechRecognition || (window as unknown as SpeechRecognitionWindow).webkitSpeechRecognition
    : null;

export class VoiceInputService {
  private recognition: SpeechRecognitionInstance | null = null;
  private status: VoiceInputStatus = 'idle';
  private options: VoiceInputOptions = {};
  private listeners: Set<(status: VoiceInputStatus) => void> = new Set();

  constructor(options: VoiceInputOptions = {}) {
    this.options = {
      language: 'en-US',
      continuous: false,
      interimResults: true,
      maxAlternatives: 1,
      ...options,
    };

    if (SpeechRecognition) {
      this.initRecognition();
    }
  }

  /**
   * Check if voice input is supported
   */
  static isSupported(): boolean {
    return SpeechRecognition !== null;
  }

  /**
   * Initialize speech recognition
   */
  private initRecognition(): void {
    if (!SpeechRecognition) {
      throw new Error('Speech recognition is not supported in this environment.');
    }
    this.recognition = new SpeechRecognition();
    this.recognition.lang = this.options.language ?? 'en-US';
    this.recognition.continuous = this.options.continuous ?? false;
    this.recognition.interimResults = this.options.interimResults ?? true;
    this.recognition.maxAlternatives = this.options.maxAlternatives ?? 1;

    this.recognition.onstart = () => {
      this.setStatus('listening');
    };

    this.recognition.onresult = (event: unknown) => {
      const e = event as SpeechRecognitionResultEventLike;
      const result = e.results[e.results.length - 1];
      const transcript = result[0].transcript;
      const confidence = result[0].confidence;
      const isFinal = result.isFinal;

      if (isFinal) {
        this.setStatus('processing');
      }

      this.options.onResult?.({
        transcript,
        confidence,
        isFinal,
      });
    };

    this.recognition.onerror = (event: unknown) => {
      const e = event as SpeechRecognitionErrorEventLike;
      logger.error('Speech recognition error', 'voice-input', { error: e.error });
      this.setStatus('error');
      this.options.onError?.(e.error);
    };

    this.recognition.onend = () => {
      if (this.status === 'listening') {
        this.setStatus('idle');
      }
    };
  }

  /**
   * Set status and notify listeners
   */
  private setStatus(status: VoiceInputStatus): void {
    this.status = status;
    this.options.onStatusChange?.(status);
    this.listeners.forEach(listener => listener(status));
  }

  /**
   * Get current status
   */
  getStatus(): VoiceInputStatus {
    return this.status;
  }

  /**
   * Start listening
   */
  start(): boolean {
    if (!this.recognition) {
      this.options.onError?.('Speech recognition not supported');
      return false;
    }

    if (this.status === 'listening') {
      return true;
    }

    try {
      this.recognition.start();
      return true;
    } catch (error) {
      logger.error('Failed to start speech recognition', 'voice-input', { error: error instanceof Error ? error.message : String(error) });
      this.setStatus('error');
      return false;
    }
  }

  /**
   * Stop listening
   */
  stop(): void {
    if (this.recognition && this.status === 'listening') {
      this.recognition.stop();
      this.setStatus('idle');
    }
  }

  /**
   * Abort listening (discard results)
   */
  abort(): void {
    if (this.recognition) {
      this.recognition.abort();
      this.setStatus('idle');
    }
  }

  /**
   * Subscribe to status changes
   */
  onStatusChange(listener: (status: VoiceInputStatus) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  /**
   * Update options
   */
  setOptions(options: Partial<VoiceInputOptions>): void {
    this.options = { ...this.options, ...options };

    if (this.recognition) {
      if (options.language) this.recognition.lang = options.language;
      if (options.continuous !== undefined) this.recognition.continuous = options.continuous;
      if (options.interimResults !== undefined) this.recognition.interimResults = options.interimResults;
    }
  }

  /**
   * Cleanup
   */
  destroy(): void {
    this.abort();
    this.listeners.clear();
    this.recognition = null;
  }
}

// Singleton instance
let voiceInputServiceInstance: VoiceInputService | null = null;

export function getVoiceInputService(options?: VoiceInputOptions): VoiceInputService {
  if (!voiceInputServiceInstance) {
    voiceInputServiceInstance = new VoiceInputService(options);
  }
  return voiceInputServiceInstance;
}

export default VoiceInputService;
