/**
 * @fileoverview DataUseNotice - Privacy disclosure for AI data use.
 *
 * @doc.type component
 * @doc.purpose Informs users about how their data is used in AI processing.
 * @doc.category atom
 * @doc.tags ai, privacy, disclosure
 */

import * as React from 'react';

export interface DataUseNoticeProps {
  /** Size variant */
  readonly size?: 'sm' | 'md' | 'lg';
  /** Whether data may be sent to external AI services */
  readonly usesExternalAI?: boolean;
  /** Specific data types being used */
  readonly dataTypes?: readonly string[];
  /** Retention period in days */
  readonly retentionDays?: number;
  /** Additional CSS classes */
  readonly className?: string;
  /** Custom message to display */
  readonly customMessage?: string;
}

const sizeConfig = {
  sm: 'text-xs p-2 gap-1.5',
  md: 'text-sm p-3 gap-2',
  lg: 'text-base p-4 gap-3',
};

const iconSizeConfig = {
  sm: 'h-3 w-3',
  md: 'h-4 w-4',
  lg: 'h-5 w-5',
};

/**
 * Shield/privacy icon.
 */
const PrivacyIcon: React.FC<{ className?: string }> = ({ className = '' }) => (
  <svg
    className={className}
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
  >
    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
    <path d="M9 12l2 2 4-4" />
  </svg>
);

/**
 * DataUseNotice component - privacy disclosure for AI data use.
 */
export const DataUseNotice: React.FC<DataUseNoticeProps> = React.memo(({
  size = 'md',
  usesExternalAI = false,
  dataTypes = [],
  retentionDays = 90,
  className = '',
  customMessage,
}) => {
  const sizeClasses = sizeConfig[size];
  const iconSize = iconSizeConfig[size];

  const defaultMessage = usesExternalAI
    ? `Data is processed by AI services. Retained for ${retentionDays} days.`
    : `Data is processed locally by AI. Retained for ${retentionDays} days.`;

  const message = customMessage ?? defaultMessage;

  return (
    <div
      className={`inline-flex items-start rounded-md bg-blue-50 text-blue-700 ${sizeClasses} ${className}`}
      role="note"
      aria-label="Data use notice"
    >
      <PrivacyIcon className={`${iconSize} flex-shrink-0 mt-0.5`} />
      <div className="flex-1 min-w-0">
        <p className="font-medium">{message}</p>
        {dataTypes.length > 0 && (
          <p className="text-blue-600/70 mt-1">
            Uses: {dataTypes.join(', ')}
          </p>
        )}
      </div>
    </div>
  );
});

DataUseNotice.displayName = 'DataUseNotice';

/**
 * Compact data use badge for inline display.
 */
export interface DataUseBadgeProps {
  readonly usesExternalAI?: boolean;
  readonly className?: string;
}

export const DataUseBadge: React.FC<DataUseBadgeProps> = React.memo(({
  usesExternalAI = false,
  className = '',
}) => {
  return (
    <span
      className={`inline-flex items-center gap-1 text-xs text-blue-600 bg-blue-50 px-1.5 py-0.5 rounded ${className}`}
      title={usesExternalAI ? 'Uses external AI services' : 'Processed locally'}
    >
      <PrivacyIcon className="h-3 w-3" />
      <span>{usesExternalAI ? 'External AI' : 'Local AI'}</span>
    </span>
  );
});

DataUseBadge.displayName = 'DataUseBadge';
