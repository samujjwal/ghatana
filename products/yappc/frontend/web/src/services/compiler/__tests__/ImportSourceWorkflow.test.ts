import { afterEach, describe, expect, it } from 'vitest';
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import JSZip from 'jszip';

import { importFromSource, importSourceToPageArtifacts } from '../ImportSourceWorkflow';

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
        allowLocalFileAccess: true,
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
      options: {
        allowLocalFileAccess: true,
      },
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
        allowLocalFileAccess: true,
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
      options: {
        allowLocalFileAccess: true,
      },
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
        allowLocalFileAccess: true,
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

  it('rejects local file imports unless trusted local access is explicitly enabled', async () => {
    const projectDir = await createTempProject();
    const componentPath = join(projectDir, 'Banner.tsx');
    await writeFile(componentPath, 'export function Banner() { return <div />; }\n');

    const result = await importFromSource({
      sourceType: 'tsx',
      source: componentPath,
      projectId: 'proj-1',
    });

    expect(result.success).toBe(false);
    expect(result.errors).toEqual(
      expect.arrayContaining([
        expect.stringContaining('Local source access requires an explicit trusted loader or allowLocalFileAccess'),
      ]),
    );
  });

  it('delegates local import to backend endpoint when local file access is disabled', async () => {
    const projectDir = await createTempProject();
    const componentPath = join(projectDir, 'ServerImportedCard.tsx');
    await writeFile(componentPath, 'export function ServerImportedCard() { return <div />; }\n');

    const originalFetch = globalThis.fetch;
    const serverResponse = {
      success: true,
      componentId: 'proj-1/ServerImportedCard',
      files: [
        {
          path: 'ServerImportedCard.tsx',
          content: 'export function ServerImportedCard() { return <div />; }',
          type: 'component',
          source: componentPath,
        },
      ],
      warnings: [],
      errors: [],
      metadata: {
        sourceType: 'tsx',
        source: componentPath,
        importedAt: new Date().toISOString(),
        componentName: 'ServerImportedCard',
        dependencies: [],
        fileCount: 1,
        totalSize: 58,
      },
    };

    globalThis.fetch = async () =>
      new Response(JSON.stringify(serverResponse), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });

    try {
      const result = await importFromSource({
        sourceType: 'tsx',
        source: componentPath,
        projectId: 'proj-1',
      });

      expect(result.success).toBe(true);
      expect(result.componentId).toBe('proj-1/ServerImportedCard');
      expect(result.files[0]?.path).toBe('ServerImportedCard.tsx');
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it('blocks unsafe imported component code by default', async () => {
    const projectDir = await createTempProject();
    const componentPath = join(projectDir, 'DangerousWidget.tsx');
    await writeFile(
      componentPath,
      [
        'export function DangerousWidget() {',
        '  eval("alert(1)");',
        '  return <div />;',
        '}',
        '',
      ].join('\n')
    );

    const result = await importFromSource({
      sourceType: 'tsx',
      source: componentPath,
      projectId: 'proj-1',
      options: {
        allowLocalFileAccess: true,
      },
    });

    expect(result.success).toBe(false);
    expect(result.errors).toEqual(
      expect.arrayContaining([
        expect.stringContaining("Imported source 'DangerousWidget.tsx' was flagged as unsafe"),
      ]),
    );
  });

  it('artifact import passes through the safety gate without false-positives on clean JSON', async () => {
    // Confirm that adding enforceImportedComponentSafety to the artifact path does not break
    // legitimate artifact imports — clean sanitized JSON must still succeed.
    const projectDir = await createTempProject();
    const artifactPath = join(projectDir, 'safe.artifact.json');
    await writeFile(
      artifactPath,
      JSON.stringify(
        {
          metadata: { name: 'SafeWidget' },
          dependencies: ['@ghatana/design-system'],
        },
        null,
        2
      )
    );

    const result = await importFromSource({
      sourceType: 'artifact',
      source: artifactPath,
      projectId: 'proj-1',
      options: {
        allowLocalFileAccess: true,
      },
    });

    expect(result.success).toBe(true);
    expect(result.metadata.componentName).toBe('SafeWidget');
    expect(result.errors).toHaveLength(0);
  });

  it('compiles imported source output into page artifacts for canvas ingestion', async () => {
    const projectDir = await createTempProject();
    const componentPath = join(projectDir, 'ProfileCard.tsx');
    await writeFile(
      componentPath,
      [
        'export function ProfileCard() {',
        '  return <section>Profile</section>;',
        '}',
        '',
      ].join('\n'),
    );

    const result = await importSourceToPageArtifacts(
      {
        sourceType: 'tsx',
        source: componentPath,
        projectId: 'proj-99',
        options: {
          allowLocalFileAccess: true,
        },
      },
      'import-runner',
    );

    expect(result.importResult.success).toBe(true);
    expect(result.pageArtifacts).toHaveLength(1);
    expect(result.pageArtifacts[0]?.source).toBe('imported');
    expect(result.pageArtifacts[0]?.artifactId).toContain('proj-99');
    expect(result.pageArtifacts[0]?.serializedBuilderDocument.name).toBe('ProfileCard');
  });

  it('produces populated canvas nodes when the TSX component uses JSX child elements', async () => {
    // COMP-001: importing a real TSX component should produce a BuilderDocument
    // whose canvas graph contains real nodes (root + child nodes from jsxUsage),
    // not just an empty document.
    const projectDir = await createTempProject();
    const componentPath = join(projectDir, 'ContactForm.tsx');
    await writeFile(
      componentPath,
      [
        "import { Card, Button, TextField } from '@ghatana/design-system';",
        '',
        'export function ContactForm() {',
        '  return (',
        '    <Card>',
        '      <TextField label="Email" />',
        '      <Button>Submit</Button>',
        '    </Card>',
        '  );',
        '}',
        '',
      ].join('\n'),
    );

    const result = await importSourceToPageArtifacts(
      {
        sourceType: 'tsx',
        source: componentPath,
        projectId: 'proj-comp001',
        options: { allowLocalFileAccess: true },
      },
      'test-runner',
    );

    expect(result.importResult.success).toBe(true);
    const artifact = result.pageArtifacts[0];
    expect(artifact).toBeDefined();
    // The serialized document should carry real nodes — root + one per unique JSX usage
    const serialized = artifact?.serializedBuilderDocument;
    expect(serialized?.name).toBe('ContactForm');
    expect(serialized?.rootNodes).toHaveLength(1); // ContactForm as root
    // At least Card, TextField, Button should appear as canvas nodes
    const nodeEntries = Object.values(serialized?.nodes ?? {}) as Array<{ contractName: string }>;
    const contractNames = nodeEntries.map((n) => n.contractName);
    expect(contractNames).toContain('ContactForm'); // root node
    expect(contractNames.some((c) => ['Card', 'TextField', 'Button'].includes(c))).toBe(true);
  });
});
