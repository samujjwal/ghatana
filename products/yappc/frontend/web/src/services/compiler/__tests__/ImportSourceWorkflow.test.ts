import { afterEach, describe, expect, it } from 'vitest';
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import JSZip from 'jszip';

import { importFromSource } from '../ImportSourceWorkflow';

describe('ImportSourceWorkflow', () => {
  const cleanupPaths: string[] = [];

  afterEach(async () => {
    await Promise.all(cleanupPaths.splice(0).map((path) => rm(path, { recursive: true, force: true })));
  });

  async function createTempProject(): Promise<string> {
    const projectDir = await mkdtemp(join(tmpdir(), 'yappc-import-workflow-'));
    cleanupPaths.push(projectDir);
    return projectDir;
  }

  it('imports TSX files with sibling styles and tests', async () => {
    const projectDir = await createTempProject();
    const componentPath = join(projectDir, 'Button.tsx');
    await writeFile(
      componentPath,
      [
        "import { Card } from '@ghatana/design-system';",
        "import { Badge } from './Badge';",
        '',
        'export function Button(props: { label: string }) {',
        "  return <Card><Badge />{props.label}</Card>;",
        '}',
        '',
      ].join('\n')
    );
    await writeFile(join(projectDir, 'Button.css'), '.button { color: red; }\n');
    await writeFile(join(projectDir, 'Button.test.tsx'), 'describe("Button", () => {});\n');

    const result = await importFromSource({
      sourceType: 'tsx',
      source: componentPath,
      projectId: 'proj-1',
      options: {
        includeStyles: true,
        includeTests: true,
      },
    });

    expect(result.success).toBe(true);
    expect(result.metadata.componentName).toBe('Button');
    expect(result.metadata.dependencies).toEqual(
      expect.arrayContaining(['Card', 'Badge'])
    );
    expect(result.files.map((file) => file.path)).toEqual(
      expect.arrayContaining(['Button.tsx', 'Button.css', 'Button.test.tsx'])
    );
  });

  it('imports route files through the page extractor', async () => {
    const projectDir = await createTempProject();
    const appDir = join(projectDir, 'src', 'app', 'dashboard');
    await mkdir(appDir, { recursive: true });
    const routePath = join(appDir, 'page.tsx');
    await writeFile(
      routePath,
      [
        "import { Shell } from '@/components/Shell';",
        "import { Summary } from '@/components/Summary';",
        '',
        'export default function DashboardPage() {',
        '  return <Shell><Summary /></Shell>;',
        '}',
        '',
      ].join('\n')
    );

    const result = await importFromSource({
      sourceType: 'route',
      source: routePath,
      projectId: 'proj-1',
    });

    expect(result.success).toBe(true);
    expect(result.metadata.componentName).toBe('-dashboard');
    expect(result.metadata.dependencies).toEqual(
      expect.arrayContaining(['Shell', 'Summary'])
    );
  });

  it('imports Storybook CSF and resolves the linked component implementation', async () => {
    const projectDir = await createTempProject();
    const storyPath = join(projectDir, 'Button.stories.tsx');
    const componentPath = join(projectDir, 'Button.tsx');

    await writeFile(
      componentPath,
      [
        "import { Card } from '@ghatana/design-system';",
        'export const Button = ({ label }: { label: string }) => <Card>{label}</Card>;',
        '',
      ].join('\n')
    );
    await writeFile(
      storyPath,
      [
        "import type { Meta, StoryObj } from '@storybook/react';",
        "import { Button } from './Button';",
        '',
        'const meta: Meta<typeof Button> = {',
        "  title: 'Components/Button',",
        '  component: Button,',
        '};',
        '',
        'export default meta;',
        'type Story = StoryObj<typeof meta>;',
        '',
        'export const Primary: Story = {',
        "  args: { label: 'Primary' },",
        '};',
        '',
      ].join('\n')
    );

    const result = await importFromSource({
      sourceType: 'storybook',
      source: storyPath,
      projectId: 'proj-1',
      options: {
        includeDependencies: true,
      },
    });

    expect(result.success).toBe(true);
    expect(result.metadata.componentName).toBe('Button');
    expect(result.files.map((file) => file.path)).toEqual(
      expect.arrayContaining(['Button.stories.tsx', 'Button.tsx'])
    );
    expect(result.metadata.dependencies).toContain('@ghatana/design-system');
  });

  it('imports artifact JSON metadata', async () => {
    const projectDir = await createTempProject();
    const artifactPath = join(projectDir, 'hero.artifact.json');
    await writeFile(
      artifactPath,
      JSON.stringify(
        {
          metadata: { name: 'HeroBanner' },
          dependencies: ['@ghatana/design-system', 'react'],
        },
        null,
        2
      )
    );

    const result = await importFromSource({
      sourceType: 'artifact',
      source: artifactPath,
      projectId: 'proj-1',
    });

    expect(result.success).toBe(true);
    expect(result.metadata.componentName).toBe('HeroBanner');
    expect(result.metadata.dependencies).toEqual(['@ghatana/design-system', 'react']);
  });

  it('imports zip archives and extracts dependencies from included source files', async () => {
    const projectDir = await createTempProject();
    const zipPath = join(projectDir, 'component.zip');
    const zip = new JSZip();
    zip.file(
      'Card.tsx',
      [
        "import { Box } from '@ghatana/design-system';",
        'export const Card = () => <Box />;',
        '',
      ].join('\n')
    );
    zip.file('README.md', '# Card\n');
    const zipContent = await zip.generateAsync({ type: 'nodebuffer' });
    await writeFile(zipPath, zipContent);

    const result = await importFromSource({
      sourceType: 'zip',
      source: zipPath,
      projectId: 'proj-1',
      options: {
        includeDocumentation: true,
      },
    });

    expect(result.success).toBe(true);
    expect(result.metadata.componentName).toBe('Card');
    expect(result.metadata.dependencies).toContain('@ghatana/design-system');
    expect(result.files.map((file) => file.path)).toEqual(
      expect.arrayContaining(['Card.tsx', 'README.md'])
    );
    expect(await readFile(zipPath)).toBeTruthy();
  });
});
