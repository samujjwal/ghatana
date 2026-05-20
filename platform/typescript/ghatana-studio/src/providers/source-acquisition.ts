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

import type {
  AcquisitionJob,
  SourceAcquisitionDescriptor,
  SourceAcquisitionKind,
} from '@ghatana/artifact-contracts';

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

function getBrowserFileRelativePath(file: File): string {
  const relativePath = (file as File & { readonly webkitRelativePath?: string }).webkitRelativePath;
  return relativePath && relativePath.trim().length > 0 ? relativePath : file.name;
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
  /** Canonical source descriptor used for this acquisition. */
  readonly descriptor?: SourceAcquisitionDescriptor;
  /** Backend acquisition job boundary when acquisition is asynchronous. */
  readonly acquisitionJob?: AcquisitionJob;
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
  readonly files: FileList | readonly File[];
}

export interface PastedSourceInput {
  readonly kind: 'pasted-source';
  readonly relativePath: string;
  readonly content: string;
}

export interface RepositorySourceInput {
  readonly kind: 'github-repository' | 'gitlab-repository';
  readonly repositoryUrl: string;
  readonly ref?: string;
}

export interface ArchiveUploadInput {
  readonly kind: 'archive-upload';
  readonly file: File;
}

export interface LocalFolderInput {
  readonly kind: 'local-folder';
  readonly descriptor: {
    readonly rootLabel: string;
    readonly files: readonly SourceFileEntry[];
  };
}

/**
 * Optional backend adapter for providers that require server-side acquisition.
 *
 * Studio keeps boundary-safe defaults (pending jobs) when this client is absent.
 */
export interface SourceAcquisitionBackendClient {
  acquireRepository(
    input: RepositorySourceInput,
    options?: SourceAcquisitionOptions,
  ): Promise<SourceAcquisitionResult>;
  acquireArchive(
    input: ArchiveUploadInput,
    options?: SourceAcquisitionOptions,
  ): Promise<SourceAcquisitionResult>;
}

/**
 * Default options for source acquisition.
 */
export const DEFAULT_ACQUISITION_OPTIONS: Required<SourceAcquisitionOptions> = {
  maxFileSize: 1_000_000, // 1 MB
  allowedExtensions: ['.ts', '.tsx'],
  includeHidden: false,
};

function hasFileListShape(value: unknown): value is FileList | readonly File[] {
  return (
    Array.isArray(value) ||
    (
      typeof value === 'object' &&
      value !== null &&
      'length' in value &&
      typeof value.length === 'number'
    )
  );
}

function allowedExtensionsLabel(extensions: readonly string[]): string {
  if (extensions.length <= 1) return extensions.join('');
  return `${extensions.slice(0, -1).join(', ')} and ${extensions[extensions.length - 1]}`;
}

function megabytesLabel(bytes: number): string {
  const mb = bytes / 1_000_000;
  return Number.isInteger(mb) ? `${mb.toFixed(0)} MB` : `${mb.toFixed(1)} MB`;
}

function createJobId(prefix: string): string {
  const random = globalThis.crypto?.randomUUID?.() ?? Math.random().toString(16).slice(2);
  return `${prefix}:${random}`;
}

function createDescriptor(kind: SourceAcquisitionKind, uri: string, label?: string, ref?: string): SourceAcquisitionDescriptor {
  return {
    kind,
    uri,
    ...(ref ? { ref } : {}),
    ...(label ? { label } : {}),
  };
}

function createPendingAcquisitionJob(descriptor: SourceAcquisitionDescriptor): AcquisitionJob {
  return {
    jobId: createJobId(`acquire:${descriptor.kind}`),
    status: 'pending',
    descriptor,
    createdAt: new Date().toISOString(),
  };
}

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
      hasFileListShape(input.files)
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
      const relativePath = getBrowserFileRelativePath(file);

      // Skip hidden files if configured
      if (!opts.includeHidden && relativePath.split('/').some((part) => part.startsWith('.'))) {
        errors.push(`Skipped "${relativePath}": hidden file.`);
        continue;
      }

      // Check file extension
      const hasAllowedExtension = opts.allowedExtensions.some((ext) =>
        relativePath.endsWith(ext),
      );
      if (!hasAllowedExtension) {
        errors.push(
          `Skipped "${relativePath}": only ${allowedExtensionsLabel(opts.allowedExtensions)} files are supported.`,
        );
        continue;
      }

      // Check file size
      if (file.size > opts.maxFileSize) {
        errors.push(
          `Skipped "${relativePath}": file exceeds ${megabytesLabel(opts.maxFileSize)} limit.`,
        );
        continue;
      }

      try {
        const content = await this.readFileAsText(file);
        sources.push({
          relativePath,
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
      descriptor: createDescriptor('browser-upload', 'browser://file-input', 'Browser upload'),
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

export class PastedSourceProvider implements SourceAcquisitionProvider {
  readonly providerName = 'PastedSource';

  canHandle(input: unknown): input is PastedSourceInput {
    return (
      typeof input === 'object' &&
      input !== null &&
      'kind' in input &&
      input.kind === 'pasted-source' &&
      'relativePath' in input &&
      typeof input.relativePath === 'string' &&
      'content' in input &&
      typeof input.content === 'string'
    );
  }

  async acquire(
    input: unknown,
    options: SourceAcquisitionOptions = {},
  ): Promise<SourceAcquisitionResult> {
    if (!this.canHandle(input)) {
      return { sources: [], errors: ['Invalid input for PastedSourceProvider'], partial: false };
    }

    const opts = { ...DEFAULT_ACQUISITION_OPTIONS, ...options };
    const hasAllowedExtension = opts.allowedExtensions.some((ext) => input.relativePath.endsWith(ext));
    if (!hasAllowedExtension) {
      return {
        sources: [],
        errors: [`Skipped "${input.relativePath}": only ${allowedExtensionsLabel(opts.allowedExtensions)} files are supported.`],
        partial: false,
      };
    }

    return {
      sources: [
        {
          relativePath: input.relativePath,
          content: input.content,
          metadata: {
            size: new Blob([input.content]).size,
            contentType: 'text/plain',
          },
        },
      ],
      errors: [],
      partial: false,
      descriptor: createDescriptor('pasted-source', `pasted://${input.relativePath}`, input.relativePath),
    };
  }
}

export class LocalFolderDescriptorProvider implements SourceAcquisitionProvider {
  readonly providerName = 'LocalFolderDescriptor';

  canHandle(input: unknown): input is LocalFolderInput {
    return (
      typeof input === 'object' &&
      input !== null &&
      'kind' in input &&
      input.kind === 'local-folder' &&
      'descriptor' in input &&
      typeof input.descriptor === 'object' &&
      input.descriptor !== null
    );
  }

  async acquire(
    input: unknown,
    options: SourceAcquisitionOptions = {},
  ): Promise<SourceAcquisitionResult> {
    if (!this.canHandle(input)) {
      return { sources: [], errors: ['Invalid input for LocalFolderDescriptorProvider'], partial: false };
    }

    const opts = { ...DEFAULT_ACQUISITION_OPTIONS, ...options };
    const sources: SourceFileEntry[] = [];
    const errors: string[] = [];
    for (const source of input.descriptor.files) {
      const hasAllowedExtension = opts.allowedExtensions.some((ext) => source.relativePath.endsWith(ext));
      if (!hasAllowedExtension) {
        errors.push(`Skipped "${source.relativePath}": only ${allowedExtensionsLabel(opts.allowedExtensions)} files are supported.`);
        continue;
      }
      if (!opts.includeHidden && source.relativePath.split('/').some((part) => part.startsWith('.'))) {
        errors.push(`Skipped "${source.relativePath}": hidden file.`);
        continue;
      }
      sources.push(source);
    }
    return {
      sources,
      errors,
      partial: errors.length > 0 && sources.length > 0,
      descriptor: createDescriptor('local-folder', `local-folder://${input.descriptor.rootLabel}`, input.descriptor.rootLabel),
    };
  }
}

export class RepositorySourceProvider implements SourceAcquisitionProvider {
  readonly providerName = 'RepositorySource';

  constructor(private readonly backendClient?: SourceAcquisitionBackendClient) {}

  canHandle(input: unknown): input is RepositorySourceInput {
    return (
      typeof input === 'object' &&
      input !== null &&
      'kind' in input &&
      (input.kind === 'github-repository' || input.kind === 'gitlab-repository') &&
      'repositoryUrl' in input &&
      typeof input.repositoryUrl === 'string'
    );
  }

  async acquire(
    input: unknown,
    options: SourceAcquisitionOptions = {},
  ): Promise<SourceAcquisitionResult> {
    if (!this.canHandle(input)) {
      return { sources: [], errors: ['Invalid input for RepositorySourceProvider'], partial: false };
    }

    const descriptor = createDescriptor(
      input.kind === 'github-repository' ? 'github' : 'gitlab',
      input.repositoryUrl,
      input.repositoryUrl,
      input.ref,
    );

    if (this.backendClient !== undefined) {
      try {
        const backendResult = await this.backendClient.acquireRepository(input, options);
        return {
          ...backendResult,
          descriptor: backendResult.descriptor ?? descriptor,
          acquisitionJob:
            backendResult.acquisitionJob ??
            (backendResult.sources.length === 0 ? createPendingAcquisitionJob(descriptor) : undefined),
        };
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        return {
          sources: [],
          errors: [`Repository acquisition failed for "${input.repositoryUrl}": ${message}`],
          partial: false,
          descriptor,
          acquisitionJob: {
            ...createPendingAcquisitionJob(descriptor),
            status: 'failed',
            completedAt: new Date().toISOString(),
            errorMessage: message,
          },
        };
      }
    }

    return {
      sources: [],
      errors: [
        `${input.kind} acquisition for "${input.repositoryUrl}" requires backend acquisition job "${descriptor.kind}".`,
      ],
      partial: false,
      descriptor,
      acquisitionJob: createPendingAcquisitionJob(descriptor),
    };
  }
}

export class ArchiveUploadProvider implements SourceAcquisitionProvider {
  readonly providerName = 'ArchiveUpload';

  constructor(private readonly backendClient?: SourceAcquisitionBackendClient) {}

  canHandle(input: unknown): input is ArchiveUploadInput {
    return (
      typeof input === 'object' &&
      input !== null &&
      'kind' in input &&
      input.kind === 'archive-upload' &&
      'file' in input &&
      input.file instanceof File
    );
  }

  async acquire(
    input: unknown,
    options: SourceAcquisitionOptions = {},
  ): Promise<SourceAcquisitionResult> {
    if (!this.canHandle(input)) {
      return { sources: [], errors: ['Invalid input for ArchiveUploadProvider'], partial: false };
    }

    const descriptor = createDescriptor('archive', `archive://${input.file.name}`, input.file.name);

    if (this.backendClient !== undefined) {
      try {
        const backendResult = await this.backendClient.acquireArchive(input, options);
        return {
          ...backendResult,
          descriptor: backendResult.descriptor ?? descriptor,
          acquisitionJob:
            backendResult.acquisitionJob ??
            (backendResult.sources.length === 0 ? createPendingAcquisitionJob(descriptor) : undefined),
        };
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        return {
          sources: [],
          errors: [`Archive acquisition failed for "${input.file.name}": ${message}`],
          partial: false,
          descriptor,
          acquisitionJob: {
            ...createPendingAcquisitionJob(descriptor),
            status: 'failed',
            completedAt: new Date().toISOString(),
            errorMessage: message,
          },
        };
      }
    }

    return {
      sources: [],
      errors: [`Archive acquisition for "${input.file.name}" requires backend acquisition job "${descriptor.kind}".`],
      partial: false,
      descriptor,
      acquisitionJob: createPendingAcquisitionJob(descriptor),
    };
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
defaultProviderRegistry.register(new PastedSourceProvider());
defaultProviderRegistry.register(new LocalFolderDescriptorProvider());
defaultProviderRegistry.register(new RepositorySourceProvider());
defaultProviderRegistry.register(new ArchiveUploadProvider());
