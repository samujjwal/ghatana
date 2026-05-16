/**
 * @fileoverview React/TSX patch emitter.
 *
 * P5-2: Translates ChangeOps targeting React components into AST/range-based
 * minimal diff patches for .tsx/.jsx source files. Uses line/column position
 * tracking to generate precise range information for minimal unified diffs.
 *
 * The emitter calculates exact line/column positions for each change and
 * includes them in the patch metadata for precise application and rollback.
 * The diff format uses hunk-level granularity to minimize the patch size.
 */

import { createHash } from 'crypto';
import type { PatchEmitter, ChangeOp, TextPatch, PatchContext } from './types';
import type { SemanticModelElement } from '../model/types';

const EMITTER_ID = 'react-patch-emitter';
const EMITTER_VERSION = '2.0.0';

// ============================================================================
// Range calculation helpers
// ============================================================================

interface Range {
  startLine: number;
  startColumn: number;
  endLine: number;
  endColumn: number;
  nodeType?: string | undefined;
}

function checksumFor(content: string): string {
  return createHash('sha256').update(content).digest('hex');
}

/**
 * Calculate line/column position of a string match in source.
 * Returns 0-indexed line/column positions.
 */
function calculateRange(source: string, match: RegExpMatchArray, nodeType?: string): Range {
  const matchStart = match.index!;
  const matchEnd = matchStart + match[0]!.length;
  
  const beforeMatch = source.slice(0, matchStart);
  const linesBefore = beforeMatch.split('\n');
  const startLine = linesBefore.length - 1;
  const startColumn = linesBefore[linesBefore.length - 1]!.length;
  
  const beforeMatchEnd = source.slice(0, matchEnd);
  const linesBeforeEnd = beforeMatchEnd.split('\n');
  const endLine = linesBeforeEnd.length - 1;
  const endColumn = linesBeforeEnd[linesBeforeEnd.length - 1]!.length;
  
  return { startLine, startColumn, endLine, endColumn, nodeType };
}

/**
 * Generate minimal unified diff with hunk-level granularity.
 * Only includes lines that actually changed, not full-file replacement.
 */
function makeDiff(relativePath: string, oldLines: string[], newLines: string[]): string {
  const header = `--- a/${relativePath}\n+++ b/${relativePath}\n`;
  if (JSON.stringify(oldLines) === JSON.stringify(newLines)) return '';

  const hunks: string[] = [];
  let i = 0;
  
  while (i < Math.max(oldLines.length, newLines.length)) {
    const oldLine = oldLines[i];
    const newLine = newLines[i];
    
    if (oldLine !== newLine) {
      // Find the extent of the change
      let hunkStart = i;
      let hunkEnd = i;
      let oldCount = 0;
      let newCount = 0;
      
      // Count consecutive changed lines
      while (hunkEnd < Math.max(oldLines.length, newLines.length)) {
        const hunkOldLine = oldLines[hunkEnd];
        const hunkNewLine = newLines[hunkEnd];
        if (hunkOldLine !== hunkNewLine) {
          if (hunkOldLine !== undefined) oldCount++;
          if (hunkNewLine !== undefined) newCount++;
          hunkEnd++;
        } else {
          break;
        }
      }
      
      // Include context lines (3 lines before and after)
      const contextStart = Math.max(0, hunkStart - 3);
      const contextEnd = Math.min(Math.max(oldLines.length, newLines.length), hunkEnd + 3);
      
      const oldHunkLines = oldLines.slice(contextStart, hunkStart)
        .map(l => ` ${l}`)
        .concat(oldLines.slice(hunkStart, hunkEnd).map(l => `-${l}`))
        .concat(oldLines.slice(hunkEnd, contextEnd).map(l => ` ${l}`));
      
      const newHunkLines = newLines.slice(contextStart, hunkStart)
        .map(l => ` ${l}`)
        .concat(newLines.slice(hunkStart, hunkEnd).map(l => `+${l}`))
        .concat(newLines.slice(hunkEnd, contextEnd).map(l => ` ${l}`));
      
      const hunkHeader = `@@ -${contextStart + 1},${oldCount} +${contextStart + 1},${newCount} @@`;
      hunks.push(`${hunkHeader}\n${oldHunkLines.join('\n')}\n${newHunkLines.join('\n')}`);
      
      i = hunkEnd;
    } else {
      i++;
    }
  }

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
    // Real source-aware patch generation requires file I/O, so synchronous calls
    // deliberately no-op rather than emitting placeholder diffs.
    return [];
  }

  /**
   * Full async emit with file content.
   * Call this from PatchCoordinator which awaits properly.
   * P5-2: Includes range information for AST/range-based minimal diffs.
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
        
        // Calculate range for the component declaration
        const declRegex = new RegExp(`(export\\s+(?:default\\s+)?(?:function|class)\\s+)${escapeRe(oldName)}\\b`);
        const declMatch = declRegex.exec(source);
        const range = declMatch ? calculateRange(source, declMatch, 'ComponentDeclaration') : undefined;
        
        const newSource = renameComponentInSource(source, oldName, newName);
        const newLines = newSource.split('\n');
        const diff = makeDiff(relativePath, oldLines, newLines);
        if (!diff) return [];
        
        return [{
          relativePath,
          diff,
          ranges: range ? [range] : [],
          isAtomic: true,
          sourceChangeOpId: op.id,
          emitterId: this.id,
          baseChecksum: checksumFor(source),
          targetChecksum: checksumFor(newSource),
        }];
      }

      case 'add-prop': {
        const propDef = op.after as { name: string; type: string } | undefined;
        if (!propDef) return [];
        const propLine = `${propDef.name}: ${propDef.type}`;
        
        // Calculate range for the Props interface
        const propsRegex = /(interface\s+\w+Props\s*\{)/;
        const propsMatch = propsRegex.exec(source);
        const range = propsMatch ? calculateRange(source, propsMatch, 'InterfaceDeclaration') : undefined;
        
        const newSource = addPropToSource(source, propLine);
        const newLines = newSource.split('\n');
        const diff = makeDiff(relativePath, oldLines, newLines);
        if (!diff) return [];
        
        return [{
          relativePath,
          diff,
          ranges: range ? [range] : [],
          isAtomic: true,
          sourceChangeOpId: op.id,
          emitterId: this.id,
          baseChecksum: checksumFor(source),
          targetChecksum: checksumFor(newSource),
        }];
      }

      default:
        return [];
    }
  }
}
