/**
 * Semantic Diff - Intelligent Canvas Document Diffing
 * 
 * Provides semantic differentiation between canvas states:
 * - Structural vs styling change classification
 * - Element-level diffing (added/removed/modified)
 * - Property-level change tracking
 * - JSON Patch (RFC 6902) export
 * - Diff statistics and summary
 * 
 * Benefits:
 * - Understand what changed and why
 * - Smart merge conflict resolution
 * - Efficient synchronization
 * - Version control integration
 * 
 * @module semanticDiff
 */

/**
 * Change type classification
 */
export type ChangeType = 
  | 'structural'  // Affects document structure (add/remove/reorder elements)
  | 'styling'     // Affects visual appearance (color, size, position)
  | 'content'     // Affects text or data content
  | 'metadata';   // Affects metadata (name, description, tags)

/**
 * Operation type for changes
 */
export type OperationType = 
  | 'add'
  | 'remove'
  | 'replace'
  | 'move'
  | 'copy';

/**
 * JSON Patch operation (RFC 6902)
 */
export interface JSONPatchOperation {
  op: OperationType;
  path: string;
  value?: unknown;
  from?: string; // For move and copy operations
}

/**
 * Individual property change
 */
export interface PropertyChange {
  property: string;
  oldValue: unknown;
  newValue: unknown;
  changeType: ChangeType;
}

/**
 * Element change information
 */
export interface ElementChange {
  elementId: string;
  elementType: string;
  operation: OperationType;
  changeType: ChangeType;
  properties: PropertyChange[];
  oldData?: unknown;
  newData?: unknown;
}

/**
 * Diff result
 */
export interface DiffResult {
  hasChanges: boolean;
  added: ElementChange[];
  removed: ElementChange[];
  modified: ElementChange[];
  moved: ElementChange[];
  statistics: DiffStatistics;
  patches: JSONPatchOperation[];
  timestamp: number;
}

/**
 * Diff statistics
 */
export interface DiffStatistics {
  totalChanges: number;
  structuralChanges: number;
  stylingChanges: number;
  contentChanges: number;
  metadataChanges: number;
  elementsAdded: number;
  elementsRemoved: number;
  elementsModified: number;
  elementsMoved: number;
}

/**
 * Diff options
 */
export interface DiffOptions {
  ignoreProperties?: string[];    // Properties to ignore in diff
  structuralProperties?: string[]; // Properties considered structural
  stylingProperties?: string[];    // Properties considered styling
  contentProperties?: string[];    // Properties considered content
  detectMoves?: boolean;           // Detect element moves
  generatePatches?: boolean;       // Generate JSON Patch operations
}

/**
 * Canvas document for diffing (simplified)
 */
export interface CanvasDocument {
  nodes?: Array<{ id: string; [key: string]: unknown }>;
  edges?: Array<{ id: string; [key: string]: unknown }>;
  [key: string]: unknown;
}

/**
 * Default property classifications
 */
const DEFAULT_STRUCTURAL_PROPS = [
  'type', 'parent', 'children', 'source', 'target', 'connections'
];

const DEFAULT_STYLING_PROPS = [
  'x', 'y', 'width', 'height', 'color', 'backgroundColor', 'borderColor',
  'fontSize', 'fontFamily', 'opacity', 'rotation', 'zIndex'
];

const DEFAULT_CONTENT_PROPS = [
  'text', 'label', 'data', 'value', 'content'
];

/**
 * Classify a property change
 */
function classifyPropertyChange(
  property: string,
  options: DiffOptions
): ChangeType {
  const structural = options.structuralProperties || DEFAULT_STRUCTURAL_PROPS;
  const styling = options.stylingProperties || DEFAULT_STYLING_PROPS;
  const content = options.contentProperties || DEFAULT_CONTENT_PROPS;
  
  if (structural.includes(property)) {
    return 'structural';
  }
  if (styling.includes(property)) {
    return 'styling';
  }
  if (content.includes(property)) {
    return 'content';
  }
  return 'metadata';
}

/**
 * Compare two values for equality
 */
function valuesEqual(a: unknown, b: unknown): boolean {
  if (a === b) return true;
  if (a == null || b == null) return false;
  if (typeof a !== typeof b) return false;
  
  if (typeof a === 'object') {
    return JSON.stringify(a) === JSON.stringify(b);
  }
  
  return false;
}

/**
 * Get property changes between two objects
 */
function getPropertyChanges(
  oldObj: unknown,
  newObj: unknown,
  options: DiffOptions
): PropertyChange[] {
  const changes: PropertyChange[] = [];
  const allKeys = new Set([...Object.keys(oldObj), ...Object.keys(newObj)]);
  const ignoreProps = options.ignoreProperties || [];
  
  for (const key of allKeys) {
    if (ignoreProps.includes(key)) continue;
    
    const oldValue = oldObj[key];
    const newValue = newObj[key];
    
    if (!valuesEqual(oldValue, newValue)) {
      changes.push({
        property: key,
        oldValue,
        newValue,
        changeType: classifyPropertyChange(key, options),
      });
    }
  }
  
  return changes;
}

/**
 * Determine the primary change type for an element
 */
function determineElementChangeType(properties: PropertyChange[]): ChangeType {
  // Priority: structural > content > styling > metadata
  const types = properties.map(p => p.changeType);
  
  if (types.includes('structural')) return 'structural';
  if (types.includes('content')) return 'content';
  if (types.includes('styling')) return 'styling';
  return 'metadata';
}

/**
 * Create diff between two canvas documents
 */
export function diff(
  oldDoc: CanvasDocument,
  newDoc: CanvasDocument,
  options: DiffOptions = {}
): DiffResult {
  const added: ElementChange[] = [];
  const removed: ElementChange[] = [];
  const modified: ElementChange[] = [];
  const moved: ElementChange[] = [];
  const patches: JSONPatchOperation[] = [];
  
  // Create element maps for efficient lookup
  const oldNodes = new Map((oldDoc.nodes || []).map(n => [n.id, n]));
  const newNodes = new Map((newDoc.nodes || []).map(n => [n.id, n]));
  const oldEdges = new Map((oldDoc.edges || []).map(e => [e.id, e]));
  const newEdges = new Map((newDoc.edges || []).map(e => [e.id, e]));
  
  // Process nodes
  processElements(oldNodes, newNodes, 'node', added, removed, modified, moved, patches, options);
  
  // Process edges
  processElements(oldEdges, newEdges, 'edge', added, removed, modified, moved, patches, options);
  
  // Calculate statistics
  const statistics = calculateStatistics(added, removed, modified, moved);
  
  return {
    hasChanges: added.length > 0 || removed.length > 0 || modified.length > 0 || moved.length > 0,
    added,
    removed,
    modified,
    moved,
    statistics,
    patches: options.generatePatches !== false ? patches : [],
    timestamp: Date.now(),
  };
}

/**
 * Process elements (nodes or edges) for diffing
 */
function processElements(
  oldElements: Map<string, unknown>,
  newElements: Map<string, unknown>,
  elementType: string,
  added: ElementChange[],
  removed: ElementChange[],
  modified: ElementChange[],
  moved: ElementChange[],
  patches: JSONPatchOperation[],
  options: DiffOptions
): void {
  // Find added elements
  for (const [id, newElement] of newElements) {
    if (!oldElements.has(id)) {
      const change: ElementChange = {
        elementId: id,
        elementType,
        operation: 'add',
        changeType: 'structural',
        properties: [],
        newData: newElement,
      };
      added.push(change);
      
      if (options.generatePatches !== false) {
        patches.push({
          op: 'add',
          path: `/${elementType}s/-`,
          value: newElement,
        });
      }
    }
  }
  
  // Find removed and modified elements
  for (const [id, oldElement] of oldElements) {
    if (!newElements.has(id)) {
      const change: ElementChange = {
        elementId: id,
        elementType,
        operation: 'remove',
        changeType: 'structural',
        properties: [],
        oldData: oldElement,
      };
      removed.push(change);
      
      if (options.generatePatches !== false) {
        const index = Array.from(oldElements.keys()).indexOf(id);
        patches.push({
          op: 'remove',
          path: `/${elementType}s/${index}`,
        });
      }
    } else {
      const newElement = newElements.get(id)!;
      const propertyChanges = getPropertyChanges(oldElement, newElement, options);
      
      if (propertyChanges.length > 0) {
        // Check if it's a move operation
        const isMove = options.detectMoves !== false &&
          propertyChanges.some(p => (p.property === 'x' || p.property === 'y') && 
                                    p.changeType === 'styling') &&
          propertyChanges.every(p => p.property === 'x' || p.property === 'y' || 
                                     p.changeType === 'metadata');
        
        const change: ElementChange = {
          elementId: id,
          elementType,
          operation: isMove ? 'move' : 'replace',
          changeType: determineElementChangeType(propertyChanges),
          properties: propertyChanges,
          oldData: oldElement,
          newData: newElement,
        };
        
        if (isMove) {
          moved.push(change);
        } else {
          modified.push(change);
        }
        
        if (options.generatePatches !== false) {
          const index = Array.from(oldElements.keys()).indexOf(id);
          for (const propChange of propertyChanges) {
            patches.push({
              op: 'replace',
              path: `/${elementType}s/${index}/${propChange.property}`,
              value: propChange.newValue,
            });
          }
        }
      }
    }
  }
}

/**
 * Calculate diff statistics
 */
function calculateStatistics(
  added: ElementChange[],
  removed: ElementChange[],
  modified: ElementChange[],
  moved: ElementChange[],
  filterTypes?: ChangeType[]
): DiffStatistics {
  const allChanges = [...added, ...removed, ...modified, ...moved];
  
  let structuralChanges = 0;
  let stylingChanges = 0;
  let contentChanges = 0;
  let metadataChanges = 0;
  
  for (const change of allChanges) {
    // For added/removed, count the element itself
    if (change.operation === 'add' || change.operation === 'remove') {
      if (!filterTypes || filterTypes.includes('structural')) {
        structuralChanges++;
      }
      continue;
    }
    
    // For modified/moved, count each property change
    if (change.properties) {
      for (const prop of change.properties) {
        if (!filterTypes || filterTypes.includes(prop.changeType)) {
          if (prop.changeType === 'structural') {
            structuralChanges++;
          } else if (prop.changeType === 'styling') {
            stylingChanges++;
          } else if (prop.changeType === 'content') {
            contentChanges++;
          } else if (prop.changeType === 'metadata') {
            metadataChanges++;
          }
        }
      }
    }
  }
  
  return {
    totalChanges: allChanges.length,
    structuralChanges,
    stylingChanges,
    contentChanges,
    metadataChanges,
    elementsAdded: added.length,
    elementsRemoved: removed.length,
    elementsModified: modified.length,
    elementsMoved: moved.length,
  };
}

/**
 * Apply JSON Patch operations to a document
 */
export function applyPatch(
  doc: CanvasDocument,
  patches: JSONPatchOperation[]
): CanvasDocument {
  let result = JSON.parse(JSON.stringify(doc)); // Deep clone
  
  for (const patch of patches) {
    result = applySinglePatch(result, patch);
  }
  
  return result;
}

/**
 * Apply a single JSON Patch operation
 */
function applySinglePatch(
  doc: unknown,
  patch: JSONPatchOperation
): unknown {
  const pathParts = patch.path.split('/').filter(p => p.length > 0);
  
  if (pathParts.length === 0) {
    throw new Error('Invalid patch path');
  }
  
  switch (patch.op) {
    case 'add':
      return applyAdd(doc, pathParts, patch.value);
    case 'remove':
      return applyRemove(doc, pathParts);
    case 'replace':
      return applyReplace(doc, pathParts, patch.value);
    default:
      throw new Error(`Unsupported operation: ${patch.op}`);
  }
}

/**
 * Apply add operation
 */
function applyAdd(doc: unknown, pathParts: string[], value: unknown): unknown {
  const result = { ...doc };
  let current = result;
  
  for (let i = 0; i < pathParts.length - 1; i++) {
    const part = pathParts[i];
    current[part] = Array.isArray(current[part]) ? [...current[part]] : { ...current[part] };
    current = current[part];
  }
  
  const lastPart = pathParts[pathParts.length - 1];
  if (lastPart === '-' && Array.isArray(current)) {
    current.push(value);
  } else if (Array.isArray(current)) {
    const index = parseInt(lastPart, 10);
    current.splice(index, 0, value);
  } else {
    current[lastPart] = value;
  }
  
  return result;
}

/**
 * Apply remove operation
 */
function applyRemove(doc: unknown, pathParts: string[]): unknown {
  const result = { ...doc };
  let current = result;
  
  for (let i = 0; i < pathParts.length - 1; i++) {
    const part = pathParts[i];
    current[part] = Array.isArray(current[part]) ? [...current[part]] : { ...current[part] };
    current = current[part];
  }
  
  const lastPart = pathParts[pathParts.length - 1];
  if (Array.isArray(current)) {
    const index = parseInt(lastPart, 10);
    current.splice(index, 1);
  } else {
    delete current[lastPart];
  }
  
  return result;
}

/**
 * Apply replace operation
 */
function applyReplace(doc: unknown, pathParts: string[], value: unknown): unknown {
  const result = { ...doc };
  let current = result;
  
  for (let i = 0; i < pathParts.length - 1; i++) {
    const part = pathParts[i];
    current[part] = Array.isArray(current[part]) ? [...current[part]] : { ...current[part] };
    current = current[part];
  }
  
  const lastPart = pathParts[pathParts.length - 1];
  current[lastPart] = value;
  
  return result;
}

/**
 * Generate diff summary text
 */
export function generateDiffSummary(diff: DiffResult): string {
  const lines: string[] = [];
  
  if (!diff.hasChanges) {
    return 'No changes detected';
  }
  
  lines.push('Changes Summary:');
  lines.push(`  Total: ${diff.statistics.totalChanges} changes`);
  
  if (diff.statistics.elementsAdded > 0) {
    lines.push(`  Added: ${diff.statistics.elementsAdded} elements`);
  }
  if (diff.statistics.elementsRemoved > 0) {
    lines.push(`  Removed: ${diff.statistics.elementsRemoved} elements`);
  }
  if (diff.statistics.elementsModified > 0) {
    lines.push(`  Modified: ${diff.statistics.elementsModified} elements`);
  }
  if (diff.statistics.elementsMoved > 0) {
    lines.push(`  Moved: ${diff.statistics.elementsMoved} elements`);
  }
  
  lines.push('');
  lines.push('Change Types:');
  lines.push(`  Structural: ${diff.statistics.structuralChanges}`);
  lines.push(`  Content: ${diff.statistics.contentChanges}`);
  lines.push(`  Styling: ${diff.statistics.stylingChanges}`);
  lines.push(`  Metadata: ${diff.statistics.metadataChanges}`);
  
  return lines.join('\n');
}

/**
 * Export patches as JSON
 */
export function exportPatchesJSON(patches: JSONPatchOperation[]): string {
  return JSON.stringify(patches, null, 2);
}

/**
 * Import patches from JSON
 */
export function importPatchesJSON(json: string): JSONPatchOperation[] {
  try {
    const parsed = JSON.parse(json);
    if (!Array.isArray(parsed)) {
      throw new Error('Patches must be an array');
    }
    return parsed as JSONPatchOperation[];
  } catch (error) {
    throw new Error(`Failed to parse patches JSON: ${(error as Error).message}`);
  }
}

/**
 * Merge multiple diffs
 */
export function mergeDiffs(diffs: DiffResult[]): DiffResult {
  const allAdded: ElementChange[] = [];
  const allRemoved: ElementChange[] = [];
  const allModified: ElementChange[] = [];
  const allMoved: ElementChange[] = [];
  const allPatches: JSONPatchOperation[] = [];
  
  for (const diff of diffs) {
    allAdded.push(...diff.added);
    allRemoved.push(...diff.removed);
    allModified.push(...diff.modified);
    allMoved.push(...diff.moved);
    allPatches.push(...diff.patches);
  }
  
  const statistics = calculateStatistics(allAdded, allRemoved, allModified, allMoved);
  
  return {
    hasChanges: statistics.totalChanges > 0,
    added: allAdded,
    removed: allRemoved,
    modified: allModified,
    moved: allMoved,
    statistics,
    patches: allPatches,
    timestamp: Date.now(),
  };
}

/**
 * Filter diff by change type
 */
export function filterDiffByType(
  diff: DiffResult,
  changeTypes: ChangeType[]
): DiffResult {
  const filterChanges = (changes: ElementChange[]) =>
    changes.filter(c => {
      // Check if element's primary change type matches
      if (changeTypes.includes(c.changeType)) return true;
      // Also check if any property change types match
      return c.properties && c.properties.some(p => changeTypes.includes(p.changeType));
    });
  
  const filtered = {
    ...diff,
    added: filterChanges(diff.added),
    removed: filterChanges(diff.removed),
    modified: filterChanges(diff.modified),
    moved: filterChanges(diff.moved),
  };
  
  filtered.statistics = calculateStatistics(
    filtered.added,
    filtered.removed,
    filtered.modified,
    filtered.moved,
    changeTypes
  );
  
  filtered.hasChanges = filtered.statistics.totalChanges > 0;
  
  return filtered;
}
