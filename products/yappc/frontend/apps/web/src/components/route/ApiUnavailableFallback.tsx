/**
 * API Unavailable Fallback Component
 *
 * Displays a user-friendly error message when the backend API or database is unavailable.
 * Provides troubleshooting steps and retry functionality.
 *
 * @doc.type component
 * @doc.purpose Error UI for API unavailability
 * @doc.layer product
 * @doc.pattern Component
 */

import { CloudOff, RefreshCw as Refresh, AlertTriangle as WarningAmber } from 'lucide-react';
import { Box, Container, Typography, Stack, Button, Surface as Paper, Alert } from '@ghatana/ui';

export interface ApiUnavailableFallbackProps {
    /** Error message to display */
    error?: string;
    /** Callback to retry */
    onRetry?: () => void;
    /** Whether retry is in progress */
    isRetrying?: boolean;
}

/**
 * ApiUnavailableFallback - Displayed when backend API is unavailable
 *
 * This component provides a polished error experience that explains to users
 * why the app isn't loading and what they can do about it.
 */
export const ApiUnavailableFallback: React.FC<ApiUnavailableFallbackProps> = ({
  error,
  onRetry,
  isRetrying = false,
}) => {
  return (
    <Box
      className="flex min-h-screen items-center justify-center p-4"
      style={{ background: 'linear-gradient(135deg, #f8fafc 0%, #eef2ff 100%)' }}
    >
      <Container size="sm">
        <Paper elevation={3} className="rounded-lg bg-[#ffffff] p-8 text-center">
          {/* Icon */}
          <Box className="mb-6 flex justify-center">
            <CloudOff className="text-[64px] text-[#f59e0b]" />
          </Box>

          {/* Title */}
          <Typography as="h4" className="mb-4 font-semibold text-[#1f2937]">
            Service Unavailable
          </Typography>

          {/* Subtitle */}
          <Typography as="p" className="mb-6 leading-relaxed text-[#6b7280]">
            We're having trouble connecting to our services. This could mean the
            backend server or database is not running.
          </Typography>

          {/* Error Alert */}
          {error && (
            <Alert
              severity="warning"
              icon={<WarningAmber />}
              className="mb-6 border-[#fcd34d] bg-[#fef3c7] text-left"
            >
              <Typography as="span" className="text-xs text-[#78350f]">
                {error}
              </Typography>
            </Alert>
          )}

          {/* Troubleshooting Steps */}
          <Box className="mb-6 rounded bg-[#f3f4f6] p-4 text-left">
            <Typography as="p" className="mb-3 font-semibold text-[#1f2937]">
              Troubleshooting Steps:
            </Typography>
            <Stack spacing={1} component="ol" className="pl-4">
              <Typography as="p" component="li" className="text-sm text-[#4b5563]">
                Ensure the backend API server is running (port 7003)
              </Typography>
              <Typography as="p" component="li" className="text-sm text-[#4b5563]">
                Verify the database service is running
              </Typography>
              <Typography as="p" component="li" className="text-sm text-[#4b5563]">
                Check your network connection
              </Typography>
              <Typography as="p" component="li" className="text-sm text-[#4b5563]">
                Try reloading the page
              </Typography>
            </Stack>
          </Box>

          {/* Action Buttons */}
          <Stack spacing={2} direction={{ xs: 'column', sm: 'row' }}>
            <Button
              variant="solid"
              tone="primary"
              size="lg"
              startIcon={<Refresh />}
              onClick={onRetry}
              disabled={isRetrying}
              fullWidth
              className="bg-[#3b82f6] hover:bg-[#2563eb]"
            >
              {isRetrying ? 'Retrying...' : 'Retry'}
            </Button>
            <Button
              variant="outlined"
              size="lg"
              onClick={() => window.location.reload()}
              fullWidth
            >
              Reload Page
            </Button>
          </Stack>

          {/* Help Text */}
          <Typography as="span" className="mt-6 block text-xs text-[#9ca3af]">
            If the problem persists, please contact support or check the
            documentation.
          </Typography>
        </Paper>
      </Container>
    </Box>
  );
};
