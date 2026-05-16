/**
 * @fileoverview ZIP archive source provider.
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

import { mkdir, readFile, writeFile, mkdtemp } from 'fs/promises';
import { join, basename } from 'path';
import { tmpdir } from 'os';
import { createHash } from 'crypto';
import type { SourceProvider, SourceProviderOptions, SnapshotFile, RepositorySnapshot } from './types';
import { SourceProviderError } from './types';
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
  throw new Error(`Unsupported compression method: ${entry.compressionMethod}`);
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

  async resolve(locator: string, options?: SourceProviderOptions): Promise<RepositorySnapshot> {
    const maxFileSizeBytes = options?.maxFileSizeBytes ?? 10 * 1024 * 1024;
    const maxFiles = options?.maxFiles ?? 20_000;
    const timeoutMs = options?.requestTimeoutMs ?? 60_000;

    let zipBuffer: Buffer;

    if (locator.startsWith('http://') || locator.startsWith('https://')) {
      try {
        zipBuffer = await downloadToBuffer(locator, timeoutMs);
      } catch (err) {
        throw new SourceProviderError(this.providerId, locator, 'Failed to download ZIP', err);
      }
    } else {
      try {
        zipBuffer = await readFile(locator);
      } catch (err) {
        throw new SourceProviderError(this.providerId, locator, 'Failed to read ZIP file', err);
      }
    }

    // Compute a stable content hash for the snapshotRef
    const contentSha = createHash('sha256').update(zipBuffer).digest('hex');
    const archiveName = basename(locator).replace(/\.zip$/, '');

    const snapshotRef: SnapshotRef = {
      provider: 'zip',
      repoId: `zip/${archiveName}`,
      commitSha: contentSha.slice(0, 40),
    };

    let entries: ZipEntry[];
    try {
      entries = parseZipCentralDirectory(zipBuffer);
    } catch (err) {
      throw new SourceProviderError(this.providerId, locator, 'Failed to parse ZIP central directory', err);
    }

    const tempRoot = await mkdtemp(join(options?.tempDir ?? tmpdir(), `yappc-zip-`));
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
      if (fileCount >= maxFiles) break;
      if (entry.isDirectory) continue;
      if (entry.uncompressedSize > maxFileSizeBytes) continue;

      const rawName = entry.name;
      const relativePath = (stripPrefix && rawName.startsWith(stripPrefix)
        ? rawName.slice(stripPrefix.length)
        : rawName);

      if (!relativePath) continue;

      try {
        const content = inflateEntry(zipBuffer, entry);
        const absolutePath = join(tempRoot, relativePath.replace(/\//g, '/'));
        const dir = absolutePath.slice(0, absolutePath.lastIndexOf('/'));
        await mkdir(dir, { recursive: true });
        await writeFile(absolutePath, content);

        files.push({
          relativePath,
          absolutePath,
          materialized: true,
          sizeBytes: content.byteLength,
          lastModifiedAt: new Date().toISOString(),
        });
        fileCount++;
      } catch {
        files.push({
          relativePath,
          materialized: false,
          sizeBytes: entry.uncompressedSize,
          lastModifiedAt: new Date().toISOString(),
        });
      }
    }

    return {
      snapshotRef,
      localRootPath: tempRoot,
      files,
      snapshotAt: new Date().toISOString(),
      shallow: false,
    };
  }
}
