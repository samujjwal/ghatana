import fs from 'node:fs';
import path from 'node:path';
import ts from 'typescript';
import { describe, expect, it } from 'vitest';

const projectRouteDir = path.resolve(__dirname, '..');
const webRoot = path.resolve(projectRouteDir, '../../../..');

const guardedFiles = [
  '_phaseCockpit.tsx',
  'PhaseEmbeddedSurface.tsx',
  'PhaseCockpitView.tsx',
  'lifecycle.tsx',
  'intent.tsx',
  'shape.tsx',
  'validate.tsx',
  'generate.tsx',
  'run.tsx',
  'observe.tsx',
  'learn.tsx',
  'evolve.tsx',
] as const;

const visibleTextAttributes = new Set(['aria-label', 'title', 'placeholder', 'alt']);
const lifecyclePhases = ['intent', 'shape', 'validate', 'generate', 'run', 'observe', 'learn', 'evolve'] as const;

interface RawStringFinding {
  readonly file: string;
  readonly line: number;
  readonly text: string;
}

function lineFor(sourceFile: ts.SourceFile, position: number): number {
  return sourceFile.getLineAndCharacterOfPosition(position).line + 1;
}

function collectRawVisibleStrings(fileName: string): readonly RawStringFinding[] {
  const filePath = path.join(projectRouteDir, fileName);
  const source = fs.readFileSync(filePath, 'utf8');
  const sourceFile = ts.createSourceFile(filePath, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  const findings: RawStringFinding[] = [];

  function visit(node: ts.Node): void {
    if (ts.isJsxText(node)) {
      const text = node.getText(sourceFile).replace(/\s+/g, ' ').trim();
      if (text.length > 0) {
        findings.push({ file: fileName, line: lineFor(sourceFile, node.getStart(sourceFile)), text });
      }
    }

    if (ts.isJsxAttribute(node) && visibleTextAttributes.has(node.name.getText(sourceFile))) {
      const initializer = node.initializer;
      if (initializer && ts.isStringLiteral(initializer)) {
        findings.push({
          file: fileName,
          line: lineFor(sourceFile, initializer.getStart(sourceFile)),
          text: initializer.text,
        });
      }
    }

    ts.forEachChild(node, visit);
  }

  visit(sourceFile);
  return findings;
}

describe('phase cockpit i18n contract', () => {
  it('keeps phase cockpit and lifecycle route visible copy behind @ghatana/i18n', () => {
    const findings = guardedFiles.flatMap(fileName => collectRawVisibleStrings(fileName));

    expect(findings).toEqual([]);
  });

  it('keeps backend phase action disabled reason keys in the translation catalog', () => {
    const commonCatalog = JSON.parse(
      fs.readFileSync(path.join(webRoot, 'public/locales/en/common.json'), 'utf8')
    ) as Record<string, string>;
    const backendSources = [
      path.resolve(webRoot, '../../core/yappc-services/src/main/java/com/ghatana/yappc/services/phase/PhaseActionAuthorizationService.java'),
      path.resolve(webRoot, '../../core/yappc-services/src/main/java/com/ghatana/yappc/services/lifecycle/AdvancePhaseUseCase.java'),
    ];
    const keyPattern = /phaseAction\.disabled\.[A-Za-z0-9]+/g;
    const backendKeys = new Set<string>();

    for (const sourcePath of backendSources) {
      const source = fs.readFileSync(sourcePath, 'utf8');
      for (const match of source.matchAll(keyPattern)) {
        backendKeys.add(match[0]);
      }
    }

    const missingKeys = [...backendKeys].filter(key => !(key in commonCatalog));
    expect(missingKeys).toEqual([]);
  });

  it('keeps phase cockpit and phase action translation key usage covered by the catalog', () => {
    const commonCatalog = JSON.parse(
      fs.readFileSync(path.join(webRoot, 'public/locales/en/common.json'), 'utf8')
    ) as Record<string, string>;
    const sourceFiles = guardedFiles.map(fileName => path.join(projectRouteDir, fileName)).concat([
      path.resolve(webRoot, '../../core/yappc-services/src/main/java/com/ghatana/yappc/services/phase/PhaseActionAuthorizationService.java'),
      path.resolve(webRoot, '../../core/yappc-services/src/main/java/com/ghatana/yappc/services/lifecycle/AdvancePhaseUseCase.java'),
    ]);
    const keyPattern = /phase(?:Cockpit|Action)\.[A-Za-z0-9.]+/g;
    const usedKeys = new Set<string>();

    for (const sourcePath of sourceFiles) {
      const source = fs.readFileSync(sourcePath, 'utf8');
      for (const match of source.matchAll(keyPattern)) {
        if (!match[0].endsWith('.')) {
          usedKeys.add(match[0]);
        }
      }
    }
    for (const phase of lifecyclePhases) {
      usedKeys.add(`phaseCockpit.phase.${phase}`);
    }

    const missingKeys = [...usedKeys].filter(key => !(key in commonCatalog));
    expect(missingKeys).toEqual([]);
  });
});
