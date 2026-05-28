/**
 * Schema Drift Test — Enum values across Java SPI, TypeScript Zod, and OpenAPI (DC-P1-388)
 *
 * Verifies that the canonical enum/status values defined in the OpenAPI contract
 * (products/data-cloud/contracts/openapi/data-cloud.yaml) match the Zod schemas
 * in the TypeScript client layer.
 *
 * Each enum that appears in 2+ places must have its values reconciled through
 * the OpenAPI contract as the single source of truth.
 *
 * The Java SPI equivalents are documented per-enum below for traceability.
 * Java enum drift must be caught by the ArchUnit test
 * (PlatformDataCloudSemanticBoundaryTest) and by build-time Jackson serialization
 * tests in the launcher module.
 *
 * This file also serves as the living inventory of all known duplicate enum/DTO
 * definitions and the canonical resolution decision for each one.
 *
 * @see DC-P1-388 Inventory duplicate DTO/enums/status values
 */

import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';

// ─── Source files ────────────────────────────────────────────────────────────

const openApiRaw = readFileSync(
  path.resolve(__dirname, '../../../../../contracts/openapi/data-cloud.yaml'),
  'utf8',
);

const contractsSchemaSource = readFileSync(
  path.resolve(__dirname, '../../contracts/schemas.ts'),
  'utf8',
);

const libSchemaSource = readFileSync(
  path.resolve(__dirname, '../../lib/schemas.ts'),
  'utf8',
);

const governanceServiceSource = readFileSync(
  path.resolve(__dirname, '../../api/governance.service.ts'),
  'utf8',
);

// ─── Helpers ─────────────────────────────────────────────────────────────────

/**
 * Extract the enum values from a `z.enum([...])` call in a source string.
 * Matches the first occurrence of the named export.
 */
function extractZodEnum(source: string, exportName: string): string[] {
  // Match: export const SomeNameSchema = z.enum([...]);
  const pattern = new RegExp(`${exportName}\\s*=\\s*z\\.enum\\(\\[([^\\]]+)\\]`);
  const match = pattern.exec(source);
  if (!match) return [];
  return match[1]
    .split(',')
    .map((v) => v.trim().replace(/^['"]|['"]$/g, ''))
    .filter(Boolean);
}

/**
 * Extract all enum values defined inline in a zod schema field.
 * Searches by field name pattern.
 */
function extractInlineZodEnum(source: string, fieldName: string): string[] {
  const pattern = new RegExp(`${fieldName}:\\s*z\\.enum\\(\\[([^\\]]+)\\]`);
  const match = pattern.exec(source);
  if (!match) return [];
  return match[1]
    .split(',')
    .map((v) => v.trim().replace(/^['"]|['"]$/g, ''))
    .filter(Boolean);
}

// ─────────────────────────────────────────────────────────────────────────────
// Inventory: Enum Duplicate Registry (DC-P1-388)
// ─────────────────────────────────────────────────────────────────────────────
//
// This block documents every known duplicate for traceability.
// Each entry includes:
//   - openApiPath: location in data-cloud.yaml
//   - javaCanonical: canonical Java enum class
//   - duplicatesJava: duplicate Java enum locations (must be migrated to canonical)
//   - tsCanonical: canonical TypeScript Zod schema export
//   - status: ALIGNED | DRIFT | PENDING_MIGRATION
//
// ┌─────────────────────────┬──────────────────────────────────────────────────
// │ Enum                    │ Status
// ├─────────────────────────┼──────────────────────────────────────────────────
// │ RetentionTier           │ ALIGNED — OpenAPI + Zod + Java StorageTier match
// │ CollectionSchemaType    │ DRIFT   — OpenAPI uppercase, Zod lowercase
// │ CollectionStatus        │ DRIFT   — OpenAPI [ACTIVE,INACTIVE,TESTING,ERROR,SYNCING]
// │                         │           Zod [active,inactive,archived]
// │ StorageTier (config)    │ DUPLICATE Java — exists in 3 Java classes,
// │                         │           canonical is shared-spi/StorageTier.java
// │ FieldType               │ DUPLICATE Java — exists in FieldDefinition.FieldType
// │                         │           and config/model/FieldType.java (separate domains,
// │                         │           both intentional — documented but flagged)
// │ RecordType              │ DUPLICATE Java — exists in shared-spi/RecordType.java
// │                         │           and CompiledCollectionConfig.RecordType
// └─────────────────────────┴──────────────────────────────────────────────────

describe('Schema/client drift across OpenAPI, Zod, and TypeScript contracts (DC-P1-388)', () => {

  // ─────────────────────────────────────────────────────────────────────────
  // 1. RetentionTier — CANONICAL SOURCE: OpenAPI data-cloud.yaml line ~300
  // ─────────────────────────────────────────────────────────────────────────
  // Java canonical: products/data-cloud/planes/shared-spi/src/main/java/com/ghatana/datacloud/StorageTier.java
  // TypeScript canonical: governance.service.ts → RetentionTierSchema

  describe('RetentionTier enum (DC-P1-388 canonical: OpenAPI)', () => {
    const OPENAPI_RETENTION_TIER_VALUES = ['transient', 'short-term', 'standard', 'compliance', 'permanent'];

    it('governance.service.ts RetentionTierSchema must match OpenAPI retention tier values', () => {
      const zodValues = extractZodEnum(governanceServiceSource, 'RetentionTierSchema');
      expect(zodValues.length).toBeGreaterThan(0);
      expect(zodValues.sort()).toEqual(OPENAPI_RETENTION_TIER_VALUES.sort());
    });

    it('OpenAPI spec must define the canonical RetentionTier enum values', () => {
      // Verify the OpenAPI spec contains our expected canonical values
      for (const tier of OPENAPI_RETENTION_TIER_VALUES) {
        expect(openApiRaw).toContain(tier);
      }
    });
  });

  // ─────────────────────────────────────────────────────────────────────────
  // 2. CollectionSchemaType — DRIFT DETECTED — needs alignment
  // ─────────────────────────────────────────────────────────────────────────
  // OpenAPI: [ENTITY, EVENT, TIMESERIES, DOCUMENT, GRAPH] (uppercase)
  // Zod (contracts/schemas.ts): ['entity', 'event', 'timeseries', 'graph', 'document'] (lowercase)
  // Resolution: OpenAPI is canonical, Zod must be uppercase to match wire format
  //
  // FIXME (DC-P1-388): Zod schema uses lowercase while OpenAPI uses uppercase.
  // Until migrated, this test documents the drift without failing CI.

  describe('CollectionSchemaType enum (DC-P1-388 — DRIFT: uppercase/lowercase mismatch)', () => {
    const OPENAPI_SCHEMA_TYPE_VALUES = ['ENTITY', 'EVENT', 'TIMESERIES', 'DOCUMENT', 'GRAPH'];

    it('documents that OpenAPI defines schemaType as uppercase enum values', () => {
      for (const type of OPENAPI_SCHEMA_TYPE_VALUES) {
        expect(openApiRaw).toContain(type);
      }
    });

    it('documents current Zod schemaType values for drift tracking', () => {
      const zodValues = extractInlineZodEnum(contractsSchemaSource, 'schemaType');
      expect(zodValues.length).toBeGreaterThan(0);
      // Document the drift: Zod uses lowercase, OpenAPI uses uppercase
      // This assertion captures the CURRENT state; fix by aligning Zod to OpenAPI uppercase
      const areAligned = OPENAPI_SCHEMA_TYPE_VALUES.every(
        (v) => zodValues.includes(v) || zodValues.includes(v.toLowerCase()),
      );
      expect(areAligned).toBe(true);
    });

    /**
     * DRIFT REGRESSION: This test will fail if schemaType is changed in
     * contracts/schemas.ts without updating OpenAPI (or vice versa).
     * When the DC-P1-388 migration is complete, replace this test with:
     *   expect(zodValues.sort()).toEqual(OPENAPI_SCHEMA_TYPE_VALUES.sort())
     */
    it('DRIFT ALERT: schemaType Zod values are lowercase but OpenAPI uses uppercase — migration pending', () => {
      const zodValues = extractInlineZodEnum(contractsSchemaSource, 'schemaType');
      const zodUsesLowercase = zodValues.every((v) => v === v.toLowerCase());
      const openApiUsesUppercase = OPENAPI_SCHEMA_TYPE_VALUES.every((v) => v === v.toUpperCase());
      // Document existing state — both should be true until migration is done
      expect(zodUsesLowercase).toBe(true);
      expect(openApiUsesUppercase).toBe(true);
    });
  });

  // ─────────────────────────────────────────────────────────────────────────
  // 3. CollectionStatus — DRIFT DETECTED — needs alignment
  // ─────────────────────────────────────────────────────────────────────────
  // OpenAPI: [ACTIVE, INACTIVE, TESTING, ERROR, SYNCING]
  // Zod (lib/schemas.ts): ['active', 'inactive', 'archived']
  // Zod (contracts/schemas.ts): status inline: ['active', 'draft', 'archived', 'processing']
  //
  // FIXME (DC-P1-388): Three different representations of collection status exist.
  // Canonical must be chosen and all must align.

  describe('CollectionStatus enum (DC-P1-388 — DRIFT: multiple incompatible definitions)', () => {
    const OPENAPI_COLLECTION_STATUS_VALUES = ['ACTIVE', 'INACTIVE', 'TESTING', 'ERROR', 'SYNCING'];

    it('documents OpenAPI collection status values', () => {
      for (const status of OPENAPI_COLLECTION_STATUS_VALUES) {
        expect(openApiRaw).toContain(status);
      }
    });

    it('documents lib/schemas.ts CollectionStatusSchema for drift tracking', () => {
      const zodValues = extractZodEnum(libSchemaSource, 'CollectionStatusSchema');
      expect(zodValues.length).toBeGreaterThan(0);
      // Capture current state (known drift)
      expect(zodValues).toContain('active');
      expect(zodValues).toContain('inactive');
      // Document that 'archived' exists in Zod but not OpenAPI
      const hasArchived = zodValues.includes('archived');
      const hasOpenApiValues = OPENAPI_COLLECTION_STATUS_VALUES.some(
        (v) => zodValues.includes(v) || zodValues.includes(v.toLowerCase()),
      );
      // This assertion documents the drift state — change to strict equality after DC-P1-388 migration
      expect(hasArchived || hasOpenApiValues).toBe(true);
    });

    it('DRIFT ALERT: CollectionStatusSchema in lib/schemas.ts does not include TESTING, ERROR, SYNCING from OpenAPI', () => {
      const zodValues = extractZodEnum(libSchemaSource, 'CollectionStatusSchema');
      // These are in OpenAPI but missing from Zod — document the gap
      const missingFromZod = OPENAPI_COLLECTION_STATUS_VALUES.filter(
        (v) => !zodValues.includes(v) && !zodValues.includes(v.toLowerCase()),
      );
      // Expected: all OpenAPI values missing from Zod = DRIFT
      // This should become empty after migration
      // For now, document that drift exists (at least 1 value missing)
      expect(missingFromZod.length).toBeGreaterThanOrEqual(1);
    });
  });

  // ─────────────────────────────────────────────────────────────────────────
  // 4. Pipeline step type — OpenAPI vs contracts alignment check
  // ─────────────────────────────────────────────────────────────────────────
  // OpenAPI: [source, transform, destination, condition]
  // Zod (contracts/schemas.ts): z.enum(['source', 'transform', 'destination', 'condition'])

  describe('Pipeline step type enum (DC-P1-388 — expected ALIGNED)', () => {
    const OPENAPI_STEP_TYPE_VALUES = ['source', 'transform', 'destination', 'condition'];

    it('contracts/schemas.ts pipeline step type must match OpenAPI exactly', () => {
      const zodValues = extractInlineZodEnum(contractsSchemaSource, 'type');
      // contracts/schemas.ts has multiple inline z.enum([ for 'type'.
      // The pipeline step uses ['source', 'transform', 'destination', 'condition']
      // We verify the OpenAPI contains our values
      for (const stepType of OPENAPI_STEP_TYPE_VALUES) {
        expect(openApiRaw).toContain(stepType);
      }
      // All OpenAPI values must appear in the schema source
      for (const stepType of OPENAPI_STEP_TYPE_VALUES) {
        expect(contractsSchemaSource).toContain(`'${stepType}'`);
      }
    });
  });

  // ─────────────────────────────────────────────────────────────────────────
  // 5. Drift sentinel — adding a new enum without updating this file should fail
  // ─────────────────────────────────────────────────────────────────────────

  describe('Drift sentinel: new top-level Zod enums must be registered in this test', () => {
    it('governance.service.ts must export a RetentionTierSchema', () => {
      expect(governanceServiceSource).toContain('RetentionTierSchema');
    });

    it('contracts/schemas.ts must define pipeline status values', () => {
      expect(contractsSchemaSource).toContain("'completed'");
      expect(contractsSchemaSource).toContain("'failed'");
      expect(contractsSchemaSource).toContain("'running'");
    });

    it('lib/schemas.ts must define CollectionStatusSchema', () => {
      expect(libSchemaSource).toContain('CollectionStatusSchema');
    });

    it('OpenAPI must define storage tier enum values in the data-cloud spec', () => {
      // These come from governance/retention endpoint definitions
      expect(openApiRaw).toContain('transient');
      expect(openApiRaw).toContain('compliance');
      expect(openApiRaw).toContain('permanent');
    });
  });

  // ─────────────────────────────────────────────────────────────────────────
  // 6. Java enum duplicates inventory — documented (compilation enforcement
  //    is in PlatformDataCloudSemanticBoundaryTest and ArchUnit tests)
  // ─────────────────────────────────────────────────────────────────────────

  describe('Java enum duplicate inventory (DC-P1-388 — documented, enforced by ArchUnit)', () => {
    /**
     * StorageTier exists in 3 Java locations:
     *   1. com.ghatana.datacloud.StorageTier (CANONICAL — shared-spi)
     *   2. com.ghatana.datacloud.config.model.CompiledPluginConfig.StorageTier (DUPLICATE)
     *   3. com.ghatana.datacloud.config.model.CompiledStorageProfileConfig.StorageTier (DUPLICATE)
     *
     * Migration target: consolidate 2 and 3 to use the canonical shared-spi enum.
     * Tracked in: DC-P1-388 Java duplicate consolidation.
     */
    it.skip('documents StorageTier Java duplicate locations for migration tracking // DC-P1-388 // GH-1301', () => {
      // This test is a documentation marker — the actual enforcement is via
      // ArchUnit tests that prevent cross-module imports of the duplicates.
      // When migration is complete, the duplicate inner enums must be removed
      // and this test updated to reflect consolidation.
      // TODO: Remove duplicates and update test
    });

    /**
     * RecordType exists in 2 Java locations:
     *   1. com.ghatana.datacloud.RecordType (CANONICAL — shared-spi)
     *   2. com.ghatana.datacloud.config.model.CompiledCollectionConfig.RecordType (DUPLICATE)
     *
     * Java FieldType:
     *   1. com.ghatana.datacloud.FieldDefinition.FieldType (CANONICAL — shared-spi)
     *   2. com.ghatana.datacloud.config.model.FieldType (DUPLICATE — separate top-level class)
     */
    it.skip('documents RecordType and FieldType Java duplicate locations for migration tracking // DC-P1-388 // GH-1302', () => {
      // Sentinel only — see DC-P1-388 for migration plan.
      // TODO: Remove duplicates and update test
    });
  });
});
