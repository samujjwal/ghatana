import { Box } from '@ghatana/design-system';
import React, { useMemo, useState } from 'react';

import { getContractByName } from './registry';
import { rendererManifestRegistry, type SlotBag, type RenderContext } from './rendererManifest';
import { registerBuiltInRenderers } from './builtInRenderers';
import { componentPluginLoader } from './pluginLoader';

import type { BuilderDocument, ComponentInstance, NodeId } from '@ghatana/ui-builder';

// Initialize built-in renderers on module load
registerBuiltInRenderers();

const DND_COMPONENT_MIME = 'application/x-page-component';
const DND_NODE_MIME = 'application/x-page-node';
const PAGE_RENDERER_TOKENS = {
  selectionBorder: 'var(--color-primary-600, var(--accent, #2563eb))',
  dropActiveBackground: 'var(--color-primary-100, var(--accent-soft, #dbeafe))',
  dropIdleBorder: 'var(--color-border-subtle, var(--border-subtle, #cbd5e1))',
  dropIdleBackground: 'var(--color-surface-muted, var(--surface-subtle, #f1f5f9))',
  textMuted: 'var(--color-text-muted, var(--text-muted, #334155))',
  dangerBorder: 'var(--color-destructive-border, #ef4444)',
  dangerBackground: 'var(--color-destructive-bg, #fef2f2)',
  dangerText: 'var(--color-destructive, #991b1b)',
} as const;

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

export type KeyboardMoveDirection = 'previous' | 'next' | 'out';

export interface KeyboardMoveRequest {
  readonly nodeId: NodeId;
  readonly direction: KeyboardMoveDirection;
}

export function getRegisteredRenderContractNames(): ReadonlySet<string> {
  return rendererManifestRegistry.getRegisteredContractNames();
}

export interface ComponentRendererProps {
  readonly document: BuilderDocument;
  readonly nodeId: NodeId;
  readonly selectedNodeId?: string | null;
  readonly hoveredNodeId?: string | null;
  readonly onSelect?: (nodeId: string) => void;
  readonly onNodeClick?: (nodeId: string, coordinates: { readonly x: number; readonly y: number }) => void;
  readonly onNodeHover?: (nodeId: string | null) => void;
  readonly onDropRequest?: (request: DropRequest) => void;
  readonly onKeyboardMoveRequest?: (request: KeyboardMoveRequest) => void;
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

function getSelectionStyle(isSelected: boolean, isHovered: boolean): React.CSSProperties {
  return {
    outline: isSelected
      ? `2px solid ${PAGE_RENDERER_TOKENS.selectionBorder}`
      : isHovered
        ? `2px dashed ${PAGE_RENDERER_TOKENS.selectionBorder}`
        : '1px solid transparent',
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
    // If this renderer belongs to a plugin package, execute it inside the
    // runtime guard so network/storage policies are enforced and the
    // PluginRuntimeEnvironment is accessible via context.pluginEnvironment.
    const packageName = componentPluginLoader.getPackageForContract(instance.contractName);
    if (packageName !== null) {
      return componentPluginLoader.executeWithRuntimeGuard(packageName, (env) =>
        manifest.render(instance, slots, { ...context, pluginEnvironment: env }),
      );
    }
    return manifest.render(instance, slots, context);
  }

  // Use fallback renderer for unknown components
  const fallback = rendererManifestRegistry.getFallbackRenderer();
  if (fallback) {
    return fallback.render(instance, slots, context);
  }

  return (
    <div
      style={{
        padding: 16,
        border: `2px dashed ${PAGE_RENDERER_TOKENS.dangerBorder}`,
        borderRadius: 8,
        backgroundColor: PAGE_RENDERER_TOKENS.dangerBackground,
        color: PAGE_RENDERER_TOKENS.dangerText,
      }}
    >
      Unknown component contract: {instance.contractName}
    </div>
  );
}

export const ComponentRenderer: React.FC<ComponentRendererProps> = ({
  document,
  nodeId,
  selectedNodeId,
  hoveredNodeId,
  onSelect,
  onNodeClick,
  onNodeHover,
  onDropRequest,
  onKeyboardMoveRequest,
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
        hoveredNodeId={hoveredNodeId}
        onSelect={onSelect}
        onNodeClick={onNodeClick}
        onNodeHover={onNodeHover}
        onDropRequest={onDropRequest}
        onKeyboardMoveRequest={onKeyboardMoveRequest}
      />
    ));
  };

  const slotDefault = renderSlot(instance.slots.default);
  const slotActions = renderSlot(instance.slots.actions);
  const isHovered = hoveredNodeId === nodeId && !isSelected;

  return (
    <Box className="relative" data-builder-node-id={nodeId}>
      <Box
        data-testid={`${testId}-drop-before`}
        className="h-2 rounded"
        style={{
          backgroundColor: activeDropZone === 'before' ? PAGE_RENDERER_TOKENS.dropActiveBackground : 'transparent',
        }}
        onDragOver={(event) => {
          event.preventDefault();
          setActiveDropZone('before');
        }}
        onDragLeave={() => setActiveDropZone((zone) => (zone === 'before' ? null : zone))}
        onDrop={(event) => emitDrop(event, 'before')}
      />

      <div
        style={getSelectionStyle(isSelected, isHovered)}
        data-testid={testId}
        data-preview-hovered={isHovered ? 'true' : undefined}
        draggable
        tabIndex={0}
        role="treeitem"
        aria-label={`${instance.contractName} component. Use Alt+ArrowUp or Alt+ArrowDown to reorder, Alt+ArrowLeft to move out of a container.`}
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
        onKeyDown={(event) => {
          if (!event.altKey || !onKeyboardMoveRequest) {
            return;
          }

          if (event.key === 'ArrowUp') {
            event.preventDefault();
            onKeyboardMoveRequest({ nodeId, direction: 'previous' });
          } else if (event.key === 'ArrowDown') {
            event.preventDefault();
            onKeyboardMoveRequest({ nodeId, direction: 'next' });
          } else if (event.key === 'ArrowLeft') {
            event.preventDefault();
            onKeyboardMoveRequest({ nodeId, direction: 'out' });
          }
        }}
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
          backgroundColor: activeDropZone === 'after' ? PAGE_RENDERER_TOKENS.dropActiveBackground : 'transparent',
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
  readonly onDragOver: (event: React.DragEvent<HTMLElement>, slotName: string) => void;
  readonly onDragLeave: (slotName: string) => void;
  readonly onDrop: (event: React.DragEvent<HTMLElement>, slotName: string) => void;
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
              borderColor: isActive ? PAGE_RENDERER_TOKENS.selectionBorder : PAGE_RENDERER_TOKENS.dropIdleBorder,
              backgroundColor: isActive ? PAGE_RENDERER_TOKENS.dropActiveBackground : PAGE_RENDERER_TOKENS.dropIdleBackground,
              fontSize: '0.75rem',
              color: PAGE_RENDERER_TOKENS.textMuted,
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
