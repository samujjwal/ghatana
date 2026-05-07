/**
 * Unified Left Panel
 * 
 * Combines TaskPanel, ComponentPalette, ArtifactPalette, and PhaseNavigator
 * into a single tabbed interface to eliminate overlap and provide cleaner canvas space.
 * 
 * Tabs:
 * - Tasks: Project lifecycle tasks and guidance
 * - Components: Reusable UI components for canvas
 * - Artifacts: Pre-defined artifact templates organized by lifecycle phase
 * - Phases: Lifecycle phase navigator and progress
 * 
 * @doc.type component
 * @doc.purpose Unified left sidebar with tabs for Tasks, Components, Artifacts, and Phases
 * @doc.layer product
 * @doc.pattern Composite Component
 */

import React, { useState, useCallback } from 'react';
import { Box, Tabs, Tab, Surface as Paper, Typography, IconButton, Tooltip, Button } from '@ghatana/design-system';
import { ClipboardList as Assignment, Component as Widgets, Activity as Timeline, Hammer as Build, History, ListOrdered as ListAlt, Save, Shield as Security, Sparkles as AutoAwesome } from 'lucide-react';

import { CanvasTaskPanel } from './tasks/CanvasTaskPanel';
import { ComponentPalette } from './ComponentPalette';
import { CanvasPhaseNavigator } from './CanvasPhaseNavigator';
import { ArtifactPalette, type ArtifactTemplate } from './workspace/ArtifactPalette';
import { AuditTimeline, type AuditEvent } from './audit/AuditTimeline';
import { GovernancePanel } from './governance/GovernancePanel';
import { GraphVisualizer } from './visualizer/GraphVisualizer';
import { TRANSITIONS } from '../../styles/design-tokens';
import type { CanvasAccessPolicy } from './canvasAccessPolicy';

// ============================================================================
// Types
// ============================================================================

export interface UnifiedLeftPanelProps {
  /** Project ID for task panel */
  projectId: string;
  /** Whether the panel is collapsed */
  collapsed?: boolean;
  /** Callback when collapse state changes */
  onCollapseChange?: (collapsed: boolean) => void;
  /** Component addition handler */
  onAddComponent: (component: object, position: { x: number; y: number }) => void;
  /** Project/phase mutation policy for canvas tools */
  canvasPolicy: CanvasAccessPolicy;
  /** Drag start handler */
  onDragStart?: (template: ArtifactTemplate) => void;
  /** Additional CSS classes */
  className?: string;
  /** Current nodes for visualization */
  nodes?: unknown[];
  /** Current edges for visualization */
  edges?: unknown[];
}

type TabType = 'tasks' | 'components' | 'phases' | 'artifacts' | 'history' | 'activity' | 'governance' | 'visualize';

// ============================================================================
// Component
// ============================================================================

export function UnifiedLeftPanel({
  projectId,
  collapsed: controlledCollapsed,
  onCollapseChange,
  onAddComponent,
  canvasPolicy,
  onDragStart,
  nodes = [],
  edges = [],
  className = '',
}: UnifiedLeftPanelProps) {
  const [activeTab, setActiveTab] = useState<TabType>('tasks');
  const [internalCollapsed, setInternalCollapsed] = useState(false);
  const [panelWidth, setPanelWidth] = useState(320);
  const [isResizing, setIsResizing] = useState(false);

  const isCollapsed = controlledCollapsed ?? internalCollapsed;

  // Resize handlers
  const startResizing = useCallback((mouseDownEvent: React.MouseEvent) => {
    mouseDownEvent.preventDefault();
    setIsResizing(true);
  }, []);

  const stopResizing = useCallback(() => {
    setIsResizing(false);
  }, []);

  const resize = useCallback(
    (mouseMoveEvent: MouseEvent) => {
      if (isResizing) {
        const newWidth = mouseMoveEvent.clientX; // Assuming panel is on left
        if (newWidth > 200 && newWidth < 600) {
          setPanelWidth(newWidth);
        }
      }
    },
    [isResizing]
  );

  React.useEffect(() => {
    window.addEventListener("mousemove", resize);
    window.addEventListener("mouseup", stopResizing);
    return () => {
      window.removeEventListener("mousemove", resize);
      window.removeEventListener("mouseup", stopResizing);
    };
  }, [resize, stopResizing]);

  const handleCollapseChange = useCallback((collapsed: boolean) => {
    if (onCollapseChange) {
      onCollapseChange(collapsed);
    } else {
      setInternalCollapsed(collapsed);
    }
  }, [onCollapseChange]);

  const handleTabChange = useCallback((_event: React.SyntheticEvent, newValue: TabType) => {
    setActiveTab(newValue);
  }, []);

  const mutationLockedNotice = (
    <Box className="m-4 rounded-xl border border-border bg-surface-muted p-4">
      <Typography className="text-sm font-semibold">{canvasPolicy.modeLabel}</Typography>
      <Typography className="mt-2 text-sm text-fg-muted" color="text.secondary">
        {canvasPolicy.readOnlyReason ?? 'Canvas edits are unavailable in this mode.'}
      </Typography>
    </Box>
  );

  // Collapsed state - show only tab icons vertically
  if (isCollapsed) {
    return (
      <Paper
        variant="outlined"
        className={`
          shrink-0 h-full w-[48px] flex flex-col items-center gap-1 py-2 bg-bg-paper border-r border-divider
          ${TRANSITIONS.default}
          ${className}
        `}
      >
        <Tooltip title="Tasks" placement="right">
          <button
            onClick={() => {
              handleCollapseChange(false);
              setActiveTab('tasks');
            }}
            className={`p-2 rounded transition-colors ${activeTab === 'tasks'
              ? 'bg-primary-50 dark:bg-primary-900/20 text-primary-600'
              : 'hover:bg-grey-100 dark:hover:bg-grey-800 text-text-secondary'
              }`}
          >
            <Assignment className="w-5 h-5" />
          </button>
        </Tooltip>
        <Tooltip title="Widgets" placement="right">
          <button
            onClick={() => {
              handleCollapseChange(false);
              setActiveTab('components');
            }}
            className={`p-2 rounded transition-colors ${activeTab === 'components'
              ? 'bg-primary-50 dark:bg-primary-900/20 text-primary-600'
              : 'hover:bg-grey-100 dark:hover:bg-grey-800 text-text-secondary'
              }`}
          >
            <Widgets className="w-5 h-5" />
          </button>
        </Tooltip>
        <Tooltip title="Build" placement="right">
          <button
            onClick={() => {
              handleCollapseChange(false);
              setActiveTab('artifacts');
            }}
            className={`p-2 rounded transition-colors ${activeTab === 'artifacts'
              ? 'bg-primary-50 dark:bg-primary-900/20 text-primary-600'
              : 'hover:bg-grey-100 dark:hover:bg-grey-800 text-text-secondary'
              }`}
          >
            <Build className="w-5 h-5" />
          </button>
        </Tooltip>
        <Tooltip title="Timeline" placement="right">
          <button
            onClick={() => {
              handleCollapseChange(false);
              setActiveTab('phases');
            }}
            className={`p-2 rounded transition-colors ${activeTab === 'phases'
              ? 'bg-primary-50 dark:bg-primary-900/20 text-primary-600'
              : 'hover:bg-grey-100 dark:hover:bg-grey-800 text-text-secondary'
              }`}
          >
            <Timeline className="w-5 h-5" />
          </button>
        </Tooltip>
        <Tooltip title="History" placement="right">
          <button
            onClick={() => {
              handleCollapseChange(false);
              setActiveTab('history');
            }}
            className={`p-2 rounded transition-colors ${activeTab === 'history'
              ? 'bg-primary-50 dark:bg-primary-900/20 text-primary-600'
              : 'hover:bg-grey-100 dark:hover:bg-grey-800 text-text-secondary'
              }`}
          >
            <History className="w-5 h-5" />
          </button>
        </Tooltip>
        <Tooltip title="Activity" placement="right">
          <button
            onClick={() => {
              handleCollapseChange(false);
              setActiveTab('activity');
            }}
            className={`p-2 rounded transition-colors ${activeTab === 'activity'
              ? 'bg-primary-50 dark:bg-primary-900/20 text-primary-600'
              : 'hover:bg-grey-100 dark:hover:bg-grey-800 text-text-secondary'
              }`}
          >
            <ListAlt className="w-5 h-5" />
          </button>
        </Tooltip>
        <Tooltip title="Governance" placement="right">
          <button
            onClick={() => {
              handleCollapseChange(false);
              setActiveTab('governance');
            }}
            className={`p-2 rounded transition-colors ${activeTab === 'governance'
              ? 'bg-primary-50 dark:bg-primary-900/20 text-primary-600'
              : 'hover:bg-grey-100 dark:hover:bg-grey-800 text-text-secondary'
              }`}
          >
            <Security className="w-5 h-5" />
          </button>
        </Tooltip>
        <Tooltip title="Visualize" placement="right">
          <button
            onClick={() => {
              handleCollapseChange(false);
              setActiveTab('visualize');
            }}
            className={`p-2 rounded transition-colors ${activeTab === 'visualize'
              ? 'bg-primary-50 dark:bg-primary-900/20 text-primary-600'
              : 'hover:bg-grey-100 dark:hover:bg-grey-800 text-text-secondary'
              }`}
          >
            <AutoAwesome className="w-5 h-5" />
          </button>
        </Tooltip>
      </Paper>
    );
  }

  // Expanded state - show tabs and content
  return (
    <Paper
      elevation={4}
      className={`
        flex flex-col border-r relative shrink-0 h-full overflow-hidden
        ${!isResizing && TRANSITIONS.default}
        ${className}
      `}
    >
      {/* Resize Handle */}
      <Box
        onMouseDown={startResizing}
        className="absolute right-0 top-0 bottom-0 w-[4px] cursor-col-resize z-10"
        style={{ transition: 'background-color 0.2s' }}
      />

      {/* Header with Collapse Button */}
      <Box
        className="flex items-center justify-between px-5 py-2"
      >
        <Typography className="text-lg font-medium tracking-[-0.02em]" fontWeight="700" color="text.primary">
          Canvas Tools
        </Typography>
        <Tooltip title="Collapse panel">
          <IconButton
            size="sm"
            onClick={() => handleCollapseChange(true)}
            className="p-1 rounded-md hover:bg-surface-muted"
          >
            <svg className="h-[18px] w-[18px]" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
              <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z" />
            </svg>
          </IconButton>
        </Tooltip>
      </Box>

      {/* Tab Navigation */}
      <Box className="px-4 pt-4 pb-2">
        <Tabs
          value={activeTab}
          onChange={handleTabChange}
          variant="underline"
          className="min-h-[36px] h-full rounded-lg border border-solid border-info-border min-w-0 px-3 py-1 text-info-color" >
          <Tab
            value="tasks"
            icon={<Assignment className="text-base" />}
            label="Tasks"
          />
          <Tab
            value="components"
            icon={<Widgets className="text-base" />}
            label="Widgets"
          />
          <Tab
            value="artifacts"
            icon={<Build className="text-base" />}
            label="Build"
          />
          <Tab
            value="phases"
            icon={<Timeline className="text-base" />}
            label="Phases"
          />
          <Tab
            value="history"
            icon={<History className="text-base" />}
            label="History"
          />
          <Tab
            value="activity"
            icon={<ListAlt className="text-lg" />}
            label="Activity"
          />
          <Tab
            value="governance"
            icon={<Security className="text-base" />}
            label="Gov"
          />
          <Tab
            value="visualize"
            icon={<AutoAwesome className="text-base" />}
            label="Diagram"
          />
        </Tabs>
      </Box>

      {/* Tab Content */}
      <Box
        className="flex-1 overflow-hidden flex flex-col"
      >
        {activeTab === 'tasks' ? (
          <Box className="flex-1 overflow-auto">
            <CanvasTaskPanel
              projectId={projectId}
              collapsed={false}
              onCollapseChange={handleCollapseChange}
            />
          </Box>
        ) : activeTab === 'components' ? (
          <Box className="flex-1 overflow-auto">
            {canvasPolicy.canCreateArtifacts ? (
              <ComponentPalette onAddComponent={(component, position) => onAddComponent(component, position ?? { x: 400, y: 300 })} />
            ) : (
              mutationLockedNotice
            )}
          </Box>
        ) : activeTab === 'artifacts' ? (
          <Box className="flex-1 overflow-auto">
            {canvasPolicy.canCreateArtifacts ? (
              <ArtifactPalette
                onDragStart={(template) => onDragStart?.(template)}
                onQuickCreate={(template: ArtifactTemplate) => {
                  onAddComponent(template, { x: 400, y: 300 });
                }}
              />
            ) : (
              mutationLockedNotice
            )}
          </Box>
        ) : activeTab === 'phases' ? (
          <Box className="flex-1 overflow-auto p-4">
            <CanvasPhaseNavigator />
          </Box>
        ) : activeTab === 'history' ? (
          <Box className="flex-1 overflow-auto p-4">
            <Typography className="mb-2 block text-xs font-extrabold uppercase tracking-wider" color="text.secondary">
              Snapshot History
            </Typography>
            <Button
              fullWidth
              variant="solid"
              startIcon={<Save />}
              className="mb-6 rounded-lg py-2 font-bold"
            >
              Save Snapshot
            </Button>

            <Box className="space-y-3">
              {[
                { id: 'v1', version: '1.0', timestamp: '1 day ago', label: 'Initial Layout', author: 'Alice Chen' },
                { id: 'v2', version: '1.1', timestamp: '1 hour ago', label: 'Auth Added', author: 'Bob Smith' },
              ].map((snapshot) => (
                <Box key={snapshot.id} className="rounded-lg border border-border p-3 dark:border-border">
                  <Box className="flex items-center justify-between gap-2">
                    <Typography className="text-sm font-medium">{snapshot.label}</Typography>
                    <Typography className="text-xs text-fg-muted">v{snapshot.version}</Typography>
                  </Box>
                  <Typography className="mt-1 text-xs text-fg-muted" color="text.secondary">
                    {snapshot.author} · {snapshot.timestamp}
                  </Typography>
                </Box>
              ))}
            </Box>
          </Box>
        ) : activeTab === 'activity' ? (
          <Box className="flex-1 overflow-auto p-4">
            <AuditTimeline
              events={[
                {
                  id: '1',
                  type: 'ARTIFACT_CREATED',
                  title: 'New Service: Auth',
                  description: 'Created baseline authentication service',
                  user: { name: 'Alice Chen', role: 'Architect' },
                  timestamp: new Date(Date.now() - 3600000),
                  impact: 'high'
                },
                {
                  id: '2',
                  type: 'TASK_COMPLETED',
                  title: 'Requirement Review',
                  description: 'Completed review of user story #42',
                  user: { name: 'Bob Smith', role: 'Product' },
                  timestamp: new Date(Date.now() - 7200000),
                  impact: 'medium'
                }
              ]}
            />
          </Box>
        ) : activeTab === 'governance' ? (
          <Box className="flex-1 overflow-auto p-4">
            <GovernancePanel />
          </Box>
        ) : (
          <Box className="flex-1 overflow-auto p-4">
            <GraphVisualizer nodes={nodes || []} edges={edges || []} />
          </Box>
        )}
      </Box>
    </Paper>
  );
}
