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
import { Box, Container, Typography, Stack, Button, Surface as Paper, Alert } from '@ghatana/design-system';
import { useI18n } from '../../i18n/I18nProvider';

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
export function ApiUnavailableFallback({
  error,
  onRetry,
  isRetrying = false,
}: ApiUnavailableFallbackProps): React.JSX.Element {
  const { t } = useI18n();
  return (
    <Box
      className="flex min-h-screen items-center justify-center p-4"
      style={{ background: 'linear-gradient(135deg, #f8fafc 0%, #eef2ff 100%)' }}
    >
      <Container className="max-w-2xl">
        <Paper elevation={3} className="rounded-lg bg-[#ffffff] p-8 text-center">
          {/* Icon */}
          <Box className="mb-6 flex justify-center">
            <CloudOff className="text-[64px] text-[#f59e0b]" />
          </Box>

          {/* Title */}
          <Typography variant="h4" className="mb-4 font-semibold text-[#1f2937]">
            {t('apiUnavailable.title')}
          </Typography>

          {/* Subtitle */}
          <Typography className="mb-6 leading-relaxed text-[#6b7280]">
            {t('apiUnavailable.subtitle')}
          </Typography>

          {/* Error Alert */}
          {error && (
            <Alert
              severity="warning"
              icon={<WarningAmber />}
              className="mb-6 border-[#fcd34d] bg-[#fef3c7] text-left"
            >
              <Typography className="text-xs text-[#78350f]">
                {error}
              </Typography>
            </Alert>
          )}

          {/* Troubleshooting Steps */}
          <Box className="mb-6 rounded bg-[#f3f4f6] p-4 text-left">
            <Typography className="mb-3 font-semibold text-[#1f2937]">
              {t('apiUnavailable.troubleshootingTitle')}
            </Typography>
            <ol className="space-y-1 pl-4">
              <li className="text-sm text-[#4b5563]">{t('apiUnavailable.step1')}</li>
              <li className="text-sm text-[#4b5563]">{t('apiUnavailable.step2')}</li>
              <li className="text-sm text-[#4b5563]">{t('apiUnavailable.step3')}</li>
              <li className="text-sm text-[#4b5563]">{t('apiUnavailable.step4')}</li>
            </ol>
          </Box>

          {/* Action Buttons */}
          <Box className="flex flex-col gap-2 sm:flex-row">
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
              {isRetrying ? t('apiUnavailable.retrying') : t('apiUnavailable.retry')}
            </Button>
            <Button
              variant="outlined"
              size="lg"
              onClick={() => window.location.reload()}
              fullWidth
            >
              {t('apiUnavailable.reloadPage')}
            </Button>
          </Box>

          {/* Help Text */}
          <Typography className="mt-6 block text-xs text-[#9ca3af]">
            {t('apiUnavailable.helpText')}
          </Typography>
        </Paper>
      </Container>
    </Box>
  );
}
