import React from 'react';
import { tokens } from '@ghatana/tokens';

export interface BreadcrumbItem {
  label: string;
  href?: string;
  onClick?: () => void;
  icon?: React.ReactNode;
}

export interface BreadcrumbProps {
  /** Breadcrumb items */
  items: BreadcrumbItem[];
  /** Separator */
  separator?: React.ReactNode;
  /** Size */
  size?: 'sm' | 'md' | 'lg';
  /** Max items to show before collapsing */
  maxItems?: number;
  /** Additional class name */
  className?: string;
}

export const Breadcrumb: React.FC<BreadcrumbProps> = ({
  items,
  separator = '/',
  size = 'md',
  maxItems,
  className,
}) => {
  const sizeConfig = {
    sm: { fontSize: tokens.typography.fontSize.xs, gap: tokens.spacing[1] },
    md: { fontSize: tokens.typography.fontSize.sm, gap: tokens.spacing[2] },
    lg: { fontSize: tokens.typography.fontSize.base, gap: tokens.spacing[3] },
  };

  const config = sizeConfig[size];

  const containerStyles: React.CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    gap: config.gap,
    flexWrap: 'wrap',
    fontFamily: tokens.typography.fontFamily.sans,
    fontSize: config.fontSize,
  };

  const itemStyles: React.CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    gap: tokens.spacing[1],
    color: tokens.colors.neutral[600],
    textDecoration: 'none',
    transition: `color ${tokens.transitions.duration.fast} ${tokens.transitions.easing.easeInOut}`,
  };

  const activeItemStyles: React.CSSProperties = {
    ...itemStyles,
    color: tokens.colors.neutral[900],
    fontWeight: tokens.typography.fontWeight.semibold,
  };

  const separatorStyles: React.CSSProperties = {
    color: tokens.colors.neutral[400],
    userSelect: 'none',
  };

  const collapsedButtonStyles: React.CSSProperties = {
    background: 'none',
    border: 'none',
    color: tokens.colors.neutral[600],
    cursor: 'pointer',
    padding: tokens.spacing[1],
    fontSize: config.fontSize,
  };

  const displayItems = maxItems && items.length > maxItems
    ? [
        items[0],
        { label: '...', onClick: undefined, href: undefined },
        ...items.slice(-(maxItems - 2)),
      ]
    : items;

  const renderItem = (item: BreadcrumbItem, index: number, isLast: boolean) => {
    const isCollapsed = item.label === '...';
    const styles = isLast ? activeItemStyles : itemStyles;

    if (isCollapsed) {
      return (
        <button
          key={index}
          style={collapsedButtonStyles}
          onClick={() => {}}
          aria-label="Show more breadcrumbs"
        >
          {item.label}
        </button>
      );
    }

    const content = (
      <>
        {item.icon && <span>{item.icon}</span>}
        {item.label}
      </>
    );

    if (item.href && !isLast) {
      return (
        <a
          key={index}
          href={item.href}
          style={styles}
          onMouseEnter={(e) => {
            e.currentTarget.style.color = tokens.colors.primary[600];
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.color = tokens.colors.neutral[600];
          }}
        >
          {content}
        </a>
      );
    }

    if (item.onClick && !isLast) {
      return (
        <button
          key={index}
          onClick={item.onClick}
          style={{
            ...styles,
            background: 'none',
            border: 'none',
            cursor: 'pointer',
            padding: 0,
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.color = tokens.colors.primary[600];
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.color = tokens.colors.neutral[600];
          }}
        >
          {content}
        </button>
      );
    }

    return (
      <span key={index} style={styles} aria-current={isLast ? 'page' : undefined}>
        {content}
      </span>
    );
  };

  return (
    <nav aria-label="Breadcrumb" style={containerStyles} className={className}>
      <ol style={{ display: 'flex', alignItems: 'center', gap: config.gap, listStyle: 'none', margin: 0, padding: 0 }}>
        {displayItems.map((item, index) => {
          const isLast = index === displayItems.length - 1;
          return (
            <React.Fragment key={index}>
              <li>{renderItem(item, index, isLast)}</li>
              {!isLast && <li style={separatorStyles} aria-hidden="true">{separator}</li>}
            </React.Fragment>
          );
        })}
      </ol>
    </nav>
  );
};

Breadcrumb.displayName = 'Breadcrumb';
