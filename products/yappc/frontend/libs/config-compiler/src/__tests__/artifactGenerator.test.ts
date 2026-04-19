/**
 * Artifact Generator Utilities Tests
 *
 * Tests for artifact generator utilities.
 *
 * @packageDocumentation
 */

import { describe, it, expect } from 'vitest';

import {
  generateComponentArtifact,
  generatePageArtifact,
  generateSceneArtifact,
  generateConfigArtifact,
  generateStyleArtifact,
  formatArtifactContent,
  validateArtifact,
} from '../utils/artifactGenerator';

describe('Artifact Generator Utilities', () => {
  describe('generateComponentArtifact', () => {
    it('should generate a component artifact', () => {
      const artifact = generateComponentArtifact('Button', 'export const Button = () => null;');

      expect(artifact.type).toBe('component');
      expect(artifact.name).toBe('Button');
      expect(artifact.language).toBe('typescript');
      expect(artifact.path).toBe('components/Button.tsx');
    });
  });

  describe('generatePageArtifact', () => {
    it('should generate a page artifact', () => {
      const artifact = generatePageArtifact('HomePage', 'export const HomePage = () => null;');

      expect(artifact.type).toBe('page');
      expect(artifact.name).toBe('HomePage');
      expect(artifact.language).toBe('typescript');
      expect(artifact.path).toBe('pages/HomePage.tsx');
    });
  });

  describe('generateSceneArtifact', () => {
    it('should generate a scene artifact', () => {
      const artifact = generateSceneArtifact('Scene1', '{"nodes": []}');

      expect(artifact.type).toBe('scene');
      expect(artifact.name).toBe('Scene1');
      expect(artifact.language).toBe('json');
      expect(artifact.path).toBe('scenes/Scene1.json');
    });
  });

  describe('generateConfigArtifact', () => {
    it('should generate a config artifact', () => {
      const artifact = generateConfigArtifact('app', '{"name": "app"}');

      expect(artifact.type).toBe('config');
      expect(artifact.name).toBe('app');
      expect(artifact.language).toBe('json');
      expect(artifact.path).toBe('configs/app.json');
    });
  });

  describe('generateStyleArtifact', () => {
    it('should generate a style artifact', () => {
      const artifact = generateStyleArtifact('main', '.button { color: red; }');

      expect(artifact.type).toBe('style');
      expect(artifact.name).toBe('main');
      expect(artifact.language).toBe('css');
      expect(artifact.path).toBe('styles/main.css');
    });
  });

  describe('formatArtifactContent', () => {
    it('should format content with indentation', () => {
      const content = 'line1\nline2\nline3';
      const formatted = formatArtifactContent(content, 2);

      expect(formatted).toContain('  line1');
      expect(formatted).toContain('  line2');
      expect(formatted).toContain('  line3');
    });

    it('should handle empty lines', () => {
      const content = 'line1\n\nline2';
      const formatted = formatArtifactContent(content, 2);

      expect(formatted).toContain('  line1');
      expect(formatted).toContain('\n');
      expect(formatted).toContain('  line2');
    });
  });

  describe('validateArtifact', () => {
    it('should validate valid artifact', () => {
      const artifact = {
        type: 'component' as const,
        name: 'Button',
        content: 'export const Button = () => null;',
        language: 'typescript',
        path: 'components/Button.tsx',
      };

      const result = validateArtifact(artifact);

      expect(result.valid).toBe(true);
      expect(result.errors).toEqual([]);
    });

    it('should reject artifact without name', () => {
      const artifact = {
        type: 'component' as const,
        name: '',
        content: 'export const Button = () => null;',
      };

      const result = validateArtifact(artifact);

      expect(result.valid).toBe(false);
      expect(result.errors.length).toBeGreaterThan(0);
    });

    it('should reject artifact without content', () => {
      const artifact = {
        type: 'component' as const,
        name: 'Button',
        content: '',
      };

      const result = validateArtifact(artifact);

      expect(result.valid).toBe(false);
      expect(result.errors.length).toBeGreaterThan(0);
    });

    it('should reject artifact with invalid type', () => {
      const artifact = {
        type: 'invalid' as any,
        name: 'Button',
        content: 'export const Button = () => null;',
      };

      const result = validateArtifact(artifact);

      expect(result.valid).toBe(false);
      expect(result.errors.length).toBeGreaterThan(0);
    });
  });
});
