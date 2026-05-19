/**
 * @fileoverview Workflow patch emitter for compile-back layer.
 *
 * Emits code patches for CI/CD workflow files (GitHub Actions, GitLab CI, etc.)
 * based on semantic model modifications. Uses placeholder diffs; actual file operations happen in PatchCoordinator.
 */

import type { ChangeOp, PatchEmitter, TextPatch, PatchContext } from './types';
import type { SemanticModelElement, WorkflowModel } from '../model/types';

// ============================================================================
// Workflow Patch Emitter
// ============================================================================

export class WorkflowPatchEmitter implements PatchEmitter {
  readonly id = 'workflow-patch-emitter';
  readonly version = '1.0.0';

  canEmit(op: ChangeOp, element: SemanticModelElement): boolean {
    return element.kind === 'workflow' && this.isWorkflowOperation(op);
  }

  emit(op: ChangeOp, element: SemanticModelElement, context: PatchContext): TextPatch[] {
    if (element.kind !== 'workflow') {
      return [];
    }

    const workflow = element as WorkflowModel;
    const workflowPath = this.findWorkflowPath(workflow, context);

    switch (op.kind) {
      case 'add-component':
        return [{
          relativePath: workflowPath,
          diff: `// YAPPC-ADD-WORKFLOW: ${workflow.name} in ${workflowPath}`,
          isAtomic: true,
          sourceChangeOpId: op.id,
          emitterId: this.id,
          ranges: [],
        }];
      case 'remove-component':
        return [{
          relativePath: workflowPath,
          diff: `// YAPPC-REMOVE-WORKFLOW: ${workflow.name} in ${workflowPath}`,
          isAtomic: true,
          sourceChangeOpId: op.id,
          emitterId: this.id,
          ranges: [],
        }];
      case 'add-prop':
        const job = op.after as { name: string; command: string } | undefined;
        return job ? [{
          relativePath: workflowPath,
          diff: `// YAPPC-ADD-JOB: ${job.name} in ${workflow.name}`,
          isAtomic: true,
          sourceChangeOpId: op.id,
          emitterId: this.id,
          ranges: [],
        }] : [];
      case 'remove-prop':
        const jobName = op.before as string | undefined;
        return jobName ? [{
          relativePath: workflowPath,
          diff: `// YAPPC-REMOVE-JOB: ${jobName} in ${workflow.name}`,
          isAtomic: true,
          sourceChangeOpId: op.id,
          emitterId: this.id,
          ranges: [],
        }] : [];
      default:
        return [];
    }
  }

  private isWorkflowOperation(op: ChangeOp): boolean {
    return [
      'add-component',
      'remove-component',
      'add-prop',
      'remove-prop',
    ].includes(op.kind);
  }

  private findWorkflowPath(workflow: WorkflowModel, _context: PatchContext): string {
    for (const path of workflow.provenance.sourcePaths) {
      if (path.endsWith('.yml') || path.endsWith('.yaml') || path.includes('.github/workflows')) {
        return path;
      }
    }
    return '.github/workflows/ci.yml';
  }
}

export const workflowPatchEmitter = new WorkflowPatchEmitter();
