import type { CRDTOperation, VectorClock } from '../core/index.js';

export type AutoResolutionRule =
  | 'distinct-position-insert'
  | 'same-position-insert'
  | 'delete-wins'
  | 'field-merge'
  | 'vector-clock-wins'
  | 'move-average'
  | 'none';

export interface AutoResolutionResult {
  resolved: boolean;
  rule: AutoResolutionRule;
  operations: CRDTOperation[];
  description: string;
}

interface Point {
  x: number;
  y: number;
}

export function autoResolveOperations(
  operationA: CRDTOperation,
  operationB: CRDTOperation
): AutoResolutionResult {
  if (operationA.targetId !== operationB.targetId) {
    return unresolved('Operations target different resources');
  }

  if (operationA.type === 'insert' && operationB.type === 'insert') {
    const positionA = extractPosition(operationA.data);
    const positionB = extractPosition(operationB.data);

    if (positionA !== null && positionB !== null) {
      const orderedOperations = [operationA, operationB].sort((left, right) => {
        const leftPosition = extractPosition(left.data) ?? 0;
        const rightPosition = extractPosition(right.data) ?? 0;

        if (leftPosition !== rightPosition) {
          return leftPosition - rightPosition;
        }

        return compareReplicaIds(left, right);
      });

      if (positionA === positionB) {
        return {
          resolved: true,
          rule: 'same-position-insert',
          operations: orderedOperations,
          description: 'Concurrent inserts at the same position are ordered deterministically by replica ID.',
        };
      }

      return {
        resolved: true,
        rule: 'distinct-position-insert',
        operations: orderedOperations,
        description: 'Concurrent inserts at different positions are both retained and applied in positional order.',
      };
    }
  }

  if (isDeleteUpdatePair(operationA, operationB)) {
    const deleteOperation = operationA.type === 'delete' ? operationA : operationB;
    return {
      resolved: true,
      rule: 'delete-wins',
      operations: [deleteOperation],
      description: 'Delete operations take precedence over concurrent updates on the same target.',
    };
  }

  if (operationA.type === 'update' && operationB.type === 'update') {
    const fieldsA = extractFieldNames(operationA.data);
    const fieldsB = extractFieldNames(operationB.data);
    const overlappingFields = [...fieldsA].filter((field) => fieldsB.has(field));

    if (fieldsA.size > 0 && fieldsB.size > 0 && overlappingFields.length === 0) {
      return {
        resolved: true,
        rule: 'field-merge',
        operations: [operationA, operationB],
        description: 'Concurrent updates touching different fields are both preserved.',
      };
    }

    const winner = selectPreferredOperation(operationA, operationB);
    return {
      resolved: true,
      rule: 'vector-clock-wins',
      operations: [winner],
      description: 'Concurrent updates to the same field resolve deterministically using vector clocks and stable tie-breakers.',
    };
  }

  if (operationA.type === 'move' && operationB.type === 'move') {
    const pointA = extractPoint(operationA.data);
    const pointB = extractPoint(operationB.data);

    if (pointA !== null && pointB !== null) {
      const mergedOperation = buildMergedMoveOperation(operationA, operationB, pointA, pointB);
      return {
        resolved: true,
        rule: 'move-average',
        operations: [mergedOperation],
        description: 'Concurrent move operations are averaged to keep the target centered between both edits.',
      };
    }
  }

  return unresolved('No automatic resolution rule matched the conflicting operations.');
}

function unresolved(description: string): AutoResolutionResult {
  return {
    resolved: false,
    rule: 'none',
    operations: [],
    description,
  };
}

function compareVectorClocks(clockA: VectorClock, clockB: VectorClock): number {
  let aGreater = false;
  let bGreater = false;

  const allReplicaIds = new Set([...clockA.values.keys(), ...clockB.values.keys()]);

  for (const replicaId of allReplicaIds) {
    const valueA = clockA.values.get(replicaId) ?? 0;
    const valueB = clockB.values.get(replicaId) ?? 0;

    if (valueA > valueB) {
      aGreater = true;
    }
    if (valueB > valueA) {
      bGreater = true;
    }
  }

  if (aGreater && !bGreater) {
    return 1;
  }
  if (bGreater && !aGreater) {
    return -1;
  }
  if (!aGreater && !bGreater) {
    return 0;
  }
  return 2;
}

function selectPreferredOperation(operationA: CRDTOperation, operationB: CRDTOperation): CRDTOperation {
  const vectorComparison = compareVectorClocks(operationA.vectorClock, operationB.vectorClock);

  if (vectorComparison === 1) {
    return operationA;
  }
  if (vectorComparison === -1) {
    return operationB;
  }
  if (operationA.timestamp !== operationB.timestamp) {
    return operationA.timestamp > operationB.timestamp ? operationA : operationB;
  }

  return compareReplicaIds(operationA, operationB) <= 0 ? operationA : operationB;
}

function compareReplicaIds(operationA: CRDTOperation, operationB: CRDTOperation): number {
  if (operationA.replicaId !== operationB.replicaId) {
    return operationA.replicaId.localeCompare(operationB.replicaId);
  }

  return operationA.id.localeCompare(operationB.id);
}

function isDeleteUpdatePair(operationA: CRDTOperation, operationB: CRDTOperation): boolean {
  return (
    (operationA.type === 'delete' && operationB.type === 'update') ||
    (operationA.type === 'update' && operationB.type === 'delete')
  );
}

function extractFieldNames(data: unknown): Set<string> {
  if (!isRecord(data)) {
    return new Set();
  }

  const explicitField = data['field'];
  if (typeof explicitField === 'string' && explicitField.length > 0) {
    return new Set([explicitField]);
  }

  const payload = data['value'];
  if (isRecord(payload)) {
    return new Set(Object.keys(payload));
  }

  return new Set(
    Object.keys(data).filter((key) => !['field', 'value', 'position', 'index', 'x', 'y'].includes(key))
  );
}

function extractPosition(data: unknown): number | null {
  if (!isRecord(data)) {
    return null;
  }

  const directPosition = data['position'];
  if (typeof directPosition === 'number') {
    return directPosition;
  }

  const directIndex = data['index'];
  if (typeof directIndex === 'number') {
    return directIndex;
  }

  return null;
}

function extractPoint(data: unknown): Point | null {
  if (!isRecord(data)) {
    return null;
  }

  const x = data['x'];
  const y = data['y'];
  if (typeof x === 'number' && typeof y === 'number') {
    return { x, y };
  }

  const position = data['position'];
  if (isRecord(position)) {
    const nestedX = position['x'];
    const nestedY = position['y'];
    if (typeof nestedX === 'number' && typeof nestedY === 'number') {
      return { x: nestedX, y: nestedY };
    }
  }

  return null;
}

function buildMergedMoveOperation(
  operationA: CRDTOperation,
  operationB: CRDTOperation,
  pointA: Point,
  pointB: Point
): CRDTOperation {
  const preferredOperation = selectPreferredOperation(operationA, operationB);
  const mergedPoint: Point = {
    x: (pointA.x + pointB.x) / 2,
    y: (pointA.y + pointB.y) / 2,
  };

  const baseData = isRecord(preferredOperation.data) ? preferredOperation.data : {};
  const usesNestedPosition = isRecord(baseData['position']);

  return {
    ...preferredOperation,
    id: `${preferredOperation.id}-auto-merged`,
    data: usesNestedPosition
      ? {
          ...baseData,
          position: {
            ...(isRecord(baseData['position']) ? baseData['position'] : {}),
            ...mergedPoint,
          },
        }
      : {
          ...baseData,
          ...mergedPoint,
        },
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}