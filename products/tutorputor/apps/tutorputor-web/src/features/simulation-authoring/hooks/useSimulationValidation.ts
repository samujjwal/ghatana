/**
 * useSimulationValidation Hook
 *
 * Hook for validating simulation manifests with real-time feedback.
 * Integrates with backend validation and provides local quick validation.
 *
 * @doc.type hook
 * @doc.purpose Simulation manifest validation
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useCallback, useMemo, useRef } from "react";
import { useMutation } from "@tanstack/react-query";
import type {
  SimulationManifest,
  SimulationDomain,
  SimulationStep,
  SimEntity,
  SimAction,
  ManifestValidationResult,
} from "@ghatana/tutorputor-contracts/v1/simulation/types";

export type { SimulationManifest, SimulationDomain, ManifestValidationResult };

// =============================================================================
// Types
// =============================================================================

export type ValidationSeverity = "error" | "warning" | "info";

export interface ValidationError {
  id: string;
  type: ValidationSeverity;
  code: string;
  message: string;
  severity: number; // 1 = critical, 2 = major, 3 = minor
  location?: ValidationLocation;
  suggestion?: string;
  autoFix?: () => SimulationManifest;
}

export interface ValidationLocation {
  path: string;
  stepIndex?: number;
  entityId?: string;
  actionIndex?: number;
  line?: number;
  column?: number;
}

export interface ValidationSummary {
  isValid: boolean;
  score: number; // 0-100
  errors: number;
  warnings: number;
  info: number;
  lastValidated: Date | null;
  duration: number; // ms
}

export interface UseSimulationValidationOptions {
  /**
   * API base URL for backend validation.
   */
  apiBaseUrl?: string;

  /**
   * Enable automatic validation on manifest change.
   */
  autoValidate?: boolean;

  /**
   * Debounce delay for auto-validation (ms).
   */
  debounceMs?: number;

  /**
   * Enable backend validation (requires API).
   */
  useBackendValidation?: boolean;

  /**
   * Additional domain-specific rules.
   */
  customRules?: ValidationRule[];
}

export interface UseSimulationValidationReturn {
  /**
   * All validation errors.
   */
  errors: ValidationError[];

  /**
   * Validation summary.
   */
  summary: ValidationSummary;

  /**
   * Whether validation is in progress.
   */
  isValidating: boolean;

  /**
   * Validate manifest manually.
   */
  validate: (manifest: SimulationManifest) => Promise<ValidationError[]>;

  /**
   * Clear validation state.
   */
  clearValidation: () => void;

  /**
   * Get errors for specific location.
   */
  getErrorsForStep: (stepIndex: number) => ValidationError[];
  getErrorsForEntity: (entityId: string) => ValidationError[];
  getErrorsForPath: (path: string) => ValidationError[];

  /**
   * Apply auto-fix if available.
   */
  applyFix: (errorId: string) => SimulationManifest | null;
}

export interface ValidationRule {
  id: string;
  name: string;
  description: string;
  domains?: SimulationDomain[];
  severity: number;
  validate: (manifest: SimulationManifest) => ValidationError[];
}

// =============================================================================
// Built-in Validation Rules
// =============================================================================

const createError = (
  code: string,
  message: string,
  type: ValidationError["type"],
  severity: number,
  location?: ValidationLocation,
  suggestion?: string,
): ValidationError => ({
  id: `${code}_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
  code,
  message,
  type,
  severity,
  location,
  suggestion,
});

const BUILTIN_RULES: ValidationRule[] = [
  // Required Fields
  {
    id: "required-id",
    name: "Manifest ID Required",
    description: "Every manifest must have a unique ID",
    severity: 1,
    validate: (manifest) => {
      if (!manifest.id) {
        return [
          createError(
            "MISSING_ID",
            "Manifest is missing a required ID field",
            "error",
            1,
            { path: "id" },
            "Generate a unique ID for this manifest",
          ),
        ];
      }
      return [];
    },
  },
  {
    id: "required-title",
    name: "Manifest Title Required",
    description: "Every manifest must have a title",
    severity: 1,
    validate: (manifest) => {
      if (!manifest.title?.trim()) {
        return [
          createError(
            "MISSING_TITLE",
            "Manifest is missing a title",
            "error",
            1,
            { path: "title" },
            "Add a descriptive title for this simulation",
          ),
        ];
      }
      return [];
    },
  },
  {
    id: "required-domain",
    name: "Domain Required",
    description: "Every manifest must specify a domain",
    severity: 1,
    validate: (manifest) => {
      if (!manifest.domain) {
        return [
          createError(
            "MISSING_DOMAIN",
            "Manifest is missing a domain",
            "error",
            1,
            { path: "domain" },
            "Specify the simulation domain (CS_DISCRETE, PHYSICS, etc.)",
          ),
        ];
      }
      return [];
    },
  },

  // Steps Validation
  {
    id: "min-steps",
    name: "Minimum Steps",
    description: "Manifest should have at least one step",
    severity: 2,
    validate: (manifest) => {
      if (!manifest.steps || manifest.steps.length === 0) {
        return [
          createError(
            "NO_STEPS",
            "Manifest has no steps defined",
            "warning",
            2,
            { path: "steps" },
            "Add at least one step to the simulation",
          ),
        ];
      }
      return [];
    },
  },
  {
    id: "step-ids",
    name: "Step IDs",
    description: "All steps must have unique IDs",
    severity: 1,
    validate: (manifest) => {
      const errors: ValidationError[] = [];
      const seenIds = new Set<string>();

      manifest.steps?.forEach((step, index) => {
        if (!step.id) {
          errors.push(
            createError(
              "STEP_MISSING_ID",
              `Step ${index + 1} is missing an ID`,
              "error",
              1,
              { path: `steps[${index}].id`, stepIndex: index },
              "Generate a unique ID for this step",
            ),
          );
        } else if (seenIds.has(step.id as string)) {
          errors.push(
            createError(
              "DUPLICATE_STEP_ID",
              `Duplicate step ID: ${step.id}`,
              "error",
              1,
              { path: `steps[${index}].id`, stepIndex: index },
              "Use a unique ID for each step",
            ),
          );
        } else {
          seenIds.add(step.id as string);
        }
      });

      return errors;
    },
  },
  {
    id: "step-narration",
    name: "Step Narration",
    description: "Steps should have narration for accessibility",
    severity: 3,
    validate: (manifest) => {
      const errors: ValidationError[] = [];

      manifest.steps?.forEach((step, index) => {
        if (!step.narration?.trim()) {
          errors.push(
            createError(
              "EMPTY_NARRATION",
              `Step ${index + 1} has no narration`,
              "info",
              3,
              { path: `steps[${index}].narration`, stepIndex: index },
              "Add narration to improve accessibility",
            ),
          );
        }
      });

      return errors;
    },
  },

  // Entity Validation
  {
    id: "entity-ids",
    name: "Entity IDs",
    description: "All entities must have unique IDs",
    severity: 1,
    validate: (manifest) => {
      const errors: ValidationError[] = [];
      const seenIds = new Set<string>();

      manifest.initialEntities?.forEach((entity, index) => {
        if (!entity.id) {
          errors.push(
            createError(
              "ENTITY_MISSING_ID",
              `Entity ${index + 1} is missing an ID`,
              "error",
              1,
              { path: `initialEntities[${index}].id` },
              "Generate a unique ID for this entity",
            ),
          );
        } else if (seenIds.has(entity.id as string)) {
          errors.push(
            createError(
              "DUPLICATE_ENTITY_ID",
              `Duplicate entity ID: ${entity.id}`,
              "error",
              1,
              {
                path: `initialEntities[${index}].id`,
                entityId: entity.id as string,
              },
              "Use a unique ID for each entity",
            ),
          );
        } else {
          seenIds.add(entity.id as string);
        }
      });

      return errors;
    },
  },

  // Reference Validation
  {
    id: "entity-references",
    name: "Entity References",
    description: "Actions should reference existing entities",
    severity: 2,
    validate: (manifest) => {
      const errors: ValidationError[] = [];
      const entityIds = new Set(
        manifest.initialEntities?.map((e) => e.id as string) || [],
      );

      // Track entities created during simulation
      manifest.steps?.forEach((step) => {
        step.actions?.forEach((action) => {
          if (action.type === "CREATE_ENTITY" && action.entityId) {
            entityIds.add(action.entityId as string);
          }
        });
      });

      // Check references
      manifest.steps?.forEach((step, stepIndex) => {
        step.actions?.forEach((action, actionIndex) => {
          if (action.entityId && action.type !== "CREATE_ENTITY") {
            // Check if entity exists before this step
            const existsBeforeStep = manifest.initialEntities?.some(
              (e) => e.id === action.entityId,
            );
            const createdBefore = manifest.steps
              ?.slice(0, stepIndex)
              .some((s) =>
                s.actions?.some(
                  (a) =>
                    a.type === "CREATE_ENTITY" &&
                    a.entityId === action.entityId,
                ),
              );

            if (!existsBeforeStep && !createdBefore) {
              errors.push(
                createError(
                  "UNKNOWN_ENTITY_REF",
                  `Action references unknown entity: ${action.entityId}`,
                  "warning",
                  2,
                  {
                    path: `steps[${stepIndex}].actions[${actionIndex}].entityId`,
                    stepIndex,
                    actionIndex,
                    entityId: action.entityId as string,
                  },
                  "Create the entity before referencing it",
                ),
              );
            }
          }
        });
      });

      return errors;
    },
  },

  // Domain-Specific: CS_DISCRETE
  {
    id: "cs-array-bounds",
    name: "Array Bounds",
    description: "Array indices should be within bounds",
    domains: ["CS_DISCRETE"],
    severity: 2,
    validate: (manifest) => {
      const errors: ValidationError[] = [];

      // Find array entities and check index references
      const arrayEntities = manifest.initialEntities?.filter(
        (e) => e.type === "array" || e.type === "array-element",
      );

      if (arrayEntities && arrayEntities.length > 0) {
        manifest.steps?.forEach((step, stepIndex) => {
          step.actions?.forEach((action, actionIndex) => {
            const index = (action.payload as Record<string, unknown>)?.index;
            if (typeof index === "number" && index < 0) {
              errors.push(
                createError(
                  "NEGATIVE_ARRAY_INDEX",
                  `Negative array index: ${index}`,
                  "warning",
                  2,
                  {
                    path: `steps[${stepIndex}].actions[${actionIndex}].payload.index`,
                    stepIndex,
                    actionIndex,
                  },
                  "Use non-negative array indices",
                ),
              );
            }
          });
        });
      }

      return errors;
    },
  },

  // Domain-Specific: PHYSICS
  {
    id: "physics-positive-mass",
    name: "Positive Mass",
    description: "Physical objects should have positive mass",
    domains: ["PHYSICS"],
    severity: 2,
    validate: (manifest) => {
      const errors: ValidationError[] = [];

      manifest.initialEntities?.forEach((entity, index) => {
        const mass = (entity.properties as Record<string, unknown>)?.mass;
        if (typeof mass === "number" && mass <= 0) {
          errors.push(
            createError(
              "NON_POSITIVE_MASS",
              `Entity has non-positive mass: ${mass}`,
              "warning",
              2,
              {
                path: `initialEntities[${index}].properties.mass`,
                entityId: entity.id as string,
              },
              "Use a positive mass value",
            ),
          );
        }
      });

      return errors;
    },
  },
];

// =============================================================================
// Hook Implementation
// =============================================================================

export function useSimulationValidation(
  options: UseSimulationValidationOptions = {},
): UseSimulationValidationReturn {
  const {
    apiBaseUrl = "/api",
    useBackendValidation = false,
    customRules = [],
  } = options;
  // Note: autoValidate and debounceMs reserved for future use
  void options.autoValidate;
  void options.debounceMs;

  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Note: debounceRef reserved for future auto-validation implementation
  void debounceRef;

  // State
  const [errors, setErrors] = useState<ValidationError[]>([]);
  const [summary, setSummary] = useState<ValidationSummary>({
    isValid: true,
    score: 100,
    errors: 0,
    warnings: 0,
    info: 0,
    lastValidated: null,
    duration: 0,
  });
  const [isValidating, setIsValidating] = useState(false);

  // Combined rules
  const allRules = useMemo(
    () => [...BUILTIN_RULES, ...customRules],
    [customRules],
  );

  // Local validation
  const validateLocally = useCallback(
    (manifest: SimulationManifest): ValidationError[] => {
      const allErrors: ValidationError[] = [];

      allRules.forEach((rule) => {
        // Check domain filter
        if (rule.domains && !rule.domains.includes(manifest.domain)) {
          return;
        }

        const ruleErrors = rule.validate(manifest);
        allErrors.push(...ruleErrors);
      });

      return allErrors;
    },
    [allRules],
  );

  // Backend validation mutation
  const backendValidation = useMutation({
    mutationFn: async (
      manifest: SimulationManifest,
    ): Promise<ManifestValidationResult> => {
      const response = await fetch(`${apiBaseUrl}/api/sim-author/validate`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ manifest }),
      });

      if (!response.ok) {
        throw new Error(`Validation failed: ${response.status}`);
      }

      return response.json();
    },
  });

  // Main validate function
  const validate = useCallback(
    async (manifest: SimulationManifest): Promise<ValidationError[]> => {
      const startTime = Date.now();
      setIsValidating(true);

      try {
        // Local validation first
        let allErrors = validateLocally(manifest);

        // Backend validation if enabled
        if (useBackendValidation) {
          try {
            const backendResult = await backendValidation.mutateAsync(manifest);

            // Convert backend errors to our format
            backendResult.errors?.forEach(
              (e: { code: string; message: string; path?: string }) => {
                allErrors.push(
                  createError(
                    e.code || "BACKEND_ERROR",
                    e.message,
                    "error",
                    1,
                    { path: e.path || "" },
                  ),
                );
              },
            );

            backendResult.warnings?.forEach(
              (w: { code: string; message: string; path?: string }) => {
                allErrors.push(
                  createError(
                    w.code || "BACKEND_WARNING",
                    w.message,
                    "warning",
                    2,
                    { path: w.path || "" },
                  ),
                );
              },
            );
          } catch (err) {
            console.warn("Backend validation failed:", err);
          }
        }

        // Calculate summary
        const errorCount = allErrors.filter((e) => e.type === "error").length;
        const warningCount = allErrors.filter(
          (e) => e.type === "warning",
        ).length;
        const infoCount = allErrors.filter((e) => e.type === "info").length;

        // Calculate score (deduct points for issues)
        const score = Math.max(
          0,
          100 - errorCount * 20 - warningCount * 5 - infoCount * 1,
        );

        setErrors(allErrors);
        setSummary({
          isValid: errorCount === 0,
          score,
          errors: errorCount,
          warnings: warningCount,
          info: infoCount,
          lastValidated: new Date(),
          duration: Date.now() - startTime,
        });

        return allErrors;
      } finally {
        setIsValidating(false);
      }
    },
    [validateLocally, useBackendValidation, backendValidation],
  );

  // Clear validation
  const clearValidation = useCallback(() => {
    setErrors([]);
    setSummary({
      isValid: true,
      score: 100,
      errors: 0,
      warnings: 0,
      info: 0,
      lastValidated: null,
      duration: 0,
    });
  }, []);

  // Get errors for specific locations
  const getErrorsForStep = useCallback(
    (stepIndex: number): ValidationError[] => {
      return errors.filter((e) => e.location?.stepIndex === stepIndex);
    },
    [errors],
  );

  const getErrorsForEntity = useCallback(
    (entityId: string): ValidationError[] => {
      return errors.filter((e) => e.location?.entityId === entityId);
    },
    [errors],
  );

  const getErrorsForPath = useCallback(
    (path: string): ValidationError[] => {
      return errors.filter((e) => e.location?.path?.startsWith(path));
    },
    [errors],
  );

  // Apply auto-fix
  const applyFix = useCallback(
    (errorId: string): SimulationManifest | null => {
      const error = errors.find((e) => e.id === errorId);
      if (error?.autoFix) {
        return error.autoFix();
      }
      return null;
    },
    [errors],
  );

  return {
    errors,
    summary,
    isValidating,
    validate,
    clearValidation,
    getErrorsForStep,
    getErrorsForEntity,
    getErrorsForPath,
    applyFix,
  };
}

export default useSimulationValidation;
