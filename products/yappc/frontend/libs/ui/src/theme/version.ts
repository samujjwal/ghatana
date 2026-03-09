/**
 * Theme version information
 * 
 * This file tracks theme versions and changes to help with migrations
 * and backward compatibility.
 */

/**
 *
 */
export interface ThemeVersion {
  /** Semantic version of the theme */
  version: string;
  /** Release date of this version */
  releaseDate: string;
  /** Major changes in this version */
  changes: string[];
  /** Whether this version has breaking changes */
  hasBreakingChanges: boolean;
}

/**
 * History of theme versions
 * Newest versions should be added at the top
 */
export const themeVersions: ThemeVersion[] = [
  {
    version: '1.1.0',
    releaseDate: '2025-09-30',
    changes: [
      'Added theme testing utilities',
      'Improved accessibility with better contrast ratios',
      'Added theme versioning',
      'Created theme migration guide'
    ],
    hasBreakingChanges: false
  },
  {
    version: '1.0.0',
    releaseDate: '2025-08-15',
    changes: [
      'Initial theme implementation',
      'Light and dark mode support',
      'Material UI integration',
      'Custom color palette',
      'Typography scale',
      'Spacing system',
      'Elevation/shadow system'
    ],
    hasBreakingChanges: false
  }
];

/**
 * Current theme version
 */
export const currentVersion = themeVersions[0];

/**
 * Check if the current theme version is compatible with a required version
 * @param requiredVersion - Minimum version required (semver format)
 * @returns Whether the current version is compatible
 */
export function isCompatibleVersion(requiredVersion: string): boolean {
  const current = parseVersion(currentVersion.version);
  const required = parseVersion(requiredVersion);
  
  // Major version must match exactly
  if (current.major !== required.major) {
    return false;
  }
  
  // Current minor version must be >= required minor version
  if (current.minor < required.minor) {
    return false;
  }
  
  // If minor versions match, current patch must be >= required patch
  if (current.minor === required.minor && current.patch < required.patch) {
    return false;
  }
  
  return true;
}

/**
 * Parse a semantic version string into its components
 * @param version - Semantic version string (e.g., "1.2.3")
 * @returns Object with major, minor, and patch numbers
 */
function parseVersion(version: string): { major: number; minor: number; patch: number } {
  const parts = version.split('.').map(Number);
  return {
    major: parts[0] || 0,
    minor: parts[1] || 0,
    patch: parts[2] || 0
  };
}

/**
 * Get breaking changes between versions
 * @param fromVersion - Starting version
 * @param toVersion - Target version (defaults to current)
 * @returns Array of breaking changes
 */
export function getBreakingChanges(
  fromVersion: string,
  toVersion: string = currentVersion.version
): string[] {
  const breakingChanges: string[] = [];
  
  // Find indices of versions
  const fromIndex = themeVersions.findIndex(v => v.version === fromVersion);
  const toIndex = themeVersions.findIndex(v => v.version === toVersion);
  
  if (fromIndex === -1 || toIndex === -1) {
    throw new Error(`Invalid version: ${fromIndex === -1 ? fromVersion : toVersion}`);
  }
  
  // Iterate through versions between from and to (inclusive of toVersion)
  const versionsToCheck = fromIndex < toIndex
    ? themeVersions.slice(toIndex, fromIndex + 1).reverse()
    : themeVersions.slice(fromIndex, toIndex + 1);
  
  versionsToCheck.forEach(version => {
    if (version.hasBreakingChanges) {
      breakingChanges.push(`Version ${version.version}: ${version.changes.join(', ')}`);
    }
  });
  
  return breakingChanges;
}
