/**
 * @ghatana/ds-governance test suite
 * Tests for naming, duplication, compatibility, and contribution gates
 */

import { describe, it, expect } from 'vitest';
import {
  checkTokenName,
  checkComponentName,
  checkCssVarName,
  validateTokenNames,
} from '../index';

describe('@ghatana/ds-governance', () => {
  describe('Naming Validation', () => {
    it('should validate valid token names (kebab-case)', () => {
      const result = checkTokenName('primary-blue', 'color.primary');
      expect(result).toBeNull();
    });

    it('should reject invalid token names', () => {
      const result = checkTokenName('PrimaryBlue', 'color.primary');
      expect(result).not.toBeNull();
      expect(result?.rule).toBe('token-kebab-case');
    });

    it('should validate valid component names (PascalCase)', () => {
      const result = checkComponentName('Button', 'components.Button');
      expect(result).toBeNull();
    });

    it('should reject invalid component names', () => {
      const result = checkComponentName('button', 'components.button');
      expect(result).not.toBeNull();
      expect(result?.rule).toBe('component-pascal-case');
    });

    it('should validate valid CSS variable names', () => {
      const result = checkCssVarName('--primary-blue', 'css.variables');
      expect(result).toBeNull();
    });

    it('should reject invalid CSS variable names', () => {
      const result = checkCssVarName('--PrimaryBlue', 'css.variables');
      expect(result).not.toBeNull();
      expect(result?.rule).toBe('css-var-kebab-case');
    });

    it('should validate token name maps', () => {
      const tokens = new Map([
        ['primary-blue', '#0000FF'],
        ['spacing-md', '16px'],
        ['$meta', 'ignored'],
      ]);

      const result = validateTokenNames(tokens);
      expect(result.valid).toBe(true);
      expect(result.violations).toHaveLength(0);
    });

    it('should detect invalid token names in maps', () => {
      const tokens = new Map([
        ['PrimaryBlue', '#0000FF'],
        ['spacing-md', '16px'],
      ]);

      const result = validateTokenNames(tokens);
      expect(result.valid).toBe(false);
      expect(result.violations).toHaveLength(1);
      expect(result.violations[0].rule).toBe('token-kebab-case');
    });
  });
});
