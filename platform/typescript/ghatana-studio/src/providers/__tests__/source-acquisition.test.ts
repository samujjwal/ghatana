/**
 * @fileoverview Source acquisition provider contract tests.
 */

import { describe, expect, it } from 'vitest';
import {
  ArchiveUploadProvider,
  BrowserFileUploadProvider,
  LocalFolderDescriptorProvider,
  PastedSourceProvider,
  RepositorySourceProvider,
  resolveProviderRegistryForEnv,
  SourceAcquisitionProviderRegistry,
} from '../source-acquisition.js';

function makeFile(name: string, content: string): File {
  return new File([content], name, { type: 'text/plain' });
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
});
