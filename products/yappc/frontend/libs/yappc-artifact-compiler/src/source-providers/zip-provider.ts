/**
 * @fileoverview ZIP archive source provider.
 *
 * P1-4: Hardened with diagnostics for skipped/unsafe/unsupported compression, cleanup contract.
 *
 * Resolves a ZIP archive (local path or HTTP/HTTPS URL) into a RepositorySnapshot
 * by extracting it to a temp directory.
 *
 * Uses Node's built-in `zlib` + streaming approach where possible.
 * Falls back gracefully when a file inside the archive is too large.
 *
 * Locator formats supported:
 *   - Absolute/relative path to a .zip file
 *   - http:// or https:// URL pointing to a .zip file
 */

import { mkdir, readFile, writeFile, rm } from 'fs/promises';
import { join, basename, normalize, resolve } from 'path';
import { tmpdir } from 'os';
import { createHash } from 'crypto';
import type {
  SourceProvider,
  SourceProviderOptions,
  SourceProviderLocator,
  SnapshotFile,
  RepositorySnapshot,
  ProviderDiagnostic,
} from './types';
import {
  assertTsSourceProviderWorkerOnly,
  SourceProviderError,
  sourceLocatorToString,
  validateCredentialPolicy,
} from './types';
import type { SnapshotRef } from '../graph/types';

// ============================================================================
// Minimal ZIP parser
// ============================================================================

/**
 * Very lightweight ZIP Central Directory reader.
 * Supports Deflate and stored entries only (covers ~99% of real archives).
 * Does not require any native modules.
 */

const DEFLATE = 8;
const STORED = 0;

interface ZipEntry {
  readonly name: string;
  readonly compressedSize: number;
  readonly uncompressedSize: number;
  readonly compressionMethod: number;
  readonly dataOffset: number;
  readonly isDirectory: boolean;
}

function readUInt32LE(buf: Buffer, offset: number): number {
  return buf.readUInt32LE(offset);
}

function readUInt16LE(buf: Buffer, offset: number): number {
  return buf.readUInt16LE(offset);
}

/**
 * Parse all entries from the central directory.
 * Returns an array of ZipEntry descriptors (no data yet).
 */
function parseZipCentralDirectory(buf: Buffer): ZipEntry[] {
  // Find End of Central Directory record (EOCD): signature 0x06054b50
  let eocdOffset = -1;
  for (let i = buf.length - 22; i >= 0; i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) {
      eocdOffset = i;
      break;
    }
  }
  if (eocdOffset < 0) throw new Error('Not a valid ZIP file: EOCD signature not found');

  const cdOffset = buf.readUInt32LE(eocdOffset + 16);
  const cdEntries = buf.readUInt16LE(eocdOffset + 10);

  const entries: ZipEntry[] = [];
  let pos = cdOffset;
  for (let i = 0; i < cdEntries; i++) {
    if (pos + 46 > buf.length) break;
    if (buf.readUInt32LE(pos) !== 0x02014b50) break; // Central directory header

    const compressionMethod = readUInt16LE(buf, pos + 10);
    const compressedSize = readUInt32LE(buf, pos + 20);
    const uncompressedSize = readUInt32LE(buf, pos + 24);
    const fileNameLength = readUInt16LE(buf, pos + 28);
    const extraFieldLength = readUInt16LE(buf, pos + 30);
    const commentLength = readUInt16LE(buf, pos + 32);
    const localHeaderOffset = readUInt32LE(buf, pos + 42);

    const name = buf.toString('utf-8', pos + 46, pos + 46 + fileNameLength);

    // Calculate local file header data offset
    const localHeaderStart = localHeaderOffset;
    if (localHeaderStart + 30 > buf.length) break;
    const localFileNameLength = readUInt16LE(buf, localHeaderStart + 26);
    const localExtraLength = readUInt16LE(buf, localHeaderStart + 28);
    const dataOffset = localHeaderStart + 30 + localFileNameLength + localExtraLength;

    entries.push({
      name,
      compressedSize,
      uncompressedSize,
      compressionMethod,
      dataOffset,
      isDirectory: name.endsWith('/'),
    });

    pos += 46 + fileNameLength + extraFieldLength + commentLength;
  }

  return entries;
}

function inflateEntry(buf: Buffer, entry: ZipEntry): Buffer {
  if (entry.compressionMethod === STORED) {
    return buf.slice(entry.dataOffset, entry.dataOffset + entry.compressedSize);
  }
  if (entry.compressionMethod === DEFLATE) {
    const compressed = buf.slice(entry.dataOffset, entry.dataOffset + entry.compressedSize);
    // Use Node's built-in zlib (raw deflate)
    const { inflateRawSync } = require('zlib') as typeof import('zlib');
    return inflateRawSync(compressed);
  }
  // P1-4: Throw error for unsupported compression methods (will be caught and diagnosed)
  throw new Error(`Unsupported ZIP compression method: ${entry.compressionMethod} (only DEFLATE and STORED are supported)`);
}

// ============================================================================
// Download helper (HTTP/HTTPS)
// ============================================================================

async function downloadToBuffer(url: string, timeoutMs: number): Promise<Buffer> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(url, { signal: controller.signal });
    if (!response.ok) {
      throw new Error(`HTTP ${response.status} fetching ${url}`);
    }
    const arrayBuffer = await response.arrayBuffer();
    return Buffer.from(arrayBuffer);
  } finally {
    clearTimeout(timer);
  }
}

// ============================================================================
// ZIP Provider
// ============================================================================

export class ZipProvider implements SourceProvider {
  readonly providerId = 'zip' as const;

  canHandle(locator: string): boolean {
    const lower = locator.toLowerCase();
    return lower.endsWith('.zip') || lower.includes('.zip?') || lower.includes('.zip#');
  }

  async resolve(locator: SourceProviderLocator, options?: SourceProviderOptions): Promise<RepositorySnapshot> {
    validateCredentialPolicy(options?.scope, options?.credentials);
    assertTsSourceProviderWorkerOnly(this.providerId, options);
    const normalizedLocator = sourceLocatorToString(locator);
    const maxFileSizeBytes = options?.maxFileSizeBytes ?? 10 * 1024 * 1024;
    const maxFiles = options?.maxFiles ?? 20_000;
    const timeoutMs = options?.requestTimeoutMs ?? 60_000;
    const diagnostics: ProviderDiagnostic[] = [];

    let zipBuffer: Buffer;

    if (normalizedLocator.startsWith('http://') || normalizedLocator.startsWith('https://')) {
      try {
        zipBuffer = await downloadToBuffer(normalizedLocator, timeoutMs);
      } catch (err) {
        throw new SourceProviderError(this.providerId, normalizedLocator, 'Failed to download ZIP', err);
      }
    } else {
      try {
        zipBuffer = await readFile(normalizedLocator);
      } catch (err) {
        throw new SourceProviderError(this.providerId, normalizedLocator, 'Failed to read ZIP file', err);
      }
    }

    // Compute a stable content hash for the snapshotRef
    const contentSha = createHash('sha256').update(zipBuffer).digest('hex');
    const archiveName = basename(normalizedLocator).replace(/\.zip$/, '');

    const snapshotRef: SnapshotRef = {
      provider: 'zip',
      repoId: `zip/${archiveName}`,
      commitSha: contentSha.slice(0, 40),
    };

    let entries: ZipEntry[];
    try {
      entries = parseZipCentralDirectory(zipBuffer);
    } catch (err) {
      throw new SourceProviderError(this.providerId, normalizedLocator, 'Failed to parse ZIP central directory', err);
    }

    // P1-4: Deterministic temp directory using content hash
    const tempRoot = join(
      options?.tempDir ?? tmpdir(),
      `yappc-zip-${archiveName}-${contentSha.slice(0, 8)}`
    );
    await mkdir(tempRoot, { recursive: true });

    const files: SnapshotFile[] = [];
    let fileCount = 0;

    // Strip common top-level directory prefix (GitHub archive convention)
    const topLevelDirs = new Set(
      entries
        .filter(e => e.isDirectory && e.name.split('/').length === 2)
        .map(e => e.name.split('/')[0]!),
    );
    const stripPrefix = topLevelDirs.size === 1 ? ([...topLevelDirs][0]! + '/') : '';

    for (const entry of entries) {
      if (fileCount >= maxFiles) {
        diagnostics.push({
          level: 'warning',
          code: 'ZIP_MAX_FILES_REACHED',
          message: `ZIP snapshot stopped after reaching maxFiles=${maxFiles}.`,
          timestamp: new Date().toISOString(),
          metadata: { maxFiles },
        });
        break;
      }

      if (entry.isDirectory) continue;

      const metadataChecksum = `zip-entry:${contentSha}:${entry.name}`;

      if (entry.uncompressedSize > maxFileSizeBytes) {
        files.push({
          relativePath: entry.name,
          materialized: false,
          sizeBytes: entry.uncompressedSize,
          lastModifiedAt: new Date().toISOString(),
          checksum: metadataChecksum,
        });
        diagnostics.push({
          level: 'warning',
          code: 'ZIP_FILE_SKIPPED_MAX_SIZE',
          message: `Skipped oversized ZIP entry ${entry.name}.`,
          timestamp: new Date().toISOString(),
          resourcePath: entry.name,
          metadata: { sizeBytes: entry.uncompressedSize, maxFileSizeBytes },
        });
        continue;
      }

      if (entry.compressionMethod !== DEFLATE && entry.compressionMethod !== STORED) {
        files.push({
          relativePath: entry.name,
          materialized: false,
          sizeBytes: entry.uncompressedSize,
          lastModifiedAt: new Date().toISOString(),
          checksum: metadataChecksum,
        });
        diagnostics.push({
          level: 'warning',
          code: 'ZIP_UNSUPPORTED_COMPRESSION',
          message: `Skipped ZIP entry ${entry.name} with unsupported compression method ${entry.compressionMethod}.`,
          timestamp: new Date().toISOString(),
          resourcePath: entry.name,
          metadata: { compressionMethod: entry.compressionMethod },
        });
        continue;
      }

      const rawName = entry.name;
      const relativePath = stripPrefix && rawName.startsWith(stripPrefix)
        ? rawName.slice(stripPrefix.length)
        : rawName;

      if (!relativePath) continue;

      try {
        const content = inflateEntry(zipBuffer, entry);
        const absolutePath = normalize(join(tempRoot, relativePath.replace(/\//g, '/')));
        const resolvedPath = resolve(absolutePath);
        const resolvedTempRoot = resolve(tempRoot);

        if (!resolvedPath.startsWith(resolvedTempRoot + (process.platform === 'win32' ? '\\' : '/'))) {
          files.push({
            relativePath,
            materialized: false,
            sizeBytes: entry.uncompressedSize,
            lastModifiedAt: new Date().toISOString(),
            checksum: metadataChecksum,
          });
          diagnostics.push({
            level: 'warning',
            code: 'ZIP_UNSAFE_PATH',
            message: `Skipped ZIP entry ${entry.name} due to unsafe path (zip-slip attempt).`,
            timestamp: new Date().toISOString(),
            resourcePath: entry.name,
            metadata: { resolvedPath, intendedPath: relativePath },
          });
          continue;
        }

        const dir = absolutePath.slice(0, absolutePath.lastIndexOf('/'));
        await mkdir(dir, { recursive: true });
        await writeFile(absolutePath, content);

        files.push({
          relativePath,
          absolutePath,
          materialized: true,
          sizeBytes: content.byteLength,
          lastModifiedAt: new Date().toISOString(),
          checksum: createHash('sha256').update(content).digest('hex'),
        });
        fileCount++;
      } catch (err) {
        files.push({
          relativePath,
          materialized: false,
          sizeBytes: entry.uncompressedSize,
          lastModifiedAt: new Date().toISOString(),
          checksum: metadataChecksum,
        });
        diagnostics.push({
          level: 'warning',
          code: 'ZIP_FILE_MATERIALIZATION_FAILED',
          message: `Failed to materialize ZIP entry ${entry.name}; keeping metadata-only snapshot entry.`,
          timestamp: new Date().toISOString(),
          resourcePath: entry.name,
          metadata: {
            sizeBytes: entry.uncompressedSize,
            error: err instanceof Error ? err.message : String(err),
          },
        });
      }
    }

    const snapshotContentHash = createHash('sha256')
      .update(files.map(file => `${file.relativePath}:${file.checksum}`).sort().join('\n'))
      .digest('hex');
    const snapshot = {
      snapshotId: `zip:${archiveName}:${snapshotContentHash.slice(0, 32)}`,
      snapshotRef,
      localRootPath: tempRoot,
      files,
      snapshotAt: new Date().toISOString(),
      shallow: false,
      diagnostics,
      contentHash: snapshotContentHash,
      contentChecksum: snapshotContentHash,
      tenantId: options?.scope?.tenantId ?? 'worker-local-tenant',
      workspaceId: options?.scope?.workspaceId ?? 'worker-local-workspace',
      projectId: options?.scope?.projectId ?? 'worker-local-project',
    };

    // P1-4: Cleanup temp files unless keepTempFiles is true
    if (!options?.keepTempFiles) {
      setImmediate(async () => {
        try {
          await rm(tempRoot, { recursive: true, force: true });
        } catch (err) {
          console.warn(`[ZipProvider] Failed to cleanup temp directory: ${tempRoot}`, err);
        }
      });
    }

    return snapshot;
  }
}
