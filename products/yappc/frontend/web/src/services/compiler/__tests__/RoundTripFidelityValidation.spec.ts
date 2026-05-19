import { describe, expect, it } from 'vitest';
import { compileImportedSourceToPageArtifacts } from '../ImportSourceWorkflow';
import { exportJSX } from '../../../components/canvas/page/exportJSX';

describe('Round-Trip Fidelity Validation', () => {
  it('preserves component structure during round-trip compilation', async () => {
    const originalSource = `
      export function SimpleComponent({ name }: { name: string }) {
        return (
          <div className="container">
            <h1>Hello, {name}!</h1>
          </div>
        );
      }
    `;

    const artifacts = await compileImportedSourceToPageArtifacts({
      projectId: 'test-project',
      componentName: 'SimpleComponent',
      source: originalSource,
      sourceType: 'tsx',
      importedAt: new Date().toISOString(),
    });

    expect(artifacts).toHaveLength(1);
    expect(artifacts[0]?.artifactId).toBeDefined();
    expect(artifacts[0]?.source).toBe('decompiled');
  });

  it('tracks round-trip confidence score', async () => {
    const sourceCode = `
      export function ComplexComponent({ items }: { items: string[] }) {
        return (
          <ul>
            {items.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        );
      }
    `;

    const artifacts = await compileImportedSourceToPageArtifacts({
      projectId: 'test-project',
      componentName: 'ComplexComponent',
      source: sourceCode,
      sourceType: 'tsx',
      importedAt: new Date().toISOString(),
    });

    expect(artifacts[0]?.roundTripFidelity).toBeDefined();
    expect(artifacts[0]?.roundTripFidelity?.confidence).toBeGreaterThan(0);
    expect(artifacts[0]?.roundTripFidelity?.confidence).toBeLessThanOrEqual(1);
  });

  it('preserves residual islands during round-trip', async () => {
    const sourceCodeWithComplexLogic = `
      export function ComponentWithSideEffects({ data }: { data: any }) {
        useEffect(() => {
          console.log(data);
        }, [data]);
        return <div>{JSON.stringify(data)}</div>;
      }
    `;

    const artifacts = await compileImportedSourceToPageArtifacts({
      projectId: 'test-project',
      componentName: 'ComponentWithSideEffects',
      source: sourceCodeWithComplexLogic,
      sourceType: 'tsx',
      importedAt: new Date().toISOString(),
    });

    // Components with side effects should have residual islands preserved
    expect(artifacts[0]?.residualIslandIds).toBeDefined();
    expect(Array.isArray(artifacts[0]?.residualIslandIds)).toBe(true);
  });
});
