/**
 * Workspace Components
 *
 * @doc.type module
 * @doc.purpose Workspace management UI components
 * @doc.layer product
 * @doc.pattern Barrel Export
 */

export { WorkspaceAdminDashboard } from './WorkspaceAdminDashboard';
export type { default as WorkspaceAdminDashboardProps } from './WorkspaceAdminDashboard';

// New workspace management components
export { WorkspaceSelector } from './WorkspaceSelector';
export type { WorkspaceSelectorProps } from './WorkspaceSelector';

export { ProjectListPanel } from './ProjectListPanel';
export type { ProjectListPanelProps } from './ProjectListPanel';

export { HeaderWithBreadcrumb } from './HeaderWithBreadcrumb';
export type { HeaderWithBreadcrumbProps } from './HeaderWithBreadcrumb';

export { CreateWorkspaceDialog } from './CreateWorkspaceDialog';
export type { CreateWorkspaceDialogProps } from './CreateWorkspaceDialog';

export { WorkspaceSelectionDialog } from './WorkspaceSelectionDialog';

export { CreateProjectDialog } from './CreateProjectDialog';
export type { CreateProjectDialogProps } from './CreateProjectDialog';

export { ImportProjectDialog } from './ImportProjectDialog';
export type { ImportProjectDialogProps } from './ImportProjectDialog';

// Onboarding
export { OnboardingFlow } from './OnboardingFlow';
export type { OnboardingFlowProps } from './OnboardingFlow';
