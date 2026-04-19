/**
 * Compiler Types
 *
 * Type definitions for the config compiler.
 *
 * @packageDocumentation
 */

export * from './CompilerContext';
export * from './CompilerOptions';

/**
 * Compilation result
 */
export interface CompilationResult {
  /**
   * Whether compilation succeeded
   */
  success: boolean;

  /**
   * Compiler context
   */
  context: import('./CompilerContext').CompilerContext;

  /**
   * Generated artifacts
   */
  artifacts: import('./CompilerContext').GeneratedArtifact[];
}

/**
 * Compiler interface
 */
export interface Compiler {
  /**
   * Compiler name
   */
  name: string;

  /**
   * Compiler version
   */
  version: string;

  /**
   * Compile config to artifacts
   */
  compile(
    config: unknown,
    options: import('./CompilerOptions').CompilerOptions
  ): Promise<CompilationResult>;

  /**
   * Validate config before compilation
   */
  validate(config: unknown): ValidationResult;

  /**
   * Get supported config types
   */
  getSupportedTypes(): string[];
}

/**
 * Validation result
 */
export interface ValidationResult {
  /**
   * Whether validation passed
   */
  valid: boolean;

  /**
   * Validation errors
   */
  errors: string[];

  /**
   * Validation warnings
   */
  warnings?: string[];
}
