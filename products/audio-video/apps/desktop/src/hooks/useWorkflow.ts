/**
 * @doc.type hook
 * @doc.purpose Cross-service workflow orchestration
 * @doc.layer application
 * @doc.pattern workflow orchestration
 */

import { useState, useCallback } from 'react';
import { invoke } from '@tauri-apps/api/core';
import type { 
  Workflow, 
  WorkflowExecution, 
  STTResult, 
  TTSResult, 
  AIVoiceResult,
  AudioData 
} from '@ghatana/audio-video-types';

interface UseWorkflowOptions {
  onProgress?: (step: number, total: number, message: string) => void;
  onComplete?: (result: WorkflowExecution) => void;
  onError?: (error: string) => void;
}

export const useWorkflow = (options: UseWorkflowOptions = {}) => {
  const [isRunning, setIsRunning] = useState(false);
  const [currentExecution, setCurrentExecution] = useState<WorkflowExecution | null>(null);

  /**
   * Speech-to-Speech workflow: STT → AI Voice → TTS
   */
  const speechToSpeech = useCallback(async (audioData: AudioData) => {
    setIsRunning(true);
    const startTime = new Date();
    
    try {
      // Step 1: Transcribe audio
      options.onProgress?.(1, 3, 'Transcribing audio...');
      const sttResult = await invoke<STTResult>('stt_transcribe', {
        audioData: Array.from(new Uint8Array(audioData.data)),
        language: 'en-US'
      });

      // Step 2: Enhance text with AI Voice
      options.onProgress?.(2, 3, 'Enhancing transcription...');
      const aiResult = await invoke<AIVoiceResult>('ai_voice_process', {
        text: sttResult.text,
        task: 'enhance'
      });

      // Step 3: Synthesize enhanced text
      options.onProgress?.(3, 3, 'Generating speech...');
      const ttsResult = await invoke<TTSResult>('tts_synthesize', {
        text: aiResult.processedText,
        voiceId: null
      });

      const execution: WorkflowExecution = {
        workflowId: 'speech-to-speech',
        status: 'completed',
        startTime,
        endTime: new Date(),
        results: {
          transcription: sttResult,
          enhanced: aiResult,
          audio: ttsResult
        }
      };

      setCurrentExecution(execution);
      options.onComplete?.(execution);
      
      return execution;

    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Unknown error';
      options.onError?.(errorMessage);
      
      const execution: WorkflowExecution = {
        workflowId: 'speech-to-speech',
        status: 'failed',
        startTime,
        endTime: new Date(),
        results: {},
        error: errorMessage
      };

      setCurrentExecution(execution);
      throw error;

    } finally {
      setIsRunning(false);
    }
  }, [options]);

  /**
   * Text Translation workflow: AI Voice (translate) → TTS
   */
  const translateAndSpeak = useCallback(async (text: string, targetLanguage: string) => {
    setIsRunning(true);
    const startTime = new Date();
    
    try {
      // Step 1: Translate text
      options.onProgress?.(1, 2, 'Translating text...');
      const aiResult = await invoke<AIVoiceResult>('ai_voice_process', {
        text,
        task: 'translate'
      });

      // Step 2: Synthesize translated text
      options.onProgress?.(2, 2, 'Generating speech...');
      const ttsResult = await invoke<TTSResult>('tts_synthesize', {
        text: aiResult.processedText,
        voiceId: null
      });

      const execution: WorkflowExecution = {
        workflowId: 'translate-and-speak',
        status: 'completed',
        startTime,
        endTime: new Date(),
        results: {
          translation: aiResult,
          audio: ttsResult
        }
      };

      setCurrentExecution(execution);
      options.onComplete?.(execution);
      
      return execution;

    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Unknown error';
      options.onError?.(errorMessage);
      throw error;

    } finally {
      setIsRunning(false);
    }
  }, [options]);

  /**
   * Content Analysis workflow: Vision → AI Voice (summarize)
   */
  const analyzeContent = useCallback(async (imageData: ArrayBuffer) => {
    setIsRunning(true);
    const startTime = new Date();
    
    try {
      // Step 1: Analyze image
      options.onProgress?.(1, 2, 'Analyzing image...');
      const visionResult = await invoke<string>('vision_process', {
        imageData: Array.from(new Uint8Array(imageData)),
        task: 'analyze'
      });

      // Step 2: Generate summary
      options.onProgress?.(2, 2, 'Generating summary...');
      const aiResult = await invoke<AIVoiceResult>('ai_voice_process', {
        text: visionResult,
        task: 'summarize'
      });

      const execution: WorkflowExecution = {
        workflowId: 'analyze-content',
        status: 'completed',
        startTime,
        endTime: new Date(),
        results: {
          vision: visionResult,
          summary: aiResult
        }
      };

      setCurrentExecution(execution);
      options.onComplete?.(execution);
      
      return execution;

    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Unknown error';
      options.onError?.(errorMessage);
      throw error;

    } finally {
      setIsRunning(false);
    }
  }, [options]);

  /**
   * Multimodal Processing workflow: Combined analysis
   */
  const processMultimodal = useCallback(async (input: {
    audio?: AudioData;
    image?: ArrayBuffer;
    text?: string;
  }) => {
    setIsRunning(true);
    const startTime = new Date();
    
    try {
      options.onProgress?.(1, 1, 'Processing multimodal data...');
      
      const request = {
        audio: input.audio ? Array.from(new Uint8Array(input.audio.data)) : undefined,
        image: input.image ? Array.from(new Uint8Array(input.image)) : undefined,
        text: input.text,
        task: 'analyze'
      };

      const result = await invoke<string>('multimodal_process', { request });

      const execution: WorkflowExecution = {
        workflowId: 'multimodal-process',
        status: 'completed',
        startTime,
        endTime: new Date(),
        results: { multimodal: result }
      };

      setCurrentExecution(execution);
      options.onComplete?.(execution);
      
      return execution;

    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Unknown error';
      options.onError?.(errorMessage);
      throw error;

    } finally {
      setIsRunning(false);
    }
  }, [options]);

  return {
    isRunning,
    currentExecution,
    workflows: {
      speechToSpeech,
      translateAndSpeak,
      analyzeContent,
      processMultimodal
    },
    reset: () => {
      setCurrentExecution(null);
      setIsRunning(false);
    }
  };
};
