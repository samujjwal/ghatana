/**
 * @fileoverview Source acquisition contracts and provider abstraction.
 *
 * Defines the contract for acquiring source files from various sources (browser
 * upload, git repository, file system, etc.) and provides a provider abstraction
 * that can be implemented for different environments.
 *
 * @doc.type module
 * @doc.purpose Source acquisition contracts and provider abstraction
 * @doc.layer studio
 * @doc.pattern Provider
 */

// ============================================================================
// Source Acquisition Contracts
// ============================================================================

/**
 * A source file entry with its path and content.
 */
export interface SourceFileEntry {
  /** Relative path of the file within the source tree. */
  readonly relativePath: string;
  /** Raw file content as text. */
  readonly content: string;
  /** Optional file metadata (size, last modified, etc.). */
  readonly metadata?: {
    readonly size?: number;
    readonly lastModified?: number;
    readonly contentType?: string;
  };
}

/**
 * Result of a source acquisition operation.
 */
export interface SourceAcquisitionResult {
  /** Successfully acquired source files. */
  readonly sources: readonly SourceFileEntry[];
  /** Errors encountered during acquisition (non-blocking). */
  readonly errors: readonly string[];
  /** Whether the acquisition was partially successful. */
  readonly partial: boolean;
}

/**
 * Options for source acquisition.
 */
export interface SourceAcquisitionOptions {
  /** Maximum file size in bytes (default: 1MB). */
  readonly maxFileSize?: number;
  /** Allowed file extensions (default: ['.ts', '.tsx']). */
  readonly allowedExtensions?: readonly string[];
  /** Whether to include hidden files (default: false). */
  readonly includeHidden?: boolean;
}

// ============================================================================
// Provider Abstraction
// ============================================================================

/**
 * Source acquisition provider interface.
 *
 * Implementations can acquire source files from different sources:
 * - Browser file upload (FileReader-based)
 * - Git repository (via git protocol)
 * - Local file system (Node.js fs)
 * - Remote URL fetch (HTTP/HTTPS)
 * - Custom adapters (IDE integrations, etc.)
 */
export interface SourceAcquisitionProvider {
  /**
   * Provider name for logging/debugging.
   */
  readonly providerName: string;

  /**
   * Acquire source files from the provider's source.
   *
   * @param input - Provider-specific input (depends on implementation).
   * @param options - Acquisition options.
   * @returns Acquisition result with sources and any errors.
   */
  acquire(
    input: unknown,
    options?: SourceAcquisitionOptions,
  ): Promise<SourceAcquisitionResult>;

  /**
   * Check if the provider can handle the given input.
   *
   * @param input - Provider-specific input.
   * @returns true if this provider can handle the input.
   */
  canHandle(input: unknown): boolean;
}

// ============================================================================
// Browser File Upload Provider
// ============================================================================

/**
 * Input for browser file upload provider.
 */
export interface BrowserFileUploadInput {
  /** FileList from an HTML file input element. */
  readonly files: FileList;
}

/**
 * Default options for source acquisition.
 */
export const DEFAULT_ACQUISITION_OPTIONS: Required<SourceAcquisitionOptions> = {
  maxFileSize: 1_000_000, // 1 MB
  allowedExtensions: ['.ts', '.tsx'],
  includeHidden: false,
};

/**
 * Browser-based file upload provider using FileReader.
 *
 * This is the default provider for web-based Studio environments.
 */
export class BrowserFileUploadProvider implements SourceAcquisitionProvider {
  readonly providerName = 'BrowserFileUpload';

  canHandle(input: unknown): input is BrowserFileUploadInput {
    return (
      typeof input === 'object' &&
      input !== null &&
      'files' in input &&
      input.files instanceof FileList
    );
  }

  async acquire(
    input: unknown,
    options: SourceAcquisitionOptions = {},
  ): Promise<SourceAcquisitionResult> {
    if (!this.canHandle(input)) {
      return {
        sources: [],
        errors: ['Invalid input for BrowserFileUploadProvider'],
        partial: false,
      };
    }

    const opts = { ...DEFAULT_ACQUISITION_OPTIONS, ...options };
    const files = Array.from(input.files);
    const sources: SourceFileEntry[] = [];
    const errors: string[] = [];

    for (const file of files) {
      // Skip hidden files if configured
      if (!opts.includeHidden && file.name.startsWith('.')) {
        errors.push(`Skipped "${file.name}": hidden file.`);
        continue;
      }

      // Check file extension
      const hasAllowedExtension = opts.allowedExtensions.some((ext) =>
        file.name.endsWith(ext),
      );
      if (!hasAllowedExtension) {
        errors.push(
          `Skipped "${file.name}": only ${opts.allowedExtensions.join(', ')} files are supported.`,
        );
        continue;
      }

      // Check file size
      if (file.size > opts.maxFileSize) {
        errors.push(
          `Skipped "${file.name}": file exceeds ${(opts.maxFileSize / 1_000_000).toFixed(1)} MB limit.`,
        );
        continue;
      }

      try {
        const content = await this.readFileAsText(file);
        sources.push({
          relativePath: file.name,
          content,
          metadata: {
            size: file.size,
            lastModified: file.lastModified,
            contentType: file.type,
          },
        });
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        errors.push(`Could not read "${file.name}": ${message}`);
      }
    }

    return {
      sources,
      errors,
      partial: errors.length > 0 && sources.length > 0,
    };
  }

  /**
   * Read a file as text using FileReader.
   */
  private readFileAsText(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result as string);
      reader.onerror = () => reject(new Error(`Failed to read file: ${file.name}`));
      reader.readAsText(file);
    });
  }
}

// ============================================================================
// Provider Registry
// ============================================================================

/**
 * Registry of available source acquisition providers.
 *
 * Allows registering multiple providers and selecting the appropriate one
 * based on input type.
 */
export class SourceAcquisitionProviderRegistry {
  private providers: SourceAcquisitionProvider[] = [];

  /**
   * Register a provider.
   */
  register(provider: SourceAcquisitionProvider): void {
    this.providers.push(provider);
  }

  /**
   * Unregister a provider by name.
   */
  unregister(providerName: string): void {
    this.providers = this.providers.filter((p) => p.providerName !== providerName);
  }

  /**
   * Get a provider that can handle the given input.
   *
   * @returns The first provider that can handle the input, or null if none found.
   */
  getProviderFor(input: unknown): SourceAcquisitionProvider | null {
    return this.providers.find((p) => p.canHandle(input)) ?? null;
  }

  /**
   * Acquire sources using the appropriate provider for the input.
   *
   * @param input - Provider-specific input.
   * @param options - Acquisition options.
   * @returns Acquisition result or error if no provider can handle the input.
   */
  async acquire(
    input: unknown,
    options?: SourceAcquisitionOptions,
  ): Promise<SourceAcquisitionResult> {
    const provider = this.getProviderFor(input);
    if (!provider) {
      return {
        sources: [],
        errors: ['No provider found for the given input type'],
        partial: false,
      };
    }
    return provider.acquire(input, options);
  }
}

/**
 * Default provider registry with browser file upload provider pre-registered.
 */
export const defaultProviderRegistry = new SourceAcquisitionProviderRegistry();
defaultProviderRegistry.register(new BrowserFileUploadProvider());
