/**
 * @fileoverview Duplication detection for design system tokens and components.
 */

export interface DuplicationReport {
  readonly hasDuplicates: boolean;
  readonly duplicates: readonly DuplicateGroup[];
}

export interface DuplicateGroup {
  readonly value: string;
  readonly paths: readonly string[];
  readonly type: 'value' | 'alias' | 'name';
}

/**
 * Detects tokens that resolve to the same value (candidates for aliasing).
 */
export function detectValueDuplicates(
  resolvedTokens: ReadonlyMap<string, string>,
): DuplicationReport {
  const byValue = new Map<string, string[]>();

  for (const [path, value] of resolvedTokens) {
    const group = byValue.get(value) ?? [];
    group.push(path);
    byValue.set(value, group);
  }

  const duplicates: DuplicateGroup[] = [];
  for (const [value, paths] of byValue) {
    if (paths.length > 1) {
      duplicates.push({ value, paths, type: 'value' });
    }
  }

  return { hasDuplicates: duplicates.length > 0, duplicates };
}

/**
 * Detects component names that differ only by casing or minor variation.
 */
export function detectComponentNameDuplicates(
  componentNames: readonly string[],
): DuplicationReport {
  const byNormalized = new Map<string, string[]>();

  for (const name of componentNames) {
    const key = name.toLowerCase().replace(/[-_\s]/g, '');
    const group = byNormalized.get(key) ?? [];
    group.push(name);
    byNormalized.set(key, group);
  }

  const duplicates: DuplicateGroup[] = [];
  for (const [, names] of byNormalized) {
    if (names.length > 1) {
      duplicates.push({ value: names[0] ?? '', paths: names, type: 'name' });
    }
  }

  return { hasDuplicates: duplicates.length > 0, duplicates };
}
