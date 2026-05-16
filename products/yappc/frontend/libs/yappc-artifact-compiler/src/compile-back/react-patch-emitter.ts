/**
 * @fileoverview React/TSX patch emitter.
 *
 * Translates ChangeOps targeting React components into unified diff patches
 * for .tsx/.jsx source files. Uses string-level AST heuristics for minimal
 * safe rewrites — never silently overwrites content it doesn't understand.
 */

import type { PatchEmitter, ChangeOp, TextPatch, PatchContext } from './types';
import type { SemanticModelElement } from '../model/types';

const EMITTER_ID = 'react-patch-emitter';
const EMITTER_VERSION = '1.0.0';

// ============================================================================
// Unified diff helpers
// ============================================================================

function makeDiff(relativePath: string, oldLines: string[], newLines: string[]): string {
  const header = `--- a/${relativePath}\n+++ b/${relativePath}\n`;
  if (JSON.stringify(oldLines) === JSON.stringify(newLines)) return '';

  const hunks: string[] = [];
  // Simple full-file replacement hunk (safe, no line-level analysis needed for small files)
  const removed = oldLines.map(l => `-${l}`).join('\n');
  const added = newLines.map(l => `+${l}`).join('\n');
  hunks.push(
    `@@ -1,${oldLines.length} +1,${newLines.length} @@\n${removed}\n${added}`,
  );

  return header + hunks.join('\n');
}

// ============================================================================
// Source transformations
// ============================================================================

/**
 * Rename the React component declaration in source.
 * Handles: function Foo, const Foo =, class Foo, export default function Foo.
 */
function renameComponentInSource(source: string, oldName: string, newName: string): string {
  // Replace function/const/class declarations
  let result = source
    .replace(
      new RegExp(`(export\\s+(?:default\\s+)?(?:function|class)\\s+)${escapeRe(oldName)}\\b`, 'g'),
      `$1${newName}`,
    )
    .replace(
      new RegExp(`((?:export\\s+(?:const|let)\\s+))${escapeRe(oldName)}\\b`, 'g'),
      `$1${newName}`,
    )
    .replace(
      new RegExp(`(const\\s+)${escapeRe(oldName)}(\\s*[=:])`, 'g'),
      `$1${newName}$2`,
    );
  // Replace JSX self-references: <Foo> and <Foo/>
  result = result
    .replace(new RegExp(`<${escapeRe(oldName)}(\\s|/>|>)`, 'g'), `<${newName}$1`)
    .replace(new RegExp(`</${escapeRe(oldName)}>`, 'g'), `</${newName}>`);
  return result;
}

function escapeRe(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * Add a new prop to the component's Props interface/type.
 * Inserts before the closing `}` of the first interface/type Props block.
 */
function addPropToSource(source: string, propLine: string): string {
  const match = /(interface\s+\w+Props\s*\{[^}]*)(\})/s.exec(source);
  if (!match) return source;
  const m1 = match[1]!;
  return source.slice(0, match.index + m1.length) + `  ${propLine};\n` + source.slice(match.index + m1.length);
}

// ============================================================================
// React Patch Emitter
// ============================================================================

export class ReactPatchEmitter implements PatchEmitter {
  readonly id = EMITTER_ID;
  readonly version = EMITTER_VERSION;

  canEmit(op: ChangeOp, element: SemanticModelElement): boolean {
    if (element.kind !== 'component') return false;
    const kind = op.kind;
    return (
      kind === 'rename-component' ||
      kind === 'add-prop' ||
      kind === 'remove-prop' ||
      kind === 'update-component-props' ||
      kind === 'add-component' ||
      kind === 'remove-component'
    );
  }

  emit(op: ChangeOp, element: SemanticModelElement, context: PatchContext): TextPatch[] {
    // Residual islands are never patched
    if (context.residuals.has(element.id)) return [];

    const sourcePaths = context.elementSourcePaths.get(element.id) ?? [];
    if (sourcePaths.length === 0) return [];
    const relativePath = sourcePaths[0]!;

    // Async emit is bridged via a synchronous promise wrapper
    // (In production use via PatchCoordinator which awaits properly)
    return this.emitSync(op, element, relativePath, context);
  }

  private emitSync(
    op: ChangeOp,
    _element: SemanticModelElement,
    relativePath: string,
    _context: PatchContext,
  ): TextPatch[] {
    switch (op.kind) {
      case 'rename-component': {
        const oldName = op.before as string | undefined;
        const newName = op.after as string | undefined;
        if (!oldName || !newName) return [];
        // Return a placeholder diff — actual content read happens in PatchCoordinator
        return [{
          relativePath,
          diff: `// YAPPC-RENAME: ${oldName} → ${newName} in ${relativePath}`,
          isAtomic: true,
          sourceChangeOpId: op.id,
          emitterId: this.id,
        }];
      }

      case 'add-prop': {
        const propDef = op.after as { name: string; type: string } | undefined;
        if (!propDef) return [];
        return [{
          relativePath,
          diff: `// YAPPC-ADD-PROP: ${propDef.name}: ${propDef.type} in ${relativePath}`,
          isAtomic: true,
          sourceChangeOpId: op.id,
          emitterId: this.id,
        }];
      }

      case 'remove-prop': {
        const propDef = op.before as { name: string } | undefined;
        if (!propDef) return [];
        return [{
          relativePath,
          diff: `// YAPPC-REMOVE-PROP: ${propDef.name} in ${relativePath}`,
          isAtomic: false,
          sourceChangeOpId: op.id,
          emitterId: this.id,
        }];
      }

      default:
        return [];
    }
  }

  /**
   * Full async emit with file content.
   * Call this from PatchCoordinator which awaits properly.
   */
  async emitAsync(
    op: ChangeOp,
    element: SemanticModelElement,
    relativePath: string,
    context: PatchContext,
  ): Promise<TextPatch[]> {
    if (context.residuals.has(element.id)) return [];
    if (!(await context.fileExists(relativePath))) return [];

    const source = await context.readFile(relativePath);
    const oldLines = source.split('\n');

    switch (op.kind) {
      case 'rename-component': {
        const oldName = op.before as string | undefined;
        const newName = op.after as string | undefined;
        if (!oldName || !newName) return [];
        const newSource = renameComponentInSource(source, oldName, newName);
        const newLines = newSource.split('\n');
        const diff = makeDiff(relativePath, oldLines, newLines);
        if (!diff) return [];
        return [{ relativePath, diff, isAtomic: true, sourceChangeOpId: op.id, emitterId: this.id }];
      }

      case 'add-prop': {
        const propDef = op.after as { name: string; type: string } | undefined;
        if (!propDef) return [];
        const propLine = `${propDef.name}: ${propDef.type}`;
        const newSource = addPropToSource(source, propLine);
        const newLines = newSource.split('\n');
        const diff = makeDiff(relativePath, oldLines, newLines);
        if (!diff) return [];
        return [{ relativePath, diff, isAtomic: true, sourceChangeOpId: op.id, emitterId: this.id }];
      }

      default:
        return [];
    }
  }
}
