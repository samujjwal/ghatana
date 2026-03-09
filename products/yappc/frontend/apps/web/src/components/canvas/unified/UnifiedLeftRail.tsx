/**
 * UnifiedLeftRail - Context-Aware Collapsible Left Panel
 *
 * @doc.type component
 * @doc.purpose Dynamic left rail with mode/role/phase-aware panel visibility
 * @doc.layer components
 * @doc.pattern Registry + Plugin Architecture
 */

import React, { useState, useMemo } from 'react';
import {
  Box,
  IconButton,
  Tabs,
  Tab,
  Typography,
} from '@ghatana/ui';
import { Drawer } from '@ghatana/ui';
import { useAtom } from 'jotai';
import {
  leftPanelOpenAtom,
  uiAtom,
} from '../../../state/atoms/unifiedCanvasAtom';
import { usePanelRegistry } from './panel-registry';
import { AssetsPanel } from './panels';
import type { RailContext, RailPanelProps } from './UnifiedLeftRail.types';

interface UnifiedLeftRailProps {
  /** Current canvas context */
  context: RailContext;
  /** Canvas state for layer panel */
  nodes?: unknown[];
  selectedNodeIds?: string[];
  /** Event handlers */
  onInsertNode?: (nodeData: unknown, position?: { x: number; y: number }) => void;
  onSelectNode?: (nodeId: string) => void;
  onUpdateNode?: (nodeId: string, updates: unknown) => void;
  onDeleteNode?: (nodeId: string) => void;
  onToggleVisibility?: (nodeId: string) => void;
  onToggleLock?: (nodeId: string) => void;
}

export function UnifiedLeftRail({
  context,
  nodes = [],
  selectedNodeIds = [],
  onInsertNode,
  onSelectNode,
  onUpdateNode,
  onDeleteNode,
  onToggleVisibility,
  onToggleLock,
}: UnifiedLeftRailProps) {
  const [panelOpen, setPanelOpen] = useAtom(leftPanelOpenAtom);
  const [ui, setUI] = useAtom(uiAtom);

  // Get visible panels based on context
  const visiblePanels = usePanelRegistry(context);

  // Debug: Log panel registry state
  React.useEffect(() => {
    console.log('[UnifiedLeftRail] Context:', context);
    console.log('[UnifiedLeftRail] Visible panels:', visiblePanels);
  }, [context, visiblePanels]);

  // Auto-select first panel if current tab is not visible
  const validPanelIds = visiblePanels.map((p) => p.id);
  const currentTab = validPanelIds.includes(ui.leftPanelTab as unknown)
    ? ui.leftPanelTab
    : validPanelIds[0] || 'assets';

  const [activeTab, setActiveTab] = useState(currentTab);

  // If no panels are visible, show fallback message with AssetsPanel
  if (visiblePanels.length === 0) {
    const fallbackPanelProps: RailPanelProps = {
      context,
      nodes,
      selectedNodeIds,
      onInsertNode,
      onSelectNode,
      onUpdateNode,
      onDeleteNode,
      onToggleVisibility,
      onToggleLock,
    };

    return (
      <>
        {!panelOpen && (
          <Box className="w-[48px] border-gray-200 dark:border-gray-700 border-r" />
        )}
        <Drawer
          variant="persistent"
          anchor="left"
          open={panelOpen}
          className="w-[320px] shrink-0 w-[320px] relative h-full"
        >
          <Box
            className="flex flex-col h-full"
          >
            <Box className="p-4 border-gray-200 dark:border-gray-700 border-b" >
              <Typography variant="h6">Assets (Fallback)</Typography>
              <Typography variant="caption" color="text.secondary">
                No panels matched context. Context: mode={context.mode}, role=
                {context.role}, phase={context.phase}
              </Typography>
            </Box>
            <Box className="flex-1 overflow-auto">
              <AssetsPanel {...fallbackPanelProps} />
            </Box>
          </Box>
        </Drawer>
      </>
    );
  }

  const handleTabChange = (_event: React.SyntheticEvent, newValue: string) => {
    setActiveTab(newValue);
    setUI({ ...ui, leftPanelTab: newValue });
  };

  // Build panel props
  const panelProps: RailPanelProps = useMemo(
    () => ({
      context,
      nodes,
      selectedNodeIds,
      onInsertNode,
      onSelectNode,
      onUpdateNode,
      onDeleteNode,
      onToggleVisibility,
      onToggleLock,
    }),
    [
      context,
      nodes,
      selectedNodeIds,
      onInsertNode,
      onSelectNode,
      onUpdateNode,
      onDeleteNode,
      onToggleVisibility,
      onToggleLock,
    ]
  );

  // Find active panel definition
  const activePanel = visiblePanels.find((p) => p.id === activeTab);

  return (
    <>
      {/* Collapsed Rail - Icon buttons for each visible panel */}
      {!panelOpen && (
        <Box
          className="flex flex-col items-center py-2 gap-2 w-[48px] border-r border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900"
        >
          {visiblePanels.slice(0, 6).map((panel) => (
            <IconButton
              key={panel.id}
              onClick={() => {
                setPanelOpen(true);
                setActiveTab(panel.id);
              }}
              title={panel.label}
              size="small"
            >
              {panel.icon}
            </IconButton>
          ))}
        </Box>
      )}

      {/* Expanded Panel */}
      <Drawer
        variant="persistent"
        anchor="left"
        open={panelOpen}
        className="w-[320px] shrink-0 w-[320px] relative h-full border-r border-gray-200 dark:border-gray-700"
      >
        <Box className="flex flex-col h-full">
          {/* Header */}
          <Box
            className="flex items-center justify-between p-4 border-gray-200 dark:border-gray-700 border-b" >
            <Typography variant="h6">
              {activePanel?.label || 'Canvas Tools'}
            </Typography>
            <IconButton size="small" onClick={() => setPanelOpen(false)}>
              ◀
            </IconButton>
          </Box>

          {/* Dynamic Tabs */}
          {visiblePanels.length > 1 && (
            <Tabs
              value={activeTab}
              onChange={handleTabChange}
              variant="scrollable"
              scrollButtons="auto"
              className="min-h-[40px] border-gray-200 dark:border-gray-700 border-b" >
              {visiblePanels.map((panel) => (
                <Tab
                  key={panel.id}
                  label={panel.label}
                  value={panel.id}
                  icon={<span>{panel.icon}</span>}
                  iconPosition="start"
                  className="py-2 normal-case min-h-[40px]"
                />
              ))}
            </Tabs>
          )}

          {/* Dynamic Panel Content */}
          <Box
            className="flex-1 overflow-auto flex flex-col"
          >
            {activePanel ? (
              React.createElement(activePanel.component, panelProps)
            ) : (
              <Box className="p-4">
                <Typography variant="body2" color="text.secondary">
                  No panel available for current context
                </Typography>
              </Box>
            )}
          </Box>
        </Box>
      </Drawer>
    </>
  );
}
