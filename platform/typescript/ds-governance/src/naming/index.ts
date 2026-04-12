/**
 * @fileoverview Naming convention enforcement for design system tokens and components.
 */

/** Naming rule result. */
export interface NamingCheckResult {
  readonly valid: boolean;
  readonly violations: readonly NamingViolation[];
}

export interface NamingViolation {
  readonly path: string;
  readonly rule: string;
  readonly message: string;
}

const KEBAB_CASE_RE = /^[a-z][a-z0-9]*(-[a-z0-9]+)*$/;
const CAMEL_CASE_RE = /^[a-z][a-zA-Z0-9]*$/;
const PASCAL_CASE_RE = /^[A-Z][a-zA-Z0-9]*$/;

/** Check a token name conforms to kebab-case. */
export function checkTokenName(name: string, path: string): NamingViolation | null {
  if (!KEBAB_CASE_RE.test(name)) {
    return {
      path,
      rule: 'token-kebab-case',
      message: `Token name "${name}" must be kebab-case (e.g. primary-blue, spacing-md)`,
    };
  }
  return null;
}

/** Check a component name conforms to PascalCase. */
export function checkComponentName(name: string, path: string): NamingViolation | null {
  if (!PASCAL_CASE_RE.test(name)) {
    return {
      path,
      rule: 'component-pascal-case',
      message: `Component name "${name}" must be PascalCase (e.g. Button, InputField)`,
    };
  }
  return null;
}

/** Check a CSS custom property name (--token-name) is valid. */
export function checkCssVarName(name: string, path: string): NamingViolation | null {
  const withoutPrefix = name.startsWith('--') ? name.slice(2) : name;
  if (!KEBAB_CASE_RE.test(withoutPrefix)) {
    return {
      path,
      rule: 'css-var-kebab-case',
      message: `CSS variable "${name}" must use --kebab-case format`,
    };
  }
  return null;
}

/** Validate a map of token keys (flat) against naming rules. */
export function validateTokenNames(
  tokens: ReadonlyMap<string, unknown>,
): NamingCheckResult {
  const violations: NamingViolation[] = [];

  for (const key of tokens.keys()) {
    if (key.startsWith('$')) continue; // DTCG meta keys
    const v = checkTokenName(key, key);
    if (v) violations.push(v);
  }

  return { valid: violations.length === 0, violations };
}
