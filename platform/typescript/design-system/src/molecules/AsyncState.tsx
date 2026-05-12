import * as React from 'react';
import { cn } from '@ghatana/platform-utils';

import { Spinner } from '../atoms/Spinner';
import { EmptyState, type EmptyStateProps } from './EmptyState';

type AsyncStateSize = 'sm' | 'md' | 'lg';
type AsyncStateTone = 'neutral' | 'info' | 'success' | 'warning' | 'danger';

export type AsyncStateStatus =
  | 'idle'
  | 'loading'
  | 'empty'
  | 'error'
  | 'success'
  | 'access-denied'
  | 'feature-unavailable';

interface AsyncStateAction {
  readonly label: string;
  readonly onClick: () => void;
}

interface AsyncStateBaseProps {
  readonly title: string;
  readonly description?: string;
  readonly action?: React.ReactNode;
  readonly secondaryAction?: React.ReactNode;
  readonly size?: AsyncStateSize;
  readonly className?: string;
}

interface LoadingStateProps extends Omit<AsyncStateBaseProps, 'title' | 'action' | 'secondaryAction'> {
  readonly title?: string;
}

export interface ErrorStateProps extends AsyncStateBaseProps {
  readonly error?: unknown;
  readonly retryAction?: AsyncStateAction;
}

export type AccessDeniedStateProps = AsyncStateBaseProps;
export type FeatureUnavailableStateProps = AsyncStateBaseProps;
export type SuccessStateProps = AsyncStateBaseProps;

export interface AsyncStateBoundaryProps {
  readonly status: AsyncStateStatus;
  readonly children: React.ReactNode;
  readonly loading?: LoadingStateProps;
  readonly empty?: AsyncStateBaseProps;
  readonly error?: ErrorStateProps;
  readonly accessDenied?: AccessDeniedStateProps;
  readonly featureUnavailable?: FeatureUnavailableStateProps;
  readonly success?: SuccessStateProps;
  readonly className?: string;
}

function actionButton(action: AsyncStateAction): React.ReactElement {
  return (
    <button
      type="button"
      onClick={action.onClick}
      className="inline-flex min-h-10 items-center justify-center rounded border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-900 transition-colors hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
    >
      {action.label}
    </button>
  );
}

function ErrorMessage({ error }: { readonly error: unknown }): React.ReactElement | null {
  if (error === undefined || error === null) {
    return null;
  }

  const message = error instanceof Error ? error.message : String(error);
  if (message.length === 0) {
    return null;
  }

  return (
    <p className="mt-2 max-w-md text-sm text-red-700" data-testid="async-state-error-detail">
      {message}
    </p>
  );
}

function StateFrame({
  title,
  description,
  action,
  secondaryAction,
  size = 'md',
  className,
  tone = 'neutral',
  role = 'status',
  children,
}: AsyncStateBaseProps & {
  readonly tone?: AsyncStateTone;
  readonly role?: 'alert' | 'status';
  readonly children?: React.ReactNode;
}): React.ReactElement {
  const toneClasses: Record<AsyncStateTone, string> = {
    neutral: 'border-gray-200 bg-white text-gray-900',
    info: 'border-blue-200 bg-blue-50 text-blue-950',
    success: 'border-green-200 bg-green-50 text-green-950',
    warning: 'border-yellow-200 bg-yellow-50 text-yellow-950',
    danger: 'border-red-200 bg-red-50 text-red-950',
  };

  return (
    <section
      {...(role === 'alert' ? { role } : {})}
      aria-live={role === 'alert' ? 'assertive' : 'polite'}
      className={cn('rounded border px-6', toneClasses[tone], className)}
    >
      <EmptyState
        title={title}
        description={description}
        size={size}
        action={action}
        secondaryAction={secondaryAction}
      />
      {children}
    </section>
  );
}

export function LoadingState({
  title = 'Loading',
  description = 'Preparing this view.',
  size = 'md',
  className,
}: LoadingStateProps): React.ReactElement {
  return (
    <section
      role="status"
      aria-live="polite"
      aria-label="Loading"
      className={cn('flex flex-col items-center justify-center rounded border border-gray-200 bg-white px-6 py-12 text-center', className)}
    >
      <Spinner size={size} aria-hidden="true" />
      <h3 className="mt-4 text-base font-semibold text-gray-900">{title}</h3>
      {description ? <p className="mt-2 max-w-md text-sm text-gray-600">{description}</p> : null}
    </section>
  );
}

export function ErrorState({
  title,
  description,
  error,
  action,
  secondaryAction,
  retryAction,
  size,
  className,
}: ErrorStateProps): React.ReactElement {
  return (
    <StateFrame
      title={title}
      description={description}
      action={action ?? (retryAction ? actionButton(retryAction) : undefined)}
      secondaryAction={secondaryAction}
      size={size}
      className={className}
      tone="danger"
      role="alert"
    >
      <ErrorMessage error={error} />
    </StateFrame>
  );
}

export function AccessDeniedState(props: AccessDeniedStateProps): React.ReactElement {
  return <StateFrame {...props} tone="warning" role="alert" />;
}

export function FeatureUnavailableState(props: FeatureUnavailableStateProps): React.ReactElement {
  return <StateFrame {...props} tone="info" role="status" />;
}

export function SuccessState(props: SuccessStateProps): React.ReactElement {
  return <StateFrame {...props} tone="success" role="status" />;
}

export function AsyncStateBoundary({
  status,
  children,
  loading,
  empty,
  error,
  accessDenied,
  featureUnavailable,
  success,
  className,
}: AsyncStateBoundaryProps): React.ReactElement {
  if (status === 'loading') {
    return <LoadingState className={className} {...loading} />;
  }

  if (status === 'empty') {
    const emptyProps: EmptyStateProps = {
      title: empty?.title ?? 'No data available',
      ...(empty?.description !== undefined ? { description: empty.description } : {}),
      ...(empty?.action !== undefined ? { action: empty.action } : {}),
      ...(empty?.secondaryAction !== undefined ? { secondaryAction: empty.secondaryAction } : {}),
      ...(empty?.size !== undefined ? { size: empty.size } : {}),
      className: cn(className, empty?.className),
    };
    return <EmptyState {...emptyProps} />;
  }

  if (status === 'error') {
    return (
      <ErrorState
        title={error?.title ?? 'Something went wrong'}
        description={error?.description ?? 'The view could not be loaded.'}
        className={cn(className, error?.className)}
        {...error}
      />
    );
  }

  if (status === 'access-denied') {
    return (
      <AccessDeniedState
        title={accessDenied?.title ?? 'Access denied'}
        description={accessDenied?.description ?? 'You do not have permission to view this surface.'}
        className={cn(className, accessDenied?.className)}
        {...accessDenied}
      />
    );
  }

  if (status === 'feature-unavailable') {
    return (
      <FeatureUnavailableState
        title={featureUnavailable?.title ?? 'Feature unavailable'}
        description={featureUnavailable?.description ?? 'This capability is not available for the current context.'}
        className={cn(className, featureUnavailable?.className)}
        {...featureUnavailable}
      />
    );
  }

  if (status === 'success' && success) {
    return <SuccessState className={className} {...success} />;
  }

  return <>{children}</>;
}
