/**
 * Card - A simple card component
 * 
 * @doc.type component
 * @doc.purpose Display content in a card
 * @doc.layer platform
 */

import React from 'react';

function cn(...classes: Array<string | false | null | undefined>): string {
  return classes.filter(Boolean).join(' ');
}

export interface CardProps {
  readonly children: React.ReactNode;
  readonly className?: string;
}

export function Card({ children, className }: CardProps): React.ReactElement {
  return (
    <div className={cn('rounded-lg border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-900', className)}>
      {children}
    </div>
  );
}
