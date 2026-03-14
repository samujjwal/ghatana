/**
 * @ghatana/ui (Compatibility Layer)
 *
 * Compatibility layer that re-exports from the new split packages.
 * This package is deprecated - use @ghatana/design-system and @ghatana/ui-integration instead.
 *
 * @package @ghatana/ui
 * @deprecated Use @ghatana/design-system for core components and @ghatana/ui-integration for AI/collaboration features
 */

// Re-export everything from design-system
export * from '@ghatana/design-system';

// Re-export integration features
export * as AI from '@ghatana/ui-integration';
export * as PageBuilder from '@ghatana/ui-integration';
export * from '@ghatana/ui-integration';

// Re-export router dependency for compatibility
export { useNavigate, useLocation, useParams } from 'react-router-dom';
