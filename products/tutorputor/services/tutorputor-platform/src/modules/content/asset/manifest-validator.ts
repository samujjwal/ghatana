/**
 * Manifest Validation Service
 *
 * Validates artifact manifest payloads against canonical rules. Used
 * at publish time to ensure all manifests meet structural requirements
 * before content enters the PUBLISHED state.
 *
 * @doc.type class
 * @doc.purpose Validate artifact manifest payloads against rules
 * @doc.layer product
 * @doc.pattern Service
 */

import type {
  ManifestValidationResult,
  ManifestValidationRule,
  ManifestPayloadMap,
  WorkedExampleManifest,
  AnimationManifest,
  AssessmentManifest,
} from "@tutorputor/contracts/v1/artifact-manifests";
import { MANIFEST_VALIDATION_RULES } from "@tutorputor/contracts/v1/artifact-manifests";

// ---------------------------------------------------------------------------
// Field-path accessor
// ---------------------------------------------------------------------------

function getFieldValue(obj: Record<string, unknown>, path: string): unknown {
  const parts = path.split(".");
  let current: unknown = obj;
  for (const part of parts) {
    if (current == null || typeof current !== "object") return undefined;
    current = (current as Record<string, unknown>)[part];
  }
  return current;
}

// ---------------------------------------------------------------------------
// Rule evaluator
// ---------------------------------------------------------------------------

function evaluateRule(value: any, rule: ManifestValidationRule): boolean {
  switch (rule.rule) {
    case "required":
      return value !== undefined && value !== null && value !== "";
    case "min_length":
      return Array.isArray(value) && value.length >= (rule.expected as number);
    case "max_length":
      return Array.isArray(value) && value.length <= (rule.expected as number);
    case "min_value":
      return typeof value === "number" && value >= (rule.expected as number);
    case "max_value":
      return typeof value === "number" && value <= (rule.expected as number);
    case "pattern":
      return (
        typeof value === "string" &&
        new RegExp(rule.expected as string).test(value)
      );
    case "custom":
      return true; // custom rules need external evaluator
    default:
      return true;
  }
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Validate a manifest payload against canonical rules for its type.
 */
export function validateManifest<T extends keyof ManifestPayloadMap>(
  manifestType: T,
  payload: Record<string, unknown>,
): ManifestValidationResult {
  const rules = MANIFEST_VALIDATION_RULES[manifestType] ?? [];
  const violations: ManifestValidationResult["violations"] = [];

  for (const rule of rules) {
    const value = getFieldValue(payload, rule.field);
    if (!evaluateRule(value, rule)) {
      violations.push({ ...rule, actualValue: value });
    }
  }

  return {
    isValid: violations.filter((v: any) => v.severity === "error").length === 0,
    manifestType,
    violations,
    validatedAt: new Date().toISOString(),
  };
}

/**
 * Validate a worked example manifest.
 */
export function validateWorkedExample(
  payload: WorkedExampleManifest,
): ManifestValidationResult {
  return validateManifest(
    "worked_example",
    payload as unknown as Record<string, unknown>,
  );
}

/**
 * Validate an animation manifest.
 */
export function validateAnimation(
  payload: AnimationManifest,
): ManifestValidationResult {
  return validateManifest(
    "animation",
    payload as unknown as Record<string, unknown>,
  );
}

/**
 * Validate an assessment manifest.
 */
export function validateAssessment(
  payload: AssessmentManifest,
): ManifestValidationResult {
  return validateManifest(
    "assessment",
    payload as unknown as Record<string, unknown>,
  );
}
