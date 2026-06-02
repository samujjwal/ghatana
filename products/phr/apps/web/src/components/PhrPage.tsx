import React from 'react';
import { t } from '../i18n/phrI18n';

/**
 * PhrPage - Standard page layout component
 * Provides consistent page structure with header, content, and action bar
 * Used across all PHR pages to eliminate layout duplication
 */

interface PhrPageProps {
  title: string;
  subtitle?: string;
  children: React.ReactNode;
  actionBar?: React.ReactNode;
  breadcrumbs?: Array<{ label: string; href?: string }>;
  className?: string;
}

export function PhrPage({
  title,
  subtitle,
  children,
  actionBar,
  breadcrumbs,
  className = '',
}: PhrPageProps): React.ReactElement {
  return (
    <div className={`phr-page ${className}`}>
      {breadcrumbs && breadcrumbs.length > 0 && (
        <nav className="phr-breadcrumbs" aria-label="Breadcrumb">
          <ol className="flex items-center gap-2 text-sm">
            {breadcrumbs.map((crumb, index) => (
              <li key={index} className="flex items-center gap-2">
                {index > 0 && <span className="text-gray-400">/</span>}
                {crumb.href ? (
                  <a href={crumb.href} className="text-blue-600 hover:text-blue-700">
                    {crumb.label}
                  </a>
                ) : (
                  <span className="text-gray-600">{crumb.label}</span>
                )}
              </li>
            ))}
          </ol>
        </nav>
      )}
      <header className="phr-page-header">
        <h1 className="text-2xl font-semibold text-gray-900">{title}</h1>
        {subtitle && <p className="text-gray-600 mt-1">{subtitle}</p>}
      </header>
      {actionBar && <div className="phr-action-bar">{actionBar}</div>}
      <main className="phr-page-content">{children}</main>
    </div>
  );
}
