/**
 * Utility functions for live edit plugin.
 *
 * <p><b>Purpose</b><br>
 * Provides helper functions for component file detection, metadata extraction,
 * and code transformation.
 *
 * @doc.type module
 * @doc.purpose Live edit plugin utilities
 * @doc.layer product
 * @doc.pattern Utilities
 */

import type { ComponentMetadata, PropDefinition } from './types';
import { minimatch } from 'minimatch';

/**
 * Checks if a file is a React component file.
 *
 * <p><b>Purpose</b><br>
 * Determines if a file should be processed by the live edit plugin
 * based on file extension and patterns.
 *
 * @param filePath - File path to check
 * @param include - Include patterns
 * @param exclude - Exclude patterns
 * @returns True if file is a component file
 *
 * @doc.type function
 * @doc.purpose Check if file is component
 * @doc.layer product
 * @doc.pattern Utility
 */
export function isComponentFile(
  filePath: string,
  include: string[] = ['**/*.tsx', '**/*.jsx'],
  exclude: string[] = ['node_modules', 'dist']
): boolean {
  // Check exclude patterns first
  for (const pattern of exclude) {
    if (minimatch(filePath, pattern)) {
      return false;
    }
  }

  // Check include patterns
  for (const pattern of include) {
    if (minimatch(filePath, pattern)) {
      return true;
    }
  }

  return false;
}

/**
 * Extracts component metadata from source code.
 *
 * <p><b>Purpose</b><br>
 * Parses component source code to extract metadata including props,
 * state, events, and imports.
 *
 * @param code - Component source code
 * @param filePath - File path
 * @returns Component metadata
 *
 * @doc.type function
 * @doc.purpose Extract component metadata
 * @doc.layer product
 * @doc.pattern Utility
 */
export function extractMetadata(code: string, filePath: string): ComponentMetadata {
  const componentName = extractComponentName(code, filePath);
  const props = extractProps(code);
  const state = extractState(code);
  const events = extractEvents(code);
  const imports = extractImports(code);
  const isDefault = isDefaultExport(code, componentName);

  return {
    name: componentName,
    file: filePath,
    props,
    state,
    events,
    imports,
    isDefault,
    source: code,
  };
}

/**
 * Extracts component name from source code.
 *
 * <p><b>Purpose</b><br>
 * Determines the component name from the file path or export statement.
 *
 * @param code - Component source code
 * @param filePath - File path
 * @returns Component name
 *
 * @doc.type function
 * @doc.purpose Extract component name
 * @doc.layer product
 * @doc.pattern Utility
 */
export function extractComponentName(code: string, filePath: string): string {
  // Try to find named export
  const namedExportMatch = code.match(/export\s+(?:default\s+)?(?:function|const)\s+(\w+)/);
  if (namedExportMatch) {
    return namedExportMatch[1];
  }

  // Fall back to file name
  const fileName = filePath.split('/').pop()?.replace(/\.(tsx|jsx)$/, '');
  return fileName || 'Component';
}

/**
 * Extracts prop definitions from component code.
 *
 * <p><b>Purpose</b><br>
 * Parses component props from TypeScript interface or prop destructuring.
 *
 * @param code - Component source code
 * @returns Array of prop definitions
 *
 * @doc.type function
 * @doc.purpose Extract props
 * @doc.layer product
 * @doc.pattern Utility
 */
export function extractProps(code: string): PropDefinition[] {
  const props: PropDefinition[] = [];

  // Look for interface Props
  const interfaceMatch = code.match(/interface\s+Props\s*{([^}]+)}/);
  if (interfaceMatch) {
    const propsText = interfaceMatch[1];
    const propMatches = propsText.matchAll(/(\w+)\??:\s*([^;]+);/g);

    for (const match of propMatches) {
      props.push({
        name: match[1],
        type: match[2].trim(),
        required: !match[0].includes('?'),
      });
    }
  }

  return props;
}

/**
 * Extracts state definitions from component code.
 *
 * <p><b>Purpose</b><br>
 * Parses component state from useState hooks.
 *
 * @param code - Component source code
 * @returns Array of state definitions
 *
 * @doc.type function
 * @doc.purpose Extract state
 * @doc.layer product
 * @doc.pattern Utility
 */
export function extractState(code: string) {
  const state = [];

  // Look for useState hooks
  const stateMatches = code.matchAll(/const\s+\[(\w+),\s*set\w+\]\s*=\s*useState\s*\(([^)]+)\)/g);

  for (const match of stateMatches) {
    state.push({
      name: match[1],
      type: 'unknown',
      initialValue: match[2],
    });
  }

  return state;
}

/**
 * Extracts event definitions from component code.
 *
 * <p><b>Purpose</b><br>
 * Parses component event handlers.
 *
 * @param code - Component source code
 * @returns Array of event definitions
 *
 * @doc.type function
 * @doc.purpose Extract events
 * @doc.layer product
 * @doc.pattern Utility
 */
export function extractEvents(code: string) {
  const events = [];

  // Look for event handler definitions
  const eventMatches = code.matchAll(/const\s+(\w+)\s*=\s*\(([^)]*)\)\s*=>/g);

  for (const match of eventMatches) {
    events.push({
      name: match[1],
      type: 'function',
    });
  }

  return events;
}

/**
 * Extracts import statements from component code.
 *
 * <p><b>Purpose</b><br>
 * Parses all import statements in the component.
 *
 * @param code - Component source code
 * @returns Array of import definitions
 *
 * @doc.type function
 * @doc.purpose Extract imports
 * @doc.layer product
 * @doc.pattern Utility
 */
export function extractImports(code: string) {
  const imports = [];

  // Look for import statements
  const importMatches = code.matchAll(/import\s+(?:{([^}]+)}|(\w+))\s+from\s+['"]([^'"]+)['"]/g);

  for (const match of importMatches) {
    if (match[1]) {
      // Named imports
      const names = match[1].split(',').map((n) => n.trim());
      for (const name of names) {
        imports.push({
          name,
          source: match[3],
          default: false,
        });
      }
    } else if (match[2]) {
      // Default import
      imports.push({
        name: match[2],
        source: match[3],
        default: true,
      });
    }
  }

  return imports;
}

/**
 * Checks if component is exported as default.
 *
 * <p><b>Purpose</b><br>
 * Determines if the component is the default export.
 *
 * @param code - Component source code
 * @param componentName - Component name
 * @returns True if default export
 *
 * @doc.type function
 * @doc.purpose Check if default export
 * @doc.layer product
 * @doc.pattern Utility
 */
export function isDefaultExport(code: string, componentName: string): boolean {
  return new RegExp(`export\\s+default\\s+${componentName}`).test(code);
}

/**
 * Injects tracking code into component source.
 *
 * <p><b>Purpose</b><br>
 * Adds metadata tracking code to enable live editing support.
 *
 * @param code - Component source code
 * @param metadata - Component metadata
 * @returns Transformed code with tracking
 *
 * @doc.type function
 * @doc.purpose Inject tracking code
 * @doc.layer product
 * @doc.pattern Utility
 */
export function injectTracking(code: string, metadata: ComponentMetadata): string {
  // Add tracking metadata as a comment
  const trackingComment = `
/* @yappc-tracking
 * Component: ${metadata.name}
 * Props: ${metadata.props.map((p) => p.name).join(', ')}
 * State: ${metadata.state.map((s) => s.name).join(', ')}
 */
`;

  // Inject tracking code before component export
  return trackingComment + code;
}
