import {
  executeTelemetryFlowFixture,
  type ProductTelemetryFlowFixture,
  type TelemetryFacet,
} from '../telemetry/index.js';
import { validateRouteEntitlementPayload } from '../route-entitlements/index.js';
import {
  validateDataAccessContextSnapshot,
  type DataAccessValidationOptions,
} from '../data-access/index.js';
import {
  validateIdempotencyObservations,
  type IdempotencyValidationOptions,
} from '../idempotency/index.js';
import { validateObservabilityFlowManifest } from '../observability-flows/index.js';

export type ConformanceArea =
  | 'manifest'
  | 'route-entitlements'
  | 'data-access'
  | 'observability'
  | 'authorization'
  | 'idempotency'
  | 'scaffolding';

export interface ProductConformanceContext {
  readonly productId: string;
  readonly manifest?: unknown;
  readonly dataAccessContextSnapshots?: readonly unknown[];
  readonly dataAccessValidationOptions?: DataAccessValidationOptions;
  readonly idempotencyObservations?: readonly unknown[];
  readonly idempotencyValidationOptions?: IdempotencyValidationOptions;
  readonly routeEntitlementPayloads?: readonly unknown[];
  readonly observabilityFlowManifest?: unknown;
  readonly telemetryFixtures?: readonly ProductTelemetryFlowFixture[];
}

export interface ProductConformanceCheckResult {
  readonly id: string;
  readonly area: ConformanceArea;
  readonly valid: boolean;
  readonly errors: readonly string[];
  readonly warnings: readonly string[];
}

export interface ProductConformanceCheck {
  readonly id: string;
  readonly area: ConformanceArea;
  readonly description: string;
  run(context: ProductConformanceContext): ProductConformanceCheckResult | Promise<ProductConformanceCheckResult>;
}

export interface ProductConformanceSuite {
  readonly id: string;
  readonly checks: readonly ProductConformanceCheck[];
}

export interface ProductConformanceSuiteResult {
  readonly suiteId: string;
  readonly productId: string;
  readonly valid: boolean;
  readonly results: readonly ProductConformanceCheckResult[];
  readonly errors: readonly string[];
  readonly warnings: readonly string[];
}

export function defineProductConformanceSuite(suite: ProductConformanceSuite): ProductConformanceSuite {
  const checkIds = new Set<string>();
  const duplicateIds: string[] = [];

  for (const check of suite.checks) {
    if (checkIds.has(check.id)) {
      duplicateIds.push(check.id);
    }
    checkIds.add(check.id);
  }

  if (duplicateIds.length > 0) {
    throw new Error(`Product conformance suite ${suite.id} has duplicate check ids: ${duplicateIds.join(', ')}`);
  }

  return suite;
}

export async function runProductConformanceSuite(
  suite: ProductConformanceSuite,
  context: ProductConformanceContext,
): Promise<ProductConformanceSuiteResult> {
  const results: ProductConformanceCheckResult[] = [];

  for (const check of suite.checks) {
    try {
      results.push(await check.run(context));
    } catch (error) {
      results.push({
        id: check.id,
        area: check.area,
        valid: false,
        errors: [`${check.description} threw: ${formatError(error)}`],
        warnings: [],
      });
    }
  }

  const errors = results.flatMap((result) => result.errors);
  const warnings = results.flatMap((result) => result.warnings);

  return {
    suiteId: suite.id,
    productId: context.productId,
    valid: results.every((result) => result.valid),
    results,
    errors,
    warnings,
  };
}

export function createManifestEnvelopeCheck(): ProductConformanceCheck {
  return {
    id: 'manifest-envelope',
    area: 'manifest',
    description: 'Validate canonical product manifest envelope',
    run(context) {
      const manifest = toRecord(context.manifest);
      const errors: string[] = [];

      if (!manifest) {
        errors.push('manifest is required');
      } else {
        requireNonBlankString(manifest, 'schemaVersion', errors);
        requireExactString(manifest, 'product', context.productId, errors);
        requireNonBlankString(manifest, 'kind', errors);
        requirePresent(manifest, 'capabilities', errors);
        requirePresent(manifest, 'policies', errors);
        requirePresent(manifest, 'surfaces', errors);
        requirePresent(manifest, 'runtimeServices', errors);
      }

      return checkResult('manifest-envelope', 'manifest', errors);
    },
  };
}

export function createTelemetryFixtureCheck(requiredFacets: readonly TelemetryFacet[]): ProductConformanceCheck {
  return {
    id: 'telemetry-fixtures',
    area: 'observability',
    description: 'Execute product telemetry fixtures and validate required facets',
    async run(context) {
      const fixtures = (context.telemetryFixtures ?? []).filter((fixture) => fixture.product === context.productId);
      const errors: string[] = [];

      if (fixtures.length === 0) {
        errors.push(`no telemetry fixtures registered for product ${context.productId}`);
      }

      for (const fixture of fixtures) {
        const result = await executeTelemetryFlowFixture(fixture, requiredFacets);
        if (!result.valid) {
          errors.push(`${fixture.product}:${fixture.flow} missing telemetry facets ${result.missingFacets.join(', ')}`);
        }
      }

      return checkResult('telemetry-fixtures', 'observability', errors);
    },
  };
}

export function createObservabilityFlowManifestCheck(): ProductConformanceCheck {
  return {
    id: 'observability-flow-manifest',
    area: 'observability',
    description: 'Validate typed observability flow manifest',
    run(context) {
      const errors: string[] = [];
      if (context.observabilityFlowManifest === undefined) {
        errors.push('observability flow manifest is required');
      } else {
        const result = validateObservabilityFlowManifest(context.observabilityFlowManifest);
        errors.push(...result.errors.map((error) => `observability: ${error}`));
        if (result.manifest) {
          const productHasFlow = result.manifest.flows.some((flow) => flow.product === context.productId);
          if (!productHasFlow) {
            errors.push(`observability: no flow coverage registered for product ${context.productId}`);
          }
        }
      }
      return checkResult('observability-flow-manifest', 'observability', errors);
    },
  };
}

export function createRouteEntitlementPayloadCheck(): ProductConformanceCheck {
  return {
    id: 'route-entitlement-payloads',
    area: 'route-entitlements',
    description: 'Validate backend route entitlement payload shape',
    run(context) {
      const payloads = context.routeEntitlementPayloads ?? [];
      const errors: string[] = [];

      if (payloads.length === 0) {
        errors.push(`no route entitlement payload fixtures registered for product ${context.productId}`);
      }

      for (const [index, payload] of payloads.entries()) {
        const result = validateRouteEntitlementPayload(payload, { expectedProduct: context.productId });
        errors.push(...result.errors.map((error) => `payload[${index}]: ${error}`));
      }

      return checkResult('route-entitlement-payloads', 'route-entitlements', errors);
    },
  };
}

export function createDataAccessContextSnapshotCheck(
  defaultOptions: DataAccessValidationOptions = {},
): ProductConformanceCheck {
  return {
    id: 'data-access-context-snapshots',
    area: 'data-access',
    description: 'Validate product data-access context snapshots',
    run(context) {
      const snapshots = context.dataAccessContextSnapshots ?? [];
      const errors: string[] = [];
      const options = { ...defaultOptions, ...context.dataAccessValidationOptions };

      if (snapshots.length === 0) {
        errors.push(`no data-access context snapshots registered for product ${context.productId}`);
      }

      for (const [index, snapshot] of snapshots.entries()) {
        const result = validateDataAccessContextSnapshot(snapshot, options);
        errors.push(...result.errors.map((error) => `snapshot[${index}]: ${error}`));
      }

      return checkResult('data-access-context-snapshots', 'data-access', errors);
    },
  };
}

export function createIdempotencyObservationCheck(
  defaultOptions: IdempotencyValidationOptions = {},
): ProductConformanceCheck {
  return {
    id: 'idempotency-observations',
    area: 'idempotency',
    description: 'Validate idempotency behavior observations',
    run(context) {
      const observations = context.idempotencyObservations ?? [];
      const options = { ...defaultOptions, ...context.idempotencyValidationOptions };
      const result = validateIdempotencyObservations(observations, options);
      return checkResult(
        'idempotency-observations',
        'idempotency',
        result.errors.map((error) => `${context.productId}: ${error}`),
      );
    },
  };
}

export const kernelProductConformanceSuite: ProductConformanceSuite = defineProductConformanceSuite({
  id: 'kernel-product-conformance',
  checks: [
    createManifestEnvelopeCheck(),
    createDataAccessContextSnapshotCheck(),
    createIdempotencyObservationCheck(),
    createRouteEntitlementPayloadCheck(),
    createObservabilityFlowManifestCheck(),
    createTelemetryFixtureCheck(['trace', 'tenantContext', 'metrics', 'audit', 'safeLogging', 'redaction']),
  ],
});

function checkResult(
  id: string,
  area: ConformanceArea,
  errors: readonly string[],
  warnings: readonly string[] = [],
): ProductConformanceCheckResult {
  return {
    id,
    area,
    valid: errors.length === 0,
    errors,
    warnings,
  };
}

function requirePresent(record: Readonly<Record<string, unknown>>, key: string, errors: string[]): void {
  if (!Object.prototype.hasOwnProperty.call(record, key)) {
    errors.push(`manifest.${key} is required`);
  }
}

function requireExactString(
  record: Readonly<Record<string, unknown>>,
  key: string,
  expectedValue: string,
  errors: string[],
): void {
  const value = record[key];
  if (value !== expectedValue) {
    errors.push(`manifest.${key} must be ${expectedValue}`);
  }
}

function requireNonBlankString(
  record: Readonly<Record<string, unknown>>,
  key: string,
  errors: string[],
): void {
  const value = record[key];
  if (typeof value !== 'string' || value.trim().length === 0) {
    errors.push(`manifest.${key} must be a non-empty string`);
  }
}

function toRecord(value: unknown): Readonly<Record<string, unknown>> | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return null;
  }
  return value as Readonly<Record<string, unknown>>;
}

function formatError(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
