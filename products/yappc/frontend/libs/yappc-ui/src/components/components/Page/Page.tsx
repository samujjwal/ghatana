import React from 'react';

import { Box, Typography, Link } from '@ghatana/design-system';

import { type spacing } from '../../tokens';
import { isMobile } from '../../utils/platform';
import { Container } from '../Container';
import { Stack } from '../Stack';

/** Props for Box-like container, replacing @mui/material BoxProps */
type BoxProps = React.HTMLAttributes<HTMLDivElement> & {
  component?: React.ElementType;
};

/**
 *
 */
export interface PageBreadcrumb {
  /**
   * Label for the breadcrumb
   */
  label: string;

  /**
   * URL for the breadcrumb link
   */
  href?: string;

  /**
   * Click handler for the breadcrumb
   */
  onClick?: () => void;
}

/**
 *
 */
type ContainerMaxWidth = 'xs' | 'sm' | 'md' | 'lg' | 'xl';
/**
 *
 */
type PageBoxProps = Omit<BoxProps, 'maxWidth'>;

/**
 *
 */
export interface PageProps extends PageBoxProps {
  /**
   * Page title
   */
  pageTitle?: React.ReactNode;

  /**
   * Page subtitle or description
   */
  subtitle?: React.ReactNode;

  /**
   * Breadcrumbs for navigation
   */
  breadcrumbs?: PageBreadcrumb[];

  /**
   * Actions to display in the header
   */
  actions?: React.ReactNode;

  /**
   * Content padding
   */
  padding?: keyof typeof spacing | number | string;

  /**
   * Maximum width of the page content
   */
  maxWidth?: ContainerMaxWidth | false;

  /**
   * Whether to use a container for the content
   */
  container?: boolean;

  /**
   * Whether to show a background for the page
   */
  background?: boolean;
}

/** Map padding values to Tailwind classes */
const paddingClasses: Record<number, string> = {
  0: 'p-0',
  1: 'p-1',
  2: 'p-2',
  3: 'p-3',
  4: 'p-4',
  5: 'p-5',
  6: 'p-6',
  8: 'p-8',
};

/**
 * Page component for consistent page layouts
 */
export const Page = React.forwardRef<HTMLDivElement, PageProps>(
  (props, ref) => {
    const {
      children,
      pageTitle,
      subtitle,
      breadcrumbs,
      actions,
      padding = 3,
      maxWidth = 'lg',
      container = true,
      background = false,
      ...rest
    } = props;
    const mobile = isMobile();

    // Render page header with title, subtitle, breadcrumbs, and actions
    const renderHeader = () => (
      <Box className={mobile ? 'mb-4' : 'mb-6'}>
        {breadcrumbs && breadcrumbs.length > 0 && (
          <nav
            aria-label="breadcrumb"
            className="mb-2 flex flex-wrap items-center gap-2"
          >
            {breadcrumbs.map((crumb, index) => {
              const isLast = index === breadcrumbs.length - 1;
              const content = isLast || !crumb.href ? (
                <Typography
                  color={isLast ? 'text.primary' : 'text.secondary'}
                  as="span"
                  className="text-sm"
                >
                  {crumb.label}
                </Typography>
              ) : (
                <Link
                  href={crumb.href}
                  onClick={crumb.onClick}
                  underline="hover"
                  className="text-sm text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100"
                >
                  {crumb.label}
                </Link>
              );

              return (
                <React.Fragment key={`${crumb.label}-${index}`}>
                  {content}
                  {!isLast && (
                    <span className="text-sm text-zinc-400" aria-hidden="true">
                      /
                    </span>
                  )}
                </React.Fragment>
              );
            })}
          </nav>
        )}

        <Box className="flex items-center justify-between flex-wrap gap-4">
          <Stack spacing="gap-1">
            {pageTitle && (
              <Typography
                variant={mobile ? 'h5' : 'h4'}
                component="h1"
                fontWeight="bold"
              >
                {pageTitle}
              </Typography>
            )}

            {subtitle && (
              <Typography as="p" color="text.secondary" className="mt-1">
                {subtitle}
              </Typography>
            )}
          </Stack>

          {actions && (
            <Box className="flex items-center gap-2 shrink-0">{actions}</Box>
          )}
        </Box>
      </Box>
    );

    // Render page content with optional container
    const content = (
      <>
        {(pageTitle || subtitle || breadcrumbs || actions) && renderHeader()}
        {children}
      </>
    );

    const padClass =
      typeof padding === 'number'
        ? paddingClasses[padding] || `p-[${padding * 8}px]`
        : '';

    return (
      <Box
        ref={ref}
        className={['flex flex-col min-h-full w-full', padClass]
          .filter(Boolean)
          .join(' ')}
        {...rest}
      >
        {container ? (
          <Container
            maxWidth={maxWidth === false ? false : maxWidth}
            className="flex flex-col flex-1"
          >
            <Box
              className={[
                'grow flex flex-col',
                background
                  ? 'bg-white dark:bg-gray-900 rounded-lg shadow p-6'
                  : '',
                background && mobile ? 'p-4' : '',
              ]
                .filter(Boolean)
                .join(' ')}
            >
              {content}
            </Box>
          </Container>
        ) : (
          content
        )}
      </Box>
    );
  }
);

Page.displayName = 'Page';
