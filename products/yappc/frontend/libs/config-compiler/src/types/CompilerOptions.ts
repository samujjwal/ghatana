/**
 * Compiler Options
 *
 * Default options and validation for compiler configuration.
 *
 * @packageDocumentation
 */

/**
 * Compiler options - configuration for compilation process
 */
export interface CompilerOptions {
  /**
   * Generate TypeScript code
   */
  typescript?: boolean;

  /**
   * Include comments in generated code
   */
  includeComments?: boolean;

  /**
   * Include imports in generated code
   */
  includeImports?: boolean;

  /**
   * Code style (functional or class-based)
   */
  style?: 'functional' | 'class';

  /**
   * Indentation level
   */
  indent?: number;

  /**
   * Include data binding code
   */
  includeDataBinding?: boolean;

  /**
   * Include event handlers
   */
  includeEvents?: boolean;

  /**
   * Include validation
   */
  includeValidation?: boolean;

  /**
   * Target platform
   */
  target?: 'web' | 'mobile' | 'desktop';

  /**
   * Output format
   */
  outputFormat?: 'file' | 'string' | 'ast';

  /**
   * Minify output
   */
  minify?: boolean;

  /**
   * Source map generation
   */
  sourceMap?: boolean;
}

/**
 * Default compiler options
 */
export const DEFAULT_COMPILER_OPTIONS: CompilerOptions = {
  typescript: true,
  includeComments: true,
  includeImports: true,
  style: 'functional',
  indent: 2,
  includeDataBinding: true,
  includeEvents: true,
  includeValidation: true,
  target: 'web',
  outputFormat: 'file',
  minify: false,
  sourceMap: false,
};

/**
 * Merge user options with defaults
 */
export function mergeCompilerOptions(
  userOptions: Partial<CompilerOptions> = {}
): CompilerOptions {
  return {
    ...DEFAULT_COMPILER_OPTIONS,
    ...userOptions,
  };
}

/**
 * Validate compiler options
 */
export function validateCompilerOptions(options: CompilerOptions): {
  valid: boolean;
  errors: string[];
} {
  const errors: string[] = [];

  if (options.style && !['functional', 'class'].includes(options.style)) {
    errors.push(
      `Invalid style: ${options.style}. Must be 'functional' or 'class'`
    );
  }

  if (
    options.target &&
    !['web', 'mobile', 'desktop'].includes(options.target)
  ) {
    errors.push(
      `Invalid target: ${options.target}. Must be 'web', 'mobile', or 'desktop'`
    );
  }

  if (
    options.outputFormat &&
    !['file', 'string', 'ast'].includes(options.outputFormat)
  ) {
    errors.push(
      `Invalid outputFormat: ${options.outputFormat}. Must be 'file', 'string', or 'ast'`
    );
  }

  if (
    options.indent !== undefined &&
    (typeof options.indent !== 'number' || options.indent < 0)
  ) {
    errors.push(
      `Invalid indent: ${options.indent}. Must be a non-negative number`
    );
  }

  return {
    valid: errors.length === 0,
    errors,
  };
}
