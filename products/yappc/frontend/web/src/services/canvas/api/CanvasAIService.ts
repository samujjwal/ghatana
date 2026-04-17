// @ts-nocheck
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
import { parseJsonResponse, readErrorResponse } from '@/lib/http';

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

function parseGenerationProgress(
  raw: string,
  context: string
): GenerationProgress | null {
  try {
    return JSON.parse(raw) as GenerationProgress;
  } catch (error) {
    console.warn(`[CanvasAIService] Failed to parse ${context}:`, error);
    return null;
  }
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
      throw new Error(
        await readErrorResponse(response, `Validation failed: ${response.statusText}`)
      );
    }

    return parseJsonResponse<ValidationReport>(response, 'canvas validation');
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
      throw new Error(
        await readErrorResponse(
          response,
          `Code generation failed: ${response.statusText}`
        )
      );
    }

    return parseJsonResponse<CodeGenerationResult>(response, 'code generation');
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
      throw new Error(
        await readErrorResponse(
          response,
          `Code generation stream failed: ${response.statusText}`
        )
      );
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
            const progress = parseGenerationProgress(line, 'stream line');
            if (progress) {
              yield progress;
            }
          }
        }
      }

      // Process remaining buffer
      if (buffer.trim()) {
        const progress = parseGenerationProgress(buffer, 'final stream buffer');
        if (progress) {
          yield progress;
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
