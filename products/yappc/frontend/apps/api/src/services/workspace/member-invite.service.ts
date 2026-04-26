/**
 * Workspace Member & Invite Management Service
 *
 * @doc.type service
 * @doc.purpose Workspace membership and invitation management
 * @doc.layer service
 * @doc.pattern Service Pattern
 */

import { getPrismaClient } from '../../database/client';
import { getAuditService } from '../audit/audit.service';

export interface InviteRequest {
  workspaceId: string;
  email: string;
  role: 'MEMBER' | 'ADMIN' | 'VIEWER';
  invitedBy: string;
  message?: string;
}

export interface InviteResponse {
  success: boolean;
  inviteId?: string;
  error?: string;
}

export async function inviteMember(request: InviteRequest): Promise<InviteResponse> {
  const prisma = getPrismaClient();
  const audit = getAuditService();

  try {
    // Check if inviter is admin
    const inviter = await prisma.workspaceMember.findFirst({
      where: { workspaceId: request.workspaceId, userId: request.invitedBy },
    });

    if (!inviter || inviter.role !== 'ADMIN') {
      return { success: false, error: 'Only admins can invite members' };
    }

    // Check if user already member
    const existing = await prisma.workspaceMember.findFirst({
      where: { workspaceId: request.workspaceId, user: { email: request.email } },
    });

    if (existing) {
      return { success: false, error: 'User is already a member' };
    }

    // Create invite
    const invite = await prisma.workspaceInvite.create({
      data: {
        workspaceId: request.workspaceId,
        email: request.email,
        role: request.role,
        invitedBy: request.invitedBy,
        status: 'PENDING',
        expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000), // 7 days
      },
    });

    // Audit log
    await audit.log({
      action: 'WORKSPACE_INVITE_SENT',
      actor: request.invitedBy,
      actorRole: 'admin',
      resource: `workspace/${request.workspaceId}`,
      severity: 'info',
      details: `Invited ${request.email} as ${request.role}`,
    });

    return { success: true, inviteId: invite.id };
  } catch (error) {
    return { success: false, error: error instanceof Error ? error.message : 'Unknown error' };
  }
}

export async function acceptInvite(inviteId: string, userId: string): Promise<InviteResponse> {
  const prisma = getPrismaClient();

  try {
    const invite = await prisma.workspaceInvite.findUnique({
      where: { id: inviteId },
    });

    if (!invite || invite.status !== 'PENDING') {
      return { success: false, error: 'Invalid or expired invite' };
    }

    if (invite.expiresAt < new Date()) {
      return { success: false, error: 'Invite has expired' };
    }

    // Add member
    await prisma.workspaceMember.create({
      data: {
        workspaceId: invite.workspaceId,
        userId,
        role: invite.role,
      },
    });

    // Update invite
    await prisma.workspaceInvite.update({
      where: { id: inviteId },
      data: { status: 'ACCEPTED', acceptedAt: new Date() },
    });

    return { success: true };
  } catch (error) {
    return { success: false, error: error instanceof Error ? error.message : 'Unknown error' };
  }
}

export async function removeMember(
  workspaceId: string,
  userId: string,
  removedBy: string
): Promise<{ success: boolean; error?: string }> {
  const prisma = getPrismaClient();

  try {
    // Verify remover is admin
    const remover = await prisma.workspaceMember.findFirst({
      where: { workspaceId, userId: removedBy },
    });

    if (!remover || remover.role !== 'ADMIN') {
      return { success: false, error: 'Only admins can remove members' };
    }

    // Cannot remove self if last admin
    const adminCount = await prisma.workspaceMember.count({
      where: { workspaceId, role: 'ADMIN' },
    });

    const targetMember = await prisma.workspaceMember.findFirst({
      where: { workspaceId, userId },
    });

    if (targetMember?.role === 'ADMIN' && adminCount <= 1) {
      return { success: false, error: 'Cannot remove the last admin' };
    }

    await prisma.workspaceMember.deleteMany({
      where: { workspaceId, userId },
    });

    return { success: true };
  } catch (error) {
    return { success: false, error: error instanceof Error ? error.message : 'Unknown error' };
  }
}
