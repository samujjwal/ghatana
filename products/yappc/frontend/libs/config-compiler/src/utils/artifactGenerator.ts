/**
 * Artifact Generator Utilities
 *
 * Utility functions for artifact generation.
 *
 * @packageDocumentation
 */

import type { GeneratedArtifact } from '../types';

/**
 * Generate a component artifact
 */
export function generateComponentArtifact(
  name: string,
  code: string,
  metadata?: Record<string, unknown>,
): GeneratedArtifact {
  return {
    type: 'component',
    name,
    content: code,
    language: 'typescript',
    path: `components/${name}.tsx`,
    metadata,
  };
}

/**
 * Generate a page artifact
 */
export function generatePageArtifact(
  name: string,
  code: string,
  metadata?: Record<string, unknown>,
): GeneratedArtifact {
  return {
    type: 'page',
    name,
    content: code,
    language: 'typescript',
    path: `pages/${name}.tsx`,
    metadata,
  };
}

/**
 * Generate a scene artifact
 */
export function generateSceneArtifact(
  name: string,
  sceneData: string,
  metadata?: Record<string, unknown>,
): GeneratedArtifact {
  return {
    type: 'scene',
    name,
    content: sceneData,
    language: 'json',
    path: `scenes/${name}.json`,
    metadata,
  };
}

/**
 * Generate a config artifact
 */
export function generateConfigArtifact(
  name: string,
  config: string,
  metadata?: Record<string, unknown>,
): GeneratedArtifact {
  return {
    type: 'config',
    name,
    content: config,
    language: 'json',
    path: `configs/${name}.json`,
    metadata,
  };
}

/**
 * Generate a style artifact
 */
export function generateStyleArtifact(
  name: string,
  styles: string,
  metadata?: Record<string, unknown>,
): GeneratedArtifact {
  return {
    type: 'style',
    name,
    content: styles,
    language: 'css',
    path: `styles/${name}.css`,
    metadata,
  };
}

/**
 * Format artifact content with indentation
 */
export function formatArtifactContent(
  content: string,
  indent: number = 2,
): string {
  const indentStr = ' '.repeat(indent);
  return content
    .split('\n')
    .map((line) => (line.trim() ? indentStr + line.trim() : line))
    .join('\n');
}

/**
 * Validate artifact
 */
export function validateArtifact(artifact: GeneratedArtifact): {
  valid: boolean;
  errors: string[];
} {
  const errors: string[] = [];

  if (!artifact.name) {
    errors.push('Artifact must have a name');
  }

  if (!artifact.content) {
    errors.push('Artifact must have content');
  }

  if (!artifact.type || !['component', 'page', 'scene', 'config', 'style'].includes(artifact.type)) {
    errors.push('Artifact must have a valid type');
  }

  return {
    valid: errors.length === 0,
    errors,
  };
}
