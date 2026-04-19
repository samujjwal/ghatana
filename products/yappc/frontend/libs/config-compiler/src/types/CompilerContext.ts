/**
 * Compiler Context
 *
 * Manages the compilation state and metadata.
 *
 * @packageDocumentation
 */

import type { CompilerOptions } from './CompilerOptions';

/**
 * Compilation error
 */
export interface CompilationError {
  /**
   * Error code
   */
  code: string;

  /**
   * Error message
   */
  message: string;

  /**
   * Error location in source
   */
  location?: {
    file?: string;
    line?: number;
    column?: number;
  };

  /**
   * Error severity
   */
  severity: 'error' | 'fatal';

  /**
   * Additional context
   */
  context?: Record<string, unknown>;
}

/**
 * Compilation warning
 */
export interface CompilationWarning {
  /**
   * Warning code
   */
  code: string;

  /**
   * Warning message
   */
  message: string;

  /**
   * Warning location in source
   */
  location?: {
    file?: string;
    line?: number;
    column?: number;
  };

  /**
   * Additional context
   */
  context?: Record<string, unknown>;
}

/**
 * Generated artifact
 */
export interface GeneratedArtifact {
  /**
   * Artifact type
   */
  type: 'component' | 'page' | 'scene' | 'config' | 'style';

  /**
   * Artifact name
   */
  name: string;

  /**
   * Artifact content
   */
  content: string;

  /**
   * Artifact language (for code artifacts)
   */
  language?: 'typescript' | 'javascript' | 'css' | 'json';

  /**
   * Artifact file path (if applicable)
   */
  path?: string;

  /**
   * Artifact metadata
   */
  metadata?: Record<string, unknown>;
}

/**
 * Compiler context - tracks compilation state and metadata
 */
export interface CompilerContext {
  /**
   * Unique compilation ID
   */
  compilationId: string;

  /**
   * Source config being compiled
   */
  sourceConfig: unknown;

  /**
   * Compilation options
   */
  options: CompilerOptions;

  /**
   * Compilation start timestamp
   */
  startedAt: Date;

  /**
   * Compilation end timestamp (if completed)
   */
  endedAt?: Date;

  /**
   * Compilation errors encountered
   */
  errors: CompilationError[];

  /**
   * Compilation warnings
   */
  warnings: CompilationWarning[];

  /**
   * Generated artifacts
   */
  artifacts: GeneratedArtifact[];

  /**
   * Metadata collected during compilation
   */
  metadata: Record<string, unknown>;
}

/**
 * Create a new compiler context
 */
export function createCompilerContext(
  sourceConfig: unknown,
  options: CompilerOptions
): CompilerContext {
  return {
    compilationId: generateCompilationId(),
    sourceConfig,
    options,
    startedAt: new Date(),
    errors: [],
    warnings: [],
    artifacts: [],
    metadata: {},
  };
}

/**
 * Generate a unique compilation ID
 */
function generateCompilationId(): string {
  return `comp-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
}

/**
 * Mark compilation as completed
 */
export function completeCompilation(context: CompilerContext): CompilerContext {
  return {
    ...context,
    endedAt: new Date(),
  };
}

/**
 * Add an error to the context
 */
export function addError(
  context: CompilerContext,
  code: string,
  message: string,
  severity: 'error' | 'fatal' = 'error'
): CompilerContext {
  return {
    ...context,
    errors: [
      ...context.errors,
      {
        code,
        message,
        severity,
      },
    ],
  };
}

/**
 * Add a warning to the context
 */
export function addWarning(
  context: CompilerContext,
  code: string,
  message: string
): CompilerContext {
  return {
    ...context,
    warnings: [
      ...context.warnings,
      {
        code,
        message,
      },
    ],
  };
}

/**
 * Add an artifact to the context
 */
export function addArtifact(
  context: CompilerContext,
  artifact: GeneratedArtifact
): CompilerContext {
  return {
    ...context,
    artifacts: [...context.artifacts, artifact],
  };
}

/**
 * Set metadata on the context
 */
export function setMetadata(
  context: CompilerContext,
  key: string,
  value: unknown
): CompilerContext {
  return {
    ...context,
    metadata: {
      ...context.metadata,
      [key]: value,
    },
  };
}
