import { Plus as AddIcon, Trash2 as DeleteIcon, Pencil as EditIcon, AlertTriangle } from 'lucide-react';
import {
  Box,
  Stack,
  Typography,
  IconButton,
  Button,
  Surface as Paper,
} from '@ghatana/design-system';
import { Drawer } from '@ghatana/design-system';
import React, { useState, useCallback, useEffect, useMemo } from 'react';

import { ComponentRenderer } from './ComponentRenderer';
import { PropertyForm } from './PropertyForm';
import { getDefaultComponentData } from './schemas';
import {
  componentDataToBuilderDocument,
  componentDataToInsertableInstance,
  builderDocumentToComponentData,
  isBuilderDocument,
} from './builder-document-adapter';
import {
  getBuilderPalette,
  getContractMap,
  getDefaultSlotName,
  isContainerContract,
  toLegacyComponentType,
  normalizeContractName,
  type LegacyComponentType,
} from './registry';

import type { ComponentData } from './schemas';
import {
  deleteNode,
  insertNode,
  moveNode,
  updateNodeProps,
  validateDocument,
} from '@ghatana/ui-builder';
import type { BuilderDocument, NodeId, ValidationResult } from '@ghatana/ui-builder';

/**
 * @doc.type component
 * @doc.purpose Registry-driven YAPPC page designer using shared BuilderDocument contract from @ghatana/ui-builder
 * @doc.layer product
 * @doc.pattern Widget
 */
interface PageDesignerProps {
  readonly initialComponents?: ComponentData[] | BuilderDocument;
  readonly onComponentsChange?: (components: ComponentData[]) => void;
  readonly onDocumentChange?: (document: BuilderDocument, validation: ValidationResult) => void;
}

export const PageDesigner: React.FC<PageDesignerProps> = ({
  initialComponents = [],
  onComponentsChange,
  onDocumentChange,
}) => {
  const contracts = useMemo(() => getContractMap(), []);
  const palette = useMemo(() => getBuilderPalette(), []);
  const [document, setDocument] = useState<BuilderDocument>(() => {
    if (isBuilderDocument(initialComponents)) {
      return initialComponents;
    }
    return componentDataToBuilderDocument(initialComponents as ComponentData[]);
  });
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const validation = useMemo(() => validateDocument(document, contracts), [contracts, document]);

  useEffect(() => {
    onDocumentChange?.(document, validation);
  }, [document, onDocumentChange, validation]);

  const selectedInstance = selectedId ? document.nodes.get(selectedId as NodeId) : undefined;

  const publishDocument = useCallback(
    (nextDocument: BuilderDocument) => {
      setDocument(nextDocument);
      onComponentsChange?.(builderDocumentToComponentData(nextDocument));
    },
    [onComponentsChange],
  );

  const resolveInsertionTarget = useCallback((): {
    readonly parentId?: NodeId;
    readonly slotName?: string;
  } => {
    if (!selectedInstance || !isContainerContract(selectedInstance.contractName)) {
      return {};
    }

    const slotName = getDefaultSlotName(selectedInstance.contractName);
    if (!slotName) {
      return {};
    }

    return {
      parentId: selectedInstance.id,
      slotName,
    };
  }, [selectedInstance]);

  const handleAddComponent = useCallback(
    (contractOrType: string) => {
      const contractName = normalizeContractName(contractOrType);
      const legacyType = toLegacyComponentType(contractName) as LegacyComponentType;
      const newComponent = getDefaultComponentData(legacyType) as ComponentData;
      let insertedNodeId: string | null = null;
      const target = resolveInsertionTarget();

      const nextDocument = insertNode(
        document,
        componentDataToInsertableInstance(newComponent),
        target.parentId,
        target.slotName,
        {
          onNodeInserted: ({ nodeId }) => {
            insertedNodeId = nodeId;
          },
        },
      );

      publishDocument(nextDocument);
      setSelectedId(insertedNodeId);
    },
    [document, publishDocument, resolveInsertionTarget],
  );

  const handlePaletteDragStart = useCallback(
    (event: React.DragEvent<HTMLButtonElement>, contractName: string) => {
      event.dataTransfer.setData('application/x-page-component', contractName);
      event.dataTransfer.effectAllowed = 'copy';
    },
    [],
  );

  const handleDesignAreaDrop = useCallback(
    (event: React.DragEvent<HTMLDivElement>) => {
      event.preventDefault();
      const contractName = event.dataTransfer.getData('application/x-page-component');
      if (contractName) {
        handleAddComponent(contractName);
      }
    },
    [handleAddComponent],
  );

  const handleDeleteComponent = useCallback(() => {
    if (!selectedId) {
      return;
    }

    const nextDocument = deleteNode(document, selectedId as NodeId);
    publishDocument(nextDocument);
    setSelectedId(null);
  }, [document, publishDocument, selectedId]);

  const handleUpdateComponent = useCallback(
    (payload: { readonly props: Record<string, unknown>; readonly name?: string }) => {
      if (!selectedInstance) {
        return;
      }

      let nextDocument = updateNodeProps(
        document,
        selectedInstance.id,
        payload.props,
      );

      const currentNode = nextDocument.nodes.get(selectedInstance.id);
      if (currentNode && payload.name !== currentNode.metadata.name) {
        const updatedNode = {
          ...currentNode,
          metadata: {
            ...currentNode.metadata,
            name: payload.name,
          },
        };
        const nextNodes = new Map(nextDocument.nodes);
        nextNodes.set(selectedInstance.id, updatedNode);
        nextDocument = {
          ...nextDocument,
          nodes: nextNodes,
        };
      }

      publishDocument(nextDocument);
      setDrawerOpen(false);
      setEditingId(null);
    },
    [document, publishDocument, selectedInstance],
  );

  const handleNestSelectedIntoParent = useCallback(() => {
    if (!selectedId || !selectedInstance) {
      return;
    }

    const rootTargetId = document.rootNodes.find((nodeId) => {
      if (nodeId === selectedInstance.id) {
        return false;
      }

      const candidate = document.nodes.get(nodeId);
      return candidate ? isContainerContract(candidate.contractName) : false;
    });

    if (!rootTargetId) {
      return;
    }

    const parent = document.nodes.get(rootTargetId);
    const slotName = parent ? getDefaultSlotName(parent.contractName) : undefined;
    if (!slotName) {
      return;
    }

    const nextDocument = moveNode(
      document,
      selectedId as NodeId,
      rootTargetId,
      slotName,
    );
    publishDocument(nextDocument);
  }, [document, publishDocument, selectedId, selectedInstance]);

  const topLevelNodes = useMemo(
    () =>
      document.rootNodes.filter((nodeId) => document.nodes.has(nodeId)),
    [document],
  );

  const editingInstance = editingId
    ? document.nodes.get(editingId as NodeId)
    : undefined;

  return (
    <Box className="flex h-full relative" data-testid="page-designer">
      <Paper elevation={2} className="w-[240px] overflow-y-auto rounded-none border-r p-4">
        <Typography variant="h6" gutterBottom>
          Registry Components
        </Typography>
        <Stack spacing={1}>
          {palette.map((entry) => (
            <Button
              key={entry.id}
              variant="outline"
              onClick={() => handleAddComponent(entry.name)}
              fullWidth
              className="justify-start"
              title={entry.tooltip}
              data-testid={`page-component-${entry.name.toLowerCase()}`}
              draggable
              onDragStart={(event) => handlePaletteDragStart(event, entry.name)}
            >
              {entry.displayName}
            </Button>
          ))}
        </Stack>
      </Paper>

      <Box
        className="flex-1 overflow-y-auto p-6"
        style={{ backgroundColor: 'var(--bg-surface)' }}
        data-testid="page-design-area"
        onDragOver={(event) => event.preventDefault()}
        onDrop={handleDesignAreaDrop}
      >
        {validation.errors.length > 0 || validation.warnings.length > 0 ? (
          <Paper
            elevation={1}
            className="mb-4 flex items-start gap-3 border border-amber-300 bg-amber-50 p-3"
          >
            <AlertTriangle size={16} />
            <Box>
              <Typography variant="body2">
                {validation.errors.length} error(s), {validation.warnings.length} warning(s)
              </Typography>
              {validation.errors[0] ? (
                <Typography variant="caption" color="danger">
                  {validation.errors[0].message}
                </Typography>
              ) : null}
            </Box>
          </Paper>
        ) : null}

        {selectedInstance ? (
          <Paper elevation={3} className="absolute right-4 top-4 z-10 flex gap-2 p-2">
            <IconButton
              size="small"
              color="primary"
              onClick={() => {
                setEditingId(selectedInstance.id);
                setDrawerOpen(true);
              }}
              title="Edit Properties"
            >
              <EditIcon size={16} />
            </IconButton>
            <IconButton
              size="small"
              color="error"
              onClick={handleDeleteComponent}
              title="Delete"
            >
              <DeleteIcon size={16} />
            </IconButton>
            {document.rootNodes.includes(selectedInstance.id) ? (
              <Button variant="outline" size="small" onClick={handleNestSelectedIntoParent}>
                Nest
              </Button>
            ) : null}
          </Paper>
        ) : null}

        <Paper elevation={1} className="mx-auto min-h-[600px] max-w-[900px] bg-white p-8">
          {topLevelNodes.length === 0 ? (
            <Box
              className="flex h-[400px] items-center justify-center rounded-lg"
              style={{ border: '2px dashed #ccc' }}
            >
              <Stack alignItems="center" spacing={2}>
                <AddIcon className="text-5xl text-gray-500 dark:text-gray-400" />
                <Typography variant="h6" color="text.secondary">
                  Drag components from the registry to start designing
                </Typography>
              </Stack>
            </Box>
          ) : (
            <Stack spacing={2}>
              {topLevelNodes.map((nodeId) => (
                <ComponentRenderer
                  key={nodeId}
                  document={document}
                  nodeId={nodeId}
                  selectedNodeId={selectedId}
                  onSelect={setSelectedId}
                />
              ))}
            </Stack>
          )}
        </Paper>
      </Box>

      <Drawer
        anchor="right"
        open={drawerOpen}
        onClose={() => {
          setDrawerOpen(false);
          setEditingId(null);
        }}
        PaperProps={{
          style: { width: 360 },
        }}
      >
        <Box className="flex items-center justify-between p-4">
          <Typography variant="h6">Registry Properties</Typography>
        </Box>
        {editingInstance ? (
          <PropertyForm
            contractName={editingInstance.contractName}
            instanceName={editingInstance.metadata.name}
            initialProps={editingInstance.props}
            onUpdate={handleUpdateComponent}
            onCancel={() => {
              setDrawerOpen(false);
              setEditingId(null);
            }}
          />
        ) : null}
      </Drawer>
    </Box>
  );
};
