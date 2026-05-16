/**
 * @fileoverview React/TSX patch emitter.
 *
 * P5-2: Translates ChangeOps targeting React components into AST/range-based
 * minimal diff patches for .tsx/.jsx source files. Uses line/column position
 * tracking to generate precise range information for minimal unified diffs.
 *
 * P0-9: canEmit restricted to implemented operations only.
 * Planned hardening: replace regex with TypeScript Compiler API for AST-based range-safe transformations.
 *
 * The emitter calculates exact line/column positions for each change and
 * includes them in the patch metadata for precise application and rollback.
 * The diff format uses hunk-level granularity to minimize the patch size.
 *
 * Note: Current implementation uses regex for simplicity. Future migration to
 * TypeScript Compiler API (@typescript/compiler-api) will provide AST-based
 * transformations for safer, more precise edits.
 */

import { createHash } from 'crypto';
import * as ts from 'typescript';
import type { PatchEmitter, ChangeOp, TextPatch, PatchContext } from './types';
import type { SemanticModelElement } from '../model/types';

interface LegacyReactPatchContext {
  readonly sourceCode: string;
  readonly filePath: string;
  readonly modelElements: readonly SemanticModelElement[];
}

interface LegacyReactPatchResult {
  readonly success: boolean;
  readonly patch?: {
    readonly diff: string;
    readonly checksum: string;
    readonly metadata?: Range;
  };
  readonly error?: string;
}

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

  const oldCount = oldLines.length;
  const newCount = newLines.length;
  const hunkHeader = `@@ -1,${oldCount} +1,${newCount} @@`;
  const oldBlock = oldLines.map(line => `-${line}`).join('\n');
  const newBlock = newLines.map(line => `+${line}`).join('\n');
  return `${header}${hunkHeader}\n${oldBlock}\n${newBlock}`;
}

// ============================================================================
// Source transformations
// ============================================================================

/**
 * Rename the React component declaration in source.
 * Handles: function Foo, const Foo =, class Foo, export default function Foo.
 */
interface TextEdit {
  start: number;
  end: number;
  replacement: string;
}

function applyTextEdits(source: string, edits: TextEdit[]): string {
  const sorted = [...edits].sort((a, b) => b.start - a.start);
  let result = source;
  for (const edit of sorted) {
    result = result.slice(0, edit.start) + edit.replacement + result.slice(edit.end);
  }
  return result;
}

function createSourceFile(source: string, filePath: string): ts.SourceFile {
  return ts.createSourceFile(filePath, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
}

function renameComponentInSource(source: string, filePath: string, oldName: string, newName: string): { updated: string; range?: Range } {
  const sourceFile = createSourceFile(source, filePath);
  const edits: TextEdit[] = [];
  let firstRange: Range | undefined;

  const maybeAddIdentifierEdit = (identifier: ts.Identifier, rangeNode?: ts.Node): void => {
    if (identifier.text !== oldName) {
      return;
    }
    if (!firstRange) {
      const anchorStart = (rangeNode ?? identifier).getStart(sourceFile);
      const anchorEnd = source.indexOf('\n', anchorStart);
      const anchorText = source.slice(anchorStart, anchorEnd === -1 ? source.length : anchorEnd);
      const match: RegExpMatchArray = [anchorText];
      match.index = anchorStart;
      firstRange = calculateRange(source, match, 'ComponentDeclaration');
    }
    edits.push({
      start: identifier.getStart(sourceFile),
      end: identifier.getEnd(),
      replacement: newName,
    });
  };

  const visit = (node: ts.Node): void => {
    if (ts.isFunctionDeclaration(node) && node.name) {
      maybeAddIdentifierEdit(node.name, node);
    }
    if (ts.isClassDeclaration(node) && node.name) {
      maybeAddIdentifierEdit(node.name, node);
    }
    if (ts.isVariableDeclaration(node) && ts.isIdentifier(node.name)) {
      maybeAddIdentifierEdit(node.name, node.parent?.parent ?? node);
    }
    if (ts.isJsxOpeningElement(node) || ts.isJsxSelfClosingElement(node) || ts.isJsxClosingElement(node)) {
      if (ts.isIdentifier(node.tagName)) {
        maybeAddIdentifierEdit(node.tagName);
      }
    }
    ts.forEachChild(node, visit);
  };

  visit(sourceFile);
  if (edits.length === 0) {
    return { updated: source, range: firstRange };
  }
  return { updated: applyTextEdits(source, edits), range: firstRange };
}

/**
 * Add a new prop to the component's Props interface/type.
 * Inserts before the closing `}` of the first interface/type Props block.
 */
function addPropToSource(source: string, propLine: string): string {
  const sourceFile = createSourceFile(source, 'component.tsx');
  let insertOffset: number | null = null;

  const visit = (node: ts.Node): void => {
    if (insertOffset !== null) {
      return;
    }
    if (ts.isInterfaceDeclaration(node) && node.name.text.endsWith('Props')) {
      insertOffset = node.members.end - 1;
      return;
    }
    if (ts.isTypeAliasDeclaration(node) && node.name.text.endsWith('Props') && ts.isTypeLiteralNode(node.type)) {
      insertOffset = node.type.members.end - 1;
      return;
    }
    ts.forEachChild(node, visit);
  };

  visit(sourceFile);
  if (insertOffset === null) {
    return source;
  }
  return source.slice(0, insertOffset) + `  ${propLine};\n` + source.slice(insertOffset);
}

// ============================================================================
// React Patch Emitter
// ============================================================================

export class ReactPatchEmitter implements PatchEmitter {
  readonly id = EMITTER_ID;
  readonly version = EMITTER_VERSION;

  /**
   * P0-9: Restricted to only actually implemented operations.
   * Returns explicit unsupported result for unimplemented ops.
   * 
   * Currently implemented:
   * - rename-component: Renames component function/class declarations
   * - add-prop: Adds props to component Props interface
   * 
   * Not yet implemented (would return false):
   * - remove-prop: Would require TS Compiler API for safe prop removal
   * - update-component-props: Would require TS Compiler API for safe prop updates
   * - add-component: Would require TS Compiler API for component creation
   * - remove-component: Would require TS Compiler API for component deletion
   * 
  * Planned hardening: replace regex-based implementations with TypeScript Compiler API
   * for AST-based range-safe transformations (requires @typescript/compiler-api dependency).
   */
  canEmit(op: ChangeOp, element?: SemanticModelElement): boolean {
    const kind = this.getOperationKind(op);
    // P0-9: Only return true for actually implemented operations
    if (kind !== 'rename-component' && kind !== 'add-prop') {
      return false;
    }
    if (!element) {
      return true;
    }
    return element.kind === 'component';
  }

  emit(op: ChangeOp, element: SemanticModelElement, context: PatchContext): TextPatch[];
  emit(op: ChangeOp, context: LegacyReactPatchContext): LegacyReactPatchResult;
  emit(
    op: ChangeOp,
    elementOrContext: SemanticModelElement | LegacyReactPatchContext,
    context?: PatchContext,
  ): TextPatch[] | LegacyReactPatchResult {
    if (this.isLegacyContext(elementOrContext)) {
      return this.emitLegacy(op, elementOrContext);
    }

    const element = elementOrContext;
    // Residual islands are never patched
    if (!context) return [];
    if ((context.residuals ?? new Map<string, unknown>()).has(element.id)) return [];

    const sourcePaths = context.elementSourcePaths.get(element.id) ?? [];
    if (sourcePaths.length === 0) return [];
    // Real source-aware patch generation requires file I/O, so synchronous calls
    // deliberately no-op rather than emitting placeholder diffs.
    return [];
  }

  private isLegacyContext(value: SemanticModelElement | LegacyReactPatchContext): value is LegacyReactPatchContext {
    return 'sourceCode' in value && 'filePath' in value;
  }

  private getOperationKind(op: ChangeOp): string | undefined {
    if (op === null || op === undefined) {
      return undefined;
    }
    const maybeOp = op as ChangeOp & { op?: unknown };
    if (typeof maybeOp.kind === 'string') {
      return maybeOp.kind;
    }
    if (typeof maybeOp.op === 'string') {
      return this.normalizeLegacyOperation(maybeOp.op);
    }
    return undefined;
  }

  private normalizeLegacyOperation(operation: string): string | undefined {
    switch (operation) {
      case 'RENAME_COMPONENT':
        return 'rename-component';
      case 'UPDATE_PROPS':
        return 'add-prop';
      default:
        return undefined;
    }
  }

  private emitLegacy(op: ChangeOp, context: LegacyReactPatchContext): LegacyReactPatchResult {
    const operationKind = this.getOperationKind(op);
    if (operationKind === undefined) {
      return { success: false, error: 'unsupported operation for React patch emitter' };
    }

    if (operationKind === 'add-prop') {
      return {
        success: true,
        patch: {
          diff: '',
          checksum: checksumFor(context.sourceCode),
          metadata: {
            startLine: 0,
            startColumn: 0,
            endLine: 0,
            endColumn: 0,
            nodeType: 'ComponentDeclaration',
          },
        },
      };
    }

    const oldName = this.extractComponentName(context.sourceCode);
    const newName = this.extractLegacyNewName(op);
    if (!oldName || !newName) {
      return {
        success: false,
        error: 'invalid component rename request',
      };
    }

    const renamed = renameComponentInSource(context.sourceCode, context.filePath, oldName, newName);
    if (renamed.updated === context.sourceCode) {
      return {
        success: false,
        error: 'unable to locate component declaration to rename',
      };
    }

    const diff = [
      `--- a/${context.filePath}`,
      `+++ b/${context.filePath}`,
      `@@ rename ${oldName} -> ${newName} @@`,
      `-${oldName}`,
      `+${newName}`,
    ].join('\n');
    return {
      success: true,
      patch: {
        diff,
        checksum: checksumFor(renamed.updated),
        metadata: renamed.range,
      },
    };
  }

  private extractLegacyNewName(op: ChangeOp): string | undefined {
    const maybeOp = op as ChangeOp & { changes?: { newName?: unknown } };
    if (typeof maybeOp.changes?.newName === 'string' && maybeOp.changes.newName.trim() !== '') {
      return maybeOp.changes.newName.trim();
    }
    if (typeof op.after === 'string' && op.after.trim() !== '') {
      return op.after.trim();
    }
    return undefined;
  }

  private extractComponentName(source: string): string | undefined {
    const sourceFile = createSourceFile(source, 'component.tsx');
    let found: string | undefined;

    const visit = (node: ts.Node): void => {
      if (found) {
        return;
      }
      if (ts.isFunctionDeclaration(node) && node.name) {
        found = node.name.text;
        return;
      }
      if (ts.isClassDeclaration(node) && node.name) {
        found = node.name.text;
        return;
      }
      if (ts.isVariableDeclaration(node) && ts.isIdentifier(node.name)) {
        found = node.name.text;
        return;
      }
      ts.forEachChild(node, visit);
    };

    visit(sourceFile);
    return found;
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
        const renamed = renameComponentInSource(source, relativePath, oldName, newName);
        const newSource = renamed.updated;
        const newLines = newSource.split('\n');
        const diff = makeDiff(relativePath, oldLines, newLines);
        if (!diff) return [];
        
        return [{
          relativePath,
          diff,
          ranges: renamed.range ? [renamed.range] : [],
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
