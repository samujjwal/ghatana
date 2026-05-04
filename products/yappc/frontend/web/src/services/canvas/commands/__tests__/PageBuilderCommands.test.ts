import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { insertNode, type BuilderDocument, type ComponentInstance, type NodeId, type ValidationResult } from '@ghatana/ui-builder';

import { createEmptyBuilderDocument } from '../../../../components/canvas/page/pageArtifactDocument';
import {
  PageBuilderCommands,
  type DeleteComponentCommand,
  type ImportDocumentCommand,
  type InsertComponentCommand,
  type MoveComponentCommand,
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
    const autosavedDocument = onAutosave.mock.calls[0]?.[0];
    expect(autosavedDocument?.rootNodes).toHaveLength(1);
    expect(autosavedDocument?.nodes.size).toBe(1);
  });
});
