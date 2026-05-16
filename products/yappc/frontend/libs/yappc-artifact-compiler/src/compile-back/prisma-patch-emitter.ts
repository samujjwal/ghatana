/**
 * @fileoverview Prisma patch emitter for compile-back layer.
 *
 * Emits code patches for Prisma schema changes based on semantic model
 * modifications. Uses placeholder diffs; actual file operations happen in PatchCoordinator.
 */

import type { ChangeOp, PatchEmitter, TextPatch, PatchContext } from './types';
import type { SemanticModelElement, DataModel } from '../model/types';

// ============================================================================
// Prisma Patch Emitter
// ============================================================================

export class PrismaPatchEmitter implements PatchEmitter {
  readonly id = 'prisma-patch-emitter';
  readonly version = '1.0.0';

  canEmit(op: ChangeOp, element: SemanticModelElement): boolean {
    return element.kind === 'data-entity' && this.isPrismaOperation(op);
  }

  emit(op: ChangeOp, element: SemanticModelElement, context: PatchContext): TextPatch[] {
    if (element.kind !== 'data-entity') {
      return [];
    }

    const dataEntity = element as DataModel;
    const schemaPath = this.findPrismaSchemaPath(dataEntity, context);

    switch (op.kind) {
      case 'add-component':
        return [{
          relativePath: schemaPath,
          diff: `// YAPPC-ADD-ENTITY: ${dataEntity.tableName} in ${schemaPath}`,
          isAtomic: true,
          sourceChangeOpId: op.id,
          emitterId: this.id,
        }];
      case 'remove-component':
        return [{
          relativePath: schemaPath,
          diff: `// YAPPC-REMOVE-ENTITY: ${dataEntity.tableName} in ${schemaPath}`,
          isAtomic: true,
          sourceChangeOpId: op.id,
          emitterId: this.id,
        }];
      case 'add-prop':
        const fieldDef = op.after as { name: string; type: string } | undefined;
        return fieldDef ? [{
          relativePath: schemaPath,
          diff: `// YAPPC-ADD-FIELD: ${fieldDef.name}: ${fieldDef.type} in ${dataEntity.tableName}`,
          isAtomic: true,
          sourceChangeOpId: op.id,
          emitterId: this.id,
        }] : [];
      case 'remove-prop':
        const fieldName = op.before as string | undefined;
        return fieldName ? [{
          relativePath: schemaPath,
          diff: `// YAPPC-REMOVE-FIELD: ${fieldName} in ${dataEntity.tableName}`,
          isAtomic: true,
          sourceChangeOpId: op.id,
          emitterId: this.id,
        }] : [];
      case 'update-prop-type':
        const { name: updateName, type: newType } = op.after as { name: string; type: string } | { name?: string; type?: string };
        return updateName && newType ? [{
          relativePath: schemaPath,
          diff: `// YAPPC-UPDATE-FIELD-TYPE: ${updateName} → ${newType} in ${dataEntity.tableName}`,
          isAtomic: true,
          sourceChangeOpId: op.id,
          emitterId: this.id,
        }] : [];
      default:
        return [];
    }
  }

  private isPrismaOperation(op: ChangeOp): boolean {
    return [
      'add-component',
      'remove-component',
      'add-prop',
      'remove-prop',
      'update-prop-type',
    ].includes(op.kind);
  }

  private findPrismaSchemaPath(entity: DataModel, _context: PatchContext): string {
    for (const path of entity.provenance.sourcePaths) {
      if (path.endsWith('schema.prisma') || path.endsWith('.prisma')) {
        return path;
      }
    }
    return 'prisma/schema.prisma';
  }
}

export const prismaPatchEmitter = new PrismaPatchEmitter();
