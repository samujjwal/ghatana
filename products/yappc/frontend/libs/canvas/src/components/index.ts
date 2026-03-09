/**
 * Canvas Components
 *
 * Modular, composable React components for the Canvas library.
 * All components follow strict DoD requirements: <200 LOC, full accessibility, comprehensive testing.
 *
 * @module canvas/components
 */

// Main canvas component
export { Canvas, type CanvasProps } from './Canvas';

// Surface rendering components
export {
  CanvasSurface,
  type CanvasSurfaceProps,
} from './surface/CanvasSurface';

// Document management UI components (Feature 1.4)
export {
  TemplateLibraryDialog,
  type TemplateLibraryDialogProps,
} from './TemplateLibraryDialog';

export {
  VersionComparisonModal,
  type VersionComparisonModalProps,
} from './VersionComparisonModal';

export {
  AutosaveIndicator,
  AutosaveStatus,
  type AutosaveIndicatorProps,
} from './AutosaveIndicator';

// Re-export component types for convenience
export type {
  CanvasElement,
  CanvasDocument,
  CanvasTheme,
  CanvasSelection,
  CanvasViewport,
} from '../types/canvas-document';

// Minimap & Viewport Controls (Feature 2.9)
export {
  MinimapPanel,
  type MinimapPanelProps,
} from './MinimapPanel';

// Breadcrumb Navigation (Feature 5: Drill-down)
export {
  BreadcrumbNavigation,
  type BreadcrumbNavigationProps,
} from './BreadcrumbNavigation';

// Custom Nodes with Drill-down Support
export { customNodeTypes, createCustomNode } from './CustomNodes';

// Persona Journey Nodes (YAPPC User Journeys)
export {
  AIPromptNode,
  DatabaseNode,
  ServiceNode,
  APIEndpointNode,
  UIScreenNode,
  TestSuiteNode,
  personaNodeTypes,
  type PersonaNodeType,
  type PersonaNodeData,
  type AIPromptNodeData,
  type DatabaseNodeData,
  type ServiceNodeData,
  type APIEndpointNodeData,
  type UIScreenNodeData,
  type TestSuiteNodeData,
} from './PersonaNodes';

// Property Panels for Persona Nodes
export {
  SchemaDesignerPanel,
  DeploymentConfigPanel,
  APIDesignerPanel,
  type SchemaField,
  type DeploymentConfig,
  type APIEndpointConfig,
  type SchemaDesignerPanelProps,
  type DeploymentConfigPanelProps,
  type APIDesignerPanelProps,
} from './PropertyPanels';

// Journey Template Dialogs
export {
  TemplateDialog,
  type TemplateDialogProps,
} from './TemplateDialog';

export {
  SaveTemplateDialog,
  type SaveTemplateDialogProps,
} from './SaveTemplateDialog';

export {
  ExportCodeDialog,
  type ExportCodeDialogProps,
} from './ExportCodeDialog';

// Node Grouping Components (Journey 1.1: PM Handoff Workflow)
export {
  NodeGroup,
  type NodeGroupData,
  type GroupStatus,
} from './NodeGroup';

export {
  GroupingToolbar,
  type GroupingToolbarProps,
} from './GroupingToolbar';

// Test Generation Components (Journey 4.1: QA - Test Generation)
export {
  TestGenToolbar,
  type TestGenToolbarProps,
  type TestType,
  type TestStatus,
} from './TestGenToolbar';

export {
  TestResultsPanel,
  type TestResultsPanelProps,
  type TestCaseWithApproval,
} from './TestResultsPanel';

export {
  TestExecutionEdge,
  type TestExecutionEdgeData,
  type TestExecutionStatus,
} from './TestExecutionEdge';

// Persona Canvas Components (Task 8: Specialized Persona Canvases)
export {
  PersonaCanvas,
  type PersonaCanvasProps,
  usePersonaConfig,
  usePersonaFeature,
  usePersonaToolbar,
  usePersonaPanels,
} from './PersonaCanvas';

export {
  PersonaSwitcher,
  CompactPersonaSwitcher,
  type PersonaSwitcherProps,
} from './PersonaSwitcher';

// Zero-Trust Architecture Canvas (Journey 15.1: Security Architect - Zero-Trust Design)
export {
  ZeroTrustArchitectureCanvas,
} from './ZeroTrustArchitectureCanvas';

// Cloud Infrastructure Canvas (Journey 16.1: Technical Architect - Cloud Infrastructure)
export {
  CloudInfrastructureCanvas,
} from './CloudInfrastructureCanvas';

// Compliance Canvas (Journey 26.1: Compliance Officer - Compliance Canvas)
export {
  ComplianceCanvas,
} from './ComplianceCanvas';

// CISO Dashboard (Journey 27.1: CISO - Executive Security Dashboard)
export {
  CISODashboard,
} from './CISODashboard';

// Release Train (Journey 29.1: Release Train - Multi-Team Orchestration)
export {
  ReleaseTrain,
} from './ReleaseTrain';

// Performance Analysis (Journey 31.1: Performance Analysis - Profiling & Optimization)
export {
  PerformanceAnalysis,
} from './PerformanceAnalysis';

