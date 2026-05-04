import { Box } from '@ghatana/design-system';
import React, { useMemo, useState } from 'react';

import { getContractByName } from './registry';
import { rendererManifestRegistry, type SlotBag, type RenderContext } from './rendererManifest';
import { registerBuiltInRenderers } from './builtInRenderers';

import type { BuilderDocument, ComponentInstance, NodeId } from '@ghatana/ui-builder';

// Initialize built-in renderers on module load
registerBuiltInRenderers();

const DND_COMPONENT_MIME = 'application/x-page-component';
const DND_NODE_MIME = 'application/x-page-node';

type DropPlacement = 'before' | 'after' | 'slot';

export type DropSource =
  | { readonly kind: 'palette'; readonly contractName: string }
  | { readonly kind: 'node'; readonly nodeId: NodeId };

export interface DropRequest {
  readonly source: DropSource;
  readonly targetNodeId: NodeId;
  readonly placement: DropPlacement;
  readonly slotName?: string;
}

export function getRegisteredRenderContractNames(): ReadonlySet<string> {
  return rendererManifestRegistry.getRegisteredContractNames();
}

export interface ComponentRendererProps {
  readonly document: BuilderDocument;
  readonly nodeId: NodeId;
  readonly selectedNodeId?: string | null;
  readonly onSelect?: (nodeId: string) => void;
  readonly onNodeClick?: (nodeId: string, coordinates: { readonly x: number; readonly y: number }) => void;
  readonly onNodeHover?: (nodeId: string | null) => void;
  readonly onDropRequest?: (request: DropRequest) => void;
}

function readDropSource(event: React.DragEvent<HTMLElement>): DropSource | null {
  const nodeId = event.dataTransfer.getData(DND_NODE_MIME);
  if (nodeId) {
    return { kind: 'node', nodeId: nodeId as NodeId };
  }

  const contractName = event.dataTransfer.getData(DND_COMPONENT_MIME);
  if (contractName) {
    return { kind: 'palette', contractName };
  }

  return null;
}

function getSelectionStyle(isSelected: boolean): React.CSSProperties {
  return {
    outline: isSelected ? '2px solid var(--accent, #2563eb)' : '1px solid transparent',
    outlineOffset: '2px',
    cursor: 'pointer',
    borderRadius: 8,
  };
}

function renderInstance(
  instance: ComponentInstance,
  slots: SlotBag,
  context: RenderContext,
): React.ReactNode {
  const manifest = rendererManifestRegistry.get(instance.contractName);
  if (manifest) {
    return manifest.render(instance, slots, context);
  }

  // Use fallback renderer for unknown components
  const fallback = rendererManifestRegistry.getFallbackRenderer();
  if (fallback) {
    return fallback.render(instance, slots, context);
  }

  return (
    <div style={{ padding: 16, border: '2px dashed #ef4444', borderRadius: 8 }}>
      Unknown component contract: {instance.contractName}
    </div>
  );
}

export const ComponentRenderer: React.FC<ComponentRendererProps> = ({
  document,
  nodeId,
  selectedNodeId,
  onSelect,
  onNodeClick,
  onNodeHover,
  onDropRequest,
}) => {
  const instance = document.nodes.get(nodeId);
  const [activeDropZone, setActiveDropZone] = useState<string | null>(null);

  if (!instance) {
    return null;
  }

  const contract = getContractByName(instance.contractName);
  const isContainer = contract?.layout?.isContainer ?? false;
  const declaredSlots = useMemo(
    () => contract?.slots.map((slot) => slot.name) ?? [],
    [contract?.slots],
  );

  const isSelected = selectedNodeId === nodeId;
  const testId = contract?.name
    ? `page-${contract.name.toLowerCase()}`
    : `page-${instance.contractName.toLowerCase()}`;

  const emitDrop = (event: React.DragEvent<HTMLElement>, placement: DropPlacement, slotName?: string): void => {
    if (!onDropRequest) {
      return;
    }

    event.preventDefault();
    event.stopPropagation();
    setActiveDropZone(null);

    const source = readDropSource(event);
    if (!source) {
      return;
    }

    onDropRequest({
      source,
      targetNodeId: nodeId,
      placement,
      slotName,
    });
  };

  const renderSlot = (slotIds: readonly NodeId[] | undefined): React.ReactNode => {
    if (!slotIds?.length) {
      return null;
    }

    return slotIds.map((childId) => (
      <ComponentRenderer
        key={childId}
        document={document}
        nodeId={childId}
        selectedNodeId={selectedNodeId}
        onSelect={onSelect}
        onNodeClick={onNodeClick}
        onNodeHover={onNodeHover}
        onDropRequest={onDropRequest}
      />
    ));
  };

  const slotDefault = renderSlot(instance.slots.default);
  const slotActions = renderSlot(instance.slots.actions);

  return (
    <Box className="relative" data-builder-node-id={nodeId}>
      <Box
        data-testid={`${testId}-drop-before`}
        className="h-2 rounded"
        style={{
          backgroundColor: activeDropZone === 'before' ? 'rgba(25,118,210,0.18)' : 'transparent',
        }}
        onDragOver={(event) => {
          event.preventDefault();
          setActiveDropZone('before');
        }}
        onDragLeave={() => setActiveDropZone((zone) => (zone === 'before' ? null : zone))}
        onDrop={(event) => emitDrop(event, 'before')}
      />

      <div
        style={getSelectionStyle(isSelected)}
        data-testid={testId}
        draggable
        onDragStart={(event) => {
          event.dataTransfer.setData(DND_NODE_MIME, nodeId);
          event.dataTransfer.effectAllowed = 'move';
        }}
        onClick={(event) => {
          event.stopPropagation();
          onSelect?.(nodeId);
          onNodeClick?.(nodeId, { x: event.clientX, y: event.clientY });
        }}
        onMouseEnter={() => onNodeHover?.(nodeId)}
        onMouseLeave={() => onNodeHover?.(null)}
      >
        {renderInstance(instance, { default: slotDefault, actions: slotActions }, { mode: 'canvas', selectedNodeId })}

        {isContainer && declaredSlots.length > 0 ? (
          <StackedSlotTargets
            slotNames={declaredSlots}
            activeDropZone={activeDropZone}
            onDragOver={(event, slotName) => {
              event.preventDefault();
              setActiveDropZone(`slot:${slotName}`);
            }}
            onDragLeave={(slotName) => {
              setActiveDropZone((zone) => (zone === `slot:${slotName}` ? null : zone));
            }}
            onDrop={(event, slotName) => emitDrop(event, 'slot', slotName)}
          />
        ) : null}
      </div>

      <Box
        data-testid={`${testId}-drop-after`}
        className="h-2 rounded"
        style={{
          backgroundColor: activeDropZone === 'after' ? 'rgba(25,118,210,0.18)' : 'transparent',
        }}
        onDragOver={(event) => {
          event.preventDefault();
          setActiveDropZone('after');
        }}
        onDragLeave={() => setActiveDropZone((zone) => (zone === 'after' ? null : zone))}
        onDrop={(event) => emitDrop(event, 'after')}
      />
    </Box>
  );
};

interface StackedSlotTargetsProps {
  readonly slotNames: readonly string[];
  readonly activeDropZone: string | null;
  readonly onDragOver: (event: React.DragEvent<HTMLDivElement>, slotName: string) => void;
  readonly onDragLeave: (slotName: string) => void;
  readonly onDrop: (event: React.DragEvent<HTMLDivElement>, slotName: string) => void;
}

const StackedSlotTargets: React.FC<StackedSlotTargetsProps> = ({
  slotNames,
  activeDropZone,
  onDragOver,
  onDragLeave,
  onDrop,
}) => {
  if (slotNames.length === 0) {
    return null;
  }

  return (
    <Box className="mt-2 flex flex-col gap-1">
      {slotNames.map((slotName) => {
        const zoneId = `slot:${slotName}`;
        const isActive = activeDropZone === zoneId;

        return (
          <Box
            key={slotName}
            data-testid={`slot-drop-${slotName}`}
            className="rounded border border-dashed px-2 py-1"
            style={{
              borderColor: isActive ? 'var(--accent, #2563eb)' : 'var(--border-subtle, #cbd5e1)',
              backgroundColor: isActive ? 'var(--accent-soft, #dbeafe)' : 'var(--surface-subtle, #f1f5f9)',
              fontSize: '0.75rem',
              color: 'var(--text-muted, #334155)',
            }}
            onDragOver={(event) => onDragOver(event, slotName)}
            onDragLeave={() => onDragLeave(slotName)}
            onDrop={(event) => onDrop(event, slotName)}
          >
            Drop into slot: {slotName}
          </Box>
        );
      })}
    </Box>
  );
};
