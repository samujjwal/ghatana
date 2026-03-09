/**
 * Refinement Engine - Applies natural language intents to simulation manifests.
 *
 * @doc.type class
 * @doc.purpose Apply parsed intents to modify simulation manifests
 * @doc.layer product
 * @doc.pattern Engine
 */

import { randomUUID } from 'crypto';
import type {
  SimulationManifest,
  SimEntityBase,
  SimulationStep,
} from '@ghatana/tutorputor-contracts/v1/simulation/types';
import "./type-augmentations";
import type { ParsedIntent, IntentParams } from './intent-parser';

/**
 * Refinement result.
 */
export interface RefinementResult {
  /** Whether the refinement was successful */
  success: boolean;
  /** The modified manifest (if successful) */
  manifest?: SimulationManifest;
  /** Error message (if failed) */
  error?: string;
  /** Description of changes made */
  changes: string[];
  /** Suggestions for further refinement */
  suggestions: string[];
}

/**
 * Undo/Redo history entry.
 */
interface HistoryEntry {
  /** The manifest before the change */
  manifest: SimulationManifest;
  /** Description of the change */
  description: string;
  /** Timestamp */
  timestamp: number;
}

/**
 * Refinement Engine class.
 */
type ManifestExt = SimulationManifest & {
  entities: SimEntityBase[];
  steps: SimulationStep[];
  domainConfig?: { duration?: number; frameRate?: number };
  domainMetadata?: any;
};

export class RefinementEngine {
  /** Undo history stack */
  private undoStack: HistoryEntry[] = [];
  /** Redo history stack */
  private redoStack: HistoryEntry[] = [];
  /** Maximum history size */
  private maxHistorySize = 50;

  private ensureManifest(manifest: SimulationManifest): ManifestExt {
    return {
      ...manifest,
      entities: manifest.entities ?? [],
      steps: manifest.steps ?? [],
      domainConfig: manifest.domainConfig ?? {},
    };
  }
  /**
   * Apply an intent to a manifest.
   *
   * @param manifest - The current manifest
   * @param intent - The parsed intent to apply
   * @returns Refinement result
   */
  apply(manifest: SimulationManifest, intent: ParsedIntent): RefinementResult {
    const m = this.ensureManifest(manifest);
    // Save to history before modification
    this.pushToUndo(m, `Before: ${intent.type}`);

    try {
      switch (intent.type) {
        case 'add_entity':
          return this.addEntity(m, intent.params);
        case 'remove_entity':
          return this.removeEntity(m, intent.params);
        case 'modify_entity':
          return this.modifyEntity(m, intent.params);
        case 'add_step':
          return this.addStep(m, intent.params);
        case 'remove_step':
          return this.removeStep(m, intent.params);
        case 'modify_step':
          return this.modifyStep(m, intent.params);
        case 'change_speed':
          return this.changeSpeed(m, intent.params);
        case 'change_visual':
          return this.changeVisual(m, intent.params);
        case 'add_annotation':
          return this.addAnnotation(m, intent.params);
        case 'undo':
          return this.undo(m);
        case 'redo':
          return this.redo(m);
        case 'explain':
        case 'clarify':
          return this.handleExplanation(m, intent.params);
        case 'unknown':
          return {
            success: false,
            error: `Could not understand: "${intent.originalInput}"`,
            changes: [],
            suggestions: [
              'Try saying "add a new element"',
              'Try saying "make the first element blue"',
              'Try saying "add a step to compare elements"',
            ],
          };
        default:
          return {
            success: false,
            error: `Unsupported intent type: ${intent.type}`,
            changes: [],
            suggestions: [],
          };
      }
    } catch (error) {
      // Restore from undo on error
      this.undoStack.pop();
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Unknown error',
        changes: [],
        suggestions: [],
      };
    }
  }

  /**
   * Apply multiple intents sequentially.
   *
   * @param manifest - The starting manifest
   * @param intents - Array of intents to apply
   * @returns Final refinement result
   */
  applyMultiple(manifest: SimulationManifest, intents: ParsedIntent[]): RefinementResult {
    let currentManifest = this.ensureManifest(manifest);
    const allChanges: string[] = [];
    const allSuggestions: string[] = [];

    for (const intent of intents) {
      const result = this.apply(currentManifest, intent);
      if (!result.success) {
        return {
          success: false,
          error: result.error,
          changes: allChanges,
          suggestions: result.suggestions,
        };
      }
      currentManifest = this.ensureManifest(result.manifest as SimulationManifest);
      allChanges.push(...result.changes);
    }

    return {
      success: true,
      manifest: currentManifest,
      changes: allChanges,
      suggestions: allSuggestions,
    };
  }

  // === Intent Handlers ===

  private addEntity(manifest: ManifestExt, params: IntentParams): RefinementResult {
    const entityType = params.entityType ?? 'element';
    const newEntity: SimEntityBase = {
      id: randomUUID() as unknown as any,
      label: entityType,
      type: entityType,
      x: params.position?.x ?? 0,
      y: params.position?.y ?? 0,
      opacity: 1,
      color: '#4A90D9',
      scale: 1,
    };

    const entities = manifest.entities ?? [];
    const updatedManifest: ManifestExt = {
      ...manifest,
      entities: [...entities, newEntity],
    };

    return {
      success: true,
      manifest: updatedManifest,
      changes: [`Added new ${entityType} entity`],
      suggestions: [
        `You can modify this entity by saying "change the ${entityType}'s color to blue"`,
        'Add more entities to build your simulation',
      ],
    };
  }

  private removeEntity(manifest: ManifestExt, params: IntentParams): RefinementResult {
    const target = params.targetEntity?.toLowerCase();
    if (!target) {
      return {
        success: false,
        error: 'Please specify which entity to remove',
        changes: [],
        suggestions: ['Try saying "remove the first element"'],
      };
    }

    const entities = manifest.entities ?? [];
    const idx = entities.findIndex((e) => e.label?.toLowerCase() === target);
    if (idx === -1) {
      return {
        success: false,
        error: `Could not find entity: ${target}`,
        changes: [],
        suggestions: [`Available entities: ${manifest.entities.map((e) => e.label).join(', ')}`],
      };
    }

    const updated = entities.filter((e) => e.label?.toLowerCase() !== target);

    return {
      success: true,
      manifest: { ...manifest, entities: updated },
      changes: [`Removed entity: ${entities[idx].label}`],
      suggestions: [],
    };
  }

  private modifyEntity(manifest: ManifestExt, params: IntentParams): RefinementResult {
    const target = params.targetEntity?.toLowerCase();
    if (!target) {
      return {
        success: false,
        error: 'Please specify which entity to modify',
        changes: [],
        suggestions: [],
      };
    }

    const entities = manifest.entities ?? [];
    const idx = entities.findIndex((e) => e.label?.toLowerCase() === target);
    if (idx === -1) {
      return {
        success: false,
        error: `Could not find entity: ${target}`,
        changes: [],
        suggestions: [`Available entities: ${manifest.entities.map((e) => e.label).join(', ')}`],
      };
    }

    const entity = entities[idx];
    const property = params.property?.toLowerCase();
    let updatedEntity: SimEntityBase;

    switch (property) {
      case 'label':
      case 'name':
        updatedEntity = { ...entity, label: String(params.newValue) };
        break;
      case 'color':
        updatedEntity = {
          ...entity,
          color: String(params.newValue),
        };
        break;
      case 'size':
        updatedEntity = {
          ...entity,
          scale: Number(params.newValue) || 1,
        };
        break;
      case 'opacity':
        updatedEntity = {
          ...entity,
          opacity: Number(params.newValue) || 1,
        };
        break;
      default:
        return {
          success: false,
          error: `Unknown property: ${property}`,
          changes: [],
          suggestions: ['Valid properties: label, color, size, opacity'],
        };
    }

    const updatedEntities = [...entities];
    updatedEntities[idx] = updatedEntity;

    return {
      success: true,
      manifest: { ...manifest, entities: updatedEntities },
      changes: [`Changed ${entity.label}'s ${property} to ${params.newValue}`],
      suggestions: [],
    };
  }

  private addStep(manifest: ManifestExt, params: IntentParams): RefinementResult {
    const description = params.text ?? 'New step';
    const steps = manifest.steps ?? [];
    const newStep: SimulationStep = {
      id: randomUUID() as unknown as any,
      orderIndex: steps.length,
      title: params.text ?? `Step ${steps.length + 1}`,
      description,
      actions: [],
    };

    return {
      success: true,
      manifest: { ...manifest, steps: [...steps, newStep] },
      changes: [`Added step ${steps.length + 1}: "${description}"`],
      suggestions: [
        'You can add actions to this step',
        'Modify the step duration with "make it slower"',
      ],
    };
  }

  private removeStep(manifest: ManifestExt, params: IntentParams): RefinementResult {
    const stepIndex = typeof params.targetStep === 'number'
      ? params.targetStep - 1
      : parseInt(String(params.targetStep)) - 1;

    if (isNaN(stepIndex) || stepIndex < 0 || stepIndex >= (manifest.steps?.length ?? 0)) {
      return {
        success: false,
        error: `Invalid step number. Available steps: 1-${manifest.steps?.length ?? 0}`,
        changes: [],
        suggestions: [],
      };
    }

    const step = manifest.steps![stepIndex];
    const updatedSteps = manifest.steps!.filter((_, i) => i !== stepIndex);

    return {
      success: true,
      manifest: { ...manifest, steps: updatedSteps },
      changes: [`Removed step ${stepIndex + 1}: "${step.description}"`],
      suggestions: [],
    };
  }

  private modifyStep(manifest: ManifestExt, params: IntentParams): RefinementResult {
    const stepIndex = typeof params.targetStep === 'number'
      ? params.targetStep - 1
      : parseInt(String(params.targetStep)) - 1;

    if (isNaN(stepIndex) || stepIndex < 0 || stepIndex >= (manifest.steps?.length ?? 0)) {
      return {
        success: false,
        error: `Invalid step number. Available steps: 1-${manifest.steps.length}`,
        changes: [],
        suggestions: [],
      };
    }

    const updatedSteps = [...manifest.steps];
    updatedSteps[stepIndex] = {
      ...updatedSteps[stepIndex],
      description: params.text ?? updatedSteps[stepIndex].description,
    };

    return {
      success: true,
      manifest: { ...manifest, steps: updatedSteps },
      changes: [`Modified step ${stepIndex + 1}`],
      suggestions: [],
    };
  }

  private changeSpeed(manifest: ManifestExt, params: IntentParams): RefinementResult {
    const multiplier = params.duration ?? 1;
    const updatedSteps = (manifest.steps ?? []).map((step) => ({
      ...step,
      duration: Math.round((step.duration ?? 1000) * multiplier),
    }));

    return {
      success: true,
      manifest: { ...manifest, steps: updatedSteps },
      changes: [`Changed simulation speed by ${multiplier}x`],
      suggestions: [],
    };
  }

  private changeVisual(manifest: ManifestExt, params: IntentParams): RefinementResult {
    const target = params.targetEntity?.toLowerCase();
    if (!target) {
      return { success: false, error: 'Please specify which entity to modify', changes: [], suggestions: [] };
    }

    const entityIndex = manifest.entities.findIndex(
      (e) =>
        e.id?.toString().toLowerCase().includes(target) ||
        e.label?.toLowerCase().includes(target)
    );

    if (entityIndex === -1) {
      return { success: false, error: `Could not find entity: ${target}`, changes: [], suggestions: [] };
    }

    const entity = manifest.entities[entityIndex];
    const updatedEntities = [...manifest.entities];
    updatedEntities[entityIndex] = {
      ...entity,
      visual: { ...(entity as any).visual, ...params.visual },
    };

    const changes: string[] = [];
    if (params.visual?.color) changes.push(`color to ${params.visual.color}`);
    if (params.visual?.size) changes.push(`size to ${params.visual.size}`);
    if (params.visual?.opacity) changes.push(`opacity to ${params.visual.opacity}`);

    return {
      success: true,
      manifest: { ...manifest, entities: updatedEntities },
      changes: [`Changed ${entity.label}'s ${changes.join(', ')}`],
      suggestions: [],
    };
  }

  private addAnnotation(manifest: ManifestExt, params: IntentParams): RefinementResult {
    const text = params.text;
    if (!text) {
      return { success: false, error: 'Please specify the annotation text', changes: [], suggestions: [] };
    }

    const updatedSteps = [...manifest.steps];
    const lastStep = updatedSteps[updatedSteps.length - 1];

    if (lastStep) {
      updatedSteps[updatedSteps.length - 1] = {
        ...lastStep,
        annotations: [
          ...(lastStep.annotations ?? []),
          {
            id: randomUUID(),
            text,
            position: { x: 0, y: -50 },
            style: 'callout',
          },
        ],
      };
    }

    return {
      success: true,
      manifest: { ...manifest, steps: updatedSteps },
      changes: [`Added annotation: "${text}"`],
      suggestions: [],
    };
  }

  private handleExplanation(manifest: ManifestExt, _params: IntentParams): RefinementResult {
    const entities = manifest.entities ?? [];
    const steps = manifest.steps ?? [];
    const summary = [
      `This simulation has ${entities.length} entities and ${steps.length} steps.`,
      `Domain: ${manifest.domain}`,
    ];

    return {
      success: true,
      manifest,
      changes: [],
      suggestions: summary,
    };
  }

  // === Undo/Redo ===

  private pushToUndo(manifest: SimulationManifest, description: string): void {
    this.undoStack.push({
      manifest: JSON.parse(JSON.stringify(manifest)),
      description,
      timestamp: Date.now(),
    });

    // Clear redo stack on new action
    this.redoStack = [];

    // Limit history size
    if (this.undoStack.length > this.maxHistorySize) {
      this.undoStack.shift();
    }
  }

  private undo(_currentManifest: SimulationManifest): RefinementResult {
    const entry = this.undoStack.pop();
    if (!entry) {
      return {
        success: false,
        error: 'Nothing to undo',
        changes: [],
        suggestions: [],
      };
    }

    // Push current to redo
    this.redoStack.push({
      manifest: _currentManifest,
      description: 'Undo',
      timestamp: Date.now(),
    });

    return {
      success: true,
      manifest: entry.manifest,
      changes: [`Undone: ${entry.description}`],
      suggestions: [],
    };
  }

  private redo(_currentManifest: SimulationManifest): RefinementResult {
    const entry = this.redoStack.pop();
    if (!entry) {
      return {
        success: false,
        error: 'Nothing to redo',
        changes: [],
        suggestions: [],
      };
    }

    // Push current to undo
    this.undoStack.push({
      manifest: _currentManifest,
      description: 'Redo',
      timestamp: Date.now(),
    });

    return {
      success: true,
      manifest: entry.manifest,
      changes: ['Redone previous action'],
      suggestions: [],
    };
  }

  /**
   * Clear history.
   */
  clearHistory(): void {
    this.undoStack = [];
    this.redoStack = [];
  }

  /**
   * Get undo history size.
   */
  getUndoSize(): number {
    return this.undoStack.length;
  }

  /**
   * Get redo history size.
   */
  getRedoSize(): number {
    return this.redoStack.length;
  }
}
