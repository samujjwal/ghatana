/**
 * Artifact Generator Utilities
 *
 * Utility functions for artifact generation.
 *
 * @packageDocumentation
 */

import type { GeneratedArtifact } from '../types';
import {
  validateIntentConfig,
  validatePageConfig,
  validateRequirementConfig,
} from 'yappc-config-schema';

export type GeneratedConfigKind = 'page' | 'intent' | 'requirement';

export interface GeneratedProjectConfigValidationResult {
  valid: boolean;
  errors: string[];
}

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

function resolveGeneratedConfigKind(artifact: GeneratedArtifact): GeneratedConfigKind | null {
  const metadataKind = artifact.metadata?.configKind;
  if (metadataKind === 'page' || metadataKind === 'intent' || metadataKind === 'requirement') {
    return metadataKind;
  }

  const path = artifact.path?.toLowerCase() ?? '';
  const name = artifact.name.toLowerCase();
  if (path.includes('page') || name.includes('page')) {
    return 'page';
  }
  if (path.includes('intent') || name.includes('intent')) {
    return 'intent';
  }
  if (path.includes('requirement') || name.includes('requirement')) {
    return 'requirement';
  }
  return null;
}

function validateConfigPayload(kind: GeneratedConfigKind, payload: unknown): GeneratedProjectConfigValidationResult {
  switch (kind) {
    case 'page':
      return validatePageConfig(payload);
    case 'intent':
      return validateIntentConfig(payload);
    case 'requirement':
      return validateRequirementConfig(payload);
  }
}

export function validateGeneratedProjectConfigArtifacts(
  artifacts: readonly GeneratedArtifact[],
): GeneratedProjectConfigValidationResult {
  const errors: string[] = [];

  for (const artifact of artifacts) {
    if (artifact.type !== 'config') {
      continue;
    }

    const kind = resolveGeneratedConfigKind(artifact);
    if (kind === null) {
      errors.push(`${artifact.path ?? artifact.name}: config artifact must declare metadata.configKind`);
      continue;
    }

    let payload: unknown;
    try {
      payload = JSON.parse(artifact.content);
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : String(error);
      errors.push(`${artifact.path ?? artifact.name}: config artifact contains invalid JSON (${message})`);
      continue;
    }

    const validation = validateConfigPayload(kind, payload);
    if (!validation.valid) {
      errors.push(
        ...validation.errors.map(error => `${artifact.path ?? artifact.name}: ${error}`),
      );
    }
  }

  return {
    valid: errors.length === 0,
    errors,
  };
}
