/**
 * @fileoverview Source acquisition provider contract tests.
 */

import { describe, expect, it } from 'vitest';
import {
  ArchiveUploadProvider,
  BrowserFileUploadProvider,
  KernelSourceAcquisitionBackendClient,
  LocalFolderDescriptorProvider,
  PastedSourceProvider,
  ProductionSourceAcquisitionBackendClient,
  RepositorySourceProvider,
  resolveSourceAcquisitionRuntimeProfileForEnv,
  resolveProviderRegistryForEnv,
  SourceAcquisitionProviderRegistry,
} from '../source-acquisition.js';

function makeFile(name: string, content: string): File {
  return new File([content], name, { type: 'text/plain' });
}

function writeTarString(target: Uint8Array, offset: number, length: number, value: string): void {
  const bytes = new TextEncoder().encode(value);
  target.set(bytes.slice(0, length), offset);
}

function makeTarFile(name: string, entries: readonly { readonly path: string; readonly bytes: Uint8Array }[]): File {
  const chunks: Uint8Array[] = [];
  for (const entry of entries) {
    const header = new Uint8Array(512);
    writeTarString(header, 0, 100, entry.path);
    writeTarString(header, 100, 8, '0000644');
    writeTarString(header, 108, 8, '0000000');
    writeTarString(header, 116, 8, '0000000');
    writeTarString(header, 124, 12, entry.bytes.byteLength.toString(8).padStart(11, '0'));
    writeTarString(header, 136, 12, '00000000000');
    writeTarString(header, 156, 1, '0');
    writeTarString(header, 257, 6, 'ustar');
    chunks.push(header);
    chunks.push(entry.bytes);
    const padding = (512 - (entry.bytes.byteLength % 512)) % 512;
    if (padding > 0) {
      chunks.push(new Uint8Array(padding));
    }
  }
  chunks.push(new Uint8Array(1024));
  return new File(chunks, name, { type: 'application/x-tar' });
}

describe('source acquisition providers', () => {
  it('acquires browser-uploaded TSX files and preserves folder-relative paths', async () => {
    const provider = new BrowserFileUploadProvider();
    const file = makeFile('App.tsx', 'export function App() { return <div />; }');
    Object.defineProperty(file, 'webkitRelativePath', {
      value: 'src/features/App.tsx',
    });

    const result = await provider.acquire({ files: [file] });

    expect(result.errors).toEqual([]);
    expect(result.sources).toHaveLength(1);
    expect(result.sources[0]?.relativePath).toBe('src/features/App.tsx');
    expect(result.descriptor?.kind).toBe('browser-upload');
  });

  it('rejects hidden browser folder entries by default', async () => {
    const provider = new BrowserFileUploadProvider();
    const file = makeFile('secret.tsx', 'export const secret = true;');
    Object.defineProperty(file, 'webkitRelativePath', {
      value: 'src/.private/secret.tsx',
    });

    const result = await provider.acquire({ files: [file] });

    expect(result.sources).toHaveLength(0);
    expect(result.errors[0]).toContain('hidden file');
  });

  it('acquires pasted source through the provider registry', async () => {
    const registry = new SourceAcquisitionProviderRegistry();
    registry.register(new PastedSourceProvider());

    const result = await registry.acquire({
      kind: 'pasted-source',
      relativePath: 'src/Pasted.tsx',
      content: 'export function Pasted() { return <main />; }',
    });

    expect(result.partial).toBe(false);
    expect(result.sources[0]?.relativePath).toBe('src/Pasted.tsx');
    expect(result.descriptor?.kind).toBe('pasted-source');
  });

  it('rejects pasted source with unsafe paths', async () => {
    const provider = new PastedSourceProvider();

    const result = await provider.acquire({
      kind: 'pasted-source',
      relativePath: '../escape.tsx',
      content: 'export const Escape = true;',
    });

    expect(result.sources).toEqual([]);
    expect(result.errors[0]).toContain('Unsafe source path rejected');
  });

  it('filters local folder descriptors using extension and hidden-file rules', async () => {
    const provider = new LocalFolderDescriptorProvider();

    const result = await provider.acquire({
      kind: 'local-folder',
      descriptor: {
        rootLabel: 'fixture',
        files: [
          { relativePath: 'src/App.tsx', content: 'export const App = () => null;' },
          { relativePath: 'src/App.css', content: '.app {}' },
          { relativePath: 'src/.private/Hidden.tsx', content: 'export const Hidden = true;' },
        ],
      },
    });

    expect(result.sources.map((source) => source.relativePath)).toEqual(['src/App.tsx']);
    expect(result.errors).toHaveLength(2);
    expect(result.partial).toBe(true);
    expect(result.descriptor?.kind).toBe('local-folder');
  });

  it('returns errors for local folder descriptor entries that look binary', async () => {
    const provider = new LocalFolderDescriptorProvider();

    const result = await provider.acquire({
      kind: 'local-folder',
      descriptor: {
        rootLabel: 'fixture',
        files: [
          { relativePath: 'src/Binary.tsx', content: 'export\u0000const Binary = true;' },
        ],
      },
    });

    expect(result.sources).toEqual([]);
    expect(result.errors[0]).toContain('appears to be binary');
    expect(result.partial).toBe(false);
  });

  it('surfaces repository acquisition as a typed backend acquisition job boundary', async () => {
    const provider = new RepositorySourceProvider();

    const result = await provider.acquire({
      kind: 'github-repository',
      repositoryUrl: 'https://github.com/example/repo',
      ref: 'main',
    });

    expect(result.sources).toEqual([]);
    expect(result.errors[0]).toContain('backend acquisition job');
    expect(result.descriptor).toMatchObject({
      kind: 'github',
      uri: 'https://github.com/example/repo',
      ref: 'main',
    });
    expect(result.acquisitionJob).toMatchObject({
      status: 'pending',
      descriptor: result.descriptor,
    });
  });

  it('surfaces archive acquisition as a typed backend acquisition job boundary', async () => {
    const provider = new ArchiveUploadProvider();

    const result = await provider.acquire({
      kind: 'archive-upload',
      file: makeFile('source.zip', 'not-a-real-zip'),
    });

    expect(result.sources).toEqual([]);
    expect(result.errors[0]).toContain('backend acquisition job');
    expect(result.descriptor).toMatchObject({
      kind: 'archive',
      uri: 'archive://source.zip',
      label: 'source.zip',
    });
    expect(result.acquisitionJob).toMatchObject({
      status: 'pending',
      descriptor: result.descriptor,
    });
  });

  it('uses backend repository acquisition client when provided', async () => {
    const provider = new RepositorySourceProvider({
      acquireRepository: async (input) => ({
        sources: [
          {
            relativePath: 'src/App.tsx',
            content: `// ${input.repositoryUrl}`,
          },
        ],
        errors: [],
        partial: false,
      }),
      acquireArchive: async () => ({
        sources: [],
        errors: ['not-used'],
        partial: false,
      }),
    });

    const result = await provider.acquire({
      kind: 'github-repository',
      repositoryUrl: 'https://github.com/example/repo',
      ref: 'main',
    });

    expect(result.errors).toEqual([]);
    expect(result.sources).toHaveLength(1);
    expect(result.sources[0]?.relativePath).toBe('src/App.tsx');
    expect(result.descriptor?.kind).toBe('github');
    expect(result.acquisitionJob).toBeUndefined();
  });

  it('uses backend archive acquisition client when provided', async () => {
    const provider = new ArchiveUploadProvider({
      acquireRepository: async () => ({
        sources: [],
        errors: ['not-used'],
        partial: false,
      }),
      acquireArchive: async (input) => ({
        sources: [
          {
            relativePath: `unzipped/${input.file.name}.tsx`,
            content: 'export const ArchiveEntry = true;',
          },
        ],
        errors: [],
        partial: false,
      }),
    });

    const result = await provider.acquire({
      kind: 'archive-upload',
      file: makeFile('source.zip', 'fake-zip'),
    });

    expect(result.errors).toEqual([]);
    expect(result.sources.map((source) => source.relativePath)).toEqual(['unzipped/source.zip.tsx']);
    expect(result.descriptor?.kind).toBe('archive');
    expect(result.acquisitionJob).toBeUndefined();
  });

  it('posts repository source acquisition requests to the Kernel API with scoped auth headers', async () => {
    const originalFetch = globalThis.fetch;
    let capturedUrl = '';
    let capturedInit: RequestInit | undefined;
    Object.defineProperty(globalThis, 'fetch', {
      configurable: true,
      writable: true,
      value: async (url: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
        capturedUrl = String(url);
        capturedInit = init;
        return new Response(JSON.stringify({
          sources: [],
          errors: [],
          partial: false,
          descriptor: {
            kind: 'github',
            uri: 'https://github.com/example/repo',
            label: 'https://github.com/example/repo',
          },
          acquisitionJob: {
            jobId: 'studio-acquisition:github:1',
            status: 'pending',
            descriptor: {
              kind: 'github',
              uri: 'https://github.com/example/repo',
              label: 'https://github.com/example/repo',
            },
            createdAt: '2026-05-21T00:00:00.000Z',
          },
        }), { status: 202, headers: { 'content-type': 'application/json' } });
      },
    });

    try {
      const client = new KernelSourceAcquisitionBackendClient({
        baseUrl: 'https://kernel.local',
        tenantId: 'tenant-1',
        workspaceId: 'workspace-1',
        projectId: 'project-1',
        authToken: 'token-1',
      });

      await client.acquireRepository({
        kind: 'github-repository',
        repositoryUrl: 'https://github.com/example/repo',
        ref: 'main',
      }, { maxFileSize: 1_000_000 });

      expect(capturedUrl).toBe('https://kernel.local/api/v1/studio/source-acquisition/repository');
      expect(capturedInit?.method).toBe('POST');
      expect(capturedInit?.headers).toMatchObject({
        Authorization: 'Bearer token-1',
        'x-ghatana-tenant-id': 'tenant-1',
        'x-ghatana-workspace-id': 'workspace-1',
        'x-ghatana-project-id': 'project-1',
      });
      expect(JSON.parse(String(capturedInit?.body))).toMatchObject({
        input: {
          kind: 'github-repository',
          repositoryUrl: 'https://github.com/example/repo',
          ref: 'main',
        },
        options: {
          maxFileSize: 1_000_000,
        },
      });
    } finally {
      Object.defineProperty(globalThis, 'fetch', {
        configurable: true,
        writable: true,
        value: originalFetch,
      });
    }
  });

  it('posts archive source acquisition requests using the Kernel API file payload contract', async () => {
    const originalFetch = globalThis.fetch;
    let capturedBody: unknown;
    Object.defineProperty(globalThis, 'fetch', {
      configurable: true,
      writable: true,
      value: async (_url: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
        capturedBody = JSON.parse(String(init?.body));
        return new Response(JSON.stringify({
          sources: [],
          errors: [],
          partial: false,
          descriptor: {
            kind: 'archive',
            uri: 'archive://source.zip',
            label: 'source.zip',
          },
          acquisitionJob: {
            jobId: 'studio-acquisition:archive:1',
            status: 'pending',
            descriptor: {
              kind: 'archive',
              uri: 'archive://source.zip',
              label: 'source.zip',
            },
            createdAt: '2026-05-21T00:00:00.000Z',
          },
        }), { status: 202, headers: { 'content-type': 'application/json' } });
      },
    });

    try {
      const client = new KernelSourceAcquisitionBackendClient({
        baseUrl: 'https://kernel.local',
        tenantId: 'tenant-1',
        workspaceId: 'workspace-1',
        projectId: 'project-1',
        authToken: 'token-1',
      });

      await client.acquireArchive({
        kind: 'archive-upload',
        file: makeFile('source.zip', 'zip-bytes'),
      }, { maxFileSize: 1_000_000 });

      expect(capturedBody).toMatchObject({
        input: {
          kind: 'archive-upload',
          file: {
            name: 'source.zip',
            type: 'text/plain',
            size: 9,
            contentBase64: expect.any(String),
          },
        },
        options: {
          maxFileSize: 1_000_000,
        },
      });
      expect(JSON.stringify(capturedBody)).not.toContain('base64Content');
      expect(JSON.stringify(capturedBody)).not.toContain('fileName');
    } finally {
      Object.defineProperty(globalThis, 'fetch', {
        configurable: true,
        writable: true,
        value: originalFetch,
      });
    }
  });

  it('retrieves Kernel source acquisition job status with scoped auth headers', async () => {
    const originalFetch = globalThis.fetch;
    let capturedUrl = '';
    let capturedInit: RequestInit | undefined;
    Object.defineProperty(globalThis, 'fetch', {
      configurable: true,
      writable: true,
      value: async (url: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
        capturedUrl = String(url);
        capturedInit = init;
        return new Response(JSON.stringify({
          jobId: 'studio-acquisition:github:1',
          status: 'pending',
          descriptor: {
            kind: 'github',
            uri: 'https://github.com/example/repo',
            label: 'https://github.com/example/repo',
          },
          createdAt: '2026-05-21T00:00:00.000Z',
          correlationId: 'corr-1',
        }), { status: 200, headers: { 'content-type': 'application/json' } });
      },
    });

    try {
      const client = new KernelSourceAcquisitionBackendClient({
        baseUrl: 'https://kernel.local',
        tenantId: 'tenant-1',
        workspaceId: 'workspace-1',
        projectId: 'project-1',
        authToken: 'token-1',
      });

      const job = await client.getAcquisitionJob('studio-acquisition:github:1');

      expect(capturedUrl).toBe('https://kernel.local/api/v1/studio/source-acquisition/jobs/studio-acquisition%3Agithub%3A1');
      expect(capturedInit?.method).toBe('GET');
      expect(capturedInit?.headers).toMatchObject({
        Authorization: 'Bearer token-1',
        'x-ghatana-tenant-id': 'tenant-1',
        'x-ghatana-workspace-id': 'workspace-1',
        'x-ghatana-project-id': 'project-1',
      });
      expect(job).toMatchObject({
        jobId: 'studio-acquisition:github:1',
        status: 'pending',
      });
    } finally {
      Object.defineProperty(globalThis, 'fetch', {
        configurable: true,
        writable: true,
        value: originalFetch,
      });
    }
  });

  it('uses boundary-safe pending acquisition when production acquisition profile is disabled', async () => {
    const registry = resolveProviderRegistryForEnv({
      VITE_STUDIO_ENABLE_PRODUCTION_ACQUISITION: 'false',
    });

    const result = await registry.acquire({
      kind: 'github-repository',
      repositoryUrl: 'https://github.com/example/repo',
      ref: 'main',
    });

    expect(result.acquisitionJob?.status).toBe('pending');
    expect(result.errors[0]).toContain('requires backend acquisition job');
  });

  it('resolves pending-backend mode for non-production repository/archive exposure', () => {
    const profile = resolveSourceAcquisitionRuntimeProfileForEnv({
      VITE_STUDIO_ENABLE_PRODUCTION_ACQUISITION: 'false',
    });

    expect(profile.mode).toBe('pending-backend');
    expect(profile.exposeRepositoryAndArchiveProviders).toBe(true);
  });

  it('fails closed when production exposes repository/archive providers without kernel acquisition', () => {
    expect(() =>
      resolveSourceAcquisitionRuntimeProfileForEnv({
        VITE_STUDIO_DEPLOYMENT_PROFILE: 'production',
        VITE_STUDIO_ENABLE_PRODUCTION_ACQUISITION: 'true',
        VITE_STUDIO_SOURCE_ACQUISITION_BACKEND: 'browser',
      }),
    ).toThrow(/must use VITE_STUDIO_SOURCE_ACQUISITION_BACKEND=kernel/);
  });

  it('allows production profile when repository/archive providers are disabled', () => {
    const profile = resolveSourceAcquisitionRuntimeProfileForEnv({
      VITE_STUDIO_DEPLOYMENT_PROFILE: 'production',
      VITE_STUDIO_EXPOSE_REPOSITORY_ARCHIVE_PROVIDERS: 'false',
    });

    expect(profile.mode).toBe('local');
  });

  it('uses production acquisition backend path when production profile is enabled', async () => {
    const originalFetch = globalThis.fetch;
    const fetchStub = async (): Promise<Response> => {
      throw new Error('network unavailable in test');
    };

    Object.defineProperty(globalThis, 'fetch', {
      configurable: true,
      writable: true,
      value: fetchStub,
    });

    try {
      const registry = resolveProviderRegistryForEnv({
        VITE_STUDIO_ENABLE_PRODUCTION_ACQUISITION: 'true',
      });

      const result = await registry.acquire({
        kind: 'github-repository',
        repositoryUrl: 'https://github.com/example/repo',
        ref: 'main',
      });

      expect(result.acquisitionJob?.status).toBe('pending');
      expect(result.errors[0]).toContain('Repository acquisition failed');
    } finally {
      Object.defineProperty(globalThis, 'fetch', {
        configurable: true,
        writable: true,
        value: originalFetch,
      });
    }
  });

  it('rejects archive entries with path traversal', async () => {
    const client = new ProductionSourceAcquisitionBackendClient({ maxArchiveSizeBytes: 10_000 });
    const result = await client.acquireArchive({
      kind: 'archive-upload',
      file: makeTarFile('unsafe.tar', [
        {
          path: '../evil.tsx',
          bytes: new TextEncoder().encode('export const Evil = true;'),
        },
      ]),
    });

    expect(result.sources).toEqual([]);
    expect(result.errors[0]).toContain('Unsafe source path rejected');
  });

  it('rejects archive entries that exceed per-file limits before decoding', async () => {
    const client = new ProductionSourceAcquisitionBackendClient({ maxArchiveSizeBytes: 10_000 });
    const result = await client.acquireArchive(
      {
        kind: 'archive-upload',
        file: makeTarFile('large.tar', [
          {
            path: 'src/Large.tsx',
            bytes: new TextEncoder().encode('x'.repeat(32)),
          },
        ]),
      },
      { maxFileSize: 16 },
    );

    expect(result.sources).toEqual([]);
    expect(result.errors[0]).toContain('exceeds file size limit');
  });

  it('rejects binary-looking archive entries', async () => {
    const client = new ProductionSourceAcquisitionBackendClient();
    const result = await client.acquireArchive({
      kind: 'archive-upload',
      file: makeTarFile('binary.tar', [
        {
          path: 'src/Binary.tsx',
          bytes: new Uint8Array([0x65, 0x78, 0x00, 0x70]),
        },
      ]),
    });

    expect(result.sources).toEqual([]);
    expect(result.errors[0]).toContain('appears to be binary');
  });
});
