/**
 * @fileoverview ToolUseDisclosure - Shows which tool or capability was invoked.
 *
 * @doc.type component
 * @doc.purpose Discloses which AI tool or capability is being used.
 * @doc.category atom
 * @doc.tags ai, transparency, disclosure
 */

import * as React from 'react';

export interface ToolUseDisclosureProps {
  /** Name of the tool/capability */
  readonly toolName: string;
  /** Optional tool icon or identifier */
  readonly toolIcon?: React.ReactNode;
  /** Description of what the tool does */
  readonly description?: string;
  /** Size variant */
  readonly size?: 'sm' | 'md' | 'lg';
  /** Additional CSS classes */
  readonly className?: string;
  /** Whether the tool is currently active/running */
  readonly isActive?: boolean;
}

const sizeConfig = {
  sm: 'text-xs px-2 py-1 gap-1.5',
  md: 'text-sm px-2.5 py-1.5 gap-2',
  lg: 'text-base px-3 py-2 gap-2.5',
};

const iconSizeConfig = {
  sm: 'h-3 w-3',
  md: 'h-4 w-4',
  lg: 'h-5 w-5',
};

/**
 * Default tool icon (wrench).
 */
const ToolIcon: React.FC<{ className?: string }> = ({ className = '' }) => (
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
    <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z" />
  </svg>
);

/**
 * ToolUseDisclosure component - shows which tool was invoked.
 */
export const ToolUseDisclosure: React.FC<ToolUseDisclosureProps> = React.memo(({
  toolName,
  toolIcon,
  description,
  size = 'md',
  className = '',
  isActive = false,
}) => {
  const sizeClasses = sizeConfig[size];
  const iconSize = iconSizeConfig[size];
  const icon = toolIcon ?? <ToolIcon className={iconSize} />;

  return (
    <div
      className={`inline-flex items-center rounded-md bg-gray-100 text-gray-700 ${sizeClasses} ${className}`}
      role="note"
      aria-label={`Tool used: ${toolName}`}
    >
      <span className="flex-shrink-0 text-gray-500">{icon}</span>
      <div className="flex flex-col">
        <span className="font-medium">
          {toolName}
          {isActive && (
            <span className="ml-1.5 inline-flex h-1.5 w-1.5 rounded-full bg-blue-500 animate-pulse" aria-label="active" />
          )}
        </span>
        {description && (
          <span className="text-gray-500 text-xs">{description}</span>
        )}
      </div>
    </div>
  );
});

ToolUseDisclosure.displayName = 'ToolUseDisclosure';

/**
 * List of tools used in an operation.
 */
export interface ToolUseListProps {
  readonly tools: readonly {
    readonly name: string;
    readonly description?: string;
    readonly icon?: React.ReactNode;
  }[];
  readonly size?: 'sm' | 'md' | 'lg';
  readonly className?: string;
}

export const ToolUseList: React.FC<ToolUseListProps> = React.memo(({
  tools,
  size = 'md',
  className = '',
}) => {
  if (tools.length === 0) return null;

  return (
    <div className={`flex flex-wrap gap-2 ${className}`}>
      {tools.map((tool, index) => (
        <ToolUseDisclosure
          key={index}
          toolName={tool.name}
          description={tool.description}
          toolIcon={tool.icon}
          size={size}
        />
      ))}
    </div>
  );
});

ToolUseList.displayName = 'ToolUseList';
