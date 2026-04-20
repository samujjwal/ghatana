/**
 * Data Export Service
 *
 * Export analytics data in multiple formats (CSV, Excel, PDF).
 *
 * @doc.type class
 * @doc.purpose Export analytics data for reporting and analysis
 * @doc.layer product
 * @doc.pattern Export Service
 */

import { createStandaloneLogger } from '@tutorputor/core/logger';
import type { TutorPrismaClient } from '@tutorputor/core/db';

const logger = createStandaloneLogger({ component: 'DataExportService' });

export type ExportFormat = 'csv' | 'excel' | 'json';
export type ExportScope = 'tenant' | 'classroom' | 'student' | 'assessment';

export interface ExportRequest {
  tenantId: string;
  format: ExportFormat;
  scope: ExportScope;
  scopeId?: string;
  dateRange?: {
    startDate: string;
    endDate: string;
  };
  anonymize?: boolean;
  includeMetadata?: boolean;
}

export interface ExportResult {
  exportId: string;
  format: ExportFormat;
  data: string;
  filename: string;
  rowCount: number;
  generatedAt: string;
}

export class DataExportService {
  constructor(private readonly prisma: TutorPrismaClient) {}

  /**
   * Export analytics data
   */
  async exportData(request: ExportRequest): Promise<ExportResult> {
    logger.info({
      message: 'Exporting analytics data',
      tenantId: request.tenantId,
      format: request.format,
      scope: request.scope,
    });

    const exportId = `export-${Date.now()}`;
    const startDate = request.dateRange?.startDate
      ? new Date(request.dateRange.startDate)
      : new Date(Date.now() - 30 * 24 * 60 * 60 * 1000);
    const endDate = request.dateRange?.endDate
      ? new Date(request.dateRange.endDate)
      : new Date();

    // Fetch data based on scope
    const data = await this.fetchExportData(request, startDate, endDate);

    // Format data
    const formattedData = this.formatData(data, request.format, request.anonymize ?? false);

    const filename = this.generateFilename(request, exportId);

    logger.info({
      message: 'Export completed',
      exportId,
      format: request.format,
      rowCount: data.length,
    });

    return {
      exportId,
      format: request.format,
      data: formattedData,
      filename,
      rowCount: data.length,
      generatedAt: new Date().toISOString(),
    };
  }

  /**
   * Get available export formats
   */
  getAvailableFormats(): ExportFormat[] {
    return ['csv', 'excel', 'json'];
  }

  /**
   * Get export history for a tenant
   */
  async getExportHistory(tenantId: string, limit = 10) {
    // In production, this would query an exports table
    return [];
  }

  private async fetchExportData(
    request: ExportRequest,
    startDate: Date,
    endDate: Date,
  ): Promise<Record<string, unknown>[]> {
    switch (request.scope) {
      case 'tenant':
        return this.fetchTenantData(request.tenantId, startDate, endDate);
      case 'classroom':
        return this.fetchClassroomData(request.tenantId, request.scopeId!, startDate, endDate);
      case 'student':
        return this.fetchStudentData(request.tenantId, request.scopeId!, startDate, endDate);
      case 'assessment':
        return this.fetchAssessmentData(request.tenantId, request.scopeId!, startDate, endDate);
      default:
        return [];
    }
  }

  private async fetchTenantData(
    tenantId: string,
    startDate: Date,
    endDate: Date,
  ): Promise<Record<string, unknown>[]> {
    const [events, enrollments, attempts] = await Promise.all([
      this.prisma.learningEvent.findMany({
        where: { tenantId, timestamp: { gte: startDate, lte: endDate } },
        take: 10000,
      }),
      this.prisma.enrollment.findMany({
        where: { tenantId, startedAt: { gte: startDate, lte: endDate } },
        take: 5000,
      }),
      this.prisma.assessmentAttempt.findMany({
        where: {
          assessment: { tenantId },
          startedAt: { gte: startDate, lte: endDate },
        },
        take: 5000,
      }),
    ]);

    return [
      ...events.map((e) => ({
        type: 'event',
        eventType: e.eventType,
        userId: e.userId,
        timestamp: e.timestamp,
      })),
      ...enrollments.map((e) => ({
        type: 'enrollment',
        userId: e.userId,
        moduleId: e.moduleId,
        progressPercent: e.progressPercent,
        status: e.status,
      })),
      ...attempts.map((a) => ({
        type: 'attempt',
        userId: a.userId,
        assessmentId: a.assessmentId,
        scorePercent: a.scorePercent,
        status: a.status,
      })),
    ];
  }

  private async fetchClassroomData(
    tenantId: string,
    classroomId: string,
    startDate: Date,
    endDate: Date,
  ): Promise<Record<string, unknown>[]> {
    const classroomMembers = await this.prisma.classroomMember.findMany({
      where: { classroomId },
      take: 5000,
    });

    const memberIds = classroomMembers.map((member) => member.userId);
    if (memberIds.length === 0) {
      return [];
    }

    const [enrollments, users] = await Promise.all([
      this.prisma.enrollment.findMany({
        where: {
          tenantId,
          userId: { in: memberIds },
          startedAt: { gte: startDate, lte: endDate },
        },
        take: 5000,
      }),
      this.prisma.user.findMany({
        where: {
          tenantId,
          id: { in: memberIds },
        },
        select: { id: true, email: true, displayName: true },
      }),
    ]);

    const usersById = new Map(users.map((user) => [user.id, user]));

    return enrollments.map((e) => ({
      userId: e.userId,
      email: usersById.get(e.userId)?.email,
      displayName: usersById.get(e.userId)?.displayName,
      moduleId: e.moduleId,
      progressPercent: e.progressPercent,
      status: e.status,
      timeSpentSeconds: e.timeSpentSeconds,
    }));
  }

  private async fetchStudentData(
    tenantId: string,
    studentId: string,
    startDate: Date,
    endDate: Date,
  ): Promise<Record<string, unknown>[]> {
    const [events, enrollments, attempts] = await Promise.all([
      this.prisma.learningEvent.findMany({
        where: { tenantId, userId: studentId, timestamp: { gte: startDate, lte: endDate } },
        take: 1000,
      }),
      this.prisma.enrollment.findMany({
        where: { tenantId, userId: studentId },
        take: 100,
      }),
      this.prisma.assessmentAttempt.findMany({
        where: { tenantId, userId: studentId, startedAt: { gte: startDate, lte: endDate } },
        take: 100,
      }),
    ]);

    return [
      ...events.map((e) => ({
        type: 'event',
        eventType: e.eventType,
        timestamp: e.timestamp,
      })),
      ...enrollments.map((e) => ({
        type: 'enrollment',
        moduleId: e.moduleId,
        progressPercent: e.progressPercent,
        status: e.status,
      })),
      ...attempts.map((a) => ({
        type: 'attempt',
        assessmentId: a.assessmentId,
        scorePercent: a.scorePercent,
        status: a.status,
      })),
    ];
  }

  private async fetchAssessmentData(
    tenantId: string,
    assessmentId: string,
    startDate: Date,
    endDate: Date,
  ): Promise<Record<string, unknown>[]> {
    const attempts = await this.prisma.assessmentAttempt.findMany({
      where: {
        tenantId,
        assessmentId,
        startedAt: { gte: startDate, lte: endDate },
      },
      take: 10000,
    });

    return attempts.map((a) => ({
      userId: a.userId,
      scorePercent: a.scorePercent,
      status: a.status,
      startedAt: a.startedAt,
      submittedAt: a.submittedAt,
      timeSpentSeconds: a.timeSpentSeconds,
    }));
  }

  private formatData(data: Record<string, unknown>[], format: ExportFormat, anonymize: boolean): string {
    if (anonymize) {
      data = this.anonymizeData(data);
    }

    switch (format) {
      case 'csv':
        return this.toCSV(data);
      case 'excel':
        return this.toExcel(data);
      case 'json':
        return JSON.stringify(data, null, 2);
      default:
        return JSON.stringify(data, null, 2);
    }
  }

  private anonymizeData(data: Record<string, unknown>[]): Record<string, unknown>[] {
    return data.map((row) => {
      const anonymized = { ...row };
      if (anonymized.userId) anonymized.userId = this.hashId(String(anonymized.userId));
      if (anonymized.email) anonymized.email = '***@***.***';
      if (anonymized.displayName) anonymized.displayName = '***';
      return anonymized;
    });
  }

  private hashId(id: string): string {
    let hash = 0;
    for (let i = 0; i < id.length; i++) {
      const char = id.charCodeAt(i);
      hash = (hash << 5) - hash + char;
      hash = hash & hash;
    }
    return `user-${Math.abs(hash)}`;
  }

  private toCSV(data: Record<string, unknown>[]): string {
    if (data.length === 0) return '';
    const firstRow = data[0];
    if (!firstRow) return '';
    const headers = Object.keys(firstRow);
    const headerRow = headers.join(',');
    const dataRows = data.map((row) =>
      headers.map((h) => JSON.stringify(row[h] ?? '')).join(','),
    );
    return [headerRow, ...dataRows].join('\n');
  }

  private toExcel(data: Record<string, unknown>[]): string {
    // Simplified - in production, use a proper Excel library
    return this.toCSV(data);
  }

  private generateFilename(request: ExportRequest, exportId: string): string {
    const date = new Date().toISOString().split('T')[0] ?? 'unknown-date';
    return `tutorputor-${request.scope}-${request.format}-${date}-${exportId}.${request.format}`;
  }
}
