/**
 * ErrorStates Components
 * 
 * Comprehensive error state components for canvas
 * 
 * Components:
 * - ConnectionLost: Network connection error state
 * - SaveFailure: Save operation failure state
 * - LoadingState: Loading indicators for various operations
 * - PermissionDenied: Access denied state
 * - ZoomLimitReached: Zoom boundary notification
 * - EmptySearchResults: No results found state
 * 
 * @doc.type component
 * @doc.purpose Error handling and user feedback
 * @doc.layer components
 */

import { Box, Button, Typography, Spinner as CircularProgress, Alert, AlertTitle } from '@ghatana/ui';
import { CloudOff as ConnectionIcon, Save as SaveIcon, Lock as LockIcon, ZoomIn as ZoomIcon, SearchX as SearchIcon, RefreshCw as RefreshIcon, AlertTriangle as WarningIcon } from 'lucide-react';
import React from 'react';

import { CANVAS_TOKENS } from '../tokens/canvas-tokens';

const { SPACING, COLORS, TYPOGRAPHY, FONT_WEIGHT, RADIUS, SHADOWS } = CANVAS_TOKENS;

// ============================================================================
// ConnectionLost Component
// ============================================================================

export interface ConnectionLostProps {
  /** Callback when retry clicked */
  onRetry?: () => void;
  
  /** Show reconnecting state */
  isReconnecting?: boolean;
  
  /** Custom message */
  message?: string;
  
  /** Last successful connection time */
  lastConnected?: Date;
}

/**
 * ConnectionLost - Network connection error state
 */
export function ConnectionLost({
  onRetry,
  isReconnecting = false,
  message = 'Connection to server lost',
  lastConnected,
}: ConnectionLostProps) {
  return (
    <Box
      className="fixed text-center top-[50%] left-[50%] max-w-[400px]" style={{ transform: 'translate(-50%, -50%)', backgroundColor: COLORS.PANEL_BG_LIGHT, borderRadius: RADIUS.LG, boxShadow: SHADOWS.XL, padding: SPACING.XL, zIndex: CANVAS_TOKENS.Z_INDEX.MODAL }} >
      {/* Icon */}
      <Box
        className="w-[80px] h-[80px] rounded-full" style={{ backgroundColor: `${COLORS.ERROR}15`, margin: '0 auto', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: SPACING.MD }}
      >
        <ConnectionIcon className="text-[40px]" style={{ color: COLORS.ERROR }} />
      </Box>

      {/* Title */}
      <Typography
        style={{
          fontSize: TYPOGRAPHY.XL,
          fontWeight: FONT_WEIGHT.BOLD,
          color: COLORS.TEXT_PRIMARY,
          marginBottom: SPACING.SM,
        }}
      >
        Connection Lost
      </Typography>

      {/* Message */}
      <Typography
        style={{
          fontSize: TYPOGRAPHY.BASE,
          color: COLORS.TEXT_SECONDARY,
          marginBottom: SPACING.MD,
        }}
      >
        {message}
      </Typography>

      {/* Last Connected */}
      {lastConnected && (
        <Typography
          style={{
            fontSize: TYPOGRAPHY.SM,
            color: COLORS.TEXT_DISABLED,
            marginBottom: SPACING.LG,
          }}
        >
          Last connected: {lastConnected.toLocaleTimeString()}
        </Typography>
      )}

      {/* Retry Button */}
      <Button
        variant="solid"
        startIcon={isReconnecting ? <CircularProgress size={16} /> : <RefreshIcon />}
        onClick={onRetry}
        disabled={isReconnecting}
        className="text-white hover:opacity-[0.9]" style={{ backgroundColor: COLORS.PRIMARY, paddingLeft: SPACING.XL, paddingRight: SPACING.XL, paddingTop: SPACING.SM, paddingBottom: SPACING.SM, boxShadow: SHADOWS.LG }}
      >
        {isReconnecting ? 'Reconnecting...' : 'Retry Connection'}
      </Button>
    </Box>
  );
}

// ============================================================================
// SaveFailure Component
// ============================================================================

export interface SaveFailureProps {
  /** Error message */
  error: string;
  
  /** Callback when retry clicked */
  onRetry?: () => void;
  
  /** Callback when discard clicked */
  onDiscard?: () => void;
  
  /** Show saving state */
  isSaving?: boolean;
  
  /** Number of unsaved changes */
  unsavedChanges?: number;
}

/**
 * SaveFailure - Save operation failure state
 */
export function SaveFailure({
  error,
  onRetry,
  onDiscard,
  isSaving = false,
  unsavedChanges = 0,
}: SaveFailureProps) {
  return (
    <Alert
      severity="error"
      icon={<SaveIcon />}
      className="fixed max-w-[400px]" action={
        <Box className="flex gap-2" >
          {onDiscard && (
            <Button
              size="sm"
              onClick={onDiscard}
              style={{ color: COLORS.ERROR }}
            >
              Discard
            </Button>
          )}
          {onRetry && (
            <Button
              size="sm"
              variant="solid"
              onClick={onRetry}
              disabled={isSaving}
              startIcon={isSaving ? <CircularProgress size={12} /> : <RefreshIcon />}
              className="hover:opacity-[0.9]" style={{ backgroundColor: COLORS.ERROR }}
            >
              {isSaving ? 'Saving...' : 'Retry'}
            </Button>
          )}
        </Box>
      }
    >
      <AlertTitle style={{ fontWeight: FONT_WEIGHT.BOLD }}>
        Failed to Save Changes
      </AlertTitle>
      <Typography style={{ fontSize: TYPOGRAPHY.SM, marginBottom: SPACING.XS }}>
        {error}
      </Typography>
      {unsavedChanges > 0 && (
        <Typography style={{ fontSize: TYPOGRAPHY.XS, color: COLORS.TEXT_SECONDARY }}>
          {unsavedChanges} unsaved {unsavedChanges === 1 ? 'change' : 'changes'}
        </Typography>
      )}
    </Alert>
  );
}

// ============================================================================
// LoadingState Component
// ============================================================================

export interface LoadingStateProps {
  /** Loading message */
  message?: string;
  
  /** Show progress percentage */
  progress?: number;
  
  /** Loading type */
  type?: 'canvas' | 'panel' | 'inline';
  
  /** Size variant */
  size?: 'small' | 'medium' | 'large';
}

/**
 * LoadingState - Loading indicators for various operations
 */
export function LoadingState({
  message = 'Loading...',
  progress,
  type = 'canvas',
  size = 'medium',
}: LoadingStateProps) {
  const sizeMap = {
    small: 24,
    medium: 40,
    large: 60,
  };

  const spinnerSize = sizeMap[size];

  if (type === 'inline') {
    return (
      <Box className="flex items-center gap-2" >
        <CircularProgress size={16} />
        <Typography style={{ fontSize: TYPOGRAPHY.SM, color: COLORS.TEXT_SECONDARY }}>
          {message}
        </Typography>
      </Box>
    );
  }

  if (type === 'panel') {
    return (
      <Box
        className="flex flex-col items-center justify-center p-8 gap-4" >
        <CircularProgress size={spinnerSize} />
        <Typography style={{ fontSize: TYPOGRAPHY.BASE, color: COLORS.TEXT_SECONDARY }}>
          {message}
        </Typography>
        {progress !== undefined && (
          <Typography style={{ fontSize: TYPOGRAPHY.SM, color: COLORS.TEXT_DISABLED }}>
            {Math.round(progress)}%
          </Typography>
        )}
      </Box>
    );
  }

  // Canvas type (full screen overlay)
  return (
    <Box
      className="fixed flex flex-col items-center justify-center top-[0px] left-[0px] right-[0px] bottom-[0px]" style={{ backgroundColor: 'rgba(255, backdropFilter: 'blur(4px)', zIndex: CANVAS_TOKENS.Z_INDEX.MODAL' }} >
      <CircularProgress size={spinnerSize} />
      <Typography
        style={{
          marginTop: SPACING.LG,
          fontSize: TYPOGRAPHY.LG,
          fontWeight: FONT_WEIGHT.MEDIUM,
          color: COLORS.TEXT_PRIMARY,
        }}
      >
        {message}
      </Typography>
      {progress !== undefined && (
        <Typography
          style={{
            marginTop: SPACING.SM,
            fontSize: TYPOGRAPHY.BASE,
            color: COLORS.TEXT_SECONDARY,
          }}
        >
          {Math.round(progress)}%
        </Typography>
      )}
    </Box>
  );
}

// ============================================================================
// PermissionDenied Component
// ============================================================================

export interface PermissionDeniedProps {
  /** Resource being accessed */
  resource?: string;
  
  /** Required permission */
  requiredPermission?: string;
  
  /** Callback when request access clicked */
  onRequestAccess?: () => void;
}

/**
 * PermissionDenied - Access denied state
 */
export function PermissionDenied({
  resource = 'this resource',
  requiredPermission,
  onRequestAccess,
}: PermissionDeniedProps) {
  return (
    <Box
      className="flex flex-col items-center justify-center text-center p-12" >
      {/* Icon */}
      <Box
        className="w-[80px] h-[80px] rounded-full" style={{ backgroundColor: `${COLORS.WARNING}15`, display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: SPACING.MD }}
      >
        <LockIcon className="text-[40px]" style={{ color: COLORS.WARNING }} />
      </Box>

      {/* Title */}
      <Typography
        style={{
          fontSize: TYPOGRAPHY.XL,
          fontWeight: FONT_WEIGHT.BOLD,
          color: COLORS.TEXT_PRIMARY,
          marginBottom: SPACING.SM,
        }}
      >
        Access Denied
      </Typography>

      {/* Message */}
      <Typography
        className="max-w-[400px] text-base mb-4" >
        You don't have permission to access {resource}.
      </Typography>

      {/* Required Permission */}
      {requiredPermission && (
        <Typography
          style={{
            fontSize: TYPOGRAPHY.SM,
            color: COLORS.TEXT_DISABLED,
            marginBottom: SPACING.LG,
          }}
        >
          Required permission: <strong>{requiredPermission}</strong>
        </Typography>
      )}

      {/* Request Access Button */}
      {onRequestAccess && (
        <Button
          variant="outlined"
          onClick={onRequestAccess}
          style={{
            borderColor: COLORS.WARNING,
            color: COLORS.WARNING,
          }}
        >
          Request Access
        </Button>
      )}
    </Box>
  );
}

// ============================================================================
// ZoomLimitReached Component
// ============================================================================

export interface ZoomLimitReachedProps {
  /** Limit type */
  limitType: 'min' | 'max';
  
  /** Current zoom level */
  currentZoom: number;
  
  /** Auto-hide after duration (ms) */
  autoHideDuration?: number;
  
  /** Callback when dismissed */
  onDismiss?: () => void;
}

/**
 * ZoomLimitReached - Zoom boundary notification
 */
export function ZoomLimitReached({
  limitType,
  currentZoom,
  autoHideDuration = 3000,
  onDismiss,
}: ZoomLimitReachedProps) {
  React.useEffect(() => {
    if (autoHideDuration && onDismiss) {
      const timer = setTimeout(onDismiss, autoHideDuration);
      return () => clearTimeout(timer);
    }
  }, [autoHideDuration, onDismiss]);

  const message = limitType === 'min' 
    ? `Minimum zoom level reached (${(currentZoom * 100).toFixed(0)}%)`
    : `Maximum zoom level reached (${(currentZoom * 100).toFixed(0)}%)`;

  return (
    <Alert
      severity="info"
      icon={<ZoomIcon />}
      onClose={onDismiss}
      className="fixed left-[50%]" style={{ bottom: SPACING.MD + 50, transform: 'translateX(-50%)', boxShadow: SHADOWS.LG, zIndex: CANVAS_TOKENS.Z_INDEX.TOAST }} >
      {message}
    </Alert>
  );
}

// ============================================================================
// EmptySearchResults Component
// ============================================================================

export interface EmptySearchResultsProps {
  /** Search query */
  query: string;
  
  /** Callback when clear search clicked */
  onClearSearch?: () => void;
  
  /** Show suggestions */
  suggestions?: string[];
}

/**
 * EmptySearchResults - No results found state
 */
export function EmptySearchResults({
  query,
  onClearSearch,
  suggestions = [],
}: EmptySearchResultsProps) {
  return (
    <Box
      className="flex flex-col items-center justify-center text-center p-12" >
      {/* Icon */}
      <Box
        className="rounded-full flex items-center justify-center w-[60px] h-[60px] mb-6" >
        <SearchIcon className="text-[32px]" style={{ color: COLORS.TEXT_DISABLED, backgroundColor: COLORS.NEUTRAL_100 }} />
      </Box>

      {/* Title */}
      <Typography
        style={{
          fontSize: TYPOGRAPHY.LG,
          fontWeight: FONT_WEIGHT.BOLD,
          color: COLORS.TEXT_PRIMARY,
          marginBottom: SPACING.SM,
        }}
      >
        No results found
      </Typography>

      {/* Query */}
      <Typography
        style={{
          fontSize: TYPOGRAPHY.BASE,
          color: COLORS.TEXT_SECONDARY,
          marginBottom: SPACING.MD,
        }}
      >
        No results for "<strong>{query}</strong>"
      </Typography>

      {/* Suggestions */}
      {suggestions.length > 0 && (
        <Box style={{ marginBottom: SPACING.LG }}>
          <Typography
            style={{
              fontSize: TYPOGRAPHY.SM,
              color: COLORS.TEXT_SECONDARY,
              marginBottom: SPACING.SM,
            }}
          >
            Try searching for:
          </Typography>
          <Box className="flex flex-wrap justify-center gap-2" >
            {suggestions.map((suggestion, index) => (
              <Box
                key={index}
                className="cursor-pointer" style={{ paddingLeft: SPACING.SM, paddingRight: SPACING.SM, paddingTop: SPACING.XS, paddingBottom: SPACING.XS, backgroundColor: COLORS.NEUTRAL_100, borderRadius: RADIUS.SM, fontSize: TYPOGRAPHY.SM, color: COLORS.TEXT_PRIMARY }}
              >
                {suggestion}
              </Box>
            ))}
          </Box>
        </Box>
      )}

      {/* Clear Search Button */}
      {onClearSearch && (
        <Button
          variant="outlined"
          onClick={onClearSearch}
          style={{
            borderColor: COLORS.BORDER_LIGHT,
            color: COLORS.TEXT_PRIMARY,
          }}
        >
          Clear Search
        </Button>
      )}
    </Box>
  );
}

// ============================================================================
// GenericError Component
// ============================================================================

export interface GenericErrorProps {
  /** Error title */
  title?: string;
  
  /** Error message */
  message: string;
  
  /** Error details (for developers) */
  details?: string;
  
  /** Callback when retry clicked */
  onRetry?: () => void;
  
  /** Show error details */
  showDetails?: boolean;
}

/**
 * GenericError - Generic error state component
 */
export function GenericError({
  title = 'Something went wrong',
  message,
  details,
  onRetry,
  showDetails = false,
}: GenericErrorProps) {
  const [detailsExpanded, setDetailsExpanded] = React.useState(false);

  return (
    <Box
      className="flex flex-col items-center justify-center text-center p-12" >
      {/* Icon */}
      <Box
        className="w-[80px] h-[80px] rounded-full" style={{ backgroundColor: COLORS.NEUTRAL_100, display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: SPACING.MD }}
      >
        <WarningIcon className="text-[40px] text-base mb-6" style={{ color: COLORS.ERROR }} />
      </Box>

      {/* Title */}
      <Typography
        style={{
          fontSize: TYPOGRAPHY.XL,
          fontWeight: FONT_WEIGHT.BOLD,
          color: COLORS.TEXT_PRIMARY,
          marginBottom: SPACING.SM,
        }}
      >
        {title}
      </Typography>

      {/* Message */}
      <Typograp: COLORS.TEXT_SECONDARY, mb: SPACING.LG */
      >
        {message}
      </Typography>

      {/* Error Details */}
      {showDetails && details && (
        <grate remaining sx: mb: SPACING.LG */>
          <Button
            size="sm"
            onClick={() => setDetailsExpanded(!detailsExpanded)}
            style={{ marginBottom: SPACING.SM }}
          >
            {detailsExpanded ? 'Hide' : 'Show'} Details
          </Button>
          {detailsExpanded && (
            <Box
              className="text-left overflow-auto font-mono max-h-[200px] p-4 rounded-[RADIUS.MDpx] text-xs" >
              {details}
            </Box>
          )}
        </Box>
      )}

      {/* Retry Button */}
      {onRetry && (
        <Button
          variant="solid"
          startIcon={<RefreshIcon />}
          onClick={onRetry}
          className="text-white hover:opacity-[0.9]" style={{ backgroundColor: COLORS.PRIMARY }}
        >
          Try Again
        </Button>
      )}
    </Box>
  );
}
