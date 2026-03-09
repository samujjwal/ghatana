/**
 * Feature 1.4 Integration Example
 *
 * Demonstrates how to integrate document management UI components
 * (TemplateLibraryDialog, VersionComparisonModal, AutosaveIndicator)
 * into a canvas application.
 *
 * This example shows:
 * - Template library management
 * - Version comparison and restoration
 * - Autosave coordination with UI feedback
 * - Keyboard shortcuts for undo/redo
 */

import { FolderOpen as TemplateIcon, Compare as CompareIcon, Undo2 as UndoIcon, Redo2 as RedoIcon } from 'lucide-react';
import { Box, AppBar, Toolbar, Typography, Button, Stack, IconButton } from '@ghatana/ui';

// Import canvas utilities and UI components
import {
  TemplateLibraryDialog,
  VersionComparisonModal,
  AutosaveIndicator,
  createHistoryManager,
  addHistory,
  undo,
  redo,
  canUndo,
  canRedo,
  createVersion,
  diffVersions,
  createTemplate,
  updateTemplate,
  filterTemplates,
  createAutosaveState,
  shouldAutosave,
  markDirty,
  markSaved,
  type HistoryState,
  type DocumentVersion,
  type DocumentTemplate,
  type AutosaveState,
} from '@ghatana/canvas';
import React, { useState, useEffect, useCallback } from 'react';

/**
 * Example canvas state type
 * Replace with your actual canvas document type
 */
interface ExampleCanvasState {
  nodes: Array<{
    id: string;
    type: string;
    position: { x: number; y: number };
    data: unknown;
  }>;
  edges: Array<{
    id: string;
    source: string;
    target: string;
    type?: string;
  }>;
}

/**
 * Document Management Integration Example Component
 */
export function DocumentManagementExample() {
  // Canvas state
  const [canvasState, setCanvasState] = useState<ExampleCanvasState>({
    nodes: [],
    edges: [],
  });

  // History management
  const [historyState, setHistoryState] = useState<
    HistoryState<ExampleCanvasState>
  >(() => createHistoryManager<ExampleCanvasState>());

  // Version management
  const [versions, setVersions] = useState<
    DocumentVersion<ExampleCanvasState>[]
  >([]);
  const [nextVersionNumber, setNextVersionNumber] = useState(1);

  // Template library
  const [templates, setTemplates] = useState<
    DocumentTemplate<ExampleCanvasState>[]
  >([]);

  // Autosave state
  const [autosaveState, setAutosaveState] = useState<AutosaveState>(
    () => createAutosaveState({ enabled: true, interval: 5000 }) // 5 second interval
  );

  // UI dialog states
  const [showTemplateLibrary, setShowTemplateLibrary] = useState(false);
  const [showVersionComparison, setShowVersionComparison] = useState(false);

  // Handle canvas state changes
  const updateCanvasState = useCallback(
    (newState: ExampleCanvasState) => {
      setCanvasState((prevState) => {
        // Add history entry
        const newHistory = addHistory(historyState, {
          before: prevState,
          after: newState,
        });
        setHistoryState(newHistory);

        // Mark as dirty for autosave
        setAutosaveState((prev) => markDirty(prev));

        return newState;
      });
    },
    [historyState]
  );

  // Undo/Redo handlers
  const handleUndo = useCallback(() => {
    if (canUndo(historyState)) {
      const { state: newHistory, value: previousState } = undo(historyState);
      setHistoryState(newHistory);
      if (previousState) {
        setCanvasState(previousState);
      }
    }
  }, [historyState]);

  const handleRedo = useCallback(() => {
    if (canRedo(historyState)) {
      const { state: newHistory, value: nextState } = redo(historyState);
      setHistoryState(newHistory);
      if (nextState) {
        setCanvasState(nextState);
      }
    }
  }, [historyState]);

  // Keyboard shortcuts
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      const isMac = navigator.platform.toUpperCase().indexOf('MAC') >= 0;
      const modifier = isMac ? e.metaKey : e.ctrlKey;

      if (modifier && e.key === 'z' && !e.shiftKey) {
        e.preventDefault();
        handleUndo();
      } else if (modifier && (e.key === 'Z' || (e.key === 'z' && e.shiftKey))) {
        e.preventDefault();
        handleRedo();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [handleUndo, handleRedo]);

  // Autosave coordination
  useEffect(() => {
    const intervalId = setInterval(() => {
      if (shouldAutosave(autosaveState, Date.now())) {
        // Trigger save operation
        setAutosaveState((prev) => markSaved(prev, Date.now()));

        // Create a version snapshot
        const version = createVersion<ExampleCanvasState>({
          documentId: 'example-doc',
          version: nextVersionNumber,
          state: canvasState,
          timestamp: Date.now(),
          description: 'Autosave snapshot',
        });
        setVersions((prev) => [...prev, version]);
        setNextVersionNumber((prev) => prev + 1);
      }
    }, autosaveState.interval);

    return () => clearInterval(intervalId);
  }, [autosaveState, canvasState, nextVersionNumber]);

  // Template library handlers
  const handleSaveAsTemplate = useCallback(
    (name: string, description: string, category: string, tags: string[]) => {
      const template = createTemplate({
        name,
        description,
        category,
        tags,
        state: canvasState,
      });
      setTemplates((prev) => [...prev, template]);
    },
    [canvasState]
  );

  const handleLoadTemplate = useCallback(
    (template: DocumentTemplate<ExampleCanvasState>) => {
      updateCanvasState(template.state);
    },
    [updateCanvasState]
  );

  const handleDeleteTemplate = useCallback((templateId: string) => {
    setTemplates((prev) => prev.filter((t) => t.id !== templateId));
  }, []);

  // Version comparison handlers
  const handleCompareVersions = useCallback(
    (versionId1: string, versionId2: string) => {
      const version1 = versions.find((v) => v.id === versionId1);
      const version2 = versions.find((v) => v.id === versionId2);
      if (version1 && version2) {
        return diffVersions(version1.state, version2.state);
      }
      return [];
    },
    [versions]
  );

  const handleRestoreVersion = useCallback(
    (versionId: string) => {
      const version = versions.find((v) => v.id === versionId);
      if (version) {
        updateCanvasState(version.state);
      }
    },
    [versions, updateCanvasState]
  );

  // Example: Add a node to demonstrate functionality
  const handleAddNode = useCallback(() => {
    const newNode = {
      id: `node-${Date.now()}`,
      type: 'default',
      position: {
        x: Math.random() * 500,
        y: Math.random() * 500,
      },
      data: { label: `Node ${canvasState.nodes.length + 1}` },
    };

    updateCanvasState({
      ...canvasState,
      nodes: [...canvasState.nodes, newNode],
    });
  }, [canvasState, updateCanvasState]);

  return (
    <Box className="h-screen flex flex-col">
      {/* App Bar with Document Management Controls */}
      <AppBar position="static" color="default" variant="raised">
        <Toolbar>
          <Typography as="h6" component="div" className="grow">
            Document Management Example
          </Typography>

          <Stack direction="row" spacing={1} alignItems="center">
            {/* Undo/Redo Controls */}
            <IconButton
              onClick={handleUndo}
              disabled={!canUndo(historyState)}
              title="Undo (Ctrl/Cmd+Z)"
              data-testid="undo-btn"
            >
              <UndoIcon />
            </IconButton>
            <IconButton
              onClick={handleRedo}
              disabled={!canRedo(historyState)}
              title="Redo (Ctrl/Cmd+Shift+Z)"
              data-testid="redo-btn"
            >
              <RedoIcon />
            </IconButton>

            {/* Template Library */}
            <Button
              startIcon={<TemplateIcon />}
              onClick={() => setShowTemplateLibrary(true)}
              data-testid="open-template-library-btn"
            >
              Templates
            </Button>

            {/* Version Comparison */}
            <Button
              startIcon={<CompareIcon />}
              onClick={() => setShowVersionComparison(true)}
              disabled={versions.length < 2}
              data-testid="open-version-comparison-btn"
            >
              Compare ({versions.length})
            </Button>

            {/* Autosave Indicator */}
            <AutosaveIndicator autosaveState={autosaveState} showToast={true} />
          </Stack>
        </Toolbar>
      </AppBar>

      {/* Main Content Area */}
      <Box className="grow p-6 bg-gray-50 dark:bg-gray-950">
        <Stack spacing={2}>
          <Typography as="h6">Canvas Content</Typography>
          <Typography as="p" className="text-sm" color="text.secondary">
            Nodes: {canvasState.nodes.length}, Edges: {canvasState.edges.length}
          </Typography>

          {/* Demo Controls */}
          <Button
            variant="solid"
            onClick={handleAddNode}
            data-testid="add-node-btn"
          >
            Add Node (Demo)
          </Button>

          {/* Canvas visualization area */}
          <Box
            className="rounded p-4 border border-gray-200 dark:border-gray-700 min-h-[400px] bg-white dark:bg-gray-900"
            data-testid="rf__wrapper"
          >
            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
              Canvas content would render here
            </Typography>
            {canvasState.nodes.map((node) => (
              <Box
                key={node.id}
                className="p-2 m-2 border border-gray-200 dark:border-gray-700"
              >
                {node.data.label}
              </Box>
            ))}
          </Box>
        </Stack>
      </Box>

      {/* Template Library Dialog */}
      <TemplateLibraryDialog
        open={showTemplateLibrary}
        onClose={() => setShowTemplateLibrary(false)}
        templates={templates}
        onLoadTemplate={handleLoadTemplate}
        onSaveAsTemplate={handleSaveAsTemplate}
        onDeleteTemplate={handleDeleteTemplate}
        currentState={canvasState}
      />

      {/* Version Comparison Modal */}
      <VersionComparisonModal
        open={showVersionComparison}
        onClose={() => setShowVersionComparison(false)}
        versions={versions}
        onCompareVersions={handleCompareVersions}
        onRestoreVersion={handleRestoreVersion}
        currentVersionId={versions[versions.length - 1]?.id}
      />
    </Box>
  );
}

export default DocumentManagementExample;
