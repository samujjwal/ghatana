/**
 * Workflow AI Service
 *
 * AI-powered workflow generation from natural language descriptions.
 * Used by SmartWorkflowBuilder for intent-based pipeline creation.
 *
 * Features:
 * - Natural language to pipeline conversion
 * - Step confidence scoring
 * - Optimization suggestions
 * - Pattern recognition
 *
 * @doc.type service
 * @doc.purpose AI pipeline generation service
 * @doc.layer frontend
 */

import { brainService } from '../api/brain.service';

/**
 * Pipeline step types
 */
export type StepType = 'source' | 'transform' | 'destination' | 'condition';

/**
 * Pipeline step
 */
export interface PipelineStep {
  id: string;
  type: StepType;
  name: string;
  description: string;
  aiGenerated: boolean;
  confidence: number;
  config?: Record<string, unknown>;
  suggestedConfig?: Record<string, unknown>;
  warnings?: string[];
}

/**
 * Generated pipeline
 */
export interface GeneratedPipeline {
  id: string;
  name: string;
  description: string;
  steps: PipelineStep[];
  overallConfidence: number;
  estimatedCost?: number;
  suggestions?: PipelineSuggestion[];
}

/**
 * Pipeline suggestion
 */
export interface PipelineSuggestion {
  id: string;
  type: 'optimization' | 'error_handling' | 'pattern' | 'best_practice';
  title: string;
  description: string;
  impact: 'low' | 'medium' | 'high';
  autoApply?: boolean;
}

/**
 * Step pattern definitions for NL parsing
 */
interface StepPattern {
  patterns: RegExp[];
  type: StepType;
  name: string;
  nameExtractor?: (match: RegExpMatchArray) => string;
  descriptionTemplate: string;
  confidence: number;
}

/**
 * Known step patterns
 */
const STEP_PATTERNS: StepPattern[] = [
  // Sources
  {
    patterns: [
      /load\s+(data\s+)?from\s+(\w+)/i,
      /read\s+(from\s+)?(\w+)/i,
      /ingest\s+(from\s+)?(\w+)/i,
      /import\s+(from\s+)?(\w+)/i,
    ],
    type: 'source',
    name: 'Load from',
    nameExtractor: (match) => `Load from ${match[2]?.toUpperCase() || 'Source'}`,
    descriptionTemplate: 'Read data from {source}',
    confidence: 0.9,
  },
  // Transforms - Clean
  {
    patterns: [
      /clean\s+(\w+)/i,
      /cleanse\s+(\w+)/i,
      /sanitize\s+(\w+)/i,
    ],
    type: 'transform',
    name: 'Clean',
    nameExtractor: (match) => `Clean ${match[1] || 'Data'}`,
    descriptionTemplate: 'Clean and validate {field}',
    confidence: 0.85,
  },
  // Transforms - Filter
  {
    patterns: [
      /filter\s+(.+)/i,
      /remove\s+(invalid|null|empty)/i,
      /exclude\s+(.+)/i,
    ],
    type: 'transform',
    name: 'Filter',
    nameExtractor: (match) => `Filter ${match[1] || 'Records'}`,
    descriptionTemplate: 'Filter records based on criteria',
    confidence: 0.82,
  },
  // Transforms - Aggregate
  {
    patterns: [
      /aggregate\s+(by\s+)?(\w+)/i,
      /group\s+(by\s+)?(\w+)/i,
      /sum\s+(by\s+)?(\w+)/i,
    ],
    type: 'transform',
    name: 'Aggregate',
    nameExtractor: (match) => `Aggregate by ${match[2] || 'Field'}`,
    descriptionTemplate: 'Aggregate data by {field}',
    confidence: 0.88,
  },
  // Transforms - Join
  {
    patterns: [
      /join\s+(with\s+)?(\w+)/i,
      /merge\s+(with\s+)?(\w+)/i,
      /combine\s+(with\s+)?(\w+)/i,
    ],
    type: 'transform',
    name: 'Join',
    nameExtractor: (match) => `Join with ${match[2] || 'Table'}`,
    descriptionTemplate: 'Join with {table}',
    confidence: 0.85,
  },
  // Transforms - General
  {
    patterns: [
      /transform\s+(.+)/i,
      /convert\s+(.+)/i,
      /format\s+(.+)/i,
    ],
    type: 'transform',
    name: 'Transform',
    nameExtractor: (match) => `Transform ${match[1] || 'Data'}`,
    descriptionTemplate: 'Apply transformation to {field}',
    confidence: 0.75,
  },
  // Transforms - Validate
  {
    patterns: [
      /validate\s+(.+)/i,
      /check\s+(.+)/i,
      /verify\s+(.+)/i,
    ],
    type: 'transform',
    name: 'Validate',
    nameExtractor: (match) => `Validate ${match[1] || 'Data'}`,
    descriptionTemplate: 'Validate data against rules',
    confidence: 0.8,
  },
  // Destinations
  {
    patterns: [
      /save\s+(to\s+)?(\w+)/i,
      /write\s+(to\s+)?(\w+)/i,
      /export\s+(to\s+)?(\w+)/i,
      /store\s+(in\s+)?(\w+)/i,
    ],
    type: 'destination',
    name: 'Save to',
    nameExtractor: (match) => `Save to ${match[2]?.toUpperCase() || 'Destination'}`,
    descriptionTemplate: 'Write data to {destination}',
    confidence: 0.9,
  },
];

/**
 * Workflow AI Service Class
 */
class WorkflowAIService {
  /**
   * Generate pipeline from natural language description
   */
  async generateFromDescription(description: string): Promise<GeneratedPipeline> {
    const steps = this.parseDescription(description);
    const suggestions = this.generateSuggestions(steps, description);
    const overallConfidence = this.calculateOverallConfidence(steps);

    const pipeline: GeneratedPipeline = {
      id: this.generateId(),
      name: this.generatePipelineName(description),
      description,
      steps,
      overallConfidence,
      suggestions,
    };

    // Try to enhance with Brain patterns
    try {
      const patterns = await brainService.matchPatterns({ description, steps });
      if (patterns.length > 0) {
        pipeline.suggestions = [
          ...pipeline.suggestions || [],
          ...patterns.map((p) => ({
            id: p.patternId,
            type: 'pattern' as const,
            title: p.name,
            description: p.description,
            impact: 'medium' as const,
          })),
        ];
      }
    } catch {
      // Continue without pattern matching
    }

    return pipeline;
  }

  /**
   * Parse description into steps
   */
  private parseDescription(description: string): PipelineStep[] {
    const steps: PipelineStep[] = [];
    const segments = this.segmentDescription(description);
    let stepIndex = 0;

    for (const segment of segments) {
      const step = this.matchSegmentToStep(segment, stepIndex);
      if (step) {
        steps.push(step);
        stepIndex++;
      }
    }

    // Ensure we have at least source and destination
    if (steps.length === 0) {
      steps.push(this.createDefaultSourceStep());
      steps.push(this.createDefaultDestinationStep());
    } else {
      // Add source if missing
      if (!steps.some((s) => s.type === 'source')) {
        steps.unshift(this.createDefaultSourceStep());
      }
      // Add destination if missing
      if (!steps.some((s) => s.type === 'destination')) {
        steps.push(this.createDefaultDestinationStep());
      }
    }

    return steps;
  }

  /**
   * Segment description into logical parts
   */
  private segmentDescription(description: string): string[] {
    // Split by common delimiters
    const delimiters = /[,;]|\s+then\s+|\s+and\s+then\s+|\s+and\s+/gi;
    return description
      .split(delimiters)
      .map((s) => s.trim())
      .filter((s) => s.length > 0);
  }

  /**
   * Match a segment to a step pattern
   */
  private matchSegmentToStep(segment: string, index: number): PipelineStep | null {
    for (const pattern of STEP_PATTERNS) {
      for (const regex of pattern.patterns) {
        const match = segment.match(regex);
        if (match) {
          return {
            id: this.generateId(),
            type: pattern.type,
            name: pattern.nameExtractor ? pattern.nameExtractor(match) : pattern.name,
            description: pattern.descriptionTemplate.replace(
              /\{(\w+)\}/g,
              (_, key) => match[2] || match[1] || key
            ),
            aiGenerated: true,
            confidence: pattern.confidence,
          };
        }
      }
    }
    return null;
  }

  /**
   * Create default source step
   */
  private createDefaultSourceStep(): PipelineStep {
    return {
      id: this.generateId(),
      type: 'source',
      name: 'Load Data',
      description: 'Configure data source',
      aiGenerated: true,
      confidence: 0.5,
      warnings: ['Source not specified - please configure'],
    };
  }

  /**
   * Create default destination step
   */
  private createDefaultDestinationStep(): PipelineStep {
    return {
      id: this.generateId(),
      type: 'destination',
      name: 'Save Data',
      description: 'Configure data destination',
      aiGenerated: true,
      confidence: 0.5,
      warnings: ['Destination not specified - please configure'],
    };
  }

  /**
   * Generate suggestions for the pipeline
   */
  private generateSuggestions(
    steps: PipelineStep[],
    description: string
  ): PipelineSuggestion[] {
    const suggestions: PipelineSuggestion[] = [];

    // Check for error handling
    if (!description.toLowerCase().includes('error') && steps.length > 2) {
      suggestions.push({
        id: 'add-error-handling',
        type: 'error_handling',
        title: 'Add Error Handling',
        description: 'Consider adding error handling steps for robustness',
        impact: 'high',
      });
    }

    // Check for batching with large operations
    if (
      description.toLowerCase().includes('aggregate') ||
      description.toLowerCase().includes('join')
    ) {
      suggestions.push({
        id: 'enable-batching',
        type: 'optimization',
        title: 'Enable Batching',
        description: 'Large operations may benefit from batch processing',
        impact: 'medium',
      });
    }

    // Check for validation
    const hasValidation = steps.some(
      (s) =>
        s.name.toLowerCase().includes('validate') ||
        s.name.toLowerCase().includes('clean')
    );
    if (!hasValidation) {
      suggestions.push({
        id: 'add-validation',
        type: 'best_practice',
        title: 'Add Data Validation',
        description: 'Validate data before processing to catch issues early',
        impact: 'medium',
      });
    }

    return suggestions;
  }

  /**
   * Calculate overall confidence
   */
  private calculateOverallConfidence(steps: PipelineStep[]): number {
    if (steps.length === 0) return 0;
    const sum = steps.reduce((acc, step) => acc + step.confidence, 0);
    return Math.round((sum / steps.length) * 100) / 100;
  }

  /**
   * Generate pipeline name from description
   */
  private generatePipelineName(description: string): string {
    const words = description.split(' ').slice(0, 4);
    return words.map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join(' ') + ' Pipeline';
  }

  /**
   * Generate unique ID
   */
  private generateId(): string {
    return `step-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }

  /**
   * Validate pipeline configuration
   */
  validatePipeline(pipeline: GeneratedPipeline): {
    valid: boolean;
    errors: string[];
    warnings: string[];
  } {
    const errors: string[] = [];
    const warnings: string[] = [];

    // Check for source
    if (!pipeline.steps.some((s) => s.type === 'source')) {
      errors.push('Pipeline must have at least one source');
    }

    // Check for destination
    if (!pipeline.steps.some((s) => s.type === 'destination')) {
      errors.push('Pipeline must have at least one destination');
    }

    // Check for low confidence steps
    const lowConfidenceSteps = pipeline.steps.filter((s) => s.confidence < 0.6);
    if (lowConfidenceSteps.length > 0) {
      warnings.push(
        `${lowConfidenceSteps.length} step(s) have low confidence - please review`
      );
    }

    return {
      valid: errors.length === 0,
      errors,
      warnings,
    };
  }

  /**
   * Optimize pipeline with AI suggestions
   */
  async optimizePipeline(pipeline: GeneratedPipeline): Promise<GeneratedPipeline> {
    // In a real implementation, this would call AI APIs
    const optimizedSteps = [...pipeline.steps];
    const newSuggestions = [...(pipeline.suggestions || [])];

    // Check for consecutive transforms that could be merged
    let consecutiveTransforms = 0;
    for (const step of optimizedSteps) {
      if (step.type === 'transform') {
        consecutiveTransforms++;
        if (consecutiveTransforms >= 3) {
          newSuggestions.push({
            id: 'merge-transforms',
            type: 'optimization',
            title: 'Merge Transform Steps',
            description: 'Multiple transforms could be combined for efficiency',
            impact: 'medium',
          });
          break;
        }
      } else {
        consecutiveTransforms = 0;
      }
    }

    return {
      ...pipeline,
      suggestions: newSuggestions,
    };
  }
}

/**
 * Singleton instance
 */
export const workflowAIService = new WorkflowAIService();

export default workflowAIService;

