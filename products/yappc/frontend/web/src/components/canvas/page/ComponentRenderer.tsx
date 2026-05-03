import {
  Box,
  Button,
  Card,
  CardContent,
  CardHeader,
  TextField,
  Typography,
} from '@ghatana/design-system';
import React, { useMemo, useState } from 'react';

import { getContractByName } from './registry';

import type { BuilderDocument, ComponentInstance, NodeId } from '@ghatana/ui-builder';

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

interface SlotBag {
  readonly default: React.ReactNode;
  readonly actions: React.ReactNode;
}

type ContractRenderFn = (instance: ComponentInstance, slots: SlotBag) => React.ReactNode;

const COMPONENT_RENDER_MAP: Record<string, ContractRenderFn> = {
  Button: (instance) => (
    <Button
      variant={instance.props.variant as 'solid' | 'outline' | 'ghost' | undefined}
      tone={instance.props.color as
        | 'primary'
        | 'secondary'
        | 'success'
        | 'warning'
        | 'danger'
        | 'info'
        | undefined}
      size={instance.props.size as 'sm' | 'md' | 'lg' | undefined}
      disabled={Boolean(instance.props.disabled)}
      fullWidth={Boolean(instance.props.fullWidth)}
    >
      {(instance.props.children as React.ReactNode) ?? instance.metadata.name ?? 'Button'}
    </Button>
  ),
  Card: (instance, { default: slotDefault, actions: slotActions }) => (
    <Card elevation={typeof instance.props.elevation === 'number' ? instance.props.elevation : 2}>
      {instance.props.title || instance.props.subtitle ? (
        <CardHeader
          title={instance.props.title as string | undefined}
          subheader={instance.props.subtitle as string | undefined}
        />
      ) : null}
      <CardContent>
        {instance.props.content ? <Typography>{instance.props.content as string}</Typography> : null}
        {slotDefault}
      </CardContent>
      {slotActions ? <Box className="flex gap-2 px-4 pb-4">{slotActions}</Box> : null}
    </Card>
  ),
  TextField: (instance) => (
    <TextField
      label={instance.props.label as string | undefined}
      placeholder={instance.props.placeholder as string | undefined}
      size={instance.props.size as 'small' | 'medium' | undefined}
      required={Boolean(instance.props.required)}
      disabled={Boolean(instance.props.disabled)}
      multiline={Boolean(instance.props.multiline)}
      style={Boolean(instance.props.fullWidth) ? { width: '100%' } : undefined}
    />
  ),
  Typography: (instance, { default: slotDefault }) => (
    <Typography
      variant={instance.props.variant as never}
      color={instance.props.color as never}
      align={instance.props.align as React.ComponentProps<typeof Typography>['align']}
    >
      {(instance.props.children as React.ReactNode) ?? slotDefault ?? instance.metadata.name}
    </Typography>
  ),
  Box: (instance, { default: slotDefault }) => (
    <Box
      p={typeof instance.props.padding === 'number' ? instance.props.padding : 2}
      m={typeof instance.props.margin === 'number' ? instance.props.margin : 0}
      backgroundColor={instance.props.backgroundColor as string | undefined}
      borderRadius={typeof instance.props.borderRadius === 'number' ? instance.props.borderRadius : 0}
      style={{
        display: (instance.props.display as string | undefined) ?? 'block',
        flexDirection: instance.props.flexDirection as React.CSSProperties['flexDirection'],
        justifyContent: instance.props.justifyContent as React.CSSProperties['justifyContent'],
        alignItems: instance.props.alignItems as React.CSSProperties['alignItems'],
        minHeight: 64,
        border: '1px dashed #d1d5db',
      }}
    >
      {slotDefault}
    </Box>
  ),
};

export function getRegisteredRenderContractNames(): ReadonlySet<string> {
  return new Set(Object.keys(COMPONENT_RENDER_MAP));
}

export interface ComponentRendererProps {
  readonly document: BuilderDocument;
  readonly nodeId: NodeId;
  readonly selectedNodeId?: string | null;
  readonly onSelect?: (nodeId: string) => void;
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
    outline: isSelected ? '2px solid #1976d2' : '1px solid transparent',
    outlineOffset: '2px',
    cursor: 'pointer',
    borderRadius: 8,
  };
}

function renderInstance(
  instance: ComponentInstance,
  slots: SlotBag,
): React.ReactNode {
  const renderFn = COMPONENT_RENDER_MAP[instance.contractName];
  if (renderFn) {
    return renderFn(instance, slots);
  }

  return (
    <Typography color="danger">
      Unknown component contract: {instance.contractName}
    </Typography>
  );
}

export const ComponentRenderer: React.FC<ComponentRendererProps> = ({
  document,
  nodeId,
  selectedNodeId,
  onSelect,
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
        }}
      >
        {renderInstance(instance, { default: slotDefault, actions: slotActions })}

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
              borderColor: isActive ? '#1976d2' : '#cbd5e1',
              backgroundColor: isActive ? 'rgba(25,118,210,0.12)' : 'rgba(148,163,184,0.08)',
              fontSize: '0.75rem',
              color: '#334155',
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
