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
