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
});
