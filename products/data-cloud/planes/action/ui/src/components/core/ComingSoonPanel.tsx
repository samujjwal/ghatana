/**
 * ComingSoonPanel — Placeholder panel for unimplemented features.
 *
 * @doc.type component
 * @doc.purpose Display "Coming Soon" or enterprise feature messaging for unimplemented features
 * @doc.layer frontend
 */

import React from 'react';
import { Lock, Zap } from 'lucide-react';

interface ComingSoonPanelProps {
  title: string;
  description?: string;
  /**
   * Optional: Whether this is an enterprise/premium feature
   */
  isEnterprise?: boolean;
  /**
   * Optional: Custom action button
   */
  action?: React.ReactNode;
}

/**
 * ComingSoonPanel component
 *
 * Displays a placeholder message for features that are not yet implemented
 * or are available only in enterprise/premium tiers.
 */
export const ComingSoonPanel: React.FC<ComingSoonPanelProps> = ({
  title,
  description,
  isEnterprise = false,
  action,
}) => {
  return (
    <div className="flex flex-col items-center justify-center p-12 text-center">
      <div className="bg-gray-100 dark:bg-gray-800 rounded-full p-4 mb-4">
        {isEnterprise ? (
          <Lock className="h-8 w-8 text-gray-500 dark:text-gray-400" />
        ) : (
          <Zap className="h-8 w-8 text-gray-500 dark:text-gray-400" />
        )}
      </div>
      <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-2">
        {title}
      </h3>
      <p className="text-sm text-gray-600 dark:text-gray-400 max-w-md mb-6">
        {description ||
          (isEnterprise
            ? 'This feature is available in the Enterprise edition. Contact your administrator to upgrade.'
            : 'This feature is coming soon. We\'re working hard to bring it to you.')}
      </p>
      {action}
    </div>
  );
};

/**
 * ComingSoonSection component for inline sections
 */
export const ComingSoonSection: React.FC<{
  title: string;
  description?: string;
  isEnterprise?: boolean;
}> = ({ title, description, isEnterprise }) => {
  return (
    <div className="border border-dashed border-gray-300 dark:border-gray-700 rounded-lg p-6 bg-gray-50 dark:bg-gray-900/50">
      <div className="flex items-center gap-3 mb-2">
        {isEnterprise ? (
          <Lock className="h-4 w-4 text-gray-500" />
        ) : (
          <Zap className="h-4 w-4 text-gray-500" />
        )}
        <h4 className="text-sm font-medium text-gray-700 dark:text-gray-300">{title}</h4>
      </div>
      <p className="text-xs text-gray-600 dark:text-gray-400">
        {description ||
          (isEnterprise
            ? 'Enterprise feature. Contact your administrator.'
            : 'Coming soon.')}
      </p>
    </div>
  );
};
