/**
 * Manifest Validation for Simulation Manifests
 * 
 * @doc.type module
 * @doc.purpose Validate simulation manifests against USP schema rules
 * @doc.layer product
 * @doc.pattern Validator
 */

import type {
  SimulationManifest,
  ManifestValidationResult,
  SimEntity,
  SimulationStep,
  SimEntityId
} from "@ghatana/tutorputor-contracts/v1/simulation";

/**
 * Validation error structure.
 */
interface ValidationIssue {
  path: string;
  message: string;
  severity: "error" | "warning";
}

/**
 * Validates a simulation manifest against USP rules.
 * 
 * @doc.type function
 * @doc.purpose Comprehensive manifest validation
 * @doc.layer product
 * @doc.pattern Validator
 */
export function validateManifest(manifest: SimulationManifest): ManifestValidationResult {
  const errors: ValidationIssue[] = [];
  const warnings: ValidationIssue[] = [];

  // Required field validation
  if (!manifest.id) {
    errors.push({ path: "id", message: "Simulation ID is required", severity: "error" });
  }
  if (!manifest.title) {
    errors.push({ path: "title", message: "Title is required", severity: "error" });
  }
  if (!manifest.domain) {
    errors.push({ path: "domain", message: "Domain is required", severity: "error" });
  }
  if (!manifest.schemaVersion) {
    errors.push({ path: "schemaVersion", message: "Schema version is required", severity: "error" });
  }

  // Canvas validation
  if (!manifest.canvas) {
    errors.push({ path: "canvas", message: "Canvas configuration is required", severity: "error" });
  } else {
    if (manifest.canvas.width <= 0) {
      errors.push({ path: "canvas.width", message: "Canvas width must be positive", severity: "error" });
    }
    if (manifest.canvas.height <= 0) {
      errors.push({ path: "canvas.height", message: "Canvas height must be positive", severity: "error" });
    }
    if (manifest.canvas.width > 4096 || manifest.canvas.height > 4096) {
      warnings.push({ path: "canvas", message: "Canvas dimensions exceed 4096px, may cause performance issues", severity: "warning" });
    }
  }

  // Playback validation
  if (!manifest.playback) {
    errors.push({ path: "playback", message: "Playback configuration is required", severity: "error" });
  } else {
    if (manifest.playback.defaultSpeed <= 0) {
      errors.push({ path: "playback.defaultSpeed", message: "Default speed must be positive", severity: "error" });
    }
    if (manifest.playback.defaultSpeed > 4) {
      warnings.push({ path: "playback.defaultSpeed", message: "Speed above 4x may make animations hard to follow", severity: "warning" });
    }
  }

  // Entity validation
  const entityIds = new Set<SimEntityId>();
  if (manifest.initialEntities) {
    manifest.initialEntities.forEach((entity, index) => {
      const entityErrors = validateEntity(entity, `initialEntities[${index}]`, entityIds);
      errors.push(...entityErrors.filter(e => e.severity === "error"));
      warnings.push(...entityErrors.filter(e => e.severity === "warning"));
      entityIds.add(entity.id);
    });
  }

  // Check for duplicate entity IDs
  const duplicateIds = findDuplicateIds(manifest.initialEntities || []);
  if (duplicateIds.length > 0) {
    errors.push({
      path: "initialEntities",
      message: `Duplicate entity IDs found: ${duplicateIds.join(", ")}`,
      severity: "error"
    });
  }

  // Steps validation
  if (!manifest.steps || manifest.steps.length === 0) {
    warnings.push({ path: "steps", message: "No steps defined, simulation will be static", severity: "warning" });
  } else {
    // Check step ordering
    const stepIndices = manifest.steps.map(s => s.orderIndex);
    if (new Set(stepIndices).size !== stepIndices.length) {
      errors.push({ path: "steps", message: "Duplicate step orderIndex values found", severity: "error" });
    }

    manifest.steps.forEach((step, index) => {
      const stepErrors = validateStep(step, `steps[${index}]`, entityIds);
      errors.push(...stepErrors.filter(e => e.severity === "error"));
      warnings.push(...stepErrors.filter(e => e.severity === "warning"));
    });
  }

  // Domain-specific validation
  const domainErrors = validateDomainSpecific(manifest);
  errors.push(...domainErrors.filter(e => e.severity === "error"));
  warnings.push(...domainErrors.filter(e => e.severity === "warning"));

  // Performance warnings
  if ((manifest.initialEntities?.length || 0) > 100) {
    warnings.push({
      path: "initialEntities",
      message: "More than 100 entities may cause performance issues",
      severity: "warning"
    });
  }
  if ((manifest.steps?.length || 0) > 200) {
    warnings.push({
      path: "steps",
      message: "More than 200 steps may cause performance issues",
      severity: "warning"
    });
  }

  // NEW: Lifecycle validation
  if (manifest.lifecycle) {
    const lifecycleErrors = validateLifecycle(manifest.lifecycle, "lifecycle");
    errors.push(...lifecycleErrors.filter(e => e.severity === "error"));
    warnings.push(...lifecycleErrors.filter(e => e.severity === "warning"));
  }

  // NEW: Safety validation
  if (manifest.safety) {
    const safetyErrors = validateSafety(manifest.safety, "safety");
    errors.push(...safetyErrors.filter(e => e.severity === "error"));
    warnings.push(...safetyErrors.filter(e => e.severity === "warning"));
  }

  // NEW: ECD metadata validation
  if (manifest.ecd) {
    const ecdErrors = validateECD(manifest.ecd, "ecd");
    errors.push(...ecdErrors.filter(e => e.severity === "error"));
    warnings.push(...ecdErrors.filter(e => e.severity === "warning"));
  }

  // NEW: Rendering capabilities validation
  if (manifest.rendering) {
    const renderingErrors = validateRendering(manifest.rendering, "rendering");
    errors.push(...renderingErrors.filter(e => e.severity === "error"));
    warnings.push(...renderingErrors.filter(e => e.severity === "warning"));
  }

  return {
    valid: errors.length === 0,
    errors: errors.map(e => ({ path: e.path, message: e.message, severity: e.severity })),
    warnings: warnings.map(w => ({ path: w.path, message: w.message }))
  };
}

/**
 * Validate an individual entity.
 */
function validateEntity(entity: SimEntity, path: string, existingIds: Set<SimEntityId>): ValidationIssue[] {
  const issues: ValidationIssue[] = [];

  if (!entity.id) {
    issues.push({ path: `${path}.id`, message: "Entity ID is required", severity: "error" });
  }
  if (!entity.type) {
    issues.push({ path: `${path}.type`, message: "Entity type is required", severity: "error" });
  }
  if (typeof entity.x !== "number" || typeof entity.y !== "number") {
    issues.push({ path: `${path}.x/y`, message: "Entity position (x, y) must be numbers", severity: "error" });
  }

  // Type-specific validation
  switch (entity.type) {
    case "node":
      if (!("value" in entity)) {
        issues.push({ path: `${path}.value`, message: "Node must have a value", severity: "error" });
      }
      break;
    case "edge":
      if (!("sourceId" in entity) || !("targetId" in entity)) {
        issues.push({ path: `${path}`, message: "Edge must have sourceId and targetId", severity: "error" });
      }
      break;
    case "rigidBody":
      if (!("mass" in entity) || (entity as { mass: number }).mass <= 0) {
        issues.push({ path: `${path}.mass`, message: "Rigid body must have positive mass", severity: "error" });
      }
      break;
    case "stock":
      if (!("value" in entity)) {
        issues.push({ path: `${path}.value`, message: "Stock must have a value", severity: "error" });
      }
      break;
    case "flow":
      if (!("sourceId" in entity) || !("targetId" in entity) || !("rate" in entity)) {
        issues.push({ path: `${path}`, message: "Flow must have sourceId, targetId, and rate", severity: "error" });
      }
      break;
    case "atom":
      if (!("element" in entity)) {
        issues.push({ path: `${path}.element`, message: "Atom must have an element symbol", severity: "error" });
      }
      break;
    case "bond":
      if (!("atom1Id" in entity) || !("atom2Id" in entity) || !("bondOrder" in entity)) {
        issues.push({ path: `${path}`, message: "Bond must have atom1Id, atom2Id, and bondOrder", severity: "error" });
      }
      break;
    case "pkCompartment":
      if (!("compartmentType" in entity) || !("volume" in entity)) {
        issues.push({ path: `${path}`, message: "PK compartment must have compartmentType and volume", severity: "error" });
      }
      break;
  }

  return issues;
}

/**
 * Validate an individual step.
 */
function validateStep(step: SimulationStep, path: string, entityIds: Set<SimEntityId>): ValidationIssue[] {
  const issues: ValidationIssue[] = [];

  if (!step.id) {
    issues.push({ path: `${path}.id`, message: "Step ID is required", severity: "error" });
  }
  if (typeof step.orderIndex !== "number") {
    issues.push({ path: `${path}.orderIndex`, message: "Step orderIndex must be a number", severity: "error" });
  }
  if (!step.actions || step.actions.length === 0) {
    issues.push({ path: `${path}.actions`, message: "Step must have at least one action", severity: "warning" });
  }

  // Validate actions
  step.actions?.forEach((action, actionIndex) => {
    if (!action.action) {
      issues.push({ path: `${path}.actions[${actionIndex}].action`, message: "Action type is required", severity: "error" });
    }

    // Check target entity references
    if ("targetId" in action && action.targetId && !entityIds.has(action.targetId as SimEntityId)) {
      // This might be a dynamically created entity, so just warn
      issues.push({
        path: `${path}.actions[${actionIndex}].targetId`,
        message: `Target entity '${action.targetId}' not found in initial entities (may be created dynamically)`,
        severity: "warning"
      });
    }
  });

  return issues;
}

/**
 * Domain-specific validation rules.
 */
function validateDomainSpecific(manifest: SimulationManifest): ValidationIssue[] {
  const issues: ValidationIssue[] = [];

  switch (manifest.domain) {
    case "PHYSICS":
      // Physics-specific validation
      if (manifest.domainMetadata && "physics" in manifest.domainMetadata) {
        const physics = manifest.domainMetadata.physics;
        if (physics.gravity) {
          if (Math.abs(physics.gravity.y) > 100) {
            issues.push({
              path: "domainMetadata.physics.gravity.y",
              message: "Gravity value seems unrealistic (expected around -9.81 m/s²)",
              severity: "warning"
            });
          }
        }
      }
      break;

    case "CHEMISTRY":
      // Chemistry-specific validation - could add mass/charge balance checks
      break;

    case "MEDICINE":
      // Medicine-specific validation
      if (manifest.domainMetadata && "medicine" in manifest.domainMetadata) {
        const medicine = manifest.domainMetadata.medicine;
        if (medicine.therapeuticRange) {
          if (medicine.therapeuticRange.min >= medicine.therapeuticRange.max) {
            issues.push({
              path: "domainMetadata.medicine.therapeuticRange",
              message: "Therapeutic range min must be less than max",
              severity: "error"
            });
          }
        }
      }
      break;
  }

  return issues;
}

/**
 * Find duplicate entity IDs.
 */
function findDuplicateIds(entities: SimEntity[]): string[] {
  const seen = new Set<string>();
  const duplicates = new Set<string>();

  for (const entity of entities) {
    if (seen.has(entity.id as string)) {
      duplicates.add(entity.id as string);
    }
    seen.add(entity.id as string);
  }

  return Array.from(duplicates);
}

/**
 * Validate lifecycle metadata.
 */
function validateLifecycle(lifecycle: any, path: string): ValidationIssue[] {
  const issues: ValidationIssue[] = [];

  const validStatuses = ['draft', 'validated', 'published', 'archived'];
  if (!validStatuses.includes(lifecycle.status)) {
    issues.push({
      path: `${path}.status`,
      message: `Invalid lifecycle status. Must be one of: ${validStatuses.join(', ')}`,
      severity: "error"
    });
  }

  const validCreatedBy = ['userId', 'ai', 'template'];
  if (!validCreatedBy.includes(lifecycle.createdBy)) {
    issues.push({
      path: `${path}.createdBy`,
      message: `Invalid createdBy value. Must be one of: ${validCreatedBy.join(', ')}`,
      severity: "error"
    });
  }

  if (lifecycle.status === 'published' && !lifecycle.publishedAt) {
    issues.push({
      path: `${path}.publishedAt`,
      message: "Published simulations must have publishedAt timestamp",
      severity: "error"
    });
  }

  return issues;
}

/**
 * Validate safety constraints.
 */
function validateSafety(safety: any, path: string): ValidationIssue[] {
  const issues: ValidationIssue[] = [];

  if (!safety.executionLimits) {
    issues.push({
      path: `${path}.executionLimits`,
      message: "Safety executionLimits are required",
      severity: "error"
    });
  } else {
    if (safety.executionLimits.maxSteps <= 0) {
      issues.push({
        path: `${path}.executionLimits.maxSteps`,
        message: "maxSteps must be positive",
        severity: "error"
      });
    }
    if (safety.executionLimits.maxSteps > 10000) {
      issues.push({
        path: `${path}.executionLimits.maxSteps`,
        message: "maxSteps exceeds 10000, may cause performance issues",
        severity: "warning"
      });
    }
    if (safety.executionLimits.maxRuntimeMs <= 0) {
      issues.push({
        path: `${path}.executionLimits.maxRuntimeMs`,
        message: "maxRuntimeMs must be positive",
        severity: "error"
      });
    }
    if (safety.executionLimits.maxRuntimeMs > 300000) {
      issues.push({
        path: `${path}.executionLimits.maxRuntimeMs`,
        message: "maxRuntimeMs exceeds 5 minutes, may cause timeout issues",
        severity: "warning"
      });
    }
  }

  return issues;
}

/**
 * Validate ECD metadata.
 */
function validateECD(ecd: any, path: string): ValidationIssue[] {
  const issues: ValidationIssue[] = [];

  if (!ecd.claims || ecd.claims.length === 0) {
    issues.push({
      path: `${path}.claims`,
      message: "ECD metadata should define at least one claim",
      severity: "warning"
    });
  } else {
    const claimIds = new Set<string>();
    ecd.claims.forEach((claim: any, index: number) => {
      if (!claim.id) {
        issues.push({
          path: `${path}.claims[${index}].id`,
          message: "Claim must have an id",
          severity: "error"
        });
      } else {
        if (claimIds.has(claim.id)) {
          issues.push({
            path: `${path}.claims[${index}].id`,
            message: `Duplicate claim id: ${claim.id}`,
            severity: "error"
          });
        }
        claimIds.add(claim.id);
      }
      if (!claim.description) {
        issues.push({
          path: `${path}.claims[${index}].description`,
          message: "Claim must have a description",
          severity: "error"
        });
      }
    });
  }

  if (!ecd.evidence || ecd.evidence.length === 0) {
    issues.push({
      path: `${path}.evidence`,
      message: "ECD metadata should define at least one evidence source",
      severity: "warning"
    });
  } else {
    const evidenceIds = new Set<string>();
    ecd.evidence.forEach((evidence: any, index: number) => {
      if (!evidence.id) {
        issues.push({
          path: `${path}.evidence[${index}].id`,
          message: "Evidence must have an id",
          severity: "error"
        });
      } else {
        if (evidenceIds.has(evidence.id)) {
          issues.push({
            path: `${path}.evidence[${index}].id`,
            message: `Duplicate evidence id: ${evidence.id}`,
            severity: "error"
          });
        }
        evidenceIds.add(evidence.id);
      }
      if (!evidence.source) {
        issues.push({
          path: `${path}.evidence[${index}].source`,
          message: "Evidence must have a source",
          severity: "error"
        });
      }
    });
  }

  if (!ecd.tasks || ecd.tasks.length === 0) {
    issues.push({
      path: `${path}.tasks`,
      message: "ECD metadata should define at least one task",
      severity: "warning"
    });
  }

  return issues;
}

/**
 * Validate rendering capabilities.
 */
function validateRendering(rendering: any, path: string): ValidationIssue[] {
  const issues: ValidationIssue[] = [];

  const validCapabilities = ['2d', '3d', 'vr', 'ar'];

  if (!rendering.requiredCapabilities || rendering.requiredCapabilities.length === 0) {
    issues.push({
      path: `${path}.requiredCapabilities`,
      message: "At least one required capability must be specified",
      severity: "error"
    });
  } else {
    rendering.requiredCapabilities.forEach((cap: string, index: number) => {
      if (!validCapabilities.includes(cap)) {
        issues.push({
          path: `${path}.requiredCapabilities[${index}]`,
          message: `Invalid capability: ${cap}. Must be one of: ${validCapabilities.join(', ')}`,
          severity: "error"
        });
      }
    });
  }

  if (rendering.optionalCapabilities) {
    rendering.optionalCapabilities.forEach((cap: string, index: number) => {
      if (!validCapabilities.includes(cap)) {
        issues.push({
          path: `${path}.optionalCapabilities[${index}]`,
          message: `Invalid capability: ${cap}. Must be one of: ${validCapabilities.join(', ')}`,
          severity: "error"
        });
      }
    });
  }

  return issues;
}

/**
 * Quick validation check (valid/invalid only).
 */
export function isValidManifest(manifest: SimulationManifest): boolean {
  return validateManifest(manifest).valid;
}
