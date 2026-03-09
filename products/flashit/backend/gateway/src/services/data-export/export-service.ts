/**
 * Data Export Service
 * Simplified implementation for web-api layer
 * Provides synchronous data export without BullMQ queue
 */

import { prisma } from '../../lib/prisma';
import { randomUUID } from 'crypto';

export class DataExportService {
  /**
   * Request a data export for a user
   * Returns export data directly for now (can be enhanced with async processing later)
   */
  static async requestExport(userId: string, options?: {
    format?: 'json' | 'csv' | 'pdf' | 'zip';
    scope?: 'all' | 'sphere' | 'dateRange';
    includeMedia?: boolean;
    filters?: {
      sphereIds?: string[];
      startDate?: string;
      endDate?: string;
    };
  }) {
    const exportId = randomUUID();
    
    // Fetch user data
    const userData = await this.collectUserData(userId, options?.filters);
    
    return {
      exportId,
      data: userData,
      format: options?.format || 'json',
      createdAt: new Date().toISOString(),
    };
  }

  /**
   * Get export status
   */
  static async getExportStatus(exportId: string) {
    // For now, exports are synchronous, so always completed
    return {
      exportId,
      status: 'completed' as const,
      downloadUrl: `/api/privacy/export/${exportId}/download`,
      expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(), // 7 days
    };
  }

  /**
   * Collect all user data for export
   */
  private static async collectUserData(userId: string, filters?: {
    sphereIds?: string[];
    startDate?: string;
    endDate?: string;
  }) {
    // Get user info
    const user = await prisma.user.findUnique({
      where: { id: userId },
      select: {
        id: true,
        email: true,
        displayName: true,
        createdAt: true,
        updatedAt: true,
      },
    });

    // Get user's spheres
    const sphereAccess = await prisma.sphereAccess.findMany({
      where: {
        userId,
        revokedAt: null,
        ...(filters?.sphereIds ? { sphereId: { in: filters.sphereIds } } : {}),
      },
      include: {
        sphere: true,
      },
    });

    const sphereIds = sphereAccess.map(sa => sa.sphereId);

    // Get moments
    const momentWhere: any = {
      sphereId: { in: sphereIds },
      deletedAt: null,
    };

    if (filters?.startDate || filters?.endDate) {
      momentWhere.capturedAt = {};
      if (filters.startDate) {
        momentWhere.capturedAt.gte = new Date(filters.startDate);
      }
      if (filters.endDate) {
        momentWhere.capturedAt.lte = new Date(filters.endDate);
      }
    }

    const moments = await prisma.moment.findMany({
      where: momentWhere,
      include: {
        mediaReferences: true,
      },
    });

    return {
      user,
      spheres: sphereAccess.map(sa => sa.sphere),
      moments,
      exportedAt: new Date().toISOString(),
      totalRecords: {
        spheres: sphereAccess.length,
        moments: moments.length,
      },
    };
  }
}
