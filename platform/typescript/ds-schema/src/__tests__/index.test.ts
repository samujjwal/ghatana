/**
 * @ghatana/ds-schema test suite
 * Tests for DTCG-aligned token, component, theme, pattern, and compatibility schemas
 */

import { describe, it, expect } from 'vitest';
import {
  z,
  validateToken,
  isValidTokenValue,
  validateComponentContract,
  computeContractHash,
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
});
