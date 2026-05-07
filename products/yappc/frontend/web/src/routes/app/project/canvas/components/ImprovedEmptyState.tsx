import { Box, Typography, Button, Stack, Card } from '@ghatana/design-system';
import { Sparkles as AutoAwesome, GitBranch as AccountTree, LayoutGrid as ViewModule } from 'lucide-react';

interface ImprovedEmptyStateProps {
  onSelectTemplate: () => void;
  onBlankCanvas: () => void;
  onAIAssistant: () => void;
}

/**
 * Improved empty state with better visual hierarchy and feature discovery
 * Shows preview of canvas capabilities with clear CTAs
 */
export function ImprovedEmptyState({
  onSelectTemplate,
  onBlankCanvas,
  onAIAssistant,
}: ImprovedEmptyStateProps) {
  return (
    <Box className="absolute inset-0 flex flex-col items-center justify-center p-6">
      <Card
        variant="flat"
        className="flex w-full max-w-[600px] flex-col items-center gap-6 border border-solid border-border p-10 dark:border-border"
      >
        <Box className="flex h-[64px] w-[64px] items-center justify-center rounded-xl bg-info-bg text-info-color dark:bg-info-bg/30 dark:text-info-color">
          <AccountTree className="text-[32px]" />
        </Box>

        <Box className="text-center">
          <Typography as="h5" fontWeight={600} gutterBottom>
            Start Building Your Architecture
          </Typography>
          <Typography as="p" color="text.secondary">
            Choose how you'd like to begin, then drag components from the left
            palette onto the canvas.
          </Typography>
        </Box>

        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          spacing={2}
          width="100%"
          justifyContent="center"
        >
          <Button
            variant="solid"
            size="lg"
            startIcon={<AutoAwesome />}
            onClick={onAIAssistant}
            className="flex-1 whitespace-nowrap"
          >
            Guided Assistant
          </Button>
          <Button
            variant="outlined"
            size="lg"
            onClick={onSelectTemplate}
            className="flex-1 whitespace-nowrap"
          >
            Use Template
          </Button>
          <Button
            variant="ghost"
            size="lg"
            onClick={onBlankCanvas}
            className="flex-1 whitespace-nowrap"
          >
            Blank Canvas
          </Button>
        </Stack>

        <Box className="mt-2 flex items-center gap-2 rounded-lg bg-surface-muted p-3 text-fg-muted dark:bg-surface dark:text-fg-muted">
          <ViewModule size={16} />
          <Typography as="p" className="text-sm">
            <strong>Tip:</strong> Press{' '}
            <Box
              component="kbd"
              className="px-[6.4px] py-[1.6px] rounded font-semibold bg-info-bg dark:bg-info-bg/20 font-mono text-[0.85em]"
            >
              ⌘K
            </Box>{' '}
            anytime for quick actions
          </Typography>
        </Box>
      </Card>
    </Box>
  );
}
