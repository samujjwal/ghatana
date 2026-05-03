import {
  Box,
  Button,
  Card,
  CardContent,
  CardHeader,
  TextField,
  Typography,
} from '@ghatana/design-system';
import React from 'react';

import { getContractByName } from './registry';

import type { BuilderDocument, ComponentInstance, NodeId } from '@ghatana/ui-builder';
type TypographyAlign = React.ComponentProps<typeof Typography>['align'];

export interface ComponentRendererProps {
  readonly document: BuilderDocument;
  readonly nodeId: NodeId;
  readonly selectedNodeId?: string | null;
  readonly onSelect?: (nodeId: string) => void;
}

function renderSlot(
  document: BuilderDocument,
  slotIds: readonly NodeId[] | undefined,
  selectedNodeId: string | null | undefined,
  onSelect: ((nodeId: string) => void) | undefined,
): React.ReactNode {
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
    />
  ));
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
  document: BuilderDocument,
  selectedNodeId: string | null | undefined,
  onSelect: ((nodeId: string) => void) | undefined,
): React.ReactNode {
  const slotDefault = renderSlot(
    document,
    instance.slots.default,
    selectedNodeId,
    onSelect,
  );
  const slotActions = renderSlot(
    document,
    instance.slots.actions,
    selectedNodeId,
    onSelect,
  );

  switch (instance.contractName) {
    case 'Button':
      return (
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
          size={instance.props.size === 'small' ? 'sm' : instance.props.size === 'large' ? 'lg' : 'md'}
          disabled={Boolean(instance.props.disabled)}
          fullWidth={Boolean(instance.props.fullWidth)}
        >
          {(instance.props.children as React.ReactNode) ?? instance.metadata.name ?? 'Button'}
        </Button>
      );
    case 'Card':
      return (
        <Card elevation={typeof instance.props.elevation === 'number' ? instance.props.elevation : 2}>
          {instance.props.title || instance.props.subtitle ? (
            <CardHeader
              title={instance.props.title as string | undefined}
              subheader={instance.props.subtitle as string | undefined}
            />
          ) : null}
          <CardContent>
            {instance.props.content ? (
              <Typography>{instance.props.content as string}</Typography>
            ) : null}
            {slotDefault}
          </CardContent>
          {slotActions ? <Box className="flex gap-2 px-4 pb-4">{slotActions}</Box> : null}
        </Card>
      );
    case 'TextField':
      return (
        <TextField
          label={instance.props.label as string | undefined}
          placeholder={instance.props.placeholder as string | undefined}
          size={instance.props.size === 'small' ? 'sm' : 'md'}
          required={Boolean(instance.props.required)}
          disabled={Boolean(instance.props.disabled)}
          style={Boolean(instance.props.fullWidth) ? { width: '100%' } : undefined}
        />
      );
    case 'Typography':
      return (
        <Typography
          variant={instance.props.variant as never}
          color={instance.props.color as never}
          align={mapTextAlign(instance.props.align as string | undefined)}
        >
          {(instance.props.children as React.ReactNode) ?? instance.metadata.name}
        </Typography>
      );
    case 'Box':
      return (
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
      );
    default:
      return (
        <Typography color="danger">
          Unknown component contract: {instance.contractName}
        </Typography>
      );
  }
}

export const ComponentRenderer: React.FC<ComponentRendererProps> = ({
  document,
  nodeId,
  selectedNodeId,
  onSelect,
}) => {
  const instance = document.nodes.get(nodeId);
  if (!instance) {
    return null;
  }

  const isSelected = selectedNodeId === nodeId;
  const contract = getContractByName(instance.contractName);
  const testId = contract?.name
    ? `page-${contract.name.toLowerCase()}`
    : `page-${instance.contractName.toLowerCase()}`;

  return (
    <div
      style={getSelectionStyle(isSelected)}
      data-testid={testId}
      data-builder-node-id={nodeId}
      onClick={(event) => {
        event.stopPropagation();
        onSelect?.(nodeId);
      }}
    >
      {renderInstance(instance, document, selectedNodeId, onSelect)}
    </div>
  );
};
function mapTextAlign(
  align: string | undefined,
): TypographyAlign {
  switch (align) {
    case 'left':
      return 'start';
    case 'right':
      return 'end';
    case 'center':
    case 'justify':
      return align;
    default:
      return undefined;
  }
}
