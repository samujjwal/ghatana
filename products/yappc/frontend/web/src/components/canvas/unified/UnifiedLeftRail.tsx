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
} from '@ghatana/design-system';
import { Drawer } from '@ghatana/design-system';
import { useAtom } from 'jotai';
import {
  leftPanelOpenAtom,
  uiAtom,
} from '../../../state/atoms/unifiedCanvasAtom';
import { usePanelRegistry } from './panel-registry';
import { AssetsPanel } from './panels';
import type { RailContext, RailPanelProps, RailTabId } from './UnifiedLeftRail.types';

const FALLBACK_TAB: RailTabId = 'assets';

function isRailTabId(value: string): value is RailTabId {
  return [
    'assets',
    'layers',
    'components',
    'infrastructure',
    'history',
    'files',
    'data',
    'ai',
    'favorites',
    'team',
  ].includes(value);
}

interface UnifiedLeftRailProps {
  /** Current canvas context */
  context: RailContext;
  /** Canvas state for layer panel */
  nodes?: unknown[];
  selectedNodeIds?: string[];
  hoveredNodeId?: string | null;
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
  hoveredNodeId = null,
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
  const registry = usePanelRegistry();
  const visiblePanels = registry.getVisiblePanels(context);

  // Debug: Log panel registry state
  React.useEffect(() => {
    console.log('[UnifiedLeftRail] Context:', context);
    console.log('[UnifiedLeftRail] Visible panels:', visiblePanels);
  }, [context, visiblePanels]);

  // Auto-select first panel if current tab is not visible
  const validPanelIds = visiblePanels.map((p) => p.id);
  const storedTab = isRailTabId(ui.leftPanelTab) ? ui.leftPanelTab : FALLBACK_TAB;
  const currentTab = validPanelIds.includes(storedTab)
    ? storedTab
    : validPanelIds[0] || FALLBACK_TAB;

  const [activeTab, setActiveTab] = useState<RailTabId>(currentTab);

  // If no panels are visible, show fallback message with AssetsPanel
  if (visiblePanels.length === 0) {
    const fallbackPanelProps: RailPanelProps = {
      context,
      nodes,
      selectedNodeIds,
      hoveredNodeId,
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
          <Box className="w-[48px] border-border dark:border-border border-r" />
        )}
        <Drawer
          anchor="left"
          open={panelOpen}
          onClose={() => setPanelOpen(false)}
          className="w-[320px] shrink-0 w-[320px] relative h-full"
        >
          <Box
            className="flex flex-col h-full"
          >
            <Box className="p-4 border-border dark:border-border border-b" >
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
    if (!isRailTabId(newValue)) {
      return;
    }

    setActiveTab(newValue);
    setUI({ ...ui, leftPanelTab: 'history' });
  };

  // Build panel props
  const panelProps: RailPanelProps = useMemo(
    () => ({
      context,
      nodes,
      selectedNodeIds,
      hoveredNodeId,
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
          className="flex flex-col items-center py-2 gap-2 w-[48px] border-r border-border dark:border-border bg-white dark:bg-surface"
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
        anchor="left"
        open={panelOpen}
        onClose={() => setPanelOpen(false)}
        className="w-[320px] shrink-0 w-[320px] relative h-full border-r border-border dark:border-border"
      >
        <Box className="flex flex-col h-full">
          {/* Header */}
          <Box
            className="flex items-center justify-between p-4 border-border dark:border-border border-b" >
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
              className="min-h-[40px] border-border dark:border-border border-b" >
              {visiblePanels.map((panel) => (
                <Tab
                  key={panel.id}
                  label={panel.label}
                  value={panel.id}
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
