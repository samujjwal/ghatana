/**
 * Codegen Preview and Diff/Merge
 *
 * Handles codegen preview with diff/merge and ownership regions.
 *
 * @doc.type service
 * @doc.purpose Codegen preview and diff/merge
 * @doc.layer product
 */

export interface CodegenPreview {
  /** Generated code */
  generatedCode: string;
  /** Original code */
  originalCode: string;
  /** Ownership regions */
  ownershipRegions: OwnershipRegion[];
  /** Diff */
  diff: DiffRegion[];
  /** Merge conflicts */
  conflicts: MergeConflict[];
}

export interface OwnershipRegion {
  /** Region ID */
  id: string;
  /** Start line */
  startLine: number;
  /** End line */
  endLine: number;
  /** Owner (system or user) */
  owner: 'system' | 'user';
  /** Region type */
  type: 'imports' | 'component' | 'props' | 'hooks' | 'render' | 'styles' | 'other';
  /** Description */
  description?: string;
  /** Editable flag */
  editable: boolean;
}

export interface DiffRegion {
  /** Region ID */
  id: string;
  /** Type of change */
  type: 'addition' | 'deletion' | 'modification';
  /** Start line */
  startLine: number;
  /** End line */
  endLine: number;
  /** Original content */
  originalContent: string;
  /** New content */
  newContent: string;
  /** Owner */
  owner: 'system' | 'user';
}

export interface MergeConflict {
  /** Conflict ID */
  id: string;
  /** Start line */
  startLine: number;
  /** End line */
  endLine: number;
  /** System version */
  systemVersion: string;
  /** User version */
  userVersion: string;
  /** Resolution strategy */
  resolution?: 'accept-system' | 'accept-user' | 'manual';
}

export interface MergeOptions {
  /** Prefer system changes */
  preferSystem?: boolean;
  /** Prefer user changes */
  preferUser?: boolean;
  /** Mark conflicts for manual resolution */
  markConflicts?: boolean;
  /** Preserve user comments */
  preserveComments?: boolean;
}

/**
 * Generate codegen preview
 */
export function generateCodegenPreview(
  originalCode: string,
  generatedCode: string
): CodegenPreview {
  const ownershipRegions = identifyOwnershipRegions(generatedCode);
  const diff = computeDiff(originalCode, generatedCode, ownershipRegions);
  const conflicts = detectMergeConflicts(originalCode, generatedCode, ownershipRegions);

  return {
    generatedCode,
    originalCode,
    ownershipRegions,
    diff,
    conflicts,
  };
}

/**
 * Identify ownership regions in code
 */
function identifyOwnershipRegions(code: string): OwnershipRegion[] {
  const regions: OwnershipRegion[] = [];
  const lines = code.split('\n');

  let currentRegion: OwnershipRegion | null = null;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const trimmed = line.trim();

    // Detect ownership markers
    if (trimmed.startsWith('// @system-owned')) {
      if (currentRegion) {
        regions.push(currentRegion);
      }
      currentRegion = {
        id: `region-${i}`,
        startLine: i + 1,
        endLine: i + 1,
        owner: 'system',
        type: 'other',
        editable: false,
      };
    } else if (trimmed.startsWith('// @user-owned')) {
      if (currentRegion) {
        regions.push(currentRegion);
      }
      currentRegion = {
        id: `region-${i}`,
        startLine: i + 1,
        endLine: i + 1,
        owner: 'user',
        type: 'other',
        editable: true,
      };
    } else if (trimmed.startsWith('// @end-region')) {
      if (currentRegion) {
        currentRegion.endLine = i + 1;
        regions.push(currentRegion);
        currentRegion = null;
      }
    } else if (currentRegion) {
      currentRegion.endLine = i + 1;
    }

    // Auto-detect regions based on patterns
    if (!currentRegion) {
      const regionType = detectRegionType(trimmed, i, lines);
      if (regionType) {
        currentRegion = {
          id: `region-${i}`,
          startLine: i + 1,
          endLine: i + 1,
          owner: 'system',
          type: regionType,
          editable: regionType === 'props' || regionType === 'styles',
        };
      }
    }
  }

  if (currentRegion) {
    regions.push(currentRegion);
  }

  return regions;
}

/**
 * Detect region type from line
 */
function detectRegionType(line: string, index: number, lines: string[]): OwnershipRegion['type'] | null {
  if (line.startsWith('import ') || line.startsWith('from ')) {
    return 'imports';
  }
  if (line.startsWith('export function') || line.startsWith('export const') || line.startsWith('interface ')) {
    return 'component';
  }
  if (line.startsWith('interface ') && line.includes('Props')) {
    return 'props';
  }
  if (line.includes('use') && line.startsWith('const ')) {
    return 'hooks';
  }
  if (line.startsWith('return ') || line.includes('return (')) {
    return 'render';
  }
  return null;
}

/**
 * Compute diff between original and generated code
 */
function computeDiff(
  originalCode: string,
  generatedCode: string,
  ownershipRegions: OwnershipRegion[]
): DiffRegion[] {
  const diff: DiffRegion[] = [];
  const originalLines = originalCode.split('\n');
  const generatedLines = generatedCode.split('\n');

  // Simple line-by-line diff (in production, use a proper diff library)
  const maxLength = Math.max(originalLines.length, generatedLines.length);

  for (let i = 0; i < maxLength; i++) {
    const originalLine = originalLines[i] || '';
    const generatedLine = generatedLines[i] || '';

    if (originalLine !== generatedLine) {
      const region = findRegionForLine(i + 1, ownershipRegions);
      diff.push({
        id: `diff-${i}`,
        type: originalLine ? (generatedLine ? 'modification' : 'deletion') : 'addition',
        startLine: i + 1,
        endLine: i + 1,
        originalContent: originalLine,
        newContent: generatedLine,
        owner: region?.owner || 'system',
      });
    }
  }

  return diff;
}

/**
 * Find ownership region for line number
 */
function findRegionForLine(line: number, regions: OwnershipRegion[]): OwnershipRegion | null {
  return regions.find(region => line >= region.startLine && line <= region.endLine) || null;
}

/**
 * Detect merge conflicts
 */
function detectMergeConflicts(
  originalCode: string,
  generatedCode: string,
  ownershipRegions: OwnershipRegion[]
): MergeConflict[] {
  const conflicts: MergeConflict[] = [];
  const originalLines = originalCode.split('\n');
  const generatedLines = generatedCode.split('\n');

  // Detect user-edited regions that conflict with system changes
  const userRegions = ownershipRegions.filter(r => r.owner === 'user');

  for (const region of userRegions) {
    const originalRegionContent = originalLines.slice(region.startLine - 1, region.endLine).join('\n');
    const generatedRegionContent = generatedLines.slice(region.startLine - 1, region.endLine).join('\n');

    if (originalRegionContent !== generatedRegionContent) {
      conflicts.push({
        id: `conflict-${region.id}`,
        startLine: region.startLine,
        endLine: region.endLine,
        systemVersion: generatedRegionContent,
        userVersion: originalRegionContent,
      });
    }
  }

  return conflicts;
}

/**
 * Merge code with options
 */
export function mergeCode(
  originalCode: string,
  generatedCode: string,
  options: MergeOptions = {}
): { mergedCode: string; conflicts: MergeConflict[] } {
  const preview = generateCodegenPreview(originalCode, generatedCode);
  let mergedCode = generatedCode;
  const conflicts: MergeConflict[] = [];

  if (options.preferSystem) {
    // Keep system version for all conflicts
    conflicts.forEach(conflict => {
      conflict.resolution = 'accept-system';
    });
  } else if (options.preferUser) {
    // Keep user version for all conflicts
    conflicts.forEach(conflict => {
      conflict.resolution = 'accept-user';
      mergedCode = applyUserVersion(mergedCode, conflict);
    });
  } else if (options.markConflicts) {
    // Mark conflicts for manual resolution
    conflicts.forEach(conflict => {
      conflict.resolution = 'manual';
      mergedCode = markConflict(mergedCode, conflict);
    });
  }

  return { mergedCode, conflicts: preview.conflicts };
}

/**
 * Apply user version to merged code
 */
function applyUserVersion(code: string, conflict: MergeConflict): string {
  const lines = code.split('\n');
  const userLines = conflict.userVersion.split('\n');

  for (let i = 0; i < userLines.length; i++) {
    const lineIndex = conflict.startLine - 1 + i;
    if (lineIndex < lines.length) {
      lines[lineIndex] = userLines[i];
    }
  }

  return lines.join('\n');
}

/**
 * Mark conflict in code
 */
function markConflict(code: string, conflict: MergeConflict): string {
  const lines = code.split('\n');
  const marker = '// <<<< MERGE CONFLICT >>>>';

  lines.splice(conflict.startLine - 1, 0, marker);
  lines.splice(conflict.endLine + 1, 0, marker);

  return lines.join('\n');
}

/**
 * Get ownership region for line
 */
export function getOwnershipRegion(line: number, preview: CodegenPreview): OwnershipRegion | null {
  return findRegionForLine(line, preview.ownershipRegions);
}

/**
 * Is line editable by user
 */
export function isLineEditable(line: number, preview: CodegenPreview): boolean {
  const region = getOwnershipRegion(line, preview);
  return region?.editable || false;
}

/**
 * Apply user edit to code
 */
export function applyUserEdit(
  preview: CodegenPreview,
  line: number,
  newContent: string
): CodegenPreview {
  if (!isLineEditable(line, preview)) {
    return preview;
  }

  const lines = preview.generatedCode.split('\n');
  lines[line - 1] = newContent;

  return generateCodegenPreview(preview.originalCode, lines.join('\n'));
}

/**
 * Merge-with-fail-closed: throws if unresolved merge conflicts exist and no resolution
 * strategy has been specified.
 *
 * Use this instead of {@link mergeCode} when the caller must not silently accept broken
 * output. Callers that explicitly pass `preferSystem`, `preferUser`, or `markConflicts`
 * in the options object behave identically to `mergeCode`.
 *
 * @throws {Error} When the preview contains merge conflicts and no resolution strategy is set.
 */
export function safeMerge(
  originalCode: string,
  generatedCode: string,
  options: MergeOptions = {},
): { mergedCode: string; conflicts: MergeConflict[] } {
  const hasResolutionStrategy =
    options.preferSystem === true || options.preferUser === true || options.markConflicts === true;

  if (!hasResolutionStrategy) {
    const preview = generateCodegenPreview(originalCode, generatedCode);
    if (preview.conflicts.length > 0) {
      throw new Error(
        `safeMerge aborted: ${preview.conflicts.length} unresolved merge conflict(s) detected. ` +
          'Provide a resolution strategy via MergeOptions (preferSystem, preferUser, or markConflicts).',
      );
    }
  }

  return mergeCode(originalCode, generatedCode, options);
}

export default {
  generateCodegenPreview,
  mergeCode,
  safeMerge,
  getOwnershipRegion,
  isLineEditable,
  applyUserEdit,
};
