/**
 * @doc.type module
 * @doc.purpose Public API exports for Canvas virtualization and diagramming library
 * @doc.layer platform
 */

// Accessibility
export type { A11yConfig, AccessibleCanvasElementProps } from './a11y/canvas-a11y';
export { 
  generateAriaProps,
  useCanvasFocus,
  useAnnouncer,
  ScreenReaderAnnouncer,
  AccessibleCanvasElement,
} from './a11y/canvas-a11y';

// Components
export type { OnboardingTourProps, TourStep } from './components/OnboardingTour';
export { OnboardingTour, defaultTourSteps } from './components/OnboardingTour';

// Hooks
export type { 
  VirtualizationConfig, 
  VirtualizationResult,
  ViewportBounds,
  VirtualElement,
} from './hooks/useCanvasVirtualization';
export { useCanvasVirtualization } from './hooks/useCanvasVirtualization';

export type { TouchConfig, TouchGesture, TouchPoint } from './hooks/useMobileTouch';
export { useMobileTouch } from './hooks/useMobileTouch';

// Layout & Templates
export type { 
  LayoutConfig, 
  LayoutElement, 
  LayoutEdge, 
  LayoutResult 
} from './layout/AutoLayoutEngine';
export { AutoLayoutEngine } from './layout/AutoLayoutEngine';

export type { DiagramTemplate } from './templates/AdvancedDiagrams';
export {
  diagramTemplates,
  getTemplatesByCategory,
  getTemplateById,
  createFromTemplate,
  bpmnProcessTemplate,
  bpmnCollaborationTemplate,
  umlClassDiagramTemplate,
  umlSequenceTemplate,
  erDiagramTemplate,
  networkTopologyTemplate,
  microservicesTemplate,
  serverlessTemplate,
} from './templates/AdvancedDiagrams';
