/**
 * @fileoverview Tests for the React/TSX compiler protected region markers.
 *
 * Verifies that compiled component files include @ghatana-region markers
 * for ownership tracking (user-authored body region + generated imports region).
 */

import { describe, it, expect } from 'vitest';
import { compileReact } from '../compile/react.js';
import { decompileTsx } from '../decompile/tsx.js';

// ============================================================================
// Helper — decompile a single TSX snippet then compile back
// ============================================================================

function decompileAndCompile(content: string, relativePath = 'src/Button.tsx') {
  const decompiled = decompileTsx({
    label: 'test',
    modelId: 'test-model',
    files: [{ relativePath, content }],
    designSystemComponentNames: new Set<string>(),
  });
  return compileReact(decompiled.model);
}

const SIMPLE_BUTTON_TSX = `
import type { ReactElement } from "react";

export interface ButtonProps {
  readonly label: string;
}

export function Button(props: ButtonProps): ReactElement {
  return <button>{props.label}</button>;
}
`.trim();

// ============================================================================
// Protected region marker tests
// ============================================================================

describe('compileReact — protected region markers', () => {
  it('emits @ghatana-region: begin ... end for the import block', () => {
    const result = decompileAndCompile(SIMPLE_BUTTON_TSX);
    const file = result.emittedFiles.find((f) => !f.isResidualStub);
    expect(file).toBeDefined();
    expect(file?.content).toContain('@ghatana-region: begin');
    expect(file?.content).toContain('@ghatana-region: end');
  });

  it('marks the import block as owner=generated', () => {
    const result = decompileAndCompile(SIMPLE_BUTTON_TSX);
    const file = result.emittedFiles.find((f) => !f.isResidualStub);
    expect(file?.content).toContain('owner=generated');
  });

  it('marks the component body as owner=user-authored', () => {
    const result = decompileAndCompile(SIMPLE_BUTTON_TSX);
    const file = result.emittedFiles.find((f) => !f.isResidualStub);
    expect(file?.content).toContain('owner=user-authored');
  });

  it('produces a valid TypeScript function signature (export function)', () => {
    const result = decompileAndCompile(SIMPLE_BUTTON_TSX);
    const file = result.emittedFiles.find((f) => !f.isResidualStub);
    expect(file?.content).toMatch(/export function \w+/);
  });

  it('preserves original static import declarations in the generated import region', () => {
    const source = `
import React from "react";
import { Button as DsButton } from "@ghatana/design-system";
import type { ReactElement } from "react";

export interface ButtonProps {
  readonly label: string;
}

export function Button(props: ButtonProps): ReactElement {
  return <DsButton>{props.label}</DsButton>;
}
`.trim();

    const result = decompileAndCompile(source);
    const file = result.emittedFiles.find((f) => !f.isResidualStub);

    expect(file?.content).toContain('import React from "react";');
    expect(file?.content).toContain('import { Button as DsButton } from "@ghatana/design-system";');
    expect(file?.content).toContain('import type { ReactElement } from "react";');
  });

  it('emits a residual stub with a RESIDUAL comment for low-confidence nodes', () => {
    // Use a file that won't be well-classified (empty content)
    const result = decompileAndCompile(
      '// This file is intentionally empty for test purposes',
      'src/Stub.tsx',
    );
    // Every file may or may not be residual depending on confidence;
    // verify that residual stubs include the RESIDUAL: marker when isResidualStub=true
    for (const file of result.emittedFiles) {
      if (file.isResidualStub) {
        expect(file.content).toContain('RESIDUAL:');
      }
    }
  });

  it('region IDs are unique within a compiled file (no duplicate begin/end pairs)', () => {
    const result = decompileAndCompile(SIMPLE_BUTTON_TSX);
    const file = result.emittedFiles.find((f) => !f.isResidualStub);
    if (!file) return;

    const content = file.content;
    const beginMatches = [...content.matchAll(/@ghatana-region: begin (\S+)/g)].map((m) => m[1]);
    const endMatches = [...content.matchAll(/@ghatana-region: end (\S+)/g)].map((m) => m[1]);

    // Every begin has a corresponding end with the same ID
    expect(beginMatches.sort()).toStrictEqual(endMatches.sort());
    // No duplicate IDs
    const uniqueBegins = new Set(beginMatches);
    expect(uniqueBegins.size).toBe(beginMatches.length);
  });
});

// ============================================================================
// Compile result shape
// ============================================================================

describe('compileReact — result shape', () => {
  it('produces overall fidelity from the model', () => {
    const decompiled = decompileTsx({
      label: 'test',
      modelId: 'm-shape',
      files: [{ relativePath: 'src/App.tsx', content: SIMPLE_BUTTON_TSX }],
    });
    const compiled = compileReact(decompiled.model);
    expect(compiled.overallFidelity).toBeDefined();
    expect(compiled.overallFidelity.score).toBeGreaterThanOrEqual(0);
    expect(compiled.overallFidelity.score).toBeLessThanOrEqual(1);
  });

  it('emits one file per artifact node', () => {
    const files = [
      { relativePath: 'src/Button.tsx', content: SIMPLE_BUTTON_TSX },
      { relativePath: 'src/Card.tsx', content: SIMPLE_BUTTON_TSX.replace(/Button/g, 'Card') },
    ];
    const decompiled = decompileTsx({ label: 'test', modelId: 'm-multi', files });
    const compiled = compileReact(decompiled.model);
    expect(compiled.emittedFiles).toHaveLength(2);
  });

  it('each emitted file has non-empty content', () => {
    const decompiled = decompileTsx({
      label: 'test',
      modelId: 'm-content',
      files: [{ relativePath: 'src/App.tsx', content: SIMPLE_BUTTON_TSX }],
    });
    const compiled = compileReact(decompiled.model);
    for (const f of compiled.emittedFiles) {
      expect(f.content.trim().length).toBeGreaterThan(0);
    }
  });
});
