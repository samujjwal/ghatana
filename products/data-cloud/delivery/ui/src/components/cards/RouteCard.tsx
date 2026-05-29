/**
 * Route Card Component
 * 
 * Displays route information from backend/runtime truth contracts.
 * Shows route availability, capabilities, and lifecycle status.
 * 
 * @doc.type component
 * @doc.purpose Route information display card
 * @doc.layer frontend
 * @doc.pattern Presentational Component
 */

import React from 'react';
import { NavLink } from 'react-router';
import { cn, cardStyles, textStyles, badgeStyles } from '../../lib/theme';
import { BaseCard } from './BaseCard';
import type { RouteSurface } from '../../lib/routing/RouteSurfaceRegistry';

interface RouteCardProps {
  /** Route surface data from registry */
  route: RouteSurface;
  /** Whether the route is currently available (runtime truth) */
  isAvailable?: boolean;
  /** Runtime availability reason if not available */
  availabilityReason?: string;
  /** Additional CSS classes */
  className?: string;
}

/**
 * Route card component displaying route information with runtime truth.
 * 
 * @example
 * ```tsx
 * <RouteCard 
 *   route={routeSurface} 
 *   isAvailable={true}
 *   availabilityReason="Feature enabled"
 * />
 * ```
 */
export const RouteCard: React.FC<RouteCardProps> = ({
  route,
  isAvailable = true,
  availabilityReason,
  className = ''
}) => {
  const getLifecycleBadgeColor = (lifecycle: string) => {
    switch (lifecycle) {
      case 'active':
        return badgeStyles.success;
      case 'preview':
        return badgeStyles.info;
      case 'boundary':
        return badgeStyles.warning;
      case 'deprecated':
        return badgeStyles.danger;
      default:
        return badgeStyles.default;
    }
  };

  const getAvailabilityBadgeColor = (available: boolean) => {
    return available ? badgeStyles.success : badgeStyles.danger;
  };

  return (
    <NavLink
      to={route.path}
      className={({ isActive }) =>
        cn(
          'block transition-all hover:scale-[1.02]',
          className
        )
      }
    >
      <BaseCard
        title={route.label}
        subtitle={route.description}
        className={cn(
          cardStyles.base,
          cardStyles.padded,
          'cursor-pointer border-2 transition-colors',
          isAvailable ? 'border-transparent hover:border-gray-300 dark:hover:border-gray-600' : 'border-gray-200 dark:border-gray-700 opacity-60'
        )}
      >
        <div className="space-y-3">
          {/* Lifecycle badge */}
          <div className="flex items-center gap-2">
            <span className={cn('px-2 py-0.5 text-xs font-medium rounded', getLifecycleBadgeColor(route.lifecycle))}>
              {route.lifecycle.toUpperCase()}
            </span>
            <span className={cn('px-2 py-0.5 text-xs font-medium rounded', getAvailabilityBadgeColor(isAvailable))}>
              {isAvailable ? 'AVAILABLE' : 'UNAVAILABLE'}
            </span>
          </div>

          {/* Capabilities */}
          {route.capabilities.length > 0 && (
            <div className="flex flex-wrap gap-1">
              {route.capabilities.slice(0, 3).map((capability) => (
                <span
                  key={capability}
                  className="px-2 py-0.5 text-xs bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400 rounded"
                >
                  {capability}
                </span>
              ))}
              {route.capabilities.length > 3 && (
                <span className="px-2 py-0.5 text-xs text-gray-500 dark:text-gray-400">
                  +{route.capabilities.length - 3} more
                </span>
              )}
            </div>
          )}

          {/* Availability reason */}
          {!isAvailable && availabilityReason && (
            <p className={cn(textStyles.small, 'text-gray-500 dark:text-gray-400')}>
              {availabilityReason}
            </p>
          )}

          {/* Minimum role requirement */}
          <div className="flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400">
            <span>Requires: {route.minimumShellRole}</span>
          </div>
        </div>
      </BaseCard>
    </NavLink>
  );
};

export default RouteCard;
