/**
 * Plugin Version Comparison Component
 *
 * Shows version comparison between installed and available versions
 * with upgrade prompts and change highlights.
 *
 * @doc.type component
 * @doc.purpose Plugin version comparison
 * @doc.layer frontend
 */

import React from 'react';
import { ArrowUp, AlertCircle, CheckCircle, Info } from 'lucide-react';
import { cn, buttonStyles } from '../../lib/theme';

interface PluginVersionCompareProps {
  currentVersion: string;
  availableVersion?: string;
  onUpgrade?: () => void;
  upgrading?: boolean;
  changelog?: string[];
}

/**
 * Parse semantic version
 */
function parseVersion(version: string): { major: number; minor: number; patch: number } {
  const parts = version.split('.').map((p) => parseInt(p, 10));
  return {
    major: parts[0] || 0,
    minor: parts[1] || 0,
    patch: parts[2] || 0,
  };
}

/**
 * Compare two versions
 */
function compareVersions(v1: string, v2: string): 'major' | 'minor' | 'patch' | 'same' {
  const ver1 = parseVersion(v1);
  const ver2 = parseVersion(v2);

  if (ver2.major > ver1.major) return 'major';
  if (ver2.minor > ver1.minor) return 'minor';
  if (ver2.patch > ver1.patch) return 'patch';
  return 'same';
}

/**
 * Plugin Version Compare Component
 */
export function PluginVersionCompare({
  currentVersion,
  availableVersion,
  onUpgrade,
  upgrading = false,
  changelog = [],
}: PluginVersionCompareProps): React.ReactElement | null {
  if (!availableVersion || currentVersion === availableVersion) {
    return null;
  }

  const updateType = compareVersions(currentVersion, availableVersion);
  
  if (updateType === 'same') {
    return null;
  }

  const severityConfig = {
    major: {
      color: 'text-red-600',
      bg: 'bg-red-50 dark:bg-red-900/20',
      border: 'border-red-200 dark:border-red-800',
      icon: <AlertCircle className="h-5 w-5" />,
      label: 'Major Update',
      description: 'Breaking changes may be included',
    },
    minor: {
      color: 'text-amber-600',
      bg: 'bg-amber-50 dark:bg-amber-900/20',
      border: 'border-amber-200 dark:border-amber-800',
      icon: <Info className="h-5 w-5" />,
      label: 'Minor Update',
      description: 'New features and improvements',
    },
    patch: {
      color: 'text-green-600',
      bg: 'bg-green-50 dark:bg-green-900/20',
      border: 'border-green-200 dark:border-green-800',
      icon: <CheckCircle className="h-5 w-5" />,
      label: 'Patch Update',
      description: 'Bug fixes and security updates',
    },
  };

  const config = severityConfig[updateType];

  return (
    <div className={cn('p-4 rounded-lg border', config.bg, config.border)}>
      <div className="flex items-start justify-between gap-4">
        <div className="flex items-start gap-3 flex-1">
          <div className={cn('flex-shrink-0', config.color)}>
            {config.icon}
          </div>
          <div className="flex-1">
            <div className="flex items-center gap-2 mb-1">
              <h4 className={cn('font-medium', config.color)}>{config.label} Available</h4>
              <span className="text-sm text-gray-600 dark:text-gray-400">
                v{currentVersion} → v{availableVersion}
              </span>
            </div>
            <p className="text-sm text-gray-600 dark:text-gray-400 mb-3">
              {config.description}
            </p>

            {/* Changelog */}
            {changelog.length > 0 && (
              <div className="space-y-1 mb-3">
                <p className="text-xs font-medium text-gray-700 dark:text-gray-300 mb-2">
                  What's new:
                </p>
                <ul className="space-y-1">
                  {changelog.slice(0, 3).map((change, idx) => (
                    <li key={idx} className="text-xs text-gray-600 dark:text-gray-400 flex items-start gap-2">
                      <span className="text-gray-400">•</span>
                      <span>{change}</span>
                    </li>
                  ))}
                </ul>
                {changelog.length > 3 && (
                  <p className="text-xs text-gray-500 dark:text-gray-500 mt-2">
                    ...and {changelog.length - 3} more changes
                  </p>
                )}
              </div>
            )}
          </div>
        </div>

        {/* Upgrade Button */}
        {onUpgrade && (
          <button
            onClick={onUpgrade}
            disabled={upgrading}
            className={cn(
              buttonStyles.primary,
              'px-4 py-2 flex-shrink-0',
              upgrading && 'opacity-50 cursor-not-allowed'
            )}
          >
            {upgrading ? (
              <>
                <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin mr-2" />
                Upgrading...
              </>
            ) : (
              <>
                <ArrowUp className="h-4 w-4 mr-2" />
                Upgrade
              </>
            )}
          </button>
        )}
      </div>

      {/* Warning for major updates */}
      {updateType === 'major' && (
        <div className="mt-3 p-3 bg-white dark:bg-gray-800/50 rounded border border-red-200 dark:border-red-800">
          <p className="text-xs text-gray-700 dark:text-gray-300 flex items-start gap-2">
            <AlertCircle className="h-4 w-4 text-red-600 flex-shrink-0 mt-0.5" />
            <span>
              <strong>Important:</strong> This is a major version update that may include breaking changes.
              Review the changelog and test in a non-production environment first.
            </span>
          </p>
        </div>
      )}
    </div>
  );
}
