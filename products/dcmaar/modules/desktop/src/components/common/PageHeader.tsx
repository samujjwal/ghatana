import React from 'react';
import { Box, Button, Stack } from '../../ui/tw-compat';
import { Link as RouterLink } from 'react-router-dom';
import { PageHeader as SharedPageHeader } from '@ghatana/product-shell';

export interface PageHeaderAction {
  label: string;
  onClick?: () => void;
  icon?: React.ReactNode;
  href?: string;
  variant?: 'contained' | 'outlined' | 'text';
  external?: boolean;
}

export interface PageHeaderProps {
  title: string;
  description?: string;
  breadcrumbs?: Array<{ label: string; href?: string }>;
  actions?: PageHeaderAction[];
}

export const PageHeader: React.FC<PageHeaderProps> = ({
  title,
  description,
  breadcrumbs,
  actions,
}) => {
  const eyebrow = breadcrumbs && breadcrumbs.length > 0 ? (
    <nav aria-label="breadcrumb" className="flex items-center gap-1 text-sm text-gray-500 dark:text-gray-400">
      {breadcrumbs.map((crumb, i) => (
        <React.Fragment key={crumb.label}>
          {i > 0 && <span className="mx-1">›</span>}
          {crumb.href ? (
            <RouterLink to={crumb.href} className="font-medium hover:text-gray-700 dark:hover:text-gray-200">
              {crumb.label}
            </RouterLink>
          ) : (
            <span>{crumb.label}</span>
          )}
        </React.Fragment>
      ))}
    </nav>
  ) : undefined;

  const actionNodes = actions && actions.length > 0 ? (
    <Stack direction="row" className="gap-1 flex-wrap items-center">
      {actions.map((action) => (
        <Button
          key={action.label}
          variant={action.variant ?? 'contained'}
          onClick={action.onClick}
          startIcon={action.icon}
        >
          {action.label}
        </Button>
      ))}
    </Stack>
  ) : undefined;

  return (
    <Box>
      <SharedPageHeader
        title={title}
        description={description}
        eyebrow={eyebrow}
        actions={actionNodes}
      />
    </Box>
  );
};

export default PageHeader;
