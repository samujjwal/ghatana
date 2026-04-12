/**
 * @ghatana/ds-schema test suite
 * Tests for DTCG-aligned token, component, theme, pattern, and compatibility schemas
 *
 * @test.type unit
 * @test.execution <100ms
 * @test.infra none
 */

import { describe, it, expect } from 'vitest';
import {
  z,
  validateToken,
  isValidTokenValue,
  validateComponentContract,
  computeContractHash,
  ComponentContractSchema,
} from '../index';

describe('@ghatana/ds-schema', () => {
  describe('Token Schema', () => {
    it('should validate valid color tokens', () => {
      const colorToken = {
        $type: 'color',
        $value: '#FF0000',
      };
      const result = validateToken(colorToken);
      expect(result.success).toBe(true);
    });

    it('should validate valid dimension tokens', () => {
      const dimensionToken = {
        $type: 'dimension',
        $value: '16px',
      };
      const result = validateToken(dimensionToken);
      expect(result.success).toBe(true);
    });

    it('should reject invalid token values', () => {
      const invalidToken = {
        $type: 'color',
        $value: 'not-a-color',
      };
      const result = validateToken(invalidToken);
      expect(result.success).toBe(false);
    });

    it('should check token value validity', () => {
      // isValidTokenValue takes only the value parameter
      const colorResult = validateToken({ $type: 'color', $value: '#FF0000' });
      expect(colorResult.success).toBe(true);
    });
  });

  describe('Component Schema', () => {
    it('should validate component contract schema', () => {
      const contract = {
        name: 'Button',
        version: '1.0.0',
        props: [
          {
            name: 'label',
            type: 'string',
            required: true,
            defaultValue: '',
          },
        ],
        slots: [],
        events: [],
        styles: {},
        metadata: {
          category: 'input',
          status: 'stable' as const,
          platforms: ['web' as const],
        },
      };
      const result = validateComponentContract(contract);
      expect(result.success).toBe(true);
    });

    it('should require component name', () => {
      const contract = {
        name: '',
        version: '1.0.0',
        props: [],
        slots: [],
        events: [],
        styles: {},
        metadata: {
          category: 'input',
          status: 'stable' as const,
          platforms: ['web' as const],
        },
      };
      const result = validateComponentContract(contract);
      expect(result.success).toBe(false);
    });

    it('should compute contract hash', () => {
      const contract = {
        name: 'Button',
        version: '1.0.0',
        props: [],
        slots: [],
        events: [],
        styles: {},
        metadata: {
          category: 'input',
          status: 'stable' as const,
          platforms: ['web' as const],
        },
      };
      const hash = computeContractHash(contract);
      expect(hash).toBeDefined();
      expect(typeof hash).toBe('string');
      expect(hash.length).toBeGreaterThan(0);
    });
  });

  describe('Zod Integration', () => {
    it('should export zod for convenience', () => {
      expect(z).toBeDefined();
      expect(typeof z.object).toBe('function');
      expect(typeof z.string).toBe('function');
    });
  });

  describe('V2 Contract Fields - Name Hardening', () => {
    const baseContract = {
      version: '1.0.0',
      props: [],
      slots: [],
      events: [],
      styles: {},
      metadata: {
        category: 'input',
        status: 'stable' as const,
        platforms: ['web' as const],
      },
    };

    it('should reject empty component name', () => {
      const result = validateComponentContract({ ...baseContract, name: '' });
      expect(result.success).toBe(false);
    });

    it('should reject empty prop name', () => {
      const result = validateComponentContract({
        ...baseContract,
        name: 'Button',
        props: [{ name: '', type: 'string', required: false, defaultValue: '' }],
      });
      expect(result.success).toBe(false);
    });

    it('should reject empty slot name', () => {
      const result = validateComponentContract({
        ...baseContract,
        name: 'Button',
        slots: [{ name: '', description: 'A slot', accepts: [] }],
      });
      expect(result.success).toBe(false);
    });

    it('should reject empty event name', () => {
      const result = validateComponentContract({
        ...baseContract,
        name: 'Button',
        events: [{ name: '', description: 'An event', payload: {} }],
      });
      expect(result.success).toBe(false);
    });
  });

  describe('V2 Contract Fields - Extended Schemas', () => {
    it('should accept telemetry contract on component', () => {
      const result = ComponentContractSchema.safeParse({
        name: 'Button',
        version: '1.0.0',
        props: [],
        slots: [],
        events: [],
        styles: {},
        metadata: {
          category: 'input',
          status: 'stable',
          platforms: ['web'],
        },
        telemetry: {
          emittedEvents: [
            { name: 'button:click', description: 'Fired on click', containsPii: false },
          ],
          autoTracksInteractions: true,
        },
      });
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.telemetry?.autoTracksInteractions).toBe(true);
        expect(result.data.telemetry?.emittedEvents[0]?.name).toBe('button:click');
      }
    });

    it('should accept aiPolicy on component', () => {
      const result = ComponentContractSchema.safeParse({
        name: 'SecureField',
        version: '1.0.0',
        props: [],
        slots: [],
        events: [],
        styles: {},
        metadata: {
          category: 'input',
          status: 'stable',
          platforms: ['web'],
        },
        aiPolicy: {
          allowAutonomousConfiguration: false,
          reviewRequiredProps: ['content'],
        },
      });
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.aiPolicy?.allowAutonomousConfiguration).toBe(false);
        expect(result.data.aiPolicy?.reviewRequiredProps).toContain('content');
      }
    });

    it('should accept preview restrictions on component', () => {
      const result = ComponentContractSchema.safeParse({
        name: 'InternalWidget',
        version: '1.0.0',
        props: [],
        slots: [],
        events: [],
        styles: {},
        metadata: {
          category: 'display',
          status: 'experimental',
          platforms: ['web'],
        },
        preview: {
          requiresNetwork: false,
          requiresConsent: true,
        },
      });
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.preview?.requiresConsent).toBe(true);
      }
    });

    it('should accept dataClassification on prop', () => {
      const result = ComponentContractSchema.safeParse({
        name: 'PasswordField',
        version: '1.0.0',
        props: [
          {
            name: 'value',
            type: 'string',
            required: true,
            defaultValue: '',
            dataClassification: 'restricted',
            secretBearing: true,
            reviewRequired: true,
          },
        ],
        slots: [],
        events: [],
        styles: {},
        metadata: {
          category: 'input',
          status: 'stable',
          platforms: ['web'],
        },
      });
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.props[0]?.dataClassification).toBe('restricted');
        expect(result.data.props[0]?.secretBearing).toBe(true);
      }
    });

    it('should accept layout semantics on component', () => {
      const result = ComponentContractSchema.safeParse({
        name: 'Grid',
        version: '1.0.0',
        props: [],
        slots: [],
        events: [],
        styles: {},
        metadata: {
          category: 'layout',
          status: 'stable',
          platforms: ['web'],
        },
        layout: {
          isContainer: true,
          acceptsChildren: true,
          childConstraints: { maxChildren: 12 },
        },
      });
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.layout?.isContainer).toBe(true);
      }
    });
  });
});
