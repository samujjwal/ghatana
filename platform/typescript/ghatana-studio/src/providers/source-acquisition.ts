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
import { ApiClient } from '@ghatana/api';
import {
  StudioProductionProfileError,
  isProductionStudioProfile,
} from '../config/studioEnvironment.js';

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

const MAX_ARCHIVE_ENTRY_COUNT = 5_000;
const MAX_TEXT_DECODE_BYTES = 5_000_000;

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
 * - Remote URL acquisition over HTTP/HTTPS
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
  getAcquisitionJob?(jobId: string): Promise<AcquisitionJob>;
}

export type SourceAcquisitionRuntimeMode = 'local' | 'pending-backend' | 'production-acquisition';

export interface SourceAcquisitionRuntimeProfile {
  readonly mode: SourceAcquisitionRuntimeMode;
  readonly exposeRepositoryAndArchiveProviders: boolean;
  readonly backendKind: 'none' | 'kernel' | 'browser';
}

/**
 * Production-grade backend client implementation for repository and archive acquisition.
 *
 * This implementation provides real acquisition capabilities:
 * - GitHub/GitLab repository cloning via API
 * - Archive unpacking (zip, tar, tar.gz)
 * - Source file filtering and validation
 * - Proper error handling and observability
 */
export class ProductionSourceAcquisitionBackendClient implements SourceAcquisitionBackendClient {
  private readonly githubApiClient: ApiClient;
  private readonly gitlabApiClient: ApiClient;

  constructor(
    private readonly config: {
      readonly githubApiUrl?: string;
      readonly gitlabApiUrl?: string;
      readonly maxRepositorySizeBytes?: number;
      readonly maxArchiveSizeBytes?: number;
    } = {},
  ) {
    this.githubApiClient = new ApiClient({ baseUrl: config.githubApiUrl ?? 'https://api.github.com' });
    this.gitlabApiClient = new ApiClient({ baseUrl: config.gitlabApiUrl ?? 'https://gitlab.com/api/v4' });
  }

  async acquireRepository(
    input: RepositorySourceInput,
    options: SourceAcquisitionOptions = {},
  ): Promise<SourceAcquisitionResult> {
    const opts = { ...DEFAULT_ACQUISITION_OPTIONS, ...options };
    const maxBytes = this.config.maxRepositorySizeBytes ?? opts.maxFileSize * 100; // Allow larger for repos

    try {
      const sources = await this.fetchRepositoryContents(input, opts, maxBytes);
      return {
        sources,
        errors: [],
        partial: false,
        descriptor: createDescriptor(
          input.kind === 'github-repository' ? 'github' : 'gitlab',
          input.repositoryUrl,
          input.repositoryUrl,
          input.ref,
        ),
      };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      return {
        sources: [],
        errors: [`Repository acquisition failed: ${message}`],
        partial: false,
        descriptor: createDescriptor(
          input.kind === 'github-repository' ? 'github' : 'gitlab',
          input.repositoryUrl,
          input.repositoryUrl,
          input.ref,
        ),
      };
    }
  }

  async acquireArchive(
    input: ArchiveUploadInput,
    options: SourceAcquisitionOptions = {},
  ): Promise<SourceAcquisitionResult> {
    const opts = { ...DEFAULT_ACQUISITION_OPTIONS, ...options };
    const maxBytes = this.config.maxArchiveSizeBytes ?? opts.maxFileSize * 50; // Allow larger for archives

    try {
      const sources = await this.unpackArchive(input.file, opts, maxBytes);
      return {
        sources,
        errors: [],
        partial: false,
        descriptor: createDescriptor('archive', `archive://${input.file.name}`, input.file.name),
      };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      return {
        sources: [],
        errors: [`Archive acquisition failed: ${message}`],
        partial: false,
        descriptor: createDescriptor('archive', `archive://${input.file.name}`, input.file.name),
      };
    }
  }

  private async fetchRepositoryContents(
    input: RepositorySourceInput,
    options: SourceAcquisitionOptions,
    maxBytes: number,
  ): Promise<readonly SourceFileEntry[]> {
    const { owner, repo, ref } = this.parseRepositoryUrl(input.repositoryUrl, input.ref);

    if (input.kind === 'github-repository') {
      return this.fetchGitHubRepository(owner, repo, ref, options, maxBytes);
    } else {
      return this.fetchGitLabRepository(owner, repo, ref, options, maxBytes);
    }
  }

  private parseRepositoryUrl(url: string, ref?: string): { readonly owner: string; readonly repo: string; readonly ref: string } {
    // Parse GitHub: https://github.com/owner/repo or git@github.com:owner/repo.git
    const githubMatch = url.match(/(?:github\.com[/:]|git@github\.com:)([^/]+)\/([^/.?]+)/);
    if (githubMatch) {
      return { owner: githubMatch[1], repo: githubMatch[2], ref: ref ?? 'main' };
    }

    // Parse GitLab: https://gitlab.com/owner/repo or git@gitlab.com:owner/repo.git
    const gitlabMatch = url.match(/(?:gitlab\.com[/:]|git@gitlab\.com:)([^/]+)\/([^/.?]+)/);
    if (gitlabMatch) {
      return { owner: gitlabMatch[1], repo: gitlabMatch[2], ref: ref ?? 'main' };
    }

    throw new Error(`Invalid repository URL: ${url}`);
  }

  private async fetchGitHubRepository(
    owner: string,
    repo: string,
    ref: string,
    options: SourceAcquisitionOptions,
    maxBytes: number,
  ): Promise<readonly SourceFileEntry[]> {
    const response = await this.githubApiClient.get<ArrayBuffer>(`/repos/${owner}/${repo}/zipball/${ref}`);
    if (response.raw.status !== 200) {
      throw new Error(`GitHub API error: ${response.raw.status} ${response.raw.statusText}`);
    }
    if (response.data.byteLength > maxBytes) {
      throw new Error(`Repository size (${response.data.byteLength} bytes) exceeds maximum (${maxBytes} bytes)`);
    }

    return this.unpackZipBuffer(response.data, options, maxBytes);
  }

  private async fetchGitLabRepository(
    owner: string,
    repo: string,
    ref: string,
    options: SourceAcquisitionOptions,
    maxBytes: number,
  ): Promise<readonly SourceFileEntry[]> {
    // Encode the project path (owner/repo -> owner%2Frepo)
    const encodedProject = encodeURIComponent(`${owner}/${repo}`);
    const response = await this.gitlabApiClient.get<ArrayBuffer>(`/projects/${encodedProject}/repository/archive`, {
      query: { sha: ref },
    });
    if (response.raw.status !== 200) {
      throw new Error(`GitLab API error: ${response.raw.status} ${response.raw.statusText}`);
    }
    if (response.data.byteLength > maxBytes) {
      throw new Error(`Repository size (${response.data.byteLength} bytes) exceeds maximum (${maxBytes} bytes)`);
    }

    return this.unpackZipBuffer(response.data, options, maxBytes);
  }

  private async unpackArchive(
    file: File,
    options: SourceAcquisitionOptions,
    maxBytes: number,
  ): Promise<readonly SourceFileEntry[]> {
    if (file.size > maxBytes) {
      throw new Error(`Archive size (${file.size} bytes) exceeds maximum (${maxBytes} bytes)`);
    }

    const arrayBuffer = await file.arrayBuffer();

    // Detect archive type by magic bytes/header signatures.
    const header = new Uint8Array(arrayBuffer);
    const isZip = header[0] === 0x50 && header[1] === 0x4b; // PK signature
    const isTarGz = header[0] === 0x1f && header[1] === 0x8b; // GZIP signature
    const isTar = this.isTarArchive(header);

    if (isZip) {
      return this.unpackZipBuffer(arrayBuffer, options, maxBytes);
    } else if (isTarGz) {
      return this.unpackTarGzBuffer(arrayBuffer, options, maxBytes);
    } else if (isTar) {
      return this.unpackTarBuffer(arrayBuffer, options, maxBytes);
    } else {
      throw new Error('Unsupported archive format (supported: ZIP, TAR, TAR.GZ)');
    }
  }

  private async unpackZipBuffer(
    buffer: ArrayBuffer,
    options: SourceAcquisitionOptions,
    maxTotalUncompressedBytes: number,
  ): Promise<readonly SourceFileEntry[]> {
    const sources: SourceFileEntry[] = [];
    const view = new DataView(buffer);
    let entryCount = 0;
    let totalUncompressedBytes = 0;

    // ZIP format: local file header signature 0x04034b50
    let offset = 0;

    while (offset < view.byteLength - 4) {
      const signature = view.getUint32(offset, true);
      if (signature !== 0x04034b50) {
        // End of central directory or other record
        break;
      }

      const flags = view.getUint16(offset + 6, true);
      const compressionMethod = view.getUint16(offset + 8, true);
      const compressedSize = view.getUint32(offset + 18, true);
      const uncompressedSize = view.getUint32(offset + 22, true);
      const fileNameLength = view.getUint16(offset + 26, true);
      const extraFieldLength = view.getUint16(offset + 28, true);
      const hasDataDescriptor = (flags & 0x0008) !== 0;

      if (hasDataDescriptor) {
        throw new Error('ZIP archives with data descriptors are not supported by the browser unpacker');
      }

      const fileNameStart = offset + 30;
      const fileNameBytes = new Uint8Array(buffer, fileNameStart, fileNameLength);
      const fileName = normalizeSourceEntryPath(new TextDecoder().decode(fileNameBytes));

      // Skip directories and macOS metadata
      if (fileName.endsWith('/') || fileName.startsWith('__MACOSX/') || fileName.includes('.DS_Store')) {
        offset += 30 + fileNameLength + extraFieldLength + compressedSize;
        continue;
      }

      const fileDataStart = offset + 30 + fileNameLength + extraFieldLength;

      if (uncompressedSize > 0) {
        entryCount += 1;
        if (entryCount > MAX_ARCHIVE_ENTRY_COUNT) {
          throw new Error(`Archive entry count exceeds maximum (${MAX_ARCHIVE_ENTRY_COUNT})`);
        }
        if (uncompressedSize > options.maxFileSize!) {
          throw new Error(`Archive entry "${fileName}" exceeds file size limit (${uncompressedSize} bytes)`);
        }
        totalUncompressedBytes += uncompressedSize;
        if (totalUncompressedBytes > maxTotalUncompressedBytes) {
          throw new Error(`Archive uncompressed size exceeds maximum (${maxTotalUncompressedBytes} bytes)`);
        }

        const compressedData = new Uint8Array(buffer, fileDataStart, compressedSize);
        const fileData = await this.decodeZipEntry(compressionMethod, compressedData, uncompressedSize);
        const content = decodeSourceText(fileData, fileName);

        const hasAllowedExtension = options.allowedExtensions?.some((ext) => fileName.endsWith(ext)) ?? true;
        const includeFile = options.includeHidden || !fileName.split('/').some((part) => part.startsWith('.'));

        if (hasAllowedExtension && includeFile) {
          sources.push({
            relativePath: fileName,
            content,
            metadata: {
              size: uncompressedSize,
              contentType: inferContentTypeFromPath(fileName),
            },
          });
        }
      }

      offset += 30 + fileNameLength + extraFieldLength + compressedSize;
    }

    return sources;
  }

  private async unpackTarGzBuffer(
    buffer: ArrayBuffer,
    options: SourceAcquisitionOptions,
    maxTotalUncompressedBytes: number,
  ): Promise<readonly SourceFileEntry[]> {
    const decompressedTar = await this.decompressBuffer(buffer, 'gzip');
    if (decompressedTar.byteLength > maxTotalUncompressedBytes) {
      throw new Error(`Archive uncompressed size exceeds maximum (${maxTotalUncompressedBytes} bytes)`);
    }
    return this.unpackTarBuffer(decompressedTar, options, maxTotalUncompressedBytes);
  }

  private async decodeZipEntry(
    compressionMethod: number,
    compressedData: Uint8Array,
    expectedSize: number,
  ): Promise<Uint8Array> {
    if (compressionMethod === 0) {
      return compressedData;
    }

    if (compressionMethod === 8) {
      const compressedEntry = new Uint8Array(compressedData.byteLength);
      compressedEntry.set(compressedData);
      const inflated = await this.decompressBuffer(compressedEntry.buffer, 'deflate-raw');
      const inflatedBytes = new Uint8Array(inflated);
      if (expectedSize > 0 && inflatedBytes.byteLength !== expectedSize) {
        throw new Error(`ZIP entry size mismatch: expected ${expectedSize}, got ${inflatedBytes.byteLength}`);
      }
      return inflatedBytes;
    }

    throw new Error(`Unsupported ZIP compression method: ${compressionMethod}`);
  }

  private async decompressBuffer(
    buffer: ArrayBuffer,
    format: 'gzip' | 'deflate-raw',
  ): Promise<ArrayBuffer> {
    if (typeof DecompressionStream !== 'function') {
      throw new Error(`DecompressionStream is unavailable; cannot process ${format} data in this runtime`);
    }

    const decompressionStream = new DecompressionStream(format);
    const stream = new Blob([buffer]).stream().pipeThrough(decompressionStream);
    return new Response(stream).arrayBuffer();
  }

  private isTarArchive(header: Uint8Array): boolean {
    if (header.byteLength < 512) {
      return false;
    }

    const signature = new TextDecoder().decode(header.slice(257, 262));
    return signature === 'ustar';
  }

  private unpackTarBuffer(
    buffer: ArrayBuffer,
    options: SourceAcquisitionOptions,
    maxTotalUncompressedBytes: number,
  ): readonly SourceFileEntry[] {
    const sources: SourceFileEntry[] = [];
    const bytes = new Uint8Array(buffer);
    let offset = 0;
    let entryCount = 0;
    let totalUncompressedBytes = 0;

    while (offset + 512 <= bytes.byteLength) {
      const header = bytes.slice(offset, offset + 512);
      if (this.isZeroTarBlock(header)) {
        break;
      }

      const name = this.readTarString(header, 0, 100);
      const prefix = this.readTarString(header, 345, 155);
      const typeFlag = this.readTarString(header, 156, 1);
      const sizeOctal = this.readTarString(header, 124, 12);
      const fileSize = parseInt(sizeOctal.trim() || '0', 8);
      const relativePath = normalizeSourceEntryPath(prefix.length > 0 ? `${prefix}/${name}` : name);

      const dataStart = offset + 512;
      const dataEnd = dataStart + fileSize;
      if (dataEnd > bytes.byteLength) {
        throw new Error('Invalid TAR archive: entry exceeds archive size');
      }

      const isRegularFile = typeFlag === '' || typeFlag === '0';
      if (isRegularFile && fileSize > 0 && !relativePath.endsWith('/')) {
        entryCount += 1;
        if (entryCount > MAX_ARCHIVE_ENTRY_COUNT) {
          throw new Error(`Archive entry count exceeds maximum (${MAX_ARCHIVE_ENTRY_COUNT})`);
        }
        if (fileSize > options.maxFileSize!) {
          throw new Error(`Archive entry "${relativePath}" exceeds file size limit (${fileSize} bytes)`);
        }
        totalUncompressedBytes += fileSize;
        if (totalUncompressedBytes > maxTotalUncompressedBytes) {
          throw new Error(`Archive uncompressed size exceeds maximum (${maxTotalUncompressedBytes} bytes)`);
        }

        const hasAllowedExtension = options.allowedExtensions?.some((ext) => relativePath.endsWith(ext)) ?? true;
        const includeFile = options.includeHidden || !relativePath.split('/').some((part) => part.startsWith('.'));

        if (hasAllowedExtension && includeFile) {
          const content = decodeSourceText(bytes.slice(dataStart, dataEnd), relativePath);
          sources.push({
            relativePath,
            content,
            metadata: {
              size: fileSize,
              contentType: inferContentTypeFromPath(relativePath),
            },
          });
        }
      }

      const paddedSize = Math.ceil(fileSize / 512) * 512;
      offset = dataStart + paddedSize;
    }

    return sources;
  }

  private isZeroTarBlock(block: Uint8Array): boolean {
    for (const byte of block) {
      if (byte !== 0) {
        return false;
      }
    }
    return true;
  }

  private readTarString(bytes: Uint8Array, start: number, length: number): string {
    const slice = bytes.slice(start, start + length);
    const raw = new TextDecoder().decode(slice);
    return raw.replace(/\0.*$/, '').trim();
  }
}

export class KernelSourceAcquisitionBackendClient implements SourceAcquisitionBackendClient {
  private readonly apiClient: ApiClient;

  constructor(
    private readonly config: {
      readonly baseUrl: string;
      readonly tenantId: string;
      readonly workspaceId: string;
      readonly projectId: string;
      readonly authToken: string;
    },
  ) {
    this.apiClient = new ApiClient({ baseUrl: config.baseUrl });
  }

  async acquireRepository(
    input: RepositorySourceInput,
    options: SourceAcquisitionOptions = {},
  ): Promise<SourceAcquisitionResult> {
    return this.request('/api/v1/studio/source-acquisition/repository', { input, options });
  }

  async acquireArchive(
    input: ArchiveUploadInput,
    options: SourceAcquisitionOptions = {},
  ): Promise<SourceAcquisitionResult> {
    const content = await input.file.arrayBuffer();
    const encoded = this.encodeBase64(content);
    return this.request('/api/v1/studio/source-acquisition/archive', {
      input: {
        kind: input.kind,
        file: {
          name: input.file.name,
          type: input.file.type,
          size: input.file.size,
          contentBase64: encoded,
        },
      },
      options,
    });
  }

  async getAcquisitionJob(jobId: string): Promise<AcquisitionJob> {
    const response = await this.apiClient.get<AcquisitionJob>(`/api/v1/studio/source-acquisition/jobs/${encodeURIComponent(jobId)}`, {
      headers: this.headers,
    });

    return response.data;
  }

  private async request(path: string, payload: unknown): Promise<SourceAcquisitionResult> {
    const response = await this.apiClient.post<SourceAcquisitionResult>(path, {
      headers: this.headers,
      body: payload,
    });

    return response.data;
  }

  private get headers(): Record<string, string> {
    return {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${this.config.authToken}`,
      'x-ghatana-tenant-id': this.config.tenantId,
      'x-ghatana-workspace-id': this.config.workspaceId,
      'x-ghatana-project-id': this.config.projectId,
      'x-tenant-id': this.config.tenantId,
      'x-workspace-id': this.config.workspaceId,
      'x-project-id': this.config.projectId,
    };
  }

  private encodeBase64(buffer: ArrayBuffer): string {
    const bytes = new Uint8Array(buffer);
    let binary = '';
    for (const byte of bytes) {
      binary += String.fromCharCode(byte);
    }
    return btoa(binary);
  }
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

function normalizeSourceEntryPath(path: string): string {
  const normalized = path.replace(/\\/g, '/').replace(/^\.\/+/, '');
  const segments = normalized.split('/');
  const hasUnsafeSegment = segments.some((segment) => segment === '' || segment === '.' || segment === '..');
  if (
    normalized.trim().length === 0 ||
    normalized.includes('\0') ||
    normalized.startsWith('/') ||
    /^[A-Za-z]:\//.test(normalized) ||
    hasUnsafeSegment
  ) {
    throw new Error(`Unsafe source path rejected: ${path}`);
  }
  return normalized;
}

function decodeSourceText(bytes: Uint8Array, relativePath: string): string {
  if (bytes.byteLength > MAX_TEXT_DECODE_BYTES) {
    throw new Error(`Source file "${relativePath}" exceeds text decode limit (${MAX_TEXT_DECODE_BYTES} bytes)`);
  }
  if (bytes.includes(0)) {
    throw new Error(`Source file "${relativePath}" appears to be binary`);
  }
  try {
    return new TextDecoder('utf-8', { fatal: true }).decode(bytes);
  } catch {
    throw new Error(`Source file "${relativePath}" is not valid UTF-8 text`);
  }
}

function allowedExtensionsLabel(extensions: readonly string[]): string {
  if (extensions.length <= 1) return extensions.join('');
  return `${extensions.slice(0, -1).join(', ')} and ${extensions[extensions.length - 1]}`;
}

function inferContentTypeFromPath(relativePath: string): string {
  if (relativePath.endsWith('.tsx') || relativePath.endsWith('.ts')) return 'text/typescript';
  if (relativePath.endsWith('.jsx') || relativePath.endsWith('.js')) return 'text/javascript';
  if (relativePath.endsWith('.css')) return 'text/css';
  if (relativePath.endsWith('.json')) return 'application/json';
  if (relativePath.endsWith('.md')) return 'text/markdown';
  if (relativePath.endsWith('.html') || relativePath.endsWith('.htm')) return 'text/html';
  return 'text/plain';
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
      let relativePath: string;
      try {
        relativePath = normalizeSourceEntryPath(getBrowserFileRelativePath(file));
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        errors.push(`Skipped "${file.name}": ${message}`);
        continue;
      }

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
        const content = await this.readFileAsText(file, relativePath);
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
  private readFileAsText(file: File, relativePath: string): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => {
        const result = reader.result;
        if (typeof result !== 'string') {
          reject(new Error(`Failed to read file as text: ${file.name}`));
          return;
        }
        try {
          resolve(decodeSourceText(new TextEncoder().encode(result), relativePath));
        } catch (error) {
          reject(error);
        }
      };
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
    let relativePath: string;
    try {
      relativePath = normalizeSourceEntryPath(input.relativePath);
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      return { sources: [], errors: [`Skipped "${input.relativePath}": ${message}`], partial: false };
    }
    const hasAllowedExtension = opts.allowedExtensions.some((ext) => relativePath.endsWith(ext));
    if (!hasAllowedExtension) {
      return {
        sources: [],
        errors: [`Skipped "${relativePath}": only ${allowedExtensionsLabel(opts.allowedExtensions)} files are supported.`],
        partial: false,
      };
    }
    const size = new Blob([input.content]).size;
    if (size > opts.maxFileSize) {
      return {
        sources: [],
        errors: [`Skipped "${relativePath}": file exceeds ${megabytesLabel(opts.maxFileSize)} limit.`],
        partial: false,
      };
    }
    let content: string;
    try {
      content = decodeSourceText(new TextEncoder().encode(input.content), relativePath);
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      return { sources: [], errors: [`Skipped "${relativePath}": ${message}`], partial: false };
    }

    return {
      sources: [
        {
          relativePath,
          content,
          metadata: {
            size,
            contentType: 'text/plain',
          },
        },
      ],
      errors: [],
      partial: false,
      descriptor: createDescriptor('pasted-source', `pasted://${relativePath}`, relativePath),
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
      let relativePath: string;
      try {
        relativePath = normalizeSourceEntryPath(source.relativePath);
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        errors.push(`Skipped "${source.relativePath}": ${message}`);
        continue;
      }
      const hasAllowedExtension = opts.allowedExtensions.some((ext) => relativePath.endsWith(ext));
      if (!hasAllowedExtension) {
        errors.push(`Skipped "${relativePath}": only ${allowedExtensionsLabel(opts.allowedExtensions)} files are supported.`);
        continue;
      }
      if (!opts.includeHidden && relativePath.split('/').some((part) => part.startsWith('.'))) {
        errors.push(`Skipped "${relativePath}": hidden file.`);
        continue;
      }
      const size = source.metadata?.size ?? new Blob([source.content]).size;
      if (size > opts.maxFileSize) {
        errors.push(`Skipped "${relativePath}": file exceeds ${megabytesLabel(opts.maxFileSize)} limit.`);
        continue;
      }
      let content: string;
      try {
        content = decodeSourceText(new TextEncoder().encode(source.content), relativePath);
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        errors.push(`Skipped "${relativePath}": ${message}`);
        continue;
      }
      sources.push({
        ...source,
        relativePath,
        content,
      });
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
 * Repository and archive providers use pending-job boundaries by default.
 */
export const defaultProviderRegistry = new SourceAcquisitionProviderRegistry();
defaultProviderRegistry.register(new BrowserFileUploadProvider());
defaultProviderRegistry.register(new PastedSourceProvider());
defaultProviderRegistry.register(new LocalFolderDescriptorProvider());
defaultProviderRegistry.register(new RepositorySourceProvider());
defaultProviderRegistry.register(new ArchiveUploadProvider());

/**
 * Create a provider registry with production-grade backend acquisition enabled.
 *
 * This registry will actually fetch GitHub/GitLab repositories and unpack archives
 * instead of returning pending acquisition jobs.
 *
 * @param config - Backend client configuration (API URLs, size limits)
 * @returns Configured provider registry with real acquisition capabilities
 */
export function createProductionProviderRegistry(
  config?: ConstructorParameters<typeof ProductionSourceAcquisitionBackendClient>[0],
): SourceAcquisitionProviderRegistry {
  const registry = new SourceAcquisitionProviderRegistry();
  const backendClient = new ProductionSourceAcquisitionBackendClient(config);

  registry.register(new BrowserFileUploadProvider());
  registry.register(new PastedSourceProvider());
  registry.register(new LocalFolderDescriptorProvider());
  registry.register(new RepositorySourceProvider(backendClient));
  registry.register(new ArchiveUploadProvider(backendClient));

  return registry;
}

export function createKernelProviderRegistry(config: ConstructorParameters<typeof KernelSourceAcquisitionBackendClient>[0]): SourceAcquisitionProviderRegistry {
  const registry = new SourceAcquisitionProviderRegistry();
  const backendClient = new KernelSourceAcquisitionBackendClient(config);

  registry.register(new BrowserFileUploadProvider());
  registry.register(new PastedSourceProvider());
  registry.register(new LocalFolderDescriptorProvider());
  registry.register(new RepositorySourceProvider(backendClient));
  registry.register(new ArchiveUploadProvider(backendClient));

  return registry;
}

export function resolveSourceAcquisitionRuntimeProfileForEnv(
  env?: Record<string, string | undefined>,
): SourceAcquisitionRuntimeProfile {
  const productionProfile = isProductionStudioProfile(env);
  const productionAcquisitionEnabled = env?.VITE_STUDIO_ENABLE_PRODUCTION_ACQUISITION === 'true';
  const backendKind = env?.VITE_STUDIO_SOURCE_ACQUISITION_BACKEND === 'kernel'
    ? 'kernel'
    : env?.VITE_STUDIO_SOURCE_ACQUISITION_BACKEND === 'browser'
      ? 'browser'
      : 'none';
  const exposeRepositoryAndArchiveProviders =
    env?.VITE_STUDIO_EXPOSE_REPOSITORY_ARCHIVE_PROVIDERS !== 'false';

  if (productionProfile && exposeRepositoryAndArchiveProviders) {
    if (!productionAcquisitionEnabled) {
      throw new StudioProductionProfileError(
        'Production Studio exposes repository/archive providers but production acquisition is disabled.',
      );
    }
    if (backendKind !== 'kernel') {
      throw new StudioProductionProfileError(
        'Production Studio repository/archive acquisition must use VITE_STUDIO_SOURCE_ACQUISITION_BACKEND=kernel.',
      );
    }
  }

  if (!exposeRepositoryAndArchiveProviders) {
    return { mode: 'local', exposeRepositoryAndArchiveProviders, backendKind };
  }

  if (!productionAcquisitionEnabled) {
    return { mode: 'pending-backend', exposeRepositoryAndArchiveProviders, backendKind: 'none' };
  }

  return { mode: 'production-acquisition', exposeRepositoryAndArchiveProviders, backendKind };
}

/**
 * Resolve the source acquisition provider registry from runtime environment.
 *
 * Uses boundary-safe defaults unless production acquisition is explicitly enabled.
 */
export function resolveProviderRegistryForEnv(
  env?: Record<string, string | undefined>,
): SourceAcquisitionProviderRegistry {
  const profile = resolveSourceAcquisitionRuntimeProfileForEnv(env);
  if (profile.mode !== 'production-acquisition') {
    return defaultProviderRegistry;
  }

  if (profile.backendKind === 'kernel') {
    const baseUrl = env?.VITE_GHATANA_KERNEL_API_BASE_URL?.trim();
    const tenantId = env?.VITE_STUDIO_TENANT_ID?.trim();
    const workspaceId = env?.VITE_STUDIO_WORKSPACE_ID?.trim();
    const projectId = env?.VITE_STUDIO_PROJECT_ID?.trim();
    const authToken = env?.VITE_STUDIO_AUTH_TOKEN?.trim();

    if (!baseUrl || !tenantId || !workspaceId || !projectId || !authToken) {
      throw new StudioProductionProfileError(
        'Kernel source acquisition requires kernel base URL, tenant, workspace, project, and auth token.',
      );
    }

    return createKernelProviderRegistry({ baseUrl, tenantId, workspaceId, projectId, authToken });
  }

  return createProductionProviderRegistry({
    githubApiUrl: env?.VITE_STUDIO_GITHUB_API_URL,
    gitlabApiUrl: env?.VITE_STUDIO_GITLAB_API_URL,
  });
}
