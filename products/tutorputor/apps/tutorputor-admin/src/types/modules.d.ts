declare module '@tutorputor/learning-kernel' {
  export interface LearningUnit {
    id: string;
    title: string;
    content?: string;
    version?: number;
    status?: string;
    intent?: { problem: string; motivation: string };
    claims?: unknown[];
    evidence?: unknown[];
    tasks?: unknown[];
    artifacts?: unknown[];
    metadata?: Record<string, unknown>;
  }

  export interface KernelConfig {
    version: string;
    features: string[];
  }

  export interface ValidationIssue {
    severity: 'error' | 'warning' | 'info';
    field: string;
    message: string;
    suggestion?: string;
  }

  export interface ValidationResult {
    valid: boolean;
    issues: ValidationIssue[];
    score: number;
  }

  export class LearningUnitValidator {
    constructor();
    validate(unit: LearningUnit): Promise<ValidationResult>;
  }

  export const globalRegistry: {
    register(name: string, handler: unknown): void;
    get(name: string): unknown;
    listAll(): Array<{ id: string; name: string; handler: unknown }>;
  };

  export function processLearningUnit(unit: LearningUnit): Promise<LearningUnit>;
  export function validateUnit(unit: LearningUnit): boolean;
  export function generateRecommendations(unit: LearningUnit): string[];
}

declare module '@tutorputor/simulation/renderer' {
  export interface SimulationConfig {
    id: string;
    type: string;
    parameters: Record<string, unknown>;
  }

  export interface RendererOptions {
    width: number;
    height: number;
    antialias?: boolean;
  }

  export class SimulationRenderer {
    constructor(container: HTMLElement, options: RendererOptions);
    loadSimulation(config: SimulationConfig): Promise<void>;
    render(): void;
    destroy(): void;
  }

  export function createRenderer(container: HTMLElement, options: RendererOptions): SimulationRenderer;
}
