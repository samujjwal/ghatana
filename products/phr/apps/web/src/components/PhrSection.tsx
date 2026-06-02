import React from 'react';

/**
 * PhrSection - Standard section component
 * Provides consistent section structure with optional header and content
 * Used within PhrPage to organize content into logical sections
 */

interface PhrSectionProps {
  title?: string;
  subtitle?: string;
  children: React.ReactNode;
  className?: string;
  variant?: 'default' | 'card' | 'panel';
}

export function PhrSection({
  title,
  subtitle,
  children,
  className = '',
  variant = 'default',
}: PhrSectionProps): React.ReactElement {
  const baseClasses = 'phr-section';
  const variantClasses = {
    default: '',
    card: 'bg-white rounded-lg shadow-sm border border-gray-200 p-6',
    panel: 'bg-gray-50 rounded-lg p-4',
  };

  return (
    <section className={`${baseClasses} ${variantClasses[variant]} ${className}`}>
      {(title || subtitle) && (
        <header className="phr-section-header mb-4">
          {title && <h2 className="text-lg font-semibold text-gray-900">{title}</h2>}
          {subtitle && <p className="text-gray-600 text-sm mt-1">{subtitle}</p>}
        </header>
      )}
      <div className="phr-section-content">{children}</div>
    </section>
  );
}
