/**
 * ProductEnvironmentBadge
 * 
 * Displays a badge for a deployment environment.
 * 
 * @doc.type component
 * @doc.purpose Display deployment environment badge
 * @doc.layer platform
 */

import type { DeploymentEnvironment } from '../../contracts/product-deployment';

interface ProductEnvironmentBadgeProps {
  readonly environment: DeploymentEnvironment;
  readonly className?: string;
}

const environmentColors = {
  local: 'bg-gray-100 text-gray-800 dark:bg-gray-900 dark:text-gray-200',
  dev: 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200',
  staging: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200',
  prod: 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200',
};

export function ProductEnvironmentBadge({ environment, className }: ProductEnvironmentBadgeProps) {
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-1 text-xs font-medium ${environmentColors[environment]} ${className || ''}`}>
      {environment}
    </span>
  );
}
