export type JsonChangeType = 'added' | 'removed' | 'changed';

export interface JsonChange {
  type: JsonChangeType;
  path: string;
  oldValue?: unknown;
  newValue?: unknown;
}

const isObject = (value: unknown): value is Record<string, unknown> =>
  value !== null && typeof value === 'object' && !Array.isArray(value);

const joinPath = (base: string, key: string): string => (base ? `${base}.${key}` : key);

const compareValues = (
  before: unknown,
  after: unknown,
  path: string,
  changes: JsonChange[],
) => {
  if (Object.is(before, after)) {
    return;
  }

  if (isObject(before) && isObject(after)) {
    const allKeys = new Set([...Object.keys(before), ...Object.keys(after)]);

    for (const key of allKeys) {
      const nextPath = joinPath(path, key);
      if (!(key in after)) {
        changes.push({ type: 'removed', path: nextPath, oldValue: before[key] });
      } else if (!(key in before)) {
        changes.push({ type: 'added', path: nextPath, newValue: after[key] });
      } else {
        compareValues(before[key], after[key], nextPath, changes);
      }
    }
    return;
  }

  if (Array.isArray(before) && Array.isArray(after)) {
    if (before.length !== after.length || before.some((value, index) => !Object.is(value, after[index]))) {
      changes.push({ type: 'changed', path, oldValue: before, newValue: after });
    }
    return;
  }

  changes.push({ type: 'changed', path, oldValue: before, newValue: after });
};

export const diffJson = (before: unknown, after: unknown): JsonChange[] => {
  const changes: JsonChange[] = [];

  if (before === undefined) {
    changes.push({ type: 'added', path: '', newValue: after });
    return changes;
  }

  if (after === undefined) {
    changes.push({ type: 'removed', path: '', oldValue: before });
    return changes;
  }

  compareValues(before, after, '', changes);
  return changes;
};
