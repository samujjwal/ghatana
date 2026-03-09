/**
 * CanvasChromeLayout Component
 *
 * Miro-inspired calm UI shell with progressive disclosure
 *
 * Features:
 * - Collapsible left rail (icons only by default)
 * - Context-aware right inspector
 * - Minimal top bar
 * - Context bar on selection
 * - Distraction-free mode support
 *
 * @doc.type component
 * @doc.purpose Canvas UI shell with calm-by-default behavior
 * @doc.layer components
 * @doc.pattern Layout
 */

import { Box, IconButton, Tooltip } from '@ghatana/ui';
import { useAtom } from 'jotai';
import React, { useEffect, type ReactNode } from 'react';

import {
  chromeCalmModeAtom,
  chromeFocusModeAtom,
  chromeLeftRailVisibleAtom,
  chromeLeftPanelAtom,
  chromeInspectorVisibleAtom,
  chromeDistractionFreeAtom,
  chromeBreadcrumbVisibleAtom,
  chromePhaseIndicatorVisibleAtom,
  openLeftPanelAtom,
  type LeftPanelType,
} from '../state/chrome-atoms';
import { CANVAS_TOKENS } from '../tokens/canvas-tokens';

const { SPACING, Z_INDEX, COLORS, TRANSITIONS, SHADOWS, CANVAS } = CANVAS_TOKENS;

/**
 *
 */
export interface CanvasChromeLayoutProps {
  /** Main canvas content */
  children: ReactNode;

  /** Top bar content (overrides default) */
  topBar?: ReactNode;

  /** Left rail panel contents */
  outline?: ReactNode;
  layers?: ReactNode;
  palette?: ReactNode;
  tasks?: ReactNode;

  /** Right inspector content */
  inspector?: ReactNode;

  /** Context bar (appears on selection) */
  contextBar?: ReactNode;

  /** Breadcrumb navigation */
  breadcrumb?: ReactNode;

  /** Phase indicator */
  phaseIndicator?: ReactNode;

  /** Zoom HUD */
  zoomHUD?: ReactNode;

  /** Empty canvas state */
  emptyState?: ReactNode;

  /** Initial calm mode state */
  defaultCalmMode?: boolean;

  /** Enable focus mode support */
  enableFocusMode?: boolean;

  /** Callback when left rail toggle */
  onLeftRailToggle?: (open: boolean) => void;
}

/**
 *
 */
export function CanvasChromeLayout({
  children,
  topBar,
  outline,
  layers,
  palette,
  tasks,
  inspector,
  contextBar,
  breadcrumb,
  phaseIndicator,
  zoomHUD,
  emptyState,
  defaultCalmMode = true,
  enableFocusMode = true,
  onLeftRailToggle,
}: CanvasChromeLayoutProps) {
  const [calmMode] = useAtom(chromeCalmModeAtom);
  const [focusMode] = useAtom(chromeFocusModeAtom);
  const [leftRailOpen, setLeftRailOpen] = useAtom(chromeLeftRailVisibleAtom);
  const [leftPanel, setLeftPanel] = useAtom(chromeLeftPanelAtom);
  const [inspectorOpen] = useAtom(chromeInspectorVisibleAtom);
  const [distractionFree] = useAtom(chromeDistractionFreeAtom);
  const [breadcrumbVisible] = useAtom(chromeBreadcrumbVisibleAtom);
  const [phaseIndicatorVisible] = useAtom(chromePhaseIndicatorVisibleAtom);
  const [, openLeftPanel] = useAtom(openLeftPanelAtom);

  // Initialize calm mode
  useEffect(() => {
    if (defaultCalmMode !== undefined) {
      // Initial setup handled by parent
    }
  }, [defaultCalmMode]);

  // Auto-collapse in calm mode
  useEffect(() => {
    if (calmMode) {
      setLeftRailOpen(false);
    }
  }, [calmMode, setLeftRailOpen]);

  // Notify parent of left rail changes
  useEffect(() => {
    onLeftRailToggle?.(leftRailOpen);
  }, [leftRailOpen, onLeftRailToggle]);

  const handleLeftRailToggle = () => {
    setLeftRailOpen(!leftRailOpen);
  };

  const handlePanelIconClick = (panel: LeftPanelType) => {
    if (leftPanel === panel && leftRailOpen) {
      // Clicking active panel collapses rail
      setLeftRailOpen(false);
    } else {
      // Open to this panel
      openLeftPanel(panel);
    }
  };

  // In focus mode or distraction-free mode, hide all chrome except zoom HUD
  if (focusMode || distractionFree) {
    return (
      <Box
        className="w-screen h-screen overflow-hidden relative" style={{ backgroundColor: COLORS.CANVAS_BG_LIGHT }} >
        {children}
        {emptyState}
        {/* Show dimmed zoom HUD in focus mode */}
        {enableFocusMode && zoomHUD && (
          <Box className="opacity-[0.5]">
            {zoomHUD}
          </Box>
        )}
      </Box>
    );
  }

  const leftRailWidth = leftRailOpen ? CANVAS.LEFT_RAIL_WIDTH_EXPANDED : CANVAS.LEFT_RAIL_WIDTH_COLLAPSED;
  const inspectorWidth = inspectorOpen ? CANVAS.PROPERTIES_PANEL_WIDTH : 0;
  const hasTopBar = topBar !== null && topBar !== undefined;
  const showBreadcrumb = breadcrumbVisible && breadcrumb;
  const showPhaseIndicator = phaseIndicatorVisible && phaseIndicator;

  const gridTemplate = hasTopBar
    ? `
          "topbar topbar topbar"
          "leftrail canvas inspector"
        `
    : `
          "leftrail canvas inspector"
        `;

  const gridRows = hasTopBar ? 'auto 1fr' : '1fr';

  return (
    <Box
      className="grid" style={{ gridTemplateAreas: gridTemplate, gridTemplateColumns: `${leftRailWidth}px 1fr ${inspectorWidth}px`, gridTemplateRows: gridRows, width: '100vw', height: '100vh', overflow: 'hidden' }}>
      {/* Top Bar - Only rendered if provided */}
      {hasTopBar && (
        <Box
          className="flex items-center" style={{ paddingLeft: SPACING.MD, paddingRight: SPACING.MD, paddingTop: SPACING.SM, paddingBottom: SPACING.SM, backgroundColor: COLORS.PANEL_BG_LIGHT, borderBottom: `1px solid ${COLORS.BORDER_LIGHT}`, gridArea: 'topbar' }}
        >
          {topBar || (
            <DefaultTopBar
              breadcrumb={showBreadcrumb ? breadcrumb : undefined}
              phaseIndicator={showPhaseIndicator ? phaseIndicator : undefined}
            />
          )}
        </Box>
      )}

      {/* Left Rail */}
      <Box
        className="flex flex-row" style={{ backgroundColor: COLORS.PANEL_BG_LIGHT, borderRight: `1px solid ${COLORS.BORDER_LIGHT}`, gridArea: 'leftrail' }}>
        {/* Icon strip (always visible) */}
        <Box
          className="w-[48px] shrink-0 flex flex-col items-center" style={{ gap: SPACING.XS, paddingTop: SPACING.MD, paddingBottom: SPACING.MD, borderRight: leftRailOpen
              ? `1px solid ${COLORS.BORDER_LIGHT}` : 'none' }}>
          <Tooltip title="Outline (O)" placement="right">
            <IconButton
              onClick={() => handlePanelIconClick('outline')}
              aria-label="Show outline panel"
              aria-pressed={leftPanel === 'outline' && leftRailOpen}
              className="hover:bg-gray-100"
              style={{
                backgroundColor:
                  leftPanel === 'outline' && leftRailOpen
                    ? COLORS.SELECTION_BG
                    : 'transparent',
              }}
            >
              <span style={{ fontSize: '20px' }}>📋</span>
            </IconButton>
          </Tooltip>

          <Tooltip title="Layers (L)" placement="right">
            <IconButton
              onClick={() => handlePanelIconClick('layers')}
              aria-label="Show layers panel"
              aria-pressed={leftPanel === 'layers' && leftRailOpen}
              className="hover:bg-gray-100"
              style={{
                backgroundColor:
                  leftPanel === 'layers' && leftRailOpen
                    ? COLORS.SELECTION_BG
                    : 'transparent',
              }}
            >
              <span style={{ fontSize: '20px' }}>📚</span>
            </IconButton>
          </Tooltip>

          <Tooltip title="Palette (P)" placement="right">
            <IconButton
              onClick={() => handlePanelIconClick('palette')}
              aria-label="Show palette panel"
              aria-pressed={leftPanel === 'palette' && leftRailOpen}
              className="hover:bg-gray-100"
              style={{
                backgroundColor:
                  leftPanel === 'palette' && leftRailOpen
                    ? COLORS.SELECTION_BG
                    : 'transparent',
              }}
            >
              <span style={{ fontSize: '20px' }}>🎨</span>
            </IconButton>
          </Tooltip>

          <Tooltip title="Tasks (T)" placement="right">
            <IconButton
              onClick={() => handlePanelIconClick('tasks')}
              aria-label="Show tasks panel"
              aria-pressed={leftPanel === 'tasks' && leftRailOpen}
              className="hover:bg-gray-100"
              style={{
                backgroundColor:
                  leftPanel === 'tasks' && leftRailOpen
                    ? COLORS.SELECTION_BG
                    : 'transparent',
              }}
            >
              <span style={{ fontSize: '20px' }}>✅</span>
            </IconButton>
          </Tooltip>

          {/* Spacer */}
          <Box className="flex-1" />

          {/* Toggle collapse button */}
          <Tooltip
            title={leftRailOpen ? 'Collapse' : 'Expand'}
            placement="right"
          >
            <IconButton
              onClick={handleLeftRailToggle}
              aria-label={
                leftRailOpen ? 'Collapse left rail' : 'Expand left rail'
              }
              size="small"
            >
              <span style={{ fontSize: '16px' }}>
                {leftRailOpen ? '◀' : '▶'}
              </span>
            </IconButton>
          </Tooltip>
        </Box>

        {/* Panel content (shows when expanded) */}
        {leftRailOpen && (
          <Box
            className="flex-1 overflow-hidden flex flex-col w-[232px]"
          >
            {leftPanel === 'outline' && outline}
            {leftPanel === 'layers' && layers}
            {leftPanel === 'palette' && palette}
            {leftPanel === 'tasks' && tasks}
            {!leftPanel && (
              <Box className="flex items-center justify-center h-full" style={{ color: COLORS.TEXT_SECONDARY, fontSize: CANVAS_TOKENS.TYPOGRAPHY.SM }}
              >
                Select a panel from the left
              </Box>
            )}
          </Box>
        )}
      </Box>

      {/* Main Canvas Area */}
      <Box style={{ gridArea: 'canvas', backgroundColor: COLORS.CANVAS_BG_LIGHT }}
      >
        {children}

        {/* Empty canvas state */}
        {emptyState}

        {/* Context Bar (appears on selection) */}
        {contextBar && (
          <Box
            className="absolute left-[50%]" style={{ bottom: SPACING.LG, zIndex: Z_INDEX.CONTROLS, backgroundColor: COLORS.PANEL_BG_LIGHT, borderRadius: CANVAS_TOKENS.RADIUS.MD, boxShadow: SHADOWS.LG, border: `1px solid ${COLORS.BORDER_LIGHT}`, transform: 'translateX(-50%)' }}
          >
            {contextBar}
          </Box>
        )}

        {/* Zoom HUD (always visible in bottom-right) */}
        {zoomHUD}
      </Box>

      {/* Right Inspector */}
      {inspectorOpen && inspector && (
        <Box
          className="flex flex-col" style={{ backgroundColor: COLORS.PANEL_BG_LIGHT, borderLeft: `1px solid ${COLORS.BORDER_LIGHT}`, gridArea: 'inspector' }}>
          {inspector}
        </Box>
      )}
    </Box>
  );
}

/**
 * Default Top Bar Component
 * Minimal breadcrumb/phase/search UI
 */
function DefaultTopBar({
  breadcrumb,
  phaseIndicator,
}: {
  breadcrumb?: ReactNode;
  phaseIndicator?: ReactNode;
}) {
  return (
    <Box
      className="flex items-center justify-between w-full"
    >
      {/* Left: Breadcrumb navigation */}
      <Box className="flex items-center gap-4" >
        {breadcrumb || (
          <Box
            style={{
              fontSize: CANVAS_TOKENS.TYPOGRAPHY.BASE,
              fontWeight: CANVAS_TOKENS.FONT_WEIGHT.SEMIBOLD,
              color: COLORS.TEXT_PRIMARY,
            }}
          >
            Canvas
          </Box>
        )}
      </Box>

      {/* Center: Phase indicator */}
      <Box
        className="flex items-center gap-2" >
        {phaseIndicator}
      </Box>

      {/* Right: Actions */}
      <Box className="flex items-center gap-2" >
        <Tooltip title="Share">
          <IconButton aria-label="Share canvas">
            <span style={{ fontSize: '20px' }}>📤</span>
          </IconButton>
        </Tooltip>
      </Box>
    </Box>
  );
}

/**
 * Export chrome atoms for consumers
 */
export {
  chromeCalmModeAtom,
  chromeFocusModeAtom,
  chromeLeftRailVisibleAtom,
  chromeLeftPanelAtom,
  chromeInspectorVisibleAtom,
  chromeDistractionFreeAtom,
  chromeBreadcrumbVisibleAtom,
  chromePhaseIndicatorVisibleAtom,
} from '../state/chrome-atoms';
