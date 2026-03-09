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
import { Box, Tabs, Tab, Surface as Paper, Typography, IconButton, Tooltip } from '@ghatana/ui';
import { ClipboardList as Assignment, Component as Widgets, Activity as Timeline, Hammer as Build, History, ListOrdered as ListAlt, Save, Shield as Security, Sparkles as AutoAwesome } from 'lucide-react';

import { CanvasTaskPanel } from './tasks/CanvasTaskPanel';
import { ComponentPalette } from './ComponentPalette';
import { CanvasPhaseNavigator } from './CanvasPhaseNavigator';
import { ArtifactPalette, type ArtifactTemplate } from './workspace/ArtifactPalette';
import { VersionHistoryPanel, VersionHistoryList } from './versioning/VersionHistoryPanel';
import { AuditTimeline, type AuditEvent } from './audit/AuditTimeline';
import { GovernancePanel } from './governance/GovernancePanel';
import { GraphVisualizer } from './visualizer/GraphVisualizer';
import { TRANSITIONS } from '../../styles/design-tokens';

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
  onAddComponent: (component: unknown, position: { x: number; y: number }) => void;
  /** Drag start handler */
  onDragStart?: (template: unknown) => void;
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
  onDragStart,
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

  // Collapsed state - show only tab icons vertically
  if (isCollapsed) {
    return (
      <Paper
        variant="flat"
        className={`
          flex flex-col items-center gap-1 py-2 bg-bg-paper border-r border-divider
          ${TRANSITIONS.default}
          ${className}
        `}
        className="shrink-0 h-full w-[48px]"
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
        <Typography as="p" className="text-lg font-medium" fontWeight="700" color="text.primary" className="tracking-[-0.02em]">
          Canvas Tools
        </Typography>
        <Tooltip title="Collapse panel">
          <IconButton
            size="sm"
            onClick={() => handleCollapseChange(true)}
            className="p-1 rounded-md hover:bg-gray-100"
          >
            <Box
              component="svg"
              className="w-[18px] h-[18px]"
              viewBox="0 0 24 24"
              fill="currentColor"
            >
              <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z" />
            </Box>
          </IconButton>
        </Tooltip>
      </Box>

      {/* Tab Navigation */}
      <Box className="px-4 pt-4 pb-2">
        <Tabs
          value={activeTab}
          onChange={handleTabChange}
          variant="scrollable"
          scrollButtons="auto"
          allowScrollButtonsMobile
          className="min-h-[36px] h-full rounded-lg border border-solid border-blue-600 z-0 min-h-[36px] min-w-0 px-3 py-1 normal-case text-xs font-semibold gap-1 z-[1] rounded-lg transition-all duration-200 mr-1 text-blue-600" >
          <Tab
            value="tasks"
            icon={<Assignment className="text-base" />}
            iconPosition="start"
            label="Tasks"
          />
          <Tab
            value="components"
            icon={<Widgets className="text-base" />}
            iconPosition="start"
            label="Widgets"
          />
          <Tab
            value="artifacts"
            icon={<Build className="text-base" />}
            iconPosition="start"
            label="Build"
          />
          <Tab
            value="phases"
            icon={<Timeline className="text-base" />}
            iconPosition="start"
            label="Phases"
          />
          <Tab
            value="history"
            icon={<History className="text-base" />}
            iconPosition="start"
            label="History"
          />
          <Tab
            value="activity"
            icon={<ListAlt className="text-lg" />}
            iconPosition="start"
            label="Activity"
          />
          <Tab
            value="governance"
            icon={<Security className="text-base" />}
            iconPosition="start"
            label="Gov"
          />
          <Tab
            value="visualize"
            icon={<AutoAwesome className="text-base" />}
            iconPosition="start"
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
            <ComponentPalette onAddComponent={onAddComponent} />
          </Box>
        ) : activeTab === 'artifacts' ? (
          <Box className="flex-1 overflow-auto">
            <ArtifactPalette
              onDragStart={(template) => onDragStart?.(template)}
              onQuickCreate={(template: ArtifactTemplate) => {
                onAddComponent(template, { x: 400, y: 300 });
              }}
            />
          </Box>
        ) : activeTab === 'phases' ? (
          <Box className="flex-1 overflow-auto p-4">
            <CanvasPhaseNavigator />
          </Box>
        ) : activeTab === 'history' ? (
          <Box className="flex-1 overflow-auto p-4">
            <Typography as="span" className="text-xs uppercase tracking-wider" color="text.secondary" className="font-extrabold mb-2 block">
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

            <VersionHistoryList
              snapshots={[
                {
                  id: 'v1',
                  version: 1.0,
                  timestamp: Date.now() - 86400000,
                  label: 'Initial Layout',
                  author: 'Alice Chen',
                  data: { elements: [], connections: [] }
                },
                {
                  id: 'v2',
                  version: 1.1,
                  timestamp: Date.now() - 3600000,
                  label: 'Auth Added',
                  author: 'Bob Smith',
                  data: { elements: [], connections: [] }
                }
              ]}
              currentVersion={1.1}
              onRestore={(id) => console.log('Restore', id)}
            />
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
