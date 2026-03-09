/**
 * Components Index File
 *
 * This file exports all components for the YAPPC UI design system.
 * Supports both flat imports (legacy) and atomic imports (new).
 *
 * @packageDocumentation
 */

// ============================================================================
// Atomic Design Exports (New Pattern - Recommended)
// ============================================================================

/**
 * Atomic design barrel exports for structured imports
 *
 * @example
 * ```tsx
 * import { Button, Input } from '@ghatana/yappc-shared-ui-core/atoms';
 * import { TextField, Alert } from '@ghatana/yappc-shared-ui-core/molecules';
 * import { Form, Card } from '@ghatana/yappc-shared-ui-core/organisms';
 * ```
 */
export * as atoms from './atoms';
export * as molecules from './molecules';
export * as organisms from './organisms';

// ============================================================================
// Flat Exports (Legacy Pattern - Backward Compatible)
// ============================================================================

// Basic Components
export * from './AppBar';
export * from './Toolbar';
export * from './List';
export * from './ListItem';
export * from './Button';
export * from './Card';
export * from './TextField';
export * from './Badge';
export * from './Dialog';
export * from './Drawer';
export * from './Menu';
export * from './Popover';
export * from './Tabs';
export * from './Grid';
export * from './Container';
export * from './Stack';
export * from './Spacer';
export * from './Typography'; // Tailwind CSS
export * from './Paper'; // Tailwind CSS (Box wrapper)
export * from './Notification';
export * from './ThemeProvider';
export * from './Page';
export * from './Shell';
export * from './Navigation';
export * from './Status';
export * from './Search';
export * from './Input';
export * from './Checkbox';
export * from './Radio';
export * from './Switch';
export * from './Slider';
export * from './Chip';
export * from './Avatar';
export * from './Divider';
export * from './Spinner';
export * from './Progress';
export * from './Rating';
export * from './Tooltip';
export * from './Select';
export * from './Alert';
export * from './Toast';
export * from './Skeleton';
export * from './FormGroup';
export * from './Autocomplete';
export * from './Accordion';
export * from './Breadcrumb';
export * from './Pagination';
export * from './Stepper';
export * from './TransferList';
export * from './TreeView';
export * from './DatePicker';
export * from './TimePicker';
export * from './DateRangePicker';
export * from './DateTimePicker';
export * from './FileUpload';
export * from './Form';
export * from './Modal';
export * from './Table';
export * from './DataTable';
export * from './Dashboard';
export * from './WorkspaceCard';
export * from './ThemeToggle';

// Table Components
export { SelectableTable } from './Table/SelectableTable';
export type {
  TableColumn,
  SelectableTableProps,
} from './Table/SelectableTable';

// Action Components
export { BulkActionBar } from './Actions/BulkActionBar';
export type {
  BulkAction,
  BulkOperationProgress,
  BulkActionBarProps,
} from './Actions/BulkActionBar';

// Keyboard Shortcuts Components
export { CommandPalette } from './CommandPalette';
export type { Command, CommandPaletteProps } from './CommandPalette';
export { ShortcutHelper } from './Shortcuts/ShortcutHelper';
export type { ShortcutHelperProps } from './Shortcuts/ShortcutHelper';

// Performance Components
export { PerformanceTrendingChart, PerformanceDashboard } from './Performance';
export type {
  TrendingChartProps,
  PerformanceDashboardProps,
} from './Performance';

// AI Components
export { AIInsightsDashboard } from './AI';
export type { AIInsightsDashboardProps } from './AI';

// Dashboard Components
export { CustomDashboardBuilder } from './Dashboard';
export type {
  CustomDashboardBuilderProps,
  DashboardWidget,
  DashboardLayout,
  WidgetType,
} from './Dashboard';

// Onboarding Components
export { OnboardingTour, onboardingTourManager } from './OnboardingTour';
export type {
  Tour,
  TourStep,
  OnboardingState,
  OnboardingTourProps,
} from './OnboardingTour';

// DevSecOps Components
export * as DevSecOps from './DevSecOps';

// ============================================================================
// YAPPC Phase-Specific Components
// ============================================================================

// Bootstrapping Phase Components
export { AIChatInterface } from './chat/AIChatInterface';

// Bootstrapping Phase UI (wrappers that reuse base components)
export {
  PhaseProgressBar,
  BootstrapConversation,
  BootstrapCanvas,
} from './bootstrapping';
export type {
  PhaseProgressBarProps,
  PhaseConfig,
  BootstrapConversationProps,
  BootstrapCanvasProps,
  PhaseLane,
} from './bootstrapping';

export { ProjectCanvas } from './canvas/ProjectCanvas';
export type {
  CanvasNode,
  CanvasEdge,
  NodeCategory,
  CanvasTool,
  ProjectCanvasRef,
} from './canvas/ProjectCanvas';

// Draggable Canvas Components
export {
  DraggableCanvas,
  DraggableItem,
  ComponentLibrary,
} from './canvas/DraggableCanvas';
export type {
  ComponentType as DraggableComponentType,
  DraggableItemProps,
  ComponentLibraryProps,
  CanvasItem,
  DraggableCanvasProps,
} from './canvas/DraggableCanvas';

// Config Components
export {
  DomainSelector,
  TaskListView,
  TaskCard,
  WorkflowRenderer,
  EnhancedDomainSelector,
  DevSecOpsNavigationHub,
} from './config';

export { ValidationPanel } from './validation/ValidationPanel';
export type {
  ValidationSeverity,
  ValidationIssue,
  ValidationReport,
} from './validation/ValidationPanel';

// Development Phase Components
export { SprintBoard } from './development/SprintBoard';
export type {
  Story,
  StoryStatus,
  StoryPriority,
  StoryType,
  BoardColumn,
  Sprint,
} from './development/SprintBoard';

// Operations Phase Components
export { IncidentDashboard } from './operations/IncidentDashboard';
export type {
  Incident,
  IncidentSeverity,
  IncidentStatus,
  Alert as IncidentAlert,
} from './operations/IncidentDashboard';

// Security Phase Components
export { SecurityDashboard } from './security/SecurityDashboard';
export type {
  Vulnerability,
  VulnerabilitySeverity,
  VulnerabilityStatus,
  ScanType,
  ComplianceFramework,
  SecurityScore,
  SecurityAlert,
} from './security/SecurityDashboard';

// ============================================================================
// MUI RE-EXPORTS REMOVED
// ============================================================================
// All MUI component re-exports have been removed.
// Use @ghatana/ui for platform-level Tailwind-native components:
//   import { Box, Button, Card, Dialog, ... } from '@ghatana/ui';
//
// For MUI-specific components still needed internally by @ghatana/yappc-ui,
// import directly from '@mui/material' within this package's source.
// ============================================================================

// ============================================================================
// Usage Examples
// ============================================================================

/**
 * @example Atomic Design Pattern (Recommended)
 * ```tsx
 * import { Button, Input, Badge } from '@ghatana/yappc-shared-ui-core/atoms';
 * import { TextField, Alert } from '@ghatana/yappc-shared-ui-core/molecules';
 * import { Form, Card, Navigation } from '@ghatana/yappc-shared-ui-core/organisms';
 *
 * function MyComponent() {
 *   return (
 *     <Card title="User Form">
 *       <Form onSubmit={handleSubmit}>
 *         <TextField label="Name" />
 *         <Button type="submit">
 *           Save <Badge>New</Badge>
 *         </Button>
 *       </Form>
 *     </Card>
 *   );
 * }
 * ```
 *
 * @example Flat Import Pattern (Legacy - Still Supported)
 * ```tsx
 * import { Button, Card } from '@ghatana/ui';
import { TextField } from '@ghatana/ui';
import { Form } from '@ghatana/yappc-ui';
 *
 * function MyComponent() {
 *   return (
 *     <Card title="User Form">
 *       <Form onSubmit={handleSubmit}>
 *         <TextField label="Name" />
 *         <Button type="submit">Save</Button>
 *       </Form>
 *     </Card>
 *   );
 * }
 * ```
 */
