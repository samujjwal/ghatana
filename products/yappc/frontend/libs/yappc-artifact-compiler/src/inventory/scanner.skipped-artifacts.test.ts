import { mkdtemp, mkdir, symlink, writeFile } from 'fs/promises';
import { tmpdir } from 'os';
import { join } from 'path';
import { afterEach, describe, expect, it } from 'vitest';
import { scanRepository } from './scanner';

const tempRoots: string[] = [];

async function createTempRepo(): Promise<string> {
  const rootPath = await mkdtemp(join(tmpdir(), 'yappc-scanner-'));
  tempRoots.push(rootPath);
  return rootPath;
}

describe('scanRepository skippedArtifacts', () => {
  afterEach(async () => {
    await Promise.all(
      tempRoots.splice(0).map(async rootPath => {
        const { rm } = await import('fs/promises');
        await rm(rootPath, { recursive: true, force: true });
      }),
    );
  });

  it('records exclude, gitignore, oversize, and symlink skip reasons', async () => {
    const rootPath = await createTempRepo();
    await mkdir(join(rootPath, 'src'), { recursive: true });
    await mkdir(join(rootPath, 'ignored'), { recursive: true });
    await mkdir(join(rootPath, 'logs'), { recursive: true });

    await writeFile(join(rootPath, '.gitignore'), 'ignored/\n');
    await writeFile(join(rootPath, 'src', 'keep.ts'), 'export const keep = true;\n');
    await writeFile(join(rootPath, 'ignored', 'secret.ts'), 'export const secret = true;\n');
    await writeFile(join(rootPath, 'logs', 'app.log'), 'line one\n');
    await writeFile(join(rootPath, 'src', 'large.ts'), 'x'.repeat(2048));
    await symlink(join(rootPath, 'src', 'keep.ts'), join(rootPath, 'src', 'linked.ts'));

    const inventory = await scanRepository({
      rootPath,
      includeGlobs: ['**/*'],
      excludeGlobs: ['**/*.log'],
      maxFileSizeBytes: 128,
      followSymlinks: false,
      respectGitignore: true,
    });

    expect(inventory.artifacts.map(artifact => artifact.relativePath)).toContain('src/keep.ts');

    expect(inventory.skippedArtifacts).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          relativePath: 'ignored',
          source: 'gitignore',
        }),
        expect.objectContaining({
          relativePath: 'logs/app.log',
          source: 'excludeGlobs',
          matchedPattern: '**/*.log',
        }),
        expect.objectContaining({
          relativePath: 'src/large.ts',
          source: 'maxFileSize',
          sizeBytes: 2048,
        }),
        expect.objectContaining({
          relativePath: 'src/linked.ts',
          source: 'symlink',
        }),
      ]),
    );
    expect(inventory.summary.ignoredFiles).toBeGreaterThanOrEqual(4);
  });
});