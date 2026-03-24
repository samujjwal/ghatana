/**
 * ID Strategy - Deterministic ID Generation and Management
 * 
 * Provides stable, deterministic ID generation for canvas elements based on:
 * - Content-based hashing (type + data)
 * - Custom ID schemes (sequential, UUID, prefixed)
 * - ID normalization and validation
 * - Collision detection and resolution
 * 
 * Benefits:
 * - Reproducible IDs across environments
 * - Stable references for version control
 * - Efficient diffing and merging
 * - Collision-free ID generation
 * 
 * @module idStrategy
 */

/**
 * ID generation strategy types
 */
export type IDStrategy = 
  | 'content-hash'    // Hash from type + data (deterministic)
  | 'uuid'            // Random UUID v4
  | 'sequential'      // Sequential numbers with prefix
  | 'timestamp'       // Timestamp-based with random suffix
  | 'custom';         // User-provided custom generator

/**
 * ID generation options
 */
export interface IDOptions {
  strategy: IDStrategy;
  prefix?: string;           // Optional prefix (e.g., "node_", "edge_")
  namespace?: string;        // Optional namespace for content hashing
  counter?: number;          // Starting counter for sequential strategy
  customGenerator?: () => string; // Custom ID generator function
}

/**
 * ID validation result
 */
export interface IDValidation {
  valid: boolean;
  id: string;
  errors: string[];
  normalized?: string;       // Normalized version if valid
}

/**
 * ID normalization options
 */
export interface NormalizationOptions {
  lowercase?: boolean;       // Convert to lowercase
  removeSpaces?: boolean;    // Remove whitespace
  replaceSpecialChars?: boolean; // Replace special characters
  maxLength?: number;        // Maximum length
}

/**
 * ID collision result
 */
export interface CollisionResult {
  hasCollision: boolean;
  existingId?: string;
  suggestedId?: string;      // Alternative ID if collision detected
}

/**
 * State for ID generation tracking
 */
export interface IDGeneratorState {
  strategy: IDStrategy;
  prefix: string;
  namespace: string;
  counter: number;
  generatedIds: Set<string>;
  collisionCount: number;
}

/**
 * Simple hash function for content-based ID generation
 * Uses FNV-1a algorithm (fast, good distribution)
 */
function hashString(str: string): string {
  let hash = 2166136261; // FNV offset basis
  for (let i = 0; i < str.length; i++) {
    hash ^= str.charCodeAt(i);
    hash += (hash << 1) + (hash << 4) + (hash << 7) + (hash << 8) + (hash << 24);
  }
  // Convert to positive 32-bit integer and then to hex
  const positive = (hash >>> 0);
  return positive.toString(16).padStart(8, '0');
}

/**
 * Generate a UUID v4
 */
function generateUUID(): string {
  // Using crypto.randomUUID if available, otherwise fallback
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  
  // Fallback UUID generation
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = Math.random() * 16 | 0;
    const v = c === 'x' ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
}

/**
 * Create initial ID generator state
 */
export function createIDGeneratorState(options: Partial<IDOptions> = {}): IDGeneratorState {
  return {
    strategy: options.strategy || 'content-hash',
    prefix: options.prefix || '',
    namespace: options.namespace || 'canvas',
    counter: options.counter || 1,
    generatedIds: new Set<string>(),
    collisionCount: 0,
  };
}

/**
 * Generate deterministic ID from content
 * 
 * @example
 * ```ts
 * const id1 = generateContentHash({ type: 'rect', data: { x: 0, y: 0 } });
 * const id2 = generateContentHash({ type: 'rect', data: { x: 0, y: 0 } });
 * // id1 === id2 (deterministic)
 * ```
 */
export function generateContentHash(
  content: Record<string, unknown>,
  options: { prefix?: string; namespace?: string } = {}
): string {
  const prefix = options.prefix || '';
  const namespace = options.namespace || 'canvas';
  
  // Create canonical JSON representation (sorted keys)
  const canonical = JSON.stringify(content, Object.keys(content).sort());
  const input = `${namespace}:${canonical}`;
  
  const hash = hashString(input);
  return prefix ? `${prefix}${hash}` : hash;
}

/**
 * Generate ID using specified strategy
 */
export function generateID(
  state: IDGeneratorState,
  content?: Record<string, unknown>
): { id: string; state: IDGeneratorState } {
  let id: string;
  
  switch (state.strategy) {
    case 'content-hash':
      if (!content) {
        throw new Error('Content is required for content-hash strategy');
      }
      id = generateContentHash(content, {
        prefix: state.prefix,
        namespace: state.namespace,
      });
      break;
      
    case 'uuid':
      id = state.prefix + generateUUID();
      break;
      
    case 'sequential':
      id = `${state.prefix}${state.counter}`;
      return {
        id,
        state: {
          ...state,
          counter: state.counter + 1,
          generatedIds: new Set([...state.generatedIds, id]),
        },
      };
      
    case 'timestamp':
      const timestamp = Date.now().toString(36);
      const random = Math.random().toString(36).substring(2, 6);
      id = `${state.prefix}${timestamp}${random}`;
      break;
      
    case 'custom':
      throw new Error('Custom strategy requires customGenerator function');
      
    default:
      throw new Error(`Unknown ID strategy: ${state.strategy}`);
  }
  
  return {
    id,
    state: {
      ...state,
      generatedIds: new Set([...state.generatedIds, id]),
    },
  };
}

/**
 * Check for ID collision
 */
export function checkCollision(
  id: string,
  state: IDGeneratorState
): CollisionResult {
  const hasCollision = state.generatedIds.has(id);
  
  if (!hasCollision) {
    return { hasCollision: false };
  }
  
  // Generate alternative ID with suffix
  let counter = 1;
  let suggestedId = `${id}_${counter}`;
  while (state.generatedIds.has(suggestedId)) {
    counter++;
    suggestedId = `${id}_${counter}`;
  }
  
  return {
    hasCollision: true,
    existingId: id,
    suggestedId,
  };
}

/**
 * Register an existing ID (marks it as used)
 */
export function registerID(
  id: string,
  state: IDGeneratorState
): IDGeneratorState {
  return {
    ...state,
    generatedIds: new Set([...state.generatedIds, id]),
  };
}

/**
 * Validate ID format and return normalized version
 */
export function validateID(
  id: string,
  options: NormalizationOptions = {}
): IDValidation {
  const errors: string[] = [];
  
  // Check if ID is empty
  if (!id || id.trim().length === 0) {
    errors.push('ID cannot be empty');
    return { valid: false, id, errors };
  }
  
  // Check for invalid characters
  const invalidChars = /[<>:"/\\|?*\x00-\x1F]/;
  if (invalidChars.test(id)) {
    errors.push('ID contains invalid characters (<>:"/\\|?* or control characters)');
  }
  
  // Check length
  if (options.maxLength && id.length > options.maxLength) {
    errors.push(`ID exceeds maximum length of ${options.maxLength} characters`);
  }
  
  // Normalize ID if valid
  let normalized = id;
  if (errors.length === 0) {
    if (options.lowercase) {
      normalized = normalized.toLowerCase();
    }
    if (options.removeSpaces) {
      normalized = normalized.replace(/\s+/g, '');
    }
    if (options.replaceSpecialChars) {
      normalized = normalized.replace(/[^a-zA-Z0-9_-]/g, '_');
    }
    if (options.maxLength) {
      normalized = normalized.substring(0, options.maxLength);
    }
  }
  
  return {
    valid: errors.length === 0,
    id,
    errors,
    normalized: errors.length === 0 ? normalized : undefined,
  };
}

/**
 * Normalize ID according to options
 */
export function normalizeID(
  id: string,
  options: NormalizationOptions = {}
): string {
  // Apply transformations first, then validate
  let normalized = id;
  
  if (options.lowercase) {
    normalized = normalized.toLowerCase();
  }
  if (options.removeSpaces) {
    normalized = normalized.replace(/\s+/g, '');
  }
  if (options.replaceSpecialChars) {
    normalized = normalized.replace(/[^a-zA-Z0-9_-]/g, '_');
  }
  if (options.maxLength) {
    normalized = normalized.substring(0, options.maxLength);
  }
  
  // Now validate the normalized result
  const validation = validateID(normalized, { ...options, maxLength: undefined });
  if (!validation.valid) {
    throw new Error(`Invalid ID after normalization: ${validation.errors.join(', ')}`);
  }
  
  return normalized;
}

/**
 * Batch generate IDs for multiple items
 */
export function batchGenerateIDs(
  items: Array<Record<string, unknown>>,
  state: IDGeneratorState
): { ids: string[]; state: IDGeneratorState } {
  const ids: string[] = [];
  let currentState = state;
  
  for (const item of items) {
    const result = generateID(currentState, item);
    ids.push(result.id);
    currentState = result.state;
  }
  
  return { ids, state: currentState };
}

/**
 * Get ID statistics from generator state
 */
export function getIDStatistics(state: IDGeneratorState): {
  totalGenerated: number;
  collisionCount: number;
  strategy: IDStrategy;
  nextCounter: number;
} {
  return {
    totalGenerated: state.generatedIds.size,
    collisionCount: state.collisionCount,
    strategy: state.strategy,
    nextCounter: state.counter,
  };
}

/**
 * Reset ID generator state
 */
export function resetIDGenerator(
  state: IDGeneratorState,
  options: Partial<IDOptions> = {}
): IDGeneratorState {
  return createIDGeneratorState({
    strategy: options.strategy || state.strategy,
    prefix: options.prefix !== undefined ? options.prefix : state.prefix,
    namespace: options.namespace !== undefined ? options.namespace : state.namespace,
    counter: options.counter !== undefined ? options.counter : 1,
  });
}

/**
 * Create ID remapping for migrating between ID strategies
 */
export function createIDRemapping(
  oldIds: string[],
  newState: IDGeneratorState,
  contents?: Array<Record<string, unknown>>
): { mapping: Map<string, string>; state: IDGeneratorState } {
  const mapping = new Map<string, string>();
  let currentState = newState;
  
  for (let i = 0; i < oldIds.length; i++) {
    const oldId = oldIds[i];
    const content = contents?.[i];
    
    const result = generateID(currentState, content);
    mapping.set(oldId, result.id);
    currentState = result.state;
  }
  
  return { mapping, state: currentState };
}

/**
 * Apply ID remapping to an object
 */
export function applyIDRemapping<T extends Record<string, unknown>>(
  obj: T,
  mapping: Map<string, string>,
  idFields: string[] = ['id']
): T {
  const result = { ...obj } as unknown;
  
  for (const field of idFields) {
    if (field in result && typeof result[field] === 'string') {
      const oldId = result[field];
      const newId = mapping.get(oldId);
      if (newId) {
        result[field] = newId;
      }
    }
  }
  
  return result as T;
}
