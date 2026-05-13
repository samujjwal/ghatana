/**
 * Badge - A simple status badge component
 * 
 * @doc.type component
 * @doc.purpose Display status badges
 * @doc.layer platform
 */

import React from 'react';

function cn(...classes: Array<string | false | null | undefined>): string {
  return classes.filter(Boolean).join(' ');
}

export interface BadgeProps {
  readonly children: React.ReactNode;
  readonly color?: 'green' | 'blue' | 'red' | 'yellow' | 'gray' | 'purple' | 'orange';
  readonly size?: 'sm' | 'md';
  readonly className?: string;
}

const colorClasses = {
  green: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200',
  blue: 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200',
  red: 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200',
  yellow: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200',
  gray: 'bg-gray-100 text-gray-800 dark:bg-gray-900 dark:text-gray-200',
  purple: 'bg-purple-100 text-purple-800 dark:bg-purple-900 dark:text-purple-200',
  orange: 'bg-orange-100 text-orange-800 dark:bg-orange-900 dark:text-orange-200',
};

const sizeClasses = {
  sm: 'text-xs px-2 py-0.5',
  md: 'text-sm px-2.5 py-1',
};

export function Badge({ children, color = 'gray', size = 'md', className }: BadgeProps): React.ReactElement {
  return (
    <span className={cn('inline-flex items-center rounded-full font-medium', colorClasses[color], sizeClasses[size], className)}>
      {children}
    </span>
  );
}
