/**
 * Canvas AI Service API - Browser-safe wrapper
 *
 * This module provides HTTP API endpoints for Canvas AI operations.
 * All gRPC communication happens on the backend only.
 *
 * @architecture Hybrid Backend - Browser -> Node.js API -> Java gRPC
 */

import type {
  CanvasState,
  ValidationReport,
  CodeGenerationResult,
  GenerationProgress,
} from '../agents/types';

const API_URL = import.meta.env.DEV
  ? `${import.meta.env.VITE_API_ORIGIN ?? 'http://localhost:7002'}`
  : '';

export interface ValidateCanvasRequest {
  canvasState: CanvasState;
  phase?: string;
  options?: {
    strictMode?: boolean;
    validateRisks?: boolean;
  };
}

export interface GenerateCodeRequest {
  canvasState: CanvasState;
  options?: {
    language?:
      | 'PROGRAMMING_LANGUAGE_TYPESCRIPT'
      | 'PROGRAMMING_LANGUAGE_JAVA'
      | 'PROGRAMMING_LANGUAGE_PYTHON'
      | 'PROGRAMMING_LANGUAGE_GO';
    framework?: string;
    includeTests?: boolean;
    includeDocumentation?: boolean;
    includeConfiguration?: boolean;
    useAi?: boolean;
  };
}

class CanvasAIService {
  private available: boolean = false;

  async initialize(): Promise<void> {
    try {
      const response = await fetch(`${API_URL}/api/canvas/health`, {
        method: 'GET',
        headers: { 'Content-Type': 'application/json' },
      });
      this.available = response.ok;
      if (this.available) {
        console.log('[CanvasAIService] Canvas AI service available');
      }
    } catch (error) {
      console.warn('[CanvasAIService] Canvas AI service unavailable:', error);
      this.available = false;
    }
  }

  isAvailable(): boolean {
    return this.available;
  }

  async validateCanvas(
    request: ValidateCanvasRequest
  ): Promise<ValidationReport> {
    if (!this.available) {
      throw new Error('Canvas AI service not available');
    }

    const response = await fetch(`${API_URL}/api/canvas/validate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error(`Validation failed: ${response.statusText}`);
    }

    return response.json();
  }

  async generateCode(
    request: GenerateCodeRequest
  ): Promise<CodeGenerationResult> {
    if (!this.available) {
      throw new Error('Canvas AI service not available');
    }

    const response = await fetch(`${API_URL}/api/canvas/generate-code`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error(`Code generation failed: ${response.statusText}`);
    }

    return response.json();
  }

  async *generateCodeStream(
    request: GenerateCodeRequest
  ): AsyncGenerator<GenerationProgress> {
    if (!this.available) {
      throw new Error('Canvas AI service not available');
    }

    const response = await fetch(`${API_URL}/api/canvas/generate-code-stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error(`Code generation stream failed: ${response.statusText}`);
    }

    if (!response.body) {
      throw new Error('Response body is empty');
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          if (line.trim()) {
            try {
              const progress = JSON.parse(line) as GenerationProgress;
              yield progress;
            } catch (e) {
              console.warn('[CanvasAIService] Failed to parse stream line:', e);
            }
          }
        }
      }

      // Process remaining buffer
      if (buffer.trim()) {
        try {
          const progress = JSON.parse(buffer) as GenerationProgress;
          yield progress;
        } catch (e) {
          console.warn('[CanvasAIService] Failed to parse final buffer:', e);
        }
      }
    } finally {
      reader.releaseLock();
    }
  }
}

let serviceInstance: CanvasAIService | null = null;

export async function getCanvasAIService(): Promise<CanvasAIService> {
  if (!serviceInstance) {
    serviceInstance = new CanvasAIService();
    await serviceInstance.initialize();
  }
  return serviceInstance;
}

export async function isCanvasAIAvailable(): Promise<boolean> {
  const service = await getCanvasAIService();
  return service.isAvailable();
}
