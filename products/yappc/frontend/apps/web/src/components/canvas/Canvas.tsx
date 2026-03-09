/**
 * Canvas Content Renderer
 *
 * Generic canvas component that renders different content types based on
 * current mode and level using the Strategy pattern.
 *
 * @doc.type component
 * @doc.purpose Generic canvas renderer with strategy pattern
 * @doc.layer product
 * @doc.pattern Strategy + Factory
 */

import { useAtomValue } from 'jotai';
import {
  canvasModeAtom,
  abstractionLevelAtom,
} from '../../state/atoms/toolbarAtom';
import { getCanvasState, CanvasContentType } from '../../config/canvas-states';
import { Suspense, ComponentType } from 'react';
import { Box, Spinner as CircularProgress } from '@ghatana/ui';

// Dev playground for quick testing
import { CanvasPlayground } from '@ghatana/canvas';

// All canvas implementations
import { CodeEditorCanvas } from './content/CodeEditorCanvas';
import { StickyNotesCanvas } from './content/StickyNotesCanvas';
import { FileExplorerCanvas } from './content/FileExplorerCanvas';
import { TestFileListCanvas } from './content/TestFileListCanvas';
import { ConfigBrowserCanvas } from './content/ConfigBrowserCanvas';
import { LogViewerCanvas } from './content/LogViewerCanvas';
import { ArchitectureDiagramCanvas } from './content/ArchitectureDiagramCanvas';
import { ComponentDiagramCanvas } from './content/ComponentDiagramCanvas';
import { ClassDiagramCanvas } from './content/ClassDiagramCanvas';
import { SequenceDiagramCanvas } from './content/SequenceDiagramCanvas';
import { ModuleGraphCanvas } from './content/ModuleGraphCanvas';
import { ApiTopologyCanvas } from './content/ApiTopologyCanvas';
import { MindMapCanvas } from './content/MindMapCanvas';
import { AnnotationsCanvas } from './content/AnnotationsCanvas';
import { PseudocodeCanvas } from './content/PseudocodeCanvas';
import { DesignSystemCanvas } from './content/DesignSystemCanvas';
import { PageLayoutsCanvas } from './content/PageLayoutsCanvas';
import { ComponentSpecsCanvas } from './content/ComponentSpecsCanvas';
import { StyleTokensCanvas } from './content/StyleTokensCanvas';
import { E2ECoverageCanvas } from './content/E2ECoverageCanvas';
import { UnitCoverageCanvas } from './content/UnitCoverageCanvas';
import { TestEditorCanvas } from './content/TestEditorCanvas';
import { InfrastructureDiagramCanvas } from './content/InfrastructureDiagramCanvas';
import { ContainerOrchestrationCanvas } from './content/ContainerOrchestrationCanvas';
import { ConfigEditorCanvas } from './content/ConfigEditorCanvas';
import { SystemDashboardCanvas } from './content/SystemDashboardCanvas';
import { ComponentMetricsCanvas } from './content/ComponentMetricsCanvas';
import { TraceExplorerCanvas } from './content/TraceExplorerCanvas';

/**
 * Canvas content renderer interface
 */
export interface CanvasContentRenderer {
  type: CanvasContentType;
  render: () => React.ReactElement;
}

/**
 * Content components registry
 * Maps each content type to its implementation
 */
const contentComponents: Record<CanvasContentType, ComponentType> = {
  // Brainstorm
  [CanvasContentType.MIND_MAP]: MindMapCanvas,
  [CanvasContentType.STICKY_NOTES]: StickyNotesCanvas,
  [CanvasContentType.ANNOTATIONS]: AnnotationsCanvas,
  [CanvasContentType.PSEUDOCODE]: PseudocodeCanvas,

  // Diagram
  [CanvasContentType.ARCHITECTURE_DIAGRAM]: ArchitectureDiagramCanvas,
  [CanvasContentType.COMPONENT_DIAGRAM]: ComponentDiagramCanvas,
  [CanvasContentType.CLASS_DIAGRAM]: ClassDiagramCanvas,
  [CanvasContentType.SEQUENCE_DIAGRAM]: SequenceDiagramCanvas,

  // Design
  [CanvasContentType.DESIGN_SYSTEM]: DesignSystemCanvas,
  [CanvasContentType.PAGE_LAYOUTS]: PageLayoutsCanvas,
  [CanvasContentType.COMPONENT_SPECS]: ComponentSpecsCanvas,
  [CanvasContentType.STYLE_TOKENS]: StyleTokensCanvas,

  // Code
  [CanvasContentType.API_TOPOLOGY]: ApiTopologyCanvas,
  [CanvasContentType.MODULE_GRAPH]: ModuleGraphCanvas,
  [CanvasContentType.FILE_EXPLORER]: FileExplorerCanvas,
  [CanvasContentType.CODE_EDITOR]: CodeEditorCanvas,

  // Test
  [CanvasContentType.E2E_COVERAGE]: E2ECoverageCanvas,
  [CanvasContentType.UNIT_COVERAGE]: UnitCoverageCanvas,
  [CanvasContentType.TEST_FILE_LIST]: TestFileListCanvas,
  [CanvasContentType.TEST_EDITOR]: TestEditorCanvas,

  // Deploy
  [CanvasContentType.INFRASTRUCTURE_DIAGRAM]: InfrastructureDiagramCanvas,
  [CanvasContentType.CONTAINER_ORCHESTRATION]: ContainerOrchestrationCanvas,
  [CanvasContentType.CONFIG_BROWSER]: ConfigBrowserCanvas,
  [CanvasContentType.CONFIG_EDITOR]: ConfigEditorCanvas,

  // Observe
  [CanvasContentType.SYSTEM_DASHBOARD]: SystemDashboardCanvas,
  [CanvasContentType.COMPONENT_METRICS]: ComponentMetricsCanvas,
  [CanvasContentType.LOG_VIEWER]: LogViewerCanvas,
  [CanvasContentType.TRACE_EXPLORER]: TraceExplorerCanvas,
};

/**
 * Loading fallback for canvas content
 */
const CanvasLoadingFallback = () => (
  <Box
    className="flex justify-center items-center h-full w-full"
  >
    <CircularProgress />
  </Box>
);

/**
 * Main Canvas component
 *
 * Renders contextually appropriate content based on current mode and level.
 * Uses lazy loading and Suspense for optimal performance.
 */
export const Canvas = () => {
  const mode = useAtomValue(canvasModeAtom);
  const level = useAtomValue(abstractionLevelAtom);

  // Get current canvas state configuration
  const state = getCanvasState(mode, level);

  // Get the appropriate content component
  const ContentComponent = contentComponents[state.contentType];

  return (
    <Box
      className="relative w-full h-full overflow-hidden" data-mode={mode}
      data-level={level}
      data-content-type={state.contentType}
    >
      <Suspense fallback={<CanvasLoadingFallback />}>
        <ContentComponent />
      </Suspense>

      {process.env.NODE_ENV !== 'production' && (
        <div style={{ position: 'absolute', top: 12, right: 12, zIndex: 999, backgroundColor: 'var(--bg-canvas)' }}>
          <CanvasPlayground />
        </div>
      )}
    </Box>
  );
};

export default Canvas;
