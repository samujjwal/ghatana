import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { insertNode, type Binding, type BuilderDocument, type ComponentInstance, type NodeId, type ValidationResult } from '@ghatana/ui-builder';

import { createEmptyBuilderDocument } from '../../../../components/canvas/page/pageArtifactDocument';
import {
  type ApplySuggestedImprovementCommand,
  type AddActionBindingCommand,
  type AddDataBindingCommand,
  type MapComponentToDesignSystemCommand,
  PageBuilderCommands,
  type DeleteComponentCommand,
  type ImportDocumentCommand,
  type InsertComponentCommand,
  type MoveComponentCommand,
  type ReorderComponentCommand,
  type ReviewResidualIslandCommand,
  type SetResponsiveVariantCommand,
  type SetStateVariantCommand,
  type UpdatePropsCommand,
} from '../PageBuilderCommands';

function makeInstance(name: string, childrenSlot = false): Omit<ComponentInstance, 'id'> {
  return {
    contractName: name,
    props: {},
    slots: childrenSlot ? { children: [] } : {},
    bindings: [],
    metadata: {
      name,
      layout: {},
    },
  };
}

function createCommands(initialDocument: BuilderDocument, onAutosave?: (document: BuilderDocument) => Promise<void>) {
  const onAudit = vi.fn();
  const onTelemetry = vi.fn();
  const validate = vi.fn<() => ValidationResult>(() => ({
    valid: true,
    errors: [],
    warnings: [],
  }));

  const commands = new PageBuilderCommands({
    initialDocument,
    onAudit,
    onTelemetry,
    validate,
    onAutosave: onAutosave
      ? async (document) => {
          await onAutosave(document);
        }
      : undefined,
    autosaveIntervalMs: 50,
  });

  return { commands, onAudit, onTelemetry, validate };
}

describe('PageBuilderCommands', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('inserts a component and undoes/redoes it using document snapshots', async () => {
    const initialDocument = createEmptyBuilderDocument('Test Page', 'tester');
    const { commands } = createCommands(initialDocument);

    const insertCommand: InsertComponentCommand = {
      id: 'insert-1',
      type: 'insert-component',
      timestamp: new Date().toISOString(),
      data: {
        instance: makeInstance('Button'),
      },
    };

    const insertResult = await commands.execute(insertCommand);
    expect(insertResult.success).toBe(true);
    expect(insertResult.document.rootNodes).toHaveLength(1);

    const insertedNodeId = insertResult.document.rootNodes[0] as NodeId;
    expect(insertResult.changedNodeIds).toEqual([insertedNodeId]);

    const undoResult = await commands.undo();
    expect(undoResult.success).toBe(true);
    expect(undoResult.document.rootNodes).toHaveLength(0);

    const redoResult = await commands.redo();
    expect(redoResult.success).toBe(true);
    expect(redoResult.document.rootNodes).toHaveLength(1);
  });

  it('emits audit and telemetry records with a shared command correlation id', async () => {
    const initialDocument = createEmptyBuilderDocument('Audited Page', 'tester');
    const { commands, onAudit, onTelemetry } = createCommands(initialDocument);

    await commands.execute({
      id: 'insert-correlated',
      type: 'insert-component',
      timestamp: new Date().toISOString(),
      artifactId: 'artifact-1',
      data: {
        instance: makeInstance('Button'),
      },
    });

    expect(onAudit).toHaveBeenCalledWith(
      expect.objectContaining({
        commandId: 'insert-correlated',
        correlationId: 'insert-correlated',
        commandType: 'insert-component',
        artifactId: 'artifact-1',
      }),
      expect.objectContaining({ success: true }),
    );
    expect(onTelemetry).toHaveBeenCalledWith(
      'page_builder_command_executed',
      expect.objectContaining({
        correlationId: 'insert-correlated',
        commandType: 'insert-component',
        artifactId: 'artifact-1',
        validationErrorCount: 0,
      }),
    );
  });

  it('moves a component into a container slot and restores its original location on undo', async () => {
    const initialDocument = createEmptyBuilderDocument('Test Page', 'tester');
    const { commands } = createCommands(initialDocument);

    const containerResult = await commands.execute({
      id: 'insert-container',
      type: 'insert-component',
      timestamp: new Date().toISOString(),
      data: {
        instance: makeInstance('Stack', true),
      },
    });
    const childResult = await commands.execute({
      id: 'insert-child',
      type: 'insert-component',
      timestamp: new Date().toISOString(),
      data: {
        instance: makeInstance('Button'),
      },
    });

    const containerId = containerResult.document.rootNodes[0] as NodeId;
    const childId = childResult.document.rootNodes[1] as NodeId;

    const moveCommand: MoveComponentCommand = {
      id: 'move-1',
      type: 'move-component',
      timestamp: new Date().toISOString(),
      data: {
        nodeId: childId,
        newParentId: containerId,
        newSlotName: 'children',
        index: 0,
      },
    };

    const moved = await commands.execute(moveCommand);
    expect(moved.success).toBe(true);
    expect(moved.document.rootNodes).toEqual([containerId]);
    expect(moved.document.nodes.get(containerId)?.slots.children).toEqual([childId]);

    const undone = await commands.undo();
    expect(undone.document.rootNodes).toEqual([containerId, childId]);
    expect(undone.document.nodes.get(containerId)?.slots.children).toEqual([]);
  });

  it('reorders root nodes deterministically and restores previous order on undo', async () => {
    const initialDocument = createEmptyBuilderDocument('Test Page', 'tester');
    const { commands } = createCommands(initialDocument);

    const first = await commands.execute({
      id: 'insert-first',
      type: 'insert-component',
      timestamp: new Date().toISOString(),
      data: {
        instance: makeInstance('Hero'),
      },
    });
    const second = await commands.execute({
      id: 'insert-second',
      type: 'insert-component',
      timestamp: new Date().toISOString(),
      data: {
        instance: makeInstance('Button'),
      },
    });

    const firstId = first.document.rootNodes[0] as NodeId;
    const secondId = second.document.rootNodes[1] as NodeId;

    const reorderCommand: ReorderComponentCommand = {
      id: 'reorder-root',
      type: 'reorder-component',
      timestamp: new Date().toISOString(),
      data: {
        nodeId: secondId,
        parentId: null,
        index: 0,
      },
    };

    const reordered = await commands.execute(reorderCommand);
    expect(reordered.success).toBe(true);
    expect(reordered.document.rootNodes).toEqual([secondId, firstId]);

    const undone = await commands.undo();
    expect(undone.success).toBe(true);
    expect(undone.document.rootNodes).toEqual([firstId, secondId]);
  });

  it('updates component props and metadata name and restores previous values on undo', async () => {
    const initialDocument = createEmptyBuilderDocument('Test Page', 'tester');
    const { commands } = createCommands(initialDocument);

    const insertResult = await commands.execute({
      id: 'insert-1',
      type: 'insert-component',
      timestamp: new Date().toISOString(),
      data: {
        instance: makeInstance('TextField'),
      },
    });
    const nodeId = insertResult.document.rootNodes[0] as NodeId;

    const updateCommand: UpdatePropsCommand = {
      id: 'update-1',
      type: 'update-props',
      timestamp: new Date().toISOString(),
      data: {
        nodeId,
        props: { label: 'Email' },
        name: 'Email Field',
      },
    };

    const updated = await commands.execute(updateCommand);
    expect(updated.document.nodes.get(nodeId)?.props.label).toBe('Email');
    expect(updated.document.nodes.get(nodeId)?.metadata.name).toBe('Email Field');

    const undone = await commands.undo();
    expect(undone.document.nodes.get(nodeId)?.props.label).toBeUndefined();
    expect(undone.document.nodes.get(nodeId)?.metadata.name).toBe('TextField');
  });

  it('adds event/data bindings as semantic commands and supports undo', async () => {
    const initialDocument = createEmptyBuilderDocument('Binding Page', 'tester');
    const { commands } = createCommands(initialDocument);

    const inserted = await commands.execute({
      id: 'insert-bind-node',
      type: 'insert-component',
      timestamp: new Date().toISOString(),
      data: {
        instance: makeInstance('Button'),
      },
    });
    const nodeId = inserted.document.rootNodes[0] as NodeId;

    const actionBinding: Binding = {
      id: 'binding-event-1',
      type: 'event',
      source: 'actions.submitForm',
      target: 'onClick',
    };
    const dataBinding: Binding = {
      id: 'binding-data-1',
      type: 'data',
      source: 'dataSource.user.email',
      target: 'label',
    };

    const addAction: AddActionBindingCommand = {
      id: 'cmd-action-binding',
      type: 'add-action-binding',
      timestamp: new Date().toISOString(),
      data: {
        nodeId,
        binding: actionBinding,
      },
    };

    const addData: AddDataBindingCommand = {
      id: 'cmd-data-binding',
      type: 'add-data-binding',
      timestamp: new Date().toISOString(),
      data: {
        nodeId,
        binding: dataBinding,
      },
    };

    const actionResult = await commands.execute(addAction);
    expect(actionResult.success).toBe(true);
    expect(actionResult.document.nodes.get(nodeId)?.bindings).toHaveLength(1);
    expect(actionResult.document.nodes.get(nodeId)?.bindings[0]?.id).toBe('binding-event-1');

    const dataResult = await commands.execute(addData);
    expect(dataResult.success).toBe(true);
    expect(dataResult.document.nodes.get(nodeId)?.bindings).toHaveLength(2);
    expect(dataResult.document.nodes.get(nodeId)?.bindings[1]?.id).toBe('binding-data-1');

    const undone = await commands.undo();
    expect(undone.success).toBe(true);
    expect(undone.document.nodes.get(nodeId)?.bindings).toHaveLength(1);
    expect(undone.document.nodes.get(nodeId)?.bindings[0]?.id).toBe('binding-event-1');
  });

  it('rejects semantic binding commands when binding type does not match the command contract', async () => {
    const initialDocument = createEmptyBuilderDocument('Binding Guard Page', 'tester');
    const { commands } = createCommands(initialDocument);

    const inserted = await commands.execute({
      id: 'insert-bind-node-2',
      type: 'insert-component',
      timestamp: new Date().toISOString(),
      data: {
        instance: makeInstance('Button'),
      },
    });
    const nodeId = inserted.document.rootNodes[0] as NodeId;

    const invalidActionCommand: AddActionBindingCommand = {
      id: 'cmd-invalid-action-binding',
      type: 'add-action-binding',
      timestamp: new Date().toISOString(),
      data: {
        nodeId,
        binding: {
          id: 'binding-invalid-1',
          type: 'data',
          source: 'dataSource.user.name',
          target: 'label',
        },
      },
    };

    const invalidDataCommand: AddDataBindingCommand = {
      id: 'cmd-invalid-data-binding',
      type: 'add-data-binding',
      timestamp: new Date().toISOString(),
      data: {
        nodeId,
        binding: {
          id: 'binding-invalid-2',
          type: 'event',
          source: 'actions.refresh',
          target: 'onClick',
        },
      },
    };

    const actionFailure = await commands.execute(invalidActionCommand);
    expect(actionFailure.success).toBe(false);
    expect(actionFailure.error).toContain('Action bindings must use event binding type');

    const dataFailure = await commands.execute(invalidDataCommand);
    expect(dataFailure.success).toBe(false);
    expect(dataFailure.error).toContain('Data bindings must use data binding type');
  });

  it('sets responsive variant overrides and restores previous values on undo', async () => {
    const initialDocument = createEmptyBuilderDocument('Responsive Page', 'tester');
    const { commands } = createCommands(initialDocument);

    const inserted = await commands.execute({
      id: 'insert-responsive-node',
      type: 'insert-component',
      timestamp: new Date().toISOString(),
      data: {
        instance: makeInstance('Card'),
      },
    });
    const nodeId = inserted.document.rootNodes[0] as NodeId;

    const setResponsiveCommand: SetResponsiveVariantCommand = {
      id: 'set-responsive-md',
      type: 'set-responsive-variant',
      timestamp: new Date().toISOString(),
      data: {
        nodeId,
        variant: {
          breakpoint: 'md',
          props: {
            elevation: 3,
          },
        },
      },
    };

    const updated = await commands.execute(setResponsiveCommand);
    expect(updated.success).toBe(true);
    expect(updated.document.nodes.get(nodeId)?.metadata.responsiveVariants).toEqual([
      {
        breakpoint: 'md',
        props: {
          elevation: 3,
        },
      },
    ]);

    const undone = await commands.undo();
    expect(undone.success).toBe(true);
    expect(undone.document.nodes.get(nodeId)?.metadata.responsiveVariants ?? []).toHaveLength(0);
  });

  it('sets state variants by state key and replaces existing state variant entries', async () => {
    const initialDocument = createEmptyBuilderDocument('State Variant Page', 'tester');
    const { commands } = createCommands(initialDocument);

    const inserted = await commands.execute({
      id: 'insert-state-node',
      type: 'insert-component',
      timestamp: new Date().toISOString(),
      data: {
        instance: makeInstance('Button'),
      },
    });
    const nodeId = inserted.document.rootNodes[0] as NodeId;

    const firstStateVariant: SetStateVariantCommand = {
      id: 'set-state-hover-1',
      type: 'set-state-variant',
      timestamp: new Date().toISOString(),
      data: {
        nodeId,
        variant: {
          state: 'hover',
          props: {
            variant: 'filled',
          },
        },
      },
    };

    const secondStateVariant: SetStateVariantCommand = {
      id: 'set-state-hover-2',
      type: 'set-state-variant',
      timestamp: new Date().toISOString(),
      data: {
        nodeId,
        variant: {
          state: 'hover',
          props: {
            variant: 'outlined',
          },
        },
      },
    };

    const first = await commands.execute(firstStateVariant);
    expect(first.success).toBe(true);
    expect(first.document.nodes.get(nodeId)?.metadata.stateVariants).toEqual([
      {
        state: 'hover',
        props: {
          variant: 'filled',
        },
      },
    ]);

    const second = await commands.execute(secondStateVariant);
    expect(second.success).toBe(true);
    expect(second.document.nodes.get(nodeId)?.metadata.stateVariants).toEqual([
      {
        state: 'hover',
        props: {
          variant: 'outlined',
        },
      },
    ]);

    const undone = await commands.undo();
    expect(undone.success).toBe(true);
    expect(undone.document.nodes.get(nodeId)?.metadata.stateVariants).toEqual([
      {
        state: 'hover',
        props: {
          variant: 'filled',
        },
      },
    ]);
  });

  it('applies a suggested improvement through semantic command and supports undo', async () => {
    const initialDocument = createEmptyBuilderDocument('Suggested Improvements', 'tester');
    const { commands } = createCommands(initialDocument);

    const inserted = await commands.execute({
      id: 'insert-suggestion-node',
      type: 'insert-component',
      timestamp: new Date().toISOString(),
      data: {
        instance: makeInstance('TextField'),
      },
    });
    const nodeId = inserted.document.rootNodes[0] as NodeId;

    const applyCommand: ApplySuggestedImprovementCommand = {
      id: 'apply-suggested-1',
      type: 'apply-suggested-improvement',
      timestamp: new Date().toISOString(),
      data: {
        suggestionId: 'improvement-aria-label',
        nodeId,
        props: {
          ariaLabel: 'Email address',
        },
        name: 'Email input',
      },
    };

    const applied = await commands.execute(applyCommand);
    expect(applied.success).toBe(true);
    expect(applied.document.nodes.get(nodeId)?.props.ariaLabel).toBe('Email address');
    expect(applied.document.nodes.get(nodeId)?.metadata.name).toBe('Email input');

    const undone = await commands.undo();
    expect(undone.success).toBe(true);
    expect(undone.document.nodes.get(nodeId)?.props.ariaLabel).toBeUndefined();
    expect(undone.document.nodes.get(nodeId)?.metadata.name).toBe('TextField');
  });

  it('maps a component node to a canonical design-system contract and restores prior contract on undo', async () => {
    const initialDocument = createEmptyBuilderDocument('Mapping Page', 'tester');
    const { commands } = createCommands(initialDocument);

    const inserted = await commands.execute({
      id: 'insert-mapping-node',
      type: 'insert-component',
      timestamp: new Date().toISOString(),
      data: {
        instance: makeInstance('LegacyTextInput'),
      },
    });
    const nodeId = inserted.document.rootNodes[0] as NodeId;

    const mapCommand: MapComponentToDesignSystemCommand = {
      id: 'map-node-to-ds',
      type: 'map-component-to-design-system',
      timestamp: new Date().toISOString(),
      data: {
        nodeId,
        contractName: 'TextField',
        props: {
          placeholder: 'email@example.com',
        },
        name: 'Email field',
      },
    };

    const mapped = await commands.execute(mapCommand);
    expect(mapped.success).toBe(true);
    expect(mapped.document.nodes.get(nodeId)?.contractName).toBe('TextField');
    expect(mapped.document.nodes.get(nodeId)?.props.placeholder).toBe('email@example.com');
    expect(mapped.document.nodes.get(nodeId)?.metadata.name).toBe('Email field');

    const undone = await commands.undo();
    expect(undone.success).toBe(true);
    expect(undone.document.nodes.get(nodeId)?.contractName).toBe('LegacyTextInput');
    expect(undone.document.nodes.get(nodeId)?.props.placeholder).toBeUndefined();
    expect(undone.document.nodes.get(nodeId)?.metadata.name).toBe('LegacyTextInput');
  });

  it('migrates legacy contract aliases when mapping a node to the design system', async () => {
    const initialDocument = createEmptyBuilderDocument('Versioned Mapping Page', 'tester');
    const { commands } = createCommands(initialDocument);

    const inserted = await commands.execute({
      id: 'insert-versioned-mapping-node',
      type: 'insert-component',
      timestamp: new Date().toISOString(),
      data: {
        instance: makeInstance('LegacyButton'),
      },
    });
    const nodeId = inserted.document.rootNodes[0] as NodeId;

    const mapped = await commands.execute({
      id: 'map-legacy-button-to-ds',
      type: 'map-component-to-design-system',
      timestamp: new Date().toISOString(),
      data: {
        nodeId,
        contractName: 'button',
        props: {
          variant: 'contained',
          size: 'large',
          color: 'error',
          text: 'Save',
        },
        name: 'Save button',
      },
    });

    expect(mapped.success).toBe(true);
    expect(mapped.document.nodes.get(nodeId)?.contractName).toBe('Button');
    expect(mapped.document.nodes.get(nodeId)?.props).toMatchObject({
      variant: 'solid',
      size: 'lg',
      color: 'danger',
      children: 'Save',
    });
  });

  it('records residual island review status on node metadata and restores previous status on undo', async () => {
    const initialDocument = createEmptyBuilderDocument('Residual Review Page', 'tester');
    const { commands } = createCommands(initialDocument);

    const inserted = await commands.execute({
      id: 'insert-residual-node',
      type: 'insert-component',
      timestamp: new Date().toISOString(),
      data: {
        instance: makeInstance('Card'),
      },
    });
    const nodeId = inserted.document.rootNodes[0] as NodeId;

    const reviewCommand: ReviewResidualIslandCommand = {
      id: 'review-residual-1',
      type: 'review-residual-island',
      timestamp: new Date().toISOString(),
      userId: 'reviewer-1',
      data: {
        nodeId,
        residualIslandId: 'legacy-chart',
        reviewStatus: 'approved',
        notes: 'Mapped to canonical chart contract',
      },
    };

    const reviewed = await commands.execute(reviewCommand);
    expect(reviewed.success).toBe(true);
    expect(reviewed.document.nodes.get(nodeId)?.metadata.reviewStatus?.status).toBe('approved');
    expect(reviewed.document.nodes.get(nodeId)?.metadata.reviewStatus?.reviewedBy).toBe('reviewer-1');
    expect(reviewed.document.nodes.get(nodeId)?.metadata.reviewStatus?.notes).toContain('legacy-chart');

    const undone = await commands.undo();
    expect(undone.success).toBe(true);
    expect(undone.document.nodes.get(nodeId)?.metadata.reviewStatus).toBeUndefined();
  });

  it('fails residual review command when node is missing', async () => {
    const initialDocument = createEmptyBuilderDocument('Residual Review Error', 'tester');
    const { commands } = createCommands(initialDocument);

    const reviewCommand: ReviewResidualIslandCommand = {
      id: 'review-missing-node',
      type: 'review-residual-island',
      timestamp: new Date().toISOString(),
      userId: 'reviewer-1',
      data: {
        nodeId: 'missing-node' as NodeId,
        residualIslandId: 'legacy-widget',
        reviewStatus: 'rejected',
        notes: 'Cannot validate source mapping',
      },
    };

    const result = await commands.execute(reviewCommand);
    expect(result.success).toBe(false);
    expect(result.error).toContain('Cannot review residual island for missing node');
  });

  it('deletes a component subtree and restores it on undo', async () => {
    const initialDocument = createEmptyBuilderDocument('Test Page', 'tester');
    const { commands } = createCommands(initialDocument);

    const parentResult = await commands.execute({
      id: 'insert-parent',
      type: 'insert-component',
      timestamp: new Date().toISOString(),
      data: {
        instance: makeInstance('Card', true),
      },
    });
    const parentId = parentResult.document.rootNodes[0] as NodeId;
    const childResult = await commands.execute({
      id: 'insert-child',
      type: 'insert-component',
      timestamp: new Date().toISOString(),
      data: {
        instance: makeInstance('Badge'),
        parentId,
        slotName: 'children',
      },
    });
    const childId = childResult.changedNodeIds[0] as NodeId;

    const deleteCommand: DeleteComponentCommand = {
      id: 'delete-1',
      type: 'delete-component',
      timestamp: new Date().toISOString(),
      data: {
        nodeId: parentId,
      },
    };

    const deleted = await commands.execute(deleteCommand);
    expect(deleted.changedNodeIds).toEqual(expect.arrayContaining([parentId, childId]));
    expect(deleted.document.rootNodes).toEqual([]);
    expect(deleted.document.nodes.size).toBe(0);

    const undone = await commands.undo();
    expect(undone.document.rootNodes).toEqual([parentId]);
    expect(undone.document.nodes.get(parentId)?.slots.children).toEqual([childId]);
  });

  it('imports a document and restores the previous document on undo', async () => {
    const initialDocument = createEmptyBuilderDocument('Original Page', 'tester');
    const importedDocument = insertNode(
      createEmptyBuilderDocument('Imported Page', 'tester'),
      makeInstance('Hero')
    );
    const { commands } = createCommands(initialDocument);

    const importCommand: ImportDocumentCommand = {
      id: 'import-1',
      type: 'import-document',
      timestamp: new Date().toISOString(),
      data: {
        document: importedDocument,
      },
    };

    const imported = await commands.execute(importCommand);
    expect(imported.document.name).toBe('Imported Page');
    expect(imported.document.rootNodes).toHaveLength(1);

    const undone = await commands.undo();
    expect(undone.document.name).toBe('Original Page');
    expect(undone.document.rootNodes).toHaveLength(0);
  });

  it('autosaves the actual current document state instead of command history', async () => {
    const initialDocument = createEmptyBuilderDocument('Autosave Page', 'tester');
    const onAutosave = vi.fn(async (_document: BuilderDocument) => undefined);
    const { commands } = createCommands(initialDocument, onAutosave);

    await commands.execute({
      id: 'insert-1',
      type: 'insert-component',
      timestamp: new Date().toISOString(),
      data: {
        instance: makeInstance('Button'),
      },
    });

    await vi.advanceTimersByTimeAsync(60);

    expect(onAutosave).toHaveBeenCalledTimes(1);
    expect(commands.getHistory()).toHaveLength(1);
    expect(commands.getSystemHistory()).toHaveLength(1);
    const autosavedDocument = onAutosave.mock.calls[0]?.[0];
    expect(autosavedDocument?.rootNodes).toHaveLength(1);
    expect(autosavedDocument?.nodes.size).toBe(1);
  });
});
