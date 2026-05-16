import { describe, expect, it } from 'vitest';
import { compileImportedSourceToPageArtifacts } from '../../ImportSourceWorkflow';

describe('Idempotent Compile Validation', () => {
  it('produces identical artifacts when compiling the same source twice', async () => {
    const sourceCode = `
      export function TestComponent({ title }: { title: string }) {
        return <div>{title}</div>;
      }
    `;

    const firstResult = await compileImportedSourceToPageArtifacts({
      projectId: 'test-project',
      componentName: 'TestComponent',
      source: sourceCode,
      sourceType: 'tsx',
      importedAt: new Date().toISOString(),
    });

    const secondResult = await compileImportedSourceToPageArtifacts({
      projectId: 'test-project',
      componentName: 'TestComponent',
      source: sourceCode,
      sourceType: 'tsx',
      importedAt: new Date().toISOString(),
    });

    expect(firstResult).toEqual(secondResult);
  });

  it('produces identical artifact IDs for the same component', async () => {
    const sourceCode = `
      export function MyComponent({ value }: { value: number }) {
        return <span>{value}</span>;
      }
    `;

    const result1 = await compileImportedSourceToPageArtifacts({
      projectId: 'test-project',
      componentName: 'MyComponent',
      source: sourceCode,
      sourceType: 'tsx',
      importedAt: new Date().toISOString(),
    });

    const result2 = await compileImportedSourceToPageArtifacts({
      projectId: 'test-project',
      componentName: 'MyComponent',
      source: sourceCode,
      sourceType: 'tsx',
      importedAt: new Date().toISOString(),
    });

    expect(result1[0]?.artifactId).toBe(result2[0]?.artifactId);
  });
});
