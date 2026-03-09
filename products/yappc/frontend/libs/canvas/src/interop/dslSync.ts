/**
 * Feature 2.14: Diagram-as-Code
 * 
 * Text-based synchronization for canvas diagrams with CI/CD integration.
 * Enables version control, automated updates, and inline editing with error detection.
 * 
 * @module dslSync
 */

import type { CanvasDocument } from './formatAdapters';

/**
 * DSL format types
 */
export type DSLFormat =
  | 'mermaid'
  | 'plantuml'
  | 'graphviz'
  | 'c4'
  | 'custom';

/**
 * Sync direction
 */
export type SyncDirection = 'dsl-to-canvas' | 'canvas-to-dsl' | 'bidirectional';

/**
 * Sync status
 */
export type SyncStatus = 
  | 'synced'
  | 'out-of-sync'
  | 'conflict'
  | 'error'
  | 'pending';

/**
 * Line-level error information
 */
export interface DSLError {
  /** Error message */
  message: string;
  /** Line number (1-indexed) */
  line: number;
  /** Column number (1-indexed) */
  column?: number;
  /** Error severity */
  severity: 'error' | 'warning' | 'info';
  /** Suggested fix */
  suggestion?: string;
  /** Error code for categorization */
  code?: string;
}

/**
 * DSL validation result
 */
export interface DSLValidationResult {
  /** Whether DSL is valid */
  valid: boolean;
  /** Parse errors */
  errors: DSLError[];
  /** Warnings */
  warnings: DSLError[];
  /** Info messages */
  info: DSLError[];
  /** Syntax tree (if valid) */
  ast?: unknown;
}

/**
 * Sync conflict information
 */
export interface SyncConflict {
  /** Conflict ID */
  id: string;
  /** Element ID in canvas */
  elementId: string;
  /** Element type */
  elementType: 'node' | 'edge' | 'metadata';
  /** Conflicting property */
  property: string;
  /** Value in canvas */
  canvasValue: unknown;
  /** Value in DSL */
  dslValue: unknown;
  /** Conflict resolution strategy */
  resolution?: 'prefer-canvas' | 'prefer-dsl' | 'merge' | 'manual';
}

/**
 * Sync configuration
 */
export interface DSLSyncConfig {
  /** DSL format */
  format: DSLFormat;
  /** Sync direction */
  direction: SyncDirection;
  /** File path for DSL source */
  filePath: string;
  /** Enable auto-sync on file change */
  autoSync: boolean;
  /** Sync interval in milliseconds (0 = manual only) */
  syncInterval: number;
  /** Enable conflict detection */
  detectConflicts: boolean;
  /** Default conflict resolution */
  defaultResolution: 'prefer-canvas' | 'prefer-dsl' | 'manual';
  /** Enable validation before sync */
  validateBeforeSync: boolean;
  /** Watch for external file changes */
  watchFile: boolean;
}

/**
 * Sync result
 */
export interface DSLSyncResult {
  /** Sync status */
  status: SyncStatus;
  /** Synced document (if successful) */
  document?: CanvasDocument;
  /** Errors encountered */
  errors: DSLError[];
  /** Detected conflicts */
  conflicts: SyncConflict[];
  /** Sync timestamp */
  timestamp: Date;
  /** Changes applied */
  changes: {
    nodesAdded: number;
    nodesUpdated: number;
    nodesDeleted: number;
    edgesAdded: number;
    edgesUpdated: number;
    edgesDeleted: number;
  };
}

/**
 * CI/CD trigger configuration
 */
export interface CITriggerConfig {
  /** Enable CI triggers */
  enabled: boolean;
  /** Trigger on commit */
  onCommit: boolean;
  /** Trigger on PR */
  onPullRequest: boolean;
  /** Trigger on tag */
  onTag: boolean;
  /** Branch filters (e.g., ['main', 'develop']) */
  branches: string[];
  /** Webhook URL for notifications */
  webhookUrl?: string;
  /** Custom command to run */
  command?: string;
}

/**
 * DSL sync state
 */
export interface DSLSyncState {
  /** Sync configuration */
  config: DSLSyncConfig;
  /** Current DSL content */
  dslContent: string;
  /** Current canvas document */
  canvasDocument: CanvasDocument;
  /** Last sync result */
  lastSync?: DSLSyncResult;
  /** Current sync status */
  status: SyncStatus;
  /** Pending conflicts */
  conflicts: SyncConflict[];
  /** File watcher active */
  isWatching: boolean;
  /** CI trigger config */
  ciConfig?: CITriggerConfig;
}

/**
 * Creates default DSL sync configuration
 */
export function createDSLSyncConfig(
  overrides?: Partial<DSLSyncConfig>
): DSLSyncConfig {
  return {
    format: 'mermaid',
    direction: 'bidirectional',
    filePath: '',
    autoSync: false,
    syncInterval: 0,
    detectConflicts: true,
    defaultResolution: 'manual',
    validateBeforeSync: true,
    watchFile: false,
    ...overrides,
  };
}

/**
 * Creates initial DSL sync state
 */
export function createDSLSyncState(
  config: Partial<DSLSyncConfig>,
  initialDocument: CanvasDocument = { nodes: [], edges: [] }
): DSLSyncState {
  return {
    config: createDSLSyncConfig(config),
    dslContent: '',
    canvasDocument: initialDocument,
    status: 'pending',
    conflicts: [],
    isWatching: false,
  };
}

/**
 * Validates DSL content
 */
export function validateDSL(
  dslContent: string,
  format: DSLFormat
): DSLValidationResult {
  const errors: DSLError[] = [];
  const warnings: DSLError[] = [];
  const info: DSLError[] = [];
  
  // Basic validation (can be extended with actual parsers)
  if (!dslContent.trim()) {
    errors.push({
      message: 'DSL content is empty',
      line: 1,
      column: 1,
      severity: 'error',
      code: 'EMPTY_DSL',
    });
    return { valid: false, errors, warnings, info };
  }
  
  // Format-specific validation
  switch (format) {
    case 'mermaid':
      return validateMermaid(dslContent);
    case 'plantuml':
      return validatePlantUML(dslContent);
    case 'graphviz':
      return validateGraphviz(dslContent);
    case 'c4':
      return validateC4(dslContent);
    default:
      return { valid: true, errors: [], warnings: [], info: [] };
  }
}

/**
 * Validates Mermaid syntax
 */
function validateMermaid(content: string): DSLValidationResult {
  const errors: DSLError[] = [];
  const warnings: DSLError[] = [];
  const info: DSLError[] = [];
  
  const lines = content.split('\n');
  
  lines.forEach((line, index) => {
    const lineNum = index + 1;
    const trimmed = line.trim();
    
    // Check for diagram type declaration
    if (lineNum === 1) {
      if (!trimmed.match(/^(flowchart|graph|sequenceDiagram|classDiagram)/)) {
        warnings.push({
          message: 'Missing or invalid diagram type declaration',
          line: lineNum,
          severity: 'warning',
          suggestion: 'Start with: flowchart TB',
          code: 'MISSING_DIAGRAM_TYPE',
        });
      }
    }
    
    // Check for unbalanced brackets
    const openBrackets = (line.match(/[\[\(\{]/g) || []).length;
    const closeBrackets = (line.match(/[\]\)\}]/g) || []).length;
    if (openBrackets !== closeBrackets) {
      errors.push({
        message: 'Unbalanced brackets',
        line: lineNum,
        severity: 'error',
        code: 'UNBALANCED_BRACKETS',
      });
    }
    
    // Check for invalid arrow syntax
    if (trimmed.includes('->') && !trimmed.match(/\w+\s*-->?\s*\w+/)) {
      errors.push({
        message: 'Invalid arrow syntax',
        line: lineNum,
        severity: 'error',
        suggestion: 'Use format: nodeA --> nodeB',
        code: 'INVALID_ARROW',
      });
    }
  });
  
  return {
    valid: errors.length === 0,
    errors,
    warnings,
    info,
  };
}

/**
 * Validates PlantUML syntax
 */
function validatePlantUML(content: string): DSLValidationResult {
  const errors: DSLError[] = [];
  const warnings: DSLError[] = [];
  
  if (!content.includes('@startuml')) {
    errors.push({
      message: 'Missing @startuml declaration',
      line: 1,
      severity: 'error',
      code: 'MISSING_START',
    });
  }
  
  if (!content.includes('@enduml')) {
    errors.push({
      message: 'Missing @enduml declaration',
      line: content.split('\n').length,
      severity: 'error',
      code: 'MISSING_END',
    });
  }
  
  return { valid: errors.length === 0, errors, warnings, info: [] };
}

/**
 * Validates Graphviz DOT syntax
 */
function validateGraphviz(content: string): DSLValidationResult {
  const errors: DSLError[] = [];
  const warnings: DSLError[] = [];
  
  if (!content.match(/^(di)?graph\s+\w+/)) {
    errors.push({
      message: 'Missing graph declaration',
      line: 1,
      severity: 'error',
      code: 'MISSING_GRAPH',
      suggestion: 'Start with: digraph G {',
    });
  }
  
  const openBraces = (content.match(/\{/g) || []).length;
  const closeBraces = (content.match(/\}/g) || []).length;
  if (openBraces !== closeBraces) {
    errors.push({
      message: 'Unmatched braces',
      line: content.split('\n').length,
      severity: 'error',
      code: 'UNMATCHED_BRACES',
    });
  }
  
  return { valid: errors.length === 0, errors, warnings, info: [] };
}

/**
 * Validates C4 DSL syntax
 */
function validateC4(content: string): DSLValidationResult {
  const errors: DSLError[] = [];
  const warnings: DSLError[] = [];
  
  const lines = content.split('\n');
  let hasWorkspace = false;
  
  lines.forEach((line, index) => {
    const trimmed = line.trim();
    if (trimmed.startsWith('workspace ')) {
      hasWorkspace = true;
    }
  });
  
  if (!hasWorkspace) {
    warnings.push({
      message: 'C4 workspace not declared',
      line: 1,
      severity: 'warning',
      code: 'MISSING_WORKSPACE',
    });
  }
  
  return { valid: errors.length === 0, errors, warnings, info: [] };
}

/**
 * Parses DSL content to canvas document
 */
export function parseDSLToCanvas(
  dslContent: string,
  format: DSLFormat
): CanvasDocument {
  // This would use actual parsers in production
  // For now, return a basic structure
  return {
    nodes: [],
    edges: [],
    metadata: {
      title: `Imported from ${format}`,
    },
  };
}

/**
 * Converts canvas document to DSL
 */
export function canvasToDSL(
  document: CanvasDocument,
  format: DSLFormat
): string {
  // This would use actual serializers in production
  switch (format) {
    case 'mermaid':
      return canvasToMermaid(document);
    case 'plantuml':
      return canvasToPlantUML(document);
    case 'graphviz':
      return canvasToGraphviz(document);
    case 'c4':
      return canvasToC4(document);
    default:
      return '';
  }
}

/**
 * Converts canvas to Mermaid
 */
function canvasToMermaid(document: CanvasDocument): string {
  let mermaid = 'flowchart TB\n';
  
  // Add nodes
  document.nodes.forEach((node) => {
    const shape = getNodeShape(node.type);
    mermaid += `  ${node.id}${shape}${node.label}${shape.replace('[', ']').replace('(', ')')}\n`;
  });
  
  // Add edges
  document.edges.forEach((edge) => {
    const arrow = edge.type === 'dashed' ? '-.->' : '-->';
    const label = edge.label ? `|${edge.label}|` : '';
    mermaid += `  ${edge.source} ${arrow}${label} ${edge.target}\n`;
  });
  
  return mermaid;
}

/**
 * Converts canvas to PlantUML
 */
function canvasToPlantUML(document: CanvasDocument): string {
  let puml = '@startuml\n';
  
  document.nodes.forEach((node) => {
    puml += `rectangle "${node.label}" as ${node.id}\n`;
  });
  
  document.edges.forEach((edge) => {
    const arrow = edge.type === 'dashed' ? '..>' : '-->';
    const label = edge.label ? ` : ${edge.label}` : '';
    puml += `${edge.source} ${arrow} ${edge.target}${label}\n`;
  });
  
  puml += '@enduml\n';
  return puml;
}

/**
 * Converts canvas to Graphviz
 */
function canvasToGraphviz(document: CanvasDocument): string {
  let dot = 'digraph G {\n';
  
  document.nodes.forEach((node) => {
    const shape = node.type === 'ellipse' ? 'ellipse' : 'box';
    dot += `  ${node.id} [label="${node.label}", shape=${shape}];\n`;
  });
  
  document.edges.forEach((edge) => {
    const style = edge.type === 'dashed' ? ', style=dashed' : '';
    const label = edge.label ? `, label="${edge.label}"` : '';
    dot += `  ${edge.source} -> ${edge.target}${style}${label};\n`;
  });
  
  dot += '}\n';
  return dot;
}

/**
 * Converts canvas to C4 DSL
 */
function canvasToC4(document: CanvasDocument): string {
  let c4 = 'workspace {\n';
  c4 += '  model {\n';
  
  document.nodes.forEach((node) => {
    c4 += `    ${node.id} = person "${node.label}"\n`;
  });
  
  document.edges.forEach((edge) => {
    const label = edge.label || 'uses';
    c4 += `    ${edge.source} -> ${edge.target} "${label}"\n`;
  });
  
  c4 += '  }\n';
  c4 += '}\n';
  return c4;
}

/**
 * Gets Mermaid node shape for canvas node type
 */
function getNodeShape(type: string): string {
  switch (type) {
    case 'rectangle':
      return '[';
    case 'ellipse':
      return '(';
    case 'diamond':
      return '{';
    case 'hexagon':
      return '{{';
    default:
      return '[';
  }
}

/**
 * Synchronizes DSL content with canvas
 */
export function syncDSL(state: DSLSyncState): DSLSyncResult {
  const errors: DSLError[] = [];
  const conflicts: SyncConflict[] = [];
  const timestamp = new Date();
  
  // Validate if enabled
  if (state.config.validateBeforeSync) {
    const validation = validateDSL(state.dslContent, state.config.format);
    if (!validation.valid) {
      return {
        status: 'error',
        errors: validation.errors,
        conflicts: [],
        timestamp,
        changes: {
          nodesAdded: 0,
          nodesUpdated: 0,
          nodesDeleted: 0,
          edgesAdded: 0,
          edgesUpdated: 0,
          edgesDeleted: 0,
        },
      };
    }
    errors.push(...validation.warnings);
  }
  
  // Parse DSL to canvas
  const parsedDocument = parseDSLToCanvas(
    state.dslContent,
    state.config.format
  );
  
  // Detect conflicts if enabled
  if (state.config.detectConflicts) {
    conflicts.push(...detectConflicts(state.canvasDocument, parsedDocument));
  }
  
  // Calculate changes
  const changes = calculateChanges(state.canvasDocument, parsedDocument);
  
  // Determine status
  let status: SyncStatus = 'synced';
  if (errors.some((e) => e.severity === 'error')) {
    status = 'error';
  } else if (conflicts.length > 0) {
    status = 'conflict';
  } else if (changes.nodesAdded + changes.edgesAdded + changes.nodesUpdated + changes.edgesUpdated > 0) {
    status = 'out-of-sync';
  }
  
  return {
    status,
    document: parsedDocument,
    errors,
    conflicts,
    timestamp,
    changes,
  };
}

/**
 * Detects conflicts between canvas and DSL
 */
function detectConflicts(
  canvas: CanvasDocument,
  dsl: CanvasDocument
): SyncConflict[] {
  const conflicts: SyncConflict[] = [];
  
  // Check for node conflicts
  canvas.nodes.forEach((canvasNode) => {
    const dslNode = dsl.nodes.find((n) => n.id === canvasNode.id);
    if (dslNode) {
      if (canvasNode.label !== dslNode.label) {
        conflicts.push({
          id: `conflict-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
          elementId: canvasNode.id,
          elementType: 'node',
          property: 'label',
          canvasValue: canvasNode.label,
          dslValue: dslNode.label,
        });
      }
    }
  });
  
  return conflicts;
}

/**
 * Calculates changes between two documents
 */
function calculateChanges(
  oldDoc: CanvasDocument,
  newDoc: CanvasDocument
): DSLSyncResult['changes'] {
  const changes = {
    nodesAdded: 0,
    nodesUpdated: 0,
    nodesDeleted: 0,
    edgesAdded: 0,
    edgesUpdated: 0,
    edgesDeleted: 0,
  };
  
  // Count node changes
  newDoc.nodes.forEach((newNode) => {
    const oldNode = oldDoc.nodes.find((n) => n.id === newNode.id);
    if (!oldNode) {
      changes.nodesAdded++;
    } else if (JSON.stringify(oldNode) !== JSON.stringify(newNode)) {
      changes.nodesUpdated++;
    }
  });
  
  changes.nodesDeleted = oldDoc.nodes.length - newDoc.nodes.filter((n) =>
    oldDoc.nodes.some((o) => o.id === n.id)
  ).length;
  
  // Count edge changes
  newDoc.edges.forEach((newEdge) => {
    const oldEdge = oldDoc.edges.find((e) => e.id === newEdge.id);
    if (!oldEdge) {
      changes.edgesAdded++;
    } else if (JSON.stringify(oldEdge) !== JSON.stringify(newEdge)) {
      changes.edgesUpdated++;
    }
  });
  
  changes.edgesDeleted = oldDoc.edges.length - newDoc.edges.filter((e) =>
    oldDoc.edges.some((o) => o.id === e.id)
  ).length;
  
  return changes;
}

/**
 * Resolves a conflict
 */
export function resolveConflict(
  state: DSLSyncState,
  conflictId: string,
  resolution: 'prefer-canvas' | 'prefer-dsl' | 'merge'
): DSLSyncState {
  const conflict = state.conflicts.find((c) => c.id === conflictId);
  if (!conflict) {
    return state;
  }
  
  const updatedConflicts = state.conflicts.filter((c) => c.id !== conflictId);
  
  return {
    ...state,
    conflicts: updatedConflicts,
  };
}

/**
 * Starts file watching
 */
export function startWatching(state: DSLSyncState): DSLSyncState {
  return {
    ...state,
    isWatching: true,
  };
}

/**
 * Stops file watching
 */
export function stopWatching(state: DSLSyncState): DSLSyncState {
  return {
    ...state,
    isWatching: false,
  };
}

/**
 * Triggers CI pipeline
 */
export function triggerCIPipeline(
  state: DSLSyncState,
  event: 'commit' | 'pull-request' | 'tag'
): boolean {
  if (!state.ciConfig?.enabled) {
    return false;
  }
  
  switch (event) {
    case 'commit':
      return state.ciConfig.onCommit;
    case 'pull-request':
      return state.ciConfig.onPullRequest;
    case 'tag':
      return state.ciConfig.onTag;
    default:
      return false;
  }
}

/**
 * Generates CI configuration file
 */
export function generateCIConfig(
  platform: 'github-actions' | 'gitlab-ci' | 'jenkins' | 'circleci'
): string {
  switch (platform) {
    case 'github-actions':
      return `name: Diagram Sync
on:
  push:
    paths:
      - '**/*.mmd'
      - '**/*.puml'
  pull_request:
    paths:
      - '**/*.mmd'
      - '**/*.puml'

jobs:
  sync:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Validate Diagrams
        run: npm run validate-diagrams
      - name: Sync to Canvas
        run: npm run sync-to-canvas
`;
    
    case 'gitlab-ci':
      return `diagram-sync:
  stage: build
  script:
    - npm run validate-diagrams
    - npm run sync-to-canvas
  only:
    changes:
      - "**/*.mmd"
      - "**/*.puml"
`;
    
    case 'jenkins':
      return `pipeline {
  agent any
  triggers {
    pollSCM('H/5 * * * *')
  }
  stages {
    stage('Validate') {
      steps {
        sh 'npm run validate-diagrams'
      }
    }
    stage('Sync') {
      steps {
        sh 'npm run sync-to-canvas'
      }
    }
  }
}
`;
    
    case 'circleci':
      return `version: 2.1
jobs:
  diagram-sync:
    docker:
      - image: node:18
    steps:
      - checkout
      - run: npm run validate-diagrams
      - run: npm run sync-to-canvas

workflows:
  version: 2
  sync-on-change:
    jobs:
      - diagram-sync:
          filters:
            branches:
              only:
                - main
                - develop
`;
    
    default:
      return '';
  }
}
