/**
 * Workspace Settings Operational Closure
 *
 * @doc.type service
 * @doc.purpose Workspace settings management with operational closure
 * @doc.layer service
 * @doc.pattern Service Pattern
 */

import { getPrismaClient } from '../../database/client';

export interface WorkspaceSettings {
  workspaceId: string;
  name: string;
  description?: string;
  // Operational settings
  autoArchiveProjects: boolean;
  autoArchiveDays: number;
  // Collaboration settings
  allowExternalSharing: boolean;
  requireApprovalForInvites: boolean;
  defaultMemberRole: 'MEMBER' | 'VIEWER';
  // Security settings
  requireMfa: boolean;
  allowedEmailDomains?: string[];
  // Billing settings
  plan: 'free' | 'team' | 'enterprise';
  seats: number;
}

export async function updateWorkspaceSettings(
  workspaceId: string,
  settings: Partial<WorkspaceSettings>,
  updatedBy: string
): Promise<{ success: boolean; error?: string }> {
  const prisma = getPrismaClient();

  try {
    // Verify updater is admin
    const member = await prisma.workspaceMember.findFirst({
      where: { workspaceId, userId: updatedBy },
    });

    if (!member || member.role !== 'ADMIN') {
      return { success: false, error: 'Only admins can update settings' };
    }

    await prisma.workspace.update({
      where: { id: workspaceId },
      data: {
        name: settings.name,
        description: settings.description,
        settings: {
          autoArchiveProjects: settings.autoArchiveProjects,
          autoArchiveDays: settings.autoArchiveDays,
          allowExternalSharing: settings.allowExternalSharing,
          requireApprovalForInvites: settings.requireApprovalForInvites,
          defaultMemberRole: settings.defaultMemberRole,
          requireMfa: settings.requireMfa,
          allowedEmailDomains: settings.allowedEmailDomains,
        },
      },
    });

    return { success: true };
  } catch (error) {
    return { success: false, error: error instanceof Error ? error.message : 'Unknown error' };
  }
}

export async function getWorkspaceSettings(
  workspaceId: string
): Promise<{ success: boolean; settings?: WorkspaceSettings; error?: string }> {
  const prisma = getPrismaClient();

  try {
    const workspace = await prisma.workspace.findUnique({
      where: { id: workspaceId },
    });

    if (!workspace) {
      return { success: false, error: 'Workspace not found' };
    }

    const settings: WorkspaceSettings = {
      workspaceId: workspace.id,
      name: workspace.name,
      description: workspace.description || undefined,
      autoArchiveProjects: (workspace.settings as Record<string, unknown>)?.autoArchiveProjects as boolean || false,
      autoArchiveDays: (workspace.settings as Record<string, unknown>)?.autoArchiveDays as number || 90,
      allowExternalSharing: (workspace.settings as Record<string, unknown>)?.allowExternalSharing as boolean || false,
      requireApprovalForInvites: (workspace.settings as Record<string, unknown>)?.requireApprovalForInvites as boolean || true,
      defaultMemberRole: (workspace.settings as Record<string, unknown>)?.defaultMemberRole as 'MEMBER' | 'VIEWER' || 'MEMBER',
      requireMfa: (workspace.settings as Record<string, unknown>)?.requireMfa as boolean || false,
      allowedEmailDomains: (workspace.settings as Record<string, unknown>)?.allowedEmailDomains as string[],
      plan: workspace.plan as 'free' | 'team' | 'enterprise' || 'free',
      seats: workspace.seats || 5,
    };

    return { success: true, settings };
  } catch (error) {
    return { success: false, error: error instanceof Error ? error.message : 'Unknown error' };
  }
}
