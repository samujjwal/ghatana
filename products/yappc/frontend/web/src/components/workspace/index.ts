/**
 * Workspace Components
 *
 * @doc.type module
 * @doc.purpose Workspace management UI components
 * @doc.layer product
 * @doc.pattern Barrel Export
 */

import type { ComponentProps } from 'react';

export { WorkspaceAdminDashboard } from './WorkspaceAdminDashboard';
export type WorkspaceAdminDashboardProps = ComponentProps<typeof import('./WorkspaceAdminDashboard').WorkspaceAdminDashboard>;

// New workspace management components
export { WorkspaceSelector } from './WorkspaceSelector';
export type WorkspaceSelectorProps = ComponentProps<typeof import('./WorkspaceSelector').WorkspaceSelector>;

export { ProjectListPanel } from './ProjectListPanel';
export type ProjectListPanelProps = ComponentProps<typeof import('./ProjectListPanel').ProjectListPanel>;

export { HeaderWithBreadcrumb } from './HeaderWithBreadcrumb';
export type HeaderWithBreadcrumbProps = ComponentProps<typeof import('./HeaderWithBreadcrumb').HeaderWithBreadcrumb>;

export { CreateWorkspaceDialog } from './CreateWorkspaceDialog';
export type CreateWorkspaceDialogProps = ComponentProps<typeof import('./CreateWorkspaceDialog').CreateWorkspaceDialog>;

export { WorkspaceSelectionDialog } from './WorkspaceSelectionDialog';

export { CreateProjectDialog } from './CreateProjectDialog';
export type CreateProjectDialogProps = ComponentProps<typeof import('./CreateProjectDialog').CreateProjectDialog>;

export { ImportProjectDialog } from './ImportProjectDialog';
export type ImportProjectDialogProps = ComponentProps<typeof import('./ImportProjectDialog').ImportProjectDialog>;

// Onboarding
export { OnboardingFlow } from './OnboardingFlow';
export type { OnboardingFlowProps } from './OnboardingFlow';
