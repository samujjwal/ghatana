/**
 * @fileoverview Tests for extracted source structure contracts.
 *
 * Verifies the public versioned envelope and migration helper through real Zod
 * schema validation.
 */

import { describe, expect, it } from 'vitest';
import {
  EXTRACTED_STRUCTURE_SCHEMA_VERSION,
  ExtractedStructureEnvelopeSchema,
  JsxTreeNodeSchema,
  migrateExtractedStructureEnvelope,
} from '../structure.js';

describe('JsxTreeNodeSchema', () => {
  it('parses nested JSX trees', () => {
    const tree = JsxTreeNodeSchema.parse({
      tagName: 'Card',
      isIntrinsic: false,
      startLine: 1,
      endLine: 5,
      children: [
        {
          tagName: 'button',
          isIntrinsic: true,
          startLine: 2,
          endLine: 4,
          children: [],
        },
      ],
    });

    expect(tree.children[0]?.tagName).toBe('button');
  });

  it('rejects invalid line ranges at the boundary', () => {
    expect(() =>
      JsxTreeNodeSchema.parse({
        tagName: 'Card',
        isIntrinsic: false,
        startLine: 0,
        endLine: 5,
        children: [],
      }),
    ).toThrow();
  });
});

describe('ExtractedStructureEnvelopeSchema', () => {
  it('parses the current versioned extracted structure envelope', () => {
    const envelope = ExtractedStructureEnvelopeSchema.parse({
      schemaVersion: EXTRACTED_STRUCTURE_SCHEMA_VERSION,
      jsxTree: [
        {
          tagName: 'App',
          isIntrinsic: false,
          startLine: 1,
          endLine: 3,
          children: [],
        },
      ],
      detectedRoutes: [
        {
          path: '/settings',
          componentName: 'SettingsPage',
          isIndex: false,
          sourceLine: 12,
        },
      ],
      componentUsages: [
        {
          tagName: 'Button',
          isDesignSystem: true,
          sourceLine: 14,
          importedFrom: '@ghatana/design-system',
        },
      ],
      protectedRegions: [
        {
          regionId: 'custom-handler',
          ownerKind: 'component',
          startLine: 20,
          endLine: 25,
          contentLines: ['function handleClick() {}'],
        },
      ],
      sourceImports: [
        {
          moduleSpecifier: '@ghatana/design-system',
          importClauseText: '{ Button }',
          isTypeOnly: false,
          sourceLine: 1,
          text: 'import { Button } from "@ghatana/design-system";',
        },
      ],
    });

    expect(envelope.schemaVersion).toBe(EXTRACTED_STRUCTURE_SCHEMA_VERSION);
    expect(envelope.detectedRoutes[0]?.componentName).toBe('SettingsPage');
    expect(envelope.sourceImports[0]?.moduleSpecifier).toBe('@ghatana/design-system');
  });

  it('defaults absent structure arrays in the current envelope', () => {
    const envelope = ExtractedStructureEnvelopeSchema.parse({
      schemaVersion: EXTRACTED_STRUCTURE_SCHEMA_VERSION,
    });

    expect(envelope.jsxTree).toEqual([]);
    expect(envelope.detectedRoutes).toEqual([]);
    expect(envelope.componentUsages).toEqual([]);
    expect(envelope.protectedRegions).toEqual([]);
    expect(envelope.sourceImports).toEqual([]);
  });

  it('rejects unknown envelope versions', () => {
    expect(() =>
      ExtractedStructureEnvelopeSchema.parse({
        schemaVersion: '2.0.0',
      }),
    ).toThrow();
  });
});

describe('migrateExtractedStructureEnvelope', () => {
  it('migrates legacy unversioned extracted structure payloads', () => {
    const envelope = migrateExtractedStructureEnvelope({
      sourceImports: [
        {
          moduleSpecifier: 'react',
          importClauseText: '{ useMemo }',
          isTypeOnly: false,
          sourceLine: 1,
          text: 'import { useMemo } from "react";',
        },
      ],
    });

    expect(envelope.schemaVersion).toBe(EXTRACTED_STRUCTURE_SCHEMA_VERSION);
    expect(envelope.sourceImports[0]?.moduleSpecifier).toBe('react');
    expect(envelope.jsxTree).toEqual([]);
  });
});
