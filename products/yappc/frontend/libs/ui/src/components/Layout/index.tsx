/**
 * Shared layout components for YAPPC frontend
 * Extracted to eliminate duplication between routes
 */

import React from 'react';
import { Link } from 'react-router';

export interface PageHeaderProps {
  title: string;
  subtitle?: string;
  backLink?: string;
  backLabel?: string;
  actions?: React.ReactNode;
}

/**
 * Standardized page header component
 * Eliminates duplication between projects.tsx, workspaces.tsx, and other routes
 */
export function PageHeader({ title, subtitle, backLink, backLabel, actions }: PageHeaderProps) {
  return (
    <header style={{ marginBottom: '1.5rem' }}>
      {backLink && (
        <nav style={{ marginBottom: '1rem' }}>
          <Link
            to={backLink}
            style={{
              color: 'var(--color-text-secondary, #666)',
              textDecoration: 'none',
              fontSize: '0.875rem',
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.25rem'
            }}
          >
            ← {backLabel || 'Back'}
          </Link>
        </nav>
      )}
      <h1 style={{ margin: 0, fontSize: '2rem', fontWeight: 600, color: 'var(--color-text-primary, #111)' }}>
        {title}
      </h1>
      {subtitle && (
        <p style={{ margin: '0.5rem 0 0 0', color: 'var(--color-text-secondary, #666)' }}>
          {subtitle}
        </p>
      )}
      {actions && (
        <div style={{ marginTop: '1rem', display: 'flex', gap: '0.5rem' }}>
          {actions}
        </div>
      )}
    </header>
  );
}

export interface LayoutCardProps {
  children: React.ReactNode;
  title?: string;
  action?: React.ReactNode;
  style?: React.CSSProperties;
}

/**
 * Standardized card component for consistent layout
 * Eliminates duplication in project/overview.tsx and other route components
 */
export function LayoutCard({ children, title, action, style }: LayoutCardProps) {
  return (
    <div
      style={{
        padding: '1.5rem',
        backgroundColor: 'white',
        borderRadius: '8px',
        border: '1px solid var(--color-border, #e0e0e0)',
        ...style
      }}
    >
      {(title || action) && (
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginBottom: '1rem'
          }}
        >
          {title && <h3 style={{ margin: 0, fontSize: '1.125rem', fontWeight: 600 }}>{title}</h3>}
          {action}
        </div>
      )}
      {children}
    </div>
  );
}

export interface EntityCardProps {
  name: string;
  description?: string;
  meta?: React.ReactNode;
  linkTo: string;
  badges?: Array<{ label: string; color?: string }>;
}

/**
 * Entity card for displaying items in a grid/list
 * Used for projects, workspaces, and other entities
 */
export function EntityCard({ name, description, meta, linkTo, badges }: EntityCardProps) {
  return (
    <Link
      to={linkTo}
      style={{
        display: 'block',
        padding: '1.5rem',
        backgroundColor: 'white',
        borderRadius: '8px',
        border: '1px solid var(--color-border, #e0e0e0)',
        textDecoration: 'none',
        color: 'inherit',
        transition: 'box-shadow 0.2s, border-color 0.2s'
      }}
    >
      <h3 style={{ margin: '0 0 0.5rem 0', fontSize: '1.25rem', fontWeight: 600 }}>{name}</h3>
      {description && (
        <p style={{ margin: '0 0 0.75rem 0', color: 'var(--color-text-secondary, #666)', fontSize: '0.875rem' }}>
          {description}
        </p>
      )}
      {badges && badges.length > 0 && (
        <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '0.75rem' }}>
          {badges.map((badge, i) => (
            <span
              key={i}
              style={{
                padding: '0.25rem 0.5rem',
                backgroundColor: badge.color || 'var(--color-surface-secondary, #f5f5f5)',
                borderRadius: '4px',
                fontSize: '0.75rem',
                color: 'var(--color-text-secondary, #666)'
              }}
            >
              {badge.label}
            </span>
          ))}
        </div>
      )}
      {meta && (
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.875rem', color: 'var(--color-text-secondary, #666)' }}>
          {meta}
        </div>
      )}
    </Link>
  );
}
