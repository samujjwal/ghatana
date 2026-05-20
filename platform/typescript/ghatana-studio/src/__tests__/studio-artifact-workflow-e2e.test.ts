/**
 * @fileoverview Behavioral gate for Studio artifact workflow round-trip.
 */

import { describe, expect, it } from 'vitest';
import { decompileTsx, compileReact } from '@ghatana/artifact-compiler-ts';
import { parseNodeId, updateNodeProps } from '@ghatana/ui-builder';
import { projectModelToBuilderDocument } from '../adapters/ModelToBuilderAdapter.js';
import { builderToCanvas, canvasToBuilder } from '../adapters/BuilderCanvasProjectionAdapter.js';
import { InMemoryPreviewRuntime } from '../preview/in-memory-preview-runtime.js';

const SOURCE = `
import type { ReactElement } from "react";

export interface ButtonProps {
  readonly label: string;
}

export function Button(props: ButtonProps): ReactElement {
  return <button type="button">{props.label}</button>;
}
`.trim();

describe('Studio artifact workflow e2e gate', () => {
  it('runs import → canvas edit → builder edit → preview → fidelity → re-import', async () => {
    const firstDecompile = decompileTsx({
      label: 'workflow-e2e',
      modelId: 'workflow-e2e-model',
      files: [{ relativePath: 'src/Button.tsx', content: SOURCE }],
    });

    expect(firstDecompile.fidelityReport.score).toBeGreaterThan(0);

    const projectedDocument = projectModelToBuilderDocument(firstDecompile.model);
    const projectedNodeId = Object.keys(projectedDocument.nodes).find(
      (nodeId) => projectedDocument.nodes[nodeId]?.contractName === 'Button',
    );
    expect(projectedNodeId).toBeDefined();
    if (projectedNodeId === undefined) return;

    const builderNodeId = parseNodeId(projectedNodeId, Object.keys(projectedDocument.nodes));
    expect(builderNodeId).not.toBeNull();
    if (builderNodeId === null) return;

    const canvasProjection = builderToCanvas(projectedDocument);
    const originalCanvasNode = canvasProjection.nodes.find((node) => node.id === projectedNodeId);
    expect(originalCanvasNode).toBeDefined();
    if (originalCanvasNode === undefined) return;

    const movedCanvasNodes = canvasProjection.nodes.map((node) => (
      node.id === projectedNodeId
        ? { ...node, position: { x: node.position.x + 32, y: node.position.y + 48 } }
        : node
    ));
    const canvasEditedDocument = canvasToBuilder({
      baseDocument: projectedDocument,
      canvasNodes: movedCanvasNodes,
    });
    expect(canvasEditedDocument.nodes[projectedNodeId]?.metadata.position).toEqual({
      x: originalCanvasNode.position.x + 32,
      y: originalCanvasNode.position.y + 48,
    });

    const builderEditedDocument = updateNodeProps(
      canvasEditedDocument,
      builderNodeId,
      { label: 'Edited in Builder' },
    );
    expect(builderEditedDocument.nodes[projectedNodeId]?.props.label).toBe('Edited in Builder');

    const compiled = compileReact(firstDecompile.model);
    const previewSource = compiled.emittedFiles.map((file) => file.content).join('\n\n');
    const preview = await new InMemoryPreviewRuntime().render({
      sessionId: 'workflow-e2e-session',
      filePath: 'src/Button.tsx',
      source: previewSource,
      securityPolicy: {
        allowScripts: true,
        allowSameOrigin: false,
        allowPopups: false,
        allowForms: false,
      },
    });
    expect(preview.success).toBe(true);
    expect(preview.html).toContain('sandbox="allow-scripts"');
    expect(preview.html).not.toContain('allow-same-origin');

    const reimported = decompileTsx({
      label: 'workflow-e2e-reimport',
      modelId: 'workflow-e2e-model',
      files: compiled.emittedFiles.map((file) => ({
        relativePath: file.relativePath,
        content: file.content,
      })),
    });
    expect(Object.keys(reimported.model.nodes)).toEqual(Object.keys(firstDecompile.model.nodes));
    expect(reimported.fidelityReport.score).toBeGreaterThan(0);
  });
});
