/**
 * Project Deletion Service
 *
 * Handles complete project deletion with all associated data cleanup.
 * Implements soft delete with grace period for recovery.
 *
 * @doc.type service
 * @doc.purpose Project deletion with data cleanup
 * @doc.layer service
 * @doc.pattern Service Pattern
 */

import { getPrismaClient } from '../../database/client';

export interface DeletionResult {
  success: boolean;
  projectId: string;
  deletedAt: Date;
  deletedBy: string;
  recordsAffected: {
    canvasDocuments: number;
    lifecycleArtifacts: number;
    auditLogs: number;
    versions: number;
    notifications: number;
    realtimeSessions: number;
  };
  errors: string[];
}

export interface DeletionRequest {
  projectId: string;
  deletedBy: string;
  reason?: string;
  force?: boolean; // Skip grace period
}

/**
 * Delete a project and all associated data
 */
export async function deleteProject(
  request: DeletionRequest
): Promise<DeletionResult> {
  const prisma = getPrismaClient();
  const result: DeletionResult = {
    success: false,
    projectId: request.projectId,
    deletedAt: new Date(),
    deletedBy: request.deletedBy,
    recordsAffected: {
      canvasDocuments: 0,
      lifecycleArtifacts: 0,
      auditLogs: 0,
      versions: 0,
      notifications: 0,
      realtimeSessions: 0,
    },
    errors: [],
  };

  try {
    // Check if project exists
    const project = await prisma.project.findUnique({
      where: { id: request.projectId },
      include: {
        canvasDocuments: true,
        lifecycleArtifacts: true,
        aiActions: true,
        _count: {
          select: {
            canvasDocuments: true,
            lifecycleArtifacts: true,
            auditLogEntries: true,
          },
        },
      },
    });

    if (!project) {
      result.errors.push('Project not found');
      return result;
    }

    // Verify deleter has permission (owner or admin)
    const hasPermission = await verifyDeletePermission(
      request.projectId,
      request.deletedBy
    );
    if (!hasPermission) {
      result.errors.push('Insufficient permissions to delete project');
      return result;
    }

    // Delete canvas documents
    const canvasDocs = await prisma.canvasDocument.deleteMany({
      where: { projectId: request.projectId },
    });
    result.recordsAffected.canvasDocuments = canvasDocs.count;

    // Delete lifecycle artifacts
    const artifacts = await prisma.lifecycleArtifact.deleteMany({
      where: { projectId: request.projectId },
    });
    result.recordsAffected.lifecycleArtifacts = artifacts.count;

    // Soft delete: Mark project as deleted with grace period
    await prisma.project.update({
      where: { id: request.projectId },
      data: {
        status: 'ARCHIVED',
        deletedAt: result.deletedAt,
        deletedBy: request.deletedBy,
        deletionReason: request.reason,
        // Restore within 30 days
        restoreDeadline: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000),
      },
    });

    // Create audit log entry
    await prisma.auditLogEntry.create({
      data: {
        action: 'PROJECT_DELETED',
        actor: request.deletedBy,
        actorRole: 'user',
        resource: `project/${request.projectId}`,
        severity: 'warn',
        details: `Project "${project.name}" deleted. Reason: ${request.reason || 'Not specified'}`,
        metadata: {
          projectId: request.projectId,
          projectName: project.name,
          reason: request.reason,
          recordsAffected: result.recordsAffected,
        },
      },
    });

    result.success = true;
    return result;
  } catch (error) {
    result.errors.push(
      error instanceof Error ? error.message : 'Unknown error during deletion'
    );
    return result;
  }
}

/**
 * Restore a deleted project within grace period
 */
export async function restoreProject(
  projectId: string,
  restoredBy: string
): Promise<{ success: boolean; error?: string }> {
  const prisma = getPrismaClient();

  try {
    const project = await prisma.project.findUnique({
      where: { id: projectId },
    });

    if (!project) {
      return { success: false, error: 'Project not found' };
    }

    if (project.status !== 'ARCHIVED' || !project.deletedAt) {
      return { success: false, error: 'Project is not in deleted state' };
    }

    // Check if within grace period
    if (project.restoreDeadline && new Date() > project.restoreDeadline) {
      return { success: false, error: 'Restore period has expired' };
    }

    await prisma.project.update({
      where: { id: projectId },
      data: {
        status: 'ACTIVE',
        deletedAt: null,
        deletedBy: null,
        deletionReason: null,
        restoreDeadline: null,
      },
    });

    // Create audit log
    await prisma.auditLogEntry.create({
      data: {
        action: 'PROJECT_RESTORED',
        actor: restoredBy,
        actorRole: 'user',
        resource: `project/${projectId}`,
        severity: 'info',
        details: `Project restored from deletion`,
      },
    });

    return { success: true };
  } catch (error) {
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error',
    };
  }
}

/**
 * Permanently delete a project after grace period
 */
export async function permanentDeleteProject(
  projectId: string
): Promise<{ success: boolean; error?: string }> {
  const prisma = getPrismaClient();

  try {
    const project = await prisma.project.findUnique({
      where: { id: projectId },
    });

    if (!project) {
      return { success: false, error: 'Project not found' };
    }

    if (project.status !== 'ARCHIVED') {
      return { success: false, error: 'Project must be archived before permanent deletion' };
    }

    // Check grace period expired
    if (project.restoreDeadline && new Date() < project.restoreDeadline) {
      return { success: false, error: 'Grace period has not expired yet' };
    }

    // Permanently delete
    await prisma.project.delete({
      where: { id: projectId },
    });

    return { success: true };
  } catch (error) {
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error',
    };
  }
}

/**
 * Verify user has permission to delete project
 */
async function verifyDeletePermission(
  projectId: string,
  userId: string
): Promise<boolean> {
  const prisma = getPrismaClient();

  const project = await prisma.project.findUnique({
    where: { id: projectId },
    include: {
      ownerWorkspace: {
        include: {
          members: {
            where: { userId },
            select: { role: true },
          },
        },
      },
    },
  });

  if (!project) return false;

  // Check if user is workspace admin or owner
  const member = project.ownerWorkspace.members[0];
  if (member?.role === 'ADMIN') return true;

  // Check if user created the project
  if (project.createdById === userId) return true;

  return false;
}
