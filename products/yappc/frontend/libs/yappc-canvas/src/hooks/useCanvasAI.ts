/**
 * Consolidated Canvas AI Hook
 * 
 * Replaces: useAIBrainstorming, useComponentGeneration, useCodeScaffold, 
 *           useRequirementWireframer, useMicroservicesExtractor, useServiceBlueprint
 * Provides: AI-powered canvas features
 * 
 * @doc.type hook
 * @doc.purpose Consolidated AI features
 * @doc.layer presentation
 */

import { useCallback, useState } from 'react';
import type { Node } from '@xyflow/react';

export type AIFeature =
  | 'brainstorming'
  | 'component-generation'
  | 'code-scaffold'
  | 'wireframing'
  | 'microservices-extraction'
  | 'service-blueprint';

export interface Idea {
  id: string;
  title: string;
  description: string;
  confidence: number;
  node?: Node;
}

export interface Component {
  id: string;
  name: string;
  type: string;
  props: Record<string, unknown>;
  code: string;
}

export interface ComponentSpec {
  name: string;
  description: string;
  framework: 'react' | 'vue' | 'angular';
  styling: 'tailwind' | 'css' | 'styled-components';
}

export interface Scaffold {
  id: string;
  template: string;
  files: ScaffoldFile[];
  instructions: string[];
}

export interface ScaffoldFile {
  path: string;
  content: string;
  language: string;
}

export interface Wireframe {
  id: string;
  requirement: string;
  screens: WireframeScreen[];
  flows: WireframeFlow[];
}

export interface WireframeScreen {
  id: string;
  name: string;
  components: string[];
  layout: string;
}

export interface WireframeFlow {
  from: string;
  to: string;
  action: string;
}

export interface Microservice {
  id: string;
  name: string;
  responsibilities: string[];
  apis: API[];
  dependencies: string[];
}

export interface API {
  method: string;
  path: string;
  description: string;
}

export interface Architecture {
  services: string[];
  databases: string[];
  messageQueues: string[];
}

export interface Blueprint {
  id: string;
  service: string;
  architecture: string;
  components: string[];
  infrastructure: string[];
}

export interface ServiceSpec {
  name: string;
  type: 'rest' | 'graphql' | 'grpc';
  language: 'typescript' | 'java' | 'python' | 'go';
}

export interface Requirement {
  id: string;
  title: string;
  description: string;
}

export interface UseCanvasAIOptions {
  canvasId: string;
  tenantId: string;
  enabledFeatures?: AIFeature[];
  apiEndpoint?: string;
}

export interface UseCanvasAIReturn {
  // Brainstorming
  generateIdeas: (prompt: string) => Promise<Idea[]>;
  
  // Component Generation
  generateComponent: (spec: ComponentSpec) => Promise<Component>;
  
  // Code Scaffolding
  generateScaffold: (template: string, config: Record<string, unknown>) => Promise<Scaffold>;
  
  // Wireframing
  generateWireframe: (requirement: Requirement) => Promise<Wireframe>;
  
  // Microservices Extraction
  extractMicroservices: (monolith: Architecture) => Promise<Microservice[]>;
  
  // Service Blueprint
  generateBlueprint: (service: ServiceSpec) => Promise<Blueprint>;
  
  // Common state
  isGenerating: boolean;
  error: Error | null;
  clearError: () => void;
}

export function useCanvasAI(options: UseCanvasAIOptions): UseCanvasAIReturn {
  const {
    canvasId,
    tenantId,
    enabledFeatures = [
      'brainstorming',
      'component-generation',
      'code-scaffold',
      'wireframing',
      'microservices-extraction',
      'service-blueprint',
    ],
    apiEndpoint = '/api/ai',
  } = options;

  const [isGenerating, setIsGenerating] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const clearError = useCallback(() => setError(null), []);

  // Helper to make AI API calls
  const callAI = useCallback(
    async <T,>(endpoint: string, payload: unknown): Promise<T> => {
      setIsGenerating(true);
      setError(null);

      try {
        const response = await fetch(`${apiEndpoint}${endpoint}`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'X-Tenant-ID': tenantId,
            'X-Canvas-ID': canvasId,
          },
          body: JSON.stringify(payload),
        });

        if (!response.ok) {
          throw new Error(`AI request failed: ${response.statusText}`);
        }

        const data = await response.json();
        return data as T;
      } catch (err) {
        const error = err instanceof Error ? err : new Error('Unknown error');
        setError(error);
        throw error;
      } finally {
        setIsGenerating(false);
      }
    },
    [apiEndpoint, tenantId, canvasId]
  );

  // Brainstorming
  const generateIdeas = useCallback(
    async (prompt: string): Promise<Idea[]> => {
      if (!enabledFeatures.includes('brainstorming')) {
        throw new Error('Brainstorming feature is not enabled');
      }

      return callAI<Idea[]>('/brainstorm', { prompt });
    },
    [enabledFeatures, callAI]
  );

  // Component Generation
  const generateComponent = useCallback(
    async (spec: ComponentSpec): Promise<Component> => {
      if (!enabledFeatures.includes('component-generation')) {
        throw new Error('Component generation feature is not enabled');
      }

      return callAI<Component>('/generate-component', { spec });
    },
    [enabledFeatures, callAI]
  );

  // Code Scaffolding
  const generateScaffold = useCallback(
    async (template: string, config: Record<string, unknown>): Promise<Scaffold> => {
      if (!enabledFeatures.includes('code-scaffold')) {
        throw new Error('Code scaffold feature is not enabled');
      }

      return callAI<Scaffold>('/scaffold', { template, config });
    },
    [enabledFeatures, callAI]
  );

  // Wireframing
  const generateWireframe = useCallback(
    async (requirement: Requirement): Promise<Wireframe> => {
      if (!enabledFeatures.includes('wireframing')) {
        throw new Error('Wireframing feature is not enabled');
      }

      return callAI<Wireframe>('/wireframe', { requirement });
    },
    [enabledFeatures, callAI]
  );

  // Microservices Extraction
  const extractMicroservices = useCallback(
    async (monolith: Architecture): Promise<Microservice[]> => {
      if (!enabledFeatures.includes('microservices-extraction')) {
        throw new Error('Microservices extraction feature is not enabled');
      }

      return callAI<Microservice[]>('/extract-microservices', { monolith });
    },
    [enabledFeatures, callAI]
  );

  // Service Blueprint
  const generateBlueprint = useCallback(
    async (service: ServiceSpec): Promise<Blueprint> => {
      if (!enabledFeatures.includes('service-blueprint')) {
        throw new Error('Service blueprint feature is not enabled');
      }

      return callAI<Blueprint>('/blueprint', { service });
    },
    [enabledFeatures, callAI]
  );

  return {
    // Brainstorming
    generateIdeas,

    // Component Generation
    generateComponent,

    // Code Scaffolding
    generateScaffold,

    // Wireframing
    generateWireframe,

    // Microservices Extraction
    extractMicroservices,

    // Service Blueprint
    generateBlueprint,

    // Common state
    isGenerating,
    error,
    clearError,
  };
}
