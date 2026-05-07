import { yappcApi } from '@/lib/api/client';

import type {
  CommandAuditRecord,
  CommandResult,
} from './PageBuilderCommands';

export interface PageBuilderCommandAuditContext {
  readonly userId: string;
  readonly tenantId: string;
  readonly workspaceId: string;
  readonly projectId: string;
  readonly artifactId?: string;
  readonly phase?: string;
}

function toAuditType(commandType: string): string {
  return `PAGE_BUILDER_${commandType.toUpperCase().replaceAll('-', '_')}`;
}

function toTelemetryAuditType(event: string): string {
  return `PAGE_BUILDER_TELEMETRY_${event.toUpperCase().replaceAll('-', '_')}`;
}

function toMetadataRecord(data: unknown): Record<string, unknown> {
  if (typeof data === 'object' && data !== null && !Array.isArray(data)) {
    return data as Record<string, unknown>;
  }

  return { value: data };
}

export async function emitPageBuilderCommandAudit(
  context: PageBuilderCommandAuditContext,
  record: CommandAuditRecord,
  result: CommandResult,
): Promise<void> {
  await yappcApi.audit.emit({
    type: toAuditType(record.commandType),
    userId: context.userId,
    projectId: context.projectId,
    artifactId: context.artifactId ?? record.artifactId,
    flowStage: 'BUILD',
    phase: context.phase ?? 'SHAPE',
    description: `Page builder command ${record.commandType} ${record.success ? 'completed' : 'failed'}.`,
    metadata: {
      tenantId: context.tenantId,
      workspaceId: context.workspaceId,
      correlationId: record.correlationId,
      commandId: record.commandId,
      commandType: record.commandType,
      nodeId: record.nodeId,
      changedNodeIds: record.changedNodeIds,
      beforeDocumentId: record.beforeDocumentId,
      afterDocumentId: record.afterDocumentId,
      success: record.success,
      validationErrorCount: result.validationErrors?.length ?? 0,
      error: result.error,
    },
  });
}

export async function emitPageBuilderCommandTelemetry(
  context: PageBuilderCommandAuditContext,
  event: string,
  data: unknown,
): Promise<void> {
  const metadata = toMetadataRecord(data);

  await yappcApi.audit.emit({
    type: toTelemetryAuditType(event),
    userId: context.userId,
    projectId: context.projectId,
    artifactId: context.artifactId,
    flowStage: 'BUILD_TELEMETRY',
    phase: context.phase ?? 'SHAPE',
    description: `Page builder telemetry event ${event} recorded.`,
    metadata: {
      tenantId: context.tenantId,
      workspaceId: context.workspaceId,
      event,
      ...metadata,
    },
  });
}
